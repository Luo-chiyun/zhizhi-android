// 顶层构建文件。本工程刻意不引入任何 Google Play Services / 广告 / 统计 SDK。
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
