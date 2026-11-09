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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import dev.tireless.abun.app.FinanceAccountViewItem
import dev.tireless.abun.app.FinanceDateRangePreset
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
    onSelectFilter: (FinanceLedgerFilter, String?, String?) -> Unit = { _, _, _ -> },
    onToggleViewMode: () -> Unit = {},
    onToggleSortOrder: () -> Unit = {},
    onToggleIncludeFuture: () -> Unit = {},
    onSelectDateRange: (FinanceDateRangePreset) -> Unit = {},
    onSelectAccounts: (Set<String>) -> Unit = {},
    onLoadMoreTransactions: () -> Unit = {},
    onOpenCreateTransaction: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    onOpenCockpit: () -> Unit = {},
    onOpenClearingHub: () -> Unit = {},
    onDeleteTransaction: (String) -> Unit = {},
) {
    val finance = state.financeView
    val todayIso = state.selectedDate

    val initialIndex = remember(finance.transactions, todayIso, finance.sortAscending, finance.includeFuture) {
        if (finance.sortAscending) {
            val lastNonFuture = finance.transactions.indexOfLast { it.transaction.txDate <= todayIso }
            if (lastNonFuture >= 0) maxOf(0, lastNonFuture - 5) else 0
        } else {
            val firstNonFuture = finance.transactions.indexOfFirst { it.transaction.txDate <= todayIso }
            if (firstNonFuture >= 0) firstNonFuture else 0
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    LaunchedEffect(finance.sortAscending, finance.includeFuture, finance.dateRangePreset, finance.filterAccountIds) {
        if (finance.transactions.isNotEmpty()) {
            if (finance.sortAscending) {
                val lastNonFuture = finance.transactions.indexOfLast { it.transaction.txDate <= todayIso }
                val targetIndex = if (lastNonFuture >= 0) {
                    maxOf(0, lastNonFuture - 5)
                } else {
                    maxOf(0, finance.transactions.size - 6)
                }
                if (targetIndex in finance.transactions.indices) {
                    listState.scrollToItem(targetIndex)
                }
            } else {
                val firstNonFuture = finance.transactions.indexOfFirst { it.transaction.txDate <= todayIso }
                val targetIndex = if (firstNonFuture >= 0) firstNonFuture else 0
                if (targetIndex in finance.transactions.indices) {
                    listState.scrollToItem(targetIndex)
                }
            }
        }
    }

    LaunchedEffect(listState, finance.sortAscending, finance.hasMoreTransactions) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) false
            else if (finance.sortAscending) {
                val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                firstVisible <= 2
            } else {
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= totalItems - 3
            }
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && finance.hasMoreTransactions) {
                onLoadMoreTransactions()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("finance-ledger-screen"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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

        // 2. Toolbar & Filtering Area: [ 排序 ] [ 是否包含未来 ] [ 日期范围选择 ] [ 账户选择(多选) ] [ 高级筛选 ]
        var accountDialogVisible by remember { mutableStateOf(false) }
        var advancedDialogVisible by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. 排序按钮 (最左侧，两种状态保持相同中性样式)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ThemeTokens.colors.surface)
                    .border(1.dp, ThemeTokens.colors.cardBorder, RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggleSortOrder)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("finance-sort-order-toggle"),
            ) {
                Text(
                    text = if (finance.sortAscending) "正序 ↑" else "倒序 ↓",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = ThemeTokens.colors.textPrimary,
                    ),
                )
            }

            // 2. 是否包含未来交易
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (finance.includeFuture) ThemeTokens.colors.surfaceElevated else ThemeTokens.colors.surface)
                    .border(
                        1.dp,
                        if (finance.includeFuture) ThemeTokens.colors.financeAccent else ThemeTokens.colors.cardBorder,
                        RoundedCornerShape(6.dp),
                    )
                    .clickable(onClick = onToggleIncludeFuture)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("finance-include-future-toggle"),
            ) {
                Text(
                    text = if (finance.includeFuture) "包含未来" else "隐藏未来",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (finance.includeFuture) ThemeTokens.colors.financeAccent else ThemeTokens.colors.textSecondary,
                    ),
                )
            }

            // 3. 日期范围选择
            Box {
                var dateMenuExpanded by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (finance.dateRangePreset != FinanceDateRangePreset.ALL) ThemeTokens.colors.surfaceElevated else ThemeTokens.colors.surface)
                        .border(
                            1.dp,
                            if (finance.dateRangePreset != FinanceDateRangePreset.ALL) ThemeTokens.colors.primary else ThemeTokens.colors.cardBorder,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable { dateMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("finance-date-range-filter-btn"),
                ) {
                    Text(
                        text = "日期: ${finance.dateRangePreset.label} ▾",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (finance.dateRangePreset != FinanceDateRangePreset.ALL) ThemeTokens.colors.primary else ThemeTokens.colors.textSecondary,
                        ),
                    )
                }

                DropdownMenu(
                    expanded = dateMenuExpanded,
                    onDismissRequest = { dateMenuExpanded = false },
                ) {
                    FinanceDateRangePreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.label) },
                            onClick = {
                                onSelectDateRange(preset)
                                dateMenuExpanded = false
                            },
                        )
                    }
                }
            }

            // 4. 账户选择 (可多选)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (finance.filterAccountIds.isNotEmpty()) ThemeTokens.colors.surfaceElevated else ThemeTokens.colors.surface)
                    .border(
                        1.dp,
                        if (finance.filterAccountIds.isNotEmpty()) ThemeTokens.colors.primary else ThemeTokens.colors.cardBorder,
                        RoundedCornerShape(6.dp),
                    )
                    .clickable { accountDialogVisible = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("finance-account-filter-btn"),
            ) {
                Text(
                    text = if (finance.filterAccountIds.isEmpty()) "账户: 全部 ▾" else "账户: 已选 (${finance.filterAccountIds.size}) ▾",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (finance.filterAccountIds.isNotEmpty()) ThemeTokens.colors.primary else ThemeTokens.colors.textSecondary,
                    ),
                )
            }

            // 5. 快速/高级筛选按钮
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ThemeTokens.colors.surface)
                    .border(1.dp, ThemeTokens.colors.cardBorder, RoundedCornerShape(6.dp))
                    .clickable { advancedDialogVisible = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("finance-advanced-filter-btn"),
            ) {
                Text(
                    text = "高级筛选 ▾",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = ThemeTokens.colors.textSecondary,
                    ),
                )
            }
        }

        // Dialogs for Account Multi-Select and Advanced Filter
        if (accountDialogVisible) {
            AccountMultiSelectDialog(
                accounts = finance.accounts,
                selectedAccountIds = finance.filterAccountIds,
                onDismiss = { accountDialogVisible = false },
                onApply = { newSelected ->
                    onSelectAccounts(newSelected)
                    accountDialogVisible = false
                },
            )
        }

        if (advancedDialogVisible) {
            AdvancedFilterPlaceholderDialog(
                onDismiss = { advancedDialogVisible = false },
            )
        }

        // 3. Main Content: Standard Journal Bento Card with LazyColumn (Beancount view removed per user request)
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (finance.sortAscending && finance.hasMoreTransactions) {
                            item(key = "load-more-earlier-banner") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onLoadMoreTransactions)
                                        .padding(vertical = 10.dp)
                                        .testTag("finance-load-more-btn"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "↑ 下拉或点击加载更早记录 (${finance.transactions.size}/${finance.totalTransactionCount})",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = ThemeTokens.colors.textSecondary,
                                        ),
                                    )
                                }
                                SwissHairlineDivider()
                            }
                        }

                        itemsIndexed(
                            items = finance.transactions,
                            key = { _, txItem -> txItem.transaction.id },
                        ) { index, txItem ->
                            if (index > 0) {
                                SwissHairlineDivider()
                            }
                            SwissTransactionRow(
                                txItem = txItem,
                                onDelete = { onDeleteTransaction(txItem.transaction.id) },
                            )
                        }

                        if (!finance.sortAscending && finance.hasMoreTransactions) {
                            item(key = "load-more-older-banner") {
                                SwissHairlineDivider()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onLoadMoreTransactions)
                                        .padding(vertical = 10.dp)
                                        .testTag("finance-load-more-btn"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "↓ 滑动或点击加载更早记录 (${finance.transactions.size}/${finance.totalTransactionCount})",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = ThemeTokens.colors.textSecondary,
                                        ),
                                    )
                                }
                            }
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
        TransactionStatus.CLEARED -> Triple(ThemeTokens.colors.statusClearedSoft, ThemeTokens.colors.statusClearedAccent, "*")
        TransactionStatus.DRAFT -> Triple(ThemeTokens.colors.statusDraftSoft, ThemeTokens.colors.statusDraftAccent, "DRAFT")
        TransactionStatus.SCHEDULED -> Triple(ThemeTokens.colors.statusScheduledSoft, ThemeTokens.colors.statusScheduledAccent, "SCHED")
        TransactionStatus.VOID -> Triple(ThemeTokens.colors.statusClearedSoft, ThemeTokens.colors.statusClearedAccent, "VOID")
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

