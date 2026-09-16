package app.zhizhi.data

/**
 * 预设名单。
 *
 * ## 这份表的定位
 *
 * 它只是"帮你先想好的候选"，不是权威数据。两点必须清楚：
 *
 *  1. **包名会失效**。应用改版、换渠道包、出极速版，都可能让某一行对不上。
 *     所以匹配逻辑同时看包名和应用显示名，且"一键套用"会先跟**本机实际安装列表求交集**，
 *     只套用真正存在的那部分。
 *  2. **分类是主观的**。同一个应用对不同人意义不同（微信可能是在回工作消息，
 *     也可能是在刷朋友圈），所以这里给的是**默认值**，分类页永远可以改。
 *
 * ## 三类
 *
 *  - [AppCategory.NEUTRAL] 不影响学习：词典、网课、办公、办公沟通工具
 *  - [AppCategory.ENTERTAINMENT] 娱乐 / 社交：可能会一刷就停不下来的
 *  - [AppCategory.GAME] 游戏：启动前确认一次，局内静默
 *
 * 想加应用就往下加一行，不用动别的地方。
 */
object PresetCatalog {

    private val NEUTRAL = AppCategory.NEUTRAL
    private val FUN = AppCategory.ENTERTAINMENT
    private val GAME = AppCategory.GAME

