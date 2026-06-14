package dev.tireless.abun.app

class AuthSessionExpiredException(
    message: String = "Auth session expired",
    cause: Throwable? = null,
) : Exception(message, cause)

class AuthSessionManager(
    private val loginPreferenceStore: LoginPreferenceStore,
    private val authRemoteApi: AuthRemoteApi,
    private val timeProvider: TimeProvider,
) : AccessTokenProvider {
    private var currentSession: AuthSession? = loginPreferenceStore.authSession()

    fun storedSession(): AuthSession? = currentSession ?: loginPreferenceStore.authSession()?.also { currentSession = it }

    suspend fun restoreSession(): AuthSession? {
        val session = storedSession() ?: return null
        val now = timeProvider.nowEpochMillis()
        return when {
            session.accessTokenExpiresAtEpochMillis > now -> session
            session.refreshTokenExpiresAtEpochMillis > now -> refreshSession(session)
            else -> {
                clearSession()
                null
            }
        }
    }

    suspend fun completeLogin(session: AuthSession) {
        persistSession(session)
    }

    suspend fun logout() {
        val session = storedSession()
        if (session != null) {
            runCatching {
                authRemoteApi.logout(
                    refreshToken = session.refreshToken,
                    accessToken = session.accessToken,
                )
            }
        }
        clearSession()
    }

    override suspend fun validAccessToken(forceRefresh: Boolean): String {
        val session = storedSession() ?: throw AuthSessionExpiredException()
        val now = timeProvider.nowEpochMillis()
        val refreshWindowStart = now + ACCESS_TOKEN_REFRESH_WINDOW_MILLIS

        if (!forceRefresh && session.accessTokenExpiresAtEpochMillis > refreshWindowStart) {
            return session.accessToken
        }

        if (session.refreshTokenExpiresAtEpochMillis <= now) {
            clearSession()
            throw AuthSessionExpiredException()
        }

        return refreshSession(session).accessToken
    }

    fun clearSession() {
        currentSession = null
        loginPreferenceStore.clearAuthSession()
    }

    private suspend fun refreshSession(session: AuthSession): AuthSession {
        return runCatching {
            authRemoteApi.refreshSession(session.refreshToken).toAuthSession()
        }.onSuccess { refreshed ->
            persistSession(refreshed)
        }.getOrElse { error ->
            clearSession()
            throw AuthSessionExpiredException(error.message ?: "Auth session expired", error)
        }
    }

    private fun persistSession(session: AuthSession) {
        currentSession = session
        loginPreferenceStore.setAuthSession(session)
    }

    companion object {
        private const val ACCESS_TOKEN_REFRESH_WINDOW_MILLIS = 60_000L
    }
}
