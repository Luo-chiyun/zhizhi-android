package app.zhizhi

import android.app.Application
import android.content.Context
import app.zhizhi.data.AppClassifier
import app.zhizhi.data.DiagnosticsStore
import app.zhizhi.data.InstalledAppsRepository
import app.zhizhi.data.SettingsRepository
import app.zhizhi.data.StatsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ZhiZhiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/**
 * 极简服务定位器。刻意不引入 DI 框架：本应用只有五个单例，
 * 少一层依赖就少一层被第三方代码带进网络调用的风险。
 */
object Graph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsRepository
    lateinit var stats: StatsStore
    lateinit var installed: InstalledAppsRepository
    lateinit var classifier: AppClassifier
    lateinit var diagnostics: DiagnosticsStore

    private var ready = false

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val app = context.applicationContext
            settings = SettingsRepository(app, scope)
            stats = StatsStore(app, scope)
            installed = InstalledAppsRepository(app)
            classifier = AppClassifier(app, settings)
            diagnostics = DiagnosticsStore(app, scope)
            classifier.start(scope)
            ready = true
        }
    }

    /** Service / Receiver 里可能先于 Application.onCreate 被调用（极少见），兜底一次。 */
    fun ensure(context: Context) {
        if (!ready) init(context)
    }
}
