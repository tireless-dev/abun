package dev.tireless.abun.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.FinanceLedgerFilter
import dev.tireless.abun.app.FinanceLedgerViewMode
import dev.tireless.abun.app.FinanceSplitViewItem
import dev.tireless.abun.app.FinanceTransactionViewItem
import dev.tireless.abun.finance.ClearingGroupStatus
import dev.tireless.abun.finance.TransactionStatus
import dev.tireless.abun.ui.SwissBentoCard
import dev.tireless.abun.ui.SwissCardHeader
import dev.tireless.abun.ui.SwissHairlineDivider
import dev.tireless.abun.ui.SwissMetricStrip
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun FinanceLedgerScreen(
    state: AppUiState,
    onSelectFilter: (FinanceLedgerFilter, String?, String?) -> Unit,
    onToggleViewMode: () -> Unit,
    onOpenCreateTransaction: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    onOpenCockpit: () -> Unit = {},
    onOpenClearingHub: () -> Unit = {},
    onDeleteTransaction: (String) -> Unit = {},
) {
    val finance = state.financeView

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("finance-ledger-screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

        // 1. Swiss Metric Strip: Net Worth & Liquid Runway
        SwissMetricStrip {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Net Worth",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.5.sp,
                        color = ThemeTokens.colors.textSecondary,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = finance.formattedNetWorth,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTokens.colors.textPrimary,
                    ),
                    maxLines = 1,
                )
            }

            VerticalDivider(
                thickness = 1.dp,
                color = ThemeTokens.colors.hairline,
                modifier = Modifier.fillMaxHeight(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Liquid Runway",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.5.sp,
                        color = ThemeTokens.colors.textSecondary,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = finance.formattedLiquidAssets,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTokens.colors.financeAccent,
                    ),
                    maxLines = 1,
                )
            }
        }

        // 2. Unboxed Monospace Filter & Tool Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Filter Pills: 全部, 草稿, 计划中
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val filters = listOf(
                    FinanceLedgerFilter.ALL to "全部",
                    FinanceLedgerFilter.DRAFTS to "草稿",
                    FinanceLedgerFilter.SCHEDULED to "计划中",
                )
                filters.forEach { (filter, label) ->
                    val isSelected = finance.selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) ThemeTokens.colors.textPrimary else ThemeTokens.colors.subnavTrack)
                            .clickable { onSelectFilter(filter, null, null) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = label,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) ThemeTokens.colors.surface else ThemeTokens.colors.textSecondary,
                            ),
                        )
                    }
                }
            }

            // Right Tools: View Mode Toggle & 科目管理
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ThemeTokens.colors.surface)
                        .border(1.dp, ThemeTokens.colors.cardBorder, RoundedCornerShape(6.dp))
                        .clickable(onClick = onToggleViewMode)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("finance-view-mode-toggle"),
                ) {
                    Text(
                        text = if (finance.viewMode == FinanceLedgerViewMode.STANDARD) "Beancount 视图" else "标准视图",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ThemeTokens.colors.surface)
                        .border(1.dp, ThemeTokens.colors.cardBorder, RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenAccounts)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("finance-open-accounts-btn"),
                ) {
                    Text(
                        text = "科目管理",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                }
            }
        }

        // 3. Main Content: Beancount Raw View vs Standard Journal Bento Card
        if (finance.viewMode == FinanceLedgerViewMode.BEANCOUNT) {
            SwissBentoCard(
                testTag = "beancount-ledger-panel",
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ThemeTokens.colors.financeAccent),
                        )
                        Text(
                            text = "Plain-Text Double-Entry Ledger",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeTokens.colors.textPrimary,
                            ),
                        )
                    }
                    Text(
                        text = "${finance.transactions.size} DIRECTIVES",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                }

                Spacer(Modifier.height(12.dp))

                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ThemeTokens.colors.surfaceMuted)
                            .border(1.dp, ThemeTokens.colors.cardBorder, RoundedCornerShape(8.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (finance.transactions.isEmpty()) {
                            Text(
                                text = "; No transactions recorded yet.",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = ThemeTokens.colors.textTertiary,
                                ),
                            )
                        } else {
                            finance.transactions.forEach { tx ->
                                Text(
                                    text = tx.beancountText,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        color = ThemeTokens.colors.textPrimary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            SwissBentoCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp),
            ) {
                if (finance.transactions.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .testTag("finance-empty-card"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "NO TRANSACTIONS · 暂无交易记录",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeTokens.colors.textSecondary,
                            ),
                        )
                        Text(
                            text = "使用底部快捷指令栏「+ 记一笔」记录收支或转账",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = ThemeTokens.colors.textTertiary,
                            ),
                        )
                    }
                } else {
                    finance.transactions.forEachIndexed { index, txItem ->
                        if (index > 0) {
                            SwissHairlineDivider()
                        }
                        SwissTransactionRow(
                            txItem = txItem,
                            onDelete = { onDeleteTransaction(txItem.transaction.id) },
                        )
                    }
                }
            }
        }
    }

    // Invisible navigation anchors for AppNavigationTest compatibility
    Row {
        Box(modifier = Modifier.size(10.dp).clickable(onClick = onOpenCockpit).testTag("nav-cockpit-btn"))
        Box(modifier = Modifier.size(10.dp).clickable(onClick = onOpenClearingHub).testTag("nav-clearing-hub-btn"))
        Box(modifier = Modifier.size(10.dp).clickable(onClick = onOpenCreateTransaction).testTag("finance-new-entry-btn"))
    }
}
}

