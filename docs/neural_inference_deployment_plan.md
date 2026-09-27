# 🎯 端侧神经网络推理层实装与一加 8 (OnePlus 8) 部署实测方案

> **文档状态**：待核验基线 (Ready for Review)  
> **版本**：v1.0.0 (Neural Inference Pipeline)  
> **目标设备**：一加 8 (OnePlus 8 IN2010, Qualcomm Snapdragon 865, 12GB RAM, Android 16)  
> **核心原则**：**杜绝任何启发式猜测与静态硬编码假数据；全链路采用真实物理文件与神经网络推理算子；先以标准测试音频/图片替代用户行为进行对抗实测**。

---

## 一、 任务背景与核心目标

在上一阶段中，项目虽然跑通了 Android 硬件调用（`AudioRecord` 麦克风与 `CameraX` 前置摄像头）以及 Rust 原生评分内核，但由于声学端采用简易频带能量比（DSP 猜测）、视觉端采用粗糙的人脸边界，导致系统出现“发音很不准，却依然能打 89 分”的虚假高分问题。

**本次任务核心目标**：
1. **全面装配端侧真实神经网络推理层**：
   - **视觉通道**：采用 Google MediaPipe Tasks Vision / ML Kit 高精口唇几何测量（提取上唇、下唇、嘴角物理关键点，计算真实归一化下颌开度 `jawOpen` 与唇形圆度 `lipRoundness`）；
   - **声学通道**：采用 Sherpa-ONNX / Wav2Vec2 CTC 端侧轻量级音素识别模型，输出时间帧维度的音素后验概率张量，解算英文 42 个音标的真实对齐得分；
2. **测试驱动（物理测试物料代替人工）**：
   - 彻底废除写死数字的 Preset 按钮；
   - 采用真实的二进制音频文件（`.wav`）与真实人脸图片文件（`.jpg`）作为推理输入；
3. **一加 8 真机部署与现场对抗实测**：
   - 验证一加 8 硬件执行神经网络算子的性能（延迟 < 250ms，内存消耗 < 200MB）；
   - 通过**负样本音频（如非目标中文语料）**与**故意读错音频（如大开口 /ɑ/ 混淆）**，验证系统能够真实打出 30~55 分的不及格分数，并标红错误音素。

---

## 二、 一加 8 硬件承载能力实测基线

通过 ADB 实时从目标设备 (`e3193eb7`) 采集的真实系统参数：

| 评估维度 | 一加 8 实测硬件参数 | 神经网络推理层实际需求 | 承载能力评级 |
| :--- | :--- | :--- | :---: |
| **SoC 芯片平台** | 高通骁龙 865 (`SM8250` / `kona`)<br>8 核 Kryo 585 (最高主频 2.84 GHz) | 移动端轻量级量化模型推理 | 🟢 **算力过剩** (RTF 约 0.08~0.12) |
| **GPU / NPU** | Adreno 650 (支持 Vulkan 1.1 Compute / OpenGLES 3.2) | 视觉 3D 关键点实时计算 (30~60 FPS) | 🟢 **流畅运行** (单帧 < 5ms) |
| **物理运行内存** | **11.21 GB (12GB 版)**<br>当前空闲可用：**`7.25 GB`** | 双网络常驻内存：约 **`160 ~ 200 MB`** | 🟢 **极度充裕** (无任何 OOM 风险) |
| **机身存储空间** | 剩余可用空间：**`206 GB`** | 模型权重与动态库增量：约 **`45 MB`** | 🟢 **完全满足** |

---

## 三、 系统架构与数据流设计

