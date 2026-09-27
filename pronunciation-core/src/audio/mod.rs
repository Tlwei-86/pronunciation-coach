pub struct AudioMetrics {
    pub rms_energy: f32,
    pub zero_crossing_rate: f32,
}

pub fn compute_audio_metrics(samples: &[f32]) -> AudioMetrics {
    if samples.is_empty() {
        return AudioMetrics { rms_energy: 0.0, zero_crossing_rate: 0.0 };
    }
    
    let sum_sq: f32 = samples.iter().map(|&s| s * s).sum();
    let rms = (sum_sq / samples.len() as f32).sqrt();
    
    let mut zcr_count = 0;
    for i in 1..samples.len() {
        if (samples[i] >= 0.0 && samples[i - 1] < 0.0) || (samples[i] < 0.0 && samples[i - 1] >= 0.0) {
            zcr_count += 1;
        }
    }
    let zcr = zcr_count as f32 / (samples.len() - 1) as f32;
    
    AudioMetrics {
        rms_energy: rms,
        zero_crossing_rate: zcr,
    }
}
