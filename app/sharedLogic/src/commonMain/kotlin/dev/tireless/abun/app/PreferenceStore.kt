package dev.tireless.abun.app

import dev.tireless.abun.sync.PreferenceValueType
import dev.tireless.abun.sync.SyncConflictResolver
import dev.tireless.abun.sync.SyncPreference

private const val PREF_TASK_TITLE_PREFIX = "task.title_prefix"
private const val PREF_TASK_DEFAULT_ALARM_LEAD_MINUTES = "task.default_alarm_lead_minutes"
private const val PREF_TASK_BLANK_TITLE_POLICY = "task.blank_title_policy"
private const val PREF_POMODORO_FOCUS_MINUTES = "pomodoro.focus_minutes"
private const val PREF_POMODORO_SHORT_BREAK_MINUTES = "pomodoro.short_break_minutes"
private const val PREF_POMODORO_LONG_BREAK_MINUTES = "pomodoro.long_break_minutes"
private const val PREF_APP_TIMEZONE_OVERRIDE = "app.timezone_override"
private const val PREF_APP_DATE_FORMAT = "app.date_format"
private const val PREF_APP_THEME_PREFERENCE = "app.theme_preference"
const val PREF_APP_ROLLOVER_TIME = "app.rollover_time"
private const val PREF_APP_ENABLED_MODULES = "app.enabled_modules"

class PreferenceStore(private val store: LocalStore) {
    fun preferences(): PreferencesViewState =
        store.queries.selectAllPreferences(::mapPreferenceRow).executeAsList().toPreferencesViewState()

    fun updatePreferences(
        titlePrefix: String,
        defaultAlarmLeadMinutes: Int,
        focusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        timezoneOverride: String,
        dateFormat: DateFormatPreference,
        themePreference: ThemePreference,
        rolloverTime: String,
        enabledModules: Set<AppModule> = preferences().enabledModules,
    ) {
        persistPreferences(
            PreferencesViewState(
                titlePrefix = titlePrefix.trim(),
                defaultAlarmLeadMinutes = defaultAlarmLeadMinutes.coerceIn(0, 24 * 60),
                focusMinutes = focusMinutes.coerceIn(1, 180),
                shortBreakMinutes = shortBreakMinutes.coerceIn(1, 60),
                longBreakMinutes = longBreakMinutes.coerceIn(1, 120),
                timezoneOverride = timezoneOverride.ifBlank { "SYSTEM" },
                dateFormat = dateFormat,
                themePreference = themePreference,
                blankTitlePolicy = BlankTitlePolicy.REJECT_BLANK,
                rolloverTime = rolloverTime,
                enabledModules = enabledModules,
            ),
        )
    }

    fun updateEnabledModules(enabledModules: Set<AppModule>) {
        persistPreferenceEntry(
            PREF_APP_ENABLED_MODULES,
            enabledModules.joinToString(",") { it.name },
            PreferenceValueType.STRING,
        )
        if (AppModule.FINANCE in enabledModules) {
            store.financeAccountStore.ensureDefaultFinanceAccounts()
        }
    }

    fun dirtyPreferences(): List<SyncPreference> =
        store.queries.selectDirtyPreferences(::mapPreferenceRow).executeAsList().map { it.toSyncPreference() }

    fun mergeRemotePreferences(items: List<SyncPreference>, clearAccepted: Boolean = false) {
        store.database.transaction {
            items.forEach { mergeRemotePreference(it, clearAccepted) }
        }
        if (AppModule.FINANCE in preferences().enabledModules) {
            store.financeAccountStore.ensureDefaultFinanceAccounts()
        }
    }

    fun migrateLegacyPreferencesIfNeeded() {
        if (store.queries.selectAllPreferences(::mapPreferenceRow).executeAsList().isEmpty()) {
            migrateLegacyPreferences()
        }
    }

    private fun migrateLegacyPreferences() {
        val legacy = store.queries.selectAppPreferences(::mapAppPreferencesRow).executeAsOneOrNull()?.toViewState() ?: return
        persistPreferences(legacy)
    }

