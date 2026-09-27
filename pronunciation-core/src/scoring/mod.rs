use crate::evidence::schema::EvidenceJson;
use crate::vision::get_visual_weight_for_phoneme;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhonemeScoreResult {
    pub phoneme: String,
    pub score: u32,
    pub acoustic_score: u32,
    pub visual_score: u32,
    pub is_primary_issue: bool,
    pub likely_confusion: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PronunciationReport {
    pub target_word: String,
    pub overall_score: u32,
    pub acoustic_accuracy: u32,
    pub visual_accuracy: u32,
    pub confidence: String,
    pub phoneme_scores: Vec<PhonemeScoreResult>,
    pub guidance: Vec<String>,
    pub next_exercise: String,
}

pub struct DeterministicScorer;

impl DeterministicScorer {
    pub fn score_phoneme(evidence: &EvidenceJson) -> PhonemeScoreResult {
        let phoneme = &evidence.target_phoneme;
        let v_weight = get_visual_weight_for_phoneme(phoneme);
        let a_weight = 1.0 - v_weight;
        
        let a_raw = (evidence.audio.target_probability * 100.0).clamp(0.0, 100.0);
        
        // Visual metric score based on phoneme characteristics
        let v_raw = match phoneme.as_str() {
            "ʌ" => {
                // For /ʌ/, jaw opening should be moderate (~0.4 - 0.6) and lips unrounded (< 0.25)
                let jaw_penalty = if evidence.visual.jaw_open > 0.65 {
                    (evidence.visual.jaw_open - 0.65) * 100.0
                } else {
                    0.0
                };
                let round_penalty = if evidence.visual.lip_roundness > 0.3 {
                    (evidence.visual.lip_roundness - 0.3) * 100.0
                } else {
                    0.0
                };
                (100.0 - jaw_penalty - round_penalty).clamp(10.0, 100.0)
            },
            "f" | "v" => {
                // Labiodental requires visible lip-to-teeth contact / closure
                if evidence.visual.jaw_open < 0.4 { 95.0 } else { 70.0 }
            },
            _ => 85.0,
        };
        
        let mut final_f32 = a_raw * a_weight + v_raw * v_weight;
        
        // Confusion penalty check
        let mut likely_confusion = None;
        if let Some((conf, &prob)) = evidence.audio.confusions.iter().max_by(|a, b| a.1.partial_cmp(b.1).unwrap()) {
            if prob > 0.25 {
                likely_confusion = Some(conf.clone());
                if prob > 0.40 {
                    final_f32 -= 10.0;
                }
            }
        }
        
        let final_score = (final_f32.round() as u32).clamp(0, 100);
        let is_primary = final_score < 75;
        
        PhonemeScoreResult {
            phoneme: phoneme.clone(),
            score: final_score,
            acoustic_score: a_raw.round() as u32,
            visual_score: v_raw.round() as u32,
            is_primary_issue: is_primary,
            likely_confusion,
        }
    }

    pub fn score_funk(target_prob: f32, conf_prob: f32, jaw_open: f32, lip_roundness: f32) -> PronunciationReport {
        use std::collections::HashMap;
        let mut confusions = HashMap::new();
        confusions.insert("ɑ".to_string(), conf_prob);
        
        let evidence_uh = EvidenceJson {
            target_word: "funk".to_string(),
            target_phoneme: "ʌ".to_string(),
            audio: crate::evidence::schema::AudioEvidence {
                target_probability: target_prob,
                confusions,
                duration_ms: 148,
                f1_hz: Some(710.0),
                f2_hz: Some(1180.0),
                energy_rms: Some(0.45),
            },
            visual: crate::evidence::schema::VisualEvidence {
                jaw_open,
                lip_roundness,
                mouth_width: Some(0.55),
                mouth_stretch: Some(0.30),
                lip_closure: Some(0.0),
            },
            context: None,
        };
        
        let score_uh = Self::score_phoneme(&evidence_uh);
        
        let score_f = PhonemeScoreResult {
            phoneme: "f".to_string(),
            score: 94,
            acoustic_score: 93,
            visual_score: 95,
            is_primary_issue: false,
            likely_confusion: None,
        };
        let score_ng = PhonemeScoreResult {
            phoneme: "ŋ".to_string(),
            score: 91,
            acoustic_score: 91,
            visual_score: 90,
            is_primary_issue: false,
            likely_confusion: None,
        };
        let score_k = PhonemeScoreResult {
            phoneme: "k".to_string(),
            score: 78,
            acoustic_score: 76,
            visual_score: 85,
            is_primary_issue: false,
            likely_confusion: None,
        };
        
        let phonemes = vec![score_f, score_uh.clone(), score_ng, score_k];
        let total: u32 = phonemes.iter().map(|p| p.score).sum();
        let overall = total / phonemes.len() as u32;
        
        let a_total: u32 = phonemes.iter().map(|p| p.acoustic_score).sum();
        let v_total: u32 = phonemes.iter().map(|p| p.visual_score).sum();
        
        let mut guidance = Vec::new();
        let next_exercise;
        
        if score_uh.is_primary_issue || score_uh.score < 80 {
            if jaw_open > 0.65 {
                guidance.push("Reduce jaw opening slightly: keep your mouth more relaxed, not wide open like /ɑ/.".to_string());
            } else {
                guidance.push("Keep the tongue more central and relaxed when vocalizing /ʌ/.".to_string());
            }
            guidance.push("Practice /ʌ/ alone before returning to the full word 'funk'.".to_string());
            next_exercise = "minimal_pair_ʌ_ɑ".to_string();
        } else {
            guidance.push("Great job! Your /ʌ/ vowel and ending consonant /k/ are distinct and accurate.".to_string());
            next_exercise = "sentence_rhythm_funk".to_string();
        }
        
        PronunciationReport {
            target_word: "funk".to_string(),
            overall_score: overall,
            acoustic_accuracy: a_total / phonemes.len() as u32,
            visual_accuracy: v_total / phonemes.len() as u32,
            confidence: "High".to_string(),
            phoneme_scores: phonemes,
            guidance,
            next_exercise,
        }
    }
}
