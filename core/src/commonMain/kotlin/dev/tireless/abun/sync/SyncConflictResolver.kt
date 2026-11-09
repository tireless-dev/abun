package dev.tireless.abun.sync

object SyncConflictResolver {
    fun shouldAcceptIncoming(incomingHlc: String?, existingHlc: String?): Boolean {
        if (incomingHlc == null) return false
        if (existingHlc == null) return true
        val inc = HlcToken.parseOrNull(incomingHlc)
        val ext = HlcToken.parseOrNull(existingHlc)
        return when {
            inc != null && ext != null -> inc > ext
            inc != null -> true
            ext != null -> false
            else -> incomingHlc > existingHlc
        }
    }
}
