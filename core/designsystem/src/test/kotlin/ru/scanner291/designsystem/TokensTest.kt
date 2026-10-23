package ru.scanner291.designsystem

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Код совпадает с дизайн-системой пользователя: каждый цвет, отступ, радиус, размер и стиль текста
 * из tokens.json (копия в test/resources) сверяется со значением в Kotlin.
 */
class TokensTest {
    private val json: JsonObject = Json.parseToJsonElement(
        javaClass.getResource("/design-system/tokens.json")!!.readText(),
    ).jsonObject

    private fun parse(v: String): Color {
        val s = v.trim()
        if (s.startsWith("#")) return Color(0xFF000000 or s.drop(1).toLong(16))
        val parts = s.removePrefix("rgba(").removeSuffix(")").split(",").map { it.trim() }
        return Color(parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), (parts[3].toFloat() * 255).roundToInt())
    }

    private fun assertSameColor(token: String, theme: String, expected: Color, actual: Color) {
        val tol = 1.5f / 255f
        val ok = abs(expected.red - actual.red) <= tol && abs(expected.green - actual.green) <= tol &&
            abs(expected.blue - actual.blue) <= tol && abs(expected.alpha - actual.alpha) <= tol
        assertTrue("$token ($theme): ожидалось $expected, в коде $actual", ok)
    }

    @Test fun everyColorTokenMatches() {
        val tokens = json["color"]!!.jsonObject["tokens"]!!.jsonArray
        val dark = DarkColors.byToken()
        val light = LightColors.byToken()
        assertEquals("число цветовых токенов", tokens.size, dark.size)
        tokens.forEach { t ->
            val o = t.jsonObject
            val name = o["name"]!!.jsonPrimitive.content
            val v = o["value"]!!.jsonObject
            assertTrue("токен $name отсутствует в коде", name in dark)
            assertSameColor(name, "dark", parse(v["dark"]!!.jsonPrimitive.content), dark.getValue(name))
            assertSameColor(name, "light", parse(v["light"]!!.jsonPrimitive.content), light.getValue(name))
        }
    }

    private fun px(section: String): Map<String, Float> = json[section]!!.jsonObject["tokens"]!!.jsonArray.associate {
        val o = it.jsonObject
        o["name"]!!.jsonPrimitive.content to o["value"]!!.jsonPrimitive.content.removeSuffix("px").toFloat()
    }

    @Test fun spacingRadiusAndSizes() {
        val sp = px("spacing")
        listOf("space-1" to Space.s1, "space-2" to Space.s2, "space-3" to Space.s3, "space-4" to Space.s4,
            "space-5" to Space.s5, "space-6" to Space.s6, "space-8" to Space.s8, "space-10" to Space.s10,
        ).forEach { (n, d) -> assertEquals(n, sp.getValue(n), d.value, 0f) }
        val r = px("radius")
        listOf("radius-xs" to Radius.xs, "radius-sm" to Radius.sm, "radius-md" to Radius.md, "radius-lg" to Radius.lg,
            "radius-xl" to Radius.xl, "radius-full" to Radius.full,
        ).forEach { (n, d) -> assertEquals(n, r.getValue(n), d.value, 0f) }
        val s = px("size")
        listOf("size-touch" to Size.touch, "size-icon" to Size.icon, "size-icon-lg" to Size.iconLg, "size-nav" to Size.nav,
            "size-shutter" to Size.shutter, "size-row-thumb" to Size.rowThumb,
        ).forEach { (n, d) -> assertEquals(n, s.getValue(n), d.value, 0f) }
        val op = json["opacity"]!!.jsonObject["tokens"]!!.jsonArray.associate {
            it.jsonObject["name"]!!.jsonPrimitive.content to it.jsonObject["value"]!!.jsonPrimitive.content.toFloat()
        }
        assertEquals(op.getValue("opacity-watermark"), OPACITY_WATERMARK, 0f)
        assertEquals(op.getValue("opacity-disabled"), OPACITY_DISABLED, 0f)
    }

    @Test fun typographyScale() {
        val styles = json["type"]!!.jsonObject["groups"]!!.jsonArray.flatMap { it.jsonObject["styles"]!!.jsonArray }
        val code = mapOf(
            "display" to Type.display, "headline" to Type.headline, "title-lg" to Type.titleLg, "title-md" to Type.titleMd,
            "title-sm" to Type.titleSm, "body-lg" to Type.bodyLg, "body" to Type.body, "ocr" to Type.ocr, "label" to Type.label,
            "caption" to Type.caption, "caption-sm" to Type.captionSm,
        )
        assertEquals(styles.size, code.size)
        styles.forEach {
            val o = it.jsonObject
            val name = o["name"]!!.jsonPrimitive.content
            val st = code.getValue(name)
            assertEquals("$name size", o["fontSize"]!!.jsonPrimitive.content.removeSuffix("px").toFloat(), st.fontSize.value, 0f)
            assertEquals("$name line", o["lineHeight"]!!.jsonPrimitive.content.removeSuffix("px").toFloat(), st.lineHeight.value, 0f)
            assertEquals("$name weight", o["fontWeight"]!!.jsonPrimitive.content.toInt(), st.fontWeight!!.weight)
        }
    }
}
