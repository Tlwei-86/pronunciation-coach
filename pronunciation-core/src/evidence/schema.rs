use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct AudioEvidence {
    pub target_probability: f32,
    #[serde(default)]
    pub confusions: HashMap<String, f32>,
    pub duration_ms: u32,
    pub f1_hz: Option<f32>,
    pub f2_hz: Option<f32>,
    pub energy_rms: Option<f32>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct VisualEvidence {
    pub jaw_open: f32,
    pub lip_roundness: f32,
    pub mouth_width: Option<f32>,
    pub mouth_stretch: Option<f32>,
    pub lip_closure: Option<f32>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct AttemptContext {
    pub attempt_index: u32,
    pub previous_attempt_score: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct EvidenceJson {
    pub target_word: String,
    pub target_phoneme: String,
    pub audio: AudioEvidence,
    pub visual: VisualEvidence,
    pub context: Option<AttemptContext>,
}

impl EvidenceJson {
    pub fn new(
        target_word: &str,
        target_phoneme: &str,
        audio: AudioEvidence,
        visual: VisualEvidence,
    ) -> Self {
        Self {
            target_word: target_word.to_string(),
            target_phoneme: target_phoneme.to_string(),
            audio,
            visual,
            context: None,
        }
    }
}
