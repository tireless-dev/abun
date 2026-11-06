package dev.tireless.abun.finance

import kotlin.math.pow
import kotlin.math.roundToLong

sealed class TransactionValidationResult {
    data object Valid : TransactionValidationResult()
    data class Invalid(val reason: String) : TransactionValidationResult()
}

data class ClearingGroupEvaluation(
    val realizedBalance: Long,
    val projectedBalance: Long,
    val derivedStatus: ClearingGroupStatus,
    val clearedSplitCount: Int,
)

object FinanceCurrencies {
    fun minorUnitScale(currency: String): Int {
        return when (currency.uppercase()) {
            "JPY", "KRW", "VND", "CLP", "PYG" -> 0
            "BHD", "JOD", "KWD", "OMR", "TND" -> 3
            else -> 2 // Default for CNY, USD, EUR, HKD, GBP, etc.
        }
    }

    fun toMinorUnits(majorAmount: Double, currency: String): Long {
        val scale = minorUnitScale(currency)
        val factor = 10.0.pow(scale)
        return (majorAmount * factor).roundToLong()
    }

    fun toMajorUnits(minorUnits: Long, currency: String): Double {
        val scale = minorUnitScale(currency)
        val factor = 10.0.pow(scale)
        return minorUnits / factor
    }

    fun format(minorUnits: Long, currency: String, includeCurrencyCode: Boolean = false): String {
        val scale = minorUnitScale(currency)
        val isNegative = minorUnits < 0
        val absValue = if (minorUnits < 0) -minorUnits else minorUnits
        val factor = 10.0.pow(scale).roundToLong()
        val integerPart = if (scale > 0) absValue / factor else absValue
        val fractionPart = if (scale > 0) absValue % factor else 0L

        val symbol = when (currency.uppercase()) {
            "CNY" -> "¥"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            "HKD" -> "HK$"
            else -> ""
        }

        val intStr = integerPart.toString().reversed().chunked(3).joinToString(",").reversed()
        val formattedNumber = if (scale > 0) {
            val fracStr = fractionPart.toString().padStart(scale, '0')
            "$intStr.$fracStr"
        } else {
            intStr
        }

        val sign = if (isNegative) "-" else ""
        return when {
            includeCurrencyCode && symbol.isNotEmpty() -> "$sign$symbol$formattedNumber $currency"
            includeCurrencyCode -> "$sign$formattedNumber $currency"
            symbol.isNotEmpty() -> "$sign$symbol$formattedNumber"
            else -> "$sign$formattedNumber $currency"
        }
    }
}

object FinanceValidators {
    fun validateTransactionSplits(
        transaction: FinanceTransaction,
        splits: List<FinanceSplit>,
    ): TransactionValidationResult {
        if (transaction.isDeleted) return TransactionValidationResult.Valid

        val activeSplits = splits.filterNot { it.isDeleted }

        // Draft and Void transactions can have 0, 1, or unbalanced splits
        if (transaction.status == TransactionStatus.DRAFT || transaction.status == TransactionStatus.VOID) {
            return TransactionValidationResult.Valid
        }

        // Cleared and Scheduled transactions must be committed double-entry entries
        if (activeSplits.size < 2) {
            return TransactionValidationResult.Invalid(
                "Committed transaction must contain at least 2 active splits (found ${activeSplits.size})",
            )
        }

        // Sign consistency: amount_minor and weight_amount_minor must have the same sign
        for (split in activeSplits) {
            val amountSign = split.amountMinor.compareTo(0L)
            val weightSign = split.weightAmountMinor.compareTo(0L)
            if (amountSign != weightSign) {
                return TransactionValidationResult.Invalid(
                    "Split ${split.id} amount sign ($amountSign) does not match weight sign ($weightSign)",
                )
            }
        }

        // Group active splits by weight_currency and verify zero sum
        val splitsByWeightCurrency = activeSplits.groupBy { it.weightCurrency.uppercase() }
        for ((currency, currencySplits) in splitsByWeightCurrency) {
            val sum = currencySplits.sumOf { it.weightAmountMinor }
            if (sum != 0L) {
                return TransactionValidationResult.Invalid(
                    "Splits do not balance to zero for weight currency $currency (sum: $sum minor units)",
                )
            }
        }

        return TransactionValidationResult.Valid
    }
}

object ClearingGroupEvaluator {
    fun effectiveSplitAmount(split: FinanceSplit, anchorCurrency: String): Long {
        val normalizedAnchor = anchorCurrency.uppercase()
        return when {
            split.currency.uppercase() == normalizedAnchor -> split.amountMinor
            split.weightCurrency.uppercase() == normalizedAnchor -> split.weightAmountMinor
            else -> 0L
        }
    }

    fun evaluate(
        group: FinanceClearingGroup,
        splits: List<FinanceSplit>,
        transactionsById: Map<String, FinanceTransaction>,
    ): ClearingGroupEvaluation {
        val activeSplits = splits.filterNot { it.isDeleted }
        val clearedSplits = activeSplits.filter {
            transactionsById[it.transactionId]?.status == TransactionStatus.CLEARED
        }
        val projectedSplits = activeSplits.filter {
            val status = transactionsById[it.transactionId]?.status
            status == TransactionStatus.CLEARED || status == TransactionStatus.SCHEDULED
        }

        val realizedBalance = clearedSplits.sumOf { effectiveSplitAmount(it, group.anchorCurrency) }
        val projectedBalance = projectedSplits.sumOf { effectiveSplitAmount(it, group.anchorCurrency) }
        val clearedCount = clearedSplits.size

        val derivedStatus = when {
            clearedCount >= 2 && realizedBalance == 0L -> ClearingGroupStatus.SETTLED
            group.groupType == ClearingGroupType.LOAN_LIFECYCLE && realizedBalance > 0L -> ClearingGroupStatus.OVERDRAWN
            else -> ClearingGroupStatus.ACTIVE
        }

        return ClearingGroupEvaluation(
            realizedBalance = realizedBalance,
            projectedBalance = projectedBalance,
            derivedStatus = derivedStatus,
            clearedSplitCount = clearedCount,
        )
    }
}
