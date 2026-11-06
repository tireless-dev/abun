package dev.tireless.abun.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.FinanceAccountViewItem
import dev.tireless.abun.app.FinanceSplitDraft
import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.FinanceCurrencies
import dev.tireless.abun.finance.TransactionStatus
import dev.tireless.abun.ui.theme.ThemeTokens

enum class FastEntryType {
    EXPENSE,
    INCOME,
    TRANSFER,
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CreateFinanceTransactionSheet(
    state: AppUiState,
    onDismiss: () -> Unit,
    onCreate: (
        txDate: String,
        summary: String,
        payee: String?,
        status: TransactionStatus,
        splits: List<FinanceSplitDraft>,
    ) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp)
                .verticalScroll(rememberScrollState())
                .testTag("create-finance-tx-sheet"),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
        ) {
            CreateFinanceTransactionContent(
                state = state,
                onDismiss = onDismiss,
                onCreate = onCreate,
            )
        }
    }
}

@Composable
fun CreateFinanceTransactionContent(
    state: AppUiState,
    onDismiss: () -> Unit,
    onCreate: (
        txDate: String,
        summary: String,
        payee: String?,
        status: TransactionStatus,
        splits: List<FinanceSplitDraft>,
    ) -> Unit,
) {
    val accounts = state.financeView.accounts
    var txDate by remember { mutableStateOf(state.selectedDate) }
    var payee by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(TransactionStatus.CLEARED) }
    var isMultiSplitMode by remember { mutableStateOf(false) }

    // Fast 2-split state
    var fastType by remember { mutableStateOf(FastEntryType.EXPENSE) }
    var fromAccountId by remember(accounts) {
        mutableStateOf(
            accounts.firstOrNull { it.account.category == AccountCategory.ASSET }?.account?.id
                ?: accounts.firstOrNull()?.account?.id.orEmpty(),
        )
    }
    var toAccountId by remember(accounts) {
        mutableStateOf(
            accounts.firstOrNull { it.account.category == AccountCategory.EXPENSE }?.account?.id
                ?: accounts.firstOrNull()?.account?.id.orEmpty(),
        )
    }
    var fastAmountText by remember { mutableStateOf("") }
    var fastWeightAmountText by remember { mutableStateOf("") }
    var selectedClearingGroupId by remember { mutableStateOf<String?>(null) }

    // Multi-split state
    val multiSplits = remember {
        mutableStateListOf(
            MutableSplitDraft(accountId = fromAccountId, amountText = "", currency = "CNY", narration = ""),
            MutableSplitDraft(accountId = toAccountId, amountText = "", currency = "CNY", narration = ""),
        )
    }

    Text("New Financial Entry", style = ThemeTokens.type.sectionTitle)

    // Mode Toggle: Simple 2-Split vs Multi-Split
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            SegmentedButton(
                selected = !isMultiSplitMode,
                onClick = { isMultiSplitMode = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text("Standard 2-Split")
            }
            SegmentedButton(
                selected = isMultiSplitMode,
                onClick = { isMultiSplitMode = true },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text("Multi-Split")
            }
        }
    }

    // Common Header Fields: Date & Payee & Summary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
    ) {
        OutlinedTextField(
            value = txDate,
            onValueChange = { txDate = it },
            label = { Text("Date (YYYY-MM-DD)") },
            modifier = Modifier.weight(1f).testTag("finance-tx-date-input"),
            singleLine = true,
        )

        OutlinedTextField(
            value = payee,
            onValueChange = { payee = it },
            label = { Text("Payee / Counterparty") },
            modifier = Modifier.weight(1f).testTag("finance-tx-payee-input"),
            singleLine = true,
        )
    }

    OutlinedTextField(
        value = summary,
        onValueChange = { summary = it },
        label = { Text("Summary / Purpose") },
        modifier = Modifier.fillMaxWidth().testTag("finance-tx-summary-input"),
        singleLine = true,
    )

    // Status Selection (Cleared, Draft, Scheduled)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Status:", style = ThemeTokens.type.label)
        TransactionStatus.entries.filterNot { it == TransactionStatus.VOID }.forEach { s ->
            val isSelected = status == s
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                    .background(if (isSelected) ThemeTokens.colors.primary.copy(alpha = 0.15f) else ThemeTokens.colors.surface)
                    .border(
                        1.dp,
                        if (isSelected) ThemeTokens.colors.primary else ThemeTokens.colors.border,
                        RoundedCornerShape(ThemeTokens.radii.smallDp),
                    )
                    .clickable { status = s }
                    .padding(horizontal = ThemeTokens.spacing.smDp, vertical = ThemeTokens.spacing.xsDp),
            ) {
                Text(
                    text = s.name.lowercase().replaceFirstChar(Char::titlecase),
                    style = ThemeTokens.type.label,
                    color = if (isSelected) ThemeTokens.colors.primary else ThemeTokens.colors.textSecondary,
                )
            }
        }
    }

    // Body: Simple 2-Split Mode
    if (!isMultiSplitMode) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            FastEntryType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = fastType == type,
                    onClick = {
                        fastType = type
                        when (type) {
                            FastEntryType.EXPENSE -> {
                                fromAccountId = accounts.firstOrNull { it.account.category == AccountCategory.ASSET }?.account?.id.orEmpty()
                                toAccountId = accounts.firstOrNull { it.account.category == AccountCategory.EXPENSE }?.account?.id.orEmpty()
                            }
                            FastEntryType.INCOME -> {
                                fromAccountId = accounts.firstOrNull { it.account.category == AccountCategory.INCOME }?.account?.id.orEmpty()
                                toAccountId = accounts.firstOrNull { it.account.category == AccountCategory.ASSET }?.account?.id.orEmpty()
                            }
                            FastEntryType.TRANSFER -> {
                                fromAccountId = accounts.firstOrNull { it.account.category == AccountCategory.ASSET }?.account?.id.orEmpty()
                                toAccountId = accounts.firstOrNull { it.account.category == AccountCategory.LIABILITY }?.account?.id.orEmpty()
                            }
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                ) {
                    Text(
                        when (type) {
                            FastEntryType.EXPENSE -> "Expense"
                            FastEntryType.INCOME -> "Income"
                            FastEntryType.TRANSFER -> "Transfer / Repay"
                        },
                    )
                }
            }
        }

        // From and To Account Selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            AccountDropdownField(
                label = when (fastType) {
                    FastEntryType.EXPENSE -> "Pay From (Asset/Card)"
                    FastEntryType.INCOME -> "Income Source"
                    FastEntryType.TRANSFER -> "Source Account"
                },
                selectedAccountId = fromAccountId,
                accounts = accounts,
                onSelectAccount = { fromAccountId = it },
                modifier = Modifier.weight(1f),
            )

            AccountDropdownField(
                label = when (fastType) {
                    FastEntryType.EXPENSE -> "Expense Category"
                    FastEntryType.INCOME -> "Deposit Into"
                    FastEntryType.TRANSFER -> "Destination Account"
                },
                selectedAccountId = toAccountId,
                accounts = accounts,
                onSelectAccount = { toAccountId = it },
                modifier = Modifier.weight(1f),
            )
        }

        val fromAcc = accounts.firstOrNull { it.account.id == fromAccountId }
        val toAcc = accounts.firstOrNull { it.account.id == toAccountId }
        val isCrossCurrency = fromAcc != null && toAcc != null && fromAcc.account.currency != toAcc.account.currency

        OutlinedTextField(
            value = fastAmountText,
            onValueChange = { fastAmountText = it },
            label = { Text("Amount (${fromAcc?.account?.currency ?: "CNY"})") },
            modifier = Modifier.fillMaxWidth().testTag("finance-fast-amount-input"),
            singleLine = true,
        )

        if (isCrossCurrency) {
            OutlinedTextField(
                value = fastWeightAmountText,
                onValueChange = { fastWeightAmountText = it },
                label = { Text("Cost in To-Currency @@ (${toAcc?.account?.currency.orEmpty()})") },
                modifier = Modifier.fillMaxWidth().testTag("finance-fast-weight-input"),
                singleLine = true,
            )
        }

        // Optional Clearing Group Tag
        if (state.financeView.clearingGroups.isNotEmpty()) {
            ClearingGroupDropdownField(
                selectedId = selectedClearingGroupId,
                groups = state.financeView.clearingGroups.map { it.group },
                onSelect = { selectedClearingGroupId = it },
            )
        }
    } else {
        // Multi-Split Mode
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            Text("Transaction Splits (Must balance to zero)", style = ThemeTokens.type.label, color = ThemeTokens.colors.textSecondary)

            multiSplits.forEachIndexed { index, splitDraft ->
                MultiSplitRowEditor(
                    index = index,
                    splitDraft = splitDraft,
                    accounts = accounts,
                    onDelete = {
                        if (multiSplits.size > 2) {
                            multiSplits.removeAt(index)
                        }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        multiSplits.add(
                            MutableSplitDraft(
                                accountId = accounts.firstOrNull()?.account?.id.orEmpty(),
                                amountText = "",
                                currency = "CNY",
                                narration = "",
                            ),
                        )
                    },
                    shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                ) {
                    Icon(imageVector = Lucide.Plus, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Add Split")
                }

                // Auto-Balance Button
                val currentNetMinor = calculateNetMinor(multiSplits)
                if (currentNetMinor != 0L) {
                    OutlinedButton(
                        onClick = {
                            val balanceAmt = -currentNetMinor
                            val defaultAcc = accounts.firstOrNull { it.account.category == AccountCategory.EXPENSE }?.account?.id
                                ?: accounts.firstOrNull()?.account?.id.orEmpty()
                            multiSplits.add(
                                MutableSplitDraft(
                                    accountId = defaultAcc,
                                    amountText = (balanceAmt / 100.0).toString(),
                                    currency = "CNY",
                                    narration = "Auto-balance",
                                ),
                            )
                        },
                        shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                    ) {
                        Text("Auto-Balance (${currentNetMinor / 100.0})")
                    }
                }
            }
        }
    }

    // Submit & Cancel Buttons
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = ThemeTokens.spacing.mdDp),
        horizontalArrangement = Arrangement.End,
    ) {
        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
            modifier = Modifier.padding(end = ThemeTokens.spacing.smDp),
        ) {
            Text("Cancel")
        }

        Button(
            onClick = {
                val splits = if (!isMultiSplitMode) {
                    val fromAcc = accounts.firstOrNull { it.account.id == fromAccountId }
                    val toAcc = accounts.firstOrNull { it.account.id == toAccountId }
                    val currencyFrom = fromAcc?.account?.currency ?: "CNY"
                    val currencyTo = toAcc?.account?.currency ?: currencyFrom
                    val majorAmt = fastAmountText.toDoubleOrNull() ?: 0.0
                    val amountMinor = FinanceCurrencies.toMinorUnits(majorAmt, currencyFrom)
                    val weightMinor = if (fastWeightAmountText.isNotBlank()) {
                        FinanceCurrencies.toMinorUnits(fastWeightAmountText.toDoubleOrNull() ?: majorAmt, currencyTo)
                    } else amountMinor

                    val cgList = selectedClearingGroupId?.let { listOf(it) } ?: emptyList()

                    listOf(
                        FinanceSplitDraft(
                            accountId = fromAccountId,
                            amountMinor = -amountMinor,
                            currency = currencyFrom,
                            weightAmountMinor = -weightMinor,
                            weightCurrency = currencyTo,
                            narration = summary,
                            clearingGroupIds = cgList,
                        ),
                        FinanceSplitDraft(
                            accountId = toAccountId,
                            amountMinor = amountMinor,
                            currency = currencyFrom,
                            weightAmountMinor = weightMinor,
                            weightCurrency = currencyTo,
                            narration = summary,
                            clearingGroupIds = cgList,
                        ),
                    )
                } else {
                    multiSplits.map { draft ->
                        val majorAmt = draft.amountText.toDoubleOrNull() ?: 0.0
                        val minor = FinanceCurrencies.toMinorUnits(majorAmt, draft.currency)
                        FinanceSplitDraft(
                            accountId = draft.accountId,
                            amountMinor = minor,
                            currency = draft.currency,
                            weightAmountMinor = minor,
                            weightCurrency = draft.currency,
                            narration = draft.narration,
                        )
                    }
                }

                onCreate(txDate, summary, payee.takeIf { it.isNotBlank() }, status, splits)
                onDismiss()
            },
            shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ThemeTokens.colors.primary,
                contentColor = Color.White,
            ),
            modifier = Modifier.testTag("finance-tx-submit-btn"),
        ) {
            Text(if (status == TransactionStatus.DRAFT) "Save as Draft" else "Record Entry")
        }
    }
}

