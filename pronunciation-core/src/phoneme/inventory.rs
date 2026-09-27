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

/// Standard American English inventory table containing all 44 phonemes plus special tokens.
pub static PHONEME_INVENTORY: &[PhonemeInfo] = &[
    // --- 12 Monophthong Vowels ---
    PhonemeInfo {
        ipa: "iː",
        espeak_symbol: "i:",
        category: PhonemeCategory::Vowel,
        voiced: true,
        description: "Close front unrounded long vowel (fleece/see)",
    },
    PhonemeInfo {
        ipa: "ɪ",
        espeak_symbol: "I",
        category: PhonemeCategory::Vowel,
        voiced: true,
        description: "Near-close near-front unrounded vowel (kit/sit)",
    },
    PhonemeInfo {
        ipa: "e",
        espeak_symbol: "E",
        category: PhonemeCategory::Vowel,
        voiced: true,
        description: "Open-mid front unrounded vowel (dress/bed)",
    },
    PhonemeInfo {
        ipa: "æ",
        espeak_symbol: "a",
        category: PhonemeCategory::Vowel,
        voiced: true,
        description: "Near-open front unrounded vowel (trap/cat)",
    },
    PhonemeInfo {
        ipa: "ʌ",
        espeak_symbol: "V",
        category: PhonemeCategory::Vowel,
        voiced: true,
        description: "Open-mid back unrounded vowel (strut/funk)",
    },
    PhonemeInfo {
        ipa: "ɜː",
        espeak_symbol: "3:",
        category: PhonemeCategory::Vowel,
        voiced: true,
        description: "Open-mid central unrounded vowel (nurse/bird)",
    },
    PhonemeInfo {
        ipa: "ə",
        espeak_symbol: "@",
        category: PhonemeCategory::Vowel,
        voiced: true,
        description: "Mid central unrounded vowel (schwa/about)",
    },
    PhonemeInfo {
        ipa: "uː",
        espeak_symbol: "u:",
        category: PhonemeCategory::Vowel,
        voiced: true,
        description: "Close back rounded long vowel (goose/boot)",
    },
    PhonemeInfo {
        ipa: "ʊ",
        espeak_symbol: "U",
        category: PhonemeCategory::Vowel,
        voiced: true,
        description: "Near-close near-back rounded vowel (foot/put)",
    },
    PhonemeInfo {
        ipa: "ɔː",
        espeak_symbol: "O:",
        category: PhonemeCategory::Vowel,
        voiced: true,
        description: "Open-mid back rounded long vowel (thought/dog)",
    },
    PhonemeInfo {
        ipa: "ɑː",
        espeak_symbol: "A:",
        category: PhonemeCategory::Vowel,
        voiced: true,
        description: "Open back unrounded long vowel (father/lot)",
    },
    PhonemeInfo {
        ipa: "ɒ",
        espeak_symbol: "Q",
        category: PhonemeCategory::Vowel,
        voiced: true,
        description: "Open back rounded short vowel (lot/not)",
    },
    // --- 8 Diphthongs ---
    PhonemeInfo {
        ipa: "eɪ",
        espeak_symbol: "eI",
        category: PhonemeCategory::Diphthong,
        voiced: true,
        description: "Closing fronting diphthong (face/day)",
    },
    PhonemeInfo {
        ipa: "aɪ",
        espeak_symbol: "aI",
        category: PhonemeCategory::Diphthong,
        voiced: true,
        description: "Closing fronting diphthong (price/my)",
    },
    PhonemeInfo {
        ipa: "ɔɪ",
        espeak_symbol: "OI",
        category: PhonemeCategory::Diphthong,
        voiced: true,
        description: "Closing fronting diphthong (choice/boy)",
    },
    PhonemeInfo {
        ipa: "aʊ",
        espeak_symbol: "aU",
        category: PhonemeCategory::Diphthong,
        voiced: true,
        description: "Closing backing diphthong (mouth/now)",
    },
    PhonemeInfo {
        ipa: "oʊ",
        espeak_symbol: "oU",
        category: PhonemeCategory::Diphthong,
        voiced: true,
        description: "Closing backing diphthong (goat/show)",
    },
    PhonemeInfo {
        ipa: "ɪə",
        espeak_symbol: "i@",
        category: PhonemeCategory::Diphthong,
        voiced: true,
        description: "Centering diphthong (near/here)",
    },
    PhonemeInfo {
        ipa: "eə",
        espeak_symbol: "e@",
        category: PhonemeCategory::Diphthong,
        voiced: true,
        description: "Centering diphthong (square/hair)",
    },
    PhonemeInfo {
        ipa: "ʊə",
        espeak_symbol: "u@",
        category: PhonemeCategory::Diphthong,
        voiced: true,
        description: "Centering diphthong (cure/tour)",
    },
    // --- 6 Plosives ---
    PhonemeInfo {
        ipa: "p",
        espeak_symbol: "p",
        category: PhonemeCategory::Plosive,
        voiced: false,
        description: "Voiceless bilabial plosive (pin/lip)",
    },
    PhonemeInfo {
        ipa: "b",
        espeak_symbol: "b",
        category: PhonemeCategory::Plosive,
        voiced: true,
        description: "Voiced bilabial plosive (bat/cab)",
    },
    PhonemeInfo {
        ipa: "t",
        espeak_symbol: "t",
        category: PhonemeCategory::Plosive,
        voiced: false,
        description: "Voiceless alveolar plosive (top/cat)",
    },
    PhonemeInfo {
        ipa: "d",
        espeak_symbol: "d",
        category: PhonemeCategory::Plosive,
        voiced: true,
        description: "Voiced alveolar plosive (dot/bed)",
    },
    PhonemeInfo {
        ipa: "k",
        espeak_symbol: "k",
        category: PhonemeCategory::Plosive,
        voiced: false,
        description: "Voiceless velar plosive (cat/funk)",
    },
    PhonemeInfo {
        ipa: "g",
        espeak_symbol: "g",
        category: PhonemeCategory::Plosive,
        voiced: true,
        description: "Voiced velar plosive (get/bag)",
    },
    // --- 9 Fricatives ---
    PhonemeInfo {
        ipa: "f",
        espeak_symbol: "f",
        category: PhonemeCategory::Fricative,
        voiced: false,
        description: "Voiceless labiodental fricative (funk/fast)",
    },
    PhonemeInfo {
        ipa: "v",
        espeak_symbol: "v",
        category: PhonemeCategory::Fricative,
        voiced: true,
        description: "Voiced labiodental fricative (voice/van)",
    },
    PhonemeInfo {
        ipa: "θ",
        espeak_symbol: "T",
        category: PhonemeCategory::Fricative,
        voiced: false,
        description: "Voiceless dental fricative (thin/math)",
    },
    PhonemeInfo {
        ipa: "ð",
        espeak_symbol: "D",
        category: PhonemeCategory::Fricative,
        voiced: true,
        description: "Voiced dental fricative (this/mother)",
    },
    PhonemeInfo {
        ipa: "s",
        espeak_symbol: "s",
        category: PhonemeCategory::Fricative,
        voiced: false,
        description: "Voiceless alveolar fricative (sip/pass)",
    },
    PhonemeInfo {
        ipa: "z",
        espeak_symbol: "z",
        category: PhonemeCategory::Fricative,
        voiced: true,
        description: "Voiced alveolar fricative (zip/buzz)",
    },
    PhonemeInfo {
        ipa: "ʃ",
        espeak_symbol: "S",
        category: PhonemeCategory::Fricative,
        voiced: false,
        description: "Voiceless postalveolar fricative (ship/fish)",
    },
    PhonemeInfo {
        ipa: "ʒ",
        espeak_symbol: "Z",
        category: PhonemeCategory::Fricative,
        voiced: true,
        description: "Voiced postalveolar fricative (vision/measure)",
    },
    PhonemeInfo {
        ipa: "h",
        espeak_symbol: "h",
        category: PhonemeCategory::Fricative,
        voiced: false,
        description: "Voiceless glottal fricative (hat/home)",
    },
    // --- 2 Affricates ---
    PhonemeInfo {
        ipa: "tʃ",
        espeak_symbol: "tS",
        category: PhonemeCategory::Affricate,
        voiced: false,
        description: "Voiceless postalveolar affricate (chin/church)",
    },
    PhonemeInfo {
        ipa: "dʒ",
        espeak_symbol: "dZ",
        category: PhonemeCategory::Affricate,
        voiced: true,
        description: "Voiced postalveolar affricate (joy/judge)",
    },
    // --- 3 Nasals ---
    PhonemeInfo {
        ipa: "m",
        espeak_symbol: "m",
        category: PhonemeCategory::Nasal,
        voiced: true,
        description: "Voiced bilabial nasal (man/sum)",
    },
    PhonemeInfo {
        ipa: "n",
        espeak_symbol: "n",
        category: PhonemeCategory::Nasal,
        voiced: true,
        description: "Voiced alveolar nasal (no/sun)",
    },
    PhonemeInfo {
        ipa: "ŋ",
        espeak_symbol: "N",
        category: PhonemeCategory::Nasal,
        voiced: true,
        description: "Voiced velar nasal (sing/funk)",
    },
    // --- 4 Approximants / Liquids / Glides ---
    PhonemeInfo {
        ipa: "l",
        espeak_symbol: "l",
        category: PhonemeCategory::Approximant,
        voiced: true,
        description: "Voiced alveolar lateral approximant (light/ball)",
    },
    PhonemeInfo {
        ipa: "r",
        espeak_symbol: "r",
        category: PhonemeCategory::Approximant,
        voiced: true,
        description: "Voiced postalveolar approximant (red/car)",
    },
    PhonemeInfo {
        ipa: "w",
        espeak_symbol: "w",
        category: PhonemeCategory::Approximant,
        voiced: true,
        description: "Voiced labio-velar approximant (win/wet)",
    },
    PhonemeInfo {
        ipa: "j",
        espeak_symbol: "j",
        category: PhonemeCategory::Approximant,
        voiced: true,
        description: "Voiced palatal approximant (yes/yellow)",
    },
    // --- Special Tokens ---
    PhonemeInfo {
        ipa: "<pad>",
        espeak_symbol: "<pad>",
        category: PhonemeCategory::Blank,
        voiced: false,
        description: "CTC Blank / Padding token",
    },
    PhonemeInfo {
        ipa: "<sil>",
        espeak_symbol: "<sil>",
        category: PhonemeCategory::Silence,
        voiced: false,
        description: "Silence token",
    },
];

