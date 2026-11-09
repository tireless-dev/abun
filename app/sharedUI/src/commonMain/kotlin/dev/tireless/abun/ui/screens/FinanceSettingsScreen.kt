package dev.tireless.abun.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.DollarSign
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.ShieldAlert
import com.composables.icons.lucide.Trash2
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.DEFAULT_FIVE_FINANCE_ACCOUNT_IDS
import dev.tireless.abun.app.FinanceAccountViewItem
import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.FinanceCurrencies
import dev.tireless.abun.finance.LiquidTier
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.SwissBentoCard
import dev.tireless.abun.ui.SwissCardHeader
import dev.tireless.abun.ui.SwissHairlineDivider
import dev.tireless.abun.ui.SwissMetricData
import dev.tireless.abun.ui.SwissMetricStrip
import dev.tireless.abun.ui.theme.ThemeTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinanceSettingsScreen(
    state: AppUiState,
    onUpdateAccount: (
        id: String,
        path: String,
        name: String,
        category: AccountCategory,
        currency: String,
        includeInNetWorth: Boolean,
        isPassThrough: Boolean,
        passThroughExpenseAccountId: String?,
        liquidTier: LiquidTier,
        statementDay: Int?,
        dueDay: Int?,
        isArchived: Boolean,
    ) -> Unit,
    onCreateAccount: (
        path: String,
        name: String,
        category: AccountCategory,
        currency: String,
        includeInNetWorth: Boolean,
        isPassThrough: Boolean,
        liquidTier: LiquidTier,
    ) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onAutoPad: (accountId: String, actualBalanceMinor: Long) -> Unit,
) {
    val accounts = state.financeView.accounts
    var selectedCategoryFilter by remember { mutableStateOf<AccountCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isCreatingAccount by remember { mutableStateOf(false) }

    val activeAccounts = accounts.filter { !it.account.isArchived }
    val liquidAccounts = accounts.filter {
        it.account.category == AccountCategory.ASSET &&
            it.account.liquidTier == LiquidTier.LIQUID &&
            !it.account.isArchived
    }
    val netWorthAccounts = accounts.filter { it.account.includeInNetWorth && !it.account.isArchived }

    val filteredAccounts = accounts.filter { item ->
        val matchesCategory = selectedCategoryFilter == null || item.account.category == selectedCategoryFilter
        val matchesSearch = searchQuery.isBlank() ||
            item.account.name.contains(searchQuery, ignoreCase = true) ||
            item.account.path.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("finance-settings-screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        // 1. Header & Metric Overview
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "FINANCE SETTINGS · 财务设置",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                    Text(
                        text = "五大类会计账户管理",
                        style = ThemeTokens.type.title,
                    )
                }

                OutlinedButton(
                    onClick = { isCreatingAccount = !isCreatingAccount },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("finance-settings-add-account-btn"),
                ) {
                    Icon(
                        imageVector = Lucide.Plus,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp),
                    )
                    Text(if (isCreatingAccount) "取消新建" else "新建账户", style = ThemeTokens.type.label)
                }
            }

            SwissMetricStrip(
                metrics = listOf(
                    SwissMetricData(
                        label = "ACTIVE ACCOUNTS",
                        value = "${activeAccounts.size} 个",
                        valueColor = ThemeTokens.colors.textPrimary,
                    ),
                    SwissMetricData(
                        label = "LIQUID ASSETS",
                        value = "${liquidAccounts.size} 账户 (${state.financeView.formattedLiquidAssets})",
                        valueColor = ThemeTokens.colors.financeAccent,
                    ),
                    SwissMetricData(
                        label = "NET WORTH SCOPE",
                        value = "${netWorthAccounts.size} 账户 (${state.financeView.formattedNetWorth})",
                        valueColor = ThemeTokens.colors.textPrimary,
                    ),
                ),
            )
        }

        // 2. Inline Create Account Card
        if (isCreatingAccount) {
            CreateAccountInlineCard(
                onCreate = { path, name, category, currency, includeInNetWorth, isPassThrough, liquidTier ->
                    onCreateAccount(path, name, category, currency, includeInNetWorth, isPassThrough, liquidTier)
                    isCreatingAccount = false
                },
                onCancel = { isCreatingAccount = false },
            )
        }

        // 3. Category Filter Tabs & Search
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CategoryFilterChip(
                    label = "全部 (${accounts.size})",
                    isSelected = selectedCategoryFilter == null,
                    onClick = { selectedCategoryFilter = null },
                )
                AccountCategory.entries.forEach { cat ->
                    val count = accounts.count { it.account.category == cat }
                    val cnName = when (cat) {
                        AccountCategory.ASSET -> "资产"
                        AccountCategory.LIABILITY -> "负债"
                        AccountCategory.INCOME -> "收入"
                        AccountCategory.EXPENSE -> "支出"
                        AccountCategory.EQUITY -> "权益"
                    }
                    CategoryFilterChip(
                        label = "$cnName ($count)",
                        isSelected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索账户...", style = ThemeTokens.type.label) },
                singleLine = true,
                modifier = Modifier.width(180.dp).testTag("finance-settings-search-input"),
            )
        }

        // 4. Accounts List Grouped Hierarchically by Category and Sub-Paths
        val activeCategories = if (selectedCategoryFilter != null) {
            listOf(selectedCategoryFilter!!)
        } else {
            AccountCategory.entries.toList()
        }

        activeCategories.forEach { category ->
            val categoryAccounts = filteredAccounts.filter { it.account.category == category }
            if (categoryAccounts.isNotEmpty()) {
                val categoryName = when (category) {
                    AccountCategory.ASSET -> "资产 (Assets)"
                    AccountCategory.LIABILITY -> "负债 (Liabilities)"
                    AccountCategory.INCOME -> "收入 (Income)"
                    AccountCategory.EXPENSE -> "支出 (Expenses)"
                    AccountCategory.EQUITY -> "权益 (Equity)"
                }

                // Group by level-2 sub-path hierarchy (e.g. Current, Growth, Loans, CreditCard)
                val subGroups = categoryAccounts.groupBy { item ->
                    val segments = item.account.path.split(":")
                    if (segments.size > 2) segments[1] else "General"
                }

                SwissBentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "finance-category-section-${category.name.lowercase()}",
                ) {
                    SwissCardHeader(
                        title = categoryName,
                        trailingText = "${categoryAccounts.size} 账户",
                    )

                    subGroups.forEach { (subGroupName, itemsInSubGroup) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (subGroupName != "General") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                ) {
                                    Icon(
                                        imageVector = Lucide.ChevronRight,
                                        contentDescription = null,
                                        tint = ThemeTokens.colors.textSecondary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = subGroupName.uppercase(),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ThemeTokens.colors.financeAccent,
                                        ),
                                    )
                                    Text(
                                        text = "· ${itemsInSubGroup.size} 项",
                                        style = ThemeTokens.type.bodyMuted,
                                        color = ThemeTokens.colors.textTertiary,
                                    )
                                }
                            }

                            itemsInSubGroup.forEach { accItem ->
                                AccountHierarchyItemRow(
                                    accItem = accItem,
                                    onUpdateAccount = onUpdateAccount,
                                    onDeleteAccount = onDeleteAccount,
                                    onAutoPad = onAutoPad,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun CategoryFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) ThemeTokens.colors.primary else ThemeTokens.colors.surfaceElevated
    val textCol = if (isSelected) Color.White else ThemeTokens.colors.textSecondary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = label, style = ThemeTokens.type.label, color = textCol)
    }
}

