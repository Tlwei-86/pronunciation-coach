# Android 多模态英语发音练习指导系统

## 需求总结与实现路径

**版本：V0.1 技术方案草案（AI-Native 开发模式更新）**  
**日期：2026-09-27**

---

## 1. 项目目标

开发一套以 Android 手机为第一目标平台的英语（优先美式英语）发音练习指导系统。系统同时利用麦克风与摄像头，对用户的语音发音和可见口型进行分析，并给出音素级错误定位、量化评分、纠正建议和后续练习。

核心目标不是普通 ASR 的“是否识别出单词”，而是回答：

- 用户是否真正发出了目标音素；
- 发音偏向哪个易混音素；
- 问题来自元音质量、辅音实现、时长、重音、节奏还是其他声学因素；
- 可见的唇形、下颌开合、圆唇、展唇、闭唇等动作是否符合目标发音；
- 用户下一步应如何调整，并练习什么。

首个典型验证词可使用 `funk /fʌŋk/`，重点验证 `/ʌ/` 与 `/ɑ/`、`/ə/` 等混淆，以及词尾 `/k/` 的实现。

---

## 2. 关键约束

### 2.1 第一阶段平台

- Android 手机优先；
- ARM64 (`arm64-v8a`) 优先；
- 摄像头通过 CameraX；
- 麦克风通过 AudioRecord；
- **Rust 作为产品核心主开发语言**，负责算法、DSP、音素处理、Evidence、融合、评分与策略；
- **Kotlin 作为 Android 平台适配语言**，负责 Jetpack Compose、CameraX、AudioRecord、MediaPipe、权限、生命周期与平台 Glue；
- 本地推理优先使用 ONNX Runtime；
- 视觉几何分析优先使用 MediaPipe Face Landmarker；
- 从 V0.1 开始即保持 `pronunciation-core` 与 Android 平台层解耦，为未来 Linux/K230D 迁移做准备。

### 2.2 零自训练原则

第一阶段尽量满足：

- 不自行采集训练集；
- 不自行人工标注训练集；
- 不训练自有神经网络；
- 不依赖自有 GPU 训练环境；
- 优先使用公开预训练模型、现成 MediaPipe 模型、语言学规则和公开 benchmark；
- 允许对阈值、权重、rubric 做工程校准，因为这不等同于重新训练神经网络。

### 2.3 云端模型约束

当前方案将高层 AI 能力限定为：

- **Jev：结构化、概率化、受约束决策；**
- **DeepSeek：复杂推理、错误归因、教学解释和练习规划。**

不要求 DeepSeek 或 Jev 直接充当原始音频/视频测量仪器。

---

## 3. 总体设计原则

系统采用“**测量 → 快速裁决 → 深度推理 → 确定性评分 → 教学反馈**”的分层架构。

```text
                     Android
                        │
        ┌───────────────┴───────────────┐
        │                               │
   AudioRecord                       CameraX
        │                               │
        ▼                               ▼
  Acoustic Frontend              MediaPipe
        │                               │
        ▼                               ▼
 Pretrained ONNX                Mouth / Face
 Acoustic/Phoneme Model         Measurements
        │                               │
        └──────────────┬────────────────┘
                       ▼
                 Evidence Engine
                       │
                       ▼
                  Evidence JSON
                       │
             ┌─────────┴─────────┐
             ▼                   ▼
            Jev              DeepSeek
       Fast Judgment        Deep Reasoning
             │                   │
             └─────────┬─────────┘
                       ▼
             Consistency / Policy
                       │
                       ▼
            Deterministic Scorer
                       │
             ┌─────────┴─────────┐
             ▼                   ▼
       Stable Score          AI Feedback
                                 │
                                 ▼
                           Next Exercise
```

最重要的职责边界是：

> **ONNX/MediaPipe 负责测量，Jev 负责判，DeepSeek 负责想清楚并教，Rust Core 负责 Evidence、融合、评分与策略，Kotlin 负责 Android 平台能力。**

### 3.1 Rust-first，但不追求纯 Rust Android

采用“Rust Core + Kotlin Shell”：

```text
Android Framework / Compose / CameraX / AudioRecord / MediaPipe
                         │
                       Kotlin
                         │
                    JNI / FFI
                         │
                    Rust Core
                         │
       DSP / Alignment / Evidence / Fusion / Scoring / Policy
```

这样做的目的不是减少 Kotlin，而是确保算法核心从第一天就可在 Android、Linux 与未来 K230D 之间复用。

---

## 4. Android 本地音频分析

### 4.1 音频采集

建议初始配置：

- 16 kHz PCM；
- 单声道；
- 保留单调时钟时间戳；
- 分帧进行 VAD、特征提取和模型推理；
- 避免把 Android ASR 的单词识别结果直接等同于发音准确度。

### 4.2 本地预训练声学模型

V0.1 不再笼统地写“选择一个 ONNX phoneme model”，而是先锁定一个主模型族，再通过 Android 实测确定部署量化版本。

#### 4.2.1 首选模型族

首选基础模型：

```text
facebook/wav2vec2-lv-60-espeak-cv-ft
```

推荐原因：

- 直接进行 phoneme recognition，而不是只输出文字；
- CTC 输出天然适合 forced alignment；
- 16 kHz 单声道语音输入；
- 使用 eSpeak IPA 风格的音素标签；
- 能输出 frame-level logits / posterior；
- 有现成 ONNX 导出；
- 基础模型许可证为 Apache-2.0；
- 不要求自行训练。

V0.1 采用同一模型族的多个 ONNX 版本进行 benchmark，避免因为模型结构变化而混入额外变量。

#### 4.2.2 Golden Reference

首选参考模型：

```text
sadda-speech/wav2vec2-espeak-ctc
```

定位：

> Golden Reference Model，用于 PC 基准、Android 精度对照和量化误差评估，不作为最终 APK 默认内置模型。

特征：

- 16 kHz mono waveform 输入；
- 输出约 50 fps 的 CTC logits；
- 输出维度为 392 个 eSpeak IPA token；
- FP16 ONNX 大约 635 MB。

参考模型的主要用途不是追求移动端速度，而是建立“未重度量化时应该得到什么 logits / alignment”的对照基线。

#### 4.2.3 Android 部署候选

优先 benchmark：

```text
onnx-community/wav2vec2-lv-60-espeak-cv-ft-ONNX
```

候选版本：

| 版本 | 约模型大小 | V0.1 定位 |
|---|---:|---|
| FP16 | 632 MB | 精度参考 |
| INT8 | 318 MB | 主要候选 |
| Q4 | 242 MB | 次要候选 |
| Q4F16 | 197 MB | 主要候选 |

当前工程假设：

```text
Golden Reference: FP16
实际部署候选: INT8 / Q4F16
```

不提前宣布 Q4F16 为最终版本，因为本系统依赖的不只是 ASR 文本正确率，而是 logits、posterior margin 和 confusion 排序。4-bit 量化对这些细粒度证据的影响必须实测。

#### 4.2.4 第二参考模型