/// Normalizes phoneme strings by stripping slashes and matching standard aliases.
pub fn normalize_phoneme_symbol(symbol: &str) -> &str {
    let clean = symbol.trim().trim_matches('/');
    match clean {
        "i" | "i:" => "iː",
        "u" | "u:" => "uː",
        "ɑ" | "A:" | "A" => "ɑː",
        "ɔ" | "O:" | "O" => "ɔː",
        "ɜ" | "3:" | "ɝ" | "3" => "ɜː",
        "ɛ" | "E" => "e",
        "V" => "ʌ",
        "@" => "ə",
        "a" => "æ",
        "I" => "ɪ",
        "U" => "ʊ",
        "Q" => "ɒ",
        "T" => "θ",
        "D" => "ð",
        "S" => "ʃ",
        "Z" => "ʒ",
        "N" => "ŋ",
        "tS" | "ch" => "tʃ",
        "dZ" => "dʒ",
        "oU" | "əʊ" => "oʊ",
        _ => clean,
    }
}

/// Finds a phoneme entry by its IPA symbol or eSpeak ascii alias, with robust normalization.
pub fn find_phoneme(symbol: &str) -> Option<&'static PhonemeInfo> {
    let norm = normalize_phoneme_symbol(symbol);
    PHONEME_INVENTORY.iter().find(|p| {
        p.ipa == norm || p.espeak_symbol == norm || p.ipa == symbol || p.espeak_symbol == symbol
    })
}

