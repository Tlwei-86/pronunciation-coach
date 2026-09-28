pub mod acoustic;
pub mod alignment;
pub mod articulatory;
pub mod common;
pub mod scoring;

pub use acoustic::classifier::{ConfidenceScore, VowelClassifier};
pub use acoustic::detector::{PlosiveBurstDetector, PlosiveClass};
pub use acoustic::formant::{FormantF1F2, FormantTracker};
pub use acoustic::fricative::{FricativeClassifier, FricativeType};
pub use acoustic::voicing::{PitchTracker, VoicingDetector};
pub use alignment::temporal_aligner::{AlignmentConfig, PhonemeBoundary, TemporalAligner};
pub use articulatory::articulatory_coach::{
    ArticulatoryCoach, ArticulatoryCorrection, DynamicCue, GeometryCue, JawDistance,
    LipApertureState, LipSpreadState, MinimalPairRecommendation, PhonemeArticulatoryProfile,
    PhysicalActionCategory, SensationFeedback, TonguePosition, TongueShape,
};
pub use articulatory::coaching_dictionary::{CoachingDictionary, WordPhonemeProfile};
pub use common::error::AcousticError;
pub use common::types::{AcousticFeatureFrame, AudioBuffer16k, Phoneme};
pub use scoring::decision_tree::{DecisionTreeEngine, DiagnosticOutput, RuleResult};
pub use scoring::scorer::{
    AcousticFeaturesDto, ArticulatoryDeviationDto, FeedbackPayloadDto, PhonemeScoreDto,
    PhonemeTimingDto, PhysicalCuesDto, PronunciationScore, ScoreBreakdownDto, WordScoreDto,
};

#[cfg(test)]
mod tests {
    use super::scoring::evidence::{
        AcousticEvidence, ArticulatoryEvidence, ArticulatoryStateEvidence, EvidenceJson,
        FormantsEvidence, PitchVoicingEvidence, RuleEvaluationEvidence,
    };

    #[test]
    fn test_evidence_json_roundtrip() {
        let ev = EvidenceJson {
            case_id: "TC-01".to_string(),
            audio_source: "synth".to_string(),
            sample_rate: 16000,
            duration_ms: 600.0,
            acoustic: AcousticEvidence {
                energy_rms: 0.12,
                spectral_centroid: 2450.0,
                zero_crossing_rate: 0.08,
                snr_db: 28.5,
            },
            formants: FormantsEvidence {
                f1_hz: 650.0,
                f2_hz: 1200.0,
                f3_hz: 2500.0,
                bandwidth_f1: 80.0,
                bandwidth_f2: 110.0,
            },
            pitch_voicing: PitchVoicingEvidence {
                mean_pitch_hz: 120.0,
                voicing_ratio: 0.85,
                jitter: 0.012,
                shimmer: 0.035,
            },
            articulatory: ArticulatoryEvidence {
                lip_rounding: 0.2,
                lip_aperture: 0.65,
                tongue_advancement: 0.5,
                tongue_height: 0.4,
                jaw_open: 0.6,
            },
            rules_triggered: vec![RuleEvaluationEvidence {
                rule_name: "test_rule".to_string(),
                condition: "f1 > 600".to_string(),
                passed: true,
                deduction: 0.0,
            }],
            overall_score: 95.0,
            visual_evidence: None,
            cross_modal: None,
        };

        let s = serde_json::to_string(&ev).unwrap();
        let decoded: EvidenceJson = serde_json::from_str(&s).unwrap();
        assert_eq!(ev, decoded);
    }
}

use crate::scoring::scorer::analyze_word_pronunciation_json;
use std::ffi::{CStr, CString};
use std::os::raw::c_char;

#[no_mangle]
pub extern "C" fn core_analyze_word_json(input: *const c_char) -> *mut c_char {
    if input.is_null() {
        return std::ptr::null_mut();
    }
    let c_str = unsafe { CStr::from_ptr(input) };
    let r_str = match c_str.to_str() {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    let output = analyze_word_pronunciation_json(r_str);
    match CString::new(output) {
        Ok(cs) => cs.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "C" fn core_free_string(ptr: *mut c_char) {
    if !ptr.is_null() {
        unsafe {
            let _ = CString::from_raw(ptr);
        }
    }
}
