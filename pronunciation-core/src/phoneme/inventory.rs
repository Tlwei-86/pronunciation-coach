//! eSpeak / IPA phoneme inventory definitions for American English pronunciation assessment.

use serde::{Deserialize, Serialize};

/// Broad phonetic categorization.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum PhonemeCategory {
    Vowel,
    Diphthong,
    Plosive,
    Fricative,
    Nasal,
    Approximant,
    Affricate,
    Silence,
    Blank,
}

/// Metadata and characteristics for an individual phoneme.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct PhonemeInfo {
    /// Standard IPA notation (e.g. "ʌ", "ɑ", "ŋ", "f", "k").
    pub ipa: &'static str,
    /// eSpeak symbol / ascii alias (e.g. "V", "A:", "N", "f", "k").
    pub espeak_symbol: &'static str,
    /// Phonetic category.
    pub category: PhonemeCategory,
    /// Whether the phoneme is voiced.
    pub voiced: bool,
    /// Human-readable description/example.
    pub description: &'static str,
}

/// Standard American English inventory table.
pub static PHONEME_INVENTORY: &[PhonemeInfo] = &[
    // Vowels
    PhonemeInfo { ipa: "ʌ", espeak_symbol: "V", category: PhonemeCategory::Vowel, voiced: true, description: "Open-mid back unrounded vowel (strut/funk)" },
    PhonemeInfo { ipa: "ɑ", espeak_symbol: "A:", category: PhonemeCategory::Vowel, voiced: true, description: "Open back unrounded vowel (father/lot)" },
    PhonemeInfo { ipa: "ə", espeak_symbol: "@", category: PhonemeCategory::Vowel, voiced: true, description: "Mid central unrounded vowel (schwa/about)" },
    PhonemeInfo { ipa: "æ", espeak_symbol: "a", category: PhonemeCategory::Vowel, voiced: true, description: "Near-open front unrounded vowel (trap/cat)" },
    PhonemeInfo { ipa: "ɛ", espeak_symbol: "E", category: PhonemeCategory::Vowel, voiced: true, description: "Open-mid front unrounded vowel (dress/bed)" },
    PhonemeInfo { ipa: "i", espeak_symbol: "i:", category: PhonemeCategory::Vowel, voiced: true, description: "Close front unrounded vowel (fleece/see)" },
    PhonemeInfo { ipa: "ɪ", espeak_symbol: "I", category: PhonemeCategory::Vowel, voiced: true, description: "Near-close near-front unrounded vowel (kit/sit)" },
    PhonemeInfo { ipa: "u", espeak_symbol: "u:", category: PhonemeCategory::Vowel, voiced: true, description: "Close back rounded vowel (goose/boot)" },
    PhonemeInfo { ipa: "ʊ", espeak_symbol: "U", category: PhonemeCategory::Vowel, voiced: true, description: "Near-close near-back rounded vowel (foot/put)" },
    PhonemeInfo { ipa: "ɔ", espeak_symbol: "O:", category: PhonemeCategory::Vowel, voiced: true, description: "Open-mid back rounded vowel (thought/dog)" },
    PhonemeInfo { ipa: "ɜ", espeak_symbol: "3:", category: PhonemeCategory::Vowel, voiced: true, description: "Open-mid central unrounded vowel (nurse/bird)" },
    // Consonants - Fricatives
    PhonemeInfo { ipa: "f", espeak_symbol: "f", category: PhonemeCategory::Fricative, voiced: false, description: "Voiceless labiodental fricative (funk/fast)" },
    PhonemeInfo { ipa: "v", espeak_symbol: "v", category: PhonemeCategory::Fricative, voiced: true, description: "Voiced labiodental fricative (voice/van)" },
    PhonemeInfo { ipa: "θ", espeak_symbol: "T", category: PhonemeCategory::Fricative, voiced: false, description: "Voiceless dental fricative (thin/math)" },
    PhonemeInfo { ipa: "ð", espeak_symbol: "D", category: PhonemeCategory::Fricative, voiced: true, description: "Voiced dental fricative (this/mother)" },
    PhonemeInfo { ipa: "s", espeak_symbol: "s", category: PhonemeCategory::Fricative, voiced: false, description: "Voiceless alveolar fricative (sip/pass)" },
    PhonemeInfo { ipa: "z", espeak_symbol: "z", category: PhonemeCategory::Fricative, voiced: true, description: "Voiced alveolar fricative (zip/buzz)" },
    PhonemeInfo { ipa: "ʃ", espeak_symbol: "S", category: PhonemeCategory::Fricative, voiced: false, description: "Voiceless postalveolar fricative (ship/fish)" },
    PhonemeInfo { ipa: "ʒ", espeak_symbol: "Z", category: PhonemeCategory::Fricative, voiced: true, description: "Voiced postalveolar fricative (vision/measure)" },
    PhonemeInfo { ipa: "h", espeak_symbol: "h", category: PhonemeCategory::Fricative, voiced: false, description: "Voiceless glottal fricative (hat/home)" },
    // Consonants - Plosives
    PhonemeInfo { ipa: "p", espeak_symbol: "p", category: PhonemeCategory::Plosive, voiced: false, description: "Voiceless bilabial plosive (pin/lip)" },
    PhonemeInfo { ipa: "b", espeak_symbol: "b", category: PhonemeCategory::Plosive, voiced: true, description: "Voiced bilabial plosive (bat/cab)" },
    PhonemeInfo { ipa: "t", espeak_symbol: "t", category: PhonemeCategory::Plosive, voiced: false, description: "Voiceless alveolar plosive (top/cat)" },
    PhonemeInfo { ipa: "d", espeak_symbol: "d", category: PhonemeCategory::Plosive, voiced: true, description: "Voiced alveolar plosive (dot/bed)" },
    PhonemeInfo { ipa: "k", espeak_symbol: "k", category: PhonemeCategory::Plosive, voiced: false, description: "Voiceless velar plosive (cat/funk)" },
    PhonemeInfo { ipa: "g", espeak_symbol: "g", category: PhonemeCategory::Plosive, voiced: true, description: "Voiced velar plosive (get/bag)" },
    // Consonants - Nasals
    PhonemeInfo { ipa: "m", espeak_symbol: "m", category: PhonemeCategory::Nasal, voiced: true, description: "Voiced bilabial nasal (man/sum)" },
    PhonemeInfo { ipa: "n", espeak_symbol: "n", category: PhonemeCategory::Nasal, voiced: true, description: "Voiced alveolar nasal (no/sun)" },
    PhonemeInfo { ipa: "ŋ", espeak_symbol: "N", category: PhonemeCategory::Nasal, voiced: true, description: "Voiced velar nasal (sing/funk)" },
    // Consonants - Approximants
    PhonemeInfo { ipa: "l", espeak_symbol: "l", category: PhonemeCategory::Approximant, voiced: true, description: "Voiced alveolar lateral approximant (light/ball)" },
    PhonemeInfo { ipa: "r", espeak_symbol: "r", category: PhonemeCategory::Approximant, voiced: true, description: "Voiced postalveolar approximant (red/car)" },
    PhonemeInfo { ipa: "w", espeak_symbol: "w", category: PhonemeCategory::Approximant, voiced: true, description: "Voiced labio-velar approximant (win/wet)" },
    PhonemeInfo { ipa: "j", espeak_symbol: "j", category: PhonemeCategory::Approximant, voiced: true, description: "Voiced palatal approximant (yes/yellow)" },
    // Special
    PhonemeInfo { ipa: "<pad>", espeak_symbol: "<pad>", category: PhonemeCategory::Blank, voiced: false, description: "CTC Blank / Padding token" },
    PhonemeInfo { ipa: "<sil>", espeak_symbol: "<sil>", category: PhonemeCategory::Silence, voiced: false, description: "Silence token" },
];

