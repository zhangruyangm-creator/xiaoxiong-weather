package com.xiaoxiong.weather.game

/**
 * 彩蛋口令契约：搜索框输入即触发对应内容。
 * 游戏口令触发对应游戏（枢纽「小熊的家」据此切换游戏）；非游戏口令（如相册）由装配方在其上扩展。
 *
 * 值集中于此是阶段 5 的解耦产物——游戏模块只依赖 [GameEntry] 接口与口令常量，
 * 不再互相 import 具体实现。
 */
const val EASTER_EGG_BEAR_GAME = "#小熊跳跳"
const val EASTER_EGG_BEAR_STAR = "#小熊摘星"

/** 别踩白块主口令（2–4 列可设）。 */
const val EASTER_EGG_BEAR_WHITE = "#小熊白块"

/** 旧名「小熊方块」，仅作别名兼容。 */
const val EASTER_EGG_BEAR_TILES = "#小熊方块"

/** @deprecated 旧雨幕玩法口令，仅作别名兼容。 */
const val EASTER_EGG_BEAR_RAIN_LEGACY = "#小熊雨幕"
const val EASTER_EGG_BEAR_MAZE = "#小熊迷宫"
const val EASTER_EGG_BEAR_FARM = "#小熊农场"
const val EASTER_EGG_BEAR_HOME = "#小熊回家"
const val EASTER_EGG_BEAR_HOCKEY = "#小熊冰球"
const val EASTER_EGG_BEAR_POOL = "#小熊台球"
const val EASTER_EGG_BEAR_PLANE = "#小熊战机"
const val EASTER_EGG_BEAR_REVERSI = "#小熊黑白棋"
const val EASTER_EGG_BEAR_GOMOKU = "#小熊五子棋"

/** 插画相册：以全屏页呈现全部生活片刻/节日插画（非游戏）。 */
const val EASTER_EGG_BEAR_ALBUM = "#小熊相册"

/** 从子页（如相册）返回 [EASTER_EGG_BEAR_HOME] 时恢复的房间 id（仅枢纽消费）。 */
const val BEAR_HOME_ROOM_BEDROOM = "bedroom"
