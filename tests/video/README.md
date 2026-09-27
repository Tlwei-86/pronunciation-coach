# Golden Video Test Fixtures

Conforming to Spec Section 42:
- `w_rounding_good.mp4`: Correct lip rounding gesture for `/w/` (lip roundness > 0.65).
- `w_rounding_bad.mp4`: Insufficient lip rounding for `/w/` (flat lips, lip roundness < 0.20).
- `f_labiodental.mp4`: Lower lip contact against upper incisors for `/f/` (labiodental contact index > 0.70).

In CI/CD environments without full MP4 video files, automated tests utilize MediaPipe facial landmark JSON fixtures or mock frames.
