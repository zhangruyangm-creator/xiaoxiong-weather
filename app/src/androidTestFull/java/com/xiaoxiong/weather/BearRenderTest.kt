package com.xiaoxiong.weather

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiaoxiong.weather.ui.game.BearFrame
import com.xiaoxiong.weather.ui.game.BearPose
import com.xiaoxiong.weather.ui.game.BearSprites
import com.xiaoxiong.weather.ui.game.drawGameBear
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 小熊 8 帧真机渲染调试。
 * `./gradlew :app:installFullDebug :app:installFullDebugAndroidTest` 后
 * `adb shell am instrument -w -e class com.xiaoxiong.weather.BearRenderTest com.xiaoxiong.weather.test/androidx.test.runner.AndroidJUnitRunner`
 */
@RunWith(AndroidJUnit4::class)
class BearRenderTest {
    @Test
    fun renderBearPoses() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { act ->
                val view =
                    ComposeView(act).apply {
                        setContent {
                            val sprites =
                                BearSprites(
                                    runA = ImageBitmap.imageResource(R.drawable.bear_run_a),
                                    runDownA = ImageBitmap.imageResource(R.drawable.bear_run_down_a),
                                    runPassA = ImageBitmap.imageResource(R.drawable.bear_run_pass_a),
                                    runPushA = ImageBitmap.imageResource(R.drawable.bear_run_push_a),
                                    runB = ImageBitmap.imageResource(R.drawable.bear_run_b),
                                    runDownB = ImageBitmap.imageResource(R.drawable.bear_run_down_b),
                                    runPassB = ImageBitmap.imageResource(R.drawable.bear_run_pass_b),
                                    runPushB = ImageBitmap.imageResource(R.drawable.bear_run_push_b),
                                    jump = ImageBitmap.imageResource(R.drawable.bear_jump),
                                    fall = ImageBitmap.imageResource(R.drawable.bear_fall),
                                    land = ImageBitmap.imageResource(R.drawable.bear_land),
                                )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFFA8DEF5)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(modifier = Modifier.size(420.dp, 300.dp)) {
                                    val bs = size.height / 3.2f
                                    // 上排：跑步 8 拍
                                    val run =
                                        listOf(
                                            BearFrame.RUN_A,
                                            BearFrame.RUN_DOWN_A,
                                            BearFrame.RUN_PASS_A,
                                            BearFrame.RUN_PUSH_A,
                                            BearFrame.RUN_B,
                                            BearFrame.RUN_DOWN_B,
                                            BearFrame.RUN_PASS_B,
                                            BearFrame.RUN_PUSH_B,
                                        )
                                    run.forEachIndexed { i, f ->
                                        drawGameBear(
                                            sprites,
                                            size.width / 16f * (1 + i * 2),
                                            size.height * 0.32f,
                                            bs,
                                            BearPose(time = i.toFloat(), frame = f),
                                        )
                                    }
                                    // 下排：跳 / 落 / 落地 / 死
                                    val air =
                                        listOf(
                                            BearPose(time = 1f, frame = BearFrame.JUMP, scaleY = 1.08f, scaleX = 0.94f),
                                            BearPose(time = 2f, frame = BearFrame.FALL),
                                            BearPose(time = 3f, frame = BearFrame.LAND, scaleY = 0.88f, scaleX = 1.08f),
                                            BearPose(time = 4f, frame = BearFrame.FALL, dead = true, bodyTilt = -95f),
                                        )
                                    air.forEachIndexed { i, pose ->
                                        drawGameBear(
                                            sprites,
                                            size.width / 8f * (1 + i * 2),
                                            size.height * 0.78f,
                                            bs,
                                            pose,
                                        )
                                    }
                                }
                            }
                        }
                    }
                act.setContentView(view)
            }
            Thread.sleep(1500)
            scenario.onActivity { act ->
                val v = act.window.decorView
                val bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bitmap))
                val out = File(act.getExternalFilesDir(null), "bear_render.png")
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                println("BEAR_RENDER_SAVED: ${out.absolutePath}")
            }
        }
    }
}
