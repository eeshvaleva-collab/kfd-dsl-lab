# Свой DSL

Создайте DSL для проверки HTTP-ответов и перепишите через него шесть функций `check01`–`check06` из `lesson09.legacy`. Новые проверки должны возвращать список расхождений с ожиданиями.

Полное условие, требования к тестам и необязательное усложнение — в [docs/lesson09.md](docs/lesson09.md).

## Что уже есть в проекте

- `app/src/main/kotlin/lesson09/HttpResponse.kt` — модель ответа.
- `app/src/main/kotlin/lesson09/legacy/Checks.kt` — шесть исходных проверок.
- `app/src/main/kotlin/lesson09/support/json/Json.kt` — готовый парсер JSON. Писать свой парсер не нужно.
- `app/src/test/kotlin/lesson09/LegacySmokeTest.kt` — начальные тесты исходных проверок.
- `app/src/test/kotlin/lesson09/support/json/JsonParserTest.kt` — тесты готового парсера.

Реализацию DSL добавьте в пакет `lesson09.dsl`, свои тесты — в `app/src/test/kotlin/lesson09`. Предоставленные исходники и тесты оставьте без изменений.

## Запуск

Установите JDK 25 и укажите его в `JAVA_HOME`. В IntelliJ IDEA выберите этот же JDK для Gradle JVM. Gradle устанавливать отдельно не нужно: проект использует Wrapper. При первом запуске нужен доступ к интернету для загрузки Gradle и зависимостей.

В проекте используются Kotlin 2.4.10, Gradle 9.5.1 и JUnit 6.1.3. Запускайте команды из корня репозитория:

| Действие | Bash | PowerShell |
|---|---|---|
| Сборка и тесты | `bash ./gradlew build` | `.\gradlew.bat build` |
| Тесты | `bash ./gradlew :app:test` | `.\gradlew.bat :app:test` |

Начальные тесты должны проходить до начала работы. По мере реализации DSL добавляйте собственные тесты из условия задания.

Если продолжаете работу в своём проекте курса, перенесите каталоги `app/src/main/kotlin/lesson09`, `app/src/test/kotlin/lesson09` и файл `docs/lesson09.md`. В модуле должны быть подключены Kotlin/JVM, JUnit Jupiter и `kotlin-test-junit5`, а задача тестирования должна использовать `useJUnitPlatform()`; настройки можно взять из `app/build.gradle.kts` и `gradle/libs.versions.toml`.

## Сдача

Передайте преподавателю ссылку на свой репозиторий. Добавьте в README пример использования DSL, пример расхождений и объяснение решения.


## Пример использования

```kotlin
val result = validate(response) {
    status {
        equalTo(200)
    }
    header("Content-Type") {
        startsWith("application/json")
    }
    body {
        json {
            field("id") {
                equalTo(42L)
            }
            field("name") {
                equalTo("Ivanov")
            }
        }
    }
}
```

## Пример вывода расхождений

Для проваленной проверки ответа со статусом `500`, заголовком `Content-Type: text/plain` и пустым JSON-телом `{}` вывод будет следующим:

```text
status: expected 200, actual 500
headers.Content-Type: expected prefix application/json, actual [text/plain]
body.json.id: expected present, actual <missing>
```

## Архитектура и устройство решения

### 1. Распределение операций между получателями (Receivers)
Все классы-билдеры помечены аннотацией `@ResponseDsl` (с `@DslMarker` внутри) для запрета неявного вызова методов внешних блоков из внутренних:

*   **`ResponseBuilder`**: Корневая точка входа. Управляет общим списком ошибок и предоставляет доступ к вложенным блокам `status`, `body` и `header(...)`
*   **`StatusBuilder`**: Отвечает за проверку равенства статус-кода (`equalTo`)
*   **`HeaderBuilder`**: Валидирует строковые значения заголовков (`equalTo`, `startsWith`) и предоставляет кастомную предикативную проверку через `satisfies(description, predicate)`
*   **`BodyBuilder`**: Проверяет верхнеуровневое состояние тела (`isEmpty`, `isNotEmpty`) и инициирует разбор JSON через блок `json { ... }`
*   **`JsonObjectBuilder`**: Предоставляет доступ к проверке конкретных полей JSON через вызов `field(name) { ... }`
*   **`JsonFieldBuilder<T>`**: Выполняет локальные проверки конкретного поля (`present`, `equalTo`)

### 2. Ключевые архитектурные решения

#### Независимость запусков
При каждом вызове функции `validate` создается новый изолированный накопитель ошибок `MutableList<Error>`
Состояние валидации не хранится внутри билдеров и не переиспользуется

#### Структурное представление путей через `sealed interface`
Вместо ручной склейки строк пути к элементам ответа сделаны в виде `sealed interface PathSegment` (с наследниками `Root` и `Property`) внутри класса `ValidationPath`.
Это гарантирует легкую масштабируемость системы (например, для будущей поддержки индексов массивов в JSON) и выносит форматирование путей через точку строго на слой отображения (`ErrorFormatter.toDslString()`).

#### Разрешение неявных несовпадений числовых типов
Чтобы при математическом равенстве чисел различались их физические типы (например, в JSON пришел `Long 111`, а в тесте проверяется `Int 111`), ыполняется проверка идентичности Java-классов (`actualValue?.javaClass != expected?.javaClass`)
+Если строковые представления совпадают, но значения различаются по типам, `ErrorFormatter` подставляет типы в тексте ошибки

#### Прерывание валидации при битом JSON
При входе в блок `json { ... }` сырая строка тела передается в  `parseJsonObject`. Если парсинг завершается ошибкой (`JsonParseResult.Failure`), в лог пишется только ошибка `Error.ParseFailure` на уровне пути `body.json`
