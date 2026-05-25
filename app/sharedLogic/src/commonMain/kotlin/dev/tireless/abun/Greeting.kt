package dev.tireless.abun

import kotlin.js.JsExport
import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return sayHello(platform.name)
    }
}
