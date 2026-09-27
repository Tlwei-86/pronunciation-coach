#!/usr/bin/env python3
"""
Generate golden test dataset for pronunciationCoach conforming to Spec Section 42.

Creates:
- tests/audio/*.wav (16 kHz, 16-bit mono PCM audio)
- tests/expected/evidence/*.evidence.json (Acoustic + Visual evidence)
- tests/expected/score/*.score.json (Phoneme & word scores, diagnosis)
- tests/video/README.md (Visual fixture definitions)
"""

import json
import math
import os
import struct
import wave
from pathlib import Path


def generate_pcm_wav(file_path: Path, duration_sec: float, base_freq: float, formants: list = None, noise_level: float = 0.02):
    """
    Synthesize a valid 16 kHz 16-bit mono PCM WAV file with realistic acoustic formants.
    """
    sample_rate = 16000
    total_samples = int(sample_rate * duration_sec)
    file_path.parent.mkdir(parents=True, exist_ok=True)

    with wave.open(str(file_path), "wb") as wav_file:
        wav_file.setnchannels(1)        # Mono
        wav_file.setsampwidth(2)       # 16-bit (2 bytes)
        wav_file.setframerate(sample_rate)

        raw_data = bytearray()
        for i in range(total_samples):
            t = float(i) / sample_rate
            # Windowing envelope (tukey / smooth trapezoid)
            fade = 1.0
            if i < sample_rate * 0.05:
                fade = i / (sample_rate * 0.05)
            elif i > total_samples - sample_rate * 0.05:
                fade = (total_samples - i) / (sample_rate * 0.05)

            # Base fundamental
            sample_val = 0.3 * math.sin(2.0 * math.pi * base_freq * t)

            # Add formants
            if formants:
                for f_idx, f_freq in enumerate(formants):
                    amp = 0.25 / (f_idx + 1)
                    sample_val += amp * math.sin(2.0 * math.pi * f_freq * t)

            # Pseudo-random noise for frication
            pseudo_noise = (((i * 1103515245 + 12345) & 0x7FFFFFFF) / 0x7FFFFFFF - 0.5) * noise_level
            sample_val += pseudo_noise

            sample_val = max(-1.0, min(1.0, sample_val * fade))
            int_sample = int(sample_val * 32767.0)
            raw_data.extend(struct.pack("<h", int_sample))

        wav_file.writeframes(raw_data)