    val ALL: List<PresetApp> = buildList {
        // ============================================================ 不影响学习
        // 学习工具
        add(PresetApp("com.jiongji.andriod.card", "百词斩", NEUTRAL))
        add(PresetApp("com.dianxinos.dict", "不背单词", NEUTRAL))
        add(PresetApp("com.duolingo", "多邻国", NEUTRAL))
        add(PresetApp("com.maimemo.android.mm", "墨墨背单词", NEUTRAL))
        add(PresetApp("com.baidu.homework", "作业帮", NEUTRAL))
        add(PresetApp("com.superke.course", "超级课程表", NEUTRAL))
        add(PresetApp("com.eduyun.app", "国家中小学智慧教育平台", NEUTRAL))
        add(PresetApp("com.xueersi.parentsapp", "学而思网校", NEUTRAL))
        add(PresetApp("cn.xuexi.android", "学习强国", NEUTRAL))
        // AI 助手
        add(PresetApp("com.openai.chatgpt", "ChatGPT", NEUTRAL))
        add(PresetApp("com.larus.nova", "豆包", NEUTRAL))
        add(PresetApp("com.moonshot.kimichat", "Kimi", NEUTRAL))
        // 效率与办公
        add(PresetApp("com.tomatodo", "番茄ToDo", NEUTRAL))
        add(PresetApp("com.ticktick.task", "滴答清单", NEUTRAL))
        add(PresetApp("cn.wps.moffice_eng", "WPS Office", NEUTRAL))
        add(PresetApp("com.baidu.netdisk", "百度网盘", NEUTRAL))
        add(PresetApp("com.tencent.wemeet", "腾讯会议", NEUTRAL))
        add(PresetApp("com.alibaba.android.rimet", "钉钉", NEUTRAL))
        // 沟通工具：默认认为是在办正事，而不是消遣
        add(PresetApp("com.tencent.mm", "微信", NEUTRAL))
        add(PresetApp("com.tencent.mobileqq", "QQ", NEUTRAL))

        // ============================================================ 娱乐 / 社交
        add(PresetApp("com.ss.android.ugc.aweme", "抖音", FUN))
        add(PresetApp("com.ss.android.ugc.aweme.lite", "抖音极速版", FUN))
        add(PresetApp("com.smile.gifmaker", "快手", FUN))
        add(PresetApp("tv.danmaku.bili", "哔哩哔哩", FUN))
        add(PresetApp("com.xingin.xhs", "小红书", FUN))
        add(PresetApp("com.sina.weibo", "微博", FUN))
        add(PresetApp("com.zhihu.android", "知乎", FUN))
        add(PresetApp("com.ss.android.article.news", "今日头条", FUN))
        add(PresetApp("com.ss.android.article.lite", "头条极速版", FUN))
        add(PresetApp("com.dragon.read", "番茄小说", FUN))
        add(PresetApp("com.qidian.QDReader", "起点读书", FUN))
        add(PresetApp("com.tencent.weread", "微信读书", FUN))
        add(PresetApp("com.ximalaya.ting.android", "喜马拉雅", FUN))
        add(PresetApp("com.taobao.taobao", "淘宝", FUN))
        add(PresetApp("com.jingdong.app.mall", "京东", FUN))
        add(PresetApp("com.xunmeng.pinduoduo", "拼多多", FUN))
        add(PresetApp("com.sankuai.meituan", "美团", FUN))
        add(PresetApp("com.dianping.v1", "大众点评", FUN))
        add(PresetApp("com.taobao.idlefish", "闲鱼", FUN))
        add(PresetApp("com.cainiao.wireless", "菜鸟", FUN))
        add(PresetApp("com.autonavi.minimap", "高德地图", FUN))
        add(PresetApp("com.baidu.BaiduMap", "百度地图", FUN))
        add(PresetApp("com.twitter.android", "X", FUN))
        add(PresetApp("org.telegram.messenger", "Telegram", FUN))
        add(PresetApp("com.instagram.android", "Instagram", FUN))
        add(PresetApp("com.google.android.youtube", "YouTube", FUN))
        add(PresetApp("com.reddit.frontpage", "Reddit", FUN))
        add(PresetApp("com.mihoyo.hyperion", "米游社", FUN))
        add(PresetApp("com.taptap", "TapTap", FUN))
        add(PresetApp("com.qiyi.video", "爱奇艺", FUN))
        add(PresetApp("com.tencent.qqlive", "腾讯视频", FUN))
        add(PresetApp("com.youku.phone", "优酷视频", FUN))
        add(PresetApp("com.hunantv.imgo.activity", "芒果TV", FUN))
        add(PresetApp("com.netease.cloudmusic", "网易云音乐", FUN))
        add(PresetApp("com.tencent.qqmusic", "QQ音乐", FUN))
        add(PresetApp("com.douban.frodo", "豆瓣", FUN))
        add(PresetApp("air.tv.douyu.android", "斗鱼", FUN))
        add(PresetApp("com.lemon.lv", "剪映", FUN))

        // ============================================================ 游戏
        add(PresetApp("com.tencent.tmgp.sgame", "王者荣耀", GAME))
        add(PresetApp("com.tencent.tmgp.pubgmhd", "和平精英", GAME))
        add(PresetApp("com.tencent.lolm", "英雄联盟手游", GAME))
        add(PresetApp("com.tencent.jkchess", "金铲铲之战", GAME))
        // 原神国内版叫 Yuanshen、国际版叫 GenshinImpact，两个都要留着
        add(PresetApp("com.miHoYo.Yuanshen", "原神", GAME))
        add(PresetApp("com.miHoYo.GenshinImpact", "原神", GAME))
        add(PresetApp("com.miHoYo.hkrpg", "崩坏：星穹铁道", GAME))
        add(PresetApp("com.miHoYo.enterprise.NGHSoD", "绝区零", GAME))
        add(PresetApp("com.kurogame.mingchao", "鸣潮", GAME))
        add(PresetApp("com.hypergryph.arknights", "明日方舟", GAME))
        add(PresetApp("com.happyelements.AndroidAnimal", "开心消消乐", GAME))
        add(PresetApp("com.mojang.minecraftpe", "我的世界", GAME))
        add(PresetApp("com.playdigious.deadcells.mobile", "重生细胞", GAME))
        add(PresetApp("com.carrot.rabbit4", "保卫萝卜4", GAME))
        add(PresetApp("com.netease.dwrg", "第五人格", GAME))
        add(PresetApp("com.netease.party", "蛋仔派对", GAME))
        add(PresetApp("com.netease.onmyoji", "阴阳师", GAME))
        add(PresetApp("com.netease.sky", "光·遇", GAME))
        add(PresetApp("com.supercell.clashofclans", "部落冲突", GAME))
        add(PresetApp("com.roblox.client", "Roblox", GAME))
    }

    private val byPackage: Map<String, PresetApp> = ALL.associateBy { it.packageName }
    private val byLabel: Map<String, PresetApp> = ALL.associateBy { it.label }

    fun byPackageName(pkg: String): PresetApp? = byPackage[pkg]

    fun byAppLabel(label: String): PresetApp? = byLabel[label]

    /** 与本机已装列表求交集，返回 (包名 -> 预设)。 */
    fun matchInstalled(installed: List<AppEntry>): Map<String, PresetApp> = buildMap {
        installed.forEach { entry ->
            val hit = byPackage[entry.packageName] ?: byLabel[entry.label]
            if (hit != null) put(entry.packageName, hit)
        }
    }
}

data class PresetApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
)