/// Finds a phoneme entry by its IPA symbol or eSpeak ascii alias.
pub fn find_phoneme(symbol: &str) -> Option<&'static PhonemeInfo> {
    PHONEME_INVENTORY
        .iter()
        .find(|p| p.ipa == symbol || p.espeak_symbol == symbol)
}

/// Returns true if the symbol is a vowel.
pub fn is_vowel(symbol: &str) -> bool {
    find_phoneme(symbol)
        .map(|p| p.category == PhonemeCategory::Vowel || p.category == PhonemeCategory::Diphthong)
        .unwrap_or(false)
}

/// Returns true if the symbol is a velar consonant (e.g. /k/, /g/, /ŋ/) having negligible visual visibility.
pub fn is_velar_consonant(symbol: &str) -> bool {
    symbol == "k" || symbol == "g" || symbol == "ŋ" || symbol == "N"
}

/// Returns true if the symbol is a labiodental or bilabial consonant with high visual visibility.
pub fn is_visually_salient(symbol: &str) -> bool {
    matches!(symbol, "f" | "v" | "p" | "b" | "m" | "w" | "θ" | "ð")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_phoneme_lookup() {
        let caret = find_phoneme("ʌ").expect("Caret /ʌ/ should be in inventory");
        assert_eq!(caret.category, PhonemeCategory::Vowel);
        assert_eq!(caret.espeak_symbol, "V");

        let f = find_phoneme("f").expect("/f/ should be in inventory");
        assert_eq!(f.category, PhonemeCategory::Fricative);

        let ng = find_phoneme("ŋ").expect("/ŋ/ should be in inventory");
        assert_eq!(ng.category, PhonemeCategory::Nasal);
    }

    #[test]
    fn test_phonetic_helpers() {
        assert!(is_vowel("ʌ"));
        assert!(is_vowel("ɑ"));
        assert!(!is_vowel("f"));
        assert!(!is_vowel("k"));

        assert!(is_velar_consonant("k"));
        assert!(is_velar_consonant("ŋ"));
        assert!(!is_velar_consonant("f"));

        assert!(is_visually_salient("f"));
        assert!(is_visually_salient("m"));
        assert!(!is_visually_salient("k"));
    }
}
