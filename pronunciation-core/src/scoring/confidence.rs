//! System confidence assessment based on multimodal stability and margins.

use crate::evidence::fusion::{FusedPhonemeEvidence, MultimodalCongruence};
use serde::{Deserialize, Serialize};

/// High-level system confidence.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
pub enum ConfidenceLevel {
    Low,
    Medium,
    High,
}

impl ConfidenceLevel {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::High => "High",
            Self::Medium => "Medium",
            Self::Low => "Low",
        }
    }

    pub fn to_display_zh(&self) -> &'static str {
        match self {
            Self::High => "高",
            Self::Medium => "中",
            Self::Low => "低",
        }
    }
}

/// Evaluates confidence across an utterance's fused phoneme evidence.
pub fn evaluate_word_confidence(fused_phonemes: &[FusedPhonemeEvidence]) -> ConfidenceLevel {
    if fused_phonemes.is_empty() {
        return ConfidenceLevel::Low;
    }

    let mut conflict_count = 0;
    let mut ambiguous_count = 0;
    let mut high_confidence_count = 0;

    for f in fused_phonemes {
        match f.congruence {
            MultimodalCongruence::VisualConflict => conflict_count += 1,
            MultimodalCongruence::Ambiguous => ambiguous_count += 1,
            MultimodalCongruence::CorroboratedPass
            | MultimodalCongruence::CorroboratedDeviation => {
                high_confidence_count += 1;
            }
            MultimodalCongruence::AcousticOnlyDeviation => {
                // If visual saliency is low (e.g. /k/), acoustic only is still trustworthy
                if f.visual_weight < 0.15 {
                    high_confidence_count += 1;
                } else {
                    ambiguous_count += 1;
                }
            }
        }
    }

    if conflict_count > 0 || ambiguous_count >= 2 {
        ConfidenceLevel::Low
    } else if high_confidence_count >= fused_phonemes.len() - 1 {
        ConfidenceLevel::High
    } else {
        ConfidenceLevel::Medium
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_confidence_evaluation() {
        let ev1 = FusedPhonemeEvidence {
            phoneme: "f".to_string(),
            acoustic_quality: 0.9,
            visual_quality: 0.9,
            visual_weight: 0.35,
            fused_quality: 0.9,
            congruence: MultimodalCongruence::CorroboratedPass,
            primary_confusion: None,
            visual_issues: vec![],
        };

        let ev2 = FusedPhonemeEvidence {
            phoneme: "ʌ".to_string(),
            acoustic_quality: 0.5,
            visual_quality: 0.5,
            visual_weight: 0.25,
            fused_quality: 0.5,
            congruence: MultimodalCongruence::CorroboratedDeviation,
            primary_confusion: Some("ɑ".to_string()),
            visual_issues: vec!["Jaw opening is excessive".to_string()],
        };

        let conf = evaluate_word_confidence(&[ev1, ev2]);
        assert_eq!(conf, ConfidenceLevel::High);
    }
}
