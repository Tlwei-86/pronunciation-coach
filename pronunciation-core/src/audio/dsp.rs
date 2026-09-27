//! Digital Signal Processing utilities for speech framing, energy, ZCR, and pitch.

use std::f32::consts::PI;

/// Configuration for audio framing.
#[derive(Debug, Clone, PartialEq)]
pub struct FramingConfig {
    /// Audio sample rate in Hz (e.g. 16000).
    pub sample_rate: u32,
    /// Window/frame size in number of samples (e.g. 320 for 20ms @ 16kHz).
    pub frame_size: usize,
    /// Hop/shift size in number of samples (e.g. 160 for 10ms @ 16kHz).
    pub hop_size: usize,
}

impl Default for FramingConfig {
    fn default() -> Self {
        Self {
            sample_rate: 16_000,
            frame_size: 320, // 20 ms
            hop_size: 160,   // 10 ms
        }
    }
}

/// Computes Hann window coefficient for index `n` of length `size`.
#[inline]
pub fn hann_window(size: usize) -> Vec<f32> {
    if size == 0 {
        return Vec::new();
    }
    if size == 1 {
        return vec![1.0];
    }
    (0..size)
        .map(|n| 0.5 * (1.0 - (2.0 * PI * n as f32 / (size - 1) as f32).cos()))
        .collect()
}

/// Slices an audio sample buffer into overlapping framed windows with Hann window applied.
pub fn frame_audio(samples: &[f32], config: &FramingConfig) -> Vec<Vec<f32>> {
    if samples.is_empty() || config.frame_size == 0 || config.hop_size == 0 {
        return Vec::new();
    }

    let window = hann_window(config.frame_size);
    let mut frames = Vec::new();
    let mut start = 0;

    while start + config.frame_size <= samples.len() {
        let frame: Vec<f32> = samples[start..start + config.frame_size]
            .iter()
            .zip(window.iter())
            .map(|(&s, &w)| s * w)
            .collect();
        frames.push(frame);
        start += config.hop_size;
    }

    frames
}

/// Calculates the Root Mean Square (RMS) energy of a sample buffer.
pub fn rms_energy(samples: &[f32]) -> f32 {
    if samples.is_empty() {
        return 0.0;
    }
    let sum_sq: f32 = samples.iter().map(|&s| s * s).sum();
    (sum_sq / samples.len() as f32).sqrt()
}

/// Calculates the Zero-Crossing Rate (ZCR) normalized by frame length.
pub fn zero_crossing_rate(samples: &[f32]) -> f32 {
    if samples.len() < 2 {
        return 0.0;
    }

    let mut crossings = 0usize;
    for i in 1..samples.len() {
        if (samples[i] >= 0.0 && samples[i - 1] < 0.0) || (samples[i] < 0.0 && samples[i - 1] >= 0.0) {
            crossings += 1;
        }
    }

    crossings as f32 / (samples.len() - 1) as f32
}

/// Estimates pitch fundamental frequency (F0 in Hz) using short-time autocorrelation.
///
/// Returns `None` if energy is too low or no pitch peak is detected within valid human voice range.
pub fn estimate_pitch_autocorr(
    samples: &[f32],
    sample_rate: u32,
    min_pitch_hz: f32,
    max_pitch_hz: f32,
) -> Option<f32> {
    if samples.len() < 64 || sample_rate == 0 {
        return None;
    }

    let energy = rms_energy(samples);
    if energy < 0.01 {
        // Silence or near-silence
        return None;
    }

    let min_lag = (sample_rate as f32 / max_pitch_hz).round() as usize;
    let max_lag = (sample_rate as f32 / min_pitch_hz).round() as usize;

    if min_lag >= samples.len() || max_lag >= samples.len() || min_lag >= max_lag {
        return None;
    }

    // Compute autocorrelation for lags in [min_lag, max_lag]
    let mut best_lag = min_lag;
    let mut max_corr = f32::MIN;
    let r0: f32 = samples.iter().map(|&x| x * x).sum();

    if r0 <= f32::EPSILON {
        return None;
    }

    for lag in min_lag..=max_lag {
        let mut corr = 0.0f32;
        let n = samples.len() - lag;
        for i in 0..n {
            corr += samples[i] * samples[i + lag];
        }
        // Normalized correlation
        let norm_corr = corr / r0;
        if norm_corr > max_corr {
            max_corr = norm_corr;
            best_lag = lag;
        }
    }

    // Require decent periodicity confidence
    if max_corr > 0.3 {
        Some(sample_rate as f32 / best_lag as f32)
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_framing() {
        let config = FramingConfig {
            sample_rate: 16_000,
            frame_size: 4,
            hop_size: 2,
        };
        let samples = vec![1.0, 1.0, 1.0, 1.0, 1.0, 1.0];
        let frames = frame_audio(&samples, &config);
        assert_eq!(frames.len(), 2);
        assert_eq!(frames[0].len(), 4);
    }

    #[test]
    fn test_rms_energy() {
        let empty: [f32; 0] = [];
        assert_eq!(rms_energy(&empty), 0.0);

        let constant = vec![0.5; 100];
        let rms = rms_energy(&constant);
        assert!((rms - 0.5).abs() < 1e-5);
    }

    #[test]
    fn test_zero_crossing_rate() {
        let sine_like = vec![1.0, -1.0, 1.0, -1.0, 1.0];
        let zcr = zero_crossing_rate(&sine_like);
        assert_eq!(zcr, 1.0);

        let positive = vec![1.0, 2.0, 3.0, 4.0];
        assert_eq!(zero_crossing_rate(&positive), 0.0);
    }

    #[test]
    fn test_pitch_estimation_sine() {
        let sample_rate = 16_000;
        let target_freq = 200.0; // 200 Hz
        let n_samples = 800; // 50 ms
        let samples: Vec<f32> = (0..n_samples)
            .map(|i| (2.0 * PI * target_freq * i as f32 / sample_rate as f32).sin())
            .collect();

        let pitch = estimate_pitch_autocorr(&samples, sample_rate, 80.0, 400.0);
        assert!(pitch.is_some());
        let estimated = pitch.unwrap();
        assert!((estimated - target_freq).abs() < 5.0, "Estimated {}, expected {}", estimated, target_freq);
    }
}
