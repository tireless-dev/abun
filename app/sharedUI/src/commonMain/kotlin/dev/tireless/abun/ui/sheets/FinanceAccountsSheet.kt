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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.Trash2
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.DEFAULT_FIVE_FINANCE_ACCOUNT_IDS
import dev.tireless.abun.app.FinanceAccountViewItem
import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.FinanceCurrencies
import dev.tireless.abun.finance.LiquidTier
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FinanceAccountsSheet(
    state: AppUiState,
    onDismiss: () -> Unit,
    onAutoPad: (accountId: String, actualBalanceMinor: Long) -> Unit,
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
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        FinanceAccountsSheetContent(
            state = state,
            onDismiss = onDismiss,
            onAutoPad = onAutoPad,
            onCreateAccount = onCreateAccount,
            onDeleteAccount = onDeleteAccount,
        )
    }
}

@Composable
fun FinanceAccountsSheetContent(
    state: AppUiState,
    onDismiss: () -> Unit,
    onAutoPad: (accountId: String, actualBalanceMinor: Long) -> Unit,
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
) {
    val accounts = state.financeView.accounts
    var isCreatingAccount by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ThemeTokens.spacing.lgDp)
            .verticalScroll(rememberScrollState())
            .testTag("finance-accounts-sheet-content"),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Column {
            Text("Accounts & Reconciliation", style = ThemeTokens.type.sectionTitle)
            Text("${accounts.size} active accounts", style = ThemeTokens.type.label, color = ThemeTokens.colors.textSecondary)
        }

        OutlinedButton(
            onClick = { isCreatingAccount = !isCreatingAccount },
            shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
            modifier = Modifier.testTag("finance-add-account-toggle-btn"),
        ) {
            Icon(imageVector = Lucide.Plus, contentDescription = null, modifier = Modifier.padding(end = ThemeTokens.spacing.xsDp))
            Text(if (isCreatingAccount) "Cancel" else "Add Account", style = ThemeTokens.type.label)
        }
    }

    // New Account Form
    if (isCreatingAccount) {
        CreateAccountCard(
            onCreate = { path, name, category, currency, includeInNetWorth, isPassThrough, liquidTier ->
                onCreateAccount(path, name, category, currency, includeInNetWorth, isPassThrough, liquidTier)
                isCreatingAccount = false
            },
        )
    }

    // Accounts Grouped by Category
    AccountCategory.entries.forEach { category ->
        val categoryAccounts = accounts.filter { it.account.category == category }
        if (categoryAccounts.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
            ) {
                Text(
                    text = category.name.lowercase().replaceFirstChar(Char::titlecase) + "s",
                    style = ThemeTokens.type.cardTitle,
                    color = ThemeTokens.colors.textPrimary,
                    modifier = Modifier.padding(top = ThemeTokens.spacing.smDp),
                )

                categoryAccounts.forEach { accItem ->
                    AccountCardItem(
                        accItem = accItem,
                        onAutoPad = onAutoPad,
                        onDelete = { onDeleteAccount(accItem.account.id) },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun AccountCardItem(
    accItem: FinanceAccountViewItem,
    onAutoPad: (accountId: String, actualBalanceMinor: Long) -> Unit,
    onDelete: () -> Unit,
) {
    var isPadExpanded by remember { mutableStateOf(false) }
    var actualBalanceText by remember { mutableStateOf("") }
    val acc = accItem.account

    EditorialCard(
        testTag = "finance-account-item-${acc.id}",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(acc.name, style = ThemeTokens.type.cardTitle)
                Text(acc.path, style = ThemeTokens.type.label, color = ThemeTokens.colors.textSecondary)

                Row(
                    modifier = Modifier.padding(top = ThemeTokens.spacing.xsDp),
                    horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
                ) {
                    if (acc.liquidTier == LiquidTier.LIQUID) {
                        AccountBadge("Liquid Runway", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    }
                    if (acc.isPassThrough) {
                        AccountBadge("Pass-Through", Color(0xFFFFF3E0), Color(0xFFE65100))
                    }
                    if (acc.includeInNetWorth) {
                        AccountBadge("Net Worth", ThemeTokens.colors.surfaceMuted, ThemeTokens.colors.textSecondary)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = accItem.formattedBalance,
                    style = ThemeTokens.type.cardTitle.copy(fontWeight = FontWeight.SemiBold),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { isPadExpanded = !isPadExpanded },
                        shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                        modifier = Modifier.testTag("finance-pad-btn-${acc.id}"),
                    ) {
                        Icon(imageVector = Lucide.Scale, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Auto-Pad", style = ThemeTokens.type.label)
                    }

                    if (acc.id !in DEFAULT_FIVE_FINANCE_ACCOUNT_IDS) {
                        IconButton(onClick = onDelete) {
                            Icon(imageVector = Lucide.Trash2, contentDescription = "Delete account", tint = ThemeTokens.colors.textTertiary)
                        }
                    }
                }
            }
        }

        // Auto-Pad Reconciliation Drawer
        if (isPadExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ThemeTokens.colors.surfaceMuted, RoundedCornerShape(ThemeTokens.radii.smallDp))
                    .border(1.dp, ThemeTokens.colors.border, RoundedCornerShape(ThemeTokens.radii.smallDp))
                    .padding(ThemeTokens.spacing.smDp),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
            ) {
                Text(
                    "Reconcile Account: Enter actual current balance from bank/statement:",
                    style = ThemeTokens.type.label,
                    color = ThemeTokens.colors.textSecondary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = actualBalanceText,
                        onValueChange = { actualBalanceText = it },
                        label = { Text("Actual Balance (${acc.currency})") },
                        modifier = Modifier.weight(1f).testTag("finance-pad-input-${acc.id}"),
                        singleLine = true,
                    )

                    Button(
                        onClick = {
                            val actualMajor = actualBalanceText.toDoubleOrNull() ?: 0.0
                            val actualMinor = FinanceCurrencies.toMinorUnits(actualMajor, acc.currency)
                            onAutoPad(acc.id, actualMinor)
                            isPadExpanded = false
                            actualBalanceText = ""
                        },
                        shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ThemeTokens.colors.primary,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.testTag("finance-pad-confirm-btn-${acc.id}"),
                    ) {
                        Text("Confirm Pad")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountBadge(label: String, bg: Color, textCol: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = label, style = ThemeTokens.type.label, color = textCol)
    }
}

@Composable
private fun CreateAccountCard(
    onCreate: (
        path: String,
        name: String,
        category: AccountCategory,
        currency: String,
        includeInNetWorth: Boolean,
        isPassThrough: Boolean,
        liquidTier: LiquidTier,
    ) -> Unit,
) {
    var path by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(AccountCategory.ASSET) }
    var currency by remember { mutableStateOf("CNY") }
    var includeInNetWorth by remember { mutableStateOf(true) }
    var isPassThrough by remember { mutableStateOf(false) }
    var liquidTier by remember { mutableStateOf(LiquidTier.LIQUID) }

    EditorialCard(testTag = "finance-create-account-card") {
        Text("Create New Account", style = ThemeTokens.type.cardTitle)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text("Account Path (e.g. Assets:Bank:CMB)") },
                modifier = Modifier.weight(1.5f).testTag("create-account-path-input"),
                singleLine = true,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display Name") },
                modifier = Modifier.weight(1f).testTag("create-account-name-input"),
                singleLine = true,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            CategoryDropdownField(
                selectedCategory = category,
                onSelectCategory = { category = it },
                modifier = Modifier.weight(1f),
            )

            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.uppercase() },
                label = { Text("Currency") },
                modifier = Modifier.weight(1f).testTag("create-account-currency-input"),
                singleLine = true,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Include in Net Worth", style = ThemeTokens.type.label)
            Switch(checked = includeInNetWorth, onCheckedChange = { includeInNetWorth = it })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pass-Through Account", style = ThemeTokens.type.label)
            Switch(checked = isPassThrough, onCheckedChange = { isPassThrough = it })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = {
                    if (path.isNotBlank() && name.isNotBlank()) {
                        onCreate(path, name, category, currency, includeInNetWorth, isPassThrough, liquidTier)
                    }
                },
                enabled = path.isNotBlank() && name.isNotBlank(),
                shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThemeTokens.colors.primary,
                    contentColor = Color.White,
                ),
                modifier = Modifier.testTag("create-account-submit-btn"),
            ) {
                Text("Save Account")
            }
        }
    }
}

@Composable
private fun CategoryDropdownField(
    selectedCategory: AccountCategory,
    onSelectCategory: (AccountCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedCategory.name.lowercase().replaceFirstChar(Char::titlecase),
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
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
                DropdownMenuItem(
                    text = { Text(cat.name.lowercase().replaceFirstChar(Char::titlecase)) },
                    onClick = {
                        onSelectCategory(cat)
                        expanded = false
                    },
                )
            }
        }
    }
}
