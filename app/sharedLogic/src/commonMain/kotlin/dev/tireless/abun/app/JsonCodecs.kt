package dev.tireless.abun.app

import dev.tireless.abun.sync.TaskPostponedPayload
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal object JsonCodecs {
    private val stringList = ListSerializer(String.serializer())
    private val stringMap = MapSerializer(String.serializer(), String.serializer())

    fun encodeList(values: List<String>): String = AppJson.encodeToString(stringList, values)

    fun decodeList(value: String): List<String> = runCatching {
        AppJson.decodeFromString(stringList, value)
    }.getOrDefault(emptyList())

    fun encodeMap(values: Map<String, String>): String = AppJson.encodeToString(stringMap, values)

    fun decodeMap(value: String): Map<String, String> = runCatching {
        AppJson.decodeFromString(stringMap, value)
    }.getOrDefault(emptyMap())

    fun encodePostponedPayload(value: TaskPostponedPayload): String = AppJson.encodeToString(value)

    fun decodePostponedPayload(value: String?): TaskPostponedPayload? = value?.let {
        runCatching { AppJson.decodeFromString<TaskPostponedPayload>(it) }.getOrNull()
    }
}