英语专用参考候选：

```text
wav2vec2-large-lv60 phoneme model fine-tuned on TIMIT
```

作用：

- English-specific phoneme recognition 对照；
- 用于验证 eSpeak 模型在美式英语易混音素上的表现。

不作为 V0.1 Android 主模型，原因：

- Large 级模型较重；
- TIMIT 域较窄；
- Android ONNX 工程链不如首选模型族统一。

#### 4.2.5 Allosaurus 的定位

Allosaurus 可作为 PC 侧 universal phone recognizer 对照，尤其可用于检查某些 phoneme confusion。

但 V0.1 不将其作为 Android 主模型，主要原因：

- 工程链偏 Python/PyTorch；
- Android 部署成本较高；
- GPL-3.0 对闭源/商业产品需要额外许可证审查。

#### 4.2.6 不作为主声学底座的模型

普通 Whisper、普通 Wav2Vec2 ASR、Paraformer 等文字识别模型，不作为 pronunciation acoustic backbone。

原因：

```text
ASR text correctness != pronunciation correctness
```

用户即使把 `/ʌ/` 发得接近 `/ɑ/`，ASR 仍可能正确识别单词。

这些模型最多用于：

- 确认用户是否读了目标词；
- 辅助语句级转写；
- 教练模式下的自由对话。

不用于核心 phoneme scoring。

#### 4.2.7 声学层目标输出

例如目标词：

```text
funk → /f ʌ ŋ k/
```

声学层理想输出：

```text
/f/  probability = 0.93
/ʌ/  probability = 0.57
/ɑ/  probability = 0.31
/ŋ/  probability = 0.91
/k/  probability = 0.76
```

真正需要关注的不是 Top-1 phoneme，而是：

- target posterior；
- nearest confusion posterior；
- posterior margin；
- phoneme time boundary；
- insertion / deletion evidence；
- duration。

由此可以识别 `/ʌ/` 存在向 `/ɑ/` 偏移的可能，而不是只知道 ASR 最终识别出了 `funk`。

### 4.3 补充声学特征

除神经网络 posterior 外，可增加无需训练的传统声学特征：

- duration；
- F0 / pitch；
- energy；
- F1/F2 等 formant；
- consonant release 特征；
- silence / pause；
- stress / rhythm 特征。

这些特征作为 Evidence，而不是单独决定最终评分。


### 4.4 预训练模型 Benchmark 方案

V0.1 模型选型采用“同一录音、多个量化版本并行比较”的方式，不靠纸面参数决定。

开发期保留：

```text
models/
├── phoneme_fp16.onnx
├── phoneme_int8.onnx
├── phoneme_q4.onnx
└── phoneme_q4f16.onnx
```

建立一个 50~100 个词的小型评估集，不用于训练，只用于工程验证。应覆盖：

- `/ʌ/ vs /ɑ/`；
- `/ɪ/ vs /i/`；
- `/ʊ/ vs /u/`；
- `/æ/ vs /ɛ/`；
- `/θ/ vs /s/`；
- `/ð/ vs /z/`；
- `/r/ vs /l/`；
- `/f/ vs /v/`；
- final `/k/`、`/t/`、`/d/`；
- consonant clusters；
- 不同录音距离与轻微环境噪声。

对同一 WAV 分别运行 FP16、INT8、Q4、Q4F16，比较：

- phoneme boundary difference；
- target posterior difference；
- Top-2 confusion ordering；
- posterior margin；
- KL divergence / logit similarity；
- model load time；
- single-word inference latency；
- peak RAM；
- sustained temperature / battery impact；
- 崩溃与兼容性。

初始工程验收目标：

```text
phoneme boundary difference < 20 ms
target posterior delta 尽量 < 0.05
Top-2 confusion ordering 尽量与 FP16 一致
```

这些只是 V0.1 工程阈值，不视为最终语言学标准。

### 4.5 ONNX Runtime 推进策略

P1 首先使用：

```text
ONNX Runtime Android
+
CPU / XNNPACK
```

目标是验证：

- 模型输出正确性；
- Android ARM64 延迟；
- 内存峰值；
- 连续推理温升；
- FP16 / INT8 / Q4F16 的 evidence fidelity。

只有 CPU/XNNPACK 无法满足产品需求时，P2/P3 再评估：

```text
NNAPI
QNN
custom ORT build
```

V0.1 不在模型尚未验证前投入 NPU 专项优化。

### 4.6 ONNX Runtime 放置位置

V0.1 优先：

```text
AudioRecord (Kotlin)
      ↓
Rust DSP / framing
      ↓
Kotlin ORT Adapter
      ↓
ONNX Runtime Android
      ↓
logits
      ↓
Rust alignment / Evidence
```

原因是先降低集成风险，避免同时调试：

```text
JNI
+
Rust FFI
+
ORT C API
```

当模型选型完成后，再决定是否把推理层下沉到 Rust Core，以增强 Android / Linux / K230D 的复用性。

---

## 5. Android 本地视觉分析

### 5.1 CameraX

使用：

```text
CameraX Preview
+
CameraX ImageAnalysis
```

Preview 用于用户实时查看自己的口型，ImageAnalysis 用于向 MediaPipe 提供帧。

所有视频帧必须携带单调时钟时间戳，以便与音素时间段对齐。

### 5.2 MediaPipe Face Landmarker

第一阶段不训练视觉神经网络，直接利用 MediaPipe 提取：

- 嘴唇关键点；
- jaw opening；
- mouth width；
- lip roundness / pucker；
- mouth stretch；
- lip closure；
- 其他可稳定提取的 blendshape / geometry 特征。

转换为统一特征，例如：

```json
{
  "jaw_open": 0.71,
  "lip_roundness": 0.08,
  "mouth_width": 0.59,
  "mouth_stretch": 0.34,
  "lip_closure": 0.03
}
```

### 5.3 Phoneme 与 Viseme 分离

摄像头不应强行识别不可见的音素。

例如 `/p/`、`/b/`、`/m/` 都具有明显双唇闭合特征；视觉系统更适合输出：

```text
bilabial_closure = 0.94
```

而不是声称：

```text
phoneme = /p/
```

同样，`/k/`、`/g/`、`/ŋ/` 的关键构音位置主要位于口腔内部，因此视觉权重应很低，主要依靠音频判断。

---

## 6. 音视频同步

这是系统从第一版开始就必须正确设计的基础设施。

```text
Audio frame ───── timestamp ───┐
                               │
Phoneme span ─── start/end ────┼→ Alignment
                               │
Video frame ───── timestamp ───┘
```

例如：

```text
/ʌ/ = 120 ms ~ 285 ms
```

系统只分析对应时间窗口附近的嘴型变化，而不是拿整个单词的视频平均值判断 `/ʌ/`。

建议保留：

- 音频 frame timestamp；
- Camera frame timestamp；
- phoneme start/end；
- 关键构音帧；
- 前后约 50~150 ms 的上下文窗口。

---

## 7. Evidence Engine

