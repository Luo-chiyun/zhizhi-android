package app.zhizhi.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONObject

/**
 * 设置持久化。整份设置序列化成一个 JSON 字符串存在 DataStore 里：
 * 字段少、读取频繁、需要原子替换，用 JSON blob 比十几个 key 更好维护。
 */
class SettingsRepository(
    private val context: Context,
    scope: CoroutineScope,
) {
    private val ds = context.focusDataStore

    val settings: StateFlow<MonitorSettings> = ds.data
        .map { decodeSettings(it[KEY_SETTINGS]) }
        .stateIn(scope, SharingStarted.Eagerly, MonitorSettings())

    val overrides: StateFlow<Map<String, AppCategory>> = ds.data
        .map { decodeOverrides(it[KEY_OVERRIDES]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    suspend fun readOnce(): MonitorSettings =
        decodeSettings(ds.data.first()[KEY_SETTINGS])

    suspend fun edit(transform: (MonitorSettings) -> MonitorSettings) {
        ds.edit { prefs ->
            val next = transform(decodeSettings(prefs[KEY_SETTINGS]))
            prefs[KEY_SETTINGS] = encode(next).toString()
        }
    }

    /**
     * 写入一个应用的分类。
     *
     * 注意 IGNORED 也会被**存下来**（而不是删掉键）。因为"不监控"有两种来源：
     *  - 用户从未决定过（键不存在）
     *  - 用户明确点了「不监控」或卡片上的「别再提醒这个应用」（键 = IGNORED）
     * 两者在界面上看起来一样，但套用预设名单时必须能区分：前者可以填，
     * 后者是用户的明确决定，不该被一键覆盖掉。
     */
    suspend fun setCategory(packageName: String, category: AppCategory) {
        ds.edit { prefs ->
            val map = decodeOverrides(prefs[KEY_OVERRIDES]).toMutableMap()
            map[packageName] = category
            prefs[KEY_OVERRIDES] = encodeOverrides(map).toString()
        }
    }

    /**
     * 批量套用预设。
     *
     * **跳过用户已经明确设为 IGNORED 的应用**——包括通过卡片上「别再提醒这个应用」
     * 设过的。否则用户刚说过"这个别管了"，下一次点「一键套用预设名单」就把它放回来了。
     */
    suspend fun setCategories(entries: Map<String, AppCategory>) {
        ds.edit { prefs ->
            val map = decodeOverrides(prefs[KEY_OVERRIDES]).toMutableMap()
            entries.forEach { (pkg, cat) ->
                if (map[pkg] == AppCategory.IGNORED) return@forEach
                map[pkg] = cat
            }
            prefs[KEY_OVERRIDES] = encodeOverrides(map).toString()
        }
    }

    suspend fun clearCategories() {
        ds.edit { it.remove(KEY_OVERRIDES) }
    }

    companion object {
        private val KEY_SETTINGS = stringPreferencesKey("settings_json")
        private val KEY_OVERRIDES = stringPreferencesKey("category_overrides_json")

        internal fun decodeSettings(raw: String?): MonitorSettings {
            if (raw.isNullOrBlank()) return MonitorSettings()
            return runCatching {
                val o = JSONObject(raw)
                val d = MonitorSettings()
                MonitorSettings(
                    masterEnabled = o.optBoolean("masterEnabled", d.masterEnabled),
                    schedule = o.optJSONObject("schedule")?.let { s ->
                        Schedule(
                            enabled = s.optBoolean("enabled", d.schedule.enabled),
                            startMinute = s.optInt("startMinute", d.schedule.startMinute),
                            endMinute = s.optInt("endMinute", d.schedule.endMinute),
                        )
                    } ?: d.schedule,
                    pausedUntilMs = o.optLong("pausedUntilMs", d.pausedUntilMs),
                    firstNudgeAfterSec = o.optInt("firstNudgeAfterSec", d.firstNudgeAfterSec).coerceIn(30, 7200),
                    repeatIntervalSec = o.optInt("repeatIntervalSec", d.repeatIntervalSec).coerceIn(0, 7200),
                    snoozeSec = o.optInt("snoozeSec", d.snoozeSec).coerceIn(60, 1800),
                    graceSec = o.optInt("graceSec", d.graceSec).coerceIn(60, 14_400),
                    answerCooldownSec = o.optInt("answerCooldownSec", d.answerCooldownSec)
                        .coerceIn(0, 3600),
                    gameConfirmEnabled = o.optBoolean("gameConfirmEnabled", d.gameConfirmEnabled),
                    cardAtBottom = o.optBoolean("cardAtBottom", d.cardAtBottom),
                    onboardingDone = o.optBoolean("onboardingDone", d.onboardingDone),
                    autoStartOnBoot = o.optBoolean("autoStartOnBoot", d.autoStartOnBoot),
                    breakDurationMin = o.optInt("breakDurationMin", d.breakDurationMin).coerceIn(1, 180),
                    breakExtendMin = o.optInt("breakExtendMin", d.breakExtendMin).coerceIn(1, 60),
                    breakEndsAtMs = o.optLong("breakEndsAtMs", d.breakEndsAtMs),
                    killTargetOnExit = o.optBoolean("killTargetOnExit", d.killTargetOnExit),
                )
            }.getOrDefault(MonitorSettings())
        }

        internal fun encode(s: MonitorSettings): JSONObject = JSONObject().apply {
            put("masterEnabled", s.masterEnabled)
            put(
                "schedule",
                JSONObject().apply {
                    put("enabled", s.schedule.enabled)
                    put("startMinute", s.schedule.startMinute)
                    put("endMinute", s.schedule.endMinute)
                },
            )
            put("pausedUntilMs", s.pausedUntilMs)
            put("firstNudgeAfterSec", s.firstNudgeAfterSec)
            put("repeatIntervalSec", s.repeatIntervalSec)
            put("snoozeSec", s.snoozeSec)
            put("graceSec", s.graceSec)
            put("answerCooldownSec", s.answerCooldownSec)
            put("gameConfirmEnabled", s.gameConfirmEnabled)
            put("cardAtBottom", s.cardAtBottom)
            put("onboardingDone", s.onboardingDone)
            put("autoStartOnBoot", s.autoStartOnBoot)
            put("breakDurationMin", s.breakDurationMin)
            put("breakExtendMin", s.breakExtendMin)
            put("breakEndsAtMs", s.breakEndsAtMs)
            put("killTargetOnExit", s.killTargetOnExit)
        }

        internal fun decodeOverrides(raw: String?): Map<String, AppCategory> {
            if (raw.isNullOrBlank()) return emptyMap()
            return runCatching {
                val o = JSONObject(raw)
                buildMap {
                    o.keys().forEach { k -> put(k, AppCategory.fromName(o.optString(k))) }
                }
            }.getOrDefault(emptyMap())
        }

        /** 全部写入，包含 IGNORED —— 见 [SettingsRepository.setCategory] 的注释。 */
        internal fun encodeOverrides(map: Map<String, AppCategory>): JSONObject = JSONObject().apply {
            map.forEach { (k, v) -> put(k, v.name) }
        }
    }
}
