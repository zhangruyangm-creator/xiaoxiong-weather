package com.xiaoxiong.weather.game

/**
 * 游戏入口提供者：游戏模块通过 Java ServiceLoader 注册自身。
 *
 * 约定：各游戏模块在 `META-INF/services/com.xiaoxiong.weather.game.GameEntryProvider`
 * 中声明 provider 全限定类名，`:app` 通过 [loadGameEntryProviders] 装配游戏列表。
 * 未选中的游戏模块不参与编译（`:app` 动态依赖），列表自然为空——裁剪能力较 `-Pgames`
 * 源码集机制更强（不选中的游戏连字节码都不存在）。
 */
interface GameEntryProvider {
    val entry: GameEntry
}

/** 按 classpath 顺序加载全部已注册的游戏提供者。 */
fun loadGameEntryProviders(): List<GameEntry> =
    buildList {
        val providers = java.util.ServiceLoader.load(GameEntryProvider::class.java)
        for (provider in providers) {
            add(provider.entry)
        }
    }
