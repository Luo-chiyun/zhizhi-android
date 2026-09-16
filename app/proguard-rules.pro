# 本应用无反射调用、无序列化框架，保留规则仅用于保住 Compose 与协程的元数据。
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn kotlinx.coroutines.**

# Compose
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# 我们的数据模型可能被 DataStore 的 JSON 序列化读取（org.json 不需保留规则，
# 但保留枚举名以防 ProGuard 混淆 enum 的 name() 语义影响配置读写）
-keepclassmembers enum app.zhizhi.** { *; }
