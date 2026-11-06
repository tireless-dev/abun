package dev.tireless.abun.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.FinanceAmortizationPlanViewItem
import dev.tireless.abun.app.FinanceClearingGroupViewItem
import dev.tireless.abun.finance.ClearingGroupStatus
import dev.tireless.abun.finance.ClearingGroupType
import dev.tireless.abun.finance.FinanceCurrencies
import dev.tireless.abun.finance.TransactionStatus
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.components.MetricRow
import dev.tireless.abun.ui.components.SectionHeader
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun ClearingHubScreen(
    state: AppUiState,
    onOpenLedger: () -> Unit,
    onOpenCockpit: () -> Unit = {},
    onOpenCombinedRepayment: () -> Unit,
    onOpenAmortizationWizard: () -> Unit,
    onRepayStatement: (clearingGroupId: String) -> Unit,
    onRepayLoanTranche: (transactionId: String) -> Unit,
    onDeleteAmortizationPlan: (planId: String) -> Unit,
) {
    val finance = state.financeView
    val statements = finance.clearingGroups.filter { it.group.groupType == ClearingGroupType.CREDIT_STATEMENT }
    val plans = finance.amortizationPlans

    val totalUnsettledDebtMinor = statements.sumOf { item ->
        if (item.evaluation.realizedBalance < 0L) -item.evaluation.realizedBalance else 0L
    } + plans.sumOf { it.remainingPrincipalMinor }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag("clearing-hub-screen"),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
    ) {
        // Subtab Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                    .clickable(onClick = onOpenCockpit)
                    .padding(horizontal = ThemeTokens.spacing.mdDp, vertical = ThemeTokens.spacing.xsDp)
                    .testTag("nav-cockpit-btn"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "总览",
                    style = ThemeTokens.type.label,
                    color = ThemeTokens.colors.textSecondary,
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                    .clickable(onClick = onOpenLedger)
                    .padding(horizontal = ThemeTokens.spacing.mdDp, vertical = ThemeTokens.spacing.xsDp)
                    .testTag("nav-finance-ledger-btn"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "明细",
                    style = ThemeTokens.type.label,
                    color = ThemeTokens.colors.textSecondary,
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                    .background(ThemeTokens.colors.primary.copy(alpha = 0.12f))
                    .padding(horizontal = ThemeTokens.spacing.mdDp, vertical = ThemeTokens.spacing.xsDp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "债务与清算",
                    style = ThemeTokens.type.label,
                    fontWeight = FontWeight.Bold,
                    color = ThemeTokens.colors.primary,
                )
            }
        }

        // Metric Row
        MetricRow(
            listOf(
                "Unsettled Debt" to FinanceCurrencies.format(-totalUnsettledDebtMinor, "CNY"),
                "Active Statements" to statements.count { it.group.status == ClearingGroupStatus.ACTIVE }.toString(),
                "Active Loans" to plans.count { it.clearedPeriods < it.totalPeriods }.toString(),
            ),
        )

        // Toolbar Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
                OutlinedButton(
                    onClick = onOpenLedger,
                    shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                    modifier = Modifier.testTag("clearing-hub-back-to-ledger-btn"),
                ) {
                    Text("Ledger View", style = ThemeTokens.type.label)
                }

                OutlinedButton(
                    onClick = onOpenCombinedRepayment,
                    shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                    modifier = Modifier.testTag("clearing-hub-combined-repay-btn"),
                ) {
                    Icon(imageVector = Lucide.Layers, contentDescription = null, modifier = Modifier.padding(end = ThemeTokens.spacing.xsDp))
                    Text("Batch Repay", style = ThemeTokens.type.label)
                }
            }

            Button(
                onClick = onOpenAmortizationWizard,
                shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThemeTokens.colors.primary,
                    contentColor = Color.White,
                ),
                modifier = Modifier.testTag("clearing-hub-new-plan-btn"),
            ) {
                Icon(imageVector = Lucide.Plus, contentDescription = null, modifier = Modifier.padding(end = ThemeTokens.spacing.xsDp))
                Text("New Plan", style = ThemeTokens.type.label)
            }
        }

        // Section 1: Credit Card Statements
        SectionHeader("Credit Card Statements", "Monthly billing cycles and payment reconciliation")

        if (statements.isEmpty()) {
            EditorialCard(modifier = Modifier.fillMaxWidth().testTag("clearing-hub-no-statements")) {
                Text(
                    text = "No credit card statements recorded yet.",
                    style = ThemeTokens.type.body,
                    color = ThemeTokens.colors.textSecondary,
                )
            }
        } else {
            for (stmt in statements) {
                StatementCard(
                    item = stmt,
                    onRepay = { onRepayStatement(stmt.group.id) },
                )
            }
        }

        Spacer(modifier = Modifier.height(ThemeTokens.spacing.smDp))

        // Section 2: Installment & Loan Plans
        SectionHeader("Installment & Loan Schedules", "Multi-period amortization tracking")

        if (plans.isEmpty()) {
            EditorialCard(modifier = Modifier.fillMaxWidth().testTag("clearing-hub-no-plans")) {
                Text(
                    text = "No installment or loan plans configured yet.",
                    style = ThemeTokens.type.body,
                    color = ThemeTokens.colors.textSecondary,
                )
            }
        } else {
            for (plan in plans) {
                val nextTrancheTx = finance.transactions
                    .map { it.transaction }
                    .firstOrNull { it.sourcePlanId == plan.plan.id && it.status == TransactionStatus.SCHEDULED }

                AmortizationPlanCard(
                    item = plan,
                    nextTrancheTxId = nextTrancheTx?.id,
                    onRepayTranche = { txId -> onRepayLoanTranche(txId) },
                    onDelete = { onDeleteAmortizationPlan(plan.plan.id) },
                )
            }
        }
    }
}

