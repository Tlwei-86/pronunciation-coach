//! Phase 3 Acoustic-to-Articulatory Tongue Position Inversion.
//!
//! Reconstructs hidden tongue physical states (height, backness) from
//! acoustic formants (F1, F2) decoupled with visual mouth metrics (JawOpen, LipRoundness).
//! Formulations and rules match RFC Sections 6.1-6.3.

use serde::{Deserialize, Serialize};

pub const ACTION_PERFECT: u16 = 0;
pub const ACTION_TONGUE_RETRACTED: u16 = 1;
pub const ACTION_TONGUE_TOO_FRONT: u16 = 2;
pub const ACTION_JAW_TONGUE_LOW: u16 = 3;
pub const ACTION_TONGUE_TOO_HIGH: u16 = 4;

/// Physical tongue position and articulatory guidance metrics.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct TonguePositionMetrics {
    /// Inferred vertical tongue height [0.0, 1.0], 1.0 = high (/iː/), 0.0 = low (/ɑː/).
    pub tongue_height: f32,
    /// Inferred horizontal tongue backness/frontness [0.0, 1.0], 1.0 = front, 0.0 = back.
    pub tongue_backness: f32,
    /// Compliance score of inferred tongue position [0, 100].
    pub tongue_score: u32,
    /// Human-readable articulatory guidance matching RFC Section 6.3.
    pub articulatory_guidance: String,
    /// Embodied physiological action code for 3D avatar / UI rendering.
    pub action_code: u16,
}

