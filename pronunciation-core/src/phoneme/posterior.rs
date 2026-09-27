//! Posterior probability and margin calculations from acoustic CTC model outputs.

use serde::{Deserialize, Serialize};

/// Detailed analysis of target phoneme probability versus competitors.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct PosteriorAnalysis {
    /// The target phoneme evaluated.
    pub target_symbol: String,
    /// Probability assigned to the target phoneme (0.0 to 1.0).
    pub target_probability: f32,
    /// The closest competing/confusing phoneme symbol.
    pub top_confusion_symbol: Option<String>,
    /// Probability of the closest competing phoneme.
    pub top_confusion_probability: f32,
    /// Posterior margin: target_probability - top_confusion_probability.
    /// Positive indicates target leads; negative indicates confusion dominates.
    pub margin: f32,
    /// Shannon entropy across the distribution (measure of uncertainty).
    pub entropy: f32,
    /// Whether target phoneme is decisively leading (e.g. prob > 0.5 and margin > 0.15).
    pub is_dominant: bool,
}

/// Computes Shannon entropy (base 2) for a probability slice.
pub fn compute_entropy(probs: &[f32]) -> f32 {
    let mut sum_entropy = 0.0f32;
    for &p in probs {
        if p > 1e-7 {
            sum_entropy -= p * p.log2();
        }
    }
    sum_entropy
}

/// Analyzes posteriors for a target phoneme against all frame/segment probabilities.
pub fn analyze_posterior(target_symbol: &str, distribution: &[(String, f32)]) -> PosteriorAnalysis {
    let mut target_prob = 0.0f32;
    let mut top_competitor: Option<String> = None;
    let mut top_competitor_prob = 0.0f32;

    let probs: Vec<f32> = distribution.iter().map(|(_, p)| *p).collect();
    let entropy = compute_entropy(&probs);

    for (symbol, prob) in distribution {
        if symbol == target_symbol {
            target_prob = *prob;
        } else if *prob > top_competitor_prob {
            top_competitor_prob = *prob;
            top_competitor = Some(symbol.clone());
        }
    }

    let margin = target_prob - top_competitor_prob;
    let is_dominant = target_prob >= 0.50 && margin >= 0.15;

    PosteriorAnalysis {
        target_symbol: target_symbol.to_string(),
        target_probability: target_prob,
        top_confusion_symbol: top_competitor,
        top_confusion_probability: top_competitor_prob,
        margin,
        entropy,
        is_dominant,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_posterior_margin_dominant() {
        let dist = vec![
            ("ʌ".to_string(), 0.75),
            ("ɑ".to_string(), 0.15),
            ("ə".to_string(), 0.10),
        ];

        let analysis = analyze_posterior("ʌ", &dist);
        assert_eq!(analysis.target_probability, 0.75);
        assert_eq!(analysis.top_confusion_symbol.as_deref(), Some("ɑ"));
        assert!((analysis.margin - 0.60).abs() < 1e-5);
        assert!(analysis.is_dominant);
    }

    #[test]
    fn test_posterior_margin_ambiguous() {
        let dist = vec![
            ("ʌ".to_string(), 0.45),
            ("ɑ".to_string(), 0.42),
            ("ə".to_string(), 0.13),
        ];

        let analysis = analyze_posterior("ʌ", &dist);
        assert_eq!(analysis.target_probability, 0.45);
        assert_eq!(analysis.top_confusion_symbol.as_deref(), Some("ɑ"));
        assert!((analysis.margin - 0.03).abs() < 1e-5);
        assert!(!analysis.is_dominant);
    }
}
