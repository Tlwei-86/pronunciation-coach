//! Scoring rubrics and weighting rules for deterministic evaluation.

use serde::{Deserialize, Serialize};

/// Weighting and evaluation criteria for a target word.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct WordRubric {
    pub word: String,
    pub phonemes: Vec<String>,
    pub weights: Vec<f32>,
    pub critical_phonemes: Vec<String>,
}

impl WordRubric {
    /// Constructs a custom rubric, normalizing weights to sum to 1.0.
    pub fn new(
        word: &str,
        phonemes: Vec<String>,
        weights: Vec<f32>,
        critical_phonemes: Vec<String>,
    ) -> Self {
        assert_eq!(
            phonemes.len(),
            weights.len(),
            "Phonemes and weights count must match"
        );
        let sum: f32 = weights.iter().sum();
        let normalized_weights = if sum > 0.0 {
            weights.iter().map(|&w| w / sum).collect()
        } else {
            vec![1.0 / phonemes.len() as f32; phonemes.len()]
        };

        Self {
            word: word.to_string(),
            phonemes,
            weights: normalized_weights,
            critical_phonemes,
        }
    }

    /// Canonical rubric for the primary test word "funk" /fʌŋk/.
    ///
    /// Weights:
    /// - /f/  : 0.20
    /// - /ʌ/  : 0.40 (primary vowel focus)
    /// - /ŋ/  : 0.15
    /// - /k/  : 0.25 (critical final velar stop release)
    pub fn funk() -> Self {
        Self {
            word: "funk".to_string(),
            phonemes: vec![
                "f".to_string(),
                "ʌ".to_string(),
                "ŋ".to_string(),
                "k".to_string(),
            ],
            weights: vec![0.20, 0.40, 0.15, 0.25],
            critical_phonemes: vec!["ʌ".to_string(), "k".to_string()],
        }
    }
}

/// Retrieves or dynamically generates a rubric for any known target word.
pub fn get_word_rubric(word: &str) -> WordRubric {
    match word.to_lowercase().as_str() {
        "funk" => WordRubric::funk(),
        "sun" => WordRubric::new(
            "sun",
            vec!["s".to_string(), "ʌ".to_string(), "n".to_string()],
            vec![0.30, 0.45, 0.25],
            vec!["ʌ".to_string()],
        ),
        "cat" => WordRubric::new(
            "cat",
            vec!["k".to_string(), "æ".to_string(), "t".to_string()],
            vec![0.25, 0.50, 0.25],
            vec!["æ".to_string()],
        ),
        _ => {
            // Default generic fallback: single phoneme target
            WordRubric::new(
                word,
                vec![word.to_string()],
                vec![1.0],
                vec![word.to_string()],
            )
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_funk_rubric() {
        let rubric = WordRubric::funk();
        assert_eq!(rubric.word, "funk");
        assert_eq!(rubric.phonemes.len(), 4);
        let sum: f32 = rubric.weights.iter().sum();
        assert!((sum - 1.0).abs() < 1e-5);
        assert!(rubric.critical_phonemes.contains(&"ʌ".to_string()));
    }
}
