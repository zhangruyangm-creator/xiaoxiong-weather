package com.xiaoxiong.weather.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAtmosphereTest {
    @Test
    fun positiveModulo_wrapsNegativeValues() {
        assertEquals(0.75f, positiveModulo(-0.25f, 1f), 1e-5f)
        assertEquals(0.5f, positiveModulo(1.5f, 1f), 1e-5f)
        assertEquals(0f, positiveModulo(-2f, 1f), 1e-5f)
        assertEquals(0.1f, positiveModulo(-1.9f, 1f), 1e-5f)
        assertEquals(0.3f, positiveModulo(2.3f, 1f), 1e-5f)
    }

    @Test
    fun rainWindSlope_matchesSceneIntensity() {
        assertEquals(0.14f, rainWindSlope(WeatherScene.DRIZZLE), 1e-5f)
        assertEquals(0.20f, rainWindSlope(WeatherScene.RAIN), 1e-5f)
        assertEquals(0.26f, rainWindSlope(WeatherScene.HEAVY_RAIN), 1e-5f)
        assertEquals(0.26f, rainWindSlope(WeatherScene.THUNDER), 1e-5f)
        assertEquals(0.10f, rainWindSlope(WeatherScene.FREEZING_RAIN), 1e-5f)
        assertEquals(0f, rainWindSlope(WeatherScene.CLEAR), 1e-5f)
        assertEquals(0f, rainWindSlope(WeatherScene.SNOW), 1e-5f)
    }

    @Test
    fun particleLayers_matchSceneBudgets() {
        assertEquals(listOf(14, 10, 6), atmosphereParticleLayers(WeatherScene.DRIZZLE))
        assertEquals(listOf(20, 22, 12), atmosphereParticleLayers(WeatherScene.RAIN))
        assertEquals(listOf(30, 34, 20), atmosphereParticleLayers(WeatherScene.HEAVY_RAIN))
        assertEquals(listOf(30, 34, 20), atmosphereParticleLayers(WeatherScene.THUNDER))
        assertEquals(listOf(22, 24, 14), atmosphereParticleLayers(WeatherScene.HAIL))
        assertEquals(listOf(22, 24, 12), atmosphereParticleLayers(WeatherScene.FREEZING_RAIN))
        assertEquals(listOf(16, 16, 10), atmosphereParticleLayers(WeatherScene.SNOW))
        assertEquals(listOf(26, 30, 20), atmosphereParticleLayers(WeatherScene.HEAVY_SNOW))
        assertTrue(atmosphereParticleLayers(WeatherScene.CLEAR).isEmpty())
    }

    @Test
    fun particles_groupedFarToNearWithLayerCounts() {
        WeatherScene.entries.forEach { scene ->
            val layerCounts = atmosphereParticleLayers(scene)
            val particles = atmosphereParticles(scene, seed = 7)
            assertEquals(layerCounts.sum(), particles.size)
            val observedLayers = particles.map { it.layer }
            assertEquals(observedLayers.sorted(), observedLayers)
            layerCounts.forEachIndexed { layer, count ->
                assertEquals(count, particles.count { it.layer == layer })
            }
            particles.forEach { particle ->
                assertTrue(particle.x >= 0f && particle.x < 1f)
                assertTrue(particle.y >= 0f && particle.y < 1f)
            }
        }
    }

    @Test
    fun particles_areDeterministicForSeed() {
        assertEquals(
            atmosphereParticles(WeatherScene.RAIN, seed = 42),
            atmosphereParticles(WeatherScene.RAIN, seed = 42),
        )
        assertNotEquals(
            atmosphereParticles(WeatherScene.RAIN, seed = 42),
            atmosphereParticles(WeatherScene.RAIN, seed = 43),
        )
    }

    @Test
    fun particleLayerMultipliers_increaseTowardNearLayer() {
        assertEquals(0.7f, particleLayerSpeedMultiplier(PARTICLE_LAYER_FAR), 1e-5f)
        assertEquals(1.0f, particleLayerSpeedMultiplier(PARTICLE_LAYER_MID), 1e-5f)
        assertEquals(1.4f, particleLayerSpeedMultiplier(PARTICLE_LAYER_NEAR), 1e-5f)
        assertTrue(
            particleLayerSizeMultiplier(PARTICLE_LAYER_FAR) <
                particleLayerSizeMultiplier(PARTICLE_LAYER_MID),
        )
        assertTrue(
            particleLayerSizeMultiplier(PARTICLE_LAYER_MID) <
                particleLayerSizeMultiplier(PARTICLE_LAYER_NEAR),
        )
        assertTrue(
            particleLayerAlphaMultiplier(PARTICLE_LAYER_FAR) <
                particleLayerAlphaMultiplier(PARTICLE_LAYER_MID),
        )
        assertTrue(
            particleLayerAlphaMultiplier(PARTICLE_LAYER_MID) <
                particleLayerAlphaMultiplier(PARTICLE_LAYER_NEAR),
        )
    }
}
