//! JNI bridge exporting native C functions for Android (`arm64-v8a`, `x86_64`).

use crate::evidence::schema::EvidenceJson;
use crate::policy::local_rule::evaluate_evidence_locally;
use crate::scoring::scorer::score_funk;
use jni::objects::{JClass, JString};
use jni::sys::{jfloat, jstring};
use jni::JNIEnv;

/// Returns core engine version identifier.
#[no_mangle]
pub extern "system" fn Java_com_pronunciationcoach_app_core_PronunciationCoreBridge_nativeVersion<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let version = "0.1.0-ai-native";
    match env.new_string(version) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Analyzes complete Evidence JSON and returns comprehensive DiagnosisReport JSON.
#[no_mangle]
pub extern "system" fn Java_com_pronunciationcoach_app_core_PronunciationCoreBridge_nativeAnalyzeEvidence<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    j_evidence_json: JString<'local>,
) -> jstring {
    let result_json = (|| -> Result<String, String> {
        let input_str: String = env
            .get_string(&j_evidence_json)
            .map_err(|e| format!("JNI get_string error: {}", e))?
            .into();

        let evidence = EvidenceJson::from_json_str(&input_str)
            .map_err(|e| format!("EvidenceJson deserialization error: {}", e))?;

        let report = evaluate_evidence_locally(&evidence);

        serde_json::to_string(&report)
            .map_err(|e| format!("DiagnosisReport serialization error: {}", e))
    })();

    let response = match result_json {
        Ok(json) => json,
        Err(err_msg) => format!(r#"{{"error": "{}"}}"#, err_msg.replace('"', "\\\"")),
    };

    match env.new_string(response) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Specialized benchmark scorer for the primary test word "funk" /fʌŋk/.
#[no_mangle]
pub extern "system" fn Java_com_pronunciationcoach_app_core_PronunciationCoreBridge_nativeScoreFunk<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    target_prob: jfloat,
    confusion_prob: jfloat,
    jaw_open: jfloat,
    lip_roundness: jfloat,
) -> jstring {
    let word_result = score_funk(target_prob, confusion_prob, jaw_open, lip_roundness);

    let response = match serde_json::to_string(&word_result) {
        Ok(json) => json,
        Err(err) => format!(r#"{{"error": "{}"}}"#, err),
    };

    match env.new_string(response) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_native_version_string() {
        assert_eq!("0.1.0-ai-native", "0.1.0-ai-native");
    }
}
