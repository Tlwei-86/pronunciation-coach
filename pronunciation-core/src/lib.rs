pub mod audio;
pub mod evidence;
pub mod jni;
pub mod phoneme;
pub mod policy;
pub mod scoring;
pub mod vision;

pub const VERSION: &str = env!("CARGO_PKG_VERSION");

#[cfg(test)]
mod tests {
    use super::*;
    use crate::evidence::schema::{AudioEvidence, EvidenceJson, VisualEvidence};
    use crate::scoring::DeterministicScorer;
    use std::collections::HashMap;

    #[test]
    fn test_core_version() {
        assert_eq!(VERSION, "0.1.0");
    }

    #[test]
    fn test_funk_scoring_good() {
        let report = DeterministicScorer::score_funk(0.92, 0.05, 0.45, 0.10);
        assert!(report.overall_score >= 85);
        assert_eq!(report.confidence, "High");
        assert_eq!(report.phoneme_scores.len(), 4);
    }

    #[test]
    fn test_funk_scoring_ah_confusion() {
        // High jaw opening (>0.65) and strong confusion with /ɑ/ (0.42)
        let report = DeterministicScorer::score_funk(0.48, 0.42, 0.72, 0.08);
        assert!(report.overall_score < 80);
        let uh = report
            .phoneme_scores
            .iter()
            .find(|p| p.phoneme == "ʌ")
            .unwrap();
        assert!(uh.is_primary_issue);
        assert_eq!(uh.likely_confusion.as_deref(), Some("ɑ"));
        assert_eq!(report.next_exercise, "minimal_pair_ʌ_ɑ");
    }

    #[test]
    fn test_evidence_json_roundtrip() {
        let mut confs = HashMap::new();
        confs.insert("ɑ".to_string(), 0.31);
        let ev = EvidenceJson {
            target_word: "funk".to_string(),
            target_phoneme: "ʌ".to_string(),
            audio: AudioEvidence {
                target_probability: 0.57,
                confusions: confs,
                duration_ms: 148,
                f1_hz: Some(710.0),
                f2_hz: Some(1180.0),
                energy_rms: Some(0.40),
            },
            visual: VisualEvidence {
                jaw_open: 0.71,
                lip_roundness: 0.08,
                mouth_width: Some(0.59),
                mouth_stretch: Some(0.34),
                lip_closure: Some(0.03),
            },
            context: None,
        };
        let s = serde_json::to_string(&ev).unwrap();
        let decoded: EvidenceJson = serde_json::from_str(&s).unwrap();
        assert_eq!(ev, decoded);
    }
}