本地测量结果统一转换为结构化 Evidence JSON。

示例：

```json
{
  "target_word": "funk",
  "target_phoneme": "ʌ",
  "audio": {
    "target_probability": 0.57,
    "confusions": {
      "ɑ": 0.31,
      "ə": 0.07
    },
    "duration_ms": 148,
    "f1_hz": 710,
    "f2_hz": 1180
  },
  "visual": {
    "jaw_open": 0.71,
    "lip_roundness": 0.08,
    "mouth_stretch": 0.34
  },
  "context": {
    "previous_attempt_score": 66,
    "attempt_index": 3
  }
}
```

Evidence JSON 是整个系统最重要的模型解耦接口。

以后替换 ONNX 模型、MediaPipe、Jev 或 DeepSeek，都尽量不改变上层协议。

---

## 8. Jev 的职责

Jev 定位为 **Fast Judgment / System-One Decision Layer**。

适合处理：

- 有限选项分类；
- 概率输出；
- pass/retry；
- pronunciation severity；
- likely confusion；
- feedback priority；
- 是否需要调用 DeepSeek；
- DeepSeek 推理强度/路由决策。

示例问题：

```text
accuracy?
- native_like
- minor_deviation
- noticeable_deviation
- major_deviation
- incorrect

likely_confusion?
- none
- /ɑ/
- /ə/
- /ɜ/

needs_repeat?
- yes
- no
```

Jev 的理想输出是概率分布，而不是教学文本。

```text
noticeable_deviation  0.64
minor_deviation       0.21
major_deviation       0.11

confusion /ɑ/         0.76
needs_repeat          0.83
```

### Jev 不负责

- 自由文本教学；
- 长链条语言学解释；
- 自由生成 0~100 分；
- 替代声学模型分析原始波形；
- 替代 MediaPipe 测量口型。

---

## 9. DeepSeek 的职责

DeepSeek 定位为 **Deep Reasoning + AI Coach**。

主要处理：

- 综合多个 Evidence；
- 判断错误原因；
- 分析声学证据与视觉证据是否一致；
- 将技术测量转化为用户可以理解的指导；
- 生成练习步骤；
- 选择 minimal pairs；
- 根据历史表现规划下一练习；
- 对复杂或低置信度案例进行二次分析。

DeepSeek 不应自由决定最终 0~100 分，而应输出固定 Schema 的诊断。

示例：

```json
{
  "primary_issue": "vowel_quality",
  "likely_confusion": "ɑ",
  "cause": "The vowel is too open and shifts toward /ɑ/.",
  "visual_support": "Jaw opening is larger than desired.",
  "correction": [
    "Reduce jaw opening slightly",
    "Keep the tongue more central and relaxed",
    "Practice /ʌ/ alone before returning to the word"
  ],
  "next_exercise": "minimal_pair_ʌ_ɑ"
}
```

---

## 10. Jev 与 DeepSeek 的协作策略

采用串并联混合，而不是每次固定同时调用两个模型。

```text
                   Evidence
                      │
                      ▼
                     Jev
                      │
          ┌───────────┼───────────┐
          │           │           │
      高置信度      中置信度      冲突/复杂
          │           │           │
          ▼           ▼           ▼
     Local Policy  DeepSeek   DeepSeek Deep Reasoning
          │           │           │
          └───────────┴───────────┘
                      │
                      ▼
               Final Policy
```

### 路由原则

例如：

```text
Jev confidence >= 0.90
且 evidence 一致
→ 直接执行本地 policy
→ DeepSeek 仅在需要生成教学语言时调用
```

```text
0.65 <= Jev confidence < 0.90
→ DeepSeek 进行综合诊断
```

```text
Jev confidence < 0.65
或音频/视觉/Jev 判断明显冲突
→ DeepSeek 深度分析
→ 必要时降低最终 confidence
→ 要求用户重新发音
```

具体阈值需要通过公开 benchmark 和实际测试校准，不作为固定理论常数。

---

## 11. Consistency Resolver

系统必须显式处理模型之间的冲突。

例如：

```text
Acoustic evidence:
/ʌ/ → /ɑ/ confusion strong

Jev:
noticeable_deviation = 0.82

DeepSeek:
minor_deviation
```

不应该简单平均两个模型。

Resolver 应重新检查：

1. 原始 acoustic evidence；
2. visual evidence 是否对当前音素有判断价值；
3. Jev confidence；
4. DeepSeek 的理由是否被 Evidence 支持；
5. 当前输入质量；
6. 是否需要用户重读。

对于证据不足的情况，正确行为不是“硬给一个精确分数”，而是：

```text
confidence = low
request_repeat = true
```

---

## 12. Deterministic Scoring Engine

最终分数由普通代码产生，而不是由 Jev 或 DeepSeek自由生成。

概念模型：

```text
FinalScore =
    AcousticScore
  + VisualContribution
  + PronunciationSeverityAdjustment
  + ConsistencyAdjustment
```

不同 phoneme 使用不同视觉权重。

例如工程初始策略：

```text
/f/, /v/, /θ/, /ð/, /w/
→ 视觉证据具有较高价值

/p/, /b/, /m/
→ 闭唇视觉证据具有辅助价值

/k/, /g/, /ŋ/
→ 视觉权重接近零，以声学证据为主
```

最终权重必须通过 benchmark 校准，而不是把经验值当成语言学标准。

### 推荐输出

不要只输出：

```text
总分：76
```

而应输出：

```text
funk                 76

/f/                  94
/ʌ/                  61   ← 主要问题
/ŋ/                  91
/k/                  73   ← 次要问题

声音准确度           72
口型准确度           84
节奏                 89
重音                 93

系统置信度           高
```

---

## 13. AI 教学闭环

系统最终目标不是“评分器”，而是“会纠错的发音老师”。

闭环：

```text
用户发音
   ↓
测量
   ↓
音素级错误定位
   ↓
Jev 快速裁决
   ↓
DeepSeek 原因分析
   ↓
生成一个具体动作建议
   ↓
安排下一练习
   ↓
用户再次发音
   ↓
比较改善幅度
```

例如：

```text
目标：funk /fʌŋk/

发现：
/ʌ/ 向 /ɑ/ 偏移
jaw opening 偏大

指导：
先减少下颌开口，保持嘴唇自然不圆，单独练 /ʌ/。

下一练习：
/ʌ/ → fun → funk

随后：
minimal pair /ʌ/ vs /ɑ/
```

---

## 14. 无需训练的实现路径

### V1 不训练方案

```text
CameraX
   +
MediaPipe Face Landmarker
   +
AudioRecord
   +
Pretrained ONNX acoustic/phoneme model
   +
CTC/forced alignment
   +
Acoustic feature extraction
   +
Evidence Engine
   +
Jev
   +
DeepSeek
   +
Deterministic Scorer
```

需要开发的是：

- Android 数据采集；
- 时间同步；
- 模型集成；
- Evidence Schema；
- phoneme/viseme 规则；
- Jev decision schema；
- DeepSeek diagnosis schema；
- scoring rubric；
- benchmark / calibration 工具。

