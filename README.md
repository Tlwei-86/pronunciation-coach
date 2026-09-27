# Pronunciation Coach (发音教练)

> Android 多模态英语发音练习指导系统（Rust Core + Kotlin Shell + AI-Native 研发范式）

## 1. 项目简介

本系统以 Android 手机为第一目标平台，利用麦克风与前置摄像头，对用户的语音发音与面部口型进行音素级（Phoneme-level）对齐分析，结合预训练声学模型（Wav2Vec2 CTC）与视觉几何特征（MediaPipe Face Landmarker），给出量化评分、错误归因与针对性纠错建议。

详细技术方案与开发规范请参考核心规范文档：
- **[Pronunciation Coach V0.1 AI-Native Spec](pronunciation_coach_v0.1_ai_native_spec.md)**（系统架构与开发治理统一基准）
- [Requirements & Provider Architecture Draft](pronunciation_coach_requirements_implementation_v0.1_provider_updated.md)

---

## 2. 核心架构与分工

`	ext
Android Framework (Kotlin / Jetpack Compose / CameraX / AudioRecord)
                         │
                      JNI / FFI
                         │
                 Rust Core Engine
       (DSP / Alignment / Evidence / Fusion / Scoring)
                         │
        ┌────────────────┴────────────────┐
        ▼                                 ▼
   Jev Fast Judge               DeepSeek / Monk Coach
 (System-1 概率快速裁决)          (System-2 深度归因与教学指导)
`

- **Rust Core (pronunciation-core/)**：DSP 特征提取、音素对齐、Evidence Engine、多模态融合与确定性评分引擎。
- **Android Shell (ndroid-app/)**：Jetpack Compose 界面、CameraX 视觉采集、AudioRecord 采集、JNI 桥接。
- **GitHub Actions (.github/workflows/)**：云端全自动 CI/CD 矩阵，包含 Rust 测试、NDK 交叉编译及 APK 构建。

---

## 3. 研发范式 (AI-Native)

本项目采用 **AI Agent 主执行、Human Owner 主治理** 的 AI-Native 研发范式：
- **Human Owner**：负责需求定义（WHAT / WHY）、架构门禁（G0~G6 卡点）及最终验收（ACCEPT）。
- **AI Agent**：负责需求拆解、代码实现（HOW / IMPLEMENT）、单元测试、Benchmark 跑测与文档维护。
