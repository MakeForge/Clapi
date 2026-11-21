package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.ui.graphics.Color
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.theme.DarkAppColors
import io.github.makeforge.clapi.ui.shared.theme.LightAppColors
import kotlin.test.Test
import kotlin.test.assertEquals

class MethodBadgeTest {

    @Test
    fun compactMethodName_returns_POST_not_POS() {
        assertEquals("POST", compactMethodName(HttpMethodType.POST))
    }

    @Test
    fun compactMethodName_other_methods() {
        assertEquals("GET", compactMethodName(HttpMethodType.GET))
        assertEquals("PUT", compactMethodName(HttpMethodType.PUT))
        assertEquals("PATCH", compactMethodName(HttpMethodType.PATCH))
        assertEquals("DEL", compactMethodName(HttpMethodType.DELETE))
        assertEquals("OPT", compactMethodName(HttpMethodType.OPTIONS))
        assertEquals("HEAD", compactMethodName(HttpMethodType.HEAD))
        assertEquals("TRACE", compactMethodName(HttpMethodType.TRACE))
        assertEquals("CONNECT", compactMethodName(HttpMethodType.CONNECT))
    }

    @Test
    fun requestTypeBadgeColors_dark_palette() {
        assertEquals(Color(0xFF22C55E), DarkAppColors.badgeHttp)
        assertEquals(Color(0xFFE10098), DarkAppColors.methodGraphQl)
        assertEquals(Color(0xFF06B6D4), DarkAppColors.badgeGrpc)
        assertEquals(Color(0xFF6366F1), DarkAppColors.methodWebSocket)
        assertEquals(Color(0xFF14B8A6), DarkAppColors.badgeMcp)
    }

    @Test
    fun requestTypeBadgeColors_light_palette() {
        assertEquals(Color(0xFF22C55E), LightAppColors.badgeHttp)
        assertEquals(Color(0xFFE10098), LightAppColors.methodGraphQl)
        assertEquals(Color(0xFF06B6D4), LightAppColors.badgeGrpc)
        assertEquals(Color(0xFF6366F1), LightAppColors.methodWebSocket)
        assertEquals(Color(0xFF14B8A6), LightAppColors.badgeMcp)
    }
}
