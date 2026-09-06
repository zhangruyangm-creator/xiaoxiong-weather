package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmCropArtTest {
    @Test
    fun fruitMorphs_useDistinctStageArt() {
        val apple = cropById("apple")!!
        val grape = cropById("grape")!!
        val strawberry = cropById("strawberry")!!
        assertNotEquals(FarmCropArt.stage(2, apple), FarmCropArt.stage(2, grape))
        assertNotEquals(FarmCropArt.stage(2, apple), FarmCropArt.stage(2, strawberry))
        assertNotEquals(FarmCropArt.stage(3, grape), FarmCropArt.stage(3, strawberry))
    }

    @Test
    fun flowerMorphs_useDistinctStageArt() {
        val tulip = cropById("tulip")!!
        val rose = cropById("rose")!!
        val wisteria = cropById("wisteria")!!
        assertNotEquals(FarmCropArt.stage(2, tulip), FarmCropArt.stage(2, rose))
        assertNotEquals(FarmCropArt.stage(2, rose), FarmCropArt.stage(2, wisteria))
    }

    @Test
    fun grainMorphs_useDistinctStageArt() {
        val wheat = cropById("wheat")!!
        val rice = cropById("rice")!!
        val corn = cropById("corn")!!
        assertNotEquals(FarmCropArt.stage(2, wheat), FarmCropArt.stage(2, rice))
        assertNotEquals(FarmCropArt.stage(3, rice), FarmCropArt.stage(3, corn))
    }

    @Test
    fun everyCrop_hasStageArtForSproutAndGrow() {
        FARM_CROPS.forEach { crop ->
            assertTrue("${crop.id} sprout", FarmCropArt.stage(2, crop) != null)
            assertTrue("${crop.id} grow", FarmCropArt.stage(3, crop) != null)
        }
    }
}
