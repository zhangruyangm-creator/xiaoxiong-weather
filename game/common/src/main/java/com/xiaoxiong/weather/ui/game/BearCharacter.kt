package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.core.content.edit
import com.xiaoxiong.weather.R

/** 动作类游戏可选主角：浅粉 Q 版 / 蜂蜜棕太阳熊。 */
enum class BearCharacter(
    @StringRes val labelRes: Int,
    @StringRes val detailRes: Int,
    @DrawableRes val previewRes: Int,
) {
    PEACH(
        labelRes = R.string.bear_character_peach,
        detailRes = R.string.bear_character_peach_detail,
        previewRes = R.drawable.bear_run_a,
    ),
    SUN(
        labelRes = R.string.bear_character_sun,
        detailRes = R.string.bear_character_sun_detail,
        previewRes = R.drawable.bear_sun_run_a,
    ),
    ;

    @DrawableRes
    fun runA(): Int = if (this == PEACH) R.drawable.bear_run_a else R.drawable.bear_sun_run_a

    @DrawableRes
    fun runDownA(): Int = if (this == PEACH) R.drawable.bear_run_down_a else R.drawable.bear_sun_run_down_a

    @DrawableRes
    fun runPassA(): Int = if (this == PEACH) R.drawable.bear_run_pass_a else R.drawable.bear_sun_run_pass_a

    @DrawableRes
    fun runPushA(): Int = if (this == PEACH) R.drawable.bear_run_push_a else R.drawable.bear_sun_run_push_a

    @DrawableRes
    fun runB(): Int = if (this == PEACH) R.drawable.bear_run_b else R.drawable.bear_sun_run_b

    @DrawableRes
    fun runDownB(): Int = if (this == PEACH) R.drawable.bear_run_down_b else R.drawable.bear_sun_run_down_b

    @DrawableRes
    fun runPassB(): Int = if (this == PEACH) R.drawable.bear_run_pass_b else R.drawable.bear_sun_run_pass_b

    @DrawableRes
    fun runPushB(): Int = if (this == PEACH) R.drawable.bear_run_push_b else R.drawable.bear_sun_run_push_b

    @DrawableRes
    fun jump(): Int = if (this == PEACH) R.drawable.bear_jump else R.drawable.bear_sun_jump

    @DrawableRes
    fun fall(): Int = if (this == PEACH) R.drawable.bear_fall else R.drawable.bear_sun_fall

    @DrawableRes
    fun land(): Int = if (this == PEACH) R.drawable.bear_land else R.drawable.bear_sun_land
}

private const val PREF_BEAR_CHARACTER = "bear_character"

object BearCharacterStore {
    fun load(prefs: SharedPreferences): BearCharacter {
        val raw = prefs.getString(PREF_BEAR_CHARACTER, BearCharacter.PEACH.name)
        return runCatching { BearCharacter.valueOf(raw!!) }.getOrDefault(BearCharacter.PEACH)
    }

    fun save(
        prefs: SharedPreferences,
        character: BearCharacter,
    ) {
        prefs.edit { putString(PREF_BEAR_CHARACTER, character.name) }
    }
}

/** 全局主角选择：写入 [bear_game] SharedPreferences，各游戏共用。 */
class BearCharacterState(
    private val prefs: SharedPreferences,
) {
    var selected by mutableStateOf(BearCharacterStore.load(prefs))
        private set

    fun select(character: BearCharacter) {
        if (selected == character) return
        selected = character
        BearCharacterStore.save(prefs, character)
    }
}

@Composable
fun rememberBearCharacterState(): BearCharacterState {
    val prefs = rememberGamePrefs()
    return remember(prefs) { BearCharacterState(prefs) }
}

@Composable
fun rememberBearSprites(character: BearCharacter): BearSprites =
    BearSprites(
        runA = ImageBitmap.imageResource(character.runA()),
        runDownA = ImageBitmap.imageResource(character.runDownA()),
        runPassA = ImageBitmap.imageResource(character.runPassA()),
        runPushA = ImageBitmap.imageResource(character.runPushA()),
        runB = ImageBitmap.imageResource(character.runB()),
        runDownB = ImageBitmap.imageResource(character.runDownB()),
        runPassB = ImageBitmap.imageResource(character.runPassB()),
        runPushB = ImageBitmap.imageResource(character.runPushB()),
        jump = ImageBitmap.imageResource(character.jump()),
        fall = ImageBitmap.imageResource(character.fall()),
        land = ImageBitmap.imageResource(character.land()),
    )
