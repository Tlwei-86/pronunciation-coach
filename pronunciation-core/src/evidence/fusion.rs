//! Multimodal fusion combining acoustic evidence and visual mouth geometry.

use crate::evidence::schema::{AudioEvidence, VisualEvidence};
use crate::vision::mouth_geometry::MouthGeometry;
use crate::vision::viseme::{evaluate_viseme, phoneme_visual_weight};
use serde::{Deserialize, Serialize};

/// Congruence state between acoustic and visual signals.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum MultimodalCongruence {
    /// Both audio and video indicate target pronunciation is correct.
    CorroboratedPass,
    /// Both audio and video consistently identify the same error (e.g. wide jaw + /ɑ/ posterior).
    CorroboratedDeviation,
    /// Audio indicates error, but video looks acceptable (or visual saliency is negligible).
    AcousticOnlyDeviation,
    /// Audio indicates correct phoneme, but mouth shape violates target constraints.
    VisualConflict,
    /// Low confidence in both channels.
    Ambiguous,
}

/// Consolidated multi-modal assessment for a phoneme.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct FusedPhonemeEvidence {
    pub phoneme: String,
    pub acoustic_quality: f32,
    pub visual_quality: f32,
    pub visual_weight: f32,
    pub fused_quality: f32,
    pub congruence: MultimodalCongruence,
    pub primary_confusion: Option<String>,
    pub visual_issues: Vec<String>,
}

/// Fuses audio evidence and visual evidence for a specific phoneme.
pub fn fuse_evidence(
    phoneme: &str,
    audio: &AudioEvidence,
    visual: &VisualEvidence,
) -> FusedPhonemeEvidence {
    // 1. Acoustic Quality [0.0, 1.0]
    let top_confusion = audio
        .confusions
        .iter()
        .max_by(|a, b| a.1.partial_cmp(b.1).unwrap_or(std::cmp::Ordering::Equal));

    let top_confusion_prob = top_confusion.map(|(_, &p)| p).unwrap_or(0.0);
    let top_confusion_sym = top_confusion.map(|(sym, _)| sym.clone());

    // Acoustic quality reflects target posterior minus confusion penalty
    let margin = audio.target_probability - top_confusion_prob;
    let acoustic_quality = if margin >= 0.0 {
        (audio.target_probability * 0.7 + (margin * 0.3)).clamp(0.0, 1.0)
    } else {
        (audio.target_probability * 0.8).clamp(0.0, 1.0)
    };

    // 2. Visual Quality [0.0, 1.0]
    let mouth_geom = MouthGeometry::new(
        visual.jaw_open,
        visual.lip_roundness,
        visual.mouth_width.unwrap_or(0.50),
        visual.lip_closure.unwrap_or(0.0),
        visual.mouth_stretch,
    );

    let viseme_eval = evaluate_viseme(phoneme, &mouth_geom);
    let visual_quality = viseme_eval.score;
    let visual_weight = phoneme_visual_weight(phoneme);
    let audio_weight = 1.0 - visual_weight;

    let fused_quality = acoustic_quality * audio_weight + visual_quality * visual_weight;

    // 3. Multimodal Congruence Determination
    let is_audio_pass = acoustic_quality >= 0.70;
    let is_visual_pass = visual_quality >= 0.70;

    let congruence = if is_audio_pass && is_visual_pass {
        MultimodalCongruence::CorroboratedPass
    } else if !is_audio_pass && !is_visual_pass {
        MultimodalCongruence::CorroboratedDeviation
    } else if !is_audio_pass && is_visual_pass {
        MultimodalCongruence::AcousticOnlyDeviation
    } else if is_audio_pass && !is_visual_pass {
        if visual_weight > 0.15 {
            MultimodalCongruence::VisualConflict
        } else {
            // If visual weight is near 0 (like /k/), disregard visual violation
            MultimodalCongruence::CorroboratedPass
        }
    } else {
        MultimodalCongruence::Ambiguous
    };

    FusedPhonemeEvidence {
        phoneme: phoneme.to_string(),
        acoustic_quality,
        visual_quality,
        visual_weight,
        fused_quality,
        congruence,
        primary_confusion: top_confusion_sym,
        visual_issues: viseme_eval.detected_issues,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::BTreeMap;

    #[test]
    fn test_fusion_caret_corroborated_deviation() {
        let mut confusions = BTreeMap::new();
        confusions.insert("ɑ".to_string(), 0.38);

        let audio = AudioEvidence {
            target_probability: 0.50,
            confusions,
            duration_ms: 150,
            f1_hz: Some(720.0),
            f2_hz: Some(1190.0),
            pitch_hz: None,
            energy: None,
        };

        let visual = VisualEvidence {
            jaw_open: 0.75, // excessive jaw opening for /ʌ/
            lip_roundness: 0.08,
            mouth_stretch: 0.30,
            mouth_width: Some(0.55),
            lip_closure: None,
        };

        let fused = fuse_evidence("ʌ", &audio, &visual);
        assert_eq!(fused.congruence, MultimodalCongruence::CorroboratedDeviation);
        assert_eq!(fused.primary_confusion.as_deref(), Some("ɑ"));
        assert!(!fused.visual_issues.is_empty());
    }

    #[test]
    fn test_fusion_velar_ignores_visual_conflict() {
        let audio = AudioEvidence {
            target_probability: 0.90,
            confusions: BTreeMap::new(),
            duration_ms: 60,
            f1_hz: None,
            f2_hz: None,
            pitch_hz: None,
            energy: None,
        };

        // Even if jaw is open or closed, velar visual weight is low (0.05)
        let visual = VisualEvidence {
            jaw_open: 0.85,
            lip_roundness: 0.50,
            mouth_stretch: 0.10,
            mouth_width: None,
            lip_closure: None,
        };

        let fused = fuse_evidence("k", &audio, &visual);
        assert_eq!(fused.congruence, MultimodalCongruence::CorroboratedPass);
        assert!(fused.fused_quality > 0.80);
    }
}
