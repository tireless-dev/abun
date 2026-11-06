package dev.tireless.abun.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.ListTodo
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Wallet
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.FinanceClearingGroupViewItem
import dev.tireless.abun.app.FinanceLedgerFilter
import dev.tireless.abun.app.FinanceLedgerViewMode
import dev.tireless.abun.app.FinanceSplitViewItem
import dev.tireless.abun.app.FinanceTransactionViewItem
import dev.tireless.abun.finance.ClearingGroupStatus
import dev.tireless.abun.finance.TransactionStatus
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.components.MetricRow
import dev.tireless.abun.ui.components.Panel
import dev.tireless.abun.ui.components.SectionHeader
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun FinanceLedgerScreen(
    state: AppUiState,
    onSelectFilter: (FinanceLedgerFilter, String?, String?) -> Unit,
    onToggleViewMode: () -> Unit,
    onOpenCreateTransaction: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCockpit: () -> Unit = {},
    onOpenClearingHub: () -> Unit = {},
    onDeleteTransaction: (String) -> Unit,
) {
    val finance = state.financeView

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("finance-ledger-screen"),
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
                    .background(ThemeTokens.colors.primary.copy(alpha = 0.12f))
                    .padding(horizontal = ThemeTokens.spacing.mdDp, vertical = ThemeTokens.spacing.xsDp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "明细",
                    style = ThemeTokens.type.label,
                    fontWeight = FontWeight.Bold,
                    color = ThemeTokens.colors.primary,
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                    .clickable(onClick = onOpenClearingHub)
                    .padding(horizontal = ThemeTokens.spacing.mdDp, vertical = ThemeTokens.spacing.xsDp)
                    .testTag("nav-clearing-hub-btn"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "债务与清算",
                    style = ThemeTokens.type.label,
                    color = ThemeTokens.colors.textSecondary,
                )
            }
        }

        // Metric Row: Net Worth & Liquid Runway Assets
        MetricRow(
            listOf(
                "Net Worth" to finance.formattedNetWorth,
                "Liquid Runway" to finance.formattedLiquidAssets,
            ),
        )

        // Actions & Mode Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
                OutlinedButton(
                    onClick = onToggleViewMode,
                    shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                    modifier = Modifier.testTag("finance-view-mode-toggle"),
                ) {
                    Icon(
                        imageVector = if (finance.viewMode == FinanceLedgerViewMode.STANDARD) Lucide.Code else Lucide.ListTodo,
                        contentDescription = null,
                        modifier = Modifier.padding(end = ThemeTokens.spacing.xsDp),
                    )
                    Text(
                        text = if (finance.viewMode == FinanceLedgerViewMode.STANDARD) "Beancount View" else "Card View",
                        style = ThemeTokens.type.label,
                    )
                }

                OutlinedButton(
                    onClick = onOpenAccounts,
                    shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                    modifier = Modifier.testTag("finance-open-accounts-btn"),
                ) {
                    Icon(
                        imageVector = Lucide.Wallet,
                        contentDescription = null,
                        modifier = Modifier.padding(end = ThemeTokens.spacing.xsDp),
                    )
                    Text("Accounts & Pad", style = ThemeTokens.type.label)
                }

                OutlinedButton(
                    onClick = onOpenClearingHub,
                    shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                    modifier = Modifier.testTag("finance-open-clearing-hub-btn"),
                ) {
                    Icon(
                        imageVector = Lucide.Layers,
                        contentDescription = null,
                        modifier = Modifier.padding(end = ThemeTokens.spacing.xsDp),
                    )
                    Text("Debt & Hub", style = ThemeTokens.type.label)
                }
            }

            Button(
                onClick = onOpenCreateTransaction,
                shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThemeTokens.colors.primary,
                    contentColor = Color.White,
                ),
                modifier = Modifier.testTag("finance-new-entry-btn"),
            ) {
                Icon(imageVector = Lucide.Plus, contentDescription = null, modifier = Modifier.padding(end = ThemeTokens.spacing.xsDp))
                Text("New Entry", style = ThemeTokens.type.label)
            }
        }

        // Filter Bar Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
        ) {
            FinanceFilterChip(
                label = "All",
                selected = finance.selectedFilter == FinanceLedgerFilter.ALL,
                onClick = { onSelectFilter(FinanceLedgerFilter.ALL, null, null) },
            )
            FinanceFilterChip(
                label = "Drafts (Inbox)",
                selected = finance.selectedFilter == FinanceLedgerFilter.DRAFTS,
                onClick = { onSelectFilter(FinanceLedgerFilter.DRAFTS, null, null) },
            )
            FinanceFilterChip(
                label = "Scheduled",
                selected = finance.selectedFilter == FinanceLedgerFilter.SCHEDULED,
                onClick = { onSelectFilter(FinanceLedgerFilter.SCHEDULED, null, null) },
            )
        }

        // Content: Beancount View vs Standard View
        if (finance.viewMode == FinanceLedgerViewMode.BEANCOUNT) {
            BeancountLedgerView(transactions = finance.transactions)
        } else {
            StandardLedgerView(
                transactions = finance.transactions,
                onDeleteTransaction = onDeleteTransaction,
            )
        }
    }
}