    private fun mergeRemotePreference(remote: SyncPreference, clearAccepted: Boolean) {
        val existing = store.queries.selectPreferenceByKey(remote.key, ::mapPreferenceRow).executeAsOneOrNull()
        if (existing == null) {
            persistPreference(
                preference = remote.toLocalPreference(),
                hlcMap = remote.hlcMap,
                dirtyFields = emptyList(),
                isDirty = false,
            )
            return
        }
        var merged = existing.entity
        val mergedHlc = existing.hlcMap.toMutableMap()
        val stillDirty = existing.dirtyFields.toMutableSet()
        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = existing.hlcMap[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "value" -> merged = merged.copy(value = remote.value, valueType = remote.valueType)
                "delete" -> merged = merged.copy(isDeleted = remote.isDeleted)
            }
            mergedHlc[field] = incomingHlc
        }
        if (clearAccepted) remote.acceptedFields.orEmpty().forEach(stillDirty::remove)
        persistPreference(
            preference = merged.copy(
                serverVersion = remote.serverVersion,
                createdAt = existing.entity.createdAt,
                updatedAt = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: existing.entity.updatedAt,
            ),
            hlcMap = mergedHlc,
            dirtyFields = stillDirty.toList(),
            isDirty = stillDirty.isNotEmpty(),
        )
    }

    private fun persistPreferences(preferences: PreferencesViewState) {
        persistPreferenceEntry(PREF_TASK_TITLE_PREFIX, preferences.titlePrefix.ifBlank { null }, PreferenceValueType.STRING)
        persistPreferenceEntry(PREF_TASK_DEFAULT_ALARM_LEAD_MINUTES, preferences.defaultAlarmLeadMinutes.toString(), PreferenceValueType.INT)
        persistPreferenceEntry(PREF_POMODORO_FOCUS_MINUTES, preferences.focusMinutes.toString(), PreferenceValueType.INT)
        persistPreferenceEntry(PREF_POMODORO_SHORT_BREAK_MINUTES, preferences.shortBreakMinutes.toString(), PreferenceValueType.INT)
        persistPreferenceEntry(PREF_POMODORO_LONG_BREAK_MINUTES, preferences.longBreakMinutes.toString(), PreferenceValueType.INT)
        persistPreferenceEntry(PREF_APP_TIMEZONE_OVERRIDE, preferences.timezoneOverride, PreferenceValueType.STRING)
        persistPreferenceEntry(PREF_APP_DATE_FORMAT, preferences.dateFormat.name, PreferenceValueType.ENUM)
        persistPreferenceEntry(PREF_APP_ROLLOVER_TIME, preferences.rolloverTime, PreferenceValueType.STRING)
        persistPreferenceEntry(PREF_TASK_BLANK_TITLE_POLICY, preferences.blankTitlePolicy.name, PreferenceValueType.ENUM)
        persistPreferenceEntry(PREF_APP_ENABLED_MODULES, preferences.enabledModules.joinToString(",") { it.name }, PreferenceValueType.STRING)
        if (AppModule.FINANCE in preferences.enabledModules) {
            store.financeAccountStore.ensureDefaultFinanceAccounts()
        }
    }

    private fun persistPreferenceEntry(key: String, value: String?, valueType: PreferenceValueType) {
        val existing = store.queries.selectPreferenceByKey(key, ::mapPreferenceRow).executeAsOneOrNull()
        val now = store.timeProvider.nowEpochMillis()
        val existingEntity = existing?.entity
        val valueChanged = if (existingEntity == null) {
            true
        } else {
            existingEntity.value != value || existingEntity.valueType != valueType || existingEntity.isDeleted
        }
        val currentHlc = existing?.hlcMap?.get("value")
        val existingDirty = existing?.dirtyFields.orEmpty()
        val dirtyFields = if (valueChanged) (existingDirty + "value").distinct() else existingDirty
        val existingHlcMap = existing?.hlcMap.orEmpty()
        persistPreference(
            preference = LocalPreference(
                key = key,
                value = value,
                valueType = valueType,
                isDeleted = false,
                serverVersion = existingEntity?.serverVersion ?: 0L,
                createdAt = existingEntity?.createdAt ?: now,
                updatedAt = now,
            ),
            hlcMap = if (valueChanged) (existingHlcMap + ("value" to store.clock.next(currentHlc))) else existingHlcMap,
            dirtyFields = dirtyFields,
            isDirty = valueChanged || existingDirty.isNotEmpty(),
        )
    }

    private fun persistPreference(preference: LocalPreference, hlcMap: Map<String, String>, dirtyFields: List<String>, isDirty: Boolean) {
        store.queries.upsertPreference(
            pref_key = preference.key,
            pref_value = preference.value,
            value_type = preference.valueType.name,
            is_deleted = preference.isDeleted.toLong(),
            hlc_map = JsonCodecs.encodeMap(hlcMap),
            dirty_fields = JsonCodecs.encodeList(dirtyFields),
            is_dirty = isDirty.toLong(),
            server_version = preference.serverVersion,
            created_at = preference.createdAt,
            updated_at = preference.updatedAt,
        )
    }

    internal fun mapAppPreferencesRow(
        id: Long,
        title_prefix: String?,
        default_alarm_lead_minutes: Long,
        default_focus_minutes: Long,
        default_short_break_minutes: Long,
        default_long_break_minutes: Long,
        timezone_override: String,
        date_format: String,
        blank_title_policy: String,
    ): LocalAppPreferences = LocalAppPreferences(
        title_prefix,
        default_alarm_lead_minutes.toInt(),
        default_focus_minutes.toInt(),
        default_short_break_minutes.toInt(),
        default_long_break_minutes.toInt(),
        timezone_override,
        enumValueOf(date_format),
        enumValueOf(blank_title_policy),
    )

    internal fun mapPreferenceRow(
        pref_key: String,
        pref_value: String?,
        value_type: String,
        is_deleted: Long,
        hlc_map: String,
        dirty_fields: String,
        is_dirty: Long,
        server_version: Long,
        created_at: Long,
        updated_at: Long,
    ): MutableSyncRow<LocalPreference> = MutableSyncRow(
        entity = LocalPreference(pref_key, pref_value, enumValueOf(value_type), is_deleted != 0L, server_version, created_at, updated_at),
        hlcMap = JsonCodecs.decodeMap(hlc_map),
        dirtyFields = if (is_dirty != 0L) JsonCodecs.decodeList(dirty_fields) else emptyList(),
    )
}

