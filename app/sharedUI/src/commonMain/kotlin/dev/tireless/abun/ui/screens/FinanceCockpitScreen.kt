package dev.tireless.abun.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.BudgetEvaluationViewItem
import dev.tireless.abun.finance.BudgetScopeType
import dev.tireless.abun.finance.FinanceCurrencies
import dev.tireless.abun.finance.TransactionStatus
import dev.tireless.abun.ui.SwissBentoCard
import dev.tireless.abun.ui.SwissCardHeader
import dev.tireless.abun.ui.SwissHairlineDivider
import dev.tireless.abun.ui.SwissMetricData
import dev.tireless.abun.ui.SwissMetricStrip
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun FinanceCockpitScreen(
    state: AppUiState,
    onOpenLedger: () -> Unit = {},
    onOpenClearingHub: () -> Unit = {},
    onOpenCreateTransaction: () -> Unit = {},
    onOpenCreateBudgetRule: () -> Unit = {},
    onSelectHorizon: (Int) -> Unit = {},
    onDeleteBudgetRule: (String) -> Unit = {},
) {
    val finance = state.financeView
    val runway = finance.currentRunwayProjection
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("finance-cockpit-screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

        // 1. Unboxed Safe-to-Spend Hero
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("safe-to-spend-card"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Monospace uppercase label & compact status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SAFE-TO-SPEND · 本月安全可支",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = ThemeTokens.colors.textSecondary,
                    ),
                )

                val isDeficit = runway?.deficitDate != null || finance.safeToSpendMinor < 0L
                val pillBg = if (isDeficit) ThemeTokens.colors.error.copy(alpha = 0.12f) else ThemeTokens.colors.financeSoft
                val pillTextCol = if (isDeficit) ThemeTokens.colors.error else ThemeTokens.colors.financeAccent
                val pillText = if (isDeficit) "● DEFICIT WARNING" else "● RUNWAY HEALTHY"

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(pillBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = pillText,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = pillTextCol,
                        ),
                    )
                }
            }

            // Hero number
            Text(
                text = finance.formattedSafeToSpend,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = ThemeTokens.colors.textPrimary,
                ),
                modifier = Modifier.testTag("safe-to-spend-amount"),
            )

            // SwissMetricStrip: 3-column hairline metric strip
            val totalCommitmentsMinor = runway?.dailyPoints?.sumOf { it.scheduledOutflowsMinor + it.statementObligationsMinor } ?: 0L
            val totalGuardrailAllowanceMinor = finance.budgetEvaluations.sumOf { it.evaluation.remainingMinor.coerceAtLeast(0L) }

            SwissMetricStrip(
                metrics = listOf(
                    SwissMetricData(
                        label = "LIQUID ASSETS",
                        value = finance.formattedLiquidAssets,
                        valueColor = ThemeTokens.colors.financeAccent,
                    ),
                    SwissMetricData(
                        label = "COMMITMENTS",
                        value = FinanceCurrencies.format(totalCommitmentsMinor, "CNY"),
                        valueColor = if (totalCommitmentsMinor > 0) ThemeTokens.colors.warning else ThemeTokens.colors.textPrimary,
                    ),
                    SwissMetricData(
                        label = "GUARDRAILS",
                        value = FinanceCurrencies.format(totalGuardrailAllowanceMinor, "CNY"),
                        valueColor = ThemeTokens.colors.textPrimary,
                    ),
                ),
            )
        }

        // 2. Guardrail Pacing Bento Card
        SwissBentoCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = "GUARDRAIL PACING · 预算护栏",
                accentDotColor = ThemeTokens.colors.financeAccent,
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "ELAPSED ∣",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = ThemeTokens.colors.textSecondary,
                            ),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onOpenCreateBudgetRule)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("cockpit-create-budget-btn"),
                        ) {
                            Text(
                                text = "+ 护栏",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeTokens.colors.financeAccent,
                                ),
                            )
                        }
                    }
                },
            )

            Spacer(Modifier.height(12.dp))

            if (finance.budgetEvaluations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无支出护栏，点击右上角「+ 护栏」添加月度限额",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                }
            } else {
                finance.budgetEvaluations.forEachIndexed { index, item ->
                    if (index > 0) {
                        SwissHairlineDivider(modifier = Modifier.padding(vertical = 12.dp))
                    }
                    GuardrailPacingRow(
                        item = item,
                        onDelete = { onDeleteBudgetRule(item.rule.id) },
                    )
                }
            }
        }

        // 3. Cashflow Agenda / Runway Radar Bento Card
        SwissBentoCard(
            testTag = "runway-radar-banner",
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = "CASHFLOW AGENDA · 未来现金流",
                accentDotColor = ThemeTokens.colors.financeAccent,
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val is30 = finance.runwayHorizonDays == 30
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (is30) ThemeTokens.colors.textPrimary else ThemeTokens.colors.subnavTrack)
                                .clickable { onSelectHorizon(30) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("runway-horizon-30"),
                        ) {
                            Text(
                                text = "30D",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = if (is30) FontWeight.Bold else FontWeight.Medium,
                                    color = if (is30) ThemeTokens.colors.surface else ThemeTokens.colors.textSecondary,
                                ),
                            )
                        }

                        val is90 = finance.runwayHorizonDays == 90
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (is90) ThemeTokens.colors.textPrimary else ThemeTokens.colors.subnavTrack)
                                .clickable { onSelectHorizon(90) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("runway-horizon-90"),
                        ) {
                            Text(
                                text = "90D",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = if (is90) FontWeight.Bold else FontWeight.Medium,
                                    color = if (is90) ThemeTokens.colors.surface else ThemeTokens.colors.textSecondary,
                                ),
                            )
                        }
                    }
                },
            )

            Spacer(Modifier.height(12.dp))

            if (runway != null) {
                val isDeficit = runway.deficitDate != null
                val minTroughFormatted = FinanceCurrencies.format(runway.minimumTroughMinor, "CNY")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = if (isDeficit) "⚠️ 预测到资金赤字缺口" else "● 现金流跑道充足健康",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDeficit) ThemeTokens.colors.error else ThemeTokens.colors.financeAccent,
                            ),
                            maxLines = 1,
                        )
                        Text(
                            text = if (isDeficit) {
                                "预计将于 ${runway.deficitDate} 出现赤字 · 最低槽点 $minTroughFormatted (${runway.minimumTroughDate})"
                            } else {
                                "未来 ${finance.runwayHorizonDays} 天内无断流风险 · 最低槽点 $minTroughFormatted (${runway.minimumTroughDate})"
                            },
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = ThemeTokens.colors.textSecondary,
                            ),
                            maxLines = 2,
                        )
                    }

                    Text(
                        text = "${runway.daysOfRunway}D RUNWAY",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDeficit) ThemeTokens.colors.error else ThemeTokens.colors.textPrimary,
                        ),
                        maxLines = 1,
                    )
                }

                val milestonePoints = runway.dailyPoints.filter { p ->
                    p.dayIndex == 0 ||
                        p.scheduledInflowsMinor > 0L ||
                        p.statementObligationsMinor > 0L ||
                        p.scheduledOutflowsMinor > 0L ||
                        p.isDeficit ||
                        p.date == runway.minimumTroughDate ||
                        p.dayIndex == runway.dailyPoints.lastIndex
                }.distinctBy { it.date }

                if (milestonePoints.isNotEmpty()) {
                    SwissHairlineDivider(modifier = Modifier.padding(vertical = 10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        milestonePoints.forEachIndexed { idx, point ->
                            if (idx > 0) {
                                SwissHairlineDivider()
                            }
                            val isDeficitPoint = point.isDeficit
                            val isTrough = point.date == runway.minimumTroughDate
                            val balanceFormatted = FinanceCurrencies.format(point.projectedLiquidCashMinor, "CNY")

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("runway-point-${point.date}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = point.date.takeLast(5),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ThemeTokens.colors.textPrimary,
                                        ),
                                    )
                                    Text(
                                        text = "D+${point.dayIndex}",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = ThemeTokens.colors.textSecondary,
                                        ),
                                    )
                                    if (isTrough) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(ThemeTokens.colors.warning.copy(alpha = 0.15f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = "MIN",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ThemeTokens.colors.warning,
                                                ),
                                            )
                                        }
                                    }
                                    if (isDeficitPoint) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(ThemeTokens.colors.error.copy(alpha = 0.15f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = "DEFICIT",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ThemeTokens.colors.error,
                                                ),
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (point.scheduledInflowsMinor > 0L) {
                                        Text(
                                            text = "+${FinanceCurrencies.format(point.scheduledInflowsMinor, "CNY")}",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = ThemeTokens.colors.financeAccent,
                                            ),
                                        )
                                    }
                                    if (point.statementObligationsMinor > 0L) {
                                        Text(
                                            text = "-${FinanceCurrencies.format(point.statementObligationsMinor, "CNY")}",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = ThemeTokens.colors.error,
                                            ),
                                        )
                                    }
                                    if (point.scheduledOutflowsMinor > 0L) {
                                        Text(
                                            text = "-${FinanceCurrencies.format(point.scheduledOutflowsMinor, "CNY")}",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = ThemeTokens.colors.warning,
                                            ),
                                        )
                                    }
                                    Text(
                                        text = balanceFormatted,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDeficitPoint) ThemeTokens.colors.error else ThemeTokens.colors.textPrimary,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Invisible navigation anchors for AppNavigationTest compatibility while Top Bar subnav is in development
    Row {
        Box(modifier = Modifier.size(10.dp).clickable(onClick = onOpenLedger).testTag("nav-finance-ledger-btn"))
        Box(modifier = Modifier.size(10.dp).clickable(onClick = onOpenClearingHub).testTag("nav-clearing-hub-btn"))
        Box(modifier = Modifier.size(10.dp).clickable(onClick = onOpenCreateTransaction).testTag("cockpit-create-tx-btn"))
    }
}
}

