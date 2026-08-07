package com.xiaoxiong.weather.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.weather.AppScreenBackground

private val urlRegex = Regex("https?://[^\\s]+")
private val mailRegex = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.-]+")

/** 文本里的一处可点击链接：字符范围 + 打开地址。 */
private class LinkSpan(
    val start: Int,
    val end: Int,
    val url: String,
)

/**
 * 把纯文本里的网址与邮箱标注为链接样式，并返回链接范围。
 * 邮箱会转成 mailto 打开。
 */
private fun linkifyText(
    raw: String,
    linkStyle: SpanStyle,
): Pair<AnnotatedString, List<LinkSpan>> {
    val candidates =
        buildList {
            urlRegex.findAll(raw).forEach { add(it.range to it.value.trimEnd('。', '，', '；', '）', ']', '》')) }
            mailRegex.findAll(raw).forEach { add(it.range to it.value) }
        }.sortedBy { it.first.first }

    val spans = mutableListOf<LinkSpan>()
    val builder =
        buildAnnotatedString {
            var last = 0
            for ((range, value) in candidates) {
                if (range.first < last) continue
                append(raw, last, range.first)
                val url = if ('@' in value) "mailto:$value" else value
                val start = length
                withStyle(linkStyle) { append(value) }
                spans += LinkSpan(start, length, url)
                last = range.last + 1
            }
            append(raw, last, raw.length)
        }
    return builder to spans
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val loadFailed = stringResource(R.string.privacy_policy_load_failed)
    val text =
        remember(loadFailed) {
            runCatching {
                context.assets
                    .open("privacy_policy.txt")
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrDefault(loadFailed)
        }
    val linkStyle =
        SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
    val (linkedText, links) = remember(text, linkStyle) { linkifyText(text, linkStyle) }
    val uriHandler = LocalUriHandler.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    BackHandler(onBack = onBack)

    AppScreenBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Text(
                            stringResource(R.string.privacy_policy_title),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            SelectionContainer {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = linkedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        onTextLayout = { layout = it },
                        modifier =
                            Modifier.pointerInput(links) {
                                awaitEachGesture {
                                    // 点击落在链接范围内才打开，其余事件（选择/滚动）不拦截
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val result = layout ?: return@awaitEachGesture
                                    val offset = result.getOffsetForPosition(down.position)
                                    val link = links.firstOrNull { offset in it.start until it.end }
                                    if (link != null) {
                                        uriHandler.openUri(link.url)
                                        waitForUpOrCancellation()?.consume()
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
}