/// Returns true if the symbol is a vowel or diphthong.
pub fn is_vowel(symbol: &str) -> bool {
    find_phoneme(symbol)
        .map(|p| p.category == PhonemeCategory::Vowel || p.category == PhonemeCategory::Diphthong)
        .unwrap_or(false)
}

/// Returns true if the symbol is a velar consonant (e.g. /k/, /g/, /ŋ/) having negligible visual visibility.
pub fn is_velar_consonant(symbol: &str) -> bool {
    let norm = normalize_phoneme_symbol(symbol);
    norm == "k" || norm == "g" || norm == "ŋ"
}

/// Returns true if the symbol is a labiodental or bilabial consonant with high visual visibility.
pub fn is_visually_salient(symbol: &str) -> bool {
    let norm = normalize_phoneme_symbol(symbol);
    matches!(
        norm,
        "f" | "v" | "p" | "b" | "m" | "w" | "θ" | "ð" | "uː" | "u"
    )
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

    #[test]
    fn test_all_44_phonemes_present() {
        // 44 standard phonemes + 2 special tokens (<pad>, <sil>) = 46 entries
        assert_eq!(PHONEME_INVENTORY.len(), 46);

        let vowel_count = PHONEME_INVENTORY
            .iter()
            .filter(|p| p.category == PhonemeCategory::Vowel)
            .count();
        assert_eq!(vowel_count, 12, "Should have 12 monophthongs");

        let diphthong_count = PHONEME_INVENTORY
            .iter()
            .filter(|p| p.category == PhonemeCategory::Diphthong)
            .count();
        assert_eq!(diphthong_count, 8, "Should have 8 diphthongs");

        let plosive_count = PHONEME_INVENTORY
            .iter()
            .filter(|p| p.category == PhonemeCategory::Plosive)
            .count();
        assert_eq!(plosive_count, 6, "Should have 6 plosives");

        let fricative_count = PHONEME_INVENTORY
            .iter()
            .filter(|p| p.category == PhonemeCategory::Fricative)
            .count();
        assert_eq!(fricative_count, 9, "Should have 9 fricatives");

        let affricate_count = PHONEME_INVENTORY
            .iter()
            .filter(|p| p.category == PhonemeCategory::Affricate)
            .count();
        assert_eq!(affricate_count, 2, "Should have 2 affricates");

        let nasal_count = PHONEME_INVENTORY
            .iter()
            .filter(|p| p.category == PhonemeCategory::Nasal)
            .count();
        assert_eq!(nasal_count, 3, "Should have 3 nasals");

        let approx_count = PHONEME_INVENTORY
            .iter()
            .filter(|p| p.category == PhonemeCategory::Approximant)
            .count();
        assert_eq!(approx_count, 4, "Should have 4 approximants");

        // Verify total standard phonemes is 44
        assert_eq!(
            vowel_count
                + diphthong_count
                + plosive_count
                + fricative_count
                + affricate_count
                + nasal_count
                + approx_count,
            44
        );
    }

    #[test]
    fn test_phoneme_normalization_and_aliases() {
        assert_eq!(find_phoneme("/i/").unwrap().ipa, "iː");
        assert_eq!(find_phoneme("i").unwrap().ipa, "iː");
        assert_eq!(find_phoneme("i:").unwrap().ipa, "iː");
        assert_eq!(find_phoneme("/θ/").unwrap().ipa, "θ");
        assert_eq!(find_phoneme("tS").unwrap().ipa, "tʃ");
        assert_eq!(find_phoneme("dZ").unwrap().ipa, "dʒ");
        assert_eq!(find_phoneme("3:").unwrap().ipa, "ɜː");
        assert_eq!(find_phoneme("ɝ").unwrap().ipa, "ɜː");
        assert_eq!(find_phoneme("A:").unwrap().ipa, "ɑː");
        assert_eq!(find_phoneme("eI").unwrap().ipa, "eɪ");
        assert!(is_vowel("eɪ"));
        assert!(is_vowel("oʊ"));
    }
}