def main():
    root = Path(__file__).resolve().parent.parent.parent
    tests_dir = root / "tests"
    audio_dir = tests_dir / "audio"
    expected_evidence_dir = tests_dir / "expected" / "evidence"
    expected_score_dir = tests_dir / "expected" / "score"
    video_dir = tests_dir / "video"

    audio_dir.mkdir(parents=True, exist_ok=True)
    expected_evidence_dir.mkdir(parents=True, exist_ok=True)
    expected_score_dir.mkdir(parents=True, exist_ok=True)
    video_dir.mkdir(parents=True, exist_ok=True)

    # 1. Define Golden Audio Test Cases per Section 42
    cases = [
        {
            "id": "funk_good",
            "word": "funk",
            "phonemes": ["f", "ʌ", "ŋ", "k"],
            "target_phoneme": "ʌ",
            "duration": 0.55,
            "base_freq": 130.0,
            "formants": [640.0, 1190.0, 2400.0],  # Standard /ʌ/
            "noise": 0.05,
            "evidence": {
                "target_word": "funk",
                "target_phoneme": "ʌ",
                "audio": {
                    "target_probability": 0.88,
                    "confusions": {"ɑ": 0.06, "ə": 0.03},
                    "duration_ms": 140,
                    "f1_hz": 640,
                    "f2_hz": 1190
                },
                "visual": {
                    "jaw_open": 0.45,
                    "lip_roundness": 0.06,
                    "mouth_stretch": 0.35
                },
                "context": {"previous_attempt_score": 75, "attempt_index": 1}
            },
            "score": {
                "target_word": "funk",
                "overall_score": 92.0,
                "phoneme_scores": {"f": 94.0, "ʌ": 92.0, "ŋ": 91.0, "k": 90.0},
                "acoustic_score": 91.0,
                "visual_score": 93.0,
                "rhythm_score": 94.0,
                "stress_score": 95.0,
                "confidence": "high",
                "error_category": "none"
            }
        },
        {
            "id": "funk_ah_like",
            "word": "funk",
            "phonemes": ["f", "ʌ", "ŋ", "k"],
            "target_phoneme": "ʌ",
            "duration": 0.60,
            "base_freq": 130.0,
            "formants": [780.0, 1150.0, 2500.0],  # Shifted towards /ɑ/
            "noise": 0.04,
            "evidence": {
                "target_word": "funk",
                "target_phoneme": "ʌ",
                "audio": {
                    "target_probability": 0.41,
                    "confusions": {"ɑ": 0.52, "ə": 0.05},
                    "duration_ms": 195,
                    "f1_hz": 780,
                    "f2_hz": 1150
                },
                "visual": {
                    "jaw_open": 0.72,  # Jaw opened too wide for /ʌ/, indicates /ɑ/
                    "lip_roundness": 0.08,
                    "mouth_stretch": 0.38
                },
                "context": {"previous_attempt_score": 62, "attempt_index": 2}
            },
            "score": {
                "target_word": "funk",
                "overall_score": 67.0,
                "phoneme_scores": {"f": 93.0, "ʌ": 52.0, "ŋ": 88.0, "k": 85.0},
                "acoustic_score": 64.0,
                "visual_score": 70.0,
                "rhythm_score": 85.0,
                "stress_score": 88.0,
                "confidence": "high",
                "error_category": "vowel_distortion",
                "diagnosis": "Vowel /ʌ/ pronounced too open, leaning towards /ɑ/. Close jaw slightly."
            }
        },
        {
            "id": "funk_no_k",
            "word": "funk",
            "phonemes": ["f", "ʌ", "ŋ", "k"],
            "target_phoneme": "k",
            "duration": 0.42,
            "base_freq": 130.0,
            "formants": [640.0, 1190.0],
            "noise": 0.01,
            "evidence": {
                "target_word": "funk",
                "target_phoneme": "k",
                "audio": {
                    "target_probability": 0.08,
                    "confusions": {"<blank>": 0.78, "t": 0.04},
                    "duration_ms": 0,
                    "f1_hz": 0,
                    "f2_hz": 0
                },
                "visual": {
                    "jaw_open": 0.30,
                    "lip_roundness": 0.05,
                    "mouth_stretch": 0.30
                },
                "context": {"previous_attempt_score": 58, "attempt_index": 1}
            },
            "score": {
                "target_word": "funk",
                "overall_score": 64.0,
                "phoneme_scores": {"f": 92.0, "ʌ": 90.0, "ŋ": 88.0, "k": 25.0},
                "acoustic_score": 61.0,
                "visual_score": 85.0,
                "rhythm_score": 72.0,
                "stress_score": 80.0,
                "confidence": "high",
                "error_category": "coda_deletion",
                "diagnosis": "Final plosive /k/ was omitted. Release back of tongue from soft palate."
            }
        },
        {
            "id": "ship",
            "word": "ship",
            "phonemes": ["ʃ", "ɪ", "p"],
            "target_phoneme": "ɪ",
            "duration": 0.45,
            "base_freq": 140.0,
            "formants": [430.0, 1950.0, 2600.0],  # Lax /ɪ/
            "noise": 0.08,
            "evidence": {
                "target_word": "ship",
                "target_phoneme": "ɪ",
                "audio": {
                    "target_probability": 0.89,
                    "confusions": {"iː": 0.07, "ɛ": 0.02},
                    "duration_ms": 110,
                    "f1_hz": 430,
                    "f2_hz": 1950
                },
                "visual": {
                    "jaw_open": 0.32,
                    "lip_roundness": 0.05,
                    "mouth_stretch": 0.42
                },
                "context": {"previous_attempt_score": 80, "attempt_index": 1}
            },
            "score": {
                "target_word": "ship",
                "overall_score": 91.0,
                "phoneme_scores": {"ʃ": 92.0, "ɪ": 91.0, "p": 90.0},
                "acoustic_score": 90.0,
                "visual_score": 92.0,
                "confidence": "high",
                "error_category": "none"
            }
        },
        {
            "id": "sheep",
            "word": "sheep",
            "phonemes": ["ʃ", "iː", "p"],
            "target_phoneme": "iː",
            "duration": 0.58,
            "base_freq": 140.0,
            "formants": [300.0, 2300.0, 3000.0],  # Tense /iː/
            "noise": 0.06,
            "evidence": {
                "target_word": "sheep",
                "target_phoneme": "iː",
                "audio": {
                    "target_probability": 0.92,
                    "confusions": {"ɪ": 0.05, "eɪ": 0.01},
                    "duration_ms": 230,
                    "f1_hz": 300,
                    "f2_hz": 2300
                },
                "visual": {
                    "jaw_open": 0.22,
                    "lip_roundness": 0.02,
                    "mouth_stretch": 0.65  # Distinct mouth stretch for tense /iː/
                },
                "context": {"previous_attempt_score": 85, "attempt_index": 1}
            },
            "score": {
                "target_word": "sheep",
                "overall_score": 93.0,
                "phoneme_scores": {"ʃ": 93.0, "iː": 94.0, "p": 92.0},
                "acoustic_score": 93.0,
                "visual_score": 95.0,
                "confidence": "high",
                "error_category": "none"
            }
        },
        {
            "id": "thin",
            "word": "thin",
            "phonemes": ["θ", "ɪ", "n"],
            "target_phoneme": "θ",
            "duration": 0.48,
            "base_freq": 135.0,
            "formants": [450.0, 1850.0, 2500.0],
            "noise": 0.12,  # Dental frication
            "evidence": {
                "target_word": "thin",
                "target_phoneme": "θ",
                "audio": {
                    "target_probability": 0.85,
                    "confusions": {"s": 0.10, "f": 0.03},
                    "duration_ms": 135,
                    "f1_hz": 450,
                    "f2_hz": 1850
                },
                "visual": {
                    "jaw_open": 0.28,
                    "lip_roundness": 0.03,
                    "mouth_stretch": 0.40,
                    "tongue_interdental": 0.85  # Tongue visible between teeth
                },
                "context": {"previous_attempt_score": 70, "attempt_index": 2}
            },
            "score": {
                "target_word": "thin",
                "overall_score": 90.0,
                "phoneme_scores": {"θ": 89.0, "ɪ": 91.0, "n": 90.0},
                "acoustic_score": 88.0,
                "visual_score": 94.0,
                "confidence": "high",
                "error_category": "none"
            }
        },
        {
            "id": "sin",
            "word": "sin",
            "phonemes": ["s", "ɪ", "n"],
            "target_phoneme": "s",
            "duration": 0.46,
            "base_freq": 135.0,
            "formants": [450.0, 1850.0, 2500.0],
            "noise": 0.15,  # Alveolar sibilant frication
            "evidence": {
                "target_word": "sin",
                "target_phoneme": "s",
                "audio": {
                    "target_probability": 0.91,
                    "confusions": {"θ": 0.04, "ʃ": 0.02},
                    "duration_ms": 145,
                    "f1_hz": 450,
                    "f2_hz": 1850
                },
                "visual": {
                    "jaw_open": 0.25,
                    "lip_roundness": 0.03,
                    "mouth_stretch": 0.45,
                    "tongue_interdental": 0.02  # Tongue behind teeth
                },
                "context": {"previous_attempt_score": 88, "attempt_index": 1}
            },
            "score": {
                "target_word": "sin",
                "overall_score": 92.0,
                "phoneme_scores": {"s": 93.0, "ɪ": 92.0, "n": 91.0},
                "acoustic_score": 92.0,
                "visual_score": 92.0,
                "confidence": "high",
                "error_category": "none"
            }
        }
    ]

    for c in cases:
        wav_path = audio_dir / f"{c['id']}.wav"
        generate_pcm_wav(wav_path, c["duration"], c["base_freq"], c["formants"], c["noise"])
        print(f"Generated WAV: {wav_path.name} ({c['duration']}s, 16kHz PCM)")

        evidence_path = expected_evidence_dir / f"{c['id']}.evidence.json"
        with open(evidence_path, "w", encoding="utf-8") as f:
            json.dump(c["evidence"], f, indent=2, ensure_ascii=False)

        score_path = expected_score_dir / f"{c['id']}.score.json"
        with open(score_path, "w", encoding="utf-8") as f:
            json.dump(c["score"], f, indent=2, ensure_ascii=False)

    # 2. Setup tests/video README & metadata
    video_readme = video_dir / "README.md"
    video_readme_content = """# Golden Video Test Fixtures

Conforming to Spec Section 42:
- `w_rounding_good.mp4`: Correct lip rounding gesture for `/w/` (lip roundness > 0.65).
- `w_rounding_bad.mp4`: Insufficient lip rounding for `/w/` (flat lips, lip roundness < 0.20).
- `f_labiodental.mp4`: Lower lip contact against upper incisors for `/f/` (labiodental contact index > 0.70).

In CI/CD environments without full MP4 video files, automated tests utilize MediaPipe facial landmark JSON fixtures or mock frames.
"""
    with open(video_readme, "w", encoding="utf-8") as f:
        f.write(video_readme_content)

    print("Golden test dataset generation complete!")


if __name__ == "__main__":
    main()
