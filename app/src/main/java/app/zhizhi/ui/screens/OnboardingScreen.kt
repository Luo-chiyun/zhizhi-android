package app.zhizhi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.zhizhi.R
import app.zhizhi.ui.components.ZhiZhiCard
import kotlinx.coroutines.launch

/**
 * 首次启动的引导页。
 *
 * 三页内容用 [HorizontalPager] 承载，**支持左右滑动**——引导页只给一个「下一步」按钮，
 * 等于逼着用户按顺序点三下；很多人第一反应是往左划，划不动就以为卡死了。
 * 按钮和滑动是两条并行的入口，指示点也可以直接点着跳。
 */
@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    val pages = listOf(
        R.string.onboard_p1,
        R.string.onboard_p2,
        R.string.onboard_p3,
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(22.dp))

        // 内容区：可左右滑动。weight(1f) 给它一个确定的高度，Pager 才能工作。
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            pageSpacing = 14.dp,
        ) { index ->
            Column(Modifier.fillMaxSize()) {
                ZhiZhiCard {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(pages[index]),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 指示点：不只是显示进度，点一下就跳过去。
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            pages.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == page) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == page) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboard_swipe_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.app_quote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page > 0) {
                TextButton(onClick = {
                    scope.launch { pagerState.animateScrollToPage(page - 1) }
                }) {
                    Text(stringResource(R.string.back))
                }
            }
            Button(
                onClick = {
                    if (page < pages.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(page + 1) }
                    } else {
                        onStart()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        if (page < pages.lastIndex) R.string.next else R.string.onboard_start,
                    ),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
