pub mod mouth_geometry;
pub mod viseme;

pub use mouth_geometry::*;
pub use viseme::*;

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub enum VisemeCategory {
    Bilabial,        // /p/, /b/, /m/
    Labiodental,     // /f/, /v/
    Dental,          // /θ/, /ð/
    Alveolar,        // /t/, /d/, /s/, /z/
    OpenVowel,       // /ɑ/, /æ/
    MidCentralVowel, // /ʌ/, /ə/
    CloseVowel,      // /i/, /u/
    RoundedVowel,    // /u/, /oʊ/, /w/
    InternalVelar,   // /k/, /g/, /ŋ/ (invisible)
}

pub fn get_phoneme_viseme_category(phoneme: &str) -> VisemeCategory {
    match phoneme {
        "p" | "b" | "m" => VisemeCategory::Bilabial,
        "f" | "v" => VisemeCategory::Labiodental,
        "θ" | "ð" => VisemeCategory::Dental,
        "ʌ" | "ə" => VisemeCategory::MidCentralVowel,
        "ɑ" | "æ" => VisemeCategory::OpenVowel,
        "i" | "ɪ" => VisemeCategory::CloseVowel,
        "u" | "ʊ" | "w" => VisemeCategory::RoundedVowel,
        "k" | "g" | "ŋ" => VisemeCategory::InternalVelar,
        _ => VisemeCategory::Alveolar,
    }
}

pub fn get_visual_weight_for_phoneme(phoneme: &str) -> f32 {
    match get_phoneme_viseme_category(phoneme) {
        VisemeCategory::Labiodental | VisemeCategory::Dental | VisemeCategory::RoundedVowel => 0.40,
        VisemeCategory::Bilabial => 0.35,
        VisemeCategory::MidCentralVowel | VisemeCategory::OpenVowel => 0.30,
        VisemeCategory::CloseVowel => 0.20,
        VisemeCategory::InternalVelar => 0.05, // near zero visual weight per spec
        _ => 0.15,
    }
}
