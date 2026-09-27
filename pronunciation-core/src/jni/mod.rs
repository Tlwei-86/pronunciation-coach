use jni::objects::{JClass, JString};
use jni::sys::{jfloat, jstring};
use jni::JNIEnv;

use crate::evidence::schema::EvidenceJson;
use crate::scoring::DeterministicScorer;

#[no_mangle]
pub extern "system" fn Java_com_pronunciationcoach_app_core_PronunciationCoreBridge_nativeVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let output = env.new_string("pronunciation-core v0.1.0 (Rust Core + JNI)").unwrap();
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_pronunciationcoach_app_core_PronunciationCoreBridge_nativeScoreFunk(
    env: JNIEnv,
    _class: JClass,
    target_prob: jfloat,
    confusion_prob: jfloat,
    jaw_open: jfloat,
    lip_roundness: jfloat,
) -> jstring {
    let report = DeterministicScorer::score_funk(target_prob, confusion_prob, jaw_open, lip_roundness);
    let json_str = serde_json::to_string(&report).unwrap_or_else(|_| "{}".to_string());
    let output = env.new_string(json_str).unwrap();
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_pronunciationcoach_app_core_PronunciationCoreBridge_nativeAnalyzeEvidence(
    mut env: JNIEnv,
    _class: JClass,
    j_evidence: JString,
) -> jstring {
    let input: String = match env.get_string(&j_evidence) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string(r#"{"error":"Invalid string"}"#).unwrap().into_raw(),
    };

    let result_json = match serde_json::from_str::<EvidenceJson>(&input) {
        Ok(evidence) => {
            let score_res = DeterministicScorer::score_phoneme(&evidence);
            serde_json::to_string(&score_res).unwrap_or_else(|_| "{}".to_string())
        }
        Err(e) => format!(r#"{{"error":"{}"}}"#, e),
    };

    let output = env.new_string(result_json).unwrap();
    output.into_raw()
}
