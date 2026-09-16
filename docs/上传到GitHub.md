# 怎么把它上传到 GitHub

仓库：<https://github.com/Luo-chiyun/zhizhi-android>

**远程和本地现在是同一条线**（都在同一个提交上），所以日常更新只需要 `git commit` + `git push`，
不需要 force push，也不会产生冲突。

---

## 先说一条铁律

> **不要在 GitHub 网页上直接编辑仓库里的文件。**

本项目已经因为“网页上改了 README + 本地也改了 README”产生过一次**没解决的 Git 冲突标记**
（`<<<<<<< HEAD`），而且连 `.gitignore` 都被写坏了。网页编辑器没有冲突检测，
两边都改同一个文件就必然出事。

**所有改动都在本地做，然后 `git push`。** 网页上只做三件本地做不了的事：
配 Secrets、删 Release、改仓库简介。

---

## 日常更新（推新版）

```bash
cd ~/.hermes/workspace/focus-guard

# 1. 先提交
git add -A
git commit -m "1.0.5：修了 xxx"

# 2. 推上去
git push

# 3. 打 tag 并推送 —— 这一步才会触发 CI 建 Release
git tag -a v1.0.5 -m "知止 1.0.5"
git push origin v1.0.5
```

推 tag 之后去 **Actions** 页看两个 job：

* `build` —— 编译 + 三条守门检查 + 上传产物
* `release` —— 下载产物、校验签名、建 Release 并挂上 APK

---

## 推送前必查：密钥没有混进去

```bash
cd ~/.hermes/workspace/focus-guard
git ls-files | grep -E "\.jks$|keystore\.properties$|\.apk$"
```

**输出必须是空的。** 签名密钥一旦推上公开仓库，等于把这个应用的所有权交出去——
任何人都能用你的身份签发“升级包”。

如果不小心看到了文件名：

```bash
git rm --cached 文件名
echo "文件名" >> .gitignore
git commit -m "把不该入库的文件移出仓库"
```

（`git rm --cached` 只从版本库里移除，本地文件还在。）

---

## 配置 CI 用你的正式签名

去 **Settings → Secrets and variables → Actions → New repository secret** 加四个：

| Secret 名 | 值 |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 ~/.hermes/workspace/focus-guard/zhizhi-release.jks` 的输出 |
| `KEYSTORE_PASSWORD` | keystore.properties 里的 storePassword |
| `KEY_ALIAS` | `zhizhi` |
| `KEY_PASSWORD` | keystore.properties 里的 keyPassword |

生成 base64 一行（在 WSL 里跑）：

```bash
base64 -w0 ~/.hermes/workspace/focus-guard/zhizhi-release.jks
# 把整行输出复制粘贴到 KEYSTORE_BASE64 的值里
```

看密码：

```bash
cat ~/.hermes/workspace/focus-guard/keystore.properties
```

**同时**去 **Settings → Actions → General → Workflow permissions** 选 **Read and write**。

> 两个地方都要改。workflow 文件里显式声明的 `permissions:` 会覆盖仓库的默认设置，
> 而仓库那个开关又是权限上限——只改一个都不会生效。

### 怎么确认 CI 用的是正式签名

看 `build` job 的日志，应该有一行：

```
签名密钥看起来正常。
```

如果那一整步显示被跳过（Skipped），说明**没配密钥**，CI 出的 release 包是 debug 签名的。
新 workflow 在这种情况下会**直接拒绝发布**，只把包留在 Actions 产物里并打印原因——
因为 debug 签名的包一旦发出去，装了它的用户以后无法升级到正式版。

---

## 怎么把这次的东西全部重新上传

本地已经和远程同步，改动都在工作区里没提交。三步搞定：

```bash
cd ~/.hermes/workspace/focus-guard

# 1. 提交（本次包含：README 重写、.gitignore 修冲突、workflow 重写、路线图更新）
git add -A
git commit -m "文档全面重写：修掉 README/.gitignore 里的冲突标记，CI 改用 gh 发布"
git push

# 2. 删掉远程那个带 debug 包的 Release 的 tag
git push origin :refs/tags/v1.0.4

# 3. 把 tag 重新指向新提交并推上去，让 CI 用新 workflow 重建 Release
git tag -f -a v1.0.4 -m "知止 1.0.4"
git push origin v1.0.4
```

第三步做完，CI 会用修好的 workflow 重新跑一遍，Release 里的附件**只会有**
`ZhiZhi-1.0.4-release.apk`（正式签名、文件名规范），不会再出现 `app-release.apk` 和 `app-debug.apk`。

### 还要在网页上手动做一件事

第 2 步只删了 tag，**旧的那个 Release 还在**（标题 `v1.0.4`，附件里有 debug 包）。
去 <https://github.com/Luo-chiyun/zhizhi-android/releases> 找到它，点右上角
垃圾桶图标 **Delete release**。

> 顺序可以颠倒，但两件事都要做：删旧 Release、重推 tag。

---

## 如果推送被拒绝

只有一种情况需要处理：

```
! [rejected]        main -> main (fetch first)
```

意思是远程有本地没有的提交（通常是网页上编辑出来的）。先看一眼：

```bash
git fetch origin
git log --oneline origin/main -3
```

* **远程那些提交是你要的** → `git pull --rebase origin main`，处理完冲突再 push。
* **远程那些提交是误操作 / 不想要的** → 用 force push 覆盖：

```bash
git push --force-with-lease
```

`--force-with-lease` 比 `--force` 安全：如果远程在你 fetch 之后又变了，它会拒绝执行。

---

## 从零开始（换台电脑 / 重新克隆）

```bash
git clone https://github.com/Luo-chiyun/zhizhi-android.git
cd zhizhi-android
bash setup_toolchain.sh
export ANDROID_HOME=$HOME/android-sdk
./gradlew assembleDebug
```

密钥**不在仓库里**，从桌面备份目录「知止-签名密钥备份」拷 `zhizhi-release.jks` 和
`keystore.properties` 到仓库根目录即可。

推送凭证：GitHub 从 2021 年起不接受账号密码，要用 Personal Access Token
（<https://github.com/settings/tokens?type=beta>，`Contents` 权限设为 Read and write）。

---

## 打赏二维码 / 商店文案

* 收款码：`app/src/main/res/drawable-nodpi/donation_wechat.png`、`donation_alipay.png`
* 商店文案（第三方商店用，非必须）：`fastlane/metadata/android/{zh-CN,en-US}/`
  * `title.txt`（≤30 字符）、`short_description.txt`（≤80）、`full_description.txt`（≤4000）
  * 截图放在 `images/phoneScreenshots/`，**不上架就不需要**
