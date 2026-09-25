package com.xiaoxiong.weather.data

/**
 * 天气物理量的可信区间夹紧。
 *
 * JSON 解析层会拒掉 `NaN` / `Infinity` 字面量（`allowSpecialFloatingPointValues`
 * 默认关），但**有限且荒谬**的值照样进得来：上游接口字段错位、单位变更或临时故障时
 * 可能返回 `humidity=5000`、`windSpeed=1e308`、`precipitation=-1`。
 * 这些值会原样上屏（“湿度 5000%”），或参与累加/比较产生荒谬结论。
 *
 * 区间取「宽松但能挡住明显错误」：真实观测极少触及边界，留足余量以免误伤，
 * 同时把越界值夹回边界而不是丢弃——丢成 null 会让 UI 少一块信息，夹回更可解释。
 */
internal object WeatherValueClamp {
    /** 百分比类（湿度/云量/降水概率）：0..100。 */
    fun percent(value: Int?): Int? = value?.coerceIn(0, 100)

    /** 温度：-100..70 °C，覆盖地球极端值并留足余量。 */
    fun temperatureC(value: Double?): Double? = value?.coerceIn(-100.0, 70.0)

    /** 风速/阵风：0..500 km/h，超出飓风量级即视为数据错误。 */
    fun windKmh(value: Double?): Double? = value?.coerceIn(0.0, 500.0)

    /**
     * 降水量：0..3000 mm/期。
     *
     * 上界取在真实记录之上（日降水世界纪录约 1825 mm），只拦荒谬值（1e308、负数）。
     * 夹得过紧会把极端天气的真实数值改小，反而成了数据失真。
     */
    fun precipitationMm(value: Double?): Double? = value?.coerceIn(0.0, 3000.0)

    /** 气压：800..1100 hPa，海平面气压的合理范围。 */
    fun pressureHpa(value: Double?): Double? = value?.coerceIn(800.0, 1100.0)

    /** 能见度：0..100000 m。 */
    fun visibilityMeters(value: Double?): Double? = value?.coerceIn(0.0, 100_000.0)

    /** 风向：0..360 度；越界按模 360 归一（模型与 DTO 都是 Int）。 */
    fun windDirectionDeg(value: Int?): Int? = value?.let { ((it % 360) + 360) % 360 }

    /**
     * 紫外线指数：0..50。
     *
     * 上界取在真实记录之上（世界纪录约 43.3，玻利维亚高原），只拦荒谬值。
     * 之前夹到 20 会把高原/极地的真实强紫外线改小。
     */
    fun uvIndex(value: Double?): Double? = value?.takeIf { it.isFinite() }?.coerceIn(0.0, 50.0)

    /** 累计降水/风力等可能因累加变 inf 的值：非有限一律丢弃。 */
    fun finiteOrNull(value: Double?): Double? = value?.takeIf { it.isFinite() }
}
