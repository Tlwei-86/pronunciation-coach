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

/// Inverts acoustic F1/F2 and visual jawOpen/lipRoundness to continuous 2D tongue coordinates [0.0, 1.0].
///
/// Formulas (RFC Section 6.2):
/// - T_height = 1.0 - (((f1 - 250.0)/650.0)*0.65 + jaw_open*0.35).clamp(0.0, 1.0)
/// - T_backness = (((f2 - 800.0)/1400.0)*0.70 + (1.0 - lip_roundness)*0.30).clamp(0.0, 1.0)
pub fn invert_vowel_tongue_coordinates(
    f1: f32,
    f2: f32,
    jaw_open: f32,
    lip_roundness: f32,
) -> (f32, f32) {
    let height_term = ((f1 - 250.0) / 650.0) * 0.65 + jaw_open * 0.35;
    let tongue_height = (1.0 - height_term.clamp(0.0, 1.0)).clamp(0.0, 1.0);

    let backness_term = ((f2 - 800.0) / 1400.0) * 0.70 + (1.0 - lip_roundness) * 0.30;
    let tongue_backness = backness_term.clamp(0.0, 1.0);

    (tongue_height, tongue_backness)
}

/// Evaluates tongue position from acoustic formants and visual observables for /ʌ/.
/// Preserved for 100% backward compatibility with Phase 2/3 test suites.
pub fn evaluate_tongue_position(
    f1: f32,
    f2: f32,
    jaw_open: f32,
    lip_roundness: f32,
) -> TonguePositionMetrics {
    let (tongue_height, tongue_backness) =
        invert_vowel_tongue_coordinates(f1, f2, jaw_open, lip_roundness);

    // Threshold evaluation based on RFC Section 6.3 for /ʌ/:
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

/// Target anatomical profile for 2D vowel trapezoid evaluation.
struct VowelProfile {
    target_height: f32,
    target_backness: f32,
    min_height: f32,
    max_height: f32,
    min_backness: f32,
    max_backness: f32,
    perfect_guidance: &'static str,
    too_low_guidance: &'static str,
    too_high_guidance: &'static str,
    retracted_guidance: &'static str,
    fronted_guidance: &'static str,
}

fn get_vowel_profile(vowel_ipa: &str) -> Option<VowelProfile> {
    let norm = crate::phoneme::inventory::normalize_phoneme_symbol(vowel_ipa);
    match norm {
        "iː" | "i" => Some(VowelProfile {
            target_height: 0.88,
            target_backness: 0.85,
            min_height: 0.68,
            max_height: 1.00,
            min_backness: 0.65,
            max_backness: 1.00,
            perfect_guidance: "发音极佳！舌前部高高拱起贴近硬腭，嘴角充分拉开展唇。",
            too_low_guidance: "舌位偏低接近 /ɪ/，请嘴角收紧像拍照微笑，舌前部用力拱到最高处。",
            too_high_guidance: "舌位贴得太死阻碍气流，请保持适度口腔通道。",
            retracted_guidance: "舌身过度后缩，请将舌面大幅向前平移，舌尖轻触下齿背。",
            fronted_guidance: "发音适度，注意保持舌位稳定。",
        }),
        "ɪ" => Some(VowelProfile {
            target_height: 0.75,
            target_backness: 0.70,
            min_height: 0.58,
            max_height: 0.88,
            min_backness: 0.50,
            max_backness: 0.85,
            perfect_guidance: "标准发音！舌前部半高，下颌微落一指宽，口型自然放松。",
            too_low_guidance: "舌位过低声音太散，请稍微抬高舌前部，下颌微收。",
            too_high_guidance: "舌位抬得太高接近 /iː/，请放松嘴角与舌身，下颌轻落一指宽。",
            retracted_guidance: "舌位太靠后，请舌尖轻触下齿根，舌前部适度前挺。",
            fronted_guidance: "舌身太靠前用力过度，请完全放松舌面。",
        }),
        "e" | "ɛ" => Some(VowelProfile {
            target_height: 0.58,
            target_backness: 0.68,
            min_height: 0.44,
            max_height: 0.72,
            min_backness: 0.50,
            max_backness: 0.85,
            perfect_guidance: "标准半开前元音！舌前部适度抬起，下巴下落约一指半宽。",
            too_low_guidance: "下巴张得太大接近 /æ/，请向上微合下颌，舌前部适度抬升。",
            too_high_guidance: "下巴太闭或舌面过高接近 /ɪ/，请下颌自然张开一指半宽。",
            retracted_guidance: "舌身过度后缩，请舌前部保持前伸扁平。",
            fronted_guidance: "舌尖过度前顶，请自然平展舌身。",
        }),
        "æ" => Some(VowelProfile {
            target_height: 0.25,
            target_backness: 0.72,
            min_height: 0.08,
            max_height: 0.45,
            min_backness: 0.50,
            max_backness: 0.90,
            perfect_guidance: "非常标准！大开口两指宽，嘴角向两侧平展，舌尖抵住下门牙。",
            too_low_guidance: "下巴已经充分下沉，保持舌尖轻触下门牙。",
            too_high_guidance:
                "口型开度不够大！请用力张开下颌约两指宽，舌尖紧抵下齿内侧，舌面压低。",
            retracted_guidance: "舌身过度后缩发成了中元音，请舌尖紧贴下门牙，舌体向前平铺。",
            fronted_guidance: "发音到位，继续保持舌尖贴紧下齿。",
        }),
        "ɜː" => Some(VowelProfile {
            target_height: 0.55,
            target_backness: 0.48,
            min_height: 0.38,
            max_height: 0.72,
            min_backness: 0.35,
            max_backness: 0.65,
            perfect_guidance: "标准央卷舌元音！舌身中部隆起悬空，舌侧轻触上槽牙，发音饱满稳定。",
            too_low_guidance: "舌身中部没有向上拱起，请舌身悬空隆起，避免舌头塌平。",
            too_high_guidance: "舌身拱得过高挤压喉咙，请略微下沉舌面，放松喉部。",
            retracted_guidance: "舌根过度后缩挤压咽部，请保持舌体在口腔中央。",
            fronted_guidance: "舌身太靠前缺乏中后部共鸣，请将舌体向中央适度收回。",
        }),
        "ə" => Some(VowelProfile {
            target_height: 0.50,
            target_backness: 0.50,
            min_height: 0.35,
            max_height: 0.65,
            min_backness: 0.35,
            max_backness: 0.65,
            perfect_guidance: "完美的极简弱读音！发音器官完全放松，轻短带过。",
            too_low_guidance: "发音用力过猛下巴太低，请完全放松面部肌肉，像叹气一样轻轻嘀咕一声。",
            too_high_guidance: "舌位偏高，请彻底放松舌头与下巴。",
            retracted_guidance: "舌身微向中央靠拢，自然轻读。",
            fronted_guidance: "舌尖不要用力向前顶，保持中央完全松弛。",
        }),
        "uː" | "u" => Some(VowelProfile {
            target_height: 0.85,
            target_backness: 0.18,
            min_height: 0.65,
            max_height: 1.00,
            min_backness: 0.00,
            max_backness: 0.35,
            perfect_guidance: "标准后高长元音！双唇收成紧圆孔前撮，舌后部向软腭高高隆起。",
            too_low_guidance:
                "舌根抬升不够或双唇不紧，请将双唇紧缩成吸管口前凸，舌后部用力向软腭隆起。",
            too_high_guidance: "舌根贴得过紧阻断气流，请留出清晰声管通路。",
            retracted_guidance: "声音饱满，继续保持后部共鸣。",
            fronted_guidance: "舌位过度靠前发成了央音，请将舌身充分向后缩，双唇紧缩前圆。",
        }),
        "ʊ" => Some(VowelProfile {
            target_height: 0.70,
            target_backness: 0.28,
            min_height: 0.52,
            max_height: 0.85,
            min_backness: 0.12,
            max_backness: 0.45,
            perfect_guidance: "标准短促圆唇元音！双唇自然微圆不紧绷，舌后部适度微抬。",
            too_low_guidance: "舌后部抬起不够声音松散，请舌后部适度向软腭微抬。",
            too_high_guidance: "嘴唇过度紧抿用力过猛接近 /uː/，请保持嘴唇自然微圆，急促发力。",
            retracted_guidance: "舌位略微前移，保持短促松弛。",
            fronted_guidance: "舌头太靠前读成了央元音，请舌身后缩至后口腔。",
        }),
        "ɔː" => Some(VowelProfile {
            target_height: 0.38,
            target_backness: 0.22,
            min_height: 0.20,
            max_height: 0.52,
            min_backness: 0.08,
            max_backness: 0.38,
            perfect_guidance: "浑厚标准！嘴唇收成椭圆形前凸，下颌下落两指宽，舌后部下压后缩。",
            too_low_guidance: "下巴掉得过低读成了扁平音，请双唇向前撮成中等椭圆 O 型。",
            too_high_guidance: "口型太闭，请充分下落两指宽，让声音在喉咙深处共鸣。",
            retracted_guidance: "共鸣良好，保持舌身后缩。",
            fronted_guidance: "嘴唇扁平舌位前倾，请立刻噘圆嘴唇，舌根向后咽壁收缩。",
        }),
        "ɑː" | "ɑ" => Some(VowelProfile {
            target_height: 0.18,
            target_backness: 0.28,
            min_height: 0.05,
            max_height: 0.38,
            min_backness: 0.10,
            max_backness: 0.48,
            perfect_guidance:
                "标准大开口长元音！下巴彻底打开，舌身平平沉在口腔底部，声音开阔通透。",
            too_low_guidance: "下巴已经充分下沉，保持开阔胸腔共鸣。",
            too_high_guidance: "嘴巴张得太小！请彻底打开下巴（如看牙医），舌头平平沉在口底。",
            retracted_guidance: "发音到位，保持平放后缩姿态。",
            fronted_guidance: "舌位过度靠前接近 /ʌ/，请舌头彻底放松压低在口底。",
        }),
        "ɒ" => Some(VowelProfile {
            target_height: 0.22,
            target_backness: 0.22,
            min_height: 0.08,
            max_height: 0.38,
            min_backness: 0.08,
            max_backness: 0.38,
            perfect_guidance: "标准短促后元音！下颌充分打开，嘴唇微圆，急促有力。",
            too_low_guidance: "下巴到位，注意短促收束。",
            too_high_guidance: "口型不够开，请下巴落开两指宽，舌根后缩，急促发音。",
            retracted_guidance: "舌身后缩到位。",
            fronted_guidance: "舌位太靠前，请向后咽壁收拢。",
        }),
        "eɪ" => Some(VowelProfile {
            target_height: 0.68,
            target_backness: 0.70,
            min_height: 0.48,
            max_height: 0.85,
            min_backness: 0.50,
            max_backness: 0.88,
            perfect_guidance: "合口双元音滑行良好！下巴由开向闭滑动，嘴角同步向两侧拉宽。",
            too_low_guidance: "滑动不完整结尾口型过开，请确保下颌上提滑动到位。",
            too_high_guidance: "起点太闭，请从半开扁唇起始滑行。",
            retracted_guidance: "舌身向前伸展，保持前元音滑动轨迹。",
            fronted_guidance: "滑动自然到位。",
        }),
        "aɪ" => Some(VowelProfile {
            target_height: 0.45,
            target_backness: 0.60,
            min_height: 0.20,
            max_height: 0.75,
            min_backness: 0.40,
            max_backness: 0.85,
            perfect_guidance: "大幅度双元音滑行极佳！下颌由充分大开迅速向上提拉闭合。",
            too_low_guidance: "尾音缺失声音发虚，请确保下颌向上提拉，舌面前部抬起。",
            too_high_guidance: "起点未彻底张开，请从大开口起始。",
            retracted_guidance: "向高前位滑动，舌身向前挺立。",
            fronted_guidance: "轨迹清晰完整。",
        }),
        "ɔɪ" => Some(VowelProfile {
            target_height: 0.55,
            target_backness: 0.45,
            min_height: 0.25,
            max_height: 0.80,
            min_backness: 0.15,
            max_backness: 0.80,
            perfect_guidance: "口型蜕变完美！双唇由圆向前滑变为展唇微笑。",
            too_low_guidance: "尾音嘴型未展唇，请从圆圈迅速拉伸成扁平微笑。",
            too_high_guidance: "起点嘴唇不够圆，请从圆唇清晰起始。",
            retracted_guidance: "滑动充分向前扩展。",
            fronted_guidance: "前后反差鲜明，发音到位。",
        }),
        "aʊ" => Some(VowelProfile {
            target_height: 0.42,
            target_backness: 0.35,
            min_height: 0.15,
            max_height: 0.70,
            min_backness: 0.15,
            max_backness: 0.65,
            perfect_guidance: "双唇收圆到位！大开口起始，双唇同步收拢向前撮圆。",
            too_low_guidance: "发到结尾时嘴唇没有收圆，请像吹口哨一样聚拢收圆。",
            too_high_guidance: "起始开口度不足，请先将下颌大开再收圆。",
            retracted_guidance: "后部收拢良好。",
            fronted_guidance: "注意结尾向后高圆唇位置靠拢。",
        }),
        "oʊ" => Some(VowelProfile {
            target_height: 0.58,
            target_backness: 0.25,
            min_height: 0.35,
            max_height: 0.80,
            min_backness: 0.10,
            max_backness: 0.45,
            perfect_guidance: "美音滑行饱满！嘴唇由微圆聚拢为小紧圆，舌根向后上方抬起。",
            too_low_guidance: "只发了单一元音，请嘴唇由松到紧向前撮起成小圆圈。",
            too_high_guidance: "起点太闭，留出滑动余量。",
            retracted_guidance: "后部共鸣充足，保持稳定滑动。",
            fronted_guidance: "舌头不要向前跑，保持在后口腔。",
        }),
        _ => None,
    }
}

/// Evaluates 2D vowel trapezoid tongue position for all American English vowels.
pub fn evaluate_vowel_tongue_position(
    vowel_ipa: &str,
    f1: f32,
    f2: f32,
    jaw_open: f32,
    lip_roundness: f32,
) -> TonguePositionMetrics {
    let norm = crate::phoneme::inventory::normalize_phoneme_symbol(vowel_ipa);
    // Keep 100% fidelity for caret /ʌ/
    if norm == "ʌ" {
        return evaluate_tongue_position(f1, f2, jaw_open, lip_roundness);
    }

    let (tongue_height, tongue_backness) =
        invert_vowel_tongue_coordinates(f1, f2, jaw_open, lip_roundness);

    let profile = match get_vowel_profile(norm) {
        Some(p) => p,
        None => {
            // Non-vowel or unknown fallback: neutral position
            return TonguePositionMetrics {
                tongue_height,
                tongue_backness,
                tongue_score: 100,
                articulatory_guidance: "非元音或常规辅音，无需舌位梯形图反演。".to_string(),
                action_code: ACTION_PERFECT,
            };
        }
    };

    let dev_h_low = if tongue_height < profile.min_height {
        profile.min_height - tongue_height
    } else {
        0.0
    };
    let dev_h_high = if tongue_height > profile.max_height {
        tongue_height - profile.max_height
    } else {
        0.0
    };
    let dev_b_retracted = if tongue_backness < profile.min_backness {
        profile.min_backness - tongue_backness
    } else {
        0.0
    };
    let dev_b_fronted = if tongue_backness > profile.max_backness {
        tongue_backness - profile.max_backness
    } else {
        0.0
    };

    let has_deviation =
        dev_h_low > 0.0 || dev_h_high > 0.0 || dev_b_retracted > 0.0 || dev_b_fronted > 0.0;

    let (action_code, articulatory_guidance) = if has_deviation {
        let mut max_dev = dev_h_low;
        let mut code = ACTION_JAW_TONGUE_LOW;
        let mut guidance = profile.too_low_guidance;

        if dev_h_high > max_dev {
            max_dev = dev_h_high;
            code = ACTION_TONGUE_TOO_HIGH;
            guidance = profile.too_high_guidance;
        }
        if dev_b_retracted > max_dev {
            max_dev = dev_b_retracted;
            code = ACTION_TONGUE_RETRACTED;
            guidance = profile.retracted_guidance;
        }
        if dev_b_fronted > max_dev {
            code = ACTION_TONGUE_TOO_FRONT;
            guidance = profile.fronted_guidance;
        }
        (code, guidance.to_string())
    } else {
        (ACTION_PERFECT, profile.perfect_guidance.to_string())
    };

    let tongue_score = if action_code == ACTION_PERFECT {
        let dist = ((tongue_height - profile.target_height).powi(2)
            + (tongue_backness - profile.target_backness).powi(2))
        .sqrt();
        let score = 100.0 - dist * 50.0;
        score.round().clamp(88.0, 100.0) as u32
    } else {
        let total_err = dev_h_low + dev_h_high + dev_b_retracted + dev_b_fronted;
        let score = 75.0 - (total_err * 150.0);
        score.round().clamp(20.0, 72.0) as u32
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
    }

    #[test]
    fn test_all_vowels_tongue_inversion() {
        // Test /iː/ high front vowel (low F1 ~280, high F2 ~2200, small jawOpen ~0.10, low roundness ~0.05)
        let metrics_i = evaluate_vowel_tongue_position("iː", 280.0, 2200.0, 0.10, 0.05);
        assert!(
            metrics_i.tongue_height > 0.75,
            "Expected high tongue for /iː/, got {}",
            metrics_i.tongue_height
        );
        assert!(
            metrics_i.tongue_backness > 0.75,
            "Expected front tongue for /iː/, got {}",
            metrics_i.tongue_backness
        );
        assert_eq!(metrics_i.action_code, ACTION_PERFECT);
        assert!(metrics_i.tongue_score >= 88);

        // Test /ɑː/ low back vowel (high F1 ~800, low F2 ~1050, large jawOpen ~0.65, low roundness ~0.05)
        let metrics_ah = evaluate_vowel_tongue_position("ɑː", 800.0, 1050.0, 0.65, 0.05);
        assert!(
            metrics_ah.tongue_height < 0.30,
            "Expected low tongue for /ɑː/, got {}",
            metrics_ah.tongue_height
        );
        assert!(
            metrics_ah.tongue_backness < 0.45,
            "Expected back tongue for /ɑː/, got {}",
            metrics_ah.tongue_backness
        );
        assert_eq!(metrics_ah.action_code, ACTION_PERFECT);

        // Test /uː/ high back rounded vowel (low F1 ~300, low F2 ~900, small jawOpen ~0.10, high roundness ~0.85)
        let metrics_u = evaluate_vowel_tongue_position("uː", 300.0, 900.0, 0.10, 0.85);
        assert!(
            metrics_u.tongue_height > 0.70,
            "Expected high tongue for /uː/, got {}",
            metrics_u.tongue_height
        );
        assert!(
            metrics_u.tongue_backness < 0.35,
            "Expected back tongue for /uː/, got {}",
            metrics_u.tongue_backness
        );
        assert_eq!(metrics_u.action_code, ACTION_PERFECT);
    }
}