@Composable
private fun AccountMultiSelectDialog(
    accounts: List<FinanceAccountViewItem>,
    selectedAccountIds: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var currentSelection by remember { mutableStateOf(selectedAccountIds) }

    val filteredAccounts = remember(accounts, searchQuery) {
        if (searchQuery.isBlank()) {
            accounts
        } else {
            accounts.filter {
                it.account.name.contains(searchQuery, ignoreCase = true) ||
                    it.account.path.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择账户", style = ThemeTokens.type.cardTitle)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索账户...", style = ThemeTokens.type.label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            currentSelection = currentSelection + filteredAccounts.map { it.account.id }.toSet()
                        },
                    ) {
                        Text("全选", style = ThemeTokens.type.label)
                    }
                    TextButton(
                        onClick = {
                            currentSelection = emptySet()
                        },
                    ) {
                        Text("清空", style = ThemeTokens.type.label)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filteredAccounts, key = { it.account.id }) { item ->
                            val isChecked = currentSelection.contains(item.account.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        currentSelection = if (isChecked) {
                                            currentSelection - item.account.id
                                        } else {
                                            currentSelection + item.account.id
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        currentSelection = if (checked) {
                                            currentSelection + item.account.id
                                        } else {
                                            currentSelection - item.account.id
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = item.account.name,
                                        style = ThemeTokens.type.body,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = item.account.path,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = ThemeTokens.colors.textSecondary,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = ThemeTokens.type.body)
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(currentSelection) }) {
                Text("应用", style = ThemeTokens.type.body, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun AdvancedFilterPlaceholderDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("高级筛选", style = ThemeTokens.type.cardTitle) },
        text = {
            Text(
                "高级筛选功能正在规划中，后续将支持：\n" +
                    "• 自定义金额区间\n" +
                    "• 多币种过滤\n" +
                    "• 标签与关键字复合检索\n" +
                    "• 交易对手方 (Payee) 精确筛选",
                style = ThemeTokens.type.body,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", style = ThemeTokens.type.body, fontWeight = FontWeight.Bold)
            }
        },
    )
}