/// Evaluates tongue position from acoustic formants and visual observables.
///
/// Formulas:
/// - height = 1.0 - (((f1 - 250.0)/650.0)*0.65 + jaw_open*0.35).clamp(0.0, 1.0)
/// - backness = (((f2 - 800.0)/1400.0)*0.70 + (1.0 - lip_roundness)*0.30).clamp(0.0, 1.0)
pub fn evaluate_tongue_position(
    f1: f32,
    f2: f32,
    jaw_open: f32,
    lip_roundness: f32,
) -> TonguePositionMetrics {
    let height_term = ((f1 - 250.0) / 650.0) * 0.65 + jaw_open * 0.35;
    let tongue_height = (1.0 - height_term.clamp(0.0, 1.0)).clamp(0.0, 1.0);

    let backness_term = ((f2 - 800.0) / 1400.0) * 0.70 + (1.0 - lip_roundness) * 0.30;
    let tongue_backness = backness_term.clamp(0.0, 1.0);

    // Threshold evaluation based on RFC Section 6.3:
    // - backness < 0.35: 舌身过度后缩 (ACTION_TONGUE_RETRACTED)
    // - backness > 0.65: 舌位过度靠前 (ACTION_TONGUE_TOO_FRONT)
    // - height < 0.30: 舌位过度下沉压低 (ACTION_JAW_TONGUE_LOW)
    // - height > 0.70: 舌位过高 (ACTION_TONGUE_TOO_HIGH)
    let dev_h_low = if tongue_height < 0.30 {
        0.30 - tongue_height
    } else {
        0.0
    };
    let dev_h_high = if tongue_height > 0.70 {
        tongue_height - 0.70
    } else {
        0.0
    };
    let dev_b_retracted = if tongue_backness < 0.35 {
        0.35 - tongue_backness
    } else {
        0.0
    };
    let dev_b_fronted = if tongue_backness > 0.65 {
        tongue_backness - 0.65
    } else {
        0.0
    };

    let (action_code, articulatory_guidance) =
        if dev_h_low > 0.0 || dev_h_high > 0.0 || dev_b_retracted > 0.0 || dev_b_fronted > 0.0 {
            // Select most severe deviation
            let mut max_dev = dev_h_low;
            let mut code = ACTION_JAW_TONGUE_LOW;
            let mut guidance = "舌头压得过低且下巴过松，请稍微收起舌底，舌尖轻触下齿龈。";

            if dev_h_high > max_dev {
                max_dev = dev_h_high;
                code = ACTION_TONGUE_TOO_HIGH;
                guidance = "舌面抬得过高，声音含在口中，请略微下压舌中部，释放气流。";
            }
            if dev_b_retracted > max_dev {
                max_dev = dev_b_retracted;
                code = ACTION_TONGUE_RETRACTED;
                guidance = "口型虽然合适，但舌根过度向咽壁收缩，请将舌面稍微向前平移放松。";
            }
            if dev_b_fronted > max_dev {
                code = ACTION_TONGUE_TOO_FRONT;
                guidance = "舌头向前拱起过多，请放松舌身，让舌面保持在口腔正中央。";
            }
            (code, guidance.to_string())
        } else {
            (
                ACTION_PERFECT,
                "完美发音！口型适中，舌位保持在标准半低央位置。".to_string(),
            )
        };

    // Calculate compliance score [0, 100]
    // Ideal range for /ʌ/: height in [0.45, 0.60], backness in [0.40, 0.55]
    let err_h = if tongue_height < 0.45 {
        0.45 - tongue_height
    } else if tongue_height > 0.60 {
        tongue_height - 0.60
    } else {
        0.0
    };

    let err_b = if tongue_backness < 0.40 {
        0.40 - tongue_backness
    } else if tongue_backness > 0.55 {
        tongue_backness - 0.55
    } else {
        0.0
    };

    let total_err = err_h + err_b;
    let tongue_score = if action_code == ACTION_PERFECT {
        let dist_center =
            ((tongue_height - 0.525).powi(2) + (tongue_backness - 0.475).powi(2)).sqrt();
        let score = 100.0 - dist_center * 60.0;
        score.round().clamp(88.0, 100.0) as u32
    } else {
        let score = 75.0 - (total_err * 180.0);
        score.round().clamp(10.0, 68.0) as u32
    };

    TonguePositionMetrics {
        tongue_height,
        tongue_backness,
        tongue_score,
        articulatory_guidance,
        action_code,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_tongue_standard_position() {
        // Benchmark standard input matching RFC Section 6.2/6.3 & Verification Plan 3.2
        let metrics = evaluate_tongue_position(600.0, 1200.0, 0.24, 0.42);
        assert!(
            metrics.tongue_height >= 0.45 && metrics.tongue_height <= 0.58,
            "Expected height in [0.45, 0.58], got {}",
            metrics.tongue_height
        );
        assert!(
            metrics.tongue_backness >= 0.35 && metrics.tongue_backness <= 0.55,
            "Expected backness in [0.35, 0.55], got {}",
            metrics.tongue_backness
        );
        assert_eq!(metrics.action_code, ACTION_PERFECT);
        assert!(
            metrics.tongue_score >= 88,
            "Expected tongue score >= 88, got {}",
            metrics.tongue_score
        );
        assert!(metrics.articulatory_guidance.contains("完美发音"));
    }

    #[test]
    fn test_tongue_retracted_position() {
        // F2 is low, tongue root retracted toward pharynx wall
        let metrics = evaluate_tongue_position(600.0, 950.0, 0.24, 0.42);
        assert!(
            metrics.tongue_backness < 0.35,
            "Expected backness < 0.35, got {}",
            metrics.tongue_backness
        );
        assert_eq!(metrics.action_code, ACTION_TONGUE_RETRACTED);
        assert!(
            metrics.tongue_score < 60,
            "Expected tongue score < 60, got {}",
            metrics.tongue_score
        );
        assert!(metrics.articulatory_guidance.contains("舌根过度向咽壁收缩"));
    }

    #[test]
    fn test_tongue_fronted_position() {
        // High F2, tongue arched too far forward
        let metrics = evaluate_tongue_position(600.0, 1800.0, 0.24, 0.10);
        assert!(
            metrics.tongue_backness > 0.65,
            "Expected backness > 0.65, got {}",
            metrics.tongue_backness
        );
        assert_eq!(metrics.action_code, ACTION_TONGUE_TOO_FRONT);
        assert!(
            metrics.tongue_score < 60,
            "Expected tongue score < 60, got {}",
            metrics.tongue_score
        );
        assert!(metrics.articulatory_guidance.contains("舌头向前拱起过多"));
    }

    #[test]
    fn test_tongue_flattened_low_position() {
        // High F1 and wide jaw, tongue dropped excessively low
        let metrics = evaluate_tongue_position(800.0, 1100.0, 0.48, 0.35);
        assert!(
            metrics.tongue_height < 0.30,
            "Expected height < 0.30, got {}",
            metrics.tongue_height
        );
        assert_eq!(metrics.action_code, ACTION_JAW_TONGUE_LOW);
        assert!(
            metrics.tongue_score < 60,
            "Expected tongue score < 60, got {}",
            metrics.tongue_score
        );
        assert!(metrics.articulatory_guidance.contains("舌头压得过低"));
    }

    #[test]
    fn test_tongue_too_high_position() {
        // Low F1 and small jaw opening, close vowel tendency
        let metrics = evaluate_tongue_position(350.0, 1200.0, 0.10, 0.20);
        assert!(
            metrics.tongue_height > 0.70,
            "Expected height > 0.70, got {}",
            metrics.tongue_height
        );
        assert_eq!(metrics.action_code, ACTION_TONGUE_TOO_HIGH);
        assert!(
            metrics.tongue_score < 60,
            "Expected tongue score < 60, got {}",
            metrics.tongue_score
        );
        assert!(metrics.articulatory_guidance.contains("舌面抬得过高"));
    }
}
