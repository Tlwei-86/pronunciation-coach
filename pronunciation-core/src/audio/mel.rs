//! Mel scale and spectral feature computation.

use std::f32::consts::PI;

/// Converts Frequency in Hertz to Mel scale.
#[inline]
pub fn hz_to_mel(hz: f32) -> f32 {
    2595.0 * (1.0 + hz / 700.0).log10()
}

/// Converts Mel scale to Frequency in Hertz.
#[inline]
pub fn mel_to_hz(mel: f32) -> f32 {
    700.0 * (10.0f32.powf(mel / 2595.0) - 1.0)
}

/// Computes the magnitude/power spectrum of a frame using standard DFT.
pub fn power_spectrum(frame: &[f32], fft_size: usize) -> Vec<f32> {
    let n = frame.len().min(fft_size);
    let num_bins = fft_size / 2 + 1;
    let mut power = vec![0.0f32; num_bins];

    for k in 0..num_bins {
        let mut real = 0.0f32;
        let mut imag = 0.0f32;
        let angle_step = 2.0 * PI * (k as f32) / (fft_size as f32);

        for t in 0..n {
            let angle = angle_step * (t as f32);
            real += frame[t] * angle.cos();
            imag -= frame[t] * angle.sin();
        }

        power[k] = (real * real + imag * imag) / (fft_size as f32);
    }

    power
}

/// Triangular Mel filterbank.
#[derive(Debug, Clone)]
pub struct MelFilterbank {
    pub num_filters: usize,
    pub fft_size: usize,
    pub filters: Vec<Vec<f32>>,
}

impl MelFilterbank {
    /// Constructs a triangular Mel filterbank spanning `[low_freq, high_freq]`.
    pub fn new(
        num_filters: usize,
        fft_size: usize,
        sample_rate: u32,
        low_freq: f32,
        high_freq: f32,
    ) -> Self {
        let num_bins = fft_size / 2 + 1;
        let low_mel = hz_to_mel(low_freq);
        let high_mel = hz_to_mel(high_freq);
        let mel_step = (high_mel - low_mel) / (num_filters + 1) as f32;

        let mut mel_points = Vec::with_capacity(num_filters + 2);
        for i in 0..=(num_filters + 1) {
            mel_points.push(low_mel + i as f32 * mel_step);
        }

        let bin_points: Vec<usize> = mel_points
            .iter()
            .map(|&m| {
                let hz = mel_to_hz(m);
                let bin = (hz * fft_size as f32 / sample_rate as f32).round() as usize;
                bin.min(num_bins - 1)
            })
            .collect();

        let mut filters = Vec::with_capacity(num_filters);

        for m in 1..=num_filters {
            let mut filter = vec![0.0f32; num_bins];
            let left = bin_points[m - 1];
            let center = bin_points[m];
            let right = bin_points[m + 1];

            if center > left {
                for k in left..center {
                    filter[k] = (k - left) as f32 / (center - left) as f32;
                }
            }
            if right > center {
                for k in center..=right {
                    filter[k] = (right - k) as f32 / (right - center) as f32;
                }
            }

            filters.push(filter);
        }

        Self {
            num_filters,
            fft_size,
            filters,
        }
    }

    /// Applies the Mel filterbank to a power spectrum and returns logarithmic Mel energies.
    pub fn filter(&self, power_spec: &[f32]) -> Vec<f32> {
        let mut mel_energies = Vec::with_capacity(self.num_filters);
        for filter in &self.filters {
            let mut energy = 0.0f32;
            for (k, &w) in filter.iter().enumerate() {
                if k < power_spec.len() {
                    energy += power_spec[k] * w;
                }
            }
            // Log energy with floor to prevent log(0)
            mel_energies.push((energy.max(1e-6)).ln());
        }
        mel_energies
    }
}

/// Simple formant peak estimator (estimating F1 and F2 in Hz) from power spectrum.
/// F1 is typically between 300 - 1000 Hz, F2 between 800 - 2500 Hz.
pub fn estimate_formants(power_spec: &[f32], sample_rate: u32, fft_size: usize) -> (f32, f32) {
    let hz_per_bin = sample_rate as f32 / fft_size as f32;
    let num_bins = power_spec.len();

    // Helper to find peak in frequency range [min_hz, max_hz]
    let find_peak = |min_hz: f32, max_hz: f32| -> f32 {
        let start_bin = (min_hz / hz_per_bin).floor() as usize;
        let end_bin = ((max_hz / hz_per_bin).ceil() as usize).min(num_bins);

        if start_bin >= end_bin || start_bin >= num_bins {
            return (min_hz + max_hz) / 2.0;
        }

        let mut max_val = f32::MIN;
        let mut peak_bin = start_bin;

        for b in start_bin..end_bin {
            if power_spec[b] > max_val {
                max_val = power_spec[b];
                peak_bin = b;
            }
        }

        peak_bin as f32 * hz_per_bin
    };

    let f1 = find_peak(300.0, 1000.0);
    let f2 = find_peak(f1 + 200.0, 2600.0);

    (f1, f2)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_mel_conversion_roundtrip() {
        let hz = 1000.0;
        let mel = hz_to_mel(hz);
        let back = mel_to_hz(mel);
        assert!((hz - back).abs() < 1e-2);
    }

    #[test]
    fn test_mel_filterbank() {
        let fb = MelFilterbank::new(10, 64, 16_000, 100.0, 8000.0);
        assert_eq!(fb.num_filters, 10);
        let dummy_spec = vec![1.0; 33];
        let energies = fb.filter(&dummy_spec);
        assert_eq!(energies.len(), 10);
    }

    #[test]
    fn test_formant_estimator() {
        let mut spec = vec![0.0f32; 65]; // fft_size = 128, bins = 65, hz_per_bin = 16000 / 128 = 125 Hz
        // Place a peak at 625 Hz (bin 5) and 1250 Hz (bin 10)
        spec[5] = 10.0;
        spec[10] = 8.0;

        let (f1, f2) = estimate_formants(&spec, 16_000, 128);
        assert_eq!(f1, 625.0);
        assert_eq!(f2, 1250.0);
    }
}
