# 知止 ZhiZhi

> **知足不辱，知止不殆，可以长久。**
> —— 《道德经·第四十四章》

**知止不殆**：知道适可而止，就不会遇到危险。这是一种充满智慧的自我保护。

这个工具想做的，就是把这句两千多年前的话，变成一次温和的提醒。

不锁机、不弹常驻计时器，只在你可能走神的时候，轻轻问一句：

> **“知止不殆。你进来时想做什么？”**

答案永远由你给。它不替你做决定，也不替你关掉任何应用——只是把“你原本想做什么”还给你。

---

## 它到底做什么

| 场景 | 行为 |
| --- | --- |
| 打开小红书 / 抖音 / B站 | 屏幕上什么都不显示。后台安静计时，超过阈值后弹出一张卡片 |
| 打开王者荣耀 / 原神 | 游戏加载前问一次“你确定现在要开始吗”。回答之后，**局内完全静默**，不会断触 |
| 学习途中想休息 | 首页或通知栏一键休息，期间完全不打扰；到点弹「知止不殆 · 休息有度」 |
| 不在设定时段内 | 服务照常在后台，但完全不监测、不提醒 |
| 某个应用不想管 | 卡片上点「别再提醒这个应用」，永久移出监控名单 |

提醒卡片上有四个答案，你选哪个都行：

* 已完成，退出
* 还在用，再给 N 分钟
* 我走神了，退出
* 我正在做正事，请勿打扰（进入冷静期，一段时间内不再打扰）

## 三条不可动摇的设计约束

1. **没有联网权限。** 应用不声明 `android.permission.INTERNET`，这是系统强制的物理隔离，不是承诺。
   识别、计时、统计全部在本机 DataStore 里，只记次数和总时长，不记你看了什么。
   CI 里有一条守门检查：APK 一旦出现 `INTERNET` 权限，构建直接失败。
2. **不使用 AccessibilityService。** 前台应用识别只靠 `UsageStatsManager`。
   没有无障碍权限，也就不会被判定为“滥用无障碍”。
3. **不强制、不拦阻。** 应用不锁屏、不会阻止你打开任何应用，也不会强制关闭你正在用的应用。
   “退出”按钮的实现在下面「技术决策」第 2 条里有说明。
   另有一个**默认关闭**的可选开关「退出后清掉该应用的后台进程」：只在你主动点「退出」之后，
   清掉刚才那个应用的后台进程，防止它被系统再次唤醒。Android 13 及以下有效；
   Android 14 起系统禁止第三方应用结束别的应用的进程，该开关会自动禁用。

---

## 下载与安装

