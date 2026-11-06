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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.AmortizationGenerator
import dev.tireless.abun.finance.AmortizationMethod
import dev.tireless.abun.finance.FinanceCurrencies
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AmortizationWizardSheet(
    state: AppUiState,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        principalMinor: Long,
        currency: String,
        totalPeriods: Int,
        repaymentMethod: AmortizationMethod,
        annualRateBps: Int,
        feePerPeriodMinor: Long,
        startDate: String,
        repaymentDay: Int,
        liabilityAccountId: String,
        fundingAccountId: String?,
        interestAccountId: String,
        recordInitialDisbursement: Boolean,
    ) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        AmortizationWizardSheetContent(
            state = state,
            onDismiss = onDismiss,
            onCreate = onCreate,
        )
    }
}

@Composable
fun AmortizationWizardSheetContent(
    state: AppUiState,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        principalMinor: Long,
        currency: String,
        totalPeriods: Int,
        repaymentMethod: AmortizationMethod,
        annualRateBps: Int,
        feePerPeriodMinor: Long,
        startDate: String,
        repaymentDay: Int,
        liabilityAccountId: String,
        fundingAccountId: String?,
        interestAccountId: String,
        recordInitialDisbursement: Boolean,
    ) -> Unit,
) {
    val finance = state.financeView
    val liabilityAccounts = finance.accounts.filter { it.account.category == AccountCategory.LIABILITY }
    val assetAccounts = finance.accounts.filter { it.account.category == AccountCategory.ASSET && !it.account.isPassThrough }
    val expenseAccounts = finance.accounts.filter { it.account.category == AccountCategory.EXPENSE }

    val defaultInterestAccount = expenseAccounts.find { it.account.path == "Expenses:Finance:Interest" }
        ?: expenseAccounts.firstOrNull()

    var name by remember { mutableStateOf("") }
    var principalStr by remember { mutableStateOf("") }
    var totalPeriodsStr by remember { mutableStateOf("12") }
    var method by remember { mutableStateOf(AmortizationMethod.EQUAL_INSTALLMENT) }
    var annualRatePercentStr by remember { mutableStateOf("4.8") }
    var feePerPeriodStr by remember { mutableStateOf("0") }
    var startDate by remember { mutableStateOf(state.selectedDate) }
    var repaymentDayStr by remember {
        val day = state.selectedDate.split("-").getOrNull(2) ?: "15"
        mutableStateOf(day)
    }
    var liabilityAccountId by remember {
        mutableStateOf(liabilityAccounts.firstOrNull()?.account?.id.orEmpty())
    }
    var fundingAccountId by remember {
        mutableStateOf(assetAccounts.firstOrNull()?.account?.id.orEmpty())
    }
    var interestAccountId by remember {
        mutableStateOf(defaultInterestAccount?.account?.id.orEmpty())
    }
    var recordDisbursement by remember { mutableStateOf(true) }

    var liabilityMenuExpanded by remember { mutableStateOf(false) }
    var fundingMenuExpanded by remember { mutableStateOf(false) }
    var interestMenuExpanded by remember { mutableStateOf(false) }

    val selectedLiability = finance.accounts.find { it.account.id == liabilityAccountId }
    val selectedFunding = finance.accounts.find { it.account.id == fundingAccountId }
    val selectedInterest = finance.accounts.find { it.account.id == interestAccountId }

    val principalMinor = FinanceCurrencies.toMinorUnits(principalStr.toDoubleOrNull() ?: 0.0, "CNY")
    val totalPeriods = totalPeriodsStr.toIntOrNull() ?: 12
    val annualRateBps = ((annualRatePercentStr.toDoubleOrNull() ?: 0.0) * 100).toInt()
    val feePerPeriodMinor = FinanceCurrencies.toMinorUnits(feePerPeriodStr.toDoubleOrNull() ?: 0.0, "CNY")
    val repaymentDay = repaymentDayStr.toIntOrNull() ?: 15

    // Preview generation
    val previewSchedule = remember(principalMinor, totalPeriods, method, annualRateBps, feePerPeriodMinor, repaymentDay, startDate) {
        if (principalMinor > 0L && totalPeriods > 0 && startDate.split("-").size == 3) {
            runCatching {
                AmortizationGenerator.generateSchedule(
                    principalMinor = principalMinor,
                    totalPeriods = totalPeriods,
                    method = method,
                    annualRateBps = annualRateBps,
                    feePerPeriodMinor = feePerPeriodMinor,
                    repaymentDay = repaymentDay,
                    firstRepaymentDate = startDate,
                )
            }.getOrNull()
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ThemeTokens.spacing.mdDp)
            .verticalScroll(rememberScrollState())
            .testTag("amortization-wizard-sheet"),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
    ) {
        Text(
            text = "New Amortization Plan",
            style = ThemeTokens.type.title,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Generate scheduled installment or loan amortization with exact cent reconciliation.",
            style = ThemeTokens.type.body,
            color = ThemeTokens.colors.textSecondary,
        )

        // Plan Name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Plan Name (e.g. MacBook Pro 12-Period)") },
            shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
            modifier = Modifier.fillMaxWidth().testTag("plan-name-input"),
            singleLine = true,
        )

        // Principal and Periods
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            OutlinedTextField(
                value = principalStr,
                onValueChange = { principalStr = it },
                label = { Text("Principal Amount (¥)") },
                shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                modifier = Modifier.weight(1.5f).testTag("plan-principal-input"),
                singleLine = true,
            )
            OutlinedTextField(
                value = totalPeriodsStr,
                onValueChange = { totalPeriodsStr = it },
                label = { Text("Periods (Months)") },
                shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                modifier = Modifier.weight(1f).testTag("plan-periods-input"),
                singleLine = true,
            )
        }

        // Repayment Method
        Text(text = "Repayment Method", style = ThemeTokens.type.label, fontWeight = FontWeight.SemiBold)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().testTag("plan-method-segmented")) {
            val methods = listOf(
                AmortizationMethod.EQUAL_INSTALLMENT to "Equal Installment",
                AmortizationMethod.EQUAL_PRINCIPAL to "Equal Principal",
                AmortizationMethod.FLAT_FEE to "Flat Fee",
                AmortizationMethod.INTEREST_FIRST to "Interest First",
            )
            methods.forEachIndexed { index, (m, label) ->
                SegmentedButton(
                    selected = method == m,
                    onClick = { method = m },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = methods.size),
                ) {
                    Text(label, style = ThemeTokens.type.label)
                }
            }
        }

        // Rate or Fee
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            OutlinedTextField(
                value = annualRatePercentStr,
                onValueChange = { annualRatePercentStr = it },
                label = { Text("Annual Rate APR (%)") },
                shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                modifier = Modifier.weight(1f).testTag("plan-rate-input"),
                singleLine = true,
            )
            OutlinedTextField(
                value = feePerPeriodStr,
                onValueChange = { feePerPeriodStr = it },
                label = { Text("Fee Per Period (¥)") },
                shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                modifier = Modifier.weight(1f).testTag("plan-fee-input"),
                singleLine = true,
            )
        }

        // Dates
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            OutlinedTextField(
                value = startDate,
                onValueChange = { startDate = it },
                label = { Text("First Repayment Date") },
                shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                modifier = Modifier.weight(1.5f).testTag("plan-start-date-input"),
                singleLine = true,
            )
            OutlinedTextField(
                value = repaymentDayStr,
                onValueChange = { repaymentDayStr = it },
                label = { Text("Due Day (1-31)") },
                shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                modifier = Modifier.weight(1f).testTag("plan-due-day-input"),
                singleLine = true,
            )
        }

        // Liability Account Selector
        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
            Text(text = "Liability Account (Debt Holder)", style = ThemeTokens.type.label, fontWeight = FontWeight.SemiBold)
            Box {
                OutlinedButton(
                    onClick = { liabilityMenuExpanded = true },
                    shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                    modifier = Modifier.fillMaxWidth().testTag("plan-liability-select"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(selectedLiability?.account?.name ?: "Select Liability Account", style = ThemeTokens.type.body)
                        Icon(imageVector = Lucide.ChevronDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                    expanded = liabilityMenuExpanded,
                    onDismissRequest = { liabilityMenuExpanded = false },
                ) {
                    for (acc in liabilityAccounts) {
                        DropdownMenuItem(
                            text = { Text(acc.account.name) },
                            onClick = {
                                liabilityAccountId = acc.account.id
                                liabilityMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }

        // Funding Account Selector
        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
            Text(text = "Repayment Funding Account (Bank/Cash)", style = ThemeTokens.type.label, fontWeight = FontWeight.SemiBold)
            Box {
                OutlinedButton(
                    onClick = { fundingMenuExpanded = true },
                    shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                    modifier = Modifier.fillMaxWidth().testTag("plan-funding-select"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(selectedFunding?.account?.name ?: "Select Funding Account", style = ThemeTokens.type.body)
                        Icon(imageVector = Lucide.ChevronDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                    expanded = fundingMenuExpanded,
                    onDismissRequest = { fundingMenuExpanded = false },
                ) {
                    for (acc in assetAccounts) {
                        DropdownMenuItem(
                            text = { Text("${acc.account.name} (${acc.formattedBalance})") },
                            onClick = {
                                fundingAccountId = acc.account.id
                                fundingMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }

        // Interest Account Selector
        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
            Text(text = "Interest Expense Account", style = ThemeTokens.type.label, fontWeight = FontWeight.SemiBold)
            Box {
                OutlinedButton(
                    onClick = { interestMenuExpanded = true },
                    shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                    modifier = Modifier.fillMaxWidth().testTag("plan-interest-select"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(selectedInterest?.account?.name ?: "Select Interest Account", style = ThemeTokens.type.body)
                        Icon(imageVector = Lucide.ChevronDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                    expanded = interestMenuExpanded,
                    onDismissRequest = { interestMenuExpanded = false },
                ) {
                    for (acc in expenseAccounts) {
                        DropdownMenuItem(
                            text = { Text(acc.account.name) },
                            onClick = {
                                interestAccountId = acc.account.id
                                interestMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }

        // Checkbox: Record initial disbursement
        Row(
            modifier = Modifier.fillMaxWidth().clickable { recordDisbursement = !recordDisbursement },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
        ) {
            Checkbox(
                checked = recordDisbursement,
                onCheckedChange = { recordDisbursement = it },
                modifier = Modifier.testTag("plan-record-disbursement-checkbox"),
            )
            Text(text = "Record Initial Loan Disbursement (+Bank, -Liability)", style = ThemeTokens.type.body)
        }

        // Preview Box
        if (previewSchedule != null && previewSchedule.isNotEmpty()) {
            val totalPayment = previewSchedule.sumOf { it.totalPaymentMinor }
            val totalInterest = previewSchedule.sumOf { it.interestMinor }
            val totalFee = previewSchedule.sumOf { it.feeMinor }
            val firstPayment = previewSchedule.first().totalPaymentMinor

            EditorialCard(modifier = Modifier.fillMaxWidth().testTag("plan-preview-card")) {
                Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
                    Text(text = "Amortization Preview", style = ThemeTokens.type.cardTitle, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Period 1 Payment:", style = ThemeTokens.type.label)
                        Text(FinanceCurrencies.format(firstPayment, "CNY"), style = ThemeTokens.type.label, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Interest & Fees:", style = ThemeTokens.type.label)
                        Text(FinanceCurrencies.format(totalInterest + totalFee, "CNY"), style = ThemeTokens.type.label)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Outflow Across $totalPeriods Periods:", style = ThemeTokens.type.label)
                        Text(FinanceCurrencies.format(totalPayment, "CNY"), style = ThemeTokens.type.cardTitle, fontWeight = FontWeight.Bold, color = ThemeTokens.colors.primary)
                    }
                }
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
                modifier = Modifier.weight(1f).testTag("plan-cancel-btn"),
            ) {
                Text("Cancel", style = ThemeTokens.type.label)
            }

            Button(
                onClick = {
                    if (name.isNotBlank() && principalMinor > 0L && liabilityAccountId.isNotBlank()) {
                        onCreate(
                            name,
                            principalMinor,
                            "CNY",
                            totalPeriods,
                            method,
                            annualRateBps,
                            feePerPeriodMinor,
                            startDate,
                            repaymentDay,
                            liabilityAccountId,
                            fundingAccountId.ifBlank { null },
                            interestAccountId,
                            recordDisbursement,
                        )
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThemeTokens.colors.primary,
                    contentColor = Color.White,
                ),
                enabled = name.isNotBlank() && principalMinor > 0L && liabilityAccountId.isNotBlank(),
                modifier = Modifier.weight(1f).testTag("plan-create-btn"),
            ) {
                Text("Create Plan", style = ThemeTokens.type.label)
            }
        }
    }
}
