<div align="center">

<img src="assets/logo.svg" width="160" alt="CoTran Logo"/>

# CoTran

**Copy · Translate · Anywhere**

复制即翻译 —— 悬浮于任何应用之上的轻量级 Android 翻译工具

[![Version](https://img.shields.io/badge/Version-1.4.1-1C1B1F?style=for-the-badge&logo=android&logoColor=white)](https://github.com/wob25/CoTran/releases)
[![Platform](https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![API](https://img.shields.io/badge/API-26%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![Offline](https://img.shields.io/badge/Offline-ML%20Kit-blue?style=for-the-badge&logo=google&logoColor=white)](https://developers.google.com/ml-kit/language/translation)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge&logo=opensourceinitiative&logoColor=white)](LICENSE)
[![PRs](https://img.shields.io/badge/PRs-Welcome-ff69b4?style=for-the-badge&logo=github&logoColor=white)](https://github.com/wob25/CoTran/pulls)

</div>

<div align="center">

[English](README.md) | **简体中文**

</div>

---

## 📸 应用预览

| 主页 | 界面语言 | 设置 |
|:---:|:---:|:---:|
| ![主页](assets/screenshots/Home.jpg) | ![界面语言](assets/screenshots/Interface-Language.jpg) | ![设置](assets/screenshots/site-1.jpg) |
| **手动翻译** | **离线语言包** | **历史记录** |
| ![手动翻译](assets/screenshots/Manual-translation.jpg) | ![离线语言包](assets/screenshots/Offline-language-pack.jpg) | ![历史记录](assets/screenshots/History.jpg) |

---

## ✨ 核心特性

- 🔔 **复制即译** —— 复制/划词后屏幕浮现悬浮「译」按钮，点击立即翻译
- 📴 **完全离线** —— 内置 ML Kit 中英双向离线模型（43MB），装上即用、数据不出设备
- 🌐 **多源翻译** —— 智能优先自动换源，支持 B站 / 谷歌 / MyMemory 等免费源与微软 / 谷歌云 / 百度 / DeepL 官方源
- 🗣️ **68 种语言** —— 离线语言包管理：按需下载 / 卸载 / 手动指定目标语言，支持全球主流语言
- 🪟 **悬浮翻译面板** —— 85% 屏高固定面板，输入、译文独立滚动，点击面板外部即可关闭
- 🎨 **个性定制** —— 悬浮按钮大小（24–64dp）与背景透明度（10%–100%）滑块实时调节
- 🕘 **本地历史** —— 最近 100 条翻译记录，随时回查、复制、删除
- 🛡️ **隐私优先** —— 离线引擎数据不离开设备；在线源均标注隐私政策，自行选择

## 📥 安装

### 方式一：下载 Releases

前往 [Releases](https://github.com/wob25/CoTran/releases) 页面下载最新的 `app-release.apk`，安装即可。

### 方式二：自行构建

```bash
git clone https://github.com/wob25/CoTran.git
cd CoTran
# Android Studio 打开项目直接 Run，或使用命令行：
./gradlew :app:assembleRelease
# 产物位于 app/build/outputs/apk/release/app-release.apk
```

> **环境要求**：Android Studio（自带 JBR）· SDK Platform 34 · minSdk 26（Android 8.0+）

## 🚀 快速上手

1. 打开应用 → 授予 **悬浮窗权限** 与 **无障碍权限**
2. 点击「开启服务」，两个前台服务自动拉起
3. 在任意应用 **复制 / 选中** 一段文本 → 右上角出现「译」按钮
4. 点击按钮 → 面板自动填入内容并翻译 → 译文标签显示来源平台
5. 点击面板外部任意位置即可关闭

## 🔐 权限说明

| 权限 | 用途 | 必要性 |
| --- | --- | --- |
| 悬浮窗 | 显示「译」按钮与翻译面板 | 必须 |
| 无障碍 | Android 10+ 后台剪贴板检测通道 | 必须 |
| 通知 | 前台服务常驻通知 | 建议 |
| 电池优化豁免 | 防止服务被系统清理 | 建议 |

## 🌐 翻译源与隐私

| 翻译源 | 费用 | 隐私说明 |
| --- | --- | --- |
| **离线翻译** ⭐ | 免费 | 完全本地运行，数据不离开设备 |
| 智能优先 | 免费 | 免费接口自动尝试，数据经实际命中的平台 |
| B站翻译 | 免费 | 数据经哔哩哔哩服务器处理 |
| 谷歌翻译 | 免费 | 非官方接口，数据会上传至谷歌 |
| zvo.cn | 免费 | 第三方接口，数据经其服务器中转 |
| MyMemory | 免费 | 译文会上传成为公共 TM 翻译记忆库的一部分 |
| 微软翻译 | 需密钥 | 官方接口，F0 免费层每月 200 万字符 |
| 谷歌云翻译 | 需密钥 | 官方接口，每月约 50 万字符免费额度 |
| 百度翻译 | 需密钥 | 官方接口，数据按百度隐私政策处理 |
| DeepL | 需密钥 | 官方接口，免费版数据可能用于改进模型 |

> 未配置密钥时选择官方源会自动回落到免费源，不会中断翻译。

## 🧩 离线语言包

设置 → 翻译源选择「离线翻译」即可进入语言包管理：

- **中英双向包已内置**，安装后无需联网即可使用
- 其余语言包（俄语、日语、韩语、法语、德语、西班牙语等 60+ 种）按需下载，每个约 30–45MB
- 支持查看已安装状态、下载进度、卸载与目标语言指定

## 📁 项目结构

```
CoTran/
├── app/src/main/
│   ├── java/com/copytranslate/app/
│   │   ├── App.kt                             # 全局 Application
│   │   ├── service/
│   │   │   ├── ClipboardAccessibilityService.kt  # 无障碍检测（划词/剪贴板变化）
│   │   │   ├── ClipboardMonitorService.kt        # 剪贴板监听前台服务
│   │   │   ├── FloatingButtonService.kt          # 悬浮按钮与翻译面板
│   │   │   └── BootReceiver.kt                   # 开机自启
│   │   ├── translate/
│   │   │   ├── TranslateManager.kt               # 统一入口：选源/fallback/分段翻译
│   │   │   ├── TranslateProvider.kt              # 翻译源接口 + Http 辅助
│   │   │   └── provider/                         # 10 个翻译源实现
│   │   │       ├── OfflineMlKitProvider.kt           # ML Kit 离线
│   │   │       ├── MlKitModelWarmer.kt               # 离线模型播种/预热
│   │   │       ├── BiliIndexProvider.kt              # B站 Index-Translate
│   │   │       ├── GoogleGtxProvider.kt              # 谷歌 gtx
│   │   │       ├── GoogleCloudProvider.kt            # 谷歌云官方 v2
│   │   │       ├── MicrosoftProvider.kt              # 微软官方 v3
│   │   │       ├── ZvoProvider.kt / MyMemoryProvider.kt
│   │   │       └── BaiduProvider.kt / DeepLProvider.kt
│   │   ├── ui/
│   │   │   ├── MainActivity.kt                   # 入口：服务开关/权限引导
│   │   │   ├── SettingsActivity.kt               # 设置页（翻译源/语言包/滑块）
│   │   │   ├── HistoryActivity.kt                # 历史记录
│   │   │   └── ResultOverlay.kt                  # 悬浮翻译面板
│   │   ├── util/                                 # Prefs / 剪贴板门控 / 语言检测
│   │   └── db/HistoryRepository.kt               # 历史记录存储
│   ├── assets/mlkit_models/                      # 内置中英离线模型
│   └── res/                                      # 布局 / 图标 / 字符串
├── assets/                                       # README 素材（logo/截图）
├── gradle/                                       # Gradle Wrapper
└── LICENSE                                       # MIT License
```

## ⚠️ 已知限制

- Android 10+ 系统限制后台读取剪贴板，本应用通过无障碍服务通道解决；部分机型 ROM 对后台管控严格，个别场景下需点击面板内的「刷新」按钮获取最新内容
- 自绘控件应用（如 Termux）内的文本无法被无障碍通道捕获，可长按悬浮按钮使用手动输入翻译
- 在线语言包从谷歌服务器下载，需要网络能够访问谷歌服务

## 🤝 贡献

欢迎提交 Issue 与 Pull Request！翻译源适配、UI 改进、问题反馈都非常欢迎。

## 📄 License

本项目基于 [MIT License](LICENSE) 开源。

---

<div align="center">

**如果这个项目对你有帮助，欢迎点一个 ⭐ Star**

</div>