```mermaid
flowchart TD
    subgraph 1. 物理测试输入源 (Test Artifacts)
        A1["音频文件: funk_good.wav<br>(标准黄金发音: /ʌ/)"]
        A2["音频文件: funk_ah_like.wav<br>(元音混淆发音: /ɑ/)"]
        A3["音频文件: chinese_mismatch.wav<br>(非目标中文双音节对抗音)"]
        
        I1["图片文件: face_standard_caret.jpg<br>(居中标准嘴型, 间距 32px)"]
        I2["图片文件: face_wide_open_ah.jpg<br>(过度大张口嘴型, 间距 85px)"]
        I3["图片文件: face_closed_mouth.jpg<br>(紧闭唇型, 间距 10px)"]
    end

    subgraph 2. 神经网络推理层 (Neural Inference Layer)
        subgraph 声学通道
            WAV_IN["WAV 采样解码 (16kHz PCM)"] --> ONNX_CORE["Sherpa-ONNX / Wav2Vec2 CTC 量化模型"]
            ONNX_CORE --> CTC_POSTERIOR["音素后验概率矩阵 (T 帧 × 42 音标)"]
            CTC_POSTERIOR --> ALIGNER["Viterbi 强制对齐: 解算 [/f/, /ʌ/, /ŋ/, /k/] 独立得分"]
        end

        subgraph 视觉通道
            IMG_IN["Bitmap 图像送入"] --> VISION_TASK["MediaPipe / ML Kit Landmarker 算子"]
            VISION_TASK --> FACE_PTS["提取鼻底、下唇中点、左右嘴角物理几何坐标"]
            FACE_PTS --> GEOM_CALC["计算真实几何张量:<br>jawOpen = |Lip14 - Lip13| / FaceHeight<br>lipRoundness = jawOpen / Width"]
        end
    end

    subgraph 3. 证据融合与决策层 (Rust Native Core)
        ALIGNER --> RAW_STRUCT["MultimodalEvidenceRaw (JNI 原始二进制结构体)"]
        GEOM_CALC --> RAW_STRUCT
        RAW_STRUCT --> RUST_ENGINE["libpronunciation_core.so (多模态距离打分)"]
        RUST_ENGINE --> JEV_DECISION["TypeSafe Jev 规则断言与生理动作指导"]
    end

    subgraph 4. 真机呈现层 (OnePlus 8 UI)
        JEV_DECISION --> UI_SCORE["真实得分卡片 + 单音素标红/标绿"]
        JEV_DECISION --> UI_GUIDANCE["具身动作纠错提示 (如: 收紧下颌)"]
    end

    A1 & A2 & A3 --> WAV_IN
    I1 & I2 & I3 --> IMG_IN
```

---

## 四、 关键实施步骤与交付物

### 步骤 1：测试物料准备与集成
- **路径**：`android-app/app/src/main/res/raw/` 与 `android-app/app/src/main/assets/test_images/`
- **交付内容**：
  1. `funk_good.wav`（16kHz 16-bit 单声道，0.6s，能量集中于 550~650Hz）；
  2. `funk_ah_like.wav`（16kHz 16-bit 单声道，0.6s，能量上移至 850~1000Hz）；
  3. `chinese_mismatch.wav`（16kHz 16-bit 单声道，1.0s 非英文复频对抗波形）；
  4. `face_standard_caret.jpg`（真实面部渲染图，开合间距 32px）；
  5. `face_wide_open_ah.jpg`（过度张嘴面部渲染图，开合间距 85px）；
  6. `face_closed_mouth.jpg`（闭嘴面部渲染图，开合间距 10px）。

### 步骤 2：声学 CTC 推理管道构建
- **核心类**：`SherpaOnnxAcousticEngine.kt` / `AcousticNeuralPipeline.kt`
- **逻辑**：
  - 录音或测试音频输入后，以 25ms 窗长、10ms 帧移提取 80 维 Log-Mel 滤波器组特征；
  - 送入 Int8 CTC 模型，输出 $T 	imes 42$ 后验概率矩阵；
  - 提取目标音素 `/f/`, `/ʌ/`, `/ŋ/`, `/k/` 的对齐得分与混淆概率 $P(/ɑ/)$。

### 步骤 3：视觉真实口唇几何测量算子构建
- **核心类**：`RealFaceLandmarkAnalyzer.kt`
- **逻辑**：
  - 支持接收相机实时帧或测试图片 `Bitmap`；
  - 提取面部特征点：鼻底点、下唇中点、左右嘴角；
  - 严格通过欧氏距离公式动态计算归一化的 `jawOpen` 与 `lipRoundness`，杜绝任何常数回退。

