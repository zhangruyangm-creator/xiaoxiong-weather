package com.xiaoxiong.weather.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 城市列表的容错解码与规范化。
 *
 * 保护的核心不变式：**单条坏数据只丢它自己**。
 * 旧实现整批 `runCatching{ decodeFromString(ListSerializer) }.getOrDefault(emptyList())`，
 * 一条坏数据就让整批变空；收藏的下一次 toggle 以空列表为基准写回，
 * 等于把用户其余收藏永久抹掉——这是本组测试存在的唯一理由。
 */
class CityListPersistenceTest {
    @Serializable
    private data class Item(
        val name: String,
        val lat: Double,
        val lon: Double,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val itemSerializer = Item.serializer()

    private fun city(
        name: String,
        lat: Double,
        lon: Double,
    ) = City(id = name.hashCode().toLong(), name = name, latitude = lat, longitude = lon)

    @Test
    fun oneMalformedEntry_doesNotDiscardSiblings() {
        val raw =
            """[
                {"name":"济南","lat":36.65,"lon":117.12},
                {"name":"BAD_NO_COORDS"},
                {"name":"北京","lat":39.90,"lon":116.41}
            ]""".trimIndent()
        val decoded = decodeCityListTolerant(json, raw, itemSerializer)
        assertEquals("坏条目只应丢自己，其余两条保留", 2, decoded.size)
        assertEquals("济南", decoded[0].name)
        assertEquals("北京", decoded[1].name)
    }

    @Test
    fun topLevelGarbage_returnsEmpty() {
        assertTrue(decodeCityListTolerant(json, "not json at all", itemSerializer).isEmpty())
        assertTrue(decodeCityListTolerant(json, """{"name":"x"}""", itemSerializer).isEmpty())
        assertTrue(decodeCityListTolerant(json, null, itemSerializer).isEmpty())
        assertTrue(decodeCityListTolerant(json, "", itemSerializer).isEmpty())
    }

    @Test
    fun emptyArray_returnsEmpty() {
        assertTrue(decodeCityListTolerant(json, "[]", itemSerializer).isEmpty())
    }

    @Test
    fun isUsableForList_rejectsBlankNameAndBadCoords() {
        assertTrue(city("济南", 36.65, 117.12).isUsableForList())
        assertFalse(city("  ", 36.65, 117.12).isUsableForList())
        assertFalse(city("北京", 91.0, 116.41).isUsableForList())
        assertFalse(city("北京", 39.90, 181.0).isUsableForList())
        assertFalse(city("北京", Double.NaN, 116.41).isUsableForList())
        assertFalse(city("北京", 39.90, Double.POSITIVE_INFINITY).isUsableForList())
    }

    @Test
    fun dedupeKey_treatsSameCityAsOne() {
        // 同一城市不同小数位（约 100m 内）应视为同一条
        assertEquals(
            city("济南", 36.650000, 117.120000).listDedupKey(),
            city("济南市", 36.650050, 117.120020).listDedupKey(),
        )
    }

    @Test
    fun normalize_filtersDeduplicatesAndCaps() {
        val list =
            listOf(
                city("济南", 36.65, 117.12),
                city("济南市", 36.65001, 117.12001), // 同城重复
                city("", 30.0, 120.0), // 非法：空名
                city("坏坐标", 95.0, 120.0), // 非法：纬度越界
                city("北京", 39.90, 116.41),
                city("上海", 31.23, 121.47),
            )
        val normalized = list.normalizeCityList(max = 12)
        assertEquals(3, normalized.size)
        assertEquals("保留首次出现（列表最新在前）", "济南", normalized[0].name)
        assertFalse(normalized.any { it.name.isBlank() })
    }

    @Test
    fun normalize_capsToMax() {
        val many = (1..20).map { city("城市$it", it * 1.0, it * 1.0) }
        assertEquals(5, many.normalizeCityList(max = 5).size)
    }

    @Test
    fun roundTripKeepsAllCities() {
        val original = listOf(city("济南", 36.65, 117.12), city("北京", 39.90, 116.41))
        val encoded =
            json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(Item.serializer()),
                original.map { Item(it.name, it.latitude, it.longitude) },
            )
        val decoded = decodeCityListTolerant(json, encoded, itemSerializer)
        assertEquals(original.size, decoded.size)
        assertEquals("济南", decoded[0].name)
    }
}
