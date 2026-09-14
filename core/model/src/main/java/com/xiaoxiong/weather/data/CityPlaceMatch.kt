package com.xiaoxiong.weather.data

import kotlin.math.abs

/**
 * 坐标级「同城」判定：两轴经纬度差的绝对值都严格小于 [epsilon]。
 *
 * 行为约定（收口自各调用处原有实现）：
 * - [City.latitude]/[City.longitude] 在本模型是非空 `Double`，不存在「缺坐标 -> 按名称/城市 ID 比较」
 *   的分支；上游 DTO 缺坐标时落为默认值 0.0，因此两个 (0,0) 的城市会被判为同城——与替换前一致。
 * - 空 `City?` 由调用方做空判断：历史行为是一侧为 null 即视为不同城，本函数不接收 null。
 * - `abs(NaN - x) < epsilon` 恒为 false，畸形坐标（NaN）自然判为不同城，无需额外分支。
 * - 跨 ±180° 经线（如 179.99 与 -179.99）不做特殊处理：按普通数值差比较会判为不同城。
 */
fun isSamePlace(
    latitudeA: Double,
    longitudeA: Double,
    latitudeB: Double,
    longitudeB: Double,
    epsilon: Double = 0.01,
): Boolean = abs(latitudeA - latitudeB) < epsilon && abs(longitudeA - longitudeB) < epsilon

/** 严格同城：默认 0.01 阈值（约 1km，纬度方向）。行为约定见 [isSamePlace]。 */
fun City.isSamePlace(
    other: City,
    epsilon: Double = 0.01,
): Boolean = isSamePlace(latitude, longitude, other.latitude, other.longitude, epsilon)

/** 搜索/近似同城：默认 0.08 阈值（约 9km，用于地理编码结果去重）。行为约定见 [isSamePlace]。 */
fun City.isNearSamePlace(
    other: City,
    epsilon: Double = 0.08,
): Boolean = isSamePlace(latitude, longitude, other.latitude, other.longitude, epsilon)
