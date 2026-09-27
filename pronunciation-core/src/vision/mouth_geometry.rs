//! Mouth geometry representations extracted from MediaPipe Face Landmarker.

use serde::{Deserialize, Serialize};

/// Normalized geometric measurements of the mouth and lips (0.0 to 1.0).
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct MouthGeometry {
    /// Degree of vertical jaw opening (0.0 = fully closed, 1.0 = wide open).
    pub jaw_open: f32,
    /// Degree of lip rounding / pucker (0.0 = completely flat/spread, 1.0 = tightly rounded).
    pub lip_roundness: f32,
    /// Horizontal mouth corner-to-corner width (normalized).
    pub mouth_width: f32,
    /// Degree of lip closure / contact (0.0 = open lips, 1.0 = fully pressed together).
    pub lip_closure: f32,
    /// Degree of horizontal mouth stretch / grin (0.0 = neutral/compressed, 1.0 = maximum stretch).
    pub mouth_stretch: f32,
}

impl Default for MouthGeometry {
    fn default() -> Self {
        Self {
            jaw_open: 0.0,
            lip_roundness: 0.0,
            mouth_width: 0.5,
            lip_closure: 0.0,
            mouth_stretch: 0.0,
        }
    }
}

impl MouthGeometry {
    /// Constructs a new MouthGeometry, clamping all values to [0.0, 1.0].
    pub fn new(
        jaw_open: f32,
        lip_roundness: f32,
        mouth_width: f32,
        lip_closure: f32,
        mouth_stretch: f32,
    ) -> Self {
        Self {
            jaw_open: jaw_open.clamp(0.0, 1.0),
            lip_roundness: lip_roundness.clamp(0.0, 1.0),
            mouth_width: mouth_width.clamp(0.0, 1.0),
            lip_closure: lip_closure.clamp(0.0, 1.0),
            mouth_stretch: mouth_stretch.clamp(0.0, 1.0),
        }
    }

    /// Computes the Euclidean distance to a target/reference geometry.
    pub fn distance_to(&self, other: &MouthGeometry) -> f32 {
        let d_jaw = self.jaw_open - other.jaw_open;
        let d_round = self.lip_roundness - other.lip_roundness;
        let d_width = self.mouth_width - other.mouth_width;
        let d_close = self.lip_closure - other.lip_closure;
        let d_stretch = self.mouth_stretch - other.mouth_stretch;

        (d_jaw * d_jaw
            + d_round * d_round
            + d_width * d_width
            + d_close * d_close
            + d_stretch * d_stretch)
            .sqrt()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_mouth_geometry_clamping() {
        let geom = MouthGeometry::new(1.5, -0.5, 0.5, 2.0, -1.0);
        assert_eq!(geom.jaw_open, 1.0);
        assert_eq!(geom.lip_roundness, 0.0);
        assert_eq!(geom.mouth_width, 0.5);
        assert_eq!(geom.lip_closure, 1.0);
        assert_eq!(geom.mouth_stretch, 0.0);
    }

    #[test]
    fn test_distance_to() {
        let a = MouthGeometry::new(0.5, 0.2, 0.5, 0.0, 0.3);
        let b = MouthGeometry::new(0.5, 0.2, 0.5, 0.0, 0.3);
        assert_eq!(a.distance_to(&b), 0.0);
    }
}
