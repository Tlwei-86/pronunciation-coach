//! Structured policy and diagnosis data contracts.

use crate::scoring::scorer::WordScoreResult;
use serde::{Deserialize, Serialize};

/// Pronunciation accuracy grade.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum AccuracyGrade {
    NativeLike,
    MinorDeviation,
    NoticeableDeviation,
    MajorDeviation,
    Incorrect,
}

impl AccuracyGrade {
    pub fn from_score(score: u32) -> Self {
        match score {
            90..=100 => Self::NativeLike,
            78..=89 => Self::MinorDeviation,
            60..=77 => Self::NoticeableDeviation,
            40..=59 => Self::MajorDeviation,
            _ => Self::Incorrect,
        }
    }
}

/// Jev Fast Judgment output schema (Section 8 of spec).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct JevDecision {
    /// Categorical accuracy judgment.
    pub accuracy: AccuracyGrade,
    /// Detected confusion phoneme if any.
    pub likely_confusion: Option<String>,
    /// Whether immediate repetition is advised.
    pub needs_repeat: bool,
    /// Fast decision confidence [0.0, 1.0].
    pub confidence: f32,
    /// Whether execution should route to DeepSeek AI Coach.
    pub route_to_deepseek: bool,
}

/// DeepSeek AI Coach structured diagnosis schema (Section 9 of spec).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct DeepSeekDiagnosis {
    /// Root cause category (e.g. "vowel_quality", "final_consonant_release").
    pub primary_issue: String,
    /// Most likely confused phoneme.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub likely_confusion: Option<String>,
    /// Evidence-grounded explanation of the pronunciation error.
    pub cause: String,
    /// Specific observation from mouth landmarks.
    pub visual_support: String,
    /// Concrete, actionable adjustment steps.
    pub correction: Vec<String>,
    /// Prescribed next drill or minimal pair.
    pub next_exercise: String,
}

/// Complete coaching output packaging scoring, fast judgment, and diagnosis.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct DiagnosisReport {
    pub target_word: String,
    pub score_result: WordScoreResult,
    pub jev_decision: JevDecision,
    pub diagnosis: DeepSeekDiagnosis,
    pub request_repeat: bool,
}
