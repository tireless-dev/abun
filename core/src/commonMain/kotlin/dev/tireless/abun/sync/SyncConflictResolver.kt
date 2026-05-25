package dev.tireless.abun.sync

object SyncConflictResolver {
    fun shouldAcceptIncoming(incomingHlc: String?, existingHlc: String?): Boolean = when {
        incomingHlc == null -> false
        existingHlc == null -> true
        else -> HlcToken.parse(incomingHlc) > HlcToken.parse(existingHlc)
    }
}
