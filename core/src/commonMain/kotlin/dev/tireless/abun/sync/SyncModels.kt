package dev.tireless.abun.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PullResponse<T>(
    val items: List<T>,
    @SerialName("next_cursor") val nextCursor: Long,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
data class BatchRequest<T>(
    val items: List<T>,
)

@Serializable
data class SyncTask(
    val id: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("routine_id") val routineId: String? = null,
    val title: String,
    val detail: String? = null,
    @SerialName("start_not_before") val startNotBefore: String? = null,
    @SerialName("end_not_after") val endNotAfter: String? = null,
    @SerialName("estimated_duration") val estimatedDuration: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncRoutine(
    val id: String,
    @SerialName("template_title") val templateTitle: String,
    @SerialName("template_detail") val templateDetail: String? = null,
    @SerialName("recurrence_rule") val recurrenceRule: String,
    @SerialName("default_start_not_before") val defaultStartNotBefore: String? = null,
    @SerialName("default_estimated_duration") val defaultEstimatedDuration: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncAlarm(
    val id: String,
    @SerialName("task_id") val taskId: String,
    @SerialName("trigger_time") val triggerTime: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncTaskEvent(
    val id: String,
    @SerialName("task_id") val taskId: String,
    @SerialName("journal_date") val journalDate: String,
    @SerialName("event_type") val eventType: TaskEventType,
    val content: String? = null,
    val postponed: TaskPostponedPayload? = null,
    @SerialName("event_time") val eventTime: String,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("accepted") val accepted: Boolean? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TaskPostponedPayload(
    @SerialName("previous_start_not_before") val previousStartNotBefore: String? = null,
    @SerialName("new_start_not_before") val newStartNotBefore: String? = null,
    @SerialName("previous_end_not_after") val previousEndNotAfter: String? = null,
    @SerialName("new_end_not_after") val newEndNotAfter: String? = null,
)

@Serializable
data class SyncPomodoroSession(
    val id: String,
    @SerialName("task_id") val taskId: String? = null,
    val phase: PomodoroPhaseWire,
    val state: PomodoroSessionStateWire,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val note: String? = null,
    @SerialName("task_update") val taskUpdate: PomodoroTaskUpdateWire = PomodoroTaskUpdateWire.NONE,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncPreference(
    val key: String,
    val value: String? = null,
    @SerialName("value_type") val valueType: PreferenceValueType,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
enum class TaskEventType {
    CREATED,
    POSTPONED,
    DELETED,
    MISSED,
    SKIPPED,
    MIGRATED,
    PROGRESSED,
    ALARM_FIRED,
    COMPLETED,
    CANCELLED,
}

@Serializable
enum class TaskStatus {
    UNKNOWN,
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

@Serializable
enum class PomodoroPhaseWire {
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK,
}

@Serializable
enum class PomodoroSessionStateWire {
    ACTIVE,
    COMPLETED,
    CANCELLED,
}

@Serializable
enum class PomodoroTaskUpdateWire {
    NONE,
    PROGRESS,
    COMPLETE,
    CANCEL,
}

@Serializable
enum class PreferenceValueType {
    STRING,
    INT,
    ENUM,
}

object TaskStatusDeriver {
    fun fromEvents(events: List<SyncTaskEvent>): TaskStatus {
        val latest = events
            .filterNot(SyncTaskEvent::isDeleted)
            .maxWithOrNull(compareBy<SyncTaskEvent>({ it.eventTime }, { it.createdAt ?: "" }))
            ?: return TaskStatus.UNKNOWN
        return when (latest.eventType) {
            TaskEventType.CREATED,
            TaskEventType.POSTPONED,
            TaskEventType.MIGRATED,
            TaskEventType.ALARM_FIRED,
            -> TaskStatus.PENDING
            TaskEventType.PROGRESSED -> TaskStatus.IN_PROGRESS
            TaskEventType.COMPLETED -> TaskStatus.COMPLETED
            TaskEventType.DELETED,
            TaskEventType.MISSED,
            TaskEventType.SKIPPED,
            TaskEventType.CANCELLED -> TaskStatus.CANCELLED
        }
    }
}

@Serializable
data class SyncFinanceAccount(
    val id: String,
    val path: String,
    val name: String,
    val category: String,
    val currency: String = "CNY",
    @SerialName("include_in_net_worth") val includeInNetWorth: Boolean = true,
    @SerialName("is_pass_through") val isPassThrough: Boolean = false,
    @SerialName("pass_through_expense_account_id") val passThroughExpenseAccountId: String? = null,
    @SerialName("liquid_tier") val liquidTier: String = "Liquid",
    @SerialName("statement_day") val statementDay: Int? = null,
    @SerialName("due_day") val dueDay: Int? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncFinanceExchangeRate(
    val id: String,
    @SerialName("base_currency") val baseCurrency: String,
    @SerialName("quote_currency") val quoteCurrency: String,
    @SerialName("rate_date") val rateDate: String,
    @SerialName("rate_micros") val rateMicros: Long,
    val source: String = "Manual",
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncFinanceClearingGroup(
    val id: String,
    val name: String,
    @SerialName("group_type") val groupType: String,
    @SerialName("anchor_currency") val anchorCurrency: String = "CNY",
    @SerialName("target_account_id") val targetAccountId: String? = null,
    @SerialName("statement_cycle") val statementCycle: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    val status: String = "Active",
    @SerialName("settled_at") val settledAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncFinanceTransaction(
    val id: String,
    @SerialName("tx_date") val txDate: String,
    @SerialName("tx_time") val txTime: String? = null,
    val payee: String? = null,
    val summary: String = "",
    val status: String = "Cleared",
    @SerialName("source_plan_id") val sourcePlanId: String? = null,
    @SerialName("period_index") val periodIndex: Int? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncFinanceSplit(
    val id: String,
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String = "CNY",
    @SerialName("weight_amount_minor") val weightAmountMinor: Long,
    @SerialName("weight_currency") val weightCurrency: String = "CNY",
    val narration: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncFinanceSplitClearingGroup(
    val id: String,
    @SerialName("split_id") val splitId: String,
    @SerialName("clearing_group_id") val clearingGroupId: String,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncFinanceAmortizationPlan(
    val id: String,
    val name: String,
    @SerialName("principal_minor") val principalMinor: Long,
    val currency: String = "CNY",
    @SerialName("total_periods") val totalPeriods: Int,
    @SerialName("repayment_method") val repaymentMethod: String,
    @SerialName("annual_rate_bps") val annualRateBps: Int = 0,
    @SerialName("fee_per_period_minor") val feePerPeriodMinor: Long = 0,
    @SerialName("start_date") val startDate: String,
    @SerialName("repayment_day") val repaymentDay: Int,
    @SerialName("liability_account_id") val liabilityAccountId: String,
    @SerialName("funding_account_id") val fundingAccountId: String? = null,
    @SerialName("interest_account_id") val interestAccountId: String,
    @SerialName("lifecycle_clearing_group_id") val lifecycleClearingGroupId: String,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncFinanceBudgetRule(
    val id: String,
    val name: String,
    @SerialName("scope_type") val scopeType: String,
    @SerialName("account_prefix") val accountPrefix: String = "Expenses:",
    @SerialName("excluded_prefixes_json") val excludedPrefixesJson: String = "[]",
    @SerialName("monthly_limit_minor") val monthlyLimitMinor: Long,
    val currency: String = "CNY",
    @SerialName("include_in_runway") val includeInRunway: Boolean = true,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
