package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LifeIndexGeneratorTest {

    @Test
    fun comfortBand_coolMildHotAndMuggy() {
        assertEquals(
            LifeIndexGenerator.ComfortBand.COOL,
            LifeIndexGenerator.comfortBand(15.0, 50)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.DAMP_COLD,
            LifeIndexGenerator.comfortBand(15.0, 90)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.DAMP_COLD,
            LifeIndexGenerator.comfortBand(8.0, 80)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.MILD,
            LifeIndexGenerator.comfortBand(22.0, 60)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.DRY,
            LifeIndexGenerator.comfortBand(22.0, 20)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.VERY_DRY,
            LifeIndexGenerator.comfortBand(22.0, 12)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.MILD,
            LifeIndexGenerator.comfortBand(22.0, 30)
        )
        // 体感不高、湿度很高：闷湿，不算闷热
        assertEquals(
            LifeIndexGenerator.ComfortBand.HUMID,
            LifeIndexGenerator.comfortBand(23.5, 98)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.MUGGY,
            LifeIndexGenerator.comfortBand(28.0, 80)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.SCORCHING,
            LifeIndexGenerator.comfortBand(36.0, 40)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.SCORCHING,
            LifeIndexGenerator.comfortBand(41.0, 40)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.EXTREME_HEAT,
            LifeIndexGenerator.comfortBand(46.0, 40)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.FREEZING,
            LifeIndexGenerator.comfortBand(-3.0, 40)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.FREEZING,
            LifeIndexGenerator.comfortBand(-12.0, 40)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.BITTER,
            LifeIndexGenerator.comfortBand(-16.0, 40)
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.EXTREME_COLD,
            LifeIndexGenerator.comfortBand(-32.0, 40)
        )
    }

    @Test
    fun umbrellaLevel_bands() {
        assertEquals(
            LifeIndexLevel.GOOD,
            LifeIndexGenerator.umbrellaLevel(
                rainingNow = false,
                weatherCode = 0,
                upcomingPrecipSoon = false
            )
        )
        assertEquals(
            LifeIndexLevel.FAIR,
            LifeIndexGenerator.umbrellaLevel(
                rainingNow = false,
                weatherCode = 0,
                upcomingPrecipSoon = true
            )
        )
        assertEquals(
            LifeIndexLevel.POOR,
            LifeIndexGenerator.umbrellaLevel(
                rainingNow = true,
                weatherCode = 61,
                upcomingPrecipSoon = false
            )
        )
        assertEquals(
            LifeIndexLevel.POOR,
            LifeIndexGenerator.umbrellaLevel(
                rainingNow = false,
                weatherCode = 71,
                upcomingPrecipSoon = false
            )
        )
    }

    @Test
    fun carWashLevel_bands() {
        assertEquals(
            LifeIndexLevel.GOOD,
            LifeIndexGenerator.carWashLevel(
                rainingNow = false,
                maxPrecipProb = 10,
                precipSum = 0.0,
                windKmh = 12.0
            )
        )
        assertEquals(
            LifeIndexLevel.FAIR,
            LifeIndexGenerator.carWashLevel(
                rainingNow = false,
                maxPrecipProb = 40,
                precipSum = 0.0,
                windKmh = 12.0
            )
        )
        assertEquals(
            LifeIndexLevel.POOR,
            LifeIndexGenerator.carWashLevel(
                rainingNow = true,
                maxPrecipProb = 10,
                precipSum = 0.0,
                windKmh = 12.0
            )
        )
    }
}
