//! Local rule-based diagnosis generator and offline fallback policy.

use crate::evidence::schema::EvidenceJson;
use crate::policy::decision_types::{
    AccuracyGrade, DeepSeekDiagnosis, DiagnosisReport, JevDecision,
};
use crate::scoring::scorer::score_word;

/// Generates a complete rule-grounded diagnosis report from Evidence JSON.
pub fn evaluate_evidence_locally(evidence: &EvidenceJson) -> DiagnosisReport {
    // Score the target phoneme using the deterministic scorer
    let score_res = score_word(
        &evidence.target_word,
        &[(&evidence.target_phoneme, &evidence.audio, &evidence.visual)],
    );

    let target_score = score_res
        .phoneme_scores
        .first()
        .map(|p| p.score)
        .unwrap_or(score_res.overall_score);

    let accuracy = AccuracyGrade::from_score(target_score);

    // Evaluate primary confusion
    let top_confusion = evidence
        .audio
        .confusions
        .iter()
        .max_by(|a, b| a.1.partial_cmp(b.1).unwrap_or(std::cmp::Ordering::Equal));

    let likely_confusion = top_confusion.and_then(|(sym, &p)| {
        if p >= 0.15 {
            Some(sym.clone())
        } else {
            None
        }
    });

    // Rule-based diagnostic deduction
    let (diagnosis, needs_repeat, jev_confidence) = match (
        evidence.target_phoneme.as_str(),
        likely_confusion.as_deref(),
        evidence.visual.jaw_open,
    ) {
        // Rule 1: /ʌ/ shifted to /ɑ/ (excessive jaw opening or acoustic confusion)
        ("ʌ", conf, jaw) if (conf == Some("ɑ") || jaw > 0.65) && target_score < 78 => {
            (
                DeepSeekDiagnosis {
                    primary_issue: "vowel_quality".to_string(),
                    likely_confusion: Some("ɑ".to_string()),
                    cause: "The vowel is too open and shifts toward /ɑ/.".to_string(),
                    visual_support: "Jaw opening is larger than desired.".to_string(),
                    correction: vec![
                        "Reduce jaw opening slightly".to_string(),
                        "Keep the tongue more central and relaxed".to_string(),
                        "Practice /ʌ/ alone before returning to the word".to_string(),
                    ],
                    next_exercise: "minimal_pair_ʌ_ɑ".to_string(),
                },
                false,
                0.88f32,
            )
        }
        // Rule 2: Final velar stop /k/ weak
        ("k", _, _) if target_score < 75 => (
            DeepSeekDiagnosis {
                primary_issue: "final_consonant_release".to_string(),
                likely_confusion: Some("<del>".to_string()),
                cause: "The final stop /k/ lacks a clean explosive release.".to_string(),
                visual_support: "Internal velar articulation.".to_string(),
                correction: vec![
                    "Press the back of your tongue firmly against soft palate".to_string(),
                    "Release with an audible voiceless burst".to_string(),
                ],
                next_exercise: "practice_final_k".to_string(),
            },
            false,
            0.85f32,
        ),
        // Rule 3: Labiodental /f/ weak
        ("f", _, _) if target_score < 75 => (
            DeepSeekDiagnosis {
                primary_issue: "labiodental_friction".to_string(),
                likely_confusion: likely_confusion.clone(),
                cause: "Continuous frication stream is inconsistent or weak.".to_string(),
                visual_support: "Ensure upper incisors lightly contact lower lip.".to_string(),
                correction: vec![
                    "Place upper front teeth on inside edge of lower lip".to_string(),
                    "Blow air steadily through the gap without closing lips entirely".to_string(),
                ],
                next_exercise: "practice_initial_f".to_string(),
            },
            false,
            0.82f32,
        ),
        // Rule 4: Native-like articulation
        _ if target_score >= 85 => (
            DeepSeekDiagnosis {
                primary_issue: "none".to_string(),
                likely_confusion: None,
                cause: "Pronunciation is clear, accurate, and natural.".to_string(),
                visual_support: "Mouth geometry matches target viseme perfectly.".to_string(),
                correction: vec!["Great job! Maintain this articulatory shape.".to_string()],
                next_exercise: format!("advance_word_{}", evidence.target_word),
            },
            false,
            0.95f32,
        ),
        // Rule 5: Generic deviation
        _ => {
            let req_repeat = target_score < 45;
            (
                DeepSeekDiagnosis {
                    primary_issue: "articulatory_precision".to_string(),
                    likely_confusion: likely_confusion.clone(),
                    cause: format!("Pronunciation deviated slightly from target /{}/.", evidence.target_phoneme),
                    visual_support: format!("Mouth opening: {:.2}.", evidence.visual.jaw_open),
                    correction: vec![
                        format!("Listen closely to target phoneme /{}/", evidence.target_phoneme),
                        "Focus on matching mouth opening and tongue height".to_string(),
                    ],
                    next_exercise: format!("drill_{}", evidence.target_phoneme),
                },
                req_repeat,
                0.70f32,
            )
        }
    };

    // DeepSeek routing principle (Section 10 of spec):
    // If Jev confidence >= 0.90 and clean -> local policy handles it directly (route_to_deepseek = false).
    // Otherwise -> route to DeepSeek AI Coach for rich explanation.
    let route_to_deepseek = jev_confidence < 0.90 || target_score < 75;

    let jev_decision = JevDecision {
        accuracy,
        likely_confusion,
        needs_repeat,
        confidence: jev_confidence,
        route_to_deepseek,
    };

    DiagnosisReport {
        target_word: evidence.target_word.clone(),
        score_result: score_res,
        jev_decision,
        diagnosis,
        request_repeat: needs_repeat,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    #[test]
    fn test_local_diagnosis_caret_shift_to_alpha() {
        let mut conf = HashMap::new();
        conf.insert("ɑ".to_string(), 0.31);
        conf.insert("ə".to_string(), 0.07);

        let ev = EvidenceJson {
            target_word: "funk".to_string(),
            target_phoneme: "ʌ".to_string(),
            audio: crate::evidence::schema::AudioEvidence {
                target_probability: 0.57,
                confusions: conf,
                duration_ms: 148,
                f1_hz: Some(710.0),
                f2_hz: Some(1180.0),
                energy_rms: None,
            },
            visual: crate::evidence::schema::VisualEvidence {
                jaw_open: 0.71,
                lip_roundness: 0.08,
                mouth_stretch: Some(0.34),
                mouth_width: Some(0.59),
                lip_closure: None,
            },
            context: None,
        };

        let report = evaluate_evidence_locally(&ev);
        assert_eq!(report.diagnosis.primary_issue, "vowel_quality");
        assert_eq!(report.diagnosis.likely_confusion.as_deref(), Some("ɑ"));
        assert_eq!(report.diagnosis.next_exercise, "minimal_pair_ʌ_ɑ");
        assert!(report.diagnosis.cause.contains("shifts toward /ɑ/"));
        assert!(report.diagnosis.visual_support.contains("Jaw opening"));
        assert!(report.jev_decision.route_to_deepseek);
    }
}
