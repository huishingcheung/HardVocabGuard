# 硬要背单词（HardVocabGuard）

一款**纯免费、无广告、可开源**的安卓自律监督工具：开启监督模式后，用户在未达标前尝试切换到其他应用/回到桌面，将触发强提醒报警；达标后自动解除监督。

> 目标白名单应用：**不背单词**（包名固定：`cn.com.langeasy.LangEasyLexis`）

## 适配与定位

- 适配系统：**Hyper OS 3.0.1.0（基于 Android 16）**
- `minSdkVersion = 34`，`targetSdkVersion = 36`
- **无需 ROOT、无需系统签名**：仅依赖用户手动授权的系统权限

## 你能获得什么

- 监督规则：仅按**时长**达标
- 实时进度：通知栏 + 无障碍悬浮层同步显示（悬浮层不需要“悬浮窗权限”）
- 违规报警：离开白名单应用即触发**响铃 + 震动 + 全屏报警页**
- 紧急解锁：预设 6 位数字密码，紧急情况下解除监督并留档
- 学习统计：本地记录每次会话，最近 7 天分钟数柱状图，支持 CSV 导出

## 合规限制（必须说明）

Android 16 / Hyper OS 下，**普通应用在不成为“设备所有者( Device Owner )”且不使用系统签名/ROOT 的前提下**，无法做到系统级“绝对锁死桌面/彻底屏蔽返回与多任务”。

本项目采用**官方合规 API 的最强可行组合**：

- 无障碍监听窗口切换，发现违规后**立即报警**并**尽力拉回目标应用**
- UsageStats 统计目标应用**前台有效时长**（前台/后台切换按事件累计）

同理，“无法静音”的强提醒在系统层面也无法 100% 保证；本项目通过闹钟音量拉满 + Alarm 音频属性 + 前台服务常驻实现尽力而为。

## 必要权限说明

- 无障碍服务：用于窗口切换监听、悬浮进度层、数量识别
- 使用情况访问：用于统计“不背单词”前台有效时长
- 前台服务：监督模式与报警模式常驻
- 通知权限（Android 13+）：展示监督通知/报警通知
- 忽略电池优化（强烈建议）：降低被系统杀死概率
- 设备管理器（可选）：提供“卸载前需先停用设备管理器”的合规防卸载

## 编译与运行

### 1) 环境要求

- Android Studio（建议最新稳定版）
- Android SDK：安装 `Android 16 (API 36)` 平台与 Build-Tools
- JDK 17

### 2) 导入工程

直接用 Android Studio 打开仓库根目录（含 `settings.gradle.kts`）。

### 3) 真机权限配置（Hyper OS 推荐步骤）

1. 首次启动应用，进入“权限引导”页
2. 依次开启：无障碍服务、使用情况访问、通知权限
3. 在系统电池设置中：将本应用设为“**无限制**”，并允许“自启动/后台运行”
4. 可选：开启“设备管理器(防卸载)”

## 使用教程（最短路径）

1. 打开“不背单词”，确保它在后台/最近任务中（Hyper OS 下建议先手动打开一次）
2. 打开本应用，设置时长目标
3. 设置紧急解锁密码（6 位数字，必须先设置才能启动监督）
4. 勾选“我已手动打开过‘不背单词’并保持在后台”
5. 点击“启动监督模式”后，去“不背单词”学习；若未达标尝试离开，会触发报警

## 代码结构概览

- `app/src/main/java/com/jnu/hardvocabguard/service/`
  - `SupervisionForegroundService`：监督前台服务与时长统计
  - `AlarmForegroundService`：违规报警前台服务
- `app/src/main/java/com/jnu/hardvocabguard/accessibility/`
  - `GuardAccessibilityService`：白名单判定、违规检测、数量识别
  - `ProgressOverlayController`：无障碍悬浮进度层
- `app/src/main/java/com/jnu/hardvocabguard/data/`
  - `SettingsStore`：DataStore 本地设置与运行态
  - `StudySessionRepository` + `data/db/*`：Room 本地学习记录

## 常见问题（FAQ）

### Q1：为什么我仍然能回到桌面/打开其他应用？

A：系统层面无法被普通应用“绝对锁死”。本项目会在检测到违规后**立即报警**并尝试拉回目标应用。

### Q2：为什么不做“数量统计/自动计数”？

A：不同版本“不背单词”的界面文本变化较大，无障碍计数很容易误判，稳定性不如时长统计。本项目当前仅支持“按时长监督”。

### Q3：Hyper OS 总把我后台杀掉怎么办？

A：请按“真机权限配置（Hyper OS 推荐步骤）”设置电池策略与自启动；前台服务仍可能被极端策略杀死，这是系统限制。

## 截图/演示素材规范

- 建议提供：权限引导页、主界面规则设置、监督通知、悬浮进度层、报警页、统计页
- 截图分辨率：1080×2400 或更高

## 开源协议

MIT License，见 [LICENSE](LICENSE)。

## GitHub Release 自动签名（可选但推荐）

本项目已包含自动发布工作流：推送 `v*` tag 后会自动构建并在 Release 中附带 APK。

你只需要在仓库里配置 **两个** Actions Secrets（每个 secret 都是单独点一次 “New repository secret” 创建，页面里的两个输入框是“当前这个 secret 的 Name 和 Secret”）：

- `SIGNING_KEYSTORE_BASE64`：把你的 `release.jks` 做 Base64 后的完整内容
- `SIGNING_PASSWORD`：同时作为 `storePassword` 与 `keyPassword`

说明：工作流默认固定 `keyAlias=hardvocabguard`，因此生成 keystore 时建议使用 `-alias hardvocabguard`。
