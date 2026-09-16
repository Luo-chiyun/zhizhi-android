<<<<<<< HEAD
# 知止 (Zhizhi)

> **知足不辱，知止不殆。** —— 《道德经·第四十四章》

知止是一个完全开源、无广告、无订阅的 Android 专注辅助工具。

它不会锁你的手机，不会打断你的操作，也不会在你的屏幕上放一个烦人的计时器。它只做一件事：**在你可能忘记初衷的时候，轻轻问一句：“你进来时想做什么？”**

## ✨ 核心功能

- **安静运行**：后台默默监测，屏幕上无任何常驻悬浮窗。
- **意图确认**：当你在学习模式下打开娱乐/社交应用超过预设时间，弹出一句温和的提醒，帮你找回初衷。
- **游戏防断触**：检测到打开游戏时，在游戏加载前完成确认，游戏中绝对静默，避免局内弹窗打断操作。
- **休息计时器**：学习途中休息设个时间，到点轻轻提醒你回来，适合不设闹钟的人。
- **纯本地运行**：所有数据都在本地处理，不会上传任何用户行为记录。

## 📥 下载与安装

请前往 [Releases](https://github.com/Luo-chiyun/zhizhi/releases) 页面下载最新的 APK 文件。

> **注意**：安装后需要引导授予“使用情况访问”、“悬浮窗”和“通知”权限。部分国内定制系统（小米、华为、OPPO等）可能还需要手动允许“自启动”和“后台弹出界面”。

## 🛠️ 权限说明

| 权限 | 用途 |
|---|---|
| 使用情况访问 | 用于识别当前打开的应用 |
| 悬浮窗 | 用于显示温和提醒 |
| 通知 | 用于维持后台服务运行（静默通道，无声音无振动） |

## 💡 名字的由来

知止，亦名 **Memento**（未来 iOS 版命名）。
一个说的是“知道何时停下”，一个说的是“记住你为何而来”。两个名字都在讲同一件事：把“你原本想做什么”还给你。

## 💰 打赏与支持

知止完全免费，不设内购，不设订阅。如果它确实帮到了你，你可以请作者喝杯咖啡。

（请在此处插入你的打赏二维码图片）

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 开源。你可以自由使用、修改和分发。
=======
# 知止 ZhiZhi

> 知足不辱，知止不殆，可以长久。
> —— 《道德经·第四十四章》

**知止不殆**：知道适可而止，就不会遇到危险。这是一种充满智慧的自我保护。

这个工具想做的，就是把这句两千多年前的话，变成一次温和的提醒。

不锁机、不弹常驻计时器，只在你可能走神的时候，轻轻问一句：
**“知止不殆。你进来时想做什么？”**

答案永远由你给。它不替你做决定，也不替你关掉应用——只是把“你原本想做什么”还给你。

---

## 它到底做什么

| 场景 | 行为 |
| --- | --- |
| 打开小红书 / 抖音 / B站 | 屏幕上什么都不显示。后台安静计时，超过阈值后弹出一张卡片 |
| 打开王者荣耀 / 原神 | 游戏加载前问一次“你确定现在要开始吗”。回答之后，**局内完全静默**，不会断触 |
| 学习途中想休息 | 首页或通知栏一键休息，期间完全不打扰；到点弹「知止不殆 · 休息有度」 |
| 不在设定时段内 | 服务照常在后台，但完全不监测、不提醒 |
| 某个应用不想管 | 卡片上点「别再提醒这个应用」，永久移出监控名单 |

## 三条不可动摇的设计约束

1. **没有联网权限。** 应用不声明 `android.permission.INTERNET`，这是系统强制的物理隔离，不是承诺。
   识别、计时、统计全部在本机 DataStore 里，连应用名都不记，只记次数和总时长。
2. **不使用 AccessibilityService。** 前台应用识别只靠 `UsageStatsManager`。
   没有无障碍权限，也就不会被判定为“滥用无障碍”。
3. **不强制、不锁机。** 应用无法、也不会替你关闭别的应用。
   “退出”按钮的实现在下面“技术决策”里有说明。

---

## 构建

### 本地构建

需要 JDK 17+ 和 Android SDK（platform 35 + build-tools 35.0.0）。

```bash
# 1. 工具链（WSL / Linux，一键装好 SDK 与 Gradle）
bash setup_toolchain.sh

# 2. 构建 debug APK
export ANDROID_HOME=$HOME/android-sdk
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

Windows 上直接装 Android Studio（Ladybug 或更新），用 IDE 打开本目录即可，
无需改动任何配置——`compileSdk 35 / minSdk 26 / targetSdk 35` 都是当前稳定组合。

### 用 GitHub Actions 构建（推荐给不想装 SDK 的人）

把仓库推到 GitHub，Actions 会自动出 APK。见 `.github/workflows/build.yml`。
打 tag（`v0.1.0`）会额外构建 release 包；配置好下面这四个 secret 就会用你的正式签名：

```
KEYSTORE_BASE64   # base64 -w0 release.jks
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

### 发布正式签名

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 4096 \
        -validity 10950 -alias zhizhi
cat > keystore.properties <<'EOF'
storeFile=release.jks
storePassword=你的密码
keyAlias=zhizhi
keyPassword=你的密码
EOF
./gradlew assembleRelease
```

`keystore.properties` 与 `*.jks` 已在 `.gitignore` 里。**不要把它们提交上去。**

---

## 安装

APK 没有上任何应用商店，从 GitHub Releases 直接下载安装即可。
首次启动会引导你授予四件事，每一项都写明了用途：

| 权限 | 必要性 | 用途 |
| --- | --- | --- |
| 使用情况访问 | 必需 | 读取“当前前台是哪个应用”。这是唯一的数据来源 |
| 悬浮窗 | 必需 | 显示提醒卡片；**同时是系统允许本应用在后台持续运行的前提** |
| 通知 | 可选 | 只用于一条无声、不振动的前台服务通知 |
| 电池优化白名单 | 可选 | 避免系统在后台把监测服务杀掉 |

国产 ROM 还多一步：小米叫“后台弹出界面”、华为叫“悬浮窗”或“应用启动管理”、
OPPO/VIVO 叫“允许后台弹出界面”。权限页里有一个按钮会直接跳到对应设置。

---

## 目录结构

```
app/src/main/java/app/zhizhi/
├── data/            设置、分类、预设名单、本地统计（全部 DataStore，无数据库）
│   ├── MonitorSettings.kt      设置模型 + 学习时段判定（支持跨午夜）
│   ├── SettingsRepository.kt   JSON blob 持久化
│   ├── AppClassifier.kt        分类判定（用户设置 > 预设 > 忽略）
│   ├── PresetCatalog.kt        预设应用名单（中文主流应用）
│   └── StatsStore.kt           逐日计数，只保留 120 天
├── monitor/
│   ├── ForegroundAppTracker.kt UsageStatsManager 事件流 → 内存态前台应用
│   ├── MonitorService.kt       前台服务 + 自适应轮询主循环
│   └── BootReceiver.kt         可选的开机恢复
├── policy/ReminderPolicy.kt    分级提醒策略（纯逻辑，可单独测试）
├── overlay/OverlayController.kt 锚点窗口 + 提醒卡片
├── notify/Notifications.kt     两条无声通知通道
├── ui/                         Compose 界面
└── util/                       权限跳转、格式化
```

**策略与界面完全解耦。** `ReminderPolicy` 不碰任何 Android API，输入是
“现在是几点、前台是哪个包、属于哪一类”，输出是“该做什么”。
想加一种提醒规则（比如“连续三天在同一应用超时就更早提醒”），只改这个文件。

---

## 技术决策：三个坑，以及怎么绕过去

这三条是本项目最容易被写错的地方，都已在代码注释里标注。

### 1. Android 15 收紧了“有悬浮窗权限就能后台起服务”的豁免

从 targetSdk 35 开始，仅有 `SYSTEM_ALERT_WINDOW` **不再够**——应用必须
**已经持有一个可见的 `TYPE_APPLICATION_OVERLAY` 窗口**，才允许从后台启动前台服务，
否则抛 `ForegroundServiceStartNotAllowedException`。

所以 `MonitorService.startForegroundSafely()` 的顺序是刻意的：
先挂上那个 1×1 的隐形锚点窗口，再 `startForeground()`。反过来写，
开机自启和 `START_STICKY` 重启就会直接失败。

> 参考：Android 15 behavior changes → “Restrictions on starting foreground services
> while an app holds the `SYSTEM_ALERT_WINDOW` permission”

### 2. 没有无障碍权限，靠什么“退出”

`SYSTEM_ALERT_WINDOW` 同时是**“允许从后台启动 Activity”**的豁免条件之一。
所以“已完成，退出”和“我走神了，退出”这两个按钮可以直接
`startActivity(ACTION_MAIN + CATEGORY_HOME)` 把用户送回桌面，不需要无障碍。

但豁免清单会随版本变动、部分 ROM 还会加码，所以 `goHomeSafely()` 做了一次实测：
发起后 1.5 秒回查前台是否还停在原应用，**成功和失败都记进本地统计**
（“本地记录”页里的“一键回到桌面 成功/尝试次数”），失败则给一条可点的通知兜底。
这是本项目最需要在真机上验证的一条，验收清单里排第一。

> 参考：Android “Activity security / background activity launch restrictions” 的豁免列表

### 3. 猜“当前前台是谁”不能只看查询窗口里的最后一个事件

`UsageStatsManager.queryEvents(begin, end)` 返回的是事件流。如果用户连续 20 分钟
停在小红书，这 20 分钟里可能一个 `ACTIVITY_RESUMED` 事件都没有——按“窗口内最后一个事件”
推断就会误判成“前台为空”，把计时清零。

正确做法是只在 `ACTIVITY_RESUMED` 上更新状态、把结果存在内存里跨轮询保持，
只在屏幕关闭 / 锁屏时才清空。`ForegroundAppTracker` 就是这么写的。

已知代价：事件是异步写入的，从应用切到前台到我们能看见通常有 0.5–2 秒延迟。
所以“游戏启动前确认”用的是加载期那几秒的窗口，不是 0 延迟拦截。

---

## 耗电

轮询节奏是自适应的：

| 状态 | 间隔 |
| --- | --- |
| 正在计时，或刚发生应用切换（15 秒内） | 1 秒 |
| 在学习时段内、但没有目标应用在前台 | 2.5 秒 |
| 不在学习时段 / 屏幕关闭 | 20 秒 |

屏幕关闭时主循环不查询事件、不建窗口，等 `ACTION_USER_PRESENT` 再 `seed()` 恢复。
锚点窗口只有 1 个像素、不可触摸、不参与绘制，代价接近 0。

---

## 发布：GitHub 与 F-Droid

* **GitHub Releases**：签名 APK 挂在 tag 上。
* **IzzyOnDroid**：可以直接提 issue 申请收录。硬性要求已经满足——
  FOSS 许可证、无追踪、无广告、release 签名 APK（不能带 `debuggable` / `testOnly`）、
  APK 挂在 tag 上、体积远小于 30 MB、README 有清晰说明。
  另外需要 `fastlane/metadata/android/<locale>/` 下的描述与截图，本仓库已备好文案与图标，
  **截图还需要你补**（`fastlane/metadata/android/zh-CN/images/phoneScreenshots/`）。
* **F-Droid 主仓库**：需要由 F-Droid 用自己的工具链从源码构建。本项目没有任何
  非自由依赖、没有 Play Services、没有预编译二进制，理论上可过；
  为了可复现构建，`app/build.gradle.kts` 里已关闭 `dependenciesInfo` 注入。

---

## 打赏

全部功能免费，无内购、无广告、无会员。关于页有一个收款码的位置——
把 `app/src/main/res/drawable-nodpi/donation_qr.png` 换成你自己的即可
（当前是一个占位图，扫不出任何东西）。

## 许可证

GPL-3.0-or-later。你可以自行审阅、编译、修改。
>>>>>>> 74f344c (知止 1.0.2：品牌定名、UI 按设计稿重做、正式签名)
