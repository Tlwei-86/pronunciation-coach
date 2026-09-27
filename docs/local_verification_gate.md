# Local Quality Gate & Multi-Agent Pre-Flight Verification Standard

为彻底解决**“本地环境工具链缺失”、“跨模块修改断言失配”**及**“提交前未进行编译与格式检验导致云端 CI 频发红灯”**的问题，即日起持久化并实施以下解决机制：

---

## 1. 预检门禁自动化脚本：`scripts/preflight_check.ps1`

在任何代码 `git commit` / `git push` 前，必须在根目录执行预检命令：
```powershell
powershell -ExecutionPolicy Bypass -File scripts/preflight_check.ps1
```

### 门禁检查项：
1. **Rust 核心引擎质量门禁**：
   - 自动运行 `cargo fmt --check --manifest-path pronunciation-core/Cargo.toml`（格式零容忍）。
   - 自动运行 `cargo clippy --manifest-path pronunciation-core/Cargo.toml -- -D warnings`（零警告阻断）。
   - 自动运行 `cargo test --manifest-path pronunciation-core/Cargo.toml`（Rust 22+ 单元测试全部通过）。
2. **Android & Kotlin 离线语义与语法一致性预检**：
   - **换行符检测**：检查 `android-app/gradlew` 及 shell 脚本，严禁任何 Windows `CRLF (\r\n)` 导致 Linux CI shell 报 `Syntax error`。
   - **参数歧义检测**：扫描 Compose `drawPath` 等易混淆重载调用，强制显式指明命名参数 `style = Stroke(...)`、`color = ...`。
   - **Fallback 逻辑一致性校验**：凡修改 JNI Bridge 接口或新增评分特征（如舌位 `tongueHeight`/`tongueBackness`），必须同步检查 `fallbackScoreFunk*` 的评分判定阈值，严禁与单元测试用例（如 `PronunciationCoreBridgeTest`、`ReasoningProviderTest`）产生门限冲突。
3. **16KB ELF 页对齐编译参数检验**：
   - 确保 `RUSTFLAGS` 包含 `-C link-arg=-Wl,-z,max-page-size=16384`，严禁擅自删除或降级。

---

## 2. 多 Agent 角色职责持久化更新

### Agent: `rust_core_builder`
- **铁律**：每次修改 Rust 代码后，**禁止直接报告完成或提交代码**。必须在本地命令行按顺序执行并验证通过：
  ```bash
  cargo fmt --check
  cargo clippy -- -D warnings
  cargo test
  ```

### Agent: `android_app_builder`
- **铁律**：
  1. 调用 Jetpack Compose Canvas 的重载绘图函数（尤其是 `DrawScope.drawPath`、`drawArc` 等）时，**一律使用具名参数**，严禁使用依赖隐式顺序的位置参数。
  2. 当调整业务逻辑或 Native 接口代理时，必须同时静态通读 `android-app/app/src/test/` 下对应的测试文件断言，确保测试用例输入能够正常满足评级条件。
  3. 任何新建或修改的 Shell 脚本均需保持 Linux LF 换行。

### Agent: `ci_workflow_engineer`
- **铁律**：
  1. 流水线脚本中使用 `gradle/actions/setup-gradle@v4` 及 `actions/setup-java@v5` 官方加速缓存。
  2. 严密保护 `gradlew` 执行权限与 CRLF 消毒逻辑，确保跨平台构建幂等性。