前往 [**Releases**](https://github.com/Luo-chiyun/zhizhi-android/releases) 下载最新 APK。

当前版本 **1.0.6**，约 1.8 MB，支持 Android 8.0（API 26）及以上。

> **务必认准正式签名的包。** 文件名形如 `ZhiZhi-1.0.6-release.apk`。
> 不要安装调试包（`app-debug.apk`，包名带 `.debug`）——签名不同，装了它之后无法用正式版覆盖升级，
> 只能卸载重装、数据全丢。

首次启动会引导你授予权限，每一项都写明了用途。

### 权限说明

| 权限 | 必要性 | 用途 |
| --- | --- | --- |
| 使用情况访问 | **必需** | 读取“当前前台是哪个应用”。这是唯一的数据来源 |
| 悬浮窗 | **必需** | 显示提醒卡片；同时是系统允许本应用在后台持续运行的前提 |
| 通知 | 可选 | 只用于一条无声、不振动的前台服务通知 |
| 电池优化白名单 | 可选 | 避免系统在后台把监测服务杀掉 |

**国产 ROM 还多一步。** 只开上面这几项，提醒往往还是弹不出来：

* **小米 / 红米 / POCO**：除悬浮窗外还要单独允许「后台弹出界面」
* **OPPO / 一加 / realme**：要开「允许后台弹出界面」「允许自启动」「允许关联启动」三个开关
* **华为 / 荣耀**：把应用启动管理从「自动管理」改成「手动管理」，再打开三个子开关
* **vivo / iQOO**：要开「后台弹出界面」和「自启动」

应用内的权限页会**自动识别机型**，只显示你这台机器对应的步骤，并给出「跳到对应设置页」的按钮；
路径找不到时可以在系统设置里直接搜索「知止」。

---

## 分类与预设名单

每个应用有三种状态：

| 状态 | 含义 |
| --- | --- |
| 不监控 | 明确不管它（套用预设不会覆盖这个决定） |
| 娱乐 / 社交 | 超时后温和提醒 |
| 游戏 | 启动前确认一次，局内静默 |

内置预设名单 **296 条**（不用监测 93 / 娱乐 103 / 游戏 100），
覆盖国内主流应用；套用时会先与你机器上已安装的列表求交集，只对装了的应用生效。
偏门应用可以自己在「监控哪些应用」里手动勾选。

---

## 构建

### 本地构建

需要 **JDK 17+** 和 **Android SDK（platform 35 + build-tools 35.0.0）**。

```bash
# WSL / Linux：一键装好 SDK 与 Gradle
bash setup_toolchain.sh

export ANDROID_HOME=$HOME/android-sdk
./gradlew assembleDebug        # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # 需要签名配置，见下
```

Windows 上直接装 Android Studio 用 IDE 打开本目录即可，无需改动任何配置——
`compileSdk 35 / minSdk 26 / targetSdk 35` 都是当前稳定组合。

调试包会带上 `.debug` 后缀（包名 `app.zhizhi.debug`），所以调试版和正式版可以同时装在一台手机上。

### 正式签名（发布用）

```bash
keytool -genkeypair -v -keystore zhizhi-release.jks -keyalg RSA -keysize 4096 \
        -validity 10950 -alias zhizhi
cat > keystore.properties <<'EOF'
storeFile=zhizhi-release.jks
storePassword=你的密码
keyAlias=zhizhi
keyPassword=你的密码
EOF
./gradlew assembleRelease
```

`keystore.properties` 与 `*.jks` 已在 `.gitignore` 里。**不要把它们提交上去**，
也不要把它们放进任何云盘同步的目录里。丢了就永远无法给老用户发升级包。

### 用 GitHub Actions 构建

推到 GitHub 后 Actions 会自动出包，产物在仓库的 **Actions** 页下载。
打 tag（`v1.0.6`）时还会自动建 Release 并挂上 APK。

要让 CI 用**你的正式签名**，去 **Settings → Secrets and variables → Actions** 加四个 secret：

| Secret 名 | 值 |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 zhizhi-release.jks` 的输出 |
| `KEYSTORE_PASSWORD` | keystore.properties 里的 storePassword |
| `KEY_ALIAS` | `zhizhi` |
| `KEY_PASSWORD` | keystore.properties 里的 keyPassword |

同时去 **Settings → Actions → General → Workflow permissions** 选 **Read and write**。

CI 有三条守门检查，任何一条不过都不会发布：

1. APK 不得声明 `INTERNET` 权限
2. 配了密钥时，release 包的签名证书不得是 `CN=Android Debug`
3. 没配密钥时，**不许**发 Release（只会把包留在 Actions 产物里，并打印失败原因）

第 3 条是刻意的：debug 签名的包一旦发出去，装了它的用户以后无法升级到正式版。

---

## 目录结构

```
app/src/main/java/app/zhizhi/
├── data/            设置、分类、预设名单、本地统计（全部 DataStore，无数据库）
│   ├── MonitorSettings.kt      设置模型 + 学习时段判定（支持跨午夜）
│   ├── SettingsRepository.kt   JSON blob 持久化
│   ├── AppClassifier.kt        分类判定（用户设置 > 预设 > 忽略）
│   ├── PresetCatalog.kt        预设应用名单（296 条国内主流应用）
│   ├── StatsStore.kt           逐日计数，只保留 120 天
│   └── DiagnosticsStore.kt     运行日志（内存 1000 条 / 磁盘 200 条）
├── monitor/
│   ├── ForegroundAppTracker.kt UsageStatsManager 事件流 → 内存态前台应用
│   ├── MonitorService.kt       前台服务 + 自适应轮询主循环
│   └── BootReceiver.kt         可选的开机恢复
├── policy/ReminderPolicy.kt    分级提醒策略（纯逻辑，可单独测试）
├── overlay/OverlayController.kt 1×1 锚点窗口 + 提醒卡片
├── notify/Notifications.kt     三条通知通道（常驻 / 兜底 / 提示）
├── ui/                         Compose 界面
│   └── screens/OemGuides.kt    各厂商权限设置路径
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

### 2. 没有无障碍权限，靠什么“退出”

`SYSTEM_ALERT_WINDOW` 同时是**“允许从后台启动 Activity”**的豁免条件之一。
所以“已完成，退出”和“我走神了，退出”这两个按钮可以直接
`startActivity(ACTION_MAIN + CATEGORY_HOME)` 把用户送回桌面，不需要无障碍。

但豁免清单会随版本变动、部分 ROM 还会加码，所以 `goHomeSafely()` 做了一次实测：
发起后 1.5 秒回查前台是否还停在原应用，**成功和失败都记进本地统计**
（记录页里的“一键回到桌面 成功/尝试次数”），失败则给一条可点的通知兜底。

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

轮询节奏只分两档：

| 状态 | 间隔 |
| --- | --- |
| 正在监测（总开关开、在时段内、屏幕亮着、有使用情况权限） | 1 秒 |
| 上述任一条件不满足 | 20 秒 |

被挡住的时候主循环**完全不查询事件、不建窗口**，只等下一轮重判。
屏幕关闭时也一样，等 `ACTION_USER_PRESENT` 再重新校准。
锚点窗口只有 1 个像素、不可触摸、不参与绘制，代价接近 0。

---

## 隐私

* 没有联网权限，**不可能**上传任何东西——这一点由系统保证，不需要你信任我。
* 本地只存三类数据：设置、逐日使用次数/时长、以及最多 200 条运行日志。
* 运行日志只记“几点、哪个包名、发生了什么”，用于排查问题，可以在设置里一键清空。
* 没有任何统计 SDK、广告 SDK、崩溃上报。

---

## 已知限制

* **不是锁机软件。** 你随时可以忽略卡片继续玩，这是设计如此。
* 前台识别的延迟是 0.5–2 秒，所以“游戏启动前确认”是在游戏加载期间弹的，不是 0 延迟拦截。
* 部分 ROM 会限制后台弹窗，即使权限都给全也可能弹不出来——此时会自动降级成一条高优先级通知。
* 「退出后清掉该应用的后台进程」只在 Android 13 及以下有效（Android 14 起系统禁止第三方应用结束别的应用的进程）。

更多问题见 [`docs/常见问题.md`](docs/常见问题.md)。

---

## 打赏

全部功能免费，无内购、无广告、无会员。如果它确实帮到了你，可以请作者喝杯咖啡。


## 许可证

[GPL-3.0-or-later](LICENSE)。你可以自由审阅、编译、修改和分发。
