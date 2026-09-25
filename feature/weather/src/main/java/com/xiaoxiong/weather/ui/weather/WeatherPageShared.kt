package com.xiaoxiong.weather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R

@Composable
fun OfflineBanner(lastUpdatedText: String?) {
    val text =
        if (lastUpdatedText != null) {
            stringResource(R.string.offline_updated, lastUpdatedText)
        } else {
            stringResource(R.string.offline_cached)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(MaterialTheme.colorScheme.tertiary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
fun EmptyHint(text: String) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 无数据且上一次拉取失败时的错误态。
 *
 * 此前失败只弹一次 Snackbar 就被清掉，页面随后只显示「暂无今天的天气数据」：
 * 用户错过那几秒就既看不到原因、也没有任何恢复入口，只能下拉重试或杀进程。
 * 这里把原因与重试按钮常驻在页面上。
 */
@Composable
fun ErrorRetryCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SoftCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.label_retry),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
