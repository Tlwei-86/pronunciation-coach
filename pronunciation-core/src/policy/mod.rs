use crate::evidence::schema::EvidenceJson;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JevDecision {
    pub accuracy_tier: String,
    pub likely_confusion: Option<String>,
    pub needs_repeat: bool,
    pub confidence: f32,
}

pub fn local_rule_evaluate(evidence: &EvidenceJson) -> JevDecision {
    let p = evidence.audio.target_probability;
    if p >= 0.85 {
        JevDecision {
            accuracy_tier: "native_like".to_string(),
            likely_confusion: None,
            needs_repeat: false,
            confidence: 0.95,
        }
    } else if p >= 0.65 {
        JevDecision {
            accuracy_tier: "minor_deviation".to_string(),
            likely_confusion: evidence.audio.confusions.keys().next().cloned(),
            needs_repeat: false,
            confidence: 0.80,
        }
    } else {
        JevDecision {
            accuracy_tier: "noticeable_deviation".to_string(),
            likely_confusion: evidence.audio.confusions.keys().next().cloned(),
            needs_repeat: true,
            confidence: 0.85,
        }
    }
}
