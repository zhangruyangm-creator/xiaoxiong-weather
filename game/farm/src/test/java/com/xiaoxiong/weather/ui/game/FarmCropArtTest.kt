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
        val pansy = cropById("pansy")!!
        val blossom = cropById("blossom")!!
        assertNotEquals(FarmCropArt.stage(2, tulip), FarmCropArt.stage(2, rose))
        assertNotEquals(FarmCropArt.stage(2, rose), FarmCropArt.stage(2, wisteria))
        assertNotEquals(FarmCropArt.stage(2, rose), FarmCropArt.stage(2, pansy))
        assertNotEquals(FarmCropArt.stage(2, rose), FarmCropArt.stage(2, blossom))
        assertNotEquals(FarmCropArt.stage(3, pansy), FarmCropArt.stage(3, blossom))
    }

    @Test
    fun fruitTreeMorphs_useDistinctStageArt() {
        val apple = cropById("apple")!!
        val peach = cropById("peach")!!
        val orange = cropById("orange")!!
        val lychee = cropById("lychee")!!
        val banana = cropById("banana")!!
        val grape = cropById("grape")!!
        assertNotEquals(FarmCropArt.stage(3, apple), FarmCropArt.stage(3, peach))
        assertNotEquals(FarmCropArt.stage(3, apple), FarmCropArt.stage(3, orange))
        assertNotEquals(FarmCropArt.stage(3, apple), FarmCropArt.stage(3, lychee))
        assertNotEquals(FarmCropArt.stage(3, apple), FarmCropArt.stage(3, banana))
        assertNotEquals(FarmCropArt.stage(2, peach), FarmCropArt.stage(2, grape))
        assertNotEquals(FarmCropArt.stage(3, banana), FarmCropArt.stage(3, lychee))
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
