package app.zhizhi.ui.screens

/**
 * 各厂商系统里的授权路径。
 *
 * 为什么放在 Kotlin 里而不是 `strings.xml`：
 * 这不是"一句话文案"，而是**结构化的数据表**——每个机型 × 每项权限一条路径。
 * 放进 XML 会变成一堆带下标的 string-array，加一个机型要动两处、还容易错位。
 * 界面文字（标题、说明）仍然在 strings.xml 里。
 *
 * ⚠️ 路径会随系统版本变化。所以界面上永远带一句"找不到就用设置里的搜索框搜『知止』"——
 * 这比背路径管用。
 */

enum class PermKind {
    /** 使用情况访问 */
    Usage,

    /** 悬浮窗 */
    Overlay,

    /** 通知 */
    Notifications,

    /** 电池优化 / 后台运行 */
    Battery,
}

data class OemGuide(
    val label: String,
    val keywords: List<String>,
    val steps: Map<PermKind, List<String>>,
)

object OemGuides {

    /** 找不到厂商时用的兜底说明（原生 Android 也能直接照做）。 */
    val generic = OemGuide(
        label = "通用（原生 Android / 不确定机型）",
        keywords = emptyList(),
        steps = mapOf(
            PermKind.Usage to listOf(
                "设置 → 应用 → 特殊应用权限 → 使用情况访问",
                "在列表里找到「知止」，把开关打开",
            ),
            PermKind.Overlay to listOf(
                "设置 → 应用 → 特殊应用权限 → 显示在其他应用上层",
                "找到「知止」，把开关打开",
            ),
            PermKind.Notifications to listOf(
                "设置 → 通知 → 应用通知 → 知止",
                "打开「允许通知」，并把「允许横幅」一起打开",
                "确认它没有被归进「静默通知」这一类",
            ),
            PermKind.Battery to listOf(
                "设置 → 应用 → 知止 → 电池",
                "选择「不受限制」",
            ),
        ),
    )

    val all: List<OemGuide> = listOf(
        OemGuide(
            label = "小米 / 红米 / POCO（MIUI / 澎湃OS）",
            keywords = listOf("xiaomi", "redmi", "poco"),
            steps = mapOf(
                PermKind.Usage to listOf(
                    "设置 → 应用设置 → 应用管理 → 知止 → 权限管理 → 其他权限 → 使用情况统计",
                    "新版系统在：设置 → 隐私保护 → 特殊权限设置 → 使用情况访问",
                ),
                PermKind.Overlay to listOf(
                    "设置 → 应用设置 → 应用管理 → 知止 → 权限管理 → 显示悬浮窗",
                    "还要再开一个开关：同一页的「其他权限」→「后台弹出界面」。只开悬浮窗，提醒仍然弹不出来",
                ),
                PermKind.Notifications to listOf(
                    "设置 → 通知与控制中心 → 应用通知管理 → 知止",
                    "允许通知 + 允许横幅；确认没被归进「静默通知」",
                ),
                PermKind.Battery to listOf(
                    "设置 → 应用设置 → 应用管理 → 知止 → 省电策略 → 选「无限制」",
                    "同页 → 权限管理 → 自启动 → 打开",
                ),
            ),
        ),
        OemGuide(
            label = "华为 / 荣耀（EMUI / HarmonyOS / MagicOS）",
            keywords = listOf("huawei", "honor", "hmd"),
            steps = mapOf(
                PermKind.Usage to listOf(
                    "设置 → 隐私 → 更多隐私设置 → 使用情况访问 → 知止",
                    "或：设置 → 应用 → 应用管理 → 知止 → 权限",
                ),
                PermKind.Overlay to listOf(
                    "设置 → 应用 → 应用管理 → 知止 → 权限 → 悬浮窗",
                    "同一页如有「后台弹出界面」，一并打开",
                ),
                PermKind.Notifications to listOf(
                    "设置 → 通知 → 应用通知管理 → 知止",
                    "允许通知 + 横幅；确认没被归进「静默通知」",
                ),
                PermKind.Battery to listOf(
                    "设置 → 应用 → 应用启动管理 → 知止 → 关掉「自动管理」",
                    "关掉自动管理后会露出三个开关，「自启动」「关联启动」「后台活动」三个都要打开",
                ),
            ),
        ),
        OemGuide(
            label = "OPPO / 一加 / realme（ColorOS / OxygenOS）",
            keywords = listOf("oppo", "oneplus", "realme", "oplus", "one plus"),
            steps = mapOf(
                PermKind.Usage to listOf(
                    "设置 → 应用管理 → 知止 → 权限管理 → 其他权限 → 使用情况访问",
                    "或：设置 → 权限与隐私 → 特殊权限 → 使用情况访问",
                ),
                PermKind.Overlay to listOf(
                    "设置 → 应用管理 → 知止 → 权限管理 → 悬浮窗",
                    "还要再开一个开关：同一页的「其他权限」→「后台弹出界面」",
                ),
                PermKind.Notifications to listOf(
                    "设置 → 通知与状态栏 → 应用通知管理 → 知止",
                    "允许通知 + 允许横幅；确认没被归进「静默通知」",
                ),
                PermKind.Battery to listOf(
                    "设置 → 电池 → 应用耗电管理 → 知止",
                    "「允许自启动」「允许关联启动」「允许完全后台行为」三个都要打开",
                ),
            ),
        ),
        OemGuide(
            label = "vivo / iQOO（OriginOS / FuntouchOS）",
            keywords = listOf("vivo", "iqoo"),
            steps = mapOf(
                PermKind.Usage to listOf(
                    "设置 → 应用与权限 → 权限管理 → 权限 → 使用情况统计 → 知止",
                    "或：设置 → 更多设置 → 权限管理",
                ),
                PermKind.Overlay to listOf(
                    "设置 → 应用与权限 → 权限管理 → 悬浮窗 → 知止",
                    "同一页如有「后台弹出界面」，一并打开",
                ),
                PermKind.Notifications to listOf(
                    "设置 → 通知与状态栏 → 应用通知管理 → 知止",
                    "允许通知 + 横幅；确认没被归进「静默通知」",
                ),
                PermKind.Battery to listOf(
                    "设置 → 电池 → 后台耗电管理 → 知止 → 允许后台高耗电",
                    "再开：设置 → 应用与权限 → 权限管理 → 自启动 → 知止",
                ),
            ),
        ),
        OemGuide(
            label = "三星（One UI）",
            keywords = listOf("samsung"),
            steps = mapOf(
                PermKind.Usage to listOf(
                    "设置 → 应用 → 知止 → 权限 → 使用情况访问",
                    "或：设置 → 隐私 → 特殊访问权限 → 使用情况访问",
                ),
                PermKind.Overlay to listOf("设置 → 应用 → 知止 → 在其它应用上层显示"),
                PermKind.Notifications to listOf("设置 → 通知 → 应用通知 → 知止 → 允许通知"),
                PermKind.Battery to listOf(
                    "设置 → 电池 → 后台使用限制",
                    "在「从不休眠的应用」里添加知止",
                ),
            ),
        ),
    )

    /**
     * 按厂商识别当前机型。识别不到就返回 null，界面会用 [generic]。
     * 关键字全部小写比较，同时看 MANUFACTURER 和 BRAND —— 有些机型这两个字段不一致。
     */
    fun detect(manufacturer: String?, brand: String?): OemGuide? {
        val hay = listOfNotNull(manufacturer, brand).joinToString(" ").lowercase()
        if (hay.isBlank()) return null
        return all.firstOrNull { guide -> guide.keywords.any { hay.contains(it) } }
    }
}
