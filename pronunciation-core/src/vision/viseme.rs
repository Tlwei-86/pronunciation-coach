//! Viseme classifications, reference geometries, and visual alignment rules.

use crate::vision::mouth_geometry::MouthGeometry;
use serde::{Deserialize, Serialize};

/// High-level Viseme classification.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum VisemeClass {
    Bilabial,          // /p/, /b/, /m/
    Labiodental,       // /f/, /v/
    DentalInterdental, // /θ/, /ð/
    Alveolar,          // /t/, /d/, /s/, /z/, /n/, /l/
    PalatoAlveolar,    // /ʃ/, /ʒ/, /tʃ/, /dʒ/
    Velar,             // /k/, /g/, /ŋ/
    OpenVowel,         // /ɑ/, /æ/
    MidCentralVowel,   // /ʌ/, /ə/
    CloseFrontVowel,   // /i/, /ɪ/
    CloseBackVowel,    // /u/, /ʊ/
    Neutral,
}

/// Result of evaluating measured mouth geometry against phonetic targets.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct VisemeMatchResult {
    /// Similarity score [0.0, 1.0].
    pub score: f32,
    /// Significance/weight of visual evidence for this phoneme [0.0, 1.0].
    pub visual_weight: f32,
    /// Specific geometric deviations detected.
    pub detected_issues: Vec<String>,
}

/// Returns the ideal canonical reference mouth geometry for an IPA phoneme.
pub fn reference_geometry(phoneme: &str) -> MouthGeometry {
    match phoneme {
        // /ʌ/ caret: moderate jaw opening, neutral/flat lips, moderate width
        "ʌ" => MouthGeometry::new(0.42, 0.05, 0.50, 0.0, 0.20),
        // /ɑ/ open back: wide open jaw, low roundness, natural stretch
        "ɑ" => MouthGeometry::new(0.80, 0.10, 0.55, 0.0, 0.30),
        // /ə/ schwa: relaxed, minimal jaw opening
        "ə" => MouthGeometry::new(0.30, 0.05, 0.48, 0.0, 0.15),
        // /i/ fleece: high stretch/smile, narrow jaw opening
        "i" => MouthGeometry::new(0.18, 0.0, 0.65, 0.0, 0.75),
        // /u/ goose: high lip roundness/pucker, low jaw opening
        "u" => MouthGeometry::new(0.20, 0.85, 0.35, 0.0, 0.05),
        // /æ/ trap: wide jaw and wide mouth stretch
        "æ" => MouthGeometry::new(0.70, 0.05, 0.62, 0.0, 0.45),
        // /f/ /v/ labiodental: teeth-lip contact, moderate stretch, no full lip closure
        "f" | "v" => MouthGeometry::new(0.15, 0.05, 0.50, 0.25, 0.25),
        // /p/ /b/ /m/ bilabial: full lip closure
        "p" | "b" | "m" => MouthGeometry::new(0.02, 0.10, 0.50, 0.95, 0.10),
        // /k/ /g/ /ŋ/ velar: internal tongue body against soft palate (camera sees generic neutral mouth)
        "k" | "g" | "ŋ" => MouthGeometry::new(0.30, 0.05, 0.50, 0.05, 0.20),
        _ => MouthGeometry::default(),
    }
}

/// Determines the visual evidence weighting for a given phoneme.
///
/// Labiodental and bilabial consonants have high visual saliency.
/// Velar sounds (/k/, /g/, /ŋ/) have near-zero visual saliency.
pub fn phoneme_visual_weight(phoneme: &str) -> f32 {
    match phoneme {
        "f" | "v" => 0.35,
        "p" | "b" | "m" => 0.30,
        "θ" | "ð" => 0.30,
        "ʌ" | "ɑ" | "æ" => 0.25,
        "u" | "w" => 0.25,
        "i" => 0.20,
        // Velar consonants: spec explicitly mandates visual weight near zero
        "k" | "g" | "ŋ" => 0.05,
        _ => 0.15,
    }
}

/// Evaluates user's measured mouth geometry against target phoneme expectation.
pub fn evaluate_viseme(phoneme: &str, measured: &MouthGeometry) -> VisemeMatchResult {
    let ref_geom = reference_geometry(phoneme);
    let visual_weight = phoneme_visual_weight(phoneme);
    let mut issues = Vec::new();

    match phoneme {
        "ʌ" => {
            // /ʌ/ vs /ɑ/: Jaw opened too wide indicates shift to /ɑ/
            if measured.jaw_open > 0.65 {
                issues.push("Jaw opening is excessive for /ʌ/ (shifting toward /ɑ/)".to_string());
            } else if measured.jaw_open < 0.20 {
                issues.push("Jaw opening is too small for /ʌ/".to_string());
            }

            // Lip roundness check
            if measured.lip_roundness > 0.30 {
                issues.push("Lips are rounded; /ʌ/ requires relaxed unrounded lips".to_string());
            }
        }
        "ɑ" => {
            if measured.jaw_open < 0.55 {
                issues.push("Jaw is not open enough for open vowel /ɑ/".to_string());
            }
        }
        "f" => {
            if measured.lip_closure > 0.60 {
                issues.push("Lips are fully closed; /f/ requires upper teeth touching lower lip without bilabial closure".to_string());
            }
        }
        "p" | "b" | "m" => {
            if measured.lip_closure < 0.70 {
                issues.push("Incomplete bilabial lip closure for bilabial stop/nasal".to_string());
            }
        }
        "i" => {
            if measured.mouth_stretch < 0.40 {
                issues.push("Insufficient mouth stretch/smile for /i/".to_string());
            }
        }
        "u" if measured.lip_roundness < 0.50 => {
            issues.push("Lips should be tightly rounded for /u/".to_string());
        }
        _ => {}
    }

    // Normalized Euclidean distance penalty
    let dist = measured.distance_to(&ref_geom);
    let raw_score = (1.0 - dist / 1.5).clamp(0.0, 1.0);

    // Apply penalty for each explicit geometric issue
    let penalty = (issues.len() as f32 * 0.20).min(0.60);
    let final_score = (raw_score - penalty).clamp(0.0, 1.0);

    VisemeMatchResult {
        score: final_score,
        visual_weight,
        detected_issues: issues,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_caret_visual_evaluation() {
        // Ideal /ʌ/
        let good_caret = MouthGeometry::new(0.40, 0.05, 0.50, 0.0, 0.20);
        let res_good = evaluate_viseme("ʌ", &good_caret);
        assert!(res_good.detected_issues.is_empty());
        assert!(res_good.score > 0.80);

        // Caret shifting toward /ɑ/ (jaw_open = 0.75)
        let wide_caret = MouthGeometry::new(0.75, 0.05, 0.55, 0.0, 0.30);
        let res_wide = evaluate_viseme("ʌ", &wide_caret);
        assert!(!res_wide.detected_issues.is_empty());
        assert!(res_wide.detected_issues[0].contains("Jaw opening is excessive"));
        assert!(res_wide.score < res_good.score);
    }

    #[test]
    fn test_velar_visual_weight() {
        assert_eq!(phoneme_visual_weight("k"), 0.05);
        assert_eq!(phoneme_visual_weight("g"), 0.05);
        assert_eq!(phoneme_visual_weight("ŋ"), 0.05);
    }
}
