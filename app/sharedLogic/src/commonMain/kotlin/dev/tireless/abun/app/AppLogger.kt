package dev.tireless.abun.app

interface AppLogger {
    fun info(
        message: String,
        context: Map<String, String> = emptyMap(),
    )

    fun error(
        message: String,
        context: Map<String, String> = emptyMap(),
        throwable: Throwable? = null,
    )
}

object DefaultAppLogger : AppLogger {
    override fun info(message: String, context: Map<String, String>) {
        println(formatLine(level = "INFO", message = message, context = context))
    }

    override fun error(message: String, context: Map<String, String>, throwable: Throwable?) {
        println(
            formatLine(
                level = "ERROR",
                message = message,
                context = throwable?.message?.let { context + mapOf("error" to it) } ?: context,
            ),
        )
        throwable?.printStackTrace()
    }

    private fun formatLine(
        level: String,
        message: String,
        context: Map<String, String>,
    ): String {
        val renderedContext = context.entries.joinToString(separator = " ") { (key, value) -> "$key=$value" }
        return if (renderedContext.isBlank()) {
            "[$level] $message"
        } else {
            "[$level] $message $renderedContext"
        }
    }
}
