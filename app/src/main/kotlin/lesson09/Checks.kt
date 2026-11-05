package lesson09

fun check01(response: HttpResponse): ValidationResult {
    return validate(response) {
        status { equalTo(204) }
        body { isEmpty() }
    }
}

fun check02(response: HttpResponse): ValidationResult {
    return validate(response){
        status { equalTo(200) }
        header("Content-Type") { startsWith("application/json")}
        body { isNotEmpty() }
    }
}

fun check03(response: HttpResponse): ValidationResult {
    return validate(response){
        status { equalTo(200) }
        header("Accept") {
            equalTo("application/json")
            equalTo("text/html")
        }
    }
}

fun check04(response: HttpResponse): ValidationResult {
    return validate(response){
        status { equalTo(200) }
        body {
            json {
                field<Any>("id") { present() }
                field("name") { equalTo("Ivanov") }
            }
        }
    }
}

fun check05(response: HttpResponse): ValidationResult {
    return validate(response) {
        status { equalTo(200) }
        body {
            json {
                field("id") { equalTo(42L )}
                field("active") { equalTo(true) }
            }
        }
    }
}

fun check06(response: HttpResponse): ValidationResult {
    return validate(response) {
        status {
            equalTo(422)
        }
        header("Content-Type") { startsWith("application/json")}
        body{
            json {
                field("error") { equalTo("invalid") }
                field("retry") { equalTo(false) }
            }
        }
    }
}