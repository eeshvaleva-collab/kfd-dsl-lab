package lesson09

sealed interface PathSegment {
    val name: String

    data class Root(override val name: String) : PathSegment
    data class Property(override val name: String) : PathSegment
}

data class ValidationPath(val segments: List<PathSegment>) {
    fun append(segment: PathSegment): ValidationPath = ValidationPath(this.segments + segment)
    fun toDslString(): String = segments.joinToString(".") { it.name }
}

sealed interface Error {
    val path: ValidationPath
    data class ValueMismatch(
        override val path: ValidationPath,
        val expected: Any?,
        val actual: Any?
    ) : Error

    data class MissingProperty(
        override val path: ValidationPath,
        val expected: Any?
    ) : Error

    data class ParseFailure(
        override val path: ValidationPath,
        val errorMessage: String
    ) : Error
}

data class ValidationResult(
    val errors: List<Error>
) {
    val isValid: Boolean get() = errors.isEmpty()

    companion object {
        private val SUCCESS = ValidationResult(emptyList())
        fun success(): ValidationResult = SUCCESS
    }
}

object ErrorFormatter {
    fun format(error: Error): String {
        val pathStr = error.path.toDslString()
        return when (error) {
            is Error.ValueMismatch -> {
                val exp = error.expected
                val act = error.actual

                when {
                    act == null -> "$pathStr: expected $exp, actual <null>"
                    exp == null -> "$pathStr: expected <null>, actual $act"
                    exp.toString() == act.toString() && exp.javaClass != act.javaClass ->
                        "$pathStr: expected ${exp.javaClass.simpleName} $exp, actual ${act.javaClass.simpleName} $act"
                    else -> "$pathStr: expected $exp, actual $act"
                }
            }

            is Error.MissingProperty ->
                "$pathStr: expected ${error.expected}, actual <missing>"

            is Error.ParseFailure ->
                "$pathStr: expected valid json object, actual ${error.errorMessage}"
        }
    }

    fun formatAll(result: ValidationResult): String {
        return result.errors.joinToString("\n") { format(it) }
    }
}

internal fun MutableList<Error>.addMismatch(path: ValidationPath, expected: Any?, actual: Any?) {
    add(Error.ValueMismatch(path, expected, actual))
}

internal fun MutableList<Error>.addMissing(path: ValidationPath, expected: Any?) {
    add(Error.MissingProperty(path, expected))
}

internal fun MutableList<Error>.addParseFailure(path: ValidationPath, message: String) {
    add(Error.ParseFailure(path, message))
}