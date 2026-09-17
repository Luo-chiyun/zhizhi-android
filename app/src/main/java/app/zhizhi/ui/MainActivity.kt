package app.zhizhi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zhizhi.Graph
import app.zhizhi.R
import app.zhizhi.notify.Notifications
import app.zhizhi.ui.components.NavTab
import app.zhizhi.ui.components.TopBar
import app.zhizhi.ui.components.ZhiZhiNavBar
import app.zhizhi.ui.screens.AboutScreen
import app.zhizhi.ui.screens.AdvancedSettingsScreen
import app.zhizhi.ui.screens.BreakScreen
import app.zhizhi.ui.screens.CategoryScreen
import app.zhizhi.ui.screens.DiagnosticsScreen
import app.zhizhi.ui.screens.HomeScreen
import app.zhizhi.ui.screens.OnboardingScreen
import app.zhizhi.ui.screens.PermissionsScreen
import app.zhizhi.ui.screens.StatsScreen
import app.zhizhi.ui.theme.ZhiZhiTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.ensure(this)
        Notifications.ensureChannels(this)
        setContent {
            ZhiZhiTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    AppRoot()
                }
            }
        }
    }
}

/** 从首页 push 进来的子页面。 */
private enum class Page(val titleRes: Int) {
    Categories(R.string.feature_categories),
    Break(R.string.feature_break),
    Diagnostics(R.string.diag_title),
    Permissions(R.string.perm_title),
    About(R.string.about_title),
}

/** 引导流程的三个阶段。 */
private enum class Intro { Onboarding, Permissions, Done }

@Composable
private fun AppRoot() {
    val settings by Graph.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var introName by rememberSaveable {
        mutableStateOf(
            if (settings.onboardingDone) Intro.Done.name else Intro.Onboarding.name,
        )
    }
    val intro = Intro.valueOf(introName)

    var tabName by rememberSaveable { mutableStateOf(NavTab.Home.name) }
    val tab = NavTab.valueOf(tabName)

    // 子页面返回栈：首页是栈底，任何子页面都是 push 上去的。
    // 用 rememberSaveable + listSaver：横竖屏切换会重建 Activity，
    // 普通 remember 会让用户正看着的诊断页突然弹回首页。
    val pages = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf<String>() }

    fun push(page: Page) {
        if (pages.lastOrNull() != page.name) pages.add(page.name)
    }

    fun pop(): Boolean {
        if (pages.isEmpty()) return false
        pages.removeAt(pages.lastIndex)
        return true
    }

    // 返回键 / 手势导航的「左滑右滑」都走这里。
    // 在 ColorOS 的手势导航下，屏幕左右边缘向内滑本身就是系统的"返回"手势，
    // 它派发 KEYCODE_BACK —— 接住它，先退子页面，再退 tab，最后才真的退出应用。
    BackHandler(enabled = pages.isNotEmpty() || tab != NavTab.Home) {
        if (!pop() && tab != NavTab.Home) tabName = NavTab.Home.name
    }

    when (intro) {
        Intro.Onboarding -> {
            OnboardingScreen(onStart = { introName = Intro.Permissions.name })
        }

        Intro.Permissions -> {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                TopBar(title = stringResource(R.string.perm_title))
                Box(Modifier.weight(1f)) {
                    PermissionsScreen(
                        primaryLabel = stringResource(R.string.perm_primary_done),
                        onPrimary = {
                            scope.launch { Graph.settings.edit { it.copy(onboardingDone = true) } }
                            introName = Intro.Done.name
                        },
                    )
                }
            }
        }

        Intro.Done -> {
            val current = pages.lastOrNull()?.let { Page.valueOf(it) }
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                if (current != null) {
                    TopBar(title = stringResource(current.titleRes), onBack = { pop() })
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // 子页面没有底部导航栏，需要自己避开手势条/导航栏；
                        // 有 tab 栏时它已经处理过底部 inset，这里再加会重复留白。
                        .then(
                            if (current != null) {
                                Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    when (current) {
                        Page.Categories -> CategoryScreen()
                        Page.Break -> BreakScreen()
                        Page.Diagnostics -> DiagnosticsScreen()
                        Page.Permissions -> PermissionsScreen(
                            primaryLabel = stringResource(R.string.perm_primary_back),
                            onPrimary = { pop() },
                        )

                        Page.About -> AboutScreen()

                        null -> when (tab) {
                            NavTab.Home -> HomeScreen(
                                onOpenCategories = { push(Page.Categories) },
                                onOpenBreak = { push(Page.Break) },
                                onOpenAdvanced = { tabName = NavTab.Settings.name },
                                onOpenStats = { tabName = NavTab.Stats.name },
                                onOpenDiagnostics = { push(Page.Diagnostics) },
                                onOpenPermissions = { push(Page.Permissions) },
                                onOpenAbout = { push(Page.About) },
                            )

                            NavTab.Stats -> StatsScreen()
                            NavTab.Settings -> AdvancedSettingsScreen()
                        }
                    }
                }
                if (current == null) {
                    ZhiZhiNavBar(current = tab, onSelect = { tabName = it.name })
                }
            }
        }
    }
}