不需要开发的是：

- 自有模型训练流水线；
- 自建大规模语音训练集；
- 自建视频训练集；
- 大规模人工标注平台。

---

## 15. 推荐代码模块

```text
pronunciation-coach/
│
├── android-app/                         # Kotlin / Android 平台层
│   ├── ui/                              # Compose
│   ├── camera/                          # CameraX
│   ├── audio/                           # AudioRecord / Android audio glue
│   ├── mediapipe/                       # Face Landmarker
│   ├── permissions/
│   ├── lifecycle/
│   ├── network/                         # Jev / DeepSeek HTTP provider
│   └── bridge/                          # JNI / FFI adapter
│
├── pronunciation-core/                  # Rust，产品核心主语言
│   ├── audio/
│   │   ├── dsp.rs
│   │   ├── resample.rs
│   │   ├── mel.rs
│   │   ├── formant.rs
│   │   └── prosody.rs
│   ├── phoneme/
│   │   ├── inventory.rs
│   │   ├── alignment.rs
│   │   ├── posterior.rs
│   │   └── confusion.rs
│   ├── vision/
│   │   ├── mouth_geometry.rs
│   │   └── viseme.rs
│   ├── evidence/
│   │   ├── schema.rs
│   │   └── fusion.rs
│   ├── scoring/
│   │   ├── rubric.rs
│   │   ├── scorer.rs
│   │   └── confidence.rs
│   ├── policy/
│   │   ├── jev.rs
│   │   ├── deepseek.rs
│   │   └── resolver.rs
│   └── exercises/
│
├── inference/
│   ├── onnx-android/                    # V0.1 可先由 Kotlin 调 ORT
│   └── onnx-native/                     # 后续评估 Rust + ORT C API
│
├── models/
│   ├── README.md
│   └── download_models.py
│
├── tools/
│   └── python/
│       ├── model_validation/
│       ├── benchmark/
│       └── calibration/
│
├── datasets/
│   └── README.md
│
└── .github/
    └── workflows/
        ├── android.yml
        ├── rust.yml
        └── release.yml
```

---

## 16. Rust 主语言与 Kotlin 平台层边界

### 16.1 语言定位

本项目从 V0.1 起明确采用：

> **Rust 是产品核心主语言，Kotlin 是 Android 平台适配语言。**

不追求“100% Rust Android”，也不采用 Kotlin-only。目标是让真正需要跨平台复用、可测试、对性能敏感的核心能力进入 Rust，而把 Android Framework 相关能力留在 Kotlin。

### 16.2 Rust 负责

- DSP、重采样、FFT / filterbank；
- 声学特征与 formant / prosody 处理；
- phoneme alignment；
- posterior / confusion 处理；
- mouth geometry 与 viseme 规则；
- Audio / Visual Evidence Schema；
- multimodal fusion；
- deterministic scoring；
- confidence / consistency resolver；
- Jev 请求数据构建与返回结果解释；
- DeepSeek 诊断 Schema 与策略路由；
- exercise policy；
- 可在 Android / Linux / K230D 间复用的业务核心。

### 16.3 Kotlin 负责

- Jetpack Compose；
- Activity / ViewModel / Lifecycle；
- CameraX；
- AudioRecord；
- MediaPipe Android API；
- 权限；
- Android 文件、网络和后台生命周期；
- ONNX Runtime Java/Kotlin API（V0.1 优先）；
- Jev / DeepSeek HTTP 调用；
- JNI/FFI bridge；
- Android 设备调试与 UI。

### 16.4 JNI 原则

JNI 只传递“批量、结构化、低频边界数据”，避免细粒度高频跨语言调用。

推荐：

```text
20~40 ms PCM chunk   → Rust
一帧 mouth features  → Rust
完整 Evidence        ↔ Rust
PronunciationResult  ← Rust
```

不推荐：

```text
每个 audio sample → JNI
每个 landmark 单独 → JNI
每个 score 字段单独 → JNI
```

### 16.5 阶段占比目标

- P0-P2：Kotlin 约 60~70%，Rust 约 30~40%，优先打通 Android 采集、MediaPipe 与 ONNX；
- P3-P5：Rust 约 50~60%，Evidence、alignment、fusion、scoring 逐步进入 Core；
- 成熟版本：Rust 约 65~75%，Kotlin 约 25~35%。

这里的“占比”指业务/算法代码规模，不是 APK 二进制占比。

## 17. 云端安全

不要在 APK 中硬编码长期 Jev/DeepSeek API Key。

生产架构建议：

```text
Android
   ↓
Application Backend
   ↓
Jev / DeepSeek API
```

后端负责：

- API Key；
- 用户鉴权；
- 限流；
- provider routing；
- 请求审计；
- prompt/schema 版本控制；
- 成本统计。

开发阶段可以使用本地安全配置，但必须排除出 Git 仓库。

---

## 18. 隐私设计

优先原则：**能在本地变成特征的数据，不上传原始数据。**

默认上传：

```text
Evidence JSON
```

而不是：

```text
完整麦克风录音
完整摄像头视频
```

只有未来确实需要云端多模态模型检查原始音视频时，才考虑在用户明确知情的前提下上传短音频或关键帧。

本地 MediaPipe + ONNX 的价值之一，就是减少云端隐私暴露和带宽成本。

---

## 19. Benchmark 与校准

“不自己训练”不等于“不验证”。

必须使用公开 pronunciation assessment / speech benchmark 或具有合法使用权限的公开数据，对以下指标进行验证：

- phoneme alignment accuracy；
- confusion detection；
- score consistency；
- test-retest stability；
- Jev decision stability；
- DeepSeek diagnosis consistency；
- 不同手机麦克风鲁棒性；
- 不同摄像头、角度和光照鲁棒性；
- 不同说话人和口音下的表现。

第一阶段重点不是追求一个“看起来很精确”的 0~100 数字，而是证明：

> 系统能够稳定找出真正需要改善的音素，并给出方向正确、可执行的纠正建议。

---

## 20. 分阶段推进

### P0 - Android 采集基础 + Rust Core 骨架

实现：

- CameraX；
- AudioRecord；
- 权限；
- 单调时间戳；
- 录音/摄像同步日志；
- `pronunciation-core` Rust crate；
- Android JNI bridge 最小闭环；
- GitHub Actions 同时运行 `cargo test` 与 Android build。

验收：音视频时间轴稳定，可复现实验。

### P1 - MediaPipe 视觉测量

实现：

- Face Landmarker；
- mouth ROI；
- landmarks；
- blendshape / geometry；
- 可视化 debug overlay。

验收：能稳定观察圆唇、展唇、开口、闭唇等变化。

### P2 - ONNX 声学底座

实现：

- 以 `facebook/wav2vec2-lv-60-espeak-cv-ft` 模型族作为 V0.1 主基线；
- FP16 作为 Golden Reference；
- INT8 与 Q4F16 作为 Android 主要部署候选；
- Android ONNX Runtime；
- CPU/XNNPACK 基线；
- phoneme posterior；
- CTC forced alignment；
- duration/confusion 输出；
- 量化版本对比 benchmark。

