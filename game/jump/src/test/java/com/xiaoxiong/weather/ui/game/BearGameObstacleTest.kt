package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BearGameObstacleTest {
    @Test
    fun stormAndHail_dominateStormClouds() {
        // 雷暴/冰雹：雷云占主导（r ∈ [0.45, 1.0)），而不是比晴天还少
        assertEquals(ObstacleType.STORM_CLOUD, BearGame().obstacleTypeFor(RunScene.STORM, 0.5f))
        assertEquals(ObstacleType.STORM_CLOUD, BearGame().obstacleTypeFor(RunScene.STORM, 0.99f))
        assertEquals(ObstacleType.STORM_CLOUD, BearGame().obstacleTypeFor(RunScene.HAIL, 0.6f))
        assertEquals(ObstacleType.PUDDLE, BearGame().obstacleTypeFor(RunScene.STORM, 0.29f))
        assertEquals(ObstacleType.SNOWMAN, BearGame().obstacleTypeFor(RunScene.STORM, 0.44f))
    }

    @Test
    fun sameRandomValue_stormGivesCloud_moreThanSunny() {
        // 同一随机数下，雷暴天出雷云、晴天出雪人——修复前晴天雷云(28%)反而高于雷暴(20%)
        val r = 0.5f
        assertEquals(ObstacleType.STORM_CLOUD, BearGame().obstacleTypeFor(RunScene.STORM, r))
        assertEquals(ObstacleType.SNOWMAN, BearGame().obstacleTypeFor(RunScene.SUNNY, r))
        assertEquals(ObstacleType.PUDDLE, BearGame().obstacleTypeFor(RunScene.RAIN, r))
    }

    @Test
    fun rainKeepsPuddlesDominant() {
        assertEquals(ObstacleType.PUDDLE, BearGame().obstacleTypeFor(RunScene.RAIN, 0.3f))
        assertEquals(ObstacleType.PUDDLE, BearGame().obstacleTypeFor(RunScene.RAIN, 0.61f))
        assertEquals(ObstacleType.SNOWMAN, BearGame().obstacleTypeFor(RunScene.RAIN, 0.79f))
        assertEquals(ObstacleType.STORM_CLOUD, BearGame().obstacleTypeFor(RunScene.RAIN, 0.99f))
    }

    @Test
    fun snowKeepsSnowmanDominant_andHasIceWalls() {
        val game = BearGame()
        assertEquals(ObstacleType.PUDDLE, game.obstacleTypeFor(RunScene.SNOW, 0.13f))
        assertEquals(ObstacleType.SNOWMAN, game.obstacleTypeFor(RunScene.SNOW, 0.5f))
        assertEquals(ObstacleType.ICE_WALL, game.obstacleTypeFor(RunScene.SNOW, 0.6f))
        assertEquals(ObstacleType.STORM_CLOUD, game.obstacleTypeFor(RunScene.SNOW, 0.99f))
    }

    @Test
    fun freezingRain_canHaveIceWalls() {
        val game = BearGame()
        assertEquals(ObstacleType.ICE_WALL, game.obstacleTypeFor(RunScene.FREEZING_RAIN, 0.7f))
        assertEquals(ObstacleType.PUDDLE, game.obstacleTypeFor(RunScene.FREEZING_RAIN, 0.41f))
    }

    @Test
    fun warmScenes_neverSpawnIceWalls() {
        val game = BearGame()
        // 晴/阴/雨/雷暴/冰雹：任何 r 都不出冰墙
        for (scene in listOf(RunScene.SUNNY, RunScene.CLOUDY, RunScene.RAIN, RunScene.STORM, RunScene.HAIL)) {
            for (r in listOf(0.01f, 0.3f, 0.5f, 0.75f, 0.99f)) {
                assertNotEquals(ObstacleType.ICE_WALL, game.obstacleTypeFor(scene, r))
            }
        }
    }

    @Test
    fun iceWall_gatedBySceneThreshold_downgradesToSnowman() {
        val game = BearGame()
        // 雪天：门槛 300 分，未到前冰墙降级为雪人
        assertEquals(ObstacleType.SNOWMAN, game.spawnType(RunScene.SNOW, 0.6f, score = 299))
        assertEquals(ObstacleType.ICE_WALL, game.spawnType(RunScene.SNOW, 0.6f, score = 300))
        // 冻雨天：门槛 200 分，更早出现
        assertEquals(ObstacleType.SNOWMAN, game.spawnType(RunScene.FREEZING_RAIN, 0.7f, score = 199))
        assertEquals(ObstacleType.ICE_WALL, game.spawnType(RunScene.FREEZING_RAIN, 0.7f, score = 200))
    }
}
