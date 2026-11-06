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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.finance.BudgetScopeType
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BudgetRuleSheet(
    state: AppUiState,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        scopeType: BudgetScopeType,
        accountPrefix: String,
        monthlyLimitMinor: Long,
        currency: String,
        includeInRunway: Boolean,
    ) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ThemeTokens.colors.background,
    ) {
        BudgetRuleSheetContent(
            state = state,
            onDismiss = onDismiss,
            onCreate = onCreate,
        )
    }
}

@Composable
fun BudgetRuleSheetContent(
    state: AppUiState,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        scopeType: BudgetScopeType,
        accountPrefix: String,
        monthlyLimitMinor: Long,
        currency: String,
        includeInRunway: Boolean,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("日常支出护栏") }
    var scopeType by remember { mutableStateOf(BudgetScopeType.GLOBAL_DISCRETIONARY) }
    var accountPrefix by remember { mutableStateOf("Expenses:") }
    var limitYuanText by remember { mutableStateOf("3000") }
    var includeInRunway by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(ThemeTokens.spacing.lgDp)
            .testTag("budget-rule-sheet"),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
    ) {
        Text(
            text = "创建支出护栏预算 (Budget Rule)",
            style = ThemeTokens.type.cardTitle,
            fontWeight = FontWeight.SemiBold,
            color = ThemeTokens.colors.textPrimary,
        )

        Text(
            text = "设定每月可控支出限额，动态评估消费配速并作为90天现金流推演的基础消耗速率。",
            style = ThemeTokens.type.bodyMuted,
            color = ThemeTokens.colors.textSecondary,
        )

        // Rule Name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("护栏名称", style = ThemeTokens.type.label) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("budget-name-input"),
        )

        // Scope Type Selector
        Text(
            text = "预算覆盖范围",
            style = ThemeTokens.type.label,
            color = ThemeTokens.colors.textSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            val isGlobal = scopeType == BudgetScopeType.GLOBAL_DISCRETIONARY
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                    .border(
                        width = 1.dp,
                        color = if (isGlobal) ThemeTokens.colors.primary else ThemeTokens.colors.border,
                        shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                    )
                    .background(if (isGlobal) ThemeTokens.colors.primary.copy(alpha = 0.08f) else ThemeTokens.colors.surface)
                    .clickable { scopeType = BudgetScopeType.GLOBAL_DISCRETIONARY }
                    .padding(ThemeTokens.spacing.mdDp)
                    .testTag("scope-global-btn"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "全局日常支出",
                    style = ThemeTokens.type.body,
                    fontWeight = if (isGlobal) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isGlobal) ThemeTokens.colors.primary else ThemeTokens.colors.textPrimary,
                )
            }

            val isSubtree = scopeType == BudgetScopeType.ACCOUNT_SUBTREE
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                    .border(
                        width = 1.dp,
                        color = if (isSubtree) ThemeTokens.colors.primary else ThemeTokens.colors.border,
                        shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                    )
                    .background(if (isSubtree) ThemeTokens.colors.primary.copy(alpha = 0.08f) else ThemeTokens.colors.surface)
                    .clickable { scopeType = BudgetScopeType.ACCOUNT_SUBTREE }
                    .padding(ThemeTokens.spacing.mdDp)
                    .testTag("scope-subtree-btn"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "特定科目子树",
                    style = ThemeTokens.type.body,
                    fontWeight = if (isSubtree) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSubtree) ThemeTokens.colors.primary else ThemeTokens.colors.textPrimary,
                )
            }
        }

        // Account Prefix (for subtree)
        if (scopeType == BudgetScopeType.ACCOUNT_SUBTREE) {
            OutlinedTextField(
                value = accountPrefix,
                onValueChange = { accountPrefix = it },
                label = { Text("科目路径前缀 (如 Expenses:Food)", style = ThemeTokens.type.label) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budget-prefix-input"),
            )
        }

        // Monthly Limit (CNY)
        OutlinedTextField(
            value = limitYuanText,
            onValueChange = { limitYuanText = it },
            label = { Text("每月额度 (元 ¥)", style = ThemeTokens.type.label) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("budget-limit-input"),
        )

        // Include in Runway burn
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { includeInRunway = !includeInRunway }
                .padding(vertical = ThemeTokens.spacing.xsDp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            Checkbox(
                checked = includeInRunway,
                onCheckedChange = { includeInRunway = it },
                modifier = Modifier.testTag("budget-runway-checkbox"),
            )
            Column {
                Text(
                    text = "计入90天现金流跑道测算",
                    style = ThemeTokens.type.body,
                    fontWeight = FontWeight.Medium,
                    color = ThemeTokens.colors.textPrimary,
                )
                Text(
                    text = "根据剩余预算与天数，将建议日均限额折算为每日生活现金流出。",
                    style = ThemeTokens.type.label,
                    color = ThemeTokens.colors.textSecondary,
                )
            }
        }

        if (errorText != null) {
            Text(
                text = errorText!!,
                style = ThemeTokens.type.label,
                color = ThemeTokens.colors.error,
                modifier = Modifier.testTag("budget-error-msg"),
            )
        }

        Spacer(modifier = Modifier.height(ThemeTokens.spacing.smDp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .testTag("cancel-budget-rule-btn"),
            ) {
                Text("取消", style = ThemeTokens.type.body)
            }
            Button(
                onClick = {
                    val limitNum = limitYuanText.toDoubleOrNull()
                    if (name.isBlank()) {
                        errorText = "请输入预算护栏名称"
                        return@Button
                    }
                    if (limitNum == null || limitNum <= 0.0) {
                        errorText = "请输入有效的月度预算金额"
                        return@Button
                    }
                    val limitMinor = (limitNum * 100.0).toLong()
                    onCreate(
                        name.trim(),
                        scopeType,
                        accountPrefix.trim(),
                        limitMinor,
                        "CNY",
                        includeInRunway,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("save-budget-rule-btn"),
                colors = ButtonDefaults.buttonColors(containerColor = ThemeTokens.colors.primary),
            ) {
                Text("保存护栏", style = ThemeTokens.type.body)
            }
        }
    }
}
