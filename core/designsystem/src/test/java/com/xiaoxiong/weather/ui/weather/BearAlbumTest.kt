package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BearAlbumTest {
    @Test
    fun sections_areNonEmpty() {
        val sections = bearAlbumSections()
        assertTrue(sections.isNotEmpty())
        sections.forEach { section ->
            assertTrue(
                "分组 ${section.titleRes} 不应为空",
                section.drawableRes.isNotEmpty(),
            )
        }
    }

    @Test
    fun drawables_doNotRepeatAcrossSections() {
        val all = bearAlbumSections().flatMap { it.drawableRes }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun total_isClosedUnderPoolDrift() {
        val sections = bearAlbumSections()
        val total = sections.sumOf { it.drawableRes.size }
        assertTrue("当前池应有超过 380 张生活片刻插画，实际 $total", total >= 380)
    }

    @Test
    fun workSection_containsFieldAndOfficeJobs() {
        val works = bearAlbumSections().filter { it.titleRes == R.string.sun_bear_work_morning }
        assertTrue(works.isNotEmpty())
        val total = works.sumOf { it.drawableRes.size }
        assertTrue("工作分组应覆盖 60+ 种工种插画，实际 $total", total >= 60)
    }
}