验收：

- `funk /fʌŋk/` 等测试词能够输出合理音素时间段和候选混淆；
- INT8/Q4F16 与 FP16 的 Top-2 confusion 排序基本一致；
- phoneme boundary 初始目标误差小于约 20 ms；
- target posterior delta 初始目标尽量小于 0.05；
- 至少确定一个 Android 默认部署候选。

### P3 - Evidence Engine

统一音频与视觉数据：

```text
Audio Evidence
+
Visual Evidence
→ Evidence JSON
```

验收：相同输入可得到稳定、可记录、可回放的 Evidence。

### P4 - Jev Fast Judge

实现有限选项：

- accuracy；
- confusion；
- repeat；
- priority；
- confidence；
- DeepSeek routing。

验收：相同 Evidence 多次调用结果具有足够稳定性。

### P5 - DeepSeek AI Coach

实现：

- 固定 JSON Schema；
- issue diagnosis；
- correction；
- next exercise；
- 用户可理解的中英文反馈。

验收：反馈必须被 Evidence 支持，不允许编造不存在的测量结果。

### P6 - Deterministic Scoring

实现：

- phoneme score；
- audio score；
- visual score；
- overall score；
- confidence；
- conflict resolver。

验收：同一 Evidence 必须产生完全相同的最终分数。

### P7 - 公共 Benchmark 校准

验证：

- correlation；
- repeatability；
- confusion matrix；
- device robustness；
- Jev/DeepSeek disagreement cases。

根据结果调整规则、权重和阈值，但仍不要求训练新模型。

### P8 - 实时教练体验

最终形成：

```text
AI 给目标音
→ 用户发音
→ 本地快速反馈
→ Jev 判断
→ DeepSeek 解释
→ 给一个动作建议
→ 用户立即重读
→ 显示改善幅度
```

---

## 21. V1 明确不做的事情

为控制风险和复杂度，V1 暂不：

- 自训练 audio-visual Transformer；
- 自训练口型分类网络；
- 自建大规模标注数据集；
- 直接让 DeepSeek 自由生成评分；
- 直接让 Jev 替代 acoustic model；
- 固定 50/50 融合音频和视频；
- 对 `/k/`、`/g/`、`/ŋ/` 等不可见构音强行做视觉判断；
- 为追求“纯 Rust”而重写 CameraX、MediaPipe、Compose 等 Android 平台能力；
- 一开始就做 NPU/QNN 专项优化；
- 把普通 ASR 识别正确率当成 pronunciation accuracy。

---

## 22. 成功标准

### MVP 成功

系统针对目标词能够：

1. 获取稳定音视频；
2. 对齐目标 phoneme；
3. 输出音素级声学 Evidence；
4. 输出可见构音 Evidence；
5. Jev 给出稳定的受约束判断；
6. DeepSeek 给出 Evidence-grounded 纠错建议；
7. 相同 Evidence 的最终评分完全可复现；
8. 用户重读后能够量化显示改善或退步。

### 下一阶段成功

在公开 benchmark 和真实设备测试中证明：

- 错误方向识别具有实用准确度；
- 评分具有合理的重复性；
- 不同设备下结果变化可控；
- AI 指导能帮助用户在后续尝试中改善目标音素。

---

## 23. 当前推荐技术基线

```text
UI                 Kotlin + Jetpack Compose
Camera             CameraX
Audio              AudioRecord
Face/Mouth         MediaPipe Face Landmarker
Local Inference    ONNX Runtime Android
Acoustic Model     Pretrained phoneme/acoustic model（待专项选型）
Alignment          CTC/forced alignment
Evidence           JSON Schema
Fast Judge         Jev
Deep Reasoning     DeepSeek
Final Score        Deterministic local/backend code
Model Tooling      Python
Core Language       Rust（V0.1 起作为核心主语言）
Android Platform     Kotlin（平台适配层）
CI/CD              GitHub Actions
```

---

## 24. 下一步优先事项

当前最大的不确定项不是 Jev 或 DeepSeek，而是 **Android 本地预训练声学/音素模型的选型**。

下一阶段建议优先完成专项筛选：

1. 搜索无需自行训练的美式英语 phoneme/acoustic 模型；
2. 确认模型 license；
3. 确认是否能够导出 ONNX；
4. 比较模型大小和 Android ARM64 推理速度；
5. 比较 phoneme inventory；
6. 验证 `/ʌ/`、`/ɑ/`、`/ə/`、`/ɪ/`、`/i/`、`/θ/`、`/s/` 等关键混淆；
7. 确定 V1 声学模型；
8. 再实现完整 `funk` 端到端原型。

---

## 25. 一句话技术路线

> **以 Rust 作为产品核心主语言、Kotlin 作为 Android 平台适配层：Android 使用 ONNX 声学模型“听”、MediaPipe“看”，Rust 将测量结果统一为 Evidence 并负责融合、评分和策略；Jev 做快速概率化裁决与路由，DeepSeek 做复杂错误归因与教学，最终由确定性 Scoring Engine 输出可复现的音素级分数和练习策略，全流程第一阶段不自行训练模型。**

## 21. V0.1 预训练声学模型决策

### 当前决策

```text
Base Model Family
└── facebook/wav2vec2-lv-60-espeak-cv-ft

Golden Reference
└── sadda-speech/wav2vec2-espeak-ctc FP16

Android Deployment Candidates
├── INT8
└── Q4F16
```

### 暂不采用为主模型

```text
TIMIT Wav2Vec2 phoneme model
→ English-specific benchmark

Allosaurus
→ PC reference / secondary checker

Whisper / general ASR
→ text recognition only
```

### 最终选择规则

模型最终选择不按“参数量最小”决定，而按：

```text
40% phoneme evidence fidelity
25% Android inference latency
15% memory
10% model/package size
10% runtime stability
```

其中 **phoneme evidence fidelity** 是最高优先级，因为后续 Jev 与 DeepSeek 的判断质量直接依赖 target posterior、confusion posterior 和 alignment 的可信度。

如果 Q4F16 能保持 FP16 的关键 evidence 排序和时间边界稳定性，则优先 Q4F16；如果 4-bit 量化导致 posterior / confusion 明显失真，则退回 INT8。

---

## 22. V0.1 当前正式技术基线

```text
Core language:
Rust

Android platform:
Kotlin + Jetpack Compose

Camera:
CameraX

Vision:
MediaPipe Face Landmarker

Audio:
AudioRecord / 16 kHz mono PCM

Acoustic model family:
Wav2Vec2 LV-60 eSpeak CTC

Golden acoustic model:
FP16 ONNX

Android acoustic candidates:
INT8 / Q4F16

Inference:
ONNX Runtime Android
CPU / XNNPACK first

Alignment:
CTC forced alignment

Fast decision:
Jev

Primary deep reasoning / teaching:
DeepSeek

Optional low-cost reasoning provider:
Monk

Final score:
Rust deterministic scoring engine

Training:
No self-training in V0.1

Primary ABI:
arm64-v8a

CI:
GitHub Actions
```

