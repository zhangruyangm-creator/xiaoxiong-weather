package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.game.GameEntryProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.util.ServiceLoader

class BearPlaneEntryProviderTest {
    @Test
    fun provider_isLoadableByServiceLoader_withPublicNoArgConstructor() {
        val providers = ServiceLoader.load(GameEntryProvider::class.java).toList()
        val provider =
            providers.firstOrNull {
                it.javaClass.name == "com.xiaoxiong.weather.ui.game.BearPlaneEntryProvider"
            }
        assertNotNull(provider)
        val ctor = provider!!.javaClass.getConstructor()
        assertTrue(Modifier.isPublic(ctor.modifiers))
        assertSame(BearPlaneEntry, provider.entry)
    }
}
