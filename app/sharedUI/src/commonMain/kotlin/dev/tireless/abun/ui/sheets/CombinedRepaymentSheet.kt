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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Lucide
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.ClearingGroupStatus
import dev.tireless.abun.finance.ClearingGroupType
import dev.tireless.abun.finance.FinanceCurrencies
import dev.tireless.abun.finance.TransactionStatus
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CombinedRepaymentSheet(
    state: AppUiState,
    initialClearingGroupId: String? = null,
    onDismiss: () -> Unit,
    onRepayStatement: (clearingGroupId: String, fundingAccountId: String, date: String) -> Unit,
    onRepayLoanTranche: (transactionId: String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        CombinedRepaymentSheetContent(
            state = state,
            initialClearingGroupId = initialClearingGroupId,
            onDismiss = onDismiss,
            onRepayStatement = onRepayStatement,
            onRepayLoanTranche = onRepayLoanTranche,
        )
    }
}

@Composable
fun CombinedRepaymentSheetContent(
    state: AppUiState,
    initialClearingGroupId: String? = null,
    onDismiss: () -> Unit,
    onRepayStatement: (clearingGroupId: String, fundingAccountId: String, date: String) -> Unit,
    onRepayLoanTranche: (transactionId: String) -> Unit,
) {
    val finance = state.financeView
    val liquidAccounts = finance.accounts.filter {
        it.account.category == AccountCategory.ASSET && !it.account.isPassThrough
    }
    var fundingAccountId by remember {
        mutableStateOf(liquidAccounts.firstOrNull()?.account?.id.orEmpty())
    }
    var fundingAccountMenuExpanded by remember { mutableStateOf(false) }
    var repaymentDate by remember { mutableStateOf(state.selectedDate) }

    // Collect statements with outstanding debt
    val unpaidStatements = finance.clearingGroups.filter {
        it.group.groupType == ClearingGroupType.CREDIT_STATEMENT &&
            it.group.status == ClearingGroupStatus.ACTIVE &&
            it.evaluation.realizedBalance < 0L
    }

    // Collect upcoming scheduled loan tranches
    val scheduledSource = finance.scheduledTransactions.ifEmpty { finance.transactions }
    val scheduledLoanTxs = scheduledSource.filter {
        it.transaction.status == TransactionStatus.SCHEDULED && it.transaction.sourcePlanId != null
    }

    // Selected state map: key -> Boolean
    val selectedStatements = remember {
        mutableStateMapOf<String, Boolean>().apply {
            for (stmt in unpaidStatements) {
                put(stmt.group.id, initialClearingGroupId == null || initialClearingGroupId == stmt.group.id)
            }
        }
    }

    val selectedLoanTxs = remember {
        mutableStateMapOf<String, Boolean>().apply {
            for (tx in scheduledLoanTxs) {
                put(tx.transaction.id, false)
            }
        }
    }

    // Compute total selected amount
    var totalRepaymentMinor = 0L
    for (stmt in unpaidStatements) {
        if (selectedStatements[stmt.group.id] == true) {
            totalRepaymentMinor += (-stmt.evaluation.realizedBalance)
        }
    }
    for (tx in scheduledLoanTxs) {
        if (selectedLoanTxs[tx.transaction.id] == true) {
            val outflow = tx.splits.find { it.split.amountMinor < 0 }
            val amount = if (outflow != null) -outflow.split.amountMinor else tx.splits.firstOrNull()?.split?.amountMinor ?: 0L
            totalRepaymentMinor += amount
        }
    }

    val selectedFundingAccount = finance.accounts.find { it.account.id == fundingAccountId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ThemeTokens.spacing.mdDp)
            .verticalScroll(rememberScrollState())
            .testTag("combined-repayment-sheet"),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
    ) {
        Text(
            text = "Batch Debt Repayment",
            style = ThemeTokens.type.title,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Select multiple credit card statements or loan installments to reconcile simultaneously.",
            style = ThemeTokens.type.body,
            color = ThemeTokens.colors.textSecondary,
        )

        // Funding Account Selector
        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
            Text(text = "Repayment Funding Account", style = ThemeTokens.type.label, fontWeight = FontWeight.SemiBold)
            Box {
                OutlinedButton(
                    onClick = { fundingAccountMenuExpanded = true },
                    shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                    modifier = Modifier.fillMaxWidth().testTag("repay-funding-account-select"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedFundingAccount?.let { "${it.account.name} (${it.formattedBalance})" } ?: "Select Account",
                            style = ThemeTokens.type.body,
                        )
                        Icon(imageVector = Lucide.ChevronDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                    expanded = fundingAccountMenuExpanded,
                    onDismissRequest = { fundingAccountMenuExpanded = false },
                ) {
                    for (acc in liquidAccounts) {
                        DropdownMenuItem(
                            text = { Text("${acc.account.name} (${acc.formattedBalance})") },
                            onClick = {
                                fundingAccountId = acc.account.id
                                fundingAccountMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }

        // Date input
        OutlinedTextField(
            value = repaymentDate,
            onValueChange = { repaymentDate = it },
            label = { Text("Repayment Date") },
            shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
            modifier = Modifier.fillMaxWidth().testTag("repay-date-input"),
            singleLine = true,
        )

        // Statements section
        if (unpaidStatements.isNotEmpty()) {
            Text(text = "Credit Card Statements", style = ThemeTokens.type.label, fontWeight = FontWeight.SemiBold)
            for (stmt in unpaidStatements) {
                val isChecked = selectedStatements[stmt.group.id] == true
                val debtAmt = FinanceCurrencies.format(-stmt.evaluation.realizedBalance, stmt.group.anchorCurrency)

                EditorialCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedStatements[stmt.group.id] = !isChecked }
                        .testTag("repay-item-stmt-${stmt.group.id}"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { selectedStatements[stmt.group.id] = it },
                            )
                            Column {
                                Text(text = stmt.group.name, style = ThemeTokens.type.cardTitle)
                                if (stmt.group.dueDate != null) {
                                    Text(text = "Due: ${stmt.group.dueDate}", style = ThemeTokens.type.label, color = ThemeTokens.colors.textSecondary)
                                }
                            }
                        }
                        Text(text = debtAmt, style = ThemeTokens.type.cardTitle, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                    }
                }
            }
        }

        // Loan tranches section
        if (scheduledLoanTxs.isNotEmpty()) {
            Text(text = "Upcoming Loan Tranches", style = ThemeTokens.type.label, fontWeight = FontWeight.SemiBold)
            for (txView in scheduledLoanTxs) {
                val tx = txView.transaction
                val isChecked = selectedLoanTxs[tx.id] == true
                EditorialCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedLoanTxs[tx.id] = !isChecked }
                        .testTag("repay-item-tranche-${tx.id}"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { selectedLoanTxs[tx.id] = it },
                            )
                            Column {
                                Text(text = tx.summary, style = ThemeTokens.type.cardTitle)
                                Text(text = "Due: ${tx.txDate}", style = ThemeTokens.type.label, color = ThemeTokens.colors.textSecondary)
                            }
                        }
                        Text(text = txView.primaryAmountFormatted, style = ThemeTokens.type.cardTitle, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (unpaidStatements.isEmpty() && scheduledLoanTxs.isEmpty()) {
            Text(
                text = "No open statements or scheduled installments found.",
                style = ThemeTokens.type.body,
                color = ThemeTokens.colors.textSecondary,
            )
        }

        Spacer(modifier = Modifier.height(ThemeTokens.spacing.xsDp))

        // Total Row
        EditorialCard(modifier = Modifier.fillMaxWidth().testTag("repay-total-card")) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Total Repayment", style = ThemeTokens.type.cardTitle, fontWeight = FontWeight.Bold)
                Text(
                    text = FinanceCurrencies.format(totalRepaymentMinor, "CNY"),
                    style = ThemeTokens.type.title,
                    fontWeight = FontWeight.Bold,
                    color = ThemeTokens.colors.primary,
                )
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                modifier = Modifier.weight(1f).testTag("repay-cancel-btn"),
            ) {
                Text("Cancel", style = ThemeTokens.type.label)
            }

            Button(
                onClick = {
                    if (fundingAccountId.isNotBlank()) {
                        for (stmt in unpaidStatements) {
                            if (selectedStatements[stmt.group.id] == true) {
                                onRepayStatement(stmt.group.id, fundingAccountId, repaymentDate)
                            }
                        }
                        for (tx in scheduledLoanTxs) {
                            if (selectedLoanTxs[tx.transaction.id] == true) {
                                onRepayLoanTranche(tx.transaction.id)
                            }
                        }
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThemeTokens.colors.primary,
                    contentColor = Color.White,
                ),
                enabled = fundingAccountId.isNotBlank() && totalRepaymentMinor > 0L,
                modifier = Modifier.weight(1f).testTag("repay-confirm-btn"),
            ) {
                Text("Confirm Repayment", style = ThemeTokens.type.label)
            }
        }
    }
}
