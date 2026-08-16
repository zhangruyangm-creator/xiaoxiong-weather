package com.xiaoxiong.weather.game

/**
 * 彩蛋游戏口令契约：搜索框输入即触发对应游戏，枢纽（小熊的家）据此切换游戏。
 *
 * 值集中于此是阶段 5 的解耦产物——游戏模块只依赖 [GameEntry] 接口与口令常量，
 * 不再互相 import 具体实现。
 */
const val EASTER_EGG_BEAR_GAME = "#小熊跳跳"
const val EASTER_EGG_BEAR_STAR = "#小熊摘星"
const val EASTER_EGG_BEAR_MAZE = "#小熊迷宫"
const val EASTER_EGG_BEAR_FARM = "#小熊农场"
const val EASTER_EGG_BEAR_HOME = "#小熊回家"