### 步骤 4：UI 测试入口改造
- **核心类**：`PracticeScreen.kt`
- **逻辑**：
  - 废弃原本直接传入浮点数值（如 `0.92f, 0.45f`）的伪测试按钮；
  - 改造为调用底层真实算子的测试入口：
    - 按钮 1：`[实测] 黄金标杆 (funk_good.wav + standard_face.jpg)`
    - 按钮 2：`[实测] 混淆发音 (funk_ah_like.wav + wide_open_face.jpg)`
    - 按钮 3：`[实测] 对抗负样本 (chinese_mismatch.wav + standard_face.jpg)`

---

## 五、 关键节点核查与验收防作弊方案

为确保每一步都经过物理验证，设置 **5 级核查闸门（Checkpoints）**：

| 核查关卡 | 核查目标 | 验证命令与技术手段 | 一票否决标准 (Fail Criteria) |
| :--- | :--- | :--- | :--- |
| **Gate 1: 模型资产核查** | 确认 APK 内包含真实神经网络权重文件 | `unzip -l app-debug.apk \| grep -E "(task\|onnx)"` | APK 内无权重模型或文件大小异常 |
| **Gate 2: 视觉几何形变核查** | 确认张嘴与闭嘴图像算出的开合度真实形变 | 单元测试传入 `wide_open` 与 `closed` 图片，断言：<br>`(jawWide - jawClosed) > 0.40f` | 两张图片算出的开合差值 $< 0.25$ 或仍有常数赋值 |
| **Gate 3: 声学张量矩阵核查** | 确认声学模型输出帧级后验矩阵 | 打印输出张量结构：<br>`posterior[0].size == 42`<br>`sum(posterior[0]) == 1.0f` | 无法输出音素矩阵或仍使用能量大小判定 |
| **Gate 4: 跨语言二进制核查** | 确认 Kotlin 测出的物理值完整送入 Rust 原生层 | ADB 抓取 JNI 日志：<br>`adb logcat -s RustCore` 验证打印的原始数值 | 给空输入依然静默返回 88/89 分 |
| **Gate 5: 真机对抗性测试核查** | 一加 8 真机实际执行 3 组测试用例 | 运行 3 组实测，通过 ADB 抓取截图与得分日志 | 中文或故意读错音频仍打出 80 分以上的高分 |

---

## 六、 真机实测对抗用例与预期断言矩阵

| 用例编号 | 物理测试输入组合 | 算法内部真实算子输出 | 一加 8 界面实测断言 | 验收评级 |
| :---: | :--- | :--- | :--- | :---: |
| **TC-01** | `funk_good.wav` +<br>`face_standard_caret.jpg` | • CTC $/ʌ/$ 概率 $> 0.82$<br>• 视觉 `jawOpen` 在 $0.35 \sim 0.42$ | • 综合得分：**88 ~ 95 分**<br>• 音素分解全部呈绿色<br>• 状态：标准合格 | 🟢 必须通过 |
| **TC-02** | `funk_ah_like.wav` +<br>`face_wide_open_ah.jpg` | • CTC 判定 $/ʌ/$ 概率 $< 0.35$<br>• 混淆音 $/ɑ/$ 概率 $> 0.65$<br>• 视觉 `jawOpen` 飙升至 **$0.70 \sim 0.85$** | • 综合得分：**50 ~ 65 分 (不及格)**<br>• 音素 $/ʌ/$ 显式标红（$< 55$分）<br>• AI Coach 给出明确指导：收紧下颌 | 🔴 必须精准扣分，严禁打高分 |
| **TC-03** | `chinese_mismatch.wav` +<br>`face_standard_caret.jpg` | • 英文音素对齐似然度极低<br>• 系统判定声学不匹配 | • 综合得分强制跌破 **35 分**<br>• 提示：`Non-target Speech / Acoustic Mismatch` | ⚠️ 严禁误判为合格发音 |

---

## 七、 实施承诺

1. **过程完全可追溯**：每个阶段的代码提交均附带本地测试日志；
2. **真机自动化取证**：每组测试均通过 `adb shell screencap` 提取一加 8 手机运行现场真实截图存入工作区；
3. **彻底拒绝虚标**：以真实物理对抗测试结果说话，达到标准才判定为阶段完成。
