package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.game.GameEntryProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ServiceLoader

/**
 * 回归测试：provider 必须是普通 class（public 无参构造器）。
 * 曾误用 Kotlin `object`，ServiceLoader 反射实例化时抛
 * `ServiceConfigurationError: Unable to get public no-arg constructor`，1.6.5 启动即崩。
 */
class BearGameEntryProviderTest {
    @Test
    fun provider_isLoadableByServiceLoader_withPublicNoArgConstructor() {
        val providers = ServiceLoader.load(GameEntryProvider::class.java).toList()
        val provider = providers.single { it.javaClass.name == "com.xiaoxiong.weather.ui.game.BearGameEntryProvider" }

        // 反射层面证明「能拿到 public 无参构造器」——object 类在这里必然失败
        assertNotNull(provider.javaClass.getConstructor())
        assertTrue(provider.javaClass.getConstructor().modifiers and java.lang.reflect.Modifier.PUBLIC != 0)

        // provider 暴露的 entry 与模块内单例一致
        assertSame(BearGameEntry, provider.entry)
    }
}
