import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 发布签名：如果根目录存在 keystore.properties 就用它，否则 release 包回落到 debug 签名，
// 保证 `./gradlew assembleRelease` 在没有任何密钥的机器上也能跑通（便于复现构建）。
val keystorePropsFile = rootProject.file("keystore.properties")
val hasReleaseKey = keystorePropsFile.exists()

android {
    namespace = "app.zhizhi"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.zhizhi"
        minSdk = 26
        targetSdk = 35
        // 品牌改为「知止」并换上正式签名密钥之后，这是第一个对外发布的版本，
        // 所以版本号从 1.0.0 重新起算（之前 0.1.x 那串只是内部迭代）。
        versionCode = 9
        versionName = "1.0.8"
        resourceConfigurations += listOf("zh", "en")
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                val props = Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
                // 注意用 rootProject.file：签名文件放在仓库根目录，
                // 而 android{} 里的 file() 是相对 app 模块解析的，直接用会找不到。
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                // 密钥库类型显式写出来（可选）。
                // 不写也能用——Java 会按内容自动识别；但从 JDK 9 起 keytool **默认生成 PKCS12**，
                // 而这个文件名是 .jks，两者对不上时排查起来很费劲。CI 会用 keytool 把真实类型
                // 读出来写进 keystore.properties，这样日志里就有一行明确的答案。
                props.getProperty("storeType")?.takeIf { it.isNotBlank() }?.let { storeType = it }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 没有正式密钥时回落到 debug 签名，这样 `assembleRelease` 在全新克隆的仓库里
            // 也能产出一个可安装、且已过 R8 的 APK（用来自己测体积和混淆问题）。
            // 正式发布前必须配好 keystore.properties —— debug 签名的包无法给用户升级用。
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
        )
    }

    // AGP 默认会往 APK 里塞一段 Google Play 的依赖元数据块。
    // 它会让 APK 每次构建产生差异，直接影响 F-Droid / IzzyOnDroid 的可复现构建校验。
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 引导页的左右翻页用 HorizontalPager。material3 会传递引入 foundation，
    // 但显式声明更稳妥，别依赖传递依赖。
    implementation("androidx.compose.foundation:foundation")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
