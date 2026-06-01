# AIGuide — AI 助盲 Android 应用

面向全盲与低视力用户的智能辅助应用。通过语音交互、摄像头视觉分析和无障碍服务，帮助视障人士安全出行、自主购物、操作手机。

---

## 功能总览

| 功能 | 说明 | 触发方式 |
|------|------|----------|
| 隐私黑色蒙版 | AI 工作时全屏覆盖，防止旁人窥屏 | 默认开启，三击电源键临时关闭 |
| 语音助手 | 离线唤醒词"小助"，流式 ASR 收音，2 秒静默自动结束 | 唤醒词触发，或长按音量+ |
| 摄像头识物 | 识别商品、文字、障碍物、路况 | 语音指令"帮我看看这个" |
| 导航安全辅助 | 监听导航播报 + 实时检测人/车/马路边缘/急坡 | 打开导航 App 自动启动 |
| 天黑闪光灯 | 环境光低于 10 lux 时闪烁提醒过往车辆行人 | 自动检测 |
| 半自动操作 | 语音控制点击、滑动、输入、返回、Home | 语音指令"点击发送""上滑" |
| 协助模式 | 短时关闭蒙版让明眼人辅助，3 分钟自动恢复 | 三击电源键 / 语音"关闭协助" |
| 性能自适应 | 按设备性能分 HIGH/MEDIUM/LOW 三档，电量低自动降档 | 自动检测 |

---

## 安装要求

- Android 10.0 (API 29) 及以上
- 需手动开启以下权限（首次启动有引导页）：
  - 无障碍服务（设置 → 无障碍 → AIGuide → 开启）
  - 悬浮窗权限（设置 → 应用 → AIGuide → 显示在其他应用上层）
  - 摄像头权限
  - 通知权限
  - 麦克风权限

---

## 快速开始

### 1. 首次启动

打开应用 → 查看 4 页引导 → 依次授权 → 进入主界面。

### 2. 语音唤醒

说出 **"小助"** 即可唤醒。听到提示音后说话，说完后停顿 2 秒自动提交。

常用指令示例：

| 指令 | 效果 |
|------|------|
| "小助，帮我看看这是什么" | 启动摄像头识物 |
| "小助，打开微信" | 通过无障碍服务启动微信 |
| "小助，点击搜索" | 在当前屏幕上找到并点击"搜索"按钮 |
| "小助，上滑" | 向上滑动屏幕 |
| "小助，返回" | 执行返回操作 |
| "小助，关闭协助" | 结束协助模式，恢复蒙版 |
| "小助，打开手电筒" | 开启闪光灯 |

### 3. 物理按键

| 操作 | 功能 |
|------|------|
| 三击电源键 | 切换协助模式（蒙版开/关） |
| 长按音量+ | 语音输入（嘈杂环境兜底） |

### 4. 导航使用

打开高德/百度/腾讯地图 → 开启导航 → AIGuide 自动监听导航播报并叠加摄像头路况检测。危险预警分三级：

| 级别 | 场景 | 播报时机 |
|------|------|----------|
| 紧急 | 碰撞风险、偏离路线 | 立即打断一切 |
| 警告 | 前方障碍物（人/车/自行车） | 导航播报间隙 |
| 提示 | 马路边缘、路况变化 | 仅播报一次 |

---

## 设置说明

所有设置项均可在应用内「设置」页调整：

| 设置项 | 范围 | 默认 |
|--------|------|------|
| 蒙版透明度 | 0-100 | 0（全黑） |
| 语音唤醒 | 开/关 | 开 |
| TTS 语速 | 0.5-2.0 | 1.0 |
| TTS 音调 | 0.5-2.0 | 1.0 |
| 协助超时 | 1-5 分钟 | 3 分钟 |
| 闪光灯 | 开/关 | 开 |
| 低功耗模式 | 开/关 | 关 |

---

## 技术架构

```
交互层：语音(Vosk ASR) + 手势(TalkBack) + 按键(电源键/音量键)
    ↕
引擎层：VoiceEngine / VisionEngine / AutoEngine / NavSafetyEngine
    ↕
桥接层：AccessibilityService + Camera2 + WindowManager + SensorManager
    ↕
安全层：PrivacyOverlayManager(蒙版) + SafetyGuard(危险暂停) + AssistModeManager(协助超时)
```

事件总线：`ServiceBus` — SharedFlow + StateFlow 实现模块间解耦通信。

离线模型清单（本地推理，无需联网）：

| 模型 | 用途 | 大小 | 延迟 |
|------|------|------|------|
| Vosk Small | 唤醒词 + ASR | 50 MB | 100 ms |
| MobileNetV3-SSD | 障碍物检测 | 12 MB | 80 ms |
| MobileVLM | 图片理解 | 80 MB | 500 ms |
| OCR (Tesseract) | 文字识别 | 15 MB | 200 ms |

---

## 项目结构

```
app/src/main/java/com/aiguide/assistant/
├── AIGuideApp.kt              # Application 入口
├── MainActivity.kt            # 主界面
├── engine/
│   ├── VoiceEngine.kt         # 语音引擎 (Vosk + ASR)
│   ├── VisionEngine.kt        # 视觉引擎 (Camera2 + CameraX)
│   ├── TtsManager.kt          # TTS 播报管理器 (四级队列)
│   ├── AutoEngine.kt          # 自动操作引擎
│   ├── AssistModeManager.kt   # 协助模式状态机
│   ├── HazardDetector.kt      # 危险检测器 (TFLite)
│   ├── NavSafetyEngine.kt     # 导航安全校验层
│   ├── NavigationListener.kt  # 导航播报监听
│   ├── FlashlightManager.kt   # 天黑闪光灯
│   ├── DeviceProfile.kt       # 设备性能分级
│   └── PerformanceOptimizer.kt # 自适应性能调优
├── overlay/
│   └── PrivacyOverlayManager.kt # 隐私蒙版覆盖层
├── service/
│   ├── ServiceBus.kt          # 事件总线
│   ├── AIGuideAccessibilityService.kt
│   ├── AIGuideForegroundService.kt
│   └── NotificationHelper.kt
├── ui/
│   ├── SettingsActivity.kt    # 设置页
│   ├── StatusIndicator.kt     # 状态悬浮窗
│   ├── OnboardingActivity.kt  # 引导页
│   └── SettingsAdapter.kt
└── di/
    └── AppModule.kt           # Hilt DI
```

---

## 文档

- [产品设计文档](output/2026-06-01-ai-assistive-app-design.md)
- [实施计划](output/2026-06-01-implementation-plan.md)