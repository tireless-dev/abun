package dev.tireless.abun.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Wallet
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.BudgetEvaluationViewItem
import dev.tireless.abun.finance.BudgetScopeType
import dev.tireless.abun.finance.FinanceCurrencies
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.components.MetricRow
import dev.tireless.abun.ui.components.SectionHeader
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun FinanceCockpitScreen(
    state: AppUiState,
    onOpenLedger: () -> Unit,
    onOpenClearingHub: () -> Unit,
    onOpenCreateTransaction: () -> Unit,
    onOpenCreateBudgetRule: () -> Unit,
    onSelectHorizon: (Int) -> Unit,
    onDeleteBudgetRule: (String) -> Unit,
) {
    val finance = state.financeView
    val runway = finance.currentRunwayProjection
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(ThemeTokens.spacing.lgDp)
            .testTag("finance-cockpit-screen"),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.lgDp),
    ) {
        // Top Subtab Navigation & Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Subtabs: [总览 Cockpit] [明细 Ledger] [清算与分期 Hub]
            Row(
                horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                        .background(ThemeTokens.colors.primary.copy(alpha = 0.12f))
                        .padding(horizontal = ThemeTokens.spacing.mdDp, vertical = ThemeTokens.spacing.xsDp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "总览",
                        style = ThemeTokens.type.label,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTokens.colors.primary,
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

            // Quick Actions
            Row(horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
                OutlinedButton(
                    onClick = onOpenCreateBudgetRule,
                    modifier = Modifier.testTag("cockpit-create-budget-btn"),
                ) {
                    Icon(Lucide.Plus, contentDescription = null)
                    Spacer(Modifier.width(ThemeTokens.spacing.xsDp))
                    Text("护栏", style = ThemeTokens.type.label)
                }
                Button(
                    onClick = onOpenCreateTransaction,
                    modifier = Modifier.testTag("cockpit-create-tx-btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeTokens.colors.primary),
                ) {
                    Icon(Lucide.Plus, contentDescription = null)
                    Spacer(Modifier.width(ThemeTokens.spacing.xsDp))
                    Text("记账", style = ThemeTokens.type.label)
                }
            }
        }

        // Hero: Safe-to-Spend & Runway Radar
        EditorialCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("safe-to-spend-card"),
        ) {
            Column(
                modifier = Modifier.padding(ThemeTokens.spacing.mdDp),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "真实可支配现金 (Safe-to-Spend)",
                        style = ThemeTokens.type.label,
                        color = ThemeTokens.colors.textSecondary,
                    )
                    Text(
                        text = "${finance.runwayHorizonDays}天安全窗口",
                        style = ThemeTokens.type.label,
                        color = ThemeTokens.colors.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Text(
                    text = finance.formattedSafeToSpend,
                    style = ThemeTokens.type.display,
                    color = ThemeTokens.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("safe-to-spend-amount"),
                )

                Text(
                    text = "基于流动资金，扣除到期信用卡账单、贷款分期本息及日常生活护栏后的安全开支额度。",
                    style = ThemeTokens.type.bodyMuted,
                    color = ThemeTokens.colors.textSecondary,
                )
            }
        }

        // Runway Radar Banner
        if (runway != null) {
            val isDeficit = runway.deficitDate != null
            val minTroughFormatted = FinanceCurrencies.format(runway.minimumTroughMinor, "CNY")
            val bannerBorderColor = if (isDeficit) ThemeTokens.colors.error else ThemeTokens.colors.success
            val bannerBg = if (isDeficit) ThemeTokens.colors.error.copy(alpha = 0.08f) else ThemeTokens.colors.success.copy(alpha = 0.08f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ThemeTokens.radii.mediumDp))
                    .border(1.dp, bannerBorderColor, RoundedCornerShape(ThemeTokens.radii.mediumDp))
                    .background(bannerBg)
                    .padding(ThemeTokens.spacing.mdDp)
                    .testTag("runway-radar-banner"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
                    ) {
                        Text(
                            text = if (isDeficit) "⚠️ 预测到资金赤字缺口" else "✅ 现金流跑道充足健康",
                            style = ThemeTokens.type.cardTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDeficit) ThemeTokens.colors.error else ThemeTokens.colors.textPrimary,
                        )
                    }
                    Text(
                        text = if (isDeficit) {
                            "预计将于 ${runway.deficitDate} 出现赤字。推演最低现金槽点为 $minTroughFormatted (发生于 ${runway.minimumTroughDate})，安全护栏还剩 ${runway.daysOfRunway} 天。"
                        } else {
                            "在未来 ${finance.runwayHorizonDays} 天内无断流风险。推演最低现金槽点为 $minTroughFormatted (发生于 ${runway.minimumTroughDate})。"
                        },
                        style = ThemeTokens.type.bodyMuted,
                        color = ThemeTokens.colors.textPrimary,
                    )
                }
            }
        }

        // High Level Metrics
        MetricRow(
            listOf(
                "流动资产" to finance.formattedLiquidAssets,
                "净资产" to finance.formattedNetWorth,
                "安全跑道" to "${runway?.daysOfRunway ?: 90} 天",
            ),
        )

        // Guardrail Pacing Budget Section
        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
            SectionHeader("PACING", "支出护栏配速 (Guardrail Pacing)")

            if (finance.budgetEvaluations.isEmpty()) {
                EditorialCard(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ThemeTokens.spacing.lgDp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无支出护栏，点击右上角「+ 护栏」添加月度限额",
                            style = ThemeTokens.type.bodyMuted,
                            color = ThemeTokens.colors.textSecondary,
                        )
                    }
                }
            } else {
                finance.budgetEvaluations.forEach { item ->
                    BudgetEvaluationCard(
                        item = item,
                        onDelete = { onDeleteBudgetRule(item.rule.id) },
                    )
                }
            }
        }

        // Cashflow Runway Timeline Section
        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader("RUNWAY", "现金流推演日程 (Cashflow Agenda)")

                // 30 / 90 Days Toggle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val is30 = finance.runwayHorizonDays == 30
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                            .background(if (is30) ThemeTokens.colors.primary else ThemeTokens.colors.surfaceMuted)
                            .clickable { onSelectHorizon(30) }
                            .padding(horizontal = ThemeTokens.spacing.smDp, vertical = ThemeTokens.spacing.xsDp)
                            .testTag("runway-horizon-30"),
                    ) {
                        Text(
                            text = "30天",
                            style = ThemeTokens.type.label,
                            color = if (is30) Color.White else ThemeTokens.colors.textSecondary,
                            fontWeight = if (is30) FontWeight.Bold else FontWeight.Normal,
                        )
                    }

                    val is90 = finance.runwayHorizonDays == 90
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                            .background(if (is90) ThemeTokens.colors.primary else ThemeTokens.colors.surfaceMuted)
                            .clickable { onSelectHorizon(90) }
                            .padding(horizontal = ThemeTokens.spacing.smDp, vertical = ThemeTokens.spacing.xsDp)
                            .testTag("runway-horizon-90"),
                    ) {
                        Text(
                            text = "90天",
                            style = ThemeTokens.type.label,
                            color = if (is90) Color.White else ThemeTokens.colors.textSecondary,
                            fontWeight = if (is90) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            if (runway != null) {
                val milestonePoints = runway.dailyPoints.filter { p ->
                    p.dayIndex == 0 ||
                        p.scheduledInflowsMinor > 0L ||
                        p.statementObligationsMinor > 0L ||
                        p.scheduledOutflowsMinor > 0L ||
                        p.isDeficit ||
                        p.date == runway.minimumTroughDate ||
                        p.dayIndex == runway.dailyPoints.lastIndex
                }.distinctBy { it.date }

                Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
                    milestonePoints.forEach { point ->
                        val isDeficitPoint = point.isDeficit
                        val isTrough = point.date == runway.minimumTroughDate
                        val balanceFormatted = FinanceCurrencies.format(point.projectedLiquidCashMinor, "CNY")

                        EditorialCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("runway-point-${point.date}"),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(ThemeTokens.spacing.mdDp),
                                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = point.date,
                                            style = ThemeTokens.type.body,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ThemeTokens.colors.textPrimary,
                                        )
                                        Text(
                                            text = "D+${point.dayIndex}",
                                            style = ThemeTokens.type.label,
                                            color = ThemeTokens.colors.textSecondary,
                                        )
                                        if (isTrough) {
                                            Text(
                                                text = "最低槽点",
                                                style = ThemeTokens.type.label,
                                                color = ThemeTokens.colors.warning,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                        if (isDeficitPoint) {
                                            Text(
                                                text = "缺口赤字",
                                                style = ThemeTokens.type.label,
                                                color = ThemeTokens.colors.error,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }

                                    Text(
                                        text = balanceFormatted,
                                        style = ThemeTokens.type.body,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDeficitPoint) ThemeTokens.colors.error else ThemeTokens.colors.textPrimary,
                                    )
                                }

                                // Inflows, Obligations, Outflows, Burn
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (point.scheduledInflowsMinor > 0L) {
                                        Text(
                                            text = "+${FinanceCurrencies.format(point.scheduledInflowsMinor, "CNY")} 预计入账",
                                            style = ThemeTokens.type.label,
                                            color = ThemeTokens.colors.success,
                                        )
                                    }
                                    if (point.statementObligationsMinor > 0L) {
                                        Text(
                                            text = "-${FinanceCurrencies.format(point.statementObligationsMinor, "CNY")} 账单到期",
                                            style = ThemeTokens.type.label,
                                            color = ThemeTokens.colors.error,
                                        )
                                    }
                                    if (point.scheduledOutflowsMinor > 0L) {
                                        Text(
                                            text = "-${FinanceCurrencies.format(point.scheduledOutflowsMinor, "CNY")} 分期还款",
                                            style = ThemeTokens.type.label,
                                            color = ThemeTokens.colors.warning,
                                        )
                                    }
                                    if (point.dailyDiscretionaryBurnMinor > 0L) {
                                        Text(
                                            text = "-${FinanceCurrencies.format(point.dailyDiscretionaryBurnMinor, "CNY")} 日常生活",
                                            style = ThemeTokens.type.label,
                                            color = ThemeTokens.colors.textSecondary,
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
}

@Composable
private fun BudgetEvaluationCard(
    item: BudgetEvaluationViewItem,
    onDelete: () -> Unit,
) {
    val eval = item.evaluation
    val isGlobal = item.rule.scopeType == BudgetScopeType.GLOBAL_DISCRETIONARY

    EditorialCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("budget-card-${item.rule.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.mdDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            // Header: Name, Scope badge, Limit, Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.rule.name,
                        style = ThemeTokens.type.cardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = ThemeTokens.colors.textPrimary,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                            .background(ThemeTokens.colors.surfaceMuted)
                            .padding(horizontal = ThemeTokens.spacing.xsDp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (isGlobal) "全局日常" else item.rule.accountPrefix,
                            style = ThemeTokens.type.label,
                            color = ThemeTokens.colors.textSecondary,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "限额 ${item.formattedLimit}",
                        style = ThemeTokens.type.body,
                        fontWeight = FontWeight.Medium,
                        color = ThemeTokens.colors.textPrimary,
                    )
                    if (item.rule.id != "budget-rule-global-default") {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag("delete-budget-rule-${item.rule.id}"),
                        ) {
                            Icon(
                                Lucide.Trash2,
                                contentDescription = "Delete Budget Rule",
                                tint = ThemeTokens.colors.textSecondary,
                            )
                        }
                    }
                }
            }

            // Dual-Needle Pacing Bar
            Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
                        .background(ThemeTokens.colors.surfaceMuted)
                        .border(1.dp, ThemeTokens.colors.border, RoundedCornerShape(ThemeTokens.radii.smallDp))
                        .testTag("pacing-bar-${item.rule.id}"),
                ) {
                    // Spend Progress Fill
                    val spendRatio = eval.spendRatio.coerceIn(0.0, 1.0).toFloat()
                    val spendColor = when {
                        eval.isOverBudget -> ThemeTokens.colors.error
                        eval.isAheadOfPacing -> ThemeTokens.colors.warning
                        else -> ThemeTokens.colors.primary
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = spendRatio.coerceAtLeast(0.001f))
                            .fillMaxHeight()
                            .background(spendColor),
                    )

                    // Time Elapsed Needle Marker
                    val timeRatio = eval.timeElapsedRatio.coerceIn(0.0, 1.0).toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = timeRatio.coerceAtLeast(0.001f))
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .fillMaxHeight()
                                .background(ThemeTokens.colors.textPrimary)
                                .testTag("time-needle-${item.rule.id}"),
                        )
                    }
                }

                // Pacing commentary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val timePercent = (eval.timeElapsedRatio * 100).toInt()
                    val spendPercent = (eval.spendRatio * 100).toInt()

                    val statusMsg = when {
                        eval.isOverBudget -> "⚠️ 本月已超支 ${item.formattedPacingDelta}"
                        eval.isAheadOfPacing -> "⚡️ 配速超前 ${item.formattedPacingDelta} (时间 $timePercent% / 支出 $spendPercent%)"
                        else -> "👍 配速健康，结余 ${item.formattedPacingDelta} (时间 $timePercent% / 支出 $spendPercent%)"
                    }

                    Text(
                        text = statusMsg,
                        style = ThemeTokens.type.label,
                        color = when {
                            eval.isOverBudget -> ThemeTokens.colors.error
                            eval.isAheadOfPacing -> ThemeTokens.colors.warning
                            else -> ThemeTokens.colors.textSecondary
                        },
                    )

                    Text(
                        text = "建议 ${item.formattedDailyAllowance}/天",
                        style = ThemeTokens.type.label,
                        fontWeight = FontWeight.Medium,
                        color = ThemeTokens.colors.textPrimary,
                    )
                }
            }

            // Stats Row: Spent, Remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已支出: ${item.formattedSpent}",
                    style = ThemeTokens.type.bodyMuted,
                    color = ThemeTokens.colors.textPrimary,
                )
                Text(
                    text = "剩余额度: ${item.formattedRemaining}",
                    style = ThemeTokens.type.bodyMuted,
                    fontWeight = FontWeight.Medium,
                    color = if (eval.remainingMinor < 0L) ThemeTokens.colors.error else ThemeTokens.colors.success,
                )
            }
        }
    }
}