---

## 23. 下一步实施重点

V0.1 进入工程阶段后，优先顺序：

1. 固定 Android + Rust JNI 工程骨架；
2. 跑通 AudioRecord 16 kHz PCM；
3. 下载 FP16 Golden Reference；
4. 在 PC 上建立固定 WAV golden tests；
5. 接入 Android ONNX Runtime；
6. 在同一录音上比较 FP16 / INT8 / Q4F16；
7. 实现 CTC forced alignment；
8. 输出 target / confusion posterior；
9. 再接入 MediaPipe 视觉 Evidence；
10. 最后接 Jev 与 DeepSeek。

核心原则：

> **先证明声学 Evidence 可信，再做评分；先证明评分稳定，再让 Jev 与 DeepSeek 参与决策。**

---

## 24. Cloud Reasoning Provider 架构

V0.1 不把任何单一云模型写死到核心业务代码，而是定义统一 Provider 抽象。

```text
Rust Core
   ↓
Evidence JSON
   ↓
Jev Router
   ↓
ReasoningProvider
   ├── DeepSeekProvider
   ├── MonkProvider
   └── LocalRuleProvider
```

推荐接口语义：

```rust
pub trait ReasoningProvider {
    fn diagnose(
        &self,
        evidence: &PronunciationEvidence,
        policy: &ReasoningPolicy,
    ) -> Result<DiagnosisResult, ProviderError>;
}
```

### 24.1 DeepSeek 的定位

DeepSeek 作为 V0.1 的主 Cloud Reasoning Provider。

职责：

- 多证据复杂推理；
- 发音错误归因；
- 将声学 / 视觉 / Jev 结果组织成可解释诊断；
- 生成中文或英文教学反馈；
- 选择下一步练习；
- 处理较复杂的 evidence 冲突；
- 输出固定 JSON Schema。

不负责：

- 原始音频测量；
- 口型几何测量；
- 直接生成最终 0~100 分数。

### 24.2 Monk 的定位

Monk 作为：

```text
低成本实验 Provider
+
fallback Provider
+
开发期 Agent / Coding Provider
```

主要用途：

- 快速教学反馈；
- 低成本诊断实验；
- Provider 容灾；
- Codex / OpenCode 等开发辅助场景；
- 与 DeepSeek 做一致性和稳定性 benchmark。

V0.1 不把 Monk 设为唯一生产 Provider。

原因：

- Monk 属于第三方融合/路由服务；
- 上游模型组合可能发生变化；
- 模型组成、稳定性和 SLA 不如直接调用官方模型可控；
- 不适合承载原始音频、视频和敏感用户信息。

### 24.3 LocalRuleProvider

必须保留一个完全离线的 LocalRuleProvider。

作用：

- 云 API 不可用时仍可完成基础评分；
- 给出固定规则式纠正建议；
- 为 DeepSeek / Monk 输出提供 sanity check；
- 支撑离线 MVP；
- 作为自动化测试中的 deterministic baseline。

---

## 25. Jev 与 Provider 的分工

Jev 不负责长文本生成，而负责：

```text
bounded decision
+
probability
+
confidence
+
routing
+
retry policy
```

Jev 可以根据 Evidence 决定：

```text
confidence >= 0.90
→ LocalRule / fast response

0.65 <= confidence < 0.90
→ DeepSeek normal reasoning

confidence < 0.65
or evidence conflict
→ DeepSeek deep reasoning

Cloud provider unavailable
→ Monk fallback or LocalRule

开发期低成本批量实验
→ Monk
```

推荐结构：

```text
             Evidence
                │
                ▼
               Jev
                │
       ┌────────┼────────┐
       │        │        │
       ▼        ▼        ▼
 LocalRule   DeepSeek    Monk
  fast       primary    fallback
       │        │        │
       └────────┼────────┘
                ▼
       Consistency Resolver
                ▼
      Deterministic Scorer
```

---

## 26. Provider 输入数据边界

云端默认只发送结构化 Evidence，不发送原始传感器数据。

推荐：

```json
{
  "target_phoneme": "ʌ",
  "target_probability": 0.54,
  "nearest_confusion": "ɑ",
  "confusion_probability": 0.31,
  "duration_ratio": 1.10,
  "jaw_open": 0.71,
  "lip_roundness": 0.08,
  "jev": {
    "accuracy": "noticeable_deviation",
    "confidence": 0.82
  }
}
```

默认不上传：

- 原始 PCM；
- 原始录像；
- 连续摄像头帧；
- 用户姓名；
- 账号信息；
- 与发音分析无关的设备信息；
- 私有源码；
- 敏感日志。

原则：

> 原始音视频本地处理，云端只做结构化推理。

---

## 27. Provider 安全与密钥管理

禁止把长期 API Key 硬编码进 APK。

推荐：

```text
Android
  ↓
轻量 Backend / Token Service
  ↓
DeepSeek / Monk
```

开发阶段可临时使用：

```text
local.properties
environment variable
debug-only secret
```

但必须：

- 不提交 GitHub；
- 不写入源码；
- 不写入日志；
- 不打入 release APK；
- 支持按 Provider 独立撤销。

---

## 28. DeepSeek vs Monk Benchmark

V0.1 应建立专门的 reasoning benchmark，而不是凭主观体验决定 Provider。

使用同一批 Evidence JSON，分别调用：

```text
DeepSeek
Monk
```

每个样本重复调用至少 20 次，比较：

- JSON Schema 合规率；
- classification 一致率；
- diagnosis 一致率；
- 同一输入重复稳定性；
- latency P50 / P95；
- timeout / error rate；
- token / 调用成本；
- 是否出现 evidence hallucination；
- 是否覆盖 Jev 高置信度结论；
- 是否输出超出 rubric 的自由判断。

核心指标：

```text
稳定性
> 模型文采

证据忠实度
> 推理长度

结构化输出成功率
> 自由文本丰富度
```

推荐目标：

```text
JSON schema valid rate >= 99%
same-evidence classification consistency >= 95%
hallucinated evidence rate <= 1%
```

这些是 V0.1 工程目标，不代表最终认证级标准。

---

## 29. Provider 失败与降级策略

必须从一开始设计降级路径：

```text
DeepSeek timeout
→ Monk

Monk timeout
→ LocalRule

Jev confidence high
→ 可直接 LocalRule，不强制调用云端

网络不可用
→ 本地评分 + 本地固定反馈
```

禁止：

```text
云模型失败
→ 整个发音评分不可用
```

评分核心必须独立于云服务。

---

## 30. V0.1 Provider 决策

正式决策：

```text
Primary Cloud Reasoning:
DeepSeek

Secondary / Fallback / Low-cost Experiment:
Monk

Fast bounded decision:
Jev

Offline fallback:
LocalRuleProvider

Final score:
Rust Deterministic Scoring Engine
```

