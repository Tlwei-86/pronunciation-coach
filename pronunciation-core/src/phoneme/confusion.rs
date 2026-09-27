//! Confusion definitions and acoustic/visual confusion tables.

use serde::{Deserialize, Serialize};

/// Detailed confusion relationship candidate.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ConfusionCandidate {
    /// IPA of the confusing phoneme.
    pub confused_ipa: &'static str,
    /// Typical error severity or penalty weight (0.0 to 1.0).
    pub penalty_weight: f32,
    /// Phonetic distinction description.
    pub acoustic_contrast: &'static str,
    /// Visual distinguishing characteristics.
    pub visual_clue: &'static str,
}

/// Known high-frequency confusion pairs for ESL learners (especially L1 Mandarin / Asian learners).
pub static COMMON_CONFUSIONS: &[(&str, &[ConfusionCandidate])] = &[
    (
        "ʌ",
        &[
            ConfusionCandidate {
                confused_ipa: "ɑ",
                penalty_weight: 0.35,
                acoustic_contrast: "/ʌ/ is mid-central and shorter; /ɑ/ is back, lower, and longer.",
                visual_clue: "Jaw opening is excessive for /ɑ/, while /ʌ/ requires moderate jaw opening.",
            },
            ConfusionCandidate {
                confused_ipa: "ə",
                penalty_weight: 0.20,
                acoustic_contrast: "/ə/ is a reduced unstressed vowel; /ʌ/ is fully stressed.",
                visual_clue: "Minimal jaw difference; stress and duration dominate.",
            },
        ],
    ),
    (
        "ɪ",
        &[
            ConfusionCandidate {
                confused_ipa: "i",
                penalty_weight: 0.30,
                acoustic_contrast: "/ɪ/ is lax and shorter; /i/ is tense with higher F2.",
                visual_clue: "Mouth stretch is wider for /i/, while /ɪ/ has relaxed lips.",
            },
        ],
    ),
    (
        "æ",
        &[
            ConfusionCandidate {
                confused_ipa: "ɛ",
                penalty_weight: 0.30,
                acoustic_contrast: "/æ/ has higher F1 and wider open jaw than /ɛ/.",
                visual_clue: "Jaw opening is significantly larger and mouth wider for /æ/.",
            },
        ],
    ),
    (
        "θ",
        &[
            ConfusionCandidate {
                confused_ipa: "s",
                penalty_weight: 0.40,
                acoustic_contrast: "/θ/ has diffuse low energy; /s/ has sharp high-frequency hiss (4-8kHz).",
                visual_clue: "Tongue tip should protrude between teeth for /θ/, whereas teeth are together for /s/.",
            },
        ],
    ),
    (
        "ð",
        &[
            ConfusionCandidate {
                confused_ipa: "z",
                penalty_weight: 0.40,
                acoustic_contrast: "/ð/ is dental fricative; /z/ is alveolar grooved sibilant.",
                visual_clue: "Tongue between teeth for /ð/ vs hidden teeth closure for /z/.",
            },
            ConfusionCandidate {
                confused_ipa: "d",
                penalty_weight: 0.35,
                acoustic_contrast: "/d/ is a stop with burst; /ð/ is a continuous fricative.",
                visual_clue: "Interdental tongue placement for /ð/.",
            },
        ],
    ),
    (
        "r",
        &[
            ConfusionCandidate {
                confused_ipa: "l",
                penalty_weight: 0.45,
                acoustic_contrast: "/r/ has very low F3 (~1600-1800Hz); /l/ has high F3 (~2800Hz) and lateral anti-formants.",
                visual_clue: "Lip rounding/pucker is prominent for initial /r/; tongue tip contacts alveolar ridge for /l/.",
            },
        ],
    ),
    (
        "f",
        &[
            ConfusionCandidate {
                confused_ipa: "v",
                penalty_weight: 0.25,
                acoustic_contrast: "/f/ is voiceless; /v/ is voiced with periodic vocal fold vibration.",
                visual_clue: "Both have labiodental contact (upper teeth on lower lip).",
            },
            ConfusionCandidate {
                confused_ipa: "θ",
                penalty_weight: 0.35,
                acoustic_contrast: "Different spectral tilt and friction turbulence.",
                visual_clue: "/f/ uses lower lip against upper teeth; /θ/ places tongue tip between teeth.",
            },
        ],
    ),
    (
        "k",
        &[
            ConfusionCandidate {
                confused_ipa: "g",
                penalty_weight: 0.25,
                acoustic_contrast: "/k/ has aspiration and voice onset time (VOT) > 40ms; /g/ is voiced.",
                visual_clue: "Internal velar articulation; essentially invisible on camera.",
            },
            ConfusionCandidate {
                confused_ipa: "<del>",
                penalty_weight: 0.50,
                acoustic_contrast: "Missing final stop release burst.",
                visual_clue: "Invisible; strictly relies on acoustic burst.",
            },
        ],
    ),
];

/// Retrieves known confusion candidates for a given target phoneme.
pub fn get_confusion_candidates(target_ipa: &str) -> &'static [ConfusionCandidate] {
    for (target, candidates) in COMMON_CONFUSIONS {
        if *target == target_ipa {
            return candidates;
        }
    }
    &[]
}

/// Checks whether a given candidate is a known critical confusion for the target.
pub fn is_known_confusion(target_ipa: &str, candidate_ipa: &str) -> bool {
    get_confusion_candidates(target_ipa)
        .iter()
        .any(|c| c.confused_ipa == candidate_ipa)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_caret_confusions() {
        let confusions = get_confusion_candidates("ʌ");
        assert_eq!(confusions.len(), 2);
        assert_eq!(confusions[0].confused_ipa, "ɑ");
        assert!(is_known_confusion("ʌ", "ɑ"));
        assert!(is_known_confusion("ʌ", "ə"));
        assert!(!is_known_confusion("ʌ", "i"));
    }

    #[test]
    fn test_r_l_confusion() {
        assert!(is_known_confusion("r", "l"));
        let r_confusions = get_confusion_candidates("r");
        assert_eq!(r_confusions[0].confused_ipa, "l");
    }
}
