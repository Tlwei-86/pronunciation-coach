//! Lightweight WAV reader and mock generator for 16-bit PCM mono/stereo.

use thiserror::Error;

#[derive(Error, Debug, PartialEq)]
pub enum AudioError {
    #[error("Audio buffer too short: expected at least {expected} bytes, got {actual}")]
    BufferTooShort { expected: usize, actual: usize },
    #[error("Invalid RIFF header: {0}")]
    InvalidRiff(String),
    #[error("Unsupported WAV format: format_tag {format_tag}, channels {channels}, bits_per_sample {bits_per_sample}")]
    UnsupportedFormat {
        format_tag: u16,
        channels: u16,
        bits_per_sample: u16,
    },
    #[error("Missing 'data' chunk in WAV")]
    MissingDataChunk,
}

/// Parsed WAV audio data.
#[derive(Debug, Clone, PartialEq)]
pub struct WavData {
    pub sample_rate: u32,
    pub channels: u16,
    pub bits_per_sample: u16,
    /// Audio samples converted to normalized floating point [-1.0, 1.0].
    pub samples: Vec<f32>,
}

/// Parses a standard 16-bit PCM RIFF/WAV file buffer.
pub fn parse_wav(bytes: &[u8]) -> Result<WavData, AudioError> {
    if bytes.len() < 44 {
        return Err(AudioError::BufferTooShort {
            expected: 44,
            actual: bytes.len(),
        });
    }

    if &bytes[0..4] != b"RIFF" || &bytes[8..12] != b"WAVE" {
        return Err(AudioError::InvalidRiff("Missing RIFF/WAVE header".into()));
    }

    let mut pos = 12;
    let mut format_tag = 0u16;
    let mut channels = 0u16;
    let mut sample_rate = 0u32;
    let mut bits_per_sample = 0u16;
    let mut data_samples = Vec::new();
    let mut found_data = false;

    while pos + 8 <= bytes.len() {
        let chunk_id = &bytes[pos..pos + 4];
        let chunk_size = u32::from_le_bytes(bytes[pos + 4..pos + 8].try_into().unwrap()) as usize;
        pos += 8;

        if chunk_id == b"fmt " {
            if chunk_size < 16 || pos + chunk_size > bytes.len() {
                return Err(AudioError::InvalidRiff("Corrupt fmt chunk".into()));
            }
            format_tag = u16::from_le_bytes(bytes[pos..pos + 2].try_into().unwrap());
            channels = u16::from_le_bytes(bytes[pos + 2..pos + 4].try_into().unwrap());
            sample_rate = u32::from_le_bytes(bytes[pos + 4..pos + 8].try_into().unwrap());
            bits_per_sample = u16::from_le_bytes(bytes[pos + 14..pos + 16].try_into().unwrap());

            // 1 is standard uncompressed PCM
            if format_tag != 1 || bits_per_sample != 16 {
                return Err(AudioError::UnsupportedFormat {
                    format_tag,
                    channels,
                    bits_per_sample,
                });
            }
        } else if chunk_id == b"data" {
            let data_end = (pos + chunk_size).min(bytes.len());
            let raw_data = &bytes[pos..data_end];
            let num_samples = raw_data.len() / 2;
            data_samples.reserve(num_samples);

            for i in 0..num_samples {
                let sample_int = i16::from_le_bytes(raw_data[i * 2..i * 2 + 2].try_into().unwrap());
                data_samples.push(sample_int as f32 / 32768.0);
            }
            found_data = true;
        }

        pos += chunk_size;
    }

    if !found_data {
        return Err(AudioError::MissingDataChunk);
    }

    Ok(WavData {
        sample_rate,
        channels,
        bits_per_sample,
        samples: data_samples,
    })
}

/// Generates a valid in-memory 16-bit PCM mono WAV file containing a sine wave for testing.
pub fn generate_mock_wav(sample_rate: u32, duration_secs: f32, freq_hz: f32) -> Vec<u8> {
    let num_samples = (sample_rate as f32 * duration_secs) as usize;
    let byte_rate = sample_rate * 2; // 1 channel * 16 bits = 2 bytes per sample
    let data_chunk_size = (num_samples * 2) as u32;
    let file_size = 36 + data_chunk_size;

    let mut buf = Vec::with_capacity((file_size + 8) as usize);

    // RIFF header
    buf.extend_from_slice(b"RIFF");
    buf.extend_from_slice(&file_size.to_le_bytes());
    buf.extend_from_slice(b"WAVE");

    // fmt subchunk
    buf.extend_from_slice(b"fmt ");
    buf.extend_from_slice(&16u32.to_le_bytes()); // Subchunk1Size (16 for PCM)
    buf.extend_from_slice(&1u16.to_le_bytes());  // AudioFormat (1 = PCM)
    buf.extend_from_slice(&1u16.to_le_bytes());  // NumChannels (1 = Mono)
    buf.extend_from_slice(&sample_rate.to_le_bytes()); // SampleRate
    buf.extend_from_slice(&byte_rate.to_le_bytes());   // ByteRate
    buf.extend_from_slice(&2u16.to_le_bytes());  // BlockAlign
    buf.extend_from_slice(&16u16.to_le_bytes()); // BitsPerSample

    // data subchunk
    buf.extend_from_slice(b"data");
    buf.extend_from_slice(&data_chunk_size.to_le_bytes());

    for i in 0..num_samples {
        let t = i as f32 / sample_rate as f32;
        let val = (2.0 * std::f32::consts::PI * freq_hz * t).sin();
        let sample_i16 = (val * 32767.0).clamp(-32768.0, 32767.0) as i16;
        buf.extend_from_slice(&sample_i16.to_le_bytes());
    }

    buf
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_mock_wav_generation_and_parsing() {
        let sample_rate = 16_000;
        let duration = 0.5; // 0.5 sec
        let freq = 440.0;
        let wav_bytes = generate_mock_wav(sample_rate, duration, freq);

        let parsed = parse_wav(&wav_bytes).expect("Failed to parse generated WAV");
        assert_eq!(parsed.sample_rate, 16_000);
        assert_eq!(parsed.channels, 1);
        assert_eq!(parsed.bits_per_sample, 16);
        assert_eq!(parsed.samples.len(), (16_000.0 * duration) as usize);

        // Verify peak amplitude is close to 1.0
        let max_amp = parsed.samples.iter().fold(0.0f32, |m, &s| m.max(s.abs()));
        assert!((max_amp - 1.0).abs() < 0.05);
    }

    #[test]
    fn test_short_buffer_error() {
        let bytes = vec![0u8; 10];
        assert!(matches!(parse_wav(&bytes), Err(AudioError::BufferTooShort { .. })));
    }
}