internal data class LocalAppPreferences(
    val titlePrefix: String?,
    val defaultAlarmLeadMinutes: Int,
    val defaultFocusMinutes: Int,
    val defaultShortBreakMinutes: Int,
    val defaultLongBreakMinutes: Int,
    val timezoneOverride: String,
    val dateFormat: DateFormatPreference,
    val blankTitlePolicy: BlankTitlePolicy,
)

internal data class LocalPreference(
    val key: String,
    val value: String?,
    val valueType: PreferenceValueType,
    val isDeleted: Boolean,
    val serverVersion: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

private fun LocalAppPreferences.toViewState(): PreferencesViewState = PreferencesViewState(
    titlePrefix = titlePrefix.orEmpty(),
    defaultAlarmLeadMinutes = defaultAlarmLeadMinutes,
    focusMinutes = defaultFocusMinutes,
    shortBreakMinutes = defaultShortBreakMinutes,
    longBreakMinutes = defaultLongBreakMinutes,
    timezoneOverride = timezoneOverride,
    dateFormat = dateFormat,
    blankTitlePolicy = blankTitlePolicy,
)

private fun List<MutableSyncRow<LocalPreference>>.toPreferencesViewState(): PreferencesViewState {
    val byKey = associateBy { it.entity.key }
    return PreferencesViewState(
        titlePrefix = byKey[PREF_TASK_TITLE_PREFIX]?.entity?.value.orEmpty(),
        defaultAlarmLeadMinutes = byKey[PREF_TASK_DEFAULT_ALARM_LEAD_MINUTES]?.entity?.value?.toIntOrNull() ?: 15,
        focusMinutes = byKey[PREF_POMODORO_FOCUS_MINUTES]?.entity?.value?.toIntOrNull() ?: 25,
        shortBreakMinutes = byKey[PREF_POMODORO_SHORT_BREAK_MINUTES]?.entity?.value?.toIntOrNull() ?: 5,
        longBreakMinutes = byKey[PREF_POMODORO_LONG_BREAK_MINUTES]?.entity?.value?.toIntOrNull() ?: 15,
        timezoneOverride = byKey[PREF_APP_TIMEZONE_OVERRIDE]?.entity?.value ?: "SYSTEM",
        dateFormat = byKey[PREF_APP_DATE_FORMAT]?.entity?.value?.let(DateFormatPreference::valueOf) ?: DateFormatPreference.ISO,
        themePreference = ThemePreference.SYSTEM,
        blankTitlePolicy = byKey[PREF_TASK_BLANK_TITLE_POLICY]?.entity?.value?.let(BlankTitlePolicy::valueOf) ?: BlankTitlePolicy.REJECT_BLANK,
        rolloverTime = byKey[PREF_APP_ROLLOVER_TIME]?.entity?.value ?: "02:00",
        enabledModules = parseEnabledModules(byKey[PREF_APP_ENABLED_MODULES]?.entity?.value),
    )
}

private fun parseEnabledModules(value: String?): Set<AppModule> {
    if (value == null) return AppModule.entries.toSet()
    if (value.isEmpty()) return emptySet()
    return try {
        value.split(",")
            .mapNotNull { name ->
                val trimmed = name.trim()
                if (trimmed.isEmpty()) null
                else runCatching { AppModule.valueOf(trimmed) }.getOrNull()
            }
            .toSet()
    } catch (_: Exception) {
        AppModule.entries.toSet()
    }
}

private fun Boolean.toLong(): Long = if (this) 1 else 0

private fun MutableSyncRow<LocalPreference>.toSyncPreference(): SyncPreference = SyncPreference(
    key = entity.key,
    value = entity.value,
    valueType = entity.valueType,
    isDeleted = entity.isDeleted,
    hlcMap = hlcMap,
    dirtyFields = dirtyFields,
    serverVersion = entity.serverVersion,
    createdAt = epochMillisToIsoString(entity.createdAt),
)

private fun SyncPreference.toLocalPreference(): LocalPreference = LocalPreference(
    key = key,
    value = value,
    valueType = valueType,
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    createdAt = createdAt?.let(::isoStringToEpochMillis) ?: 0L,
    updatedAt = serverUpdatedAt?.let(::isoStringToEpochMillis) ?: 0L,
)