因此 V0.1 的核心分工为：

```text
ONNX / MediaPipe
→ 测量

Jev
→ 快速裁决与路由

DeepSeek
→ 主深度推理与教学

Monk
→ 低成本实验 / fallback / 开发辅助

Rust Scoring Engine
→ 最终分数和置信度
```

---

## 31. 更新后的端到端数据流

```text
AudioRecord
   ↓
Rust DSP
   ↓
ONNX phoneme model
   ↓
phoneme Evidence
                         \
                          \
CameraX → MediaPipe → visual Evidence
                            │
                            ▼
                      Evidence JSON
                            │
                            ▼
                           Jev
                            │
                   confidence / routing
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
        LocalRule       DeepSeek         Monk
                          │               │
                          └──────┬────────┘
                                 ▼
                       Consistency Resolver
                                 ▼
                    Rust Deterministic Scorer
                                 ▼
                   score + confidence + diagnosis
                                 ▼
                            Android UI
```

---

## 32. 更新后的下一步优先级

1. 完成 Android + Rust JNI 骨架；
2. 完成 AudioRecord 16 kHz PCM；
3. 完成 FP16 / INT8 / Q4F16 声学模型 benchmark；
4. 完成 CTC forced alignment；
5. 完成 MediaPipe 视觉 Evidence；
6. 建立统一 Evidence JSON Schema；
7. 接入 Jev；
8. 实现 `ReasoningProvider` 抽象；
9. 接入 DeepSeek 主 Provider；
10. 接入 Monk Provider；
11. 建立 DeepSeek vs Monk 20 次重复稳定性测试；
12. 实现 fallback / LocalRule 降级路径；
13. 以 `funk` 做完整端到端验证。

---

## 33. AI-Native 开发模式

V0.1 正式采用：

> **AI Agent 主执行，人类 Owner 主治理。**

职责划分：

```text
Human Owner
├── 需求定义
├── 范围控制
├── 架构/模型关键决策
├── 风险接受
├── 过程审核
└── 最终产品验收

AI Agents
├── 需求拆解
├── 架构细化
├── 编码
├── 测试
├── Benchmark
├── CI/CD
├── 缺陷修复
├── 文档
├── 代码审查
└── 验证报告
```

简化表达：

```text
Human:
WHAT / WHY / ACCEPT

AI:
HOW / IMPLEMENT / TEST / VERIFY / DOCUMENT
```

---

## 34. Agent 角色模型

### 34.1 Lead Agent

负责：

- 将 Owner 需求转成 Epic / Issue；
- 拆分依赖；
- 维护阶段路线；
- 决定哪些任务可并行；
- 汇总风险；
- 输出阶段 Decision Report。

Lead Agent 不直接拥有最终技术决策权。

### 34.2 Builder Agent

负责：

- Rust Core；
- Kotlin Android 层；
- JNI；
- ONNX adapter；
- MediaPipe adapter；
- Jev / DeepSeek / Monk Provider；
- CI；
- 测试工具。

每个 Builder 任务必须附带测试，不允许只提交实现。

### 34.3 Verifier Agent

必须独立于 Builder 的结论。

负责：

- 重新运行测试；
- Golden Test；
- Benchmark；
- Android 真机自动验证；
- 日志分析；
- 性能检查；
- 输出 Verification Package。

### 34.4 Reviewer Agent

负责：

- 代码审查；
- 架构边界检查；
- JNI / 内存 / 并发风险；
- 安全和隐私检查；
- Benchmark 方法检查；
- 找反例；
- 检查是否存在 cherry-picking。

### 34.5 Release Agent

负责：

- 生成 APK；
- 汇总 changelog；
- 整理验证报告；
- 生成 Release Candidate；
- 准备 Human Acceptance Test 清单。

Release Agent 无权自行发布正式版本。

---

## 35. Agent 权限边界

| 操作 | AI Agent | Human Owner |
|---|---|---|
| 写代码 | 主负责 | 审核 |
| 写测试 | 主负责 | 抽查 |
| 修复 Bug | 主负责 | 关键问题审核 |
| CI/CD | 主负责 | 审核策略 |
| Benchmark | 主负责 | 审核方法与结论 |
| 文档 | 主负责 | 审核 |
| PR Review | 主负责 | 关键 PR 复审 |
| 普通依赖升级 | 可自主 | 抽查 |
| 核心模型更换 | 提案 | 决策 |
| 架构变化 | 提案 | 决策 |
| 用户隐私策略 | 提案 | 决策 |
| API 成本策略 | 提案 | 决策 |
| Release Candidate | 准备 | 验收 |
| 正式 Release | 不允许 | 批准 |
| 最终体验结论 | 不允许替代 | 验收 |

---

## 36. GitHub 工作流

V0.1 采用 Monorepo + Issue 驱动。

```text
Requirement
   ↓
Epic
   ↓
Issues
   ↓
Agent Branch
   ↓
Implementation + Tests
   ↓
Pull Request
   ↓
CI
   ↓
Verifier Agent
   ↓
Reviewer Agent
   ↓
Verification Package
   ↓
Human Gate（需要时）
   ↓
Merge
```

Branch 模式：

```text
main
├── feat/audio-pipeline
├── feat/rust-jni
├── feat/phoneme-model
├── feat/ctc-alignment
├── feat/mediapipe
├── feat/jev-provider
└── feat/deepseek-provider
```

禁止长期开发分支。

---

## 37. Verification Package

任何关键 Issue / PR 不得仅以“完成”作为交付。

必须包含：

```text
Implementation
- 修改内容
- 关键设计决策

Tests
- unit tests
- integration tests
- golden tests

Verification
- 结果
- 指标
- 日志

Regression
- cargo test
- cargo clippy
- Android build
- existing golden set

Artifacts
- benchmark.json
- benchmark.md
- logs
- APK（如适用）

Open Risks
- 未解决问题
- 已知限制
```

示例：

```text
Task: CTC forced alignment

✓ Rust implementation
✓ 128 unit tests
✓ 40 golden WAV tests
✓ FP16 / INT8 comparison
✓ boundary P95 = 14.6 ms
✓ target posterior delta P95 = 0.041
✓ Android arm64 build passed

Open risk:
Q4F16 /θ/ vs /s/ confusion stability below target
```

---

## 38. Definition of Done

一个 Issue 只有同时满足以下条件才算 Done：

### 38.1 Code

- 代码已合并；
- 无未处理编译警告；
- Rust `cargo fmt` 通过；
- Rust `cargo clippy` 通过；
- Kotlin lint / build 通过。

### 38.2 Tests

- 新功能具备单元测试；
- 关键路径具备 integration test；
- 不允许删除失败测试来“修复 CI”。

### 38.3 Verification

- Verifier 独立复跑；
- 有 machine-readable result；
- 有失败条件；
- Benchmark 不只输出平均值，应至少包含 P50/P95 或分布指标。

### 38.4 Documentation

- API / Schema 改动同步更新；
- 架构改变同步更新技术文档；
- 已知限制必须记录。

