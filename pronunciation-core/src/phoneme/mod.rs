use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhonemeSpan {
    pub phoneme: String,
    pub start_ms: u32,
    pub end_ms: u32,
    pub probability: f32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WordPhonemeTarget {
    pub word: String,
    pub phonemes: Vec<String>,
}

pub fn get_default_word_target(word: &str) -> Option<WordPhonemeTarget> {
    match word.to_lowercase().as_str() {
        "funk" => Some(WordPhonemeTarget {
            word: "funk".to_string(),
            phonemes: vec![
                "f".to_string(),
                "ʌ".to_string(),
                "ŋ".to_string(),
                "k".to_string(),
            ],
        }),
        "ship" => Some(WordPhonemeTarget {
            word: "ship".to_string(),
            phonemes: vec!["ʃ".to_string(), "ɪ".to_string(), "p".to_string()],
        }),
        "sheep" => Some(WordPhonemeTarget {
            word: "sheep".to_string(),
            phonemes: vec!["ʃ".to_string(), "i".to_string(), "p".to_string()],
        }),
        _ => None,
    }
}
