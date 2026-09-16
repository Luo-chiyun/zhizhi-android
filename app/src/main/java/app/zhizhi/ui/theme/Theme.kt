package app.zhizhi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 配色与形状来自 UI 模板的设计令牌（indigo 主色 + slate 中性色）。
 *
 *   primary        indigo-600  #4F46E5
 *   primaryContainer indigo-50 #EEF2FF   ← 图标方块、提示块、选中态背景
 *   文字           slate-900   #0F172A / slate-500 #64748B
 *   描边           slate-200   #E2E8F0   ← 卡片 1px 边框
 *   页面背景       纯白
 */
private val Indigo600 = Color(0xFF4F46E5)
private val Indigo50 = Color(0xFFEEF2FF)
private val Indigo100 = Color(0xFFE0E7FF)
private val Indigo300 = Color(0xFFA5B4FC)
private val Indigo800 = Color(0xFF3730A3)
private val Indigo900 = Color(0xFF312E81)

private val Slate0 = Color(0xFFFFFFFF)
private val Slate50 = Color(0xFFF8FAFC)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate300 = Color(0xFFCBD5E1)
private val Slate400 = Color(0xFF94A3B8)
private val Slate500 = Color(0xFF64748B)
private val Slate700 = Color(0xFF334155)
private val Slate800 = Color(0xFF1E293B)
private val Slate900 = Color(0xFF0F172A)

private val Green500 = Color(0xFF10B981)
private val Amber500 = Color(0xFFF59E0B)
private val Red500 = Color(0xFFEF4444)
private val Red50 = Color(0xFFFEF2F2)
private val Red800 = Color(0xFF991B1B)

private val LightColors = lightColorScheme(
    primary = Indigo600,
    onPrimary = Slate0,
    primaryContainer = Indigo50,
    onPrimaryContainer = Indigo900,
    inversePrimary = Indigo300,
    secondary = Indigo600,
    onSecondary = Slate0,
    secondaryContainer = Indigo100,
    onSecondaryContainer = Indigo900,
    tertiary = Green500,
    onTertiary = Slate0,
    background = Slate0,
    onBackground = Slate900,
    surface = Slate0,
    onSurface = Slate900,
    surfaceVariant = Slate50,
    onSurfaceVariant = Slate500,
    surfaceTint = Indigo600,
    inverseSurface = Slate900,
    inverseOnSurface = Slate50,
    error = Red500,
    onError = Slate0,
    errorContainer = Red50,
    onErrorContainer = Red800,
    outline = Slate200,
    outlineVariant = Slate100,
    scrim = Color(0xFF0F172A),
)

private val DarkColors = darkColorScheme(
    primary = Indigo300,
    onPrimary = Indigo900,
    primaryContainer = Indigo800,
    onPrimaryContainer = Indigo100,
    secondary = Indigo300,
    onSecondary = Indigo900,
    secondaryContainer = Indigo800,
    onSecondaryContainer = Indigo100,
    background = Slate900,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    surfaceTint = Indigo300,
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    outline = Slate700,
    outlineVariant = Slate800,
)

/** 模板里有、但 Material3 配色槽里没有的两个语义色。 */
object ZhiZhiPalette {
    val success = Green500
    val warning = Amber500
}

/** 卡片圆角 12、按钮 8、小控件 6 —— 与模板的 --admin-radius-* 对齐。 */
private val ZhiZhiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun ZhiZhiTheme(
    useDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.typography
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        shapes = ZhiZhiShapes,
        typography = base.copy(
            // 模板的层级：页面大标题 20 粗体、卡片标题 15 半粗、正文 14、辅助 12
            headlineSmall = base.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontSize = 15.sp),
            bodyMedium = base.bodyMedium.copy(fontSize = 14.sp),
            bodySmall = base.bodySmall.copy(fontSize = 12.sp),
            labelLarge = base.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            labelMedium = base.labelMedium.copy(fontSize = 12.sp),
            labelSmall = base.labelSmall.copy(fontSize = 11.sp),
        ),
        content = content,
    )
}
