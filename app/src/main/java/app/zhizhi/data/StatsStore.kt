package app.zhizhi.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class DayStats(
    val nudges: Int = 0,
    val returned: Int = 0,
    val snoozed: Int = 0,
    val grace: Int = 0,
    val gameConfirm: Int = 0,
    val gameDeclined: Int = 0,
    val monitoredMs: Long = 0L,
    val breakTaken: Int = 0,
    /** "一键回到桌面"被系统拦截的次数。用来在真机上验证后台启动 Activity 是否真的可行。 */
    val exitAttempts: Int = 0,
    val exitSuccess: Int = 0,
    /** 悬浮窗被确认可见的次数 */
    val overlayShown: Int = 0,
    /** 悬浮窗加不上或加上了但实测不可见、只能降级成通知的次数 */
    val overlayFailures: Int = 0,
    /** 用户点「别再提醒这个应用」的次数 */
    val mutedApps: Int = 0,
)

/**
 * 本地统计。只有"次数"和"总时长"，不记录你打开过哪个应用、什么时间打开——
 * 这样即使手机被他人拿到，这份数据也说明不了任何具体行为。
 */
class StatsStore(
    private val context: Context,
    scope: CoroutineScope,
) {
    private val ds = context.focusDataStore

    private val _days = MutableStateFlow<Map<String, DayStats>>(emptyMap())

    /** 首页与记录页观察它。 */
    val days: StateFlow<Map<String, DayStats>> = _days

    init {
        // 这里刻意**不用** `stateIn(scope, Eagerly, emptyMap())`。
        //
        // DataStore 的 data 流在读写失败时会抛异常并把上游结束掉，而 stateIn 之后
        // 得到的 StateFlow 只会**冻结在最后一个值**上：不报错、不重试，界面从此不再更新。
        // 表现出来就是"仪表盘的数据不刷新"，而且现场什么线索都没有。
        // 自己接管这个循环，断了就重连。
        scope.launch {
            while (true) {
                runCatching {
                    ds.data.collect { prefs -> _days.value = decode(prefs[KEY_DAYS]) }
                }.onFailure { error ->
                    Log.w("ZhiZhi", "统计流中断，1 秒后重连", error)
                }
                delay(1_000L)
            }
        }
    }

    suspend fun bump(block: (DayStats) -> DayStats) {
        val today = todayKey()
        ds.edit { prefs ->
            val map = decode(prefs[KEY_DAYS]).toMutableMap()
            map[today] = block(map[today] ?: DayStats())
            // 只保留最近 120 天，避免这份 JSON 无限长大。
            if (map.size > 120) {
                val keep = map.keys.sorted().takeLast(120).toSet()
                map.keys.toList().filter { it !in keep }.forEach { map.remove(it) }
            }
            prefs[KEY_DAYS] = encode(map).toString()
        }
    }

    suspend fun clear() {
        ds.edit { it.remove(KEY_DAYS) }
    }

    companion object {
        private val KEY_DAYS = stringPreferencesKey("stats_days_json")

        fun todayKey(now: Long = System.currentTimeMillis()): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

        internal fun decode(raw: String?): Map<String, DayStats> {
            if (raw.isNullOrBlank()) return emptyMap()
            return runCatching {
                val root = JSONObject(raw)
                buildMap {
                    root.keys().forEach { day ->
                        val o = root.optJSONObject(day) ?: return@forEach
                        put(
                            day,
                            DayStats(
                                nudges = o.optInt("nudges"),
                                returned = o.optInt("returned"),
                                snoozed = o.optInt("snoozed"),
                                grace = o.optInt("grace"),
                                gameConfirm = o.optInt("gameConfirm"),
                                gameDeclined = o.optInt("gameDeclined"),
                                monitoredMs = o.optLong("monitoredMs"),
                                breakTaken = o.optInt("breakTaken"),
                                exitAttempts = o.optInt("exitAttempts"),
                                exitSuccess = o.optInt("exitSuccess"),
                                overlayShown = o.optInt("overlayShown"),
                                overlayFailures = o.optInt("overlayFailures"),
                                mutedApps = o.optInt("mutedApps"),
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyMap())
        }

        internal fun encode(map: Map<String, DayStats>): JSONObject = JSONObject().apply {
            map.forEach { (day, s) ->
                put(
                    day,
                    JSONObject().apply {
                        put("nudges", s.nudges)
                        put("returned", s.returned)
                        put("snoozed", s.snoozed)
                        put("grace", s.grace)
                        put("gameConfirm", s.gameConfirm)
                        put("gameDeclined", s.gameDeclined)
                        put("monitoredMs", s.monitoredMs)
                        put("breakTaken", s.breakTaken)
                        put("exitAttempts", s.exitAttempts)
                        put("exitSuccess", s.exitSuccess)
                        put("overlayShown", s.overlayShown)
                        put("overlayFailures", s.overlayFailures)
                        put("mutedApps", s.mutedApps)
                    },
                )
            }
        }
    }
}
