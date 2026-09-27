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
    (
        "v",
        &[
            ConfusionCandidate {
                confused_ipa: "w",
                penalty_weight: 0.40,
                acoustic_contrast: "/v/ has high frequency friction turbulence; /w/ is a smooth voiced formant transition.",
                visual_clue: "/v/ requires upper teeth touching lower lip; /w/ requires tight lip rounding without teeth contact.",
            },
            ConfusionCandidate {
                confused_ipa: "b",
                penalty_weight: 0.35,
                acoustic_contrast: "/b/ is a stop with sudden burst; /v/ is a continuous fricative.",
                visual_clue: "/b/ uses bilabial closure; /v/ uses labiodental contact.",
            },
        ],
    ),
    (
        "w",
        &[
            ConfusionCandidate {
                confused_ipa: "v",
                penalty_weight: 0.40,
                acoustic_contrast: "/w/ lacks friction noise and has strong F1/F2 formant glides.",
                visual_clue: "Avoid upper teeth touching lower lip; purse lips tightly into an 'o' shape.",
            },
            ConfusionCandidate {
                confused_ipa: "uː",
                penalty_weight: 0.25,
                acoustic_contrast: "/w/ is a dynamic glide; /uː/ is a steady long vowel.",
                visual_clue: "Dynamic lip expansion from round to open for /w/.",
            },
        ],
    ),
    (
        "iː",
        &[
            ConfusionCandidate {
                confused_ipa: "ɪ",
                penalty_weight: 0.35,
                acoustic_contrast: "/iː/ is higher in pitch/F2 and held longer than lax /ɪ/.",
                visual_clue: "Corners of the mouth must pull outward in a wide smile for /iː/.",
            },
        ],
    ),
    (
        "i",
        &[
            ConfusionCandidate {
                confused_ipa: "ɪ",
                penalty_weight: 0.35,
                acoustic_contrast: "/iː/ is higher in pitch/F2 and held longer than lax /ɪ/.",
                visual_clue: "Corners of the mouth must pull outward in a wide smile for /iː/.",
            },
        ],
    ),
    (
        "e",
        &[
            ConfusionCandidate {
                confused_ipa: "æ",
                penalty_weight: 0.35,
                acoustic_contrast: "/e/ has lower F1 than /æ/; open jaw only ~1.5 fingers.",
                visual_clue: "Do not drop jaw as wide as /æ/.",
            },
            ConfusionCandidate {
                confused_ipa: "ɪ",
                penalty_weight: 0.25,
                acoustic_contrast: "/e/ is mid-front; /ɪ/ is near-close near-front.",
                visual_clue: "Slightly larger jaw opening for /e/ compared to /ɪ/.",
            },
        ],
    ),
    (
        "ɑː",
        &[
            ConfusionCandidate {
                confused_ipa: "ʌ",
                penalty_weight: 0.35,
                acoustic_contrast: "/ɑː/ has lower F2 and higher F1, fully open and sustained.",
                visual_clue: "Jaw opens to maximum aperture (2 fingers); flat relaxed tongue.",
            },
            ConfusionCandidate {
                confused_ipa: "ɔː",
                penalty_weight: 0.30,
                acoustic_contrast: "/ɑː/ is unrounded; /ɔː/ has moderate lip rounding.",
                visual_clue: "Lips must stay completely unrounded for /ɑː/.",
            },
        ],
    ),
    (
        "ɑ",
        &[
            ConfusionCandidate {
                confused_ipa: "ʌ",
                penalty_weight: 0.35,
                acoustic_contrast: "/ɑː/ has lower F2 and higher F1, fully open and sustained.",
                visual_clue: "Jaw opens to maximum aperture (2 fingers); flat relaxed tongue.",
            },
            ConfusionCandidate {
                confused_ipa: "ɔː",
                penalty_weight: 0.30,
                acoustic_contrast: "/ɑː/ is unrounded; /ɔː/ has moderate lip rounding.",
                visual_clue: "Lips must stay completely unrounded for /ɑː/.",
            },
        ],
    ),
    (
        "ɔː",
        &[
            ConfusionCandidate {
                confused_ipa: "ɑː",
                penalty_weight: 0.35,
                acoustic_contrast: "/ɔː/ has rounded lips lowering F2 and F3.",
                visual_clue: "Lips must form a distinct oval circle and protrude forward.",
            },
            ConfusionCandidate {
                confused_ipa: "oʊ",
                penalty_weight: 0.25,
                acoustic_contrast: "/ɔː/ is a steady monophthong; /oʊ/ glides toward high back.",
                visual_clue: "Maintain static lip shape throughout /ɔː/.",
            },
        ],
    ),
    (
        "uː",
        &[
            ConfusionCandidate {
                confused_ipa: "ʊ",
                penalty_weight: 0.35,
                acoustic_contrast: "/uː/ is tense, long, and has lowest F1/F2.",
                visual_clue: "Double lips tightly puckered into smallest round circle.",
            },
            ConfusionCandidate {
                confused_ipa: "oʊ",
                penalty_weight: 0.25,
                acoustic_contrast: "/uː/ maintains high back tongue; /oʊ/ starts mid.",
                visual_clue: "Jaw stays closely held; do not open wide.",
            },
        ],
    ),
    (
        "ʊ",
        &[
            ConfusionCandidate {
                confused_ipa: "uː",
                penalty_weight: 0.30,
                acoustic_contrast: "/ʊ/ is lax, shorter, and less rounded.",
                visual_clue: "Lips relaxed, slightly rounded without extreme pucker.",
            },
            ConfusionCandidate {
                confused_ipa: "ə",
                penalty_weight: 0.20,
                acoustic_contrast: "/ʊ/ has distinct back resonance compared to central /ə/.",
                visual_clue: "Slight lip rounding required for /ʊ/.",
            },
        ],
    ),
    (
        "ɜː",
        &[
            ConfusionCandidate {
                confused_ipa: "ə",
                penalty_weight: 0.25,
                acoustic_contrast: "/ɜː/ is fully stressed and sustained with rhotic lower F3 in AmE.",
                visual_clue: "Lips slightly flared; tongue body arched mid-high.",
            },
            ConfusionCandidate {
                confused_ipa: "ɔː",
                penalty_weight: 0.35,
                acoustic_contrast: "/ɜː/ is central; /ɔː/ is back rounded.",
                visual_clue: "Avoid round puckered lips.",
            },
        ],
    ),
    (
        "ə",
        &[
            ConfusionCandidate {
                confused_ipa: "ʌ",
                penalty_weight: 0.20,
                acoustic_contrast: "/ə/ is unstressed, reduced, very brief.",
                visual_clue: "Completely relaxed mouth; minimal jaw displacement.",
            },
        ],
    ),
    (
        "p",
        &[
            ConfusionCandidate {
                confused_ipa: "b",
                penalty_weight: 0.30,
                acoustic_contrast: "/p/ is voiceless with strong aspiration; /b/ is voiced.",
                visual_clue: "Both have bilabial closure; feel no throat vibration for /p/.",
            },
        ],
    ),
    (
        "b",
        &[
            ConfusionCandidate {
                confused_ipa: "p",
                penalty_weight: 0.30,
                acoustic_contrast: "/b/ is voiced stop with pre-voicing/voice bar; /p/ is aspirated.",
                visual_clue: "Throat vocal cords must buzz before lip release.",
            },
            ConfusionCandidate {
                confused_ipa: "v",
                penalty_weight: 0.35,
                acoustic_contrast: "/b/ is a plosive stop; /v/ is a fricative.",
                visual_clue: "Complete two-lip closure for /b/; do not use teeth on lip.",
            },
        ],
    ),
    (
        "t",
        &[
            ConfusionCandidate {
                confused_ipa: "d",
                penalty_weight: 0.30,
                acoustic_contrast: "/t/ is voiceless alveolar stop with strong burst; /d/ is voiced.",
                visual_clue: "Tongue tip firmly taps alveolar ridge behind upper front teeth.",
            },
            ConfusionCandidate {
                confused_ipa: "θ",
                penalty_weight: 0.35,
                acoustic_contrast: "/t/ is a stop; /θ/ is a continuous dental friction stream.",
                visual_clue: "Tongue must NOT stick out between teeth for /t/.",
            },
        ],
    ),
    (
        "d",
        &[
            ConfusionCandidate {
                confused_ipa: "t",
                penalty_weight: 0.30,
                acoustic_contrast: "/d/ is voiced; vocal cords vibrate during closure and release.",
                visual_clue: "Feel throat vibration.",
            },
            ConfusionCandidate {
                confused_ipa: "ð",
                penalty_weight: 0.35,
                acoustic_contrast: "/d/ is stop; /ð/ is continuous fricative.",
                visual_clue: "Tongue stays behind teeth on alveolar ridge for /d/.",
            },
        ],
    ),
    (
        "g",
        &[
            ConfusionCandidate {
                confused_ipa: "k",
                penalty_weight: 0.30,
                acoustic_contrast: "/g/ is voiced velar stop; vocal fold vibration during closure.",
                visual_clue: "Internal velar closure; throat must vibrate.",
            },
        ],
    ),
    (
        "s",
        &[
            ConfusionCandidate {
                confused_ipa: "θ",
                penalty_weight: 0.40,
                acoustic_contrast: "/s/ has intense high-frequency hiss (5-8kHz); /θ/ is low energy.",
                visual_clue: "Tongue must stay hidden behind teeth for /s/.",
            },
            ConfusionCandidate {
                confused_ipa: "ʃ",
                penalty_weight: 0.35,
                acoustic_contrast: "/s/ spectral peak > 5kHz; /ʃ/ peak is lower (2.5-4.5kHz).",
                visual_clue: "Corners of mouth slightly spread for /s/; lips flared for /ʃ/.",
            },
            ConfusionCandidate {
                confused_ipa: "z",
                penalty_weight: 0.25,
                acoustic_contrast: "/s/ is voiceless; /z/ is voiced.",
                visual_clue: "Vocal cords silent for /s/.",
            },
        ],
    ),
    (
        "z",
        &[
            ConfusionCandidate {
                confused_ipa: "s",
                penalty_weight: 0.30,
                acoustic_contrast: "/z/ is voiced with buzz; /s/ is voiceless hiss.",
                visual_clue: "Continuous buzzing throat vibration required for /z/.",
            },
            ConfusionCandidate {
                confused_ipa: "ð",
                penalty_weight: 0.35,
                acoustic_contrast: "/z/ is sibilant groove; /ð/ is dental friction.",
                visual_clue: "Tongue behind teeth, not between teeth.",
            },
        ],
    ),
    (
        "ʃ",
        &[
            ConfusionCandidate {
                confused_ipa: "s",
                penalty_weight: 0.35,
                acoustic_contrast: "/ʃ/ has broader mid-frequency turbulence (3-5kHz).",
                visual_clue: "Lips must flare forward into a trumpet bell shape for /ʃ/.",
            },
            ConfusionCandidate {
                confused_ipa: "tʃ",
                penalty_weight: 0.30,
                acoustic_contrast: "/ʃ/ is continuous; /tʃ/ has stop closure before friction.",
                visual_clue: "No stop burst before friction.",
            },
        ],
    ),
    (
        "ʒ",
        &[
            ConfusionCandidate {
                confused_ipa: "ʃ",
                penalty_weight: 0.30,
                acoustic_contrast: "/ʒ/ is voiced; vocal cords vibrate continuously.",
                visual_clue: "Flared lips + vocal fold buzz.",
            },
            ConfusionCandidate {
                confused_ipa: "dʒ",
                penalty_weight: 0.30,
                acoustic_contrast: "/ʒ/ is continuous; /dʒ/ has stop burst.",
                visual_clue: "No stop occlusion.",
            },
        ],
    ),
    (
        "tʃ",
        &[
            ConfusionCandidate {
                confused_ipa: "ʃ",
                penalty_weight: 0.35,
                acoustic_contrast: "/tʃ/ starts with silent occlusion stop before burst.",
                visual_clue: "Tongue tip must firmly seal alveolar ridge before releasing.",
            },
            ConfusionCandidate {
                confused_ipa: "dʒ",
                penalty_weight: 0.25,
                acoustic_contrast: "/tʃ/ is voiceless; /dʒ/ is voiced.",
                visual_clue: "No vocal cord vibration during release.",
            },
        ],
    ),
    (
        "dʒ",
        &[
            ConfusionCandidate {
                confused_ipa: "ʒ",
                penalty_weight: 0.30,
                acoustic_contrast: "/dʒ/ has distinct initial stop burst.",
                visual_clue: "Initial seal at alveolar ridge followed by voiced friction.",
            },
            ConfusionCandidate {
                confused_ipa: "tʃ",
                penalty_weight: 0.30,
                acoustic_contrast: "/dʒ/ is voiced throughout; /tʃ/ is voiceless.",
                visual_clue: "Throat vocal vibration must be sustained.",
            },
        ],
    ),
    (
        "h",
        &[
            ConfusionCandidate {
                confused_ipa: "<del>",
                penalty_weight: 0.40,
                acoustic_contrast: "/h/ requires continuous breathy glottal air stream.",
                visual_clue: "Mouth assumes shape of next vowel; exhale gently without throat scrape.",
            },
        ],
    ),
    (
        "m",
        &[
            ConfusionCandidate {
                confused_ipa: "n",
                penalty_weight: 0.35,
                acoustic_contrast: "/m/ has low frequency nasal murmur ~250Hz with bilabial anti-formant.",
                visual_clue: "Lips must be completely closed for /m/.",
            },
        ],
    ),
    (
        "n",
        &[
            ConfusionCandidate {
                confused_ipa: "ŋ",
                penalty_weight: 0.35,
                acoustic_contrast: "/n/ tongue tip seals alveolar ridge; /ŋ/ tongue back seals velum.",
                visual_clue: "Tongue tip contacts upper gum ridge; lips open.",
            },
            ConfusionCandidate {
                confused_ipa: "l",
                penalty_weight: 0.35,
                acoustic_contrast: "/n/ is nasal; /l/ is oral lateral sound.",
                visual_clue: "Air must exit nose; velum lowered.",
            },
        ],
    ),
    (
        "ŋ",
        &[
            ConfusionCandidate {
                confused_ipa: "n",
                penalty_weight: 0.40,
                acoustic_contrast: "/ŋ/ has anti-formant around 3000Hz; velar contact only.",
                visual_clue: "Do NOT let tongue tip touch front teeth or alveolar ridge.",
            },
            ConfusionCandidate {
                confused_ipa: "k",
                penalty_weight: 0.30,
                acoustic_contrast: "/ŋ/ is continuous voiced nasal murmur without oral burst.",
                visual_clue: "Continuous voiced nasal airflow.",
            },
        ],
    ),
    (
        "l",
        &[
            ConfusionCandidate {
                confused_ipa: "r",
                penalty_weight: 0.45,
                acoustic_contrast: "/l/ has high F3 ~2800Hz; /r/ has deeply dropped F3 < 2000Hz.",
                visual_clue: "Tongue tip firmly touches upper gum ridge; air flows around sides.",
            },
            ConfusionCandidate {
                confused_ipa: "n",
                penalty_weight: 0.30,
                acoustic_contrast: "/l/ is purely oral lateral; /n/ is nasal.",
                visual_clue: "No nasal resonance.",
            },
        ],
    ),
    (
        "j",
        &[
            ConfusionCandidate {
                confused_ipa: "dʒ",
                penalty_weight: 0.35,
                acoustic_contrast: "/j/ is a smooth palatal glide without friction or stop burst.",
                visual_clue: "Smooth glide; tongue does not contact palate abruptly.",
            },
        ],
    ),
    (
        "eɪ",
        &[
            ConfusionCandidate {
                confused_ipa: "e",
                penalty_weight: 0.30,
                acoustic_contrast: "/eɪ/ glides upward from mid to near-close /ɪ/.",
                visual_clue: "Jaw must glide upward and lips widen during diphthong.",
            },
        ],
    ),
    (
        "aɪ",
        &[
            ConfusionCandidate {
                confused_ipa: "ɑː",
                penalty_weight: 0.35,
                acoustic_contrast: "/aɪ/ has strong upward F2 trajectory toward high front.",
                visual_clue: "Jaw must close from wide open /a/ up toward /ɪ/.",
            },
        ],
    ),
    (
        "ɔɪ",
        &[
            ConfusionCandidate {
                confused_ipa: "ɔː",
                penalty_weight: 0.30,
                acoustic_contrast: "/ɔɪ/ glides from back rounded to front unrounded.",
                visual_clue: "Lips shift from round 'o' to spread smile.",
            },
        ],
    ),
    (
        "aʊ",
        &[
            ConfusionCandidate {
                confused_ipa: "ɑː",
                penalty_weight: 0.35,
                acoustic_contrast: "/aʊ/ glides from open to rounded near-back /ʊ/.",
                visual_clue: "Lips must close and pucker into a circle by end of sound.",
            },
        ],
    ),
    (
        "oʊ",
        &[
            ConfusionCandidate {
                confused_ipa: "ɔː",
                penalty_weight: 0.30,
                acoustic_contrast: "/oʊ/ glides from mid-back to rounded high-back /ʊ/.",
                visual_clue: "Lips tighten into smaller circle toward the end.",
            },
        ],
    ),
];

/// Retrieves known confusion candidates for a given target phoneme with symbol normalization.
pub fn get_confusion_candidates(target_ipa: &str) -> &'static [ConfusionCandidate] {
    let norm = crate::phoneme::inventory::normalize_phoneme_symbol(target_ipa);
    for (target, candidates) in COMMON_CONFUSIONS {
        if *target == norm || *target == target_ipa {
            return candidates;
        }
    }
    &[]
}

/// Checks whether a given candidate is a known critical confusion for the target.
pub fn is_known_confusion(target_ipa: &str, candidate_ipa: &str) -> bool {
    let norm_cand = crate::phoneme::inventory::normalize_phoneme_symbol(candidate_ipa);
    get_confusion_candidates(target_ipa).iter().any(|c| {
        c.confused_ipa == candidate_ipa
            || crate::phoneme::inventory::normalize_phoneme_symbol(c.confused_ipa) == norm_cand
    })
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

    #[test]
    fn test_v_w_confusion() {
        assert!(is_known_confusion("v", "w"));
        assert!(is_known_confusion("w", "v"));
    }

    #[test]
    fn test_theta_s_confusion() {
        assert!(is_known_confusion("θ", "s"));
    }
}
