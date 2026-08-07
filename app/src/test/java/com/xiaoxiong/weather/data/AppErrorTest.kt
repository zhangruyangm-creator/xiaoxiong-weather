package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AppErrorTest {

    @Test
    fun toAppError_preservesHttp() {
        val error = AppError.Http(429).toAppError()
        assertTrue(error is AppError.Http)
        assertEquals(429, (error as AppError.Http).code)
    }

    @Test
    fun toAppError_mapsLegacyHttpMessage() {
        val error = IllegalStateException("HTTP 502").toAppError()
        assertTrue(error is AppError.Http)
        assertEquals(502, (error as AppError.Http).code)
    }

    @Test
    fun toAppError_mapsIoException() {
        val error = IOException("timeout").toAppError()
        assertTrue(error is AppError.NetworkUnavailable)
    }
}
