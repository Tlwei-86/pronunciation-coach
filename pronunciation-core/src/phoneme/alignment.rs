//! CTC Forced Alignment structures and segment boundary extraction.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// A single frame output from acoustic CTC model (typically 20ms / 50fps).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct CtcFrame {
    pub frame_index: usize,
    pub timestamp_ms: u32,
    /// Probability distribution over phoneme vocabulary.
    pub posteriors: Vec<(String, f32)>,
}

impl CtcFrame {
    /// Gets probability for a specific phoneme symbol.
    pub fn prob_of(&self, symbol: &str) -> f32 {
        self.posteriors
            .iter()
            .find(|(s, _)| s == symbol)
            .map(|(_, p)| *p)
            .unwrap_or(0.0)
    }
}

/// Aligned phoneme span with acoustic timing and probability metrics.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct AlignedPhoneme {
    /// Phoneme IPA symbol.
    pub phoneme: String,
    /// Start time in milliseconds.
    pub start_ms: u32,
    /// End time in milliseconds.
    pub end_ms: u32,
    /// Duration in milliseconds.
    pub duration_ms: u32,
    /// Average posterior probability over the aligned span.
    pub average_probability: f32,
    /// Top confusion phonemes and their average probabilities over the span.
    pub confusions: Vec<(String, f32)>,
}

/// Result of aligning an entire utterance.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct AlignmentResult {
    pub aligned_phonemes: Vec<AlignedPhoneme>,
    pub total_duration_ms: u32,
    pub average_confidence: f32,
}

/// Performs linear / Viterbi-like forced alignment of a target phoneme sequence onto CTC frames.
///
/// Each phoneme in `target_phonemes` is sequentially matched to contiguous frame slices
/// by maximizing the path cumulative likelihood.
pub fn align_ctc_sequence(
    frames: &[CtcFrame],
    target_phonemes: &[&str],
    frame_duration_ms: u32,
) -> AlignmentResult {
    if frames.is_empty() || target_phonemes.is_empty() {
        return AlignmentResult {
            aligned_phonemes: Vec::new(),
            total_duration_ms: 0,
            average_confidence: 0.0,
        };
    }

    let n_frames = frames.len();
    let n_phonemes = target_phonemes.len();

    // If frames are fewer than target phonemes, allocate 1 frame per phoneme as fallback
    let mut boundaries = Vec::with_capacity(n_phonemes + 1);

    if n_frames <= n_phonemes {
        for i in 0..=n_phonemes {
            boundaries.push(i.min(n_frames));
        }
    } else {
        // Dynamic programming alignment:
        // Find split points [b_0=0, b_1, ..., b_k = n_frames]
        // maximizing sum of average log likelihood per segment
        let min_segment_len = 1;
        boundaries.push(0);

        // Simple heuristic allocation guided by peak posteriors for each target
        let chunk_size = n_frames / n_phonemes;
        for i in 1..n_phonemes {
            let ideal_split = i * chunk_size;
            // Search around ideal_split for highest confidence transition
            let search_start = boundaries[i - 1] + min_segment_len;
            let search_end = (n_frames - (n_phonemes - i) * min_segment_len).min(ideal_split + 2);

            let mut best_split = search_start.max(ideal_split);
            let mut best_score = f32::MIN;

            for split in search_start..=search_end {
                let p_prev = frames[split - 1].prob_of(target_phonemes[i - 1]);
                let p_curr = frames[split.min(n_frames - 1)].prob_of(target_phonemes[i]);
                let score = p_prev + p_curr;
                if score > best_score {
                    best_score = score;
                    best_split = split;
                }
            }
            boundaries.push(best_split);
        }
        boundaries.push(n_frames);
    }

    let mut aligned = Vec::with_capacity(n_phonemes);
    let mut total_prob = 0.0f32;

    for i in 0..n_phonemes {
        let start_frame = boundaries[i];
        let end_frame = boundaries[i + 1].max(start_frame + 1).min(n_frames);
        let segment_frames = &frames[start_frame..end_frame];

        let target = target_phonemes[i];
        let mut sum_target_prob = 0.0f32;
        let mut confusion_sums: HashMap<String, f32> = HashMap::new();

        for f in segment_frames {
            sum_target_prob += f.prob_of(target);
            for (sym, p) in &f.posteriors {
                if sym != target && sym != "<pad>" && sym != "<sil>" {
                    *confusion_sums.entry(sym.clone()).or_insert(0.0) += *p;
                }
            }
        }

        let seg_len = segment_frames.len().max(1) as f32;
        let avg_target_prob = sum_target_prob / seg_len;
        total_prob += avg_target_prob;

        let mut confusions: Vec<(String, f32)> = confusion_sums
            .into_iter()
            .map(|(sym, sum_p)| (sym, sum_p / seg_len))
            .collect();
        confusions.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
        confusions.truncate(3);

        let start_ms = (start_frame as u32) * frame_duration_ms;
        let end_ms = (end_frame as u32) * frame_duration_ms;

        aligned.push(AlignedPhoneme {
            phoneme: target.to_string(),
            start_ms,
            end_ms,
            duration_ms: end_ms.saturating_sub(start_ms),
            average_probability: avg_target_prob,
            confusions,
        });
    }

    let total_duration_ms = (n_frames as u32) * frame_duration_ms;
    let avg_confidence = total_prob / (n_phonemes as f32);

    AlignmentResult {
        aligned_phonemes: aligned,
        total_duration_ms,
        average_confidence: avg_confidence,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ctc_alignment_funk() {
        let targets = ["f", "ʌ", "ŋ", "k"];
        // 8 frames, each 20ms => 160ms total
        let mut frames = Vec::new();
        for i in 0..8 {
            let active = if i < 2 {
                "f"
            } else if i < 4 {
                "ʌ"
            } else if i < 6 {
                "ŋ"
            } else {
                "k"
            };

            let posteriors = vec![
                (active.to_string(), 0.85),
                ("ɑ".to_string(), 0.10),
                ("<pad>".to_string(), 0.05),
            ];
            frames.push(CtcFrame {
                frame_index: i,
                timestamp_ms: (i * 20) as u32,
                posteriors,
            });
        }

        let res = align_ctc_sequence(&frames, &targets, 20);
        assert_eq!(res.aligned_phonemes.len(), 4);
        assert_eq!(res.aligned_phonemes[0].phoneme, "f");
        assert_eq!(res.aligned_phonemes[1].phoneme, "ʌ");
        assert_eq!(res.aligned_phonemes[2].phoneme, "ŋ");
        assert_eq!(res.aligned_phonemes[3].phoneme, "k");
        assert!(res.average_confidence > 0.70);
    }
}
