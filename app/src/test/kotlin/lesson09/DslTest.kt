package lesson09
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DslTest {
    private fun formatErrors(result: ValidationResult): String {
        return ErrorFormatter.formatAll(result)
    }

    @Test
    fun `test body conditions - missing, empty, blank and present`() {
        val responseNull = HttpResponse(200, emptyMap(), null)
        val resNull = validate(responseNull) { body { isEmpty() } }
        assertTrue(resNull.isValid)

        val resNullNotEmpty = validate(responseNull) { body { isNotEmpty() } }
        assertFalse(resNullNotEmpty.isValid)
        assertEquals("body: expected not empty, actual <missing>", formatErrors(resNullNotEmpty))

        val responseEmpty = HttpResponse(200, emptyMap(), "")
        val resEmpty = validate(responseEmpty) { body { isEmpty() } }
        assertTrue(resEmpty.isValid)

        val resEmptyNotEmpty = validate(responseEmpty) { body { isNotEmpty() } }
        assertFalse(resEmptyNotEmpty.isValid)
        assertEquals("body: expected not empty, actual empty string", formatErrors(resEmptyNotEmpty))

        val responseBlank = HttpResponse(200, emptyMap(), " ")
        val resBlankEmpty = validate(responseBlank) { body { isEmpty() } }
        assertFalse(resBlankEmpty.isValid)
        assertEquals("body: expected empty or missing, actual  ", formatErrors(resBlankEmpty))
    }

    @Test
    fun `test header register and missing cases`() {
        val responseMissing = HttpResponse(200, emptyMap(), null)
        val resMissing = validate(responseMissing) { header("X-Foo-Bar") { equalTo("qwerty123_test") } }
        assertEquals("headers.X-Foo-Bar: expected qwerty123_test, actual <missing>", formatErrors(resMissing))

        val responseEmptyList = HttpResponse(200, mapOf("X-Foo-Bar" to emptyList()), null)
        val resEmptyList = validate(responseEmptyList) { header("X-Foo-Bar") { equalTo("asdfgh") } }
        assertEquals("headers.X-Foo-Bar: expected asdfgh, actual []", formatErrors(resEmptyList))

        val responseMulti = HttpResponse(
            status = 200,
            headers = mapOf(
                "X-My-Header" to listOf("random_stuff_here"),
                "x-my-header" to listOf("zxcvbnm")
            ),
            body = null
        )
        val resMulti = validate(responseMulti) {
            header("X-MY-HEADER") {
                equalTo("random_stuff_here")
                startsWith("zxcv")
            }
        }
        assertTrue(resMulti.isValid)
    }

    @Test
    fun `test json parsing failures and strict type checks`() {
        val responseBadJson = HttpResponse(200, emptyMap(), "{ jshdflgkuaysegfkajshd }")
        val resBadJson = validate(responseBadJson) {
            body {
                json {
                    field("foo") { present() }
                }
            }
        }
        assertFalse(resBadJson.isValid)
        assertTrue(formatErrors(resBadJson).startsWith("body.json: expected valid json object, actual malformed:"))

        val responseFields = HttpResponse(200, emptyMap(), """{"id": null, "temp": "asdf", "val": 999}""")

        val resFields = validate(responseFields) {
            body {
                json {
                    field("xyz_missing") { present() }
                    field("id") { present() }
                    field("temp") { equalTo(111L) }
                    field("val") { equalTo("999") }
                }
            }
        }

        val errors = formatErrors(resFields)
        assertTrue(errors.contains("body.json.xyz_missing: expected present, actual <missing>"))
        assertTrue(errors.contains("body.json.temp: expected 111, actual asdf"))
        assertTrue(errors.contains("body.json.val: expected String 999, actual Long 999"))
    }

    @Test
    fun `test implicit type mismatch reporting for numbers`() {
        val response = HttpResponse(200, emptyMap(), """{"temp": 111}""")

        val result = validate(response) {
            body {
                json {
                    field("temp") { equalTo(111) }
                }
            }
        }

        assertFalse(result.isValid)
        val expectedError = "body.json.temp: expected Integer 111, actual Long 111"
        assertEquals(expectedError, formatErrors(result))
    }

    @Test
    fun `test multiple independent failures and order of errors`() {
        val response = HttpResponse(
            status = 500,
            headers = mapOf("X-Type" to listOf("garbage_value_test")),
            body = "{}"
        )

        val result = validate(response) {
            status { equalTo(200) }
            header("X-Type") { startsWith("expected_prefix_123") }
            body {
                json {
                    field("some_id") { present() }
                }
            }
        }

        val expectedReport = """
            status: expected 200, actual 500
            headers.X-Type: expected prefix expected_prefix_123, actual [garbage_value_test]
            body.json.some_id: expected present, actual <missing>
        """.trimIndent()

        assertEquals(expectedReport, formatErrors(result))
    }

    private fun isTraceValid(value: String): Boolean = value.isNotBlank() && value != "error"

    @Test
    fun `test three forms of user predicate delivery`() {
        val response = HttpResponse(200, mapOf("X-Trace" to listOf("test_token_xyz")), null)

        val res1 = validate(response) {
            header("X-Trace") { satisfies("not empty", { value -> value.isNotEmpty() }) }
        }

        val predicateVar: (String) -> Boolean = { it.endsWith("xyz") }
        val res2 = validate(response) {
            header("X-Trace") { satisfies("ends with xyz", predicateVar) }
        }

        val res3 = validate(response) {
            header("X-Trace") { satisfies("is valid format", ::isTraceValid) }
        }

        assertTrue(res1.isValid)
        assertTrue(res2.isValid)
        assertTrue(res3.isValid)
    }

    @Test
    fun `test header values with spaces and empty strings`() {
        val response = HttpResponse(200, mapOf("X-Spaces" to listOf(" application/json ", "")), null)
        val result = validate(response) {
            header("X-Spaces") {
                equalTo("application/json")
            }
        }
        assertFalse(result.isValid)
        assertTrue(formatErrors(result).contains("actual [ application/json , ]"))
    }

    @Test
    fun `test simultaneous status failure and json parse failure`() {
        val response = HttpResponse(500, emptyMap(), "{ broken json }")
        val result = validate(response) {
            status { equalTo(200) }
            body { json { field("id") { present() } } }
        }
        assertFalse(result.isValid)
        val errors = formatErrors(result)
        assertTrue(errors.startsWith("status: expected 200, actual 500\nbody.json: expected valid json object"))
        assertFalse(errors.contains("body.json.id"))
    }

    @Test
    fun `test duplicate validation operations produce duplicate errors`() {
        val response = HttpResponse(200, emptyMap(), null)
        val result = validate(response) {
            header("X-Missing") {
                equalTo("secret")
                equalTo("secret")
            }
        }
        val errors = formatErrors(result).split("\n")
        assertEquals(2, errors.size)
        assertEquals(errors, errors)
    }

    @Test
    fun `test independent sequential runs with re-used configuration block`() {
        val configBlock: ResponseBuilder.() -> Unit = {
            status { equalTo(200) }
            body { json { field("id") { equalTo(42L) } } }
        }

        val goodResponse1 = HttpResponse(200, emptyMap(), """{"id": 42}""")
        val badResponse = HttpResponse(500, emptyMap(), """{"id": 999}""")
        val goodResponse2 = HttpResponse(200, emptyMap(), """{"id": 42}""")

        val res1 = validate(goodResponse1, configBlock)
        assertTrue(res1.isValid)

        val res2 = validate(badResponse, configBlock)
        assertFalse(res2.isValid)

        val res3 = validate(goodResponse2, configBlock)
        assertTrue(res3.isValid)

        assertTrue(res1.isValid)
        assertFalse(res2.isValid)
        assertTrue(res3.isValid)
    }

    @Test
    fun `test header value casing sensitivity`() {
        val response = HttpResponse(200, mapOf("Content-Type" to listOf("Application/Json")), null)

        val result = validate(response) {
            header("Content-Type") {
                equalTo("application/json")
            }
        }

        assertFalse(result.isValid)
        assertTrue(formatErrors(result).contains("expected application/json, actual [Application/Json]"))
    }
}