@Composable
private fun FinanceFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) ThemeTokens.colors.primary.copy(alpha = 0.12f) else ThemeTokens.colors.surface
    val border = if (selected) ThemeTokens.colors.primary else ThemeTokens.colors.border
    val textCol = if (selected) ThemeTokens.colors.primary else ThemeTokens.colors.textSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(ThemeTokens.radii.smallDp))
            .clickable(onClick = onClick)
            .padding(horizontal = ThemeTokens.spacing.mdDp, vertical = ThemeTokens.spacing.xsDp),
    ) {
        Text(text = label, style = ThemeTokens.type.label, color = textCol)
    }
}

@Composable
private fun StandardLedgerView(
    transactions: List<FinanceTransactionViewItem>,
    onDeleteTransaction: (String) -> Unit,
) {
    if (transactions.isEmpty()) {
        EditorialCard(testTag = "finance-empty-card") {
            Text("No transactions found", style = ThemeTokens.type.cardTitle)
            Text(
                "Tap 'New Entry' or the '+' button to record your first income, expense, transfer, or split bill.",
                style = ThemeTokens.type.bodyMuted,
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
        transactions.forEach { txItem ->
            TransactionCard(
                txItem = txItem,
                onDelete = { onDeleteTransaction(txItem.transaction.id) },
            )
        }
    }
}

@Composable
private fun TransactionCard(
    txItem: FinanceTransactionViewItem,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val tx = txItem.transaction

    EditorialCard(
        testTag = "finance-tx-card-${tx.id}",
        modifier = Modifier.clickable { expanded = !expanded },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                ) {
                    val titleText = if (tx.payee.isNullOrBlank()) tx.summary.ifBlank { "Transaction" } else tx.payee.orEmpty()
                    Text(
                        text = titleText,
                        style = ThemeTokens.type.cardTitle,
                    )
                    TransactionStatusBadge(status = tx.status)
                }

                Text(
                    text = "${tx.txDate} • ${txItem.primaryAccountsFormatted}",
                    style = ThemeTokens.type.label,
                    color = ThemeTokens.colors.textSecondary,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = txItem.primaryAmountFormatted,
                    style = ThemeTokens.type.cardTitle.copy(fontWeight = FontWeight.SemiBold),
                )
                if (tx.summary.isNotBlank() && !tx.payee.isNullOrBlank()) {
                    Text(
                        text = tx.summary,
                        style = ThemeTokens.type.label,
                        color = ThemeTokens.colors.textTertiary,
                    )
                }
            }
        }

        // Expanded Splits & Clearing Group Tags
        if (expanded || txItem.splits.size > 2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        ThemeTokens.colors.surfaceMuted,
                        RoundedCornerShape(ThemeTokens.radii.smallDp),
                    )
                    .border(
                        1.dp,
                        ThemeTokens.colors.border,
                        RoundedCornerShape(ThemeTokens.radii.smallDp),
                    )
                    .padding(ThemeTokens.spacing.smDp),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
            ) {
                Text("Double-Entry Splits", style = ThemeTokens.type.label, color = ThemeTokens.colors.textTertiary)

                txItem.splits.forEach { splitItem ->
                    SplitRowView(splitItem)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Lucide.Trash2,
                            contentDescription = "Delete transaction",
                            tint = ThemeTokens.colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitRowView(splitItem: FinanceSplitViewItem) {
    val s = splitItem.split

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = splitItem.accountPath,
                    style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Medium),
                )
                if (s.narration.isNotBlank()) {
                    Text(
                        text = "; ${s.narration}",
                        style = ThemeTokens.type.label,
                        color = ThemeTokens.colors.textSecondary,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = splitItem.formattedAmount,
                    style = ThemeTokens.type.body.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (s.amountMinor < 0) ThemeTokens.colors.textSecondary else ThemeTokens.colors.textPrimary,
                    ),
                )
                val weight = splitItem.formattedWeight
                if (weight != null) {
                    Text(
                        text = weight,
                        style = ThemeTokens.type.label,
                        color = ThemeTokens.colors.primary,
                    )
                }
            }
        }

        // Clearing Group Tags on Split
        if (splitItem.clearingGroups.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = ThemeTokens.spacing.xsDp),
                horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
            ) {
                splitItem.clearingGroups.forEach { cg ->
                    ClearingGroupPill(cg.name, cg.status)
                }
            }
        }
    }
}

