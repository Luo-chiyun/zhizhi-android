package app.zhizhi.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.zhizhi.BuildConfig
import app.zhizhi.R
import app.zhizhi.ui.components.CardSection
import app.zhizhi.ui.components.HintBlock
import app.zhizhi.ui.components.ValueRow

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // 名字来源 —— 这一页最重要的一块，放在最上面
        HintBlock(
            text = stringResource(R.string.app_quote) + "\n" +
                stringResource(R.string.app_quote_source),
        )

        Spacer(Modifier.height(16.dp))
        CardSection(title = stringResource(R.string.about_name_title)) {
            Text(
                text = stringResource(R.string.about_name_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        CardSection(title = stringResource(R.string.about_privacy_title)) {
            Text(
                text = stringResource(R.string.about_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        CardSection(title = stringResource(R.string.about_license_title)) {
            Text(
                text = stringResource(R.string.about_license_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ValueRow(
                stringResource(R.string.diag_version),
                stringResource(
                    R.string.about_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
            )
        }

        Spacer(Modifier.height(16.dp))
        CardSection(title = stringResource(R.string.about_donate_title)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.about_donate_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                DonationQr(
                    painterRes = R.drawable.donation_wechat,
                    label = stringResource(R.string.about_donate_wechat),
                )
                Spacer(Modifier.height(18.dp))
                DonationQr(
                    painterRes = R.drawable.donation_alipay,
                    label = stringResource(R.string.about_donate_alipay),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        CardSection(title = stringResource(R.string.about_source)) {
            Text(
                text = "https://github.com/Luo-chiyun/zhizhi-android",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 收款码 + 说明。两张码统一尺寸和间距，避免一高一低。 */
@Composable
private fun DonationQr(painterRes: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(painterRes),
            contentDescription = label,
            modifier = Modifier
                .size(210.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
