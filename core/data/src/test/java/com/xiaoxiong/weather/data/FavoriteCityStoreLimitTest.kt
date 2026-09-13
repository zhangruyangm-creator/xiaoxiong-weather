package com.xiaoxiong.weather.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteCityStoreLimitTest {
    @Test
    fun belowLimit_allowsAdd() {
        assertTrue(FavoriteCityStore.canAddFavorite(currentCount = 0, isAlreadyFavorite = false))
        assertTrue(
            FavoriteCityStore.canAddFavorite(
                currentCount = FavoriteCityStore.MAX_FAVORITES - 1,
                isAlreadyFavorite = false,
            ),
        )
    }

    @Test
    fun atLimit_rejectsAdd() {
        assertFalse(
            FavoriteCityStore.canAddFavorite(
                currentCount = FavoriteCityStore.MAX_FAVORITES,
                isAlreadyFavorite = false,
            ),
        )
    }

    @Test
    fun atLimit_stillAllowsExistingFavorite() {
        assertTrue(
            FavoriteCityStore.canAddFavorite(
                currentCount = FavoriteCityStore.MAX_FAVORITES,
                isAlreadyFavorite = true,
            ),
        )
    }
}