@Composable
private fun AccountHierarchyItemRow(
    accItem: FinanceAccountViewItem,
    onUpdateAccount: (
        id: String,
        path: String,
        name: String,
        category: AccountCategory,
        currency: String,
        includeInNetWorth: Boolean,
        isPassThrough: Boolean,
        passThroughExpenseAccountId: String?,
        liquidTier: LiquidTier,
        statementDay: Int?,
        dueDay: Int?,
        isArchived: Boolean,
    ) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onAutoPad: (accountId: String, actualBalanceMinor: Long) -> Unit,
) {
    val acc = accItem.account
    var isPadExpanded by remember { mutableStateOf(false) }
    var actualBalanceInput by remember { mutableStateOf("") }

    val segments = acc.path.split(":")
    val indentLevel = (segments.size - 2).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (acc.isArchived) ThemeTokens.colors.surfaceMuted.copy(alpha = 0.5f) else ThemeTokens.colors.surface)
            .border(1.dp, ThemeTokens.colors.border, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag("finance-account-row-${acc.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Path & Name with indentation
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = (indentLevel * 12).dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = acc.name,
                        style = ThemeTokens.type.cardTitle.copy(fontWeight = FontWeight.Bold),
                    )
                    if (acc.isArchived) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ThemeTokens.colors.surfaceMuted)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Text("已关闭/归档", style = TextStyle(fontSize = 10.sp, color = ThemeTokens.colors.textTertiary))
                        }
                    }
                }

                Text(
                    text = acc.path,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ThemeTokens.colors.textSecondary,
                    ),
                )
            }

            // Balance
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = accItem.formattedBalance,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (accItem.balanceMinor < 0) ThemeTokens.colors.error else ThemeTokens.colors.textPrimary,
                    ),
                )
            }
        }

        // Quick Setting Toggles (Liquidity, Net Worth, Archive, Auto-Pad)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 1. Asset Account Role Segment: [🟢 流动主力] [🟡 沉淀资产] [⚡ 转出即消费]
                if (acc.category == AccountCategory.ASSET) {
                    val isOperating = !acc.isPassThrough && acc.liquidTier == LiquidTier.LIQUID
                    val isSediment = !acc.isPassThrough && acc.liquidTier != LiquidTier.LIQUID
                    val isPass = acc.isPassThrough

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ThemeTokens.colors.surfaceMuted)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 🟢 流动主力
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isOperating) ThemeTokens.colors.financeSoft else Color.Transparent)
                                .clickable {
                                    onUpdateAccount(
                                        acc.id, acc.path, acc.name, acc.category, acc.currency,
                                        true, false, acc.passThroughExpenseAccountId,
                                        LiquidTier.LIQUID, acc.statementDay, acc.dueDay, acc.isArchived,
                                    )
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("role-operating-${acc.id}"),
                        ) {
                            Text(
                                text = "🟢 流动主力",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = if (isOperating) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isOperating) ThemeTokens.colors.financeAccent else ThemeTokens.colors.textTertiary,
                                ),
                            )
                        }

                        // 🟡 沉淀资产
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSediment) ThemeTokens.colors.surfaceElevated else Color.Transparent)
                                .clickable {
                                    onUpdateAccount(
                                        acc.id, acc.path, acc.name, acc.category, acc.currency,
                                        acc.includeInNetWorth, false, acc.passThroughExpenseAccountId,
                                        LiquidTier.NONE, acc.statementDay, acc.dueDay, acc.isArchived,
                                    )
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("role-sediment-${acc.id}"),
                        ) {
                            Text(
                                text = "🟡 沉淀资产",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSediment) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSediment) ThemeTokens.colors.textPrimary else ThemeTokens.colors.textTertiary,
                                ),
                            )
                        }

                        // ⚡ 转出即消费
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isPass) ThemeTokens.colors.passThroughSoft else Color.Transparent)
                                .clickable {
                                    onUpdateAccount(
                                        acc.id, acc.path, acc.name, acc.category, acc.currency,
                                        false, true, acc.passThroughExpenseAccountId,
                                        LiquidTier.NONE, acc.statementDay, acc.dueDay, acc.isArchived,
                                    )
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("toggle-pass-through-${acc.id}"),
                        ) {
                            Text(
                                text = "⚡ 转出即消费",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = if (isPass) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isPass) ThemeTokens.colors.passThroughAccent else ThemeTokens.colors.textTertiary,
                                ),
                            )
                        }
                    }

                    // Secondary Net Worth toggle ONLY for Sediment (沉淀资产)
                    if (isSediment) {
                        val incNetWorth = acc.includeInNetWorth && !acc.isArchived
                        val pillBg = if (incNetWorth) ThemeTokens.colors.surfaceElevated else ThemeTokens.colors.surfaceMuted
                        val textCol = if (incNetWorth) ThemeTokens.colors.textPrimary else ThemeTokens.colors.textTertiary

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(pillBg)
                                .clickable {
                                    onUpdateAccount(
                                        acc.id, acc.path, acc.name, acc.category, acc.currency,
                                        !acc.includeInNetWorth, false, acc.passThroughExpenseAccountId,
                                        LiquidTier.NONE, acc.statementDay, acc.dueDay, acc.isArchived,
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("toggle-net-worth-${acc.id}"),
                        ) {
                            Text(
                                text = if (incNetWorth) "✓ 计入净资产" else "✕ 排除净资产",
                                style = TextStyle(fontSize = 11.sp, color = textCol),
                            )
                        }
                    }
                }

                // 2. Liability Account Role Segment: [🔴 商业负债] [⚪ 个人借款]
                if (acc.category == AccountCategory.LIABILITY) {
                    val isCommercial = acc.includeInNetWorth && !acc.isArchived
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ThemeTokens.colors.surfaceMuted)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 🔴 商业负债
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isCommercial) ThemeTokens.colors.surfaceElevated else Color.Transparent)
                                .clickable {
                                    onUpdateAccount(
                                        acc.id, acc.path, acc.name, acc.category, acc.currency,
                                        true, false, acc.passThroughExpenseAccountId,
                                        acc.liquidTier, acc.statementDay, acc.dueDay, acc.isArchived,
                                    )
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("role-commercial-${acc.id}"),
                        ) {
                            Text(
                                text = "🔴 商业负债",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = if (isCommercial) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCommercial) ThemeTokens.colors.error else ThemeTokens.colors.textTertiary,
                                ),
                            )
                        }

                        // ⚪ 个人借款
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (!isCommercial) ThemeTokens.colors.surfaceElevated else Color.Transparent)
                                .clickable {
                                    onUpdateAccount(
                                        acc.id, acc.path, acc.name, acc.category, acc.currency,
                                        false, false, acc.passThroughExpenseAccountId,
                                        acc.liquidTier, acc.statementDay, acc.dueDay, acc.isArchived,
                                    )
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("role-informal-${acc.id}"),
                        ) {
                            Text(
                                text = "⚪ 个人借款",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = if (!isCommercial) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!isCommercial) ThemeTokens.colors.textPrimary else ThemeTokens.colors.textTertiary,
                                ),
                            )
                        }
                    }
                }

                // 3. Archive Toggle
                val archiveText = if (acc.isArchived) "已归档" else "正常"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ThemeTokens.colors.surfaceMuted)
                        .clickable {
                            onUpdateAccount(
                                acc.id, acc.path, acc.name, acc.category, acc.currency,
                                acc.includeInNetWorth, acc.isPassThrough, acc.passThroughExpenseAccountId,
                                acc.liquidTier, acc.statementDay, acc.dueDay, !acc.isArchived,
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(text = archiveText, style = TextStyle(fontSize = 11.sp, color = ThemeTokens.colors.textTertiary))
                }
            }

            // Action Buttons (Auto-Pad & Delete)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { isPadExpanded = !isPadExpanded },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("finance-settings-pad-btn-${acc.id}"),
                ) {
                    Icon(imageVector = Lucide.Scale, contentDescription = null, modifier = Modifier.size(13.dp).padding(end = 3.dp))
                    Text("对账", style = TextStyle(fontSize = 11.sp))
                }

                if (acc.id !in DEFAULT_FIVE_FINANCE_ACCOUNT_IDS) {
                    IconButton(
                        onClick = { onDeleteAccount(acc.id) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(imageVector = Lucide.Trash2, contentDescription = "Delete", tint = ThemeTokens.colors.textTertiary, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        // Auto-Pad Reconciliation Drawer
        if (isPadExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ThemeTokens.colors.surfaceMuted, RoundedCornerShape(6.dp))
                    .border(1.dp, ThemeTokens.colors.border, RoundedCornerShape(6.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "对账平账：输入银行/账单真实当前余额，系统将自动生成差额平账交易：",
                    style = ThemeTokens.type.bodyMuted,
                    color = ThemeTokens.colors.textSecondary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = actualBalanceInput,
                        onValueChange = { actualBalanceInput = it },
                        label = { Text("真实余额 (${acc.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("finance-pad-input-${acc.id}"),
                        singleLine = true,
                    )

                    Button(
                        onClick = {
                            val minor = runCatching {
                                FinanceCurrencies.toMinorUnitsFromDecimalString(actualBalanceInput, acc.currency)
                            }.getOrNull()
                            if (minor != null) {
                                onAutoPad(acc.id, minor)
                                isPadExpanded = false
                                actualBalanceInput = ""
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("finance-pad-submit-${acc.id}"),
                    ) {
                        Text("平账", style = ThemeTokens.type.label)
                    }
                }
            }
        }
    }
}

private enum class AssetRoleChoice { OPERATING, SEDIMENT, PASS_THROUGH }
private enum class LiabilityRoleChoice { COMMERCIAL, INFORMAL }

@Composable
private fun CreateAccountInlineCard(
    onCreate: (
        path: String,
        name: String,
        category: AccountCategory,
        currency: String,
        includeInNetWorth: Boolean,
        isPassThrough: Boolean,
        liquidTier: LiquidTier,
    ) -> Unit,
    onCancel: () -> Unit,
) {
    var path by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(AccountCategory.ASSET) }
    var currency by remember { mutableStateOf("CNY") }
    var assetRole by remember { mutableStateOf(AssetRoleChoice.OPERATING) }
    var sedimentIncludeNetWorth by remember { mutableStateOf(true) }
    var liabilityRole by remember { mutableStateOf(LiabilityRoleChoice.COMMERCIAL) }

    EditorialCard(testTag = "finance-settings-create-account-card") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("新建会计账户", style = ThemeTokens.type.cardTitle)
            OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(6.dp)) {
                Text("取消", style = ThemeTokens.type.label)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text("账户路径 (如 Assets:Current:CMB)") },
                modifier = Modifier.weight(1.5f).testTag("settings-create-account-path"),
                singleLine = true,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("账户名称 (如 招商银行)") },
                modifier = Modifier.weight(1f).testTag("settings-create-account-name"),
                singleLine = true,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryDropdown(
                selectedCategory = category,
                onSelectCategory = { category = it },
                modifier = Modifier.weight(1f),
            )

            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.uppercase() },
                label = { Text("币种 (CNY, USD, USDT等)") },
                modifier = Modifier.weight(1f).testTag("settings-create-account-currency"),
                singleLine = true,
            )
        }

        // Role-based configuration based on category
        if (category == AccountCategory.ASSET) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("资产定位", style = ThemeTokens.type.label)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ThemeTokens.colors.surfaceMuted)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (assetRole == AssetRoleChoice.OPERATING) ThemeTokens.colors.financeSoft else Color.Transparent)
                            .clickable { assetRole = AssetRoleChoice.OPERATING }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "🟢 流动主力",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = if (assetRole == AssetRoleChoice.OPERATING) FontWeight.Bold else FontWeight.Normal,
                                color = if (assetRole == AssetRoleChoice.OPERATING) ThemeTokens.colors.financeAccent else ThemeTokens.colors.textTertiary,
                            ),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (assetRole == AssetRoleChoice.SEDIMENT) ThemeTokens.colors.surfaceElevated else Color.Transparent)
                            .clickable { assetRole = AssetRoleChoice.SEDIMENT }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "🟡 沉淀资产",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = if (assetRole == AssetRoleChoice.SEDIMENT) FontWeight.Bold else FontWeight.Normal,
                                color = if (assetRole == AssetRoleChoice.SEDIMENT) ThemeTokens.colors.textPrimary else ThemeTokens.colors.textTertiary,
                            ),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (assetRole == AssetRoleChoice.PASS_THROUGH) ThemeTokens.colors.passThroughSoft else Color.Transparent)
                            .clickable { assetRole = AssetRoleChoice.PASS_THROUGH }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "⚡ 转出即消费",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = if (assetRole == AssetRoleChoice.PASS_THROUGH) FontWeight.Bold else FontWeight.Normal,
                                color = if (assetRole == AssetRoleChoice.PASS_THROUGH) ThemeTokens.colors.passThroughAccent else ThemeTokens.colors.textTertiary,
                            ),
                        )
                    }
                }
            }

            if (assetRole == AssetRoleChoice.SEDIMENT) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("计入净资产统计 (Net Worth)", style = ThemeTokens.type.label)
                    Switch(checked = sedimentIncludeNetWorth, onCheckedChange = { sedimentIncludeNetWorth = it })
                }
            }
        }

        if (category == AccountCategory.LIABILITY) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("负债性质", style = ThemeTokens.type.label)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ThemeTokens.colors.surfaceMuted)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (liabilityRole == LiabilityRoleChoice.COMMERCIAL) ThemeTokens.colors.surfaceElevated else Color.Transparent)
                            .clickable { liabilityRole = LiabilityRoleChoice.COMMERCIAL }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "🔴 商业负债",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = if (liabilityRole == LiabilityRoleChoice.COMMERCIAL) FontWeight.Bold else FontWeight.Normal,
                                color = if (liabilityRole == LiabilityRoleChoice.COMMERCIAL) ThemeTokens.colors.error else ThemeTokens.colors.textTertiary,
                            ),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (liabilityRole == LiabilityRoleChoice.INFORMAL) ThemeTokens.colors.surfaceElevated else Color.Transparent)
                            .clickable { liabilityRole = LiabilityRoleChoice.INFORMAL }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "⚪ 个人借款",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = if (liabilityRole == LiabilityRoleChoice.INFORMAL) FontWeight.Bold else FontWeight.Normal,
                                color = if (liabilityRole == LiabilityRoleChoice.INFORMAL) ThemeTokens.colors.textPrimary else ThemeTokens.colors.textTertiary,
                            ),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = {
                    if (path.isNotBlank() && name.isNotBlank()) {
                        val (incNetWorth, isPass, tier) = when (category) {
                            AccountCategory.ASSET -> when (assetRole) {
                                AssetRoleChoice.OPERATING -> Triple(true, false, LiquidTier.LIQUID)
                                AssetRoleChoice.SEDIMENT -> Triple(sedimentIncludeNetWorth, false, LiquidTier.NONE)
                                AssetRoleChoice.PASS_THROUGH -> Triple(false, true, LiquidTier.NONE)
                            }
                            AccountCategory.LIABILITY -> when (liabilityRole) {
                                LiabilityRoleChoice.COMMERCIAL -> Triple(true, false, LiquidTier.LIQUID)
                                LiabilityRoleChoice.INFORMAL -> Triple(false, false, LiquidTier.NONE)
                            }
                            else -> Triple(false, false, LiquidTier.NONE)
                        }
                        onCreate(path, name, category, currency, incNetWorth, isPass, tier)
                    }
                },
                enabled = path.isNotBlank() && name.isNotBlank(),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("settings-create-account-submit"),
            ) {
                Text("保存账户")
            }
        }
    }
}

@Composable
private fun CategoryDropdown(
    selectedCategory: AccountCategory,
    onSelectCategory: (AccountCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = when (selectedCategory) {
                AccountCategory.ASSET -> "资产 (Asset)"
                AccountCategory.LIABILITY -> "负债 (Liability)"
                AccountCategory.INCOME -> "收入 (Income)"
                AccountCategory.EXPENSE -> "支出 (Expense)"
                AccountCategory.EQUITY -> "权益 (Equity)"
            },
            onValueChange = {},
            readOnly = true,
            label = { Text("分类") },
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
            AccountCategory.entries.forEach { cat ->
                val label = when (cat) {
                    AccountCategory.ASSET -> "资产 (Asset)"
                    AccountCategory.LIABILITY -> "负债 (Liability)"
                    AccountCategory.INCOME -> "收入 (Income)"
                    AccountCategory.EXPENSE -> "支出 (Expense)"
                    AccountCategory.EQUITY -> "权益 (Equity)"
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelectCategory(cat)
                        expanded = false
                    },
                )
            }
        }
    }
}
