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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class DiagnosticEvent(
    val atMs: Long,
    val tag: String,
    val detail: String,
) {
    fun timeText(): String = TIME_FORMAT.format(Date(atMs))

    private companion object {
        val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    }
}

/**
 * 应用内诊断日志。
 *
 * ## 设计约束（0.1.3 重写的原因）
 *
 * 0.1.2 的诊断完全靠 DataStore 持久化，读取端是一个 `ds.data.map{}.stateIn(...)`。
 * 结果在真机上出现了最难查的情况：**代码调了 record()，UI 一条都读不到，而且没有任何报错。**
 * 原因可能有很多种（写入抛异常被 runCatching 吞掉、读取流被上游异常掐死、看的根本不是同一个安装），
 * 但根子在于——**一个用来排查故障的机制本身不允许有任何静默失败的可能。**
 *
 * 所以现在分两层：
 *
 *  - **内存层（权威）**：一个 MutableStateFlow 环形缓冲。只要有调用就一定出现在 UI 上，
 *    不依赖磁盘、不依赖协程调度、不依赖反序列化。进程重启才丢。
 *  - **持久层（尽力而为）**：写 DataStore，失败只记 [lastError] 并打一条 Log.e，绝不影响内存层。
 *
 * 另外暴露 [totalRecorded] 和 [lastError]：前者是"这个进程一共记了多少条"，
 * 只要它在涨就说明记录调用真的发生了；后者让写入失败第一次变得可见。
 */
class DiagnosticsStore(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val ds = context.focusDataStore

    private val _events = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events: StateFlow<List<DiagnosticEvent>> = _events.asStateFlow()

    /** 本进程累计调用 record() 的次数。它只增不减，用来证明"记录确实发生了"。 */
    private val _totalRecorded = MutableStateFlow(0)
    val totalRecorded: StateFlow<Int> = _totalRecorded.asStateFlow()

    /** 持久化失败时的异常摘要。为 null 表示一直写得好好的。 */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** 是否已有一次落盘在排队，用于节流。 */
    @Volatile
    private var persistScheduled = false

    init {
        // 上次运行留下的记录，尽力读一次，读不到就算了——内存层照样能用。
        scope.launch {
            runCatching { decode(ds.data.first()[KEY_EVENTS]) }
                .onSuccess { persisted ->
                    if (persisted.isNotEmpty() && _events.value.isEmpty()) {
                        _events.value = persisted
                    }
                }
                .onFailure { remember(it, "读取历史记录失败（不影响本次会话）") }
        }
    }

    fun record(tag: String, detail: String) {
        // 1) logcat —— 用 adb 的时候不依赖应用内任何东西
        runCatching { Log.i(LOGCAT_TAG, "[$tag] $detail") }

        // 2) 内存层：先做这一步，保证 UI 一定看得到
        val event = DiagnosticEvent(System.currentTimeMillis(), tag, detail)
        _events.update { current ->
            (listOf(event) + current).take(MAX_EVENTS)
        }
        _totalRecorded.update { it + 1 }

        // 3) 持久层：失败也不影响上面
        schedulePersist()
    }

    /**
     * 持久化节流：最多每 [PERSIST_DEBOUNCE_MS] 写一次。
     *
     * 内存里留 1000 条，但**不把 1000 条都写盘**：每写一次都要序列化并重写整个 DataStore 文件，
     * 1000 条约 150 KB，每条记录都写一遍纯属浪费 I/O。所以磁盘只保留最近 [PERSIST_LIMIT] 条，
     * 并且至少间隔 [PERSIST_DEBOUNCE_MS] 才写一次。内存层不受影响。
     */
    private fun schedulePersist() {
        if (persistScheduled) return
        persistScheduled = true
        scope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persistScheduled = false
            persist()
        }
    }

    private suspend fun persist() {
        try {
            val toWrite = encode(_events.value.take(PERSIST_LIMIT)).toString()
            ds.edit { prefs ->
                prefs[KEY_EVENTS] = toWrite
            }
            if (_lastError.value != null) _lastError.value = null
        } catch (error: Throwable) {
            remember(error, "写入记录失败")
        }
    }

    private fun remember(error: Throwable, what: String) {
        val text = "$what：${error.javaClass.name}: ${error.message}"
        _lastError.value = text
        runCatching { Log.e(LOGCAT_TAG, text, error) }
    }

    suspend fun clear() {
        _events.value = emptyList()
        try {
            ds.edit { it.remove(KEY_EVENTS) }
        } catch (error: Throwable) {
            remember(error, "清空失败")
        }
    }

    /** 供"复制到剪贴板"用。用户可以直接把它贴进 issue，不需要 adb，也不需要截图。 */
    fun dumpText(): String = buildString {
        appendLine(HEADER_TITLE)
        appendLine("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("包名：${context.packageName}")
        appendLine("本进程累计记录：${_totalRecorded.value} 条（当前保留 ${_events.value.size} 条）")
        appendLine("持久化错误：${_lastError.value ?: "无"}")
        appendLine("---- 事件（新→旧，最多 $MAX_EVENTS 条）----")
        append(toLines(_events.value))
    }

    companion object {
        /** logcat 里一条命令就能抓全部本应用的关键事件。 */
        const val LOGCAT_TAG = "ZhiZhi"

        const val HEADER_TITLE = "知止 诊断导出"

        /** 内存里保留的事件条数。这一层是权威，UI 和导出都读它。 */
        const val MAX_EVENTS = 1000

        /** 磁盘上保留的条数。见 [schedulePersist] 的注释。 */
        private const val PERSIST_LIMIT = 200

        /** 两次落盘之间的最小间隔。 */
        private const val PERSIST_DEBOUNCE_MS = 2_000L

        private val KEY_EVENTS = stringPreferencesKey("diagnostics_json")

        /** 把事件列表渲染成逐行文本，供 UI 的单一块文本区域和剪贴板共用。 */
        fun toLines(events: List<DiagnosticEvent>): String = buildString {
            if (events.isEmpty()) {
                appendLine("（空）")
                return@buildString
            }
            events.forEach { event ->
                append(event.timeText())
                append("  [")
                append(event.tag)
                append("] ")
                appendLine(event.detail)
            }
        }

        internal fun decode(raw: String?): List<DiagnosticEvent> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) {
                        val o = array.optJSONObject(i) ?: continue
                        add(
                            DiagnosticEvent(
                                atMs = o.optLong("at"),
                                tag = o.optString("tag"),
                                detail = o.optString("detail"),
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        internal fun encode(events: List<DiagnosticEvent>): JSONArray = JSONArray().apply {
            events.forEach { event ->
                put(
                    JSONObject().apply {
                        put("at", event.atMs)
                        put("tag", event.tag)
                        put("detail", event.detail)
                    },
                )
            }
        }
    }
}