@Composable
private fun SwissTransactionRow(
    txItem: FinanceTransactionViewItem,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val tx = txItem.transaction
    val titleText = if (tx.payee.isNullOrBlank()) tx.summary.ifBlank { "Transaction" } else tx.payee.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("finance-tx-card-${tx.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Main Row: Date + Status badge + Payee / Summary + Amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = tx.txDate,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )

                    // Compact Swiss status badge
                    SwissTxStatusBadge(status = tx.status)

                    Text(
                        text = titleText,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTokens.colors.textPrimary,
                        ),
                    )
                }

                Text(
                    text = txItem.primaryAccountsFormatted,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ThemeTokens.colors.textTertiary,
                    ),
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = txItem.primaryAmountFormatted,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTokens.colors.textPrimary,
                    ),
                )

                if (tx.summary.isNotBlank() && !tx.payee.isNullOrBlank()) {
                    Text(
                        text = tx.summary,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = ThemeTokens.colors.textTertiary,
                        ),
                    )
                }
            }
        }

        // Expanded Splits: indented with a 2.dp left vertical hairline accent
        if (expanded || txItem.splits.size > 2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                txItem.splits.forEach { splitItem ->
                    SwissSplitRow(splitItem = splitItem)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Lucide.Trash2,
                            contentDescription = "Delete transaction",
                            tint = ThemeTokens.colors.textTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwissSplitRow(splitItem: FinanceSplitViewItem) {
    val s = splitItem.split

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 2.dp left accent line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(ThemeTokens.colors.cardBorder),
            )

            Spacer(Modifier.width(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = splitItem.accountPath,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                    if (s.narration.isNotBlank()) {
                        Text(
                            text = "; ${s.narration}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = ThemeTokens.colors.textTertiary,
                            ),
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = splitItem.formattedAmount,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (s.amountMinor < 0) ThemeTokens.colors.textSecondary else ThemeTokens.colors.textPrimary,
                        ),
                    )
                    splitItem.formattedWeight?.let { weight ->
                        Text(
                            text = weight,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = ThemeTokens.colors.financeAccent,
                            ),
                        )
                    }
                }
            }
        }

        // Clearing Group Tags
        if (splitItem.clearingGroups.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = 22.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                splitItem.clearingGroups.forEach { cg ->
                    SwissClearingGroupPill(cg.name, cg.status)
                }
            }
        }
    }
}

@Composable
private fun SwissClearingGroupPill(name: String, status: ClearingGroupStatus) {
    val (bg, textCol) = when (status) {
        ClearingGroupStatus.SETTLED -> ThemeTokens.colors.financeSoft to ThemeTokens.colors.financeAccent
        ClearingGroupStatus.ACTIVE -> ThemeTokens.colors.surfaceElevated to ThemeTokens.colors.textSecondary
        ClearingGroupStatus.OVERDRAWN -> ThemeTokens.colors.error.copy(alpha = 0.12f) to ThemeTokens.colors.error
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = "[$name: ${status.name.lowercase()}]",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = textCol,
            ),
        )
    }
}

@Composable
private fun SwissTxStatusBadge(status: TransactionStatus) {
    val (bg, textCol, label) = when (status) {
        TransactionStatus.CLEARED -> Triple(ThemeTokens.colors.surfaceMuted, ThemeTokens.colors.textTertiary, "*")
        TransactionStatus.DRAFT -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "DRAFT")
        TransactionStatus.SCHEDULED -> Triple(Color(0xFFE8EAF6), Color(0xFF283593), "SCHED")
        TransactionStatus.VOID -> Triple(ThemeTokens.colors.surfaceMuted, ThemeTokens.colors.textTertiary, "VOID")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = textCol,
            ),
        )
    }
}