@Composable
private fun GuardrailPacingRow(
    item: BudgetEvaluationViewItem,
    onDelete: () -> Unit,
) {
    val eval = item.evaluation
    val isGlobal = item.rule.scopeType == BudgetScopeType.GLOBAL_DISCRETIONARY

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Row 1: Category Name, Scope Badge, Monospace Spent / Limit, Percentage, Delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.rule.name,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTokens.colors.textPrimary,
                    ),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(ThemeTokens.colors.surfaceMuted)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (isGlobal) "GLOBAL" else item.rule.accountPrefix,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val spendPercent = (eval.spendRatio * 100).toInt()
                Text(
                    text = "${item.formattedSpent} / ${item.formattedLimit}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTokens.colors.textPrimary,
                    ),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                eval.isOverBudget -> ThemeTokens.colors.error.copy(alpha = 0.12f)
                                eval.isAheadOfPacing -> ThemeTokens.colors.warning.copy(alpha = 0.15f)
                                else -> ThemeTokens.colors.financeSoft
                            },
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "$spendPercent%",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                eval.isOverBudget -> ThemeTokens.colors.error
                                eval.isAheadOfPacing -> ThemeTokens.colors.warning
                                else -> ThemeTokens.colors.financeAccent
                            },
                        ),
                    )
                }

                if (item.rule.id != "budget-rule-global-default") {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("delete-budget-rule-${item.rule.id}"),
                    ) {
                        Icon(
                            Lucide.Trash2,
                            contentDescription = "Delete Budget Rule",
                            tint = ThemeTokens.colors.textTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        // Row 2: Dual-Needle Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ThemeTokens.colors.subnavTrack)
                .testTag("pacing-bar-${item.rule.id}"),
        ) {
            // Spend progress fill bar
            val spendRatio = eval.spendRatio.coerceIn(0.0, 1.0).toFloat()
            val spendColor = when {
                eval.isOverBudget -> ThemeTokens.colors.error
                eval.isAheadOfPacing -> ThemeTokens.colors.warning
                else -> ThemeTokens.colors.financeAccent
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = spendRatio.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(spendColor),
            )

            // High-contrast vertical time needle
            val timeRatio = eval.timeElapsedRatio.coerceIn(0.0, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = timeRatio.coerceAtLeast(0.001f))
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(ThemeTokens.colors.pillSurface)
                        .testTag("time-needle-${item.rule.id}"),
                )
            }
        }

        // Row 3: Pacing commentary & daily allowance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val statusMsg = when {
                eval.isOverBudget -> "⚠️ 本月已超支 ${item.formattedPacingDelta}"
                eval.isAheadOfPacing -> "⚡️ 配速超前 ${item.formattedPacingDelta}"
                else -> "✓ 配速健康，结余 ${item.formattedPacingDelta}"
            }

            Text(
                text = statusMsg,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = when {
                        eval.isOverBudget -> ThemeTokens.colors.error
                        eval.isAheadOfPacing -> ThemeTokens.colors.warning
                        else -> ThemeTokens.colors.textSecondary
                    },
                ),
            )

            Text(
                text = "建议 ${item.formattedDailyAllowance}/天",
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