private class MutableSplitDraft(
    var accountId: String,
    var amountText: String,
    var currency: String,
    var narration: String,
)

private fun calculateNetMinor(splits: List<MutableSplitDraft>): Long {
    return splits.sumOf { draft ->
        val amt = draft.amountText.toDoubleOrNull() ?: 0.0
        FinanceCurrencies.toMinorUnits(amt, draft.currency)
    }
}

@Composable
private fun MultiSplitRowEditor(
    index: Int,
    splitDraft: MutableSplitDraft,
    accounts: List<FinanceAccountViewItem>,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ThemeTokens.colors.surfaceMuted, RoundedCornerShape(ThemeTokens.radii.smallDp))
            .border(1.dp, ThemeTokens.colors.border, RoundedCornerShape(ThemeTokens.radii.smallDp))
            .padding(ThemeTokens.spacing.smDp),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountDropdownField(
                label = "Account",
                selectedAccountId = splitDraft.accountId,
                accounts = accounts,
                onSelectAccount = { splitDraft.accountId = it },
                modifier = Modifier.weight(1.5f),
            )

            var amt by remember { mutableStateOf(splitDraft.amountText) }
            OutlinedTextField(
                value = amt,
                onValueChange = {
                    amt = it
                    splitDraft.amountText = it
                },
                label = { Text("Amount") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )

            IconButton(onClick = onDelete) {
                Icon(imageVector = Lucide.Trash2, contentDescription = "Delete split", tint = ThemeTokens.colors.textTertiary)
            }
        }

        var narr by remember { mutableStateOf(splitDraft.narration) }
        OutlinedTextField(
            value = narr,
            onValueChange = {
                narr = it
                splitDraft.narration = it
            },
            label = { Text("Split Narration (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
private fun AccountDropdownField(
    label: String,
    selectedAccountId: String,
    accounts: List<FinanceAccountViewItem>,
    onSelectAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentAccount = accounts.firstOrNull { it.account.id == selectedAccountId }
    val displayText = currentAccount?.let { "${it.account.name} (${it.account.path})" } ?: "Select Account"

    Box(modifier = modifier) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = Lucide.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.clickable { expanded = true },
                )
            },
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            singleLine = true,
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            accounts.forEach { accItem ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(accItem.account.name, style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Medium))
                            Text(accItem.account.path, style = ThemeTokens.type.label, color = ThemeTokens.colors.textSecondary)
                        }
                    },
                    onClick = {
                        onSelectAccount(accItem.account.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ClearingGroupDropdownField(
    selectedId: String?,
    groups: List<dev.tireless.abun.finance.FinanceClearingGroup>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = groups.firstOrNull { it.id == selectedId }
    val displayText = current?.name ?: "None (Unlinked)"

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Clearing Group Tag (Optional)") },
            trailingIcon = {
                Icon(
                    imageVector = Lucide.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.clickable { expanded = true },
                )
            },
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            singleLine = true,
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("None (Unlinked)") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            groups.forEach { cg ->
                DropdownMenuItem(
                    text = { Text(cg.name) },
                    onClick = {
                        onSelect(cg.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