@Composable
private fun ClearingGroupPill(name: String, status: ClearingGroupStatus) {
    val (bg, textCol) = when (status) {
        ClearingGroupStatus.SETTLED -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        ClearingGroupStatus.ACTIVE -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
        ClearingGroupStatus.OVERDRAWN -> Color(0xFFFFEBEE) to Color(0xFFC62828)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "[$name: ${status.name.lowercase()}]",
            style = ThemeTokens.type.label,
            color = textCol,
        )
    }
}

@Composable
private fun TransactionStatusBadge(status: TransactionStatus) {
    val (bg, textCol) = when (status) {
        TransactionStatus.CLEARED -> ThemeTokens.colors.surfaceMuted to ThemeTokens.colors.textSecondary
        TransactionStatus.DRAFT -> Color(0xFFFFF3E0) to Color(0xFFE65100)
        TransactionStatus.SCHEDULED -> Color(0xFFE8EAF6) to Color(0xFF283593)
        TransactionStatus.VOID -> ThemeTokens.colors.surfaceMuted to ThemeTokens.colors.textTertiary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
            .background(bg)
            .border(1.dp, ThemeTokens.colors.border, RoundedCornerShape(ThemeTokens.radii.smallDp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = status.name.lowercase().replaceFirstChar(Char::titlecase),
            style = ThemeTokens.type.label,
            color = textCol,
        )
    }
}

@Composable
private fun BeancountLedgerView(transactions: List<FinanceTransactionViewItem>) {
    Panel(testTag = "beancount-ledger-panel") {
        SectionHeader("Plain-Text Double-Entry Ledger", "${transactions.size} directives")

        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ThemeTokens.colors.surfaceMuted, RoundedCornerShape(ThemeTokens.radii.mediumDp))
                    .border(1.dp, ThemeTokens.colors.border, RoundedCornerShape(ThemeTokens.radii.mediumDp))
                    .padding(ThemeTokens.spacing.mdDp),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
            ) {
                if (transactions.isEmpty()) {
                    Text("; No transactions recorded yet.", fontFamily = FontFamily.Monospace, style = ThemeTokens.type.bodyMuted)
                } else {
                    transactions.forEach { tx ->
                        Text(
                            text = tx.beancountText,
                            fontFamily = FontFamily.Monospace,
                            style = ThemeTokens.type.body.copy(fontSize = ThemeTokens.type.label.fontSize),
                            color = ThemeTokens.colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}
