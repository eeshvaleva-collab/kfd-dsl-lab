package lesson09
import lesson09.support.json.*


@DslMarker
annotation class ResponseDsl

fun validate(response: HttpResponse, block: ResponseBuilder.() -> Unit): ValidationResult {
    val errors = mutableListOf<Error>()
    ResponseBuilder(response, errors).block()
    return ValidationResult(errors)
}

@ResponseDsl
class ResponseBuilder internal constructor(
    private val response: HttpResponse,
    private val errors: MutableList<Error>
) {

    fun status(init: StatusBuilder.() -> Unit) {
        val path = ValidationPath(listOf(PathSegment.Root("status")))
        StatusBuilder(response.status, path, errors).init()
    }

    fun body(init: BodyBuilder.() -> Unit) {
        val path = ValidationPath(listOf(PathSegment.Root("body")))
        BodyBuilder(response.body, path, errors).init()
    }

    fun header(name: String, init: HeaderBuilder.() -> Unit) {
        val path = ValidationPath(listOf(PathSegment.Root("headers"), PathSegment.Property(name)))
        val matchedHeaders = response.headers.filter { it.key.equals(name, ignoreCase = true) }
        val isMissing = matchedHeaders.isEmpty()
        val values = matchedHeaders.flatMap { it.value }

        HeaderBuilder(path, isMissing, values, errors).init()
    }
}

@ResponseDsl
class StatusBuilder internal constructor(
    private val actualStatus: Int,
    private val path: ValidationPath,
    private val errors: MutableList<Error>
) {
    fun equalTo(expected: Int) {
        if (actualStatus != expected) {
            errors.addMismatch(path, expected, actualStatus)
        }
    }
}

@ResponseDsl
class BodyBuilder internal constructor(
    private val body: String?,
    private val path: ValidationPath,
    private val errors: MutableList<Error>
) {
    fun isEmpty() {
        if (!body.isNullOrEmpty()) {
            errors.addMismatch(path, "empty or missing", body)
        }
    }

    fun isNotEmpty() {
        when {
            body == null -> errors.addMissing(path, expected = "not empty")
            body.isEmpty() -> errors.addMismatch(path, expected = "not empty", actual = "empty string")
        }
    }


    fun json(init: JsonObjectBuilder.() -> Unit) {
        val jsonPath = path.append(PathSegment.Property("json"))

        if (body == null) {
            errors.addMissing(jsonPath, "valid json object")
            return
        }

        when (val result = parseJsonObject(body)) {
            is JsonParseResult.Failure -> errors.addParseFailure(jsonPath, "malformed: ${result.reason}")
            is JsonParseResult.Success -> JsonObjectBuilder(result.fields, jsonPath, errors).init()
        }
    }
}

@ResponseDsl
class HeaderBuilder internal constructor(
    private val path: ValidationPath,
    private val isMissing: Boolean,
    private val values: List<String>,
    private val errors: MutableList<Error>

) {
    private fun checkValues(expected: String, match: (List<String>) -> Boolean) {
        when {
            isMissing ->
                errors.addMissing(path, expected)
            values.isEmpty() ->
                errors.addMismatch(path, expected, "[]")
            !match(values) ->
                errors.addMismatch(path, expected, "$values")
        }
    }
    fun equalTo(expected: String) {
        checkValues(expected) { list -> list.any { it == expected } }
    }

    fun startsWith(prefix: String) {
        checkValues("prefix $prefix") { list -> list.any { it.startsWith(prefix) } }
    }

    fun satisfies(description: String, predicate: (String) -> Boolean) {
        checkValues(description) { list -> list.any { predicate(it) } }
    }
}

@ResponseDsl
class JsonObjectBuilder internal constructor(
    private val fields: Map<String, Any?>,
    private val path: ValidationPath,
    private val errors: MutableList<Error>
) {
    fun <T : Any> field(name: String, init: JsonFieldBuilder<T>.() -> Unit) {
        val fieldPath = path.append(PathSegment.Property(name))
        val exists = fields.containsKey(name)
        val value = fields[name]

        JsonFieldBuilder<T>(fieldPath, exists, value, errors).init()
    }
}

@ResponseDsl
class JsonFieldBuilder<T : Any> internal constructor(
    val path: ValidationPath,
    val isPresent: Boolean,
    val actualValue: Any?,
    private val errors: MutableList<Error>
) {
    fun present() {
        if (!isPresent) errors.addMissing(path, "present")
    }

    fun equalTo(expected: T?) {
        when {
            !isPresent ->
                errors.addMissing(path, "equal to $expected")
            actualValue != expected || actualValue?.javaClass != expected?.javaClass ->
                errors.addMismatch(path, expected, actualValue)
        }
    }
}