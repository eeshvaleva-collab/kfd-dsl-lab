package lesson09
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FuncTest {
    private fun formatErrors(result: ValidationResult): String {
        return ErrorFormatter.formatAll(result)
    }

    @Test
    fun `test compare legacy and dsl check01`() {
        val successResponse = HttpResponse(204, emptyMap(), "")
        assertTrue(lesson09.legacy.check01(successResponse))
        assertTrue(check01(successResponse).isValid)

        val badStatus = HttpResponse(500, emptyMap(), "")
        assertFalse(lesson09.legacy.check01(badStatus))
        assertEquals("status: expected 204, actual 500", formatErrors(check01(badStatus)))

        val badBody = HttpResponse(204, emptyMap(), "content")
        assertFalse(lesson09.legacy.check01(badBody))
        assertEquals("body: expected empty or missing, actual content", formatErrors(check01(badBody)))
    }

    @Test
    fun `test compare legacy and dsl check02`() {
        val successResponse = HttpResponse(200, mapOf("Content-Type" to listOf("application/json;charset=utf-8")), "{}")
        assertTrue(lesson09.legacy.check02(successResponse))
        assertTrue(check02(successResponse).isValid)

        val badStatus = HttpResponse(500, mapOf("Content-Type" to listOf("application/json")), "{}")
        assertFalse(lesson09.legacy.check02(badStatus))
        assertEquals("status: expected 200, actual 500", formatErrors(check02(badStatus)))

        val badHeader = HttpResponse(200, mapOf("Content-Type" to listOf("text/plain")), "{}")
        assertFalse(lesson09.legacy.check02(badHeader))
        assertEquals("headers.Content-Type: expected prefix application/json, actual [text/plain]", formatErrors(check02(badHeader)))

        val badBody = HttpResponse(200, mapOf("Content-Type" to listOf("application/json")), "")
        assertFalse(lesson09.legacy.check02(badBody))
        assertEquals("body: expected not empty, actual empty string", formatErrors(check02(badBody)))
    }

    @Test
    fun `test compare legacy and dsl check03`() {
        val successResponse = HttpResponse(200, mapOf("Accept" to listOf("application/json", "text/html")), null)
        assertTrue(lesson09.legacy.check03(successResponse))
        assertTrue(check03(successResponse).isValid)

        val badStatus = HttpResponse(500, mapOf("Accept" to listOf("application/json", "text/html")), null)
        assertFalse(lesson09.legacy.check03(badStatus))
        assertEquals("status: expected 200, actual 500", formatErrors(check03(badStatus)))

        val missingJson = HttpResponse(200, mapOf("Accept" to listOf("text/html")), null)
        assertFalse(lesson09.legacy.check03(missingJson))
        assertEquals("headers.Accept: expected application/json, actual [text/html]", formatErrors(check03(missingJson)))

        val missingHtml = HttpResponse(200, mapOf("Accept" to listOf("application/json")), null)
        assertFalse(lesson09.legacy.check03(missingHtml))
        assertEquals("headers.Accept: expected text/html, actual [application/json]", formatErrors(check03(missingHtml)))
    }

    @Test
    fun `test compare legacy and dsl check04`() {
        val successResponse = HttpResponse(200, emptyMap(), """{"id": 123, "name": "Ivanov"}""")
        assertTrue(lesson09.legacy.check04(successResponse))
        assertTrue(check04(successResponse).isValid)

        val badStatus = HttpResponse(500, emptyMap(), """{"id": 123, "name": "Ivanov"}""")
        assertFalse(lesson09.legacy.check04(badStatus))
        assertEquals("status: expected 200, actual 500", formatErrors(check04(badStatus)))

        val missingId = HttpResponse(200, emptyMap(), """{"name": "Ivanov"}""")
        assertFalse(lesson09.legacy.check04(missingId))
        assertEquals("body.json.id: expected present, actual <missing>", formatErrors(check04(missingId)))

        val badName = HttpResponse(200, emptyMap(), """{"id": 123, "name": "Petrov"}""")
        assertFalse(lesson09.legacy.check04(badName))
        assertEquals("body.json.name: expected Ivanov, actual Petrov", formatErrors(check04(badName)))
    }

    @Test
    fun `test compare legacy and dsl check05`() {
        val successResponse = HttpResponse(200, emptyMap(), """{"id": 42, "active": true}""")
        assertTrue(lesson09.legacy.check05(successResponse))
        assertTrue(check05(successResponse).isValid)

        val badStatus = HttpResponse(500, emptyMap(), """{"id": 42, "active": true}""")
        assertFalse(lesson09.legacy.check05(badStatus))
        assertFalse(check05(badStatus).isValid)
        assertEquals("status: expected 200, actual 500", formatErrors(check05(badStatus)))

        val badId = HttpResponse(200, emptyMap(), """{"id": 999, "active": true}""")
        assertFalse(lesson09.legacy.check05(badId))
        assertFalse(check05(badId).isValid)
        assertEquals("body.json.id: expected 42, actual 999", formatErrors(check05(badId)))

        val badActive = HttpResponse(200, emptyMap(), """{"id": 42, "active": false}""")
        assertFalse(lesson09.legacy.check05(badActive))
        assertFalse(check05(badActive).isValid)
        assertEquals("body.json.active: expected true, actual false", formatErrors(check05(badActive)))
    }

    @Test
    fun `test compare legacy and dsl check06`() {
        val successResponse = HttpResponse(422, mapOf("Content-Type" to listOf("application/json")), """{"error": "invalid", "retry": false}""")
        assertTrue(lesson09.legacy.check06(successResponse))
        assertTrue(check06(successResponse).isValid)

        val badStatus = HttpResponse(200, mapOf("Content-Type" to listOf("application/json")), """{"error": "invalid", "retry": false}""")
        assertFalse(lesson09.legacy.check06(badStatus))
        assertEquals("status: expected 422, actual 200", formatErrors(check06(badStatus)))

        val badHeader = HttpResponse(422, mapOf("Content-Type" to listOf("text/plain")), """{"error": "invalid", "retry": false}""")
        assertFalse(lesson09.legacy.check06(badHeader))
        assertEquals("headers.Content-Type: expected prefix application/json, actual [text/plain]", formatErrors(check06(badHeader)))

        val badErrorField = HttpResponse(422, mapOf("Content-Type" to listOf("application/json")), """{"error": "valid", "retry": false}""")
        assertFalse(lesson09.legacy.check06(badErrorField))
        assertEquals("body.json.error: expected invalid, actual valid", formatErrors(check06(badErrorField)))

        val badRetryField = HttpResponse(422, mapOf("Content-Type" to listOf("application/json")), """{"error": "invalid", "retry": true}""")
        assertFalse(lesson09.legacy.check06(badRetryField))
        assertEquals("body.json.retry: expected false, actual true", formatErrors(check06(badRetryField)))
    }
}


