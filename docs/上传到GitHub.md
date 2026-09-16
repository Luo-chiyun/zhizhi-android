# 怎么把它上传到 GitHub

目标仓库：<https://github.com/Luo-chiyun/zhizhi-android>

本地已经准备好了：已经 `git init`、已经提交了第一版、`.gitignore` 已经生效。
你只差"建远程仓库"和"推上去"。

---

## 第 0 步：先确认密钥不会被推上去

这一步**不能跳过**。签名密钥一旦推上公开仓库，等于把这个应用的所有权交出去——
任何人都能用你的身份签发"升级包"。

```bash
cd ~/.hermes/workspace/focus-guard
git ls-files | grep -E "\.jks$|keystore\.properties$|\.apk$"
```

**输出必须是空的。** 如果列出了文件名，先停下来，把那个文件从暂存区拿掉：

```bash
git rm --cached 文件名
echo "文件名" >> .gitignore
git commit -m "把不该入库的文件移出仓库"
```

---

## 第 1 步：在网页上建一个空仓库

1. 打开 <https://github.com/new>
2. **Repository name**：`zhizhi-android`
3. **Description**：填一句，比如
   `知止 —— 不锁机，只在你可能走神时问一句"你进来时想做什么"`
4. 选 **Public**
5. **下面三个勾全部不要勾**（README / .gitignore / license 本地已经有了，
   勾了会造成"远程有提交、本地也有提交"的冲突，第一次推就要处理分叉）

点 Create repository。建完之后页面上会出现一堆命令提示，**先不用管**，看下一步。

---

## 第 2 步：把本地代码推上去

### 方式 A：HTTPS + 访问令牌（推荐，最省事）

GitHub 从 2021 年起不接受账号密码推送，必须用 **Personal Access Token**。

1. 打开 <https://github.com/settings/tokens?type=beta> → **Generate new token**
2. 名字随便填（如 `zhizhi-push`），有效期选 90 天
3. **Repository access** 选 `Only select repositories` → 选 `zhizhi-android`
4. **Permissions** → `Repository permissions` → 把 **Contents** 改成 **Read and write**
5. 生成，**把 token 复制下来**（只显示一次）

然后：

```bash
cd ~/.hermes/workspace/focus-guard
git remote add origin https://github.com/Luo-chiyun/zhizhi-android.git
git push -u origin main
# 提示用户名：填 Luo-chiyun
# 提示密码：粘贴刚才那个 token（不是账号密码）
```

推成功之后刷新网页就能看到代码了。

> 也可以在本地缓存凭证，省得每次输入：
> `git config --global credential.helper store`（会把 token 明文存在 ~/.git-credentials，
> 自己的电脑上无所谓）

### 方式 B：用 gh 命令行

```bash
sudo apt install gh          # Ubuntu 24.04 自带这个包（2.45.0）
gh auth login                # 选 GitHub.com → HTTPS → 用浏览器登录
cd ~/.hermes/workspace/focus-guard
gh repo create zhizhi-android --public --source=. --remote=origin --push
```

`gh` 的好处是后面打 Release、看 Actions 日志都不用开网页。

### 方式 C：SSH 密钥

```bash
ssh-keygen -t ed25519 -C "Luo-chiyun@users.noreply.github.com"
cat ~/.ssh/id_ed25519.pub      # 复制输出
# 粘贴到 https://github.com/settings/keys → New SSH key
git remote add origin git@github.com:Luo-chiyun/zhizhi-android.git
git push -u origin main
```

---

## 第 3 步：把签名 APK 作为 Release 发出去

Release 是给用户下载的地方，也是 IzzyOnDroid 收录时看的入口。

**先打 tag：**

```bash
cd ~/.hermes/workspace/focus-guard
git tag -a v1.0.2 -m "知止 1.0.2"
git push origin v1.0.2
```

**再附上 APK：** 网页 → 仓库 → Releases → **Draft a new release**
* Choose a tag：选 `v1.0.2`
* Release title：`知止 1.0.2`
* 说明写上这一版的变更（可以直接抄 CHANGELOG）
* **把 `ZhiZhi-1.0.2-release.apk` 拖进附件区**
* Publish release

> ⚠️ 上传的必须是**正式签名**的那个 APK，不能是 debug 包。
> 用户装过 debug 版之后没法用正式版覆盖升级（签名不同，Android 会拒绝）。

---

## 第 4 步：让 GitHub 自动帮你构建（可选）

仓库里已经有 `.github/workflows/build.yml`，推上去之后会自动跑，产物在 **Actions** 页下载。

要在 CI 里也用**你的正式签名**（否则 CI 出来的 release 包是 debug 签名的），
去 **Settings → Secrets and variables → Actions** 加四个 secret：

```bash
# 先把密钥文件转成 base64 一行
base64 -w0 ~/.hermes/workspace/focus-guard/zhizhi-release.jks
# 把输出粘到 KEYSTORE_BASE64
```

| Secret 名 | 值 |
| --- | --- |
| `KEYSTORE_BASE64` | 上面那行 base64 |
| `KEYSTORE_PASSWORD` | keystore.properties 里的 storePassword |
| `KEY_ALIAS` | `zhizhi` |
| `KEY_PASSWORD` | keystore.properties 里的 keyPassword |

`keystore.properties` 里的密码可以用 `cat` 看一眼：

```bash
cat ~/.hermes/workspace/focus-guard/keystore.properties
```

CI 里还有一条检查：**APK 一旦声明 `INTERNET` 权限就直接构建失败**——
这是这个项目的核心承诺，用 CI 守住它。

---

## 第 5 步：申请上架 IzzyOnDroid（可选）

<https://gitlab.com/IzzyOnDroid/repo/-/issues> 新建 issue，说明想收录。

硬性要求基本都满足了：GPL-3.0、无追踪、无广告、release 签名 APK、APK 挂在 tag 上、
体积远小于 30MB、README 有说明。

**唯一还缺的是截图**（至少 4 张），放进：

```
fastlane/metadata/android/zh-CN/images/phoneScreenshots/
```

装好 APK 之后截这几张：首页仪表盘、监控哪些应用、那张提醒卡片、本地记录。

```bash
adb exec-out screencap -p > 1-home.png
```

---

## 以后每次发新版的流程

```bash
cd ~/.hermes/workspace/focus-guard
# 1. 改 app/build.gradle.kts 里的 versionCode +1、versionName
# 2. 提交
git add -A && git commit -m "1.0.3：修了 xxx"
git push
# 3. 打新的 tag 并推送（CI 会自动出包）
git tag -a v1.0.3 -m "知止 1.0.3" && git push origin v1.0.3
# 4. 网页上把这次的 APK 加到 Release
```

**`versionCode` 每次必须 +1**，否则老用户收不到升级（Android 用版本号判断新旧）。
