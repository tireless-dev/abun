package dev.tireless.abun.app

class AuthSessionExpiredException(
    message: String = "Auth session expired",
    cause: Throwable? = null,
) : Exception(message, cause)

class AuthSessionManager(
    private val loginPreferenceStore: LoginPreferenceStore,
    private val authRemoteApi: AuthRemoteApi,
    private val timeProvider: TimeProvider,
    private val logger: AppLogger,
) : AccessTokenProvider {
    private var currentSession: AuthSession? = loginPreferenceStore.authSession()

    fun storedSession(): AuthSession? {
        currentSession?.let { session ->
            if (session.isLegacyDebugSession()) {
                logger.info(message = "auth.legacy_debug_session.cleared")
                clearSession()
                return null
            }
            return session
        }
        val persisted = loginPreferenceStore.authSession() ?: return null
        if (persisted.isLegacyDebugSession()) {
            logger.info(message = "auth.legacy_debug_session.cleared")
            clearSession()
            return null
        }
        currentSession = persisted
        return persisted
    }

    suspend fun restoreSession(): AuthSession? {
        val session = storedSession() ?: return null
        val now = timeProvider.nowEpochMillis()
        logger.info(
            message = "auth.restore.started",
            context = mapOf("userId" to session.userId),
        )
        return when {
            session.accessTokenExpiresAtEpochMillis > now -> session
            session.refreshTokenExpiresAtEpochMillis > now -> refreshSession(session)
            else -> {
                logger.info(
                    message = "auth.restore.expired",
                    context = mapOf("userId" to session.userId),
                )
                clearSession()
                null
            }
        }
    }

    suspend fun completeLogin(session: AuthSession) {
        persistSession(session)
        logger.info(
            message = "auth.login.completed",
            context = mapOf("userId" to session.userId),
        )
    }

    suspend fun logout() {
        val session = storedSession()
        if (session != null) {
            logger.info(
                message = "auth.logout.started",
                context = mapOf("userId" to session.userId),
            )
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
            logger.info(
                message = "auth.access.expired",
                context = mapOf("userId" to session.userId),
            )
            clearSession()
            throw AuthSessionExpiredException()
        }

        logger.info(
            message = "auth.refresh.required",
            context = mapOf(
                "userId" to session.userId,
                "forceRefresh" to forceRefresh.toString(),
            ),
        )
        return refreshSession(session).accessToken
    }

    fun clearSession() {
        currentSession = null
        loginPreferenceStore.clearAuthSession()
        logger.info(message = "auth.session.cleared")
    }

    private suspend fun refreshSession(session: AuthSession): AuthSession {
        return runCatching {
            authRemoteApi.refreshSession(session.refreshToken).toAuthSession()
        }.onSuccess { refreshed ->
            persistSession(refreshed)
            logger.info(
                message = "auth.refresh.completed",
                context = mapOf("userId" to refreshed.userId),
            )
        }.onFailure { error ->
            logger.error(
                message = "auth.refresh.failed",
                context = mapOf("userId" to session.userId),
                throwable = error,
            )
        }.getOrElse { error ->
            clearSession()
            throw AuthSessionExpiredException(error.toReadableAuthMessage(), error)
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

private fun AuthSession.isLegacyDebugSession(): Boolean =
    accessToken == "debug-access-token" ||
        refreshToken == "debug-refresh-token" ||
        userId == "debug-user"

private fun Throwable.toReadableAuthMessage(): String = when (this) {
    is RemoteApiException -> "Your session expired. Please log in again."
    else -> message ?: "Your session expired. Please log in again."
}