### 38.5 Regression

- 不破坏已通过的 Golden Set；
- 不降低当前性能基线而不说明原因。

---

## 39. Human Gates

减少人工中断，但保留关键控制点。

### G0 需求冻结

Human Owner 决定：

- V0.1 做什么；
- 不做什么；
- 验收目标。

### G1 架构冻结

审核：

- Rust / Kotlin 边界；
- ONNX / MediaPipe 数据流；
- Provider 架构；
- 隐私边界。

### G2 P0 工程闭环

Agent 提交：

- 可编译 APK；
- Rust JNI；
- Audio / Camera pipeline；
- CI 报告。

Human Owner 只审核阶段报告。

### G3 声学模型选型

Agent 提交：

```text
FP16
INT8
Q4F16
```

完整 benchmark。

Human Owner 决策：

```text
采用 Q4F16
or
采用 INT8
or
继续测试
```

### G4 多模态评分闭环

Human Owner 真机测试：

- 发音错误是否检测正确；
- 口型反馈是否合理；
- 分数是否稳定。

### G5 AI Provider

审核：

- Jev / DeepSeek / Monk 分工；
- 稳定性；
- 成本；
- 降级链路。

### G6 V0.1 RC

Human Owner 做最终验收并决定发布。

---

## 40. Agent-Friendly 架构要求

为了让 AI Agent 能承担大部分验证，所有输入源必须可替换。

### AudioSource

```text
AudioSource
├── MicrophoneSource
└── WavFileSource
```

### VideoSource

```text
VideoSource
├── CameraSource
└── VideoFileSource
```

### ReasoningProvider

```text
ReasoningProvider
├── DeepSeekProvider
├── MonkProvider
└── LocalRuleProvider
```

这样 Agent 可以自动执行：

```text
WAV + MP4
   ↓
完整 pipeline
   ↓
Evidence
   ↓
Jev
   ↓
Provider
   ↓
Score
   ↓
Feedback
```

无需真人站在手机前。

---

## 41. 自动真机验证

Android 真机需支持 Agent 驱动测试。

### 自动化能力

```text
adb install
adb shell am start
logcat capture
screen capture
performance stats
test artifact export
```

### 音频测试模式

App 增加：

```text
--test-audio=<wav>
```

或 Debug Menu：

```text
Test Source:
Microphone / WAV
```

Agent 可以：

```text
固定 WAV
→ App
→ ONNX
→ Evidence
→ 导出 JSON
```

### 视频测试模式

Debug 模式允许：

```text
Camera / MP4
```

这样 MediaPipe 可用固定视频回归。

### 自动输出

```text
results/
├── evidence.json
├── score.json
├── timing.json
├── perf.json
├── logcat.txt
└── screenshots/
```

---

## 42. Golden Test 体系

V0.1 必须建设 Golden Test，不允许主要依赖人工“感觉”。

目录：

```text
tests/
├── audio/
│   ├── funk_good.wav
│   ├── funk_ah_like.wav
│   ├── funk_no_k.wav
│   ├── ship.wav
│   ├── sheep.wav
│   ├── thin.wav
│   └── sin.wav
│
├── video/
│   ├── w_rounding_good.mp4
│   ├── w_rounding_bad.mp4
│   └── f_labiodental.mp4
│
└── expected/
    ├── evidence/
    └── score/
```

Golden Test 用于：

- Agent 自动回归；
- 模型量化比较；
- 算法修改前后 diff；
- Android 与 PC 输出一致性验证。

---

## 43. CI 设计

拆分 Workflow：

```text
rust-ci.yml
android-ci.yml
model-benchmark.yml
provider-benchmark.yml
release-candidate.yml
```

### rust-ci

```text
cargo fmt --check
cargo clippy
cargo test
```

### android-ci

```text
build Rust arm64-v8a
copy .so
Gradle test
assembleDebug
```

### model-benchmark

输入：

```text
Golden WAV Set
```

输出：

```text
FP16 / INT8 / Q4F16
posterior diff
alignment diff
latency
memory
```

### provider-benchmark

输入：

```text
固定 Evidence JSON
```

比较：

```text
DeepSeek
Monk
```

重复调用并输出稳定性。

---

## 44. P0-P5 Agent 推进方式

### P0

Agent：

- 建仓；
- Android Compose；
- Rust crate；
- JNI；
- CI。

Human：

- 审核 G1/G2。

### P1

Agent：

- AudioRecord；
- WAV source；
- timestamp；
- Golden Tests。

Human：

- 无需日常参与。

### P2

Agent：

- FP16 / INT8 / Q4F16；
- ONNX Runtime；
- CTC alignment；
- benchmark。

Human：

- G3 模型决策。

### P3

Agent：

- CameraX；
- MediaPipe；
- VideoFileSource；
- visual Evidence；
- fusion。

Human：

- G4 真机体验。

### P4

Agent：

- Jev；
- scoring；
- confidence；
- retry policy。

Human：

- 审核评分逻辑。

### P5

Agent：

- DeepSeek；
- Monk；
- Provider benchmark；
- fallback；
- LocalRule。

Human：

- G5 决策。

---

## 45. AI Agent 自主推进规则

Agent 可自主：

- 修复 CI；
- 增加测试；
- 重构内部实现；
- 优化非核心性能；
- 改善文档；
- 增加可观测性；
- 修复普通 Bug。

Agent 必须停止并提交 Decision Request 的情况：

```text
核心模型更换
API Provider 主从关系改变
评分 rubric 改变
音频/视频上传策略改变
隐私边界改变
许可证风险
架构跨层依赖增加
性能优化导致精度下降
Golden Set 出现系统性回归
```

Decision Request 必须包含：

```text
Problem
Options
Evidence
Tradeoffs
Recommendation
Rollback Plan
```

---

## 46. Human Acceptance Test

最终验收不重复 Agent 已自动完成的测试。

Human 重点验证：

- 使用体验；
- 反馈是否真的有教学价值；
- 是否指出正确发音问题；
- 是否存在明显误导；
- 分数的主观合理性；
- 重复发音结果是否稳定；
- 交互延迟是否可接受。

建议首批人工场景：

```text
funk
ship / sheep
full / fool
thin / sin
fan / van
rice / lice
```

Human 验收的价值是：

> 判断产品是否“有用、可信、可教”，而不是替代机器做回归测试。

---

## 47. V0.1 AI-Native 开发模式最终定义

```text
Development model:
AI-Agent-driven

Human role:
Product / Technical Owner

Repository:
GitHub Monorepo

Core language:
Rust

Android platform:
Kotlin

Coding:
AI Builder Agents

Verification:
AI Verifier Agents

Review:
AI Reviewer + Human critical review

CI:
GitHub Actions

Physical verification:
Automated Android device tests

Final product validation:
Human Owner

Decision authority:
Human Owner
```

核心原则：

> **AI Agent 对“实现与验证证据”负责；Human Owner 对“需求、关键决策与最终产品结果”负责。**
