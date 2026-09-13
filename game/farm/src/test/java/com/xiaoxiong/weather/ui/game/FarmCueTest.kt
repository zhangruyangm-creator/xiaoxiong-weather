package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 农场操作 → 音效 cue 映射的单测：收获 / 解锁 / 换季各自播一次，
 * 同一事件不重复叠播；收获带 rewards 时走 COIN，不再落到成功集合的
 * UI_CLICK 死分支（`bear_farm_toast_harvest` 已从成功集合移除）。
 */
class FarmCueTest {
    private val harvestReward = FarmRewardSpec(plotIndex = 0, label = "+1 🥕")

    @Test
    fun harvest_playsCoinOnce() {
        val effect =
            FarmEffect(
                toastRes = R.string.bear_farm_toast_harvest,
                rewards = listOf(harvestReward),
            )

        assertEquals(listOf(GameCue.COIN), farmEffectCues(effect))
    }

    @Test
    fun harvestWithUnlock_playsCoinThenStar() {
        val effect =
            FarmEffect(
                toastRes = R.string.bear_farm_toast_harvest,
                rewards = listOf(harvestReward),
                unlockCropIds = listOf("cucumber"),
            )

        assertEquals(listOf(GameCue.COIN, GameCue.STAR), farmEffectCues(effect))
    }

    @Test
    fun buyUnlock_playsStarOnce() {
        val effect = FarmEffect(toastRes = R.string.bear_farm_toast_unlocked)

        assertEquals(listOf(GameCue.STAR), farmEffectCues(effect))
    }

    @Test
    fun newSeason_playsGoalOnce() {
        val effect = FarmEffect(toastRes = R.string.bear_farm_new_season_started)

        assertEquals(listOf(GameCue.GOAL), farmEffectCues(effect))
    }

    @Test
    fun sell_playsCoin() {
        assertEquals(
            listOf(GameCue.COIN),
            farmEffectCues(FarmEffect(toastRes = R.string.bear_farm_toast_sell)),
        )
        assertEquals(
            listOf(GameCue.COIN),
            farmEffectCues(FarmEffect(toastRes = R.string.bear_farm_toast_sell_all)),
        )
    }

    @Test
    fun plainSuccess_playsClick() {
        val effect = FarmEffect(toastRes = R.string.bear_farm_toast_planted)

        assertEquals(listOf(GameCue.UI_CLICK), farmEffectCues(effect))
    }

    @Test
    fun failure_playsBump() {
        val effect = FarmEffect(toastRes = R.string.bear_farm_toast_occupied)

        assertEquals(listOf(GameCue.BUMP), farmEffectCues(effect))
    }

    @Test
    fun careAllWithHarvest_playsCoin() {
        val effect =
            FarmEffect(
                toastRes = R.string.bear_farm_care_all_summary,
                rewards = listOf(harvestReward),
            )

        assertEquals(listOf(GameCue.COIN), farmEffectCues(effect))
    }

    @Test
    fun noFeedback_isSilent() {
        assertTrue(farmEffectCues(FarmEffect()).isEmpty())
    }
}
