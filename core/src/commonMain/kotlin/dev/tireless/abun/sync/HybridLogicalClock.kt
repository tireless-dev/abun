package dev.tireless.abun.sync

class HybridLogicalClock(
    private val nodeId: String,
    private val timeSource: () -> Long,
) {
    private var lastPhysicalMs: Long = 0
    private var lastLogicalCounter: Int = 0

    init {
        require(nodeId.isNotBlank()) { "nodeId must not be blank" }
    }

    fun next(observed: String? = null): String {
        val observedClock = observed?.let(HlcToken::parse)
        val now = timeSource()
        val physical = maxOf(now, lastPhysicalMs, observedClock?.physicalMs ?: 0L)
        val logical = when {
            physical == lastPhysicalMs && physical == observedClock?.physicalMs ->
                maxOf(lastLogicalCounter, observedClock.logicalCounter) + 1

            physical == lastPhysicalMs -> lastLogicalCounter + 1
            physical == observedClock?.physicalMs -> observedClock.logicalCounter + 1
            else -> 0
        }
        lastPhysicalMs = physical
        lastLogicalCounter = logical
        return HlcToken(physical, logical, nodeId).toString()
    }
}

data class HlcToken(
    val physicalMs: Long,
    val logicalCounter: Int,
    val nodeId: String,
) : Comparable<HlcToken> {
    override fun compareTo(other: HlcToken): Int = toString().compareTo(other.toString())

    override fun toString(): String = buildString {
        append(physicalMs.toString().padStart(13, '0'))
        append('-')
        append(logicalCounter.toString().padStart(4, '0'))
        append('-')
        append(nodeId)
    }

    companion object {
        fun parse(value: String): HlcToken {
            val parts = value.split('-', limit = 3)
            require(parts.size == 3) { "Invalid HLC token: $value" }
            require(parts[0].length == 13) { "Invalid physical_ms width: $value" }
            require(parts[1].length >= 4) { "Invalid logical_counter width: $value" }
            require(parts[2].isNotBlank()) { "Invalid node_id: $value" }
            return HlcToken(
                physicalMs = parts[0].toLong(),
                logicalCounter = parts[1].toInt(),
                nodeId = parts[2],
            )
        }
    }
}