@Composable
private fun StatementCard(
    item: FinanceClearingGroupViewItem,
    onRepay: () -> Unit,
) {
    val group = item.group
    val isSettled = group.status == ClearingGroupStatus.SETTLED
    val debtMinor = item.evaluation.realizedBalance

    EditorialCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("statement-card-${group.id}"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
                ) {
                    Icon(imageVector = Lucide.CreditCard, contentDescription = null, tint = ThemeTokens.colors.primary)
                    Text(text = group.name, style = ThemeTokens.type.cardTitle, fontWeight = FontWeight.Bold)
                }

                // Status Badge
                val badgeColor = when (group.status) {
                    ClearingGroupStatus.SETTLED -> Color(0xFF2E7D32)
                    ClearingGroupStatus.OVERDRAWN -> Color(0xFF1976D2)
                    ClearingGroupStatus.ACTIVE -> Color(0xFFD32F2F)
                }
                val badgeText = when (group.status) {
                    ClearingGroupStatus.SETTLED -> "Settled"
                    ClearingGroupStatus.OVERDRAWN -> "Overdrawn"
                    ClearingGroupStatus.ACTIVE -> "Unpaid"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                        .background(badgeColor.copy(alpha = 0.12f))
                        .padding(horizontal = ThemeTokens.spacing.xsDp, vertical = 2.dp),
                ) {
                    Text(text = badgeText, style = ThemeTokens.type.label, color = badgeColor)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    if (group.dueDate != null) {
                        Text(text = "Due: ${group.dueDate}", style = ThemeTokens.type.label, color = ThemeTokens.colors.textSecondary)
                    }
                    Text(
                        text = "Balance: ${item.formattedRealizedBalance}",
                        style = ThemeTokens.type.title,
                        color = if (debtMinor < 0L) Color(0xFFD32F2F) else ThemeTokens.colors.textPrimary,
                    )
                }

                if (!isSettled && debtMinor < 0L) {
                    Button(
                        onClick = onRepay,
                        shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ThemeTokens.colors.primary,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.testTag("repay-statement-btn-${group.id}"),
                    ) {
                        Text("Repay", style = ThemeTokens.type.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun AmortizationPlanCard(
    item: FinanceAmortizationPlanViewItem,
    nextTrancheTxId: String?,
    onRepayTranche: (String) -> Unit,
    onDelete: () -> Unit,
) {
    EditorialCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("amortization-plan-card-${item.plan.id}"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = item.plan.name, style = ThemeTokens.type.cardTitle, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${item.liabilityAccountName} · ${item.plan.repaymentMethod.name} (${item.plan.annualRateBps / 100.0}% APR)",
                        style = ThemeTokens.type.label,
                        color = ThemeTokens.colors.textSecondary,
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete-plan-btn-${item.plan.id}"),
                ) {
                    Icon(imageVector = Lucide.Trash2, contentDescription = "Delete Plan", tint = ThemeTokens.colors.textSecondary)
                }
            }

            // Progress bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Paid ${item.formattedPaidPrincipal} of ${item.formattedPrincipal}",
                        style = ThemeTokens.type.label,
                    )
                    Text(
                        text = "${item.clearedPeriods}/${item.totalPeriods} periods",
                        style = ThemeTokens.type.label,
                        fontWeight = FontWeight.Bold,
                    )
                }

                LinearProgressIndicator(
                    progress = { item.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(ThemeTokens.radii.smallDp)),
                    color = ThemeTokens.colors.primary,
                    trackColor = ThemeTokens.colors.surfaceMuted,
                )
            }

            // Next Due / Completion
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.clearedPeriods >= item.totalPeriods) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                            .background(Color(0xFF2E7D32).copy(alpha = 0.12f))
                            .padding(horizontal = ThemeTokens.spacing.xsDp, vertical = 2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Lucide.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.padding(end = 4.dp))
                            Text(text = "Fully Repaid", style = ThemeTokens.type.label, color = Color(0xFF2E7D32))
                        }
                    }
                } else {
                    Column {
                        if (item.nextDueDate != null) {
                            Text(text = "Next: ${item.nextDueDate}", style = ThemeTokens.type.label, color = ThemeTokens.colors.textSecondary)
                        }
                        val nextPayment = item.nextPaymentFormatted
                        if (nextPayment != null) {
                            Text(text = nextPayment, style = ThemeTokens.type.cardTitle, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (nextTrancheTxId != null) {
                        Button(
                            onClick = { onRepayTranche(nextTrancheTxId) },
                            shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ThemeTokens.colors.primary,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.testTag("repay-tranche-btn-${item.plan.id}"),
                        ) {
                            Text("Repay This Period", style = ThemeTokens.type.label)
                        }
                    }
                }
            }
        }
    }
}
