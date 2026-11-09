package dev.tireless.abun.ui.i18n

import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.app.LanguagePreference

interface CommonStrings {
    val save: String
    val cancel: String
    val delete: String
    val edit: String
    val add: String
    val confirm: String
    val retry: String
    val back: String
    val close: String
    val done: String
    val copy: String
    val copied: String
    val loading: String
    val error: String
    val success: String
    val search: String
    val filter: String
    val clear: String
    val all: String
    val apply: String
    fun itemsCount(count: Int): String
}

interface NavStrings {
    val dashboard: String
    val cockpit: String
    val ledger: String
    val clearing: String
    val settings: String
    val tasks: String
    val notes: String
    val things: String
    val finance: String
}

interface CockpitStrings {
    val title: String
    val subtitle: String
    val safeToSpend: String
    val netAssets: String
    val totalAssets: String
    val totalLiabilities: String
    val monthIncome: String
    val monthExpense: String
    val monthCashFlow: String
    val monthlyTrend: String
    val assetStructure: String
    val topExpenses: String
    val recentTransactions: String
    val viewAll: String
    val recordExpense: String
    val income: String
    val expense: String
    val balance: String
    val guardrailPacing: String
    val addGuardrail: String
    val noGuardrails: String
    val cashflowAgenda: String
    val deficitWarning: String
    val runwayHealthy: String
    fun overBudget(amount: String): String
    fun aheadOfPacing(amount: String): String
    fun pacingHealthy(amount: String): String
    fun suggestedDaily(amount: String): String
    fun deficitPredicted(date: String, minTrough: String, minDate: String): String
    fun noDeficitRisk(days: Int, minTrough: String, minDate: String): String
}

interface LedgerStrings {
    val title: String
    val totalTransactions: String
    val dateRange: String
    val allDates: String
    val accountFilter: String
    val allAccounts: String
    fun selectedAccounts(count: Int): String
    val order: String
    val newestFirst: String
    val oldestFirst: String
    val includeFuture: String
    val futureIncluded: String
    val excludeFuture: String
    val noTransactions: String
    val noTransactionsDesc: String
    val today: String
    val futureBadge: String
    val loadEarlier: String
    val scrollToFuture: String
    fun showingCount(visible: Int, total: Int): String
    val addTransaction: String
    val advancedFilter: String
    val selectAccountsTitle: String
    val searchAccountsPlaceholder: String
    val selectAll: String
    val clearSelection: String
    val advancedFilterTitle: String
    val advancedFilterNotice: String
    val acknowledged: String
    fun pullDownLoadEarlier(visible: Int, total: Int): String
    fun scrollUpLoadEarlier(visible: Int, total: Int): String
}

interface ClearingStrings {
    val title: String
    val unreconciled: String
    val reconciled: String
    val pendingTransactions: String
    val reconcile: String
    val batchSettle: String
    val markAsCleared: String
    val noPendingItems: String
    val unsettledObligations: String
    val creditStatements: String
    val combineRepayment: String
    val noCreditStatements: String
    val loanAmortization: String
    val newAmortization: String
    val noLoans: String
    val oneClickRepay: String
    val recordRepayment: String
}

interface FinanceSettingsStrings {
    val title: String
    val accountManagement: String
    val createAccount: String
    val cancelCreate: String
    val liquidPrimary: String
    val sedimentAsset: String
    val passThroughExpense: String
    val includeInNetWorth: String
    val excludeFromNetWorth: String
    val commercialLiability: String
    val personalLoan: String
    val archived: String
    val normal: String
    val reconcileBalance: String
    val reconcileBalanceDesc: String
    val actualBalanceLabel: String
    val autoPadButton: String
    val newAccountTitle: String
    val accountPathLabel: String
    val accountNameLabel: String
    val currencyLabel: String
    val assetRoleLabel: String
    val liabilityNatureLabel: String
    val saveAccount: String
    val searchPlaceholder: String
    fun categoryName(category: AccountCategory): String
    fun accountCount(count: Int): String
}

interface SettingsStrings {
    val title: String
    val appearanceCard: String
    val themeMode: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val languageMode: String
    val langOptionSystem: String
    val langOptionEn: String
    val langOptionSc: String
    val langOptionTc: String
    val cloudCard: String
    val syncStatus: String
    val syncing: String
    val syncedJustNow: String
    val syncError: String
    val syncNow: String
    val fullPush: String
    val pullRemote: String
    val accountInfo: String
    val accountLogin: String
    val signedInAs: String
    val signOut: String
    val signIn: String
    val tasksCard: String
    val financeCard: String
    val accountsCard: String
    val manageAccounts: String
    val addAccount: String
    val systemInfoCard: String
    val version: String
    val clientId: String
    val storageEngine: String
    val localDatabase: String
    val modulesCard: String
    val modulesEnabledCount: (enabled: Int, total: Int) -> String
    val taskTitlePrefix: String
    val focusMinutes: String
    val shortBreakMinutes: String
    val longBreakMinutes: String
    val timezoneOverride: String
    val dayRolloverTime: String
    val saveSettings: String
    val financePresetsStatusDefault: String
    val financePresetsStatusConfigured: String
    val loadPredefinedAccounts: String
    val resetToDefaultFiveAccounts: String
    val guestAccountDesc: String
    val signedInAccountDesc: String
    val softwareUpdatesCard: String
    val currentBuild: String
    val apiVersion: String
    val checkForUpdates: String
    val autoCheckUpdates: String
}

interface ActionStrings {
    val quickInput: String
    val quickInputPlaceholder: String
    val updateAvailable: String
    val installingUpdate: String
    val restartToApply: String
    val updateNow: String
    val quickCaptureHeader: String
    val workspacesHeader: String
    val configureModules: String
    val quickCaptureTask: String
    val quickCaptureExpense: String
    val quickCaptureNote: String
    val quickCaptureThing: String
}

interface AppStrings {
    val language: AppLanguage
    val common: CommonStrings
    val nav: NavStrings
    val cockpit: CockpitStrings
    val ledger: LedgerStrings
    val clearing: ClearingStrings
    val financeSettings: FinanceSettingsStrings
    val settings: SettingsStrings
    val actions: ActionStrings
}

// -------------------------------------------------------------------------
// English Implementation
// -------------------------------------------------------------------------

object EnStrings : AppStrings {
    override val language = AppLanguage.EN

    override val common = object : CommonStrings {
        override val save = "Save"
        override val cancel = "Cancel"
        override val delete = "Delete"
        override val edit = "Edit"
        override val add = "Add"
        override val confirm = "Confirm"
        override val retry = "Retry"
        override val back = "Back"
        override val close = "Close"
        override val done = "Done"
        override val copy = "Copy"
        override val copied = "Copied"
        override val loading = "Loading..."
        override val error = "Error"
        override val success = "Success"
        override val search = "Search"
        override val filter = "Filter"
        override val clear = "Clear"
        override val all = "All"
        override val apply = "Apply"
        override fun itemsCount(count: Int) = if (count == 1) "1 item" else "$count items"
    }

    override val nav = object : NavStrings {
        override val dashboard = "Dashboard"
        override val cockpit = "Cockpit"
        override val ledger = "Ledger"
        override val clearing = "Clearing"
        override val settings = "Settings"
        override val tasks = "Tasks"
        override val notes = "Notes"
        override val things = "Things"
        override val finance = "Finance"
    }

    override val cockpit = object : CockpitStrings {
        override val title = "COCKPIT"
        override val subtitle = "Financial Overview & Balance"
        override val safeToSpend = "SAFE-TO-SPEND"
        override val netAssets = "Net Assets"
        override val totalAssets = "Total Assets"
        override val totalLiabilities = "Total Liabilities"
        override val monthIncome = "Month Income"
        override val monthExpense = "Month Expense"
        override val monthCashFlow = "Cash Flow"
        override val monthlyTrend = "Monthly Trend"
        override val assetStructure = "Asset Structure"
        override val topExpenses = "Top Expenses"
        override val recentTransactions = "Recent Transactions"
        override val viewAll = "View All"
        override val recordExpense = "+ Expense"
        override val income = "Income"
        override val expense = "Expense"
        override val balance = "Balance"
        override val guardrailPacing = "GUARDRAIL PACING"
        override val addGuardrail = "+ Guardrail"
        override val noGuardrails = "No spending guardrails set. Tap \"+ Guardrail\" to add monthly limits."
        override val cashflowAgenda = "CASHFLOW AGENDA"
        override val deficitWarning = "⚠️ Cash deficit projected"
        override val runwayHealthy = "● Healthy cashflow runway"
        override fun overBudget(amount: String) = "⚠️ Over budget by $amount"
        override fun aheadOfPacing(amount: String) = "⚡️ Ahead of pacing by $amount"
        override fun pacingHealthy(amount: String) = "✓ Healthy pacing, surplus $amount"
        override fun suggestedDaily(amount: String) = "Suggested $amount/day"
        override fun deficitPredicted(date: String, minTrough: String, minDate: String) =
            "Deficit projected on $date · Trough $minTrough ($minDate)"
        override fun noDeficitRisk(days: Int, minTrough: String, minDate: String) =
            "No deficit risk in next $days days · Trough $minTrough ($minDate)"
    }

    override val ledger = object : LedgerStrings {
        override val title = "LEDGER"
        override val totalTransactions = "Transactions"
        override val dateRange = "Date Range"
        override val allDates = "All Dates"
        override val accountFilter = "Account"
        override val allAccounts = "All Accounts"
        override fun selectedAccounts(count: Int) = "$count Accounts"
        override val order = "Order"
        override val newestFirst = "Newest"
        override val oldestFirst = "Oldest"
        override val includeFuture = "Future"
        override val futureIncluded = "Future On"
        override val excludeFuture = "Future Off"
        override val noTransactions = "NO TRANSACTIONS"
        override val noTransactionsDesc = "Use the bottom command pill \"+ Expense\" to record an expense or transfer."
        override val today = "Today"
        override val futureBadge = "Future"
        override val loadEarlier = "Pull down to load earlier"
        override val scrollToFuture = "Scroll up to view future"
        override fun showingCount(visible: Int, total: Int) = "Showing $visible of $total"
        override val addTransaction = "+ Expense"
        override val advancedFilter = "Advanced ▾"
        override val selectAccountsTitle = "Select Accounts"
        override val searchAccountsPlaceholder = "Search accounts..."
        override val selectAll = "Select All"
        override val clearSelection = "Clear"
        override val advancedFilterTitle = "Advanced Filter"
        override val advancedFilterNotice = "Advanced filter is coming soon:\n• Custom amount ranges\n• Multi-currency filtering\n• Compound tag & keyword search\n• Payee exact matching"
        override val acknowledged = "Got it"
        override fun pullDownLoadEarlier(visible: Int, total: Int) = "↑ Pull down or tap to load earlier ($visible/$total)"
        override fun scrollUpLoadEarlier(visible: Int, total: Int) = "↓ Scroll or tap to load earlier ($visible/$total)"
    }

    override val clearing = object : ClearingStrings {
        override val title = "CLEARING"
        override val unreconciled = "Unreconciled"
        override val reconciled = "Reconciled"
        override val pendingTransactions = "Pending Transactions"
        override val reconcile = "Reconcile"
        override val batchSettle = "Batch Settle"
        override val markAsCleared = "Mark Cleared"
        override val noPendingItems = "No pending items to clear"
        override val unsettledObligations = "UNSETTLED OBLIGATIONS"
        override val creditStatements = "CREDIT STATEMENTS"
        override val combineRepayment = "Combined Repay"
        override val noCreditStatements = "No credit card statements"
        override val loanAmortization = "LOAN AMORTIZATION"
        override val newAmortization = "+ Installment"
        override val noLoans = "No active loans or installments"
        override val oneClickRepay = "Repay"
        override val recordRepayment = "Record Repayment"
    }

    override val financeSettings = object : FinanceSettingsStrings {
        override val title = "FINANCE SETTINGS"
        override val accountManagement = "Five-Category Account Management"
        override val createAccount = "New Account"
        override val cancelCreate = "Cancel"
        override val liquidPrimary = "🟢 Liquid Primary"
        override val sedimentAsset = "🟡 Sediment Asset"
        override val passThroughExpense = "⚡ Pass-Through Expense"
        override val includeInNetWorth = "✓ Include in Net Worth"
        override val excludeFromNetWorth = "✕ Exclude Net Worth"
        override val commercialLiability = "🔴 Commercial Liability"
        override val personalLoan = "⚪ Personal Loan"
        override val archived = "Archived"
        override val normal = "Active"
        override val reconcileBalance = "Reconcile"
        override val reconcileBalanceDesc = "Enter the current verified balance from your bank statement to generate a balancing transaction:"
        override val actualBalanceLabel = "Verified Balance"
        override val autoPadButton = "Auto Pad"
        override val newAccountTitle = "Create Account"
        override val accountPathLabel = "Account Path (e.g. Assets:Current:CMB)"
        override val accountNameLabel = "Account Name (e.g. CMB Debit)"
        override val currencyLabel = "Currency (CNY, USD, etc.)"
        override val assetRoleLabel = "Asset Role"
        override val liabilityNatureLabel = "Liability Nature"
        override val saveAccount = "Save Account"
        override val searchPlaceholder = "Search accounts..."
        override fun categoryName(category: AccountCategory) = when (category) {
            AccountCategory.ASSET -> "Assets"
            AccountCategory.LIABILITY -> "Liabilities"
            AccountCategory.INCOME -> "Income"
            AccountCategory.EXPENSE -> "Expenses"
            AccountCategory.EQUITY -> "Equity"
        }
        override fun accountCount(count: Int) = if (count == 1) "1 account" else "$count accounts"
    }

    override val settings = object : SettingsStrings {
        override val title = "SETTINGS"
        override val appearanceCard = "APPEARANCE & LANGUAGE"
        override val themeMode = "Theme Mode"
        override val themeSystem = "System"
        override val themeLight = "Light"
        override val themeDark = "Dark"
        override val languageMode = "Language"
        override val langOptionSystem = "System"
        override val langOptionEn = "En"
        override val langOptionSc = "SC"
        override val langOptionTc = "TC"
        override val cloudCard = "CLOUD & SYNC"
        override val syncStatus = "Sync Status"
        override val syncing = "Syncing..."
        override val syncedJustNow = "Synced just now"
        override val syncError = "Sync error"
        override val syncNow = "Sync Now"
        override val fullPush = "Full Push"
        override val pullRemote = "Pull Remote"
        override val accountInfo = "Account"
        override val accountLogin = "Login"
        override val signedInAs = "Signed in as"
        override val signOut = "Log out"
        override val signIn = "Open login"
        override val tasksCard = "TASKS & FOCUS"
        override val financeCard = "FINANCE PRESETS"
        override val accountsCard = "ACCOUNTS & CATEGORIES"
        override val manageAccounts = "Manage Accounts"
        override val addAccount = "Add Account"
        override val systemInfoCard = "SYSTEM INFO"
        override val version = "Version"
        override val clientId = "Client ID"
        override val storageEngine = "Storage Engine"
        override val localDatabase = "Local Database"
        override val modulesCard = "MODULES"
        override val modulesEnabledCount = { enabled: Int, total: Int -> "$enabled OF $total ENABLED" }
        override val taskTitlePrefix = "Task title prefix"
        override val focusMinutes = "Focus minutes"
        override val shortBreakMinutes = "Short break"
        override val longBreakMinutes = "Long break"
        override val timezoneOverride = "Timezone override"
        override val dayRolloverTime = "Day rollover time"
        override val saveSettings = "Save settings"
        override val financePresetsStatusDefault = "Only 5 root categories configured. Load presets for quick setup."
        override val financePresetsStatusConfigured = "Custom accounts or entries exist. Reset restores the default 5 root categories."
        override val loadPredefinedAccounts = "Load Preset Accounts"
        override val resetToDefaultFiveAccounts = "Reset to 5 Root Accounts"
        override val guestAccountDesc = "Login anytime to enable cloud sync on this device."
        override val signedInAccountDesc = "This device is signed in and can sync with your server account."
        override val softwareUpdatesCard = "SOFTWARE"
        override val currentBuild = "Current Build"
        override val apiVersion = "API Version"
        override val checkForUpdates = "Check for updates"
        override val autoCheckUpdates = "Auto check"
    }

    override val actions = object : ActionStrings {
        override val quickInput = "Quick Input"
        override val quickInputPlaceholder = "Natural language entry, e.g. 'Coffee 35 Cash'"
        override val updateAvailable = "Update Available"
        override val installingUpdate = "Installing update..."
        override val restartToApply = "Restart to apply update"
        override val updateNow = "Update Now"
        override val quickCaptureHeader = "QUICK CAPTURE"
        override val workspacesHeader = "WORKSPACES"
        override val configureModules = "Configure →"
        override val quickCaptureTask = "Task"
        override val quickCaptureExpense = "Expense"
        override val quickCaptureNote = "Note"
        override val quickCaptureThing = "Thing"
    }
}

// -------------------------------------------------------------------------
// Simplified Chinese Implementation
// -------------------------------------------------------------------------

object ScStrings : AppStrings {
    override val language = AppLanguage.SC

    override val common = object : CommonStrings {
        override val save = "保存"
        override val cancel = "取消"
        override val delete = "删除"
        override val edit = "编辑"
        override val add = "新增"
        override val confirm = "确认"
        override val retry = "重试"
        override val back = "返回"
        override val close = "关闭"
        override val done = "完成"
        override val copy = "复制"
        override val copied = "已复制"
        override val loading = "加载中..."
        override val error = "错误"
        override val success = "成功"
        override val search = "搜索"
        override val filter = "筛选"
        override val clear = "清除"
        override val all = "全部"
        override val apply = "应用"
        override fun itemsCount(count: Int) = "$count 项"
    }

    override val nav = object : NavStrings {
        override val dashboard = "总览"
        override val cockpit = "总览"
        override val ledger = "明细"
        override val clearing = "待办"
        override val settings = "设置"
        override val tasks = "任务"
        override val notes = "笔记"
        override val things = "物品"
        override val finance = "财务"
    }

    override val cockpit = object : CockpitStrings {
        override val title = "财务总览"
        override val subtitle = "资产负债与现金流概览"
        override val safeToSpend = "本月安全可支"
        override val netAssets = "净资产"
        override val totalAssets = "总资产"
        override val totalLiabilities = "总负债"
        override val monthIncome = "本月收入"
        override val monthExpense = "本月支出"
        override val monthCashFlow = "本月现金流"
        override val monthlyTrend = "月度趋势"
        override val assetStructure = "资产结构"
        override val topExpenses = "支出排行"
        override val recentTransactions = "近期交易"
        override val viewAll = "查看全部"
        override val recordExpense = "+ 记一笔"
        override val income = "收入"
        override val expense = "支出"
        override val balance = "结余"
        override val guardrailPacing = "预算护栏"
        override val addGuardrail = "+ 护栏"
        override val noGuardrails = "暂无支出护栏，点击右上角「+ 护栏」添加月度限额"
        override val cashflowAgenda = "未来现金流"
        override val deficitWarning = "⚠️ 预测到资金赤字缺口"
        override val runwayHealthy = "● 现金流跑道充足健康"
        override fun overBudget(amount: String) = "⚠️ 本月已超支 $amount"
        override fun aheadOfPacing(amount: String) = "⚡️ 配速超前 $amount"
        override fun pacingHealthy(amount: String) = "✓ 配速健康，结余 $amount"
        override fun suggestedDaily(amount: String) = "建议 $amount/天"
        override fun deficitPredicted(date: String, minTrough: String, minDate: String) =
            "预计将于 $date 出现赤字 · 最低槽点 $minTrough ($minDate)"
        override fun noDeficitRisk(days: Int, minTrough: String, minDate: String) =
            "未来 $days 天内无断流风险 · 最低槽点 $minTrough ($minDate)"
    }

    override val ledger = object : LedgerStrings {
        override val title = "交易明细"
        override val totalTransactions = "交易记录"
        override val dateRange = "日期范围"
        override val allDates = "全部日期"
        override val accountFilter = "账户"
        override val allAccounts = "全部账户"
        override fun selectedAccounts(count: Int) = "已选 $count 个账户"
        override val order = "排序"
        override val newestFirst = "倒序"
        override val oldestFirst = "正序"
        override val includeFuture = "未来"
        override val futureIncluded = "包含未来"
        override val excludeFuture = "隐藏未来"
        override val noTransactions = "暂无交易记录"
        override val noTransactionsDesc = "使用底部快捷指令栏「+ 记一笔」记录收支或转账"
        override val today = "今天"
        override val futureBadge = "未来"
        override val loadEarlier = "下拉加载更早记录"
        override val scrollToFuture = "上滑查看未来记录"
        override fun showingCount(visible: Int, total: Int) = "显示 $visible / 共 $total 条"
        override val addTransaction = "+ 记一笔"
        override val advancedFilter = "高级筛选 ▾"
        override val selectAccountsTitle = "选择账户"
        override val searchAccountsPlaceholder = "搜索账户..."
        override val selectAll = "全选"
        override val clearSelection = "清空"
        override val advancedFilterTitle = "高级筛选"
        override val advancedFilterNotice = "高级筛选功能正在规划中，后续将支持：\n• 自定义金额区间\n• 多币种过滤\n• 标签与关键字复合检索\n• 交易对手方精确筛选"
        override val acknowledged = "知道了"
        override fun pullDownLoadEarlier(visible: Int, total: Int) = "↑ 下拉或点击加载更早记录 ($visible/$total)"
        override fun scrollUpLoadEarlier(visible: Int, total: Int) = "↓ 滑动或点击加载更早记录 ($visible/$total)"
    }

    override val clearing = object : ClearingStrings {
        override val title = "待办结算"
        override val unreconciled = "待对账"
        override val reconciled = "已对账"
        override val pendingTransactions = "待办交易"
        override val reconcile = "对账"
        override val batchSettle = "批量结算"
        override val markAsCleared = "标记已平"
        override val noPendingItems = "暂无待结算项目"
        override val unsettledObligations = "待清算总额"
        override val creditStatements = "信用卡账单"
        override val combineRepayment = "合并还款"
        override val noCreditStatements = "暂无信用卡账单数据"
        override val loanAmortization = "贷款分期"
        override val newAmortization = "+ 新建分期"
        override val noLoans = "暂无贷款或分期计划"
        override val oneClickRepay = "一键还款"
        override val recordRepayment = "记录本期还款"
    }

    override val financeSettings = object : FinanceSettingsStrings {
        override val title = "财务设置"
        override val accountManagement = "五大类会计账户管理"
        override val createAccount = "新建账户"
        override val cancelCreate = "取消新建"
        override val liquidPrimary = "🟢 流动主力"
        override val sedimentAsset = "🟡 沉淀资产"
        override val passThroughExpense = "⚡ 转出即消费"
        override val includeInNetWorth = "✓ 计入净资产"
        override val excludeFromNetWorth = "✕ 排除净资产"
        override val commercialLiability = "🔴 商业负债"
        override val personalLoan = "⚪ 个人借款"
        override val archived = "已归档"
        override val normal = "正常"
        override val reconcileBalance = "对账"
        override val reconcileBalanceDesc = "输入银行/账单真实当前余额，系统将自动生成差额平账交易："
        override val actualBalanceLabel = "真实余额"
        override val autoPadButton = "平账"
        override val newAccountTitle = "新建会计账户"
        override val accountPathLabel = "账户路径 (如 Assets:Current:CMB)"
        override val accountNameLabel = "账户名称 (如 招商银行)"
        override val currencyLabel = "币种 (CNY, USD等)"
        override val assetRoleLabel = "资产定位"
        override val liabilityNatureLabel = "负债性质"
        override val saveAccount = "保存账户"
        override val searchPlaceholder = "搜索账户..."
        override fun categoryName(category: AccountCategory) = when (category) {
            AccountCategory.ASSET -> "资产"
            AccountCategory.LIABILITY -> "负债"
            AccountCategory.INCOME -> "收入"
            AccountCategory.EXPENSE -> "支出"
            AccountCategory.EQUITY -> "权益"
        }
        override fun accountCount(count: Int) = "$count 个账户"
    }

    override val settings = object : SettingsStrings {
        override val title = "系统设置"
        override val appearanceCard = "外观与语言"
        override val themeMode = "主题模式"
        override val themeSystem = "系统"
        override val themeLight = "浅色"
        override val themeDark = "深色"
        override val languageMode = "语言"
        override val langOptionSystem = "系统"
        override val langOptionEn = "英文"
        override val langOptionSc = "简中"
        override val langOptionTc = "繁中"
        override val cloudCard = "云端与同步"
        override val syncStatus = "同步状态"
        override val syncing = "正在同步..."
        override val syncedJustNow = "刚刚完成同步"
        override val syncError = "同步异常"
        override val syncNow = "立即同步"
        override val fullPush = "完整推送"
        override val pullRemote = "拉取云端"
        override val accountInfo = "账号信息"
        override val accountLogin = "登录"
        override val signedInAs = "已登录为"
        override val signOut = "退出登录"
        override val signIn = "登录账号"
        override val tasksCard = "任务与专注"
        override val financeCard = "财务预设"
        override val accountsCard = "账户与分类"
        override val manageAccounts = "账户管理"
        override val addAccount = "新增账户"
        override val systemInfoCard = "系统信息"
        override val version = "版本号"
        override val clientId = "客户端标识"
        override val storageEngine = "存储引擎"
        override val localDatabase = "本地数据库"
        override val modulesCard = "功能模块"
        override val modulesEnabledCount = { enabled: Int, total: Int -> "已启用 $enabled / 共 $total 项" }
        override val taskTitlePrefix = "默认任务前缀"
        override val focusMinutes = "专注时长 (分钟)"
        override val shortBreakMinutes = "短休时长"
        override val longBreakMinutes = "长休时长"
        override val timezoneOverride = "时区覆盖"
        override val dayRolloverTime = "日结切换时间"
        override val saveSettings = "保存设置"
        override val financePresetsStatusDefault = "当前仅有基础五类根账户，可加载预设子账户以快速上手记账。"
        override val financePresetsStatusConfigured = "已配置子账户或已有记账记录。如需重置，可清除并恢复为基础五类根账户。"
        override val loadPredefinedAccounts = "加载预设子账户"
        override val resetToDefaultFiveAccounts = "重置为基础五类账户"
        override val guestAccountDesc = "登录账号以开启此设备的数据云端同步。"
        override val signedInAccountDesc = "当前设备已登录并与服务器保持同步。"
        override val softwareUpdatesCard = "软件更新"
        override val currentBuild = "当前构建"
        override val apiVersion = "接口版本"
        override val checkForUpdates = "检查更新"
        override val autoCheckUpdates = "自动检查"
    }

    override val actions = object : ActionStrings {
        override val quickInput = "快速记账"
        override val quickInputPlaceholder = "支持自然语言，如: 午餐 35 招行"
        override val updateAvailable = "发现新版本"
        override val installingUpdate = "正在安装更新..."
        override val restartToApply = "重启以应用更新"
        override val updateNow = "立即更新"
        override val quickCaptureHeader = "全局快速捕获"
        override val workspacesHeader = "已启用模块"
        override val configureModules = "配置 →"
        override val quickCaptureTask = "任务"
        override val quickCaptureExpense = "记账"
        override val quickCaptureNote = "闪念"
        override val quickCaptureThing = "物品"
    }
}

// -------------------------------------------------------------------------
// Traditional Chinese Implementation
// -------------------------------------------------------------------------

object TcStrings : AppStrings {
    override val language = AppLanguage.TC

    override val common = object : CommonStrings {
        override val save = "儲存"
        override val cancel = "取消"
        override val delete = "刪除"
        override val edit = "編輯"
        override val add = "新增"
        override val confirm = "確認"
        override val retry = "重試"
        override val back = "返回"
        override val close = "關閉"
        override val done = "完成"
        override val copy = "複製"
        override val copied = "已複製"
        override val loading = "載入中..."
        override val error = "錯誤"
        override val success = "成功"
        override val search = "搜尋"
        override val filter = "篩選"
        override val clear = "清除"
        override val all = "全部"
        override val apply = "套用"
        override fun itemsCount(count: Int) = "$count 項"
    }

    override val nav = object : NavStrings {
        override val dashboard = "總覽"
        override val cockpit = "總覽"
        override val ledger = "明細"
        override val clearing = "待辦"
        override val settings = "設定"
        override val tasks = "任務"
        override val notes = "筆記"
        override val things = "物品"
        override val finance = "財務"
    }

    override val cockpit = object : CockpitStrings {
        override val title = "財務總覽"
        override val subtitle = "資產負債與現金流概覽"
        override val safeToSpend = "本月安全可支"
        override val netAssets = "淨資產"
        override val totalAssets = "總資產"
        override val totalLiabilities = "總負債"
        override val monthIncome = "本月收入"
        override val monthExpense = "本月支出"
        override val monthCashFlow = "本月現金流"
        override val monthlyTrend = "月度趨勢"
        override val assetStructure = "資產結構"
        override val topExpenses = "支出排行"
        override val recentTransactions = "近期交易"
        override val viewAll = "查看全部"
        override val recordExpense = "+ 記一筆"
        override val income = "收入"
        override val expense = "支出"
        override val balance = "結餘"
        override val guardrailPacing = "預算護欄"
        override val addGuardrail = "+ 護欄"
        override val noGuardrails = "暫無支出護欄，點擊右上角「+ 護欄」新增月度限額"
        override val cashflowAgenda = "未來現金流"
        override val deficitWarning = "⚠️ 預測到資金赤字缺口"
        override val runwayHealthy = "● 現金流跑道充足健康"
        override fun overBudget(amount: String) = "⚠️ 本月已超支 $amount"
        override fun aheadOfPacing(amount: String) = "⚡️ 配速超前 $amount"
        override fun pacingHealthy(amount: String) = "✓ 配速健康，結餘 $amount"
        override fun suggestedDaily(amount: String) = "建議 $amount/天"
        override fun deficitPredicted(date: String, minTrough: String, minDate: String) =
            "預計將於 $date 出現赤字 · 最低槽點 $minTrough ($minDate)"
        override fun noDeficitRisk(days: Int, minTrough: String, minDate: String) =
            "未來 $days 天內無斷流風險 · 最低槽點 $minTrough ($minDate)"
    }

    override val ledger = object : LedgerStrings {
        override val title = "交易明細"
        override val totalTransactions = "交易記錄"
        override val dateRange = "日期範圍"
        override val allDates = "全部日期"
        override val accountFilter = "賬戶"
        override val allAccounts = "全部賬戶"
        override fun selectedAccounts(count: Int) = "已選 $count 個賬戶"
        override val order = "排序"
        override val newestFirst = "倒序"
        override val oldestFirst = "正序"
        override val includeFuture = "未來"
        override val futureIncluded = "包含未來"
        override val excludeFuture = "隱藏未來"
        override val noTransactions = "暫無交易記錄"
        override val noTransactionsDesc = "使用底部快捷指令欄「+ 記一筆」記錄收支或轉賬"
        override val today = "今天"
        override val futureBadge = "未來"
        override val loadEarlier = "下拉載入更早記錄"
        override val scrollToFuture = "上滑查看未來記錄"
        override fun showingCount(visible: Int, total: Int) = "顯示 $visible / 共 $total 條"
        override val addTransaction = "+ 記一筆"
        override val advancedFilter = "進階篩選 ▾"
        override val selectAccountsTitle = "選擇賬戶"
        override val searchAccountsPlaceholder = "搜尋賬戶..."
        override val selectAll = "全選"
        override val clearSelection = "清空"
        override val advancedFilterTitle = "進階篩選"
        override val advancedFilterNotice = "進階篩選功能正在規劃中，後續將支援：\n• 自訂金額區間\n• 多幣種過濾\n• 標籤與關鍵字複合檢索\n• 交易對手方精確篩選"
        override val acknowledged = "知道了"
        override fun pullDownLoadEarlier(visible: Int, total: Int) = "↑ 下拉或點擊載入更早記錄 ($visible/$total)"
        override fun scrollUpLoadEarlier(visible: Int, total: Int) = "↓ 滑動或點擊載入更早記錄 ($visible/$total)"
    }

    override val clearing = object : ClearingStrings {
        override val title = "待辦結算"
        override val unreconciled = "待對賬"
        override val reconciled = "已對賬"
        override val pendingTransactions = "待辦交易"
        override val reconcile = "對賬"
        override val batchSettle = "批量結算"
        override val markAsCleared = "標記已平"
        override val noPendingItems = "暫無待結算項目"
        override val unsettledObligations = "待清算總額"
        override val creditStatements = "信用卡賬單"
        override val combineRepayment = "合併還款"
        override val noCreditStatements = "暫無信用卡賬單數據"
        override val loanAmortization = "貸款分期"
        override val newAmortization = "+ 新建分期"
        override val noLoans = "暫無貸款或分期計劃"
        override val oneClickRepay = "一鍵還款"
        override val recordRepayment = "記錄本期還款"
    }

    override val financeSettings = object : FinanceSettingsStrings {
        override val title = "財務設定"
        override val accountManagement = "五大類會計賬戶管理"
        override val createAccount = "新增賬戶"
        override val cancelCreate = "取消新增"
        override val liquidPrimary = "🟢 流動主力"
        override val sedimentAsset = "🟡 沉澱資產"
        override val passThroughExpense = "⚡ 轉出即消費"
        override val includeInNetWorth = "✓ 計入淨資產"
        override val excludeFromNetWorth = "✕ 排除淨資產"
        override val commercialLiability = "🔴 商業負債"
        override val personalLoan = "⚪ 個人借款"
        override val archived = "已歸檔"
        override val normal = "正常"
        override val reconcileBalance = "對賬"
        override val reconcileBalanceDesc = "輸入銀行/賬單真實當前餘額，系統將自動生成差額平賬交易："
        override val actualBalanceLabel = "真實餘額"
        override val autoPadButton = "平賬"
        override val newAccountTitle = "新建會計賬戶"
        override val accountPathLabel = "賬戶路徑 (如 Assets:Current:CMB)"
        override val accountNameLabel = "賬戶名稱 (如 招商銀行)"
        override val currencyLabel = "幣種 (CNY, USD等)"
        override val assetRoleLabel = "資產定位"
        override val liabilityNatureLabel = "負債性質"
        override val saveAccount = "儲存賬戶"
        override val searchPlaceholder = "搜尋賬戶..."
        override fun categoryName(category: AccountCategory) = when (category) {
            AccountCategory.ASSET -> "資產"
            AccountCategory.LIABILITY -> "負債"
            AccountCategory.INCOME -> "收入"
            AccountCategory.EXPENSE -> "支出"
            AccountCategory.EQUITY -> "權益"
        }
        override fun accountCount(count: Int) = "$count 個賬戶"
    }

    override val settings = object : SettingsStrings {
        override val title = "系統設定"
        override val appearanceCard = "外觀與語言"
        override val themeMode = "主題模式"
        override val themeSystem = "系統"
        override val themeLight = "淺色"
        override val themeDark = "深色"
        override val languageMode = "語言"
        override val langOptionSystem = "系統"
        override val langOptionEn = "英文"
        override val langOptionSc = "簡中"
        override val langOptionTc = "繁中"
        override val cloudCard = "雲端與同步"
        override val syncStatus = "同步狀態"
        override val syncing = "正在同步..."
        override val syncedJustNow = "剛剛完成同步"
        override val syncError = "同步異常"
        override val syncNow = "立即同步"
        override val fullPush = "完整推送"
        override val pullRemote = "拉取雲端"
        override val accountInfo = "賬號資訊"
        override val accountLogin = "登入"
        override val signedInAs = "已登入為"
        override val signOut = "退出登入"
        override val signIn = "登入賬號"
        override val tasksCard = "任務與專注"
        override val financeCard = "財務預設"
        override val accountsCard = "賬戶與分類"
        override val manageAccounts = "賬戶管理"
        override val addAccount = "新增賬戶"
        override val systemInfoCard = "系統資訊"
        override val version = "版本號"
        override val clientId = "客戶端標識"
        override val storageEngine = "存儲引擎"
        override val localDatabase = "本地資料庫"
        override val modulesCard = "功能模組"
        override val modulesEnabledCount = { enabled: Int, total: Int -> "已啟用 $enabled / 共 $total 項" }
        override val taskTitlePrefix = "預設任務前綴"
        override val focusMinutes = "專注時長 (分鐘)"
        override val shortBreakMinutes = "短休時長"
        override val longBreakMinutes = "長休時長"
        override val timezoneOverride = "時區覆蓋"
        override val dayRolloverTime = "日結切換時間"
        override val saveSettings = "儲存設定"
        override val financePresetsStatusDefault = "目前僅有基礎五類根賬戶，可載入預設子賬戶以快速上手記賬。"
        override val financePresetsStatusConfigured = "已配置子賬戶或已有記賬記錄。如需重置，可清除並恢復為基礎五类根賬戶。"
        override val loadPredefinedAccounts = "載入預設子賬戶"
        override val resetToDefaultFiveAccounts = "重置為基礎五類賬戶"
        override val guestAccountDesc = "登入賬號以開啟此設備的資料雲端同步。"
        override val signedInAccountDesc = "當前設備已登入並與伺服器保持同步。"
        override val softwareUpdatesCard = "軟體更新"
        override val currentBuild = "當前組建"
        override val apiVersion = "介面版本"
        override val checkForUpdates = "檢查更新"
        override val autoCheckUpdates = "自動檢查"
    }

    override val actions = object : ActionStrings {
        override val quickInput = "快速記賬"
        override val quickInputPlaceholder = "支援自然語言，如: 午餐 35 招行"
        override val updateAvailable = "發現新版本"
        override val installingUpdate = "正在安裝更新..."
        override val restartToApply = "重啟以套用更新"
        override val updateNow = "立即更新"
        override val quickCaptureHeader = "全域快速捕獲"
        override val workspacesHeader = "已啟用模組"
        override val configureModules = "配置 →"
        override val quickCaptureTask = "任務"
        override val quickCaptureExpense = "記賬"
        override val quickCaptureNote = "閃念"
        override val quickCaptureThing = "物品"
    }
}

// -------------------------------------------------------------------------
// Resolution Helpers
// -------------------------------------------------------------------------

fun resolveAppLanguage(
    preference: LanguagePreference,
    systemLanguage: AppLanguage = platformSystemLanguage(),
): AppLanguage = when (preference) {
    LanguagePreference.SYSTEM -> systemLanguage
    LanguagePreference.EN -> AppLanguage.EN
    LanguagePreference.SC -> AppLanguage.SC
    LanguagePreference.TC -> AppLanguage.TC
}

fun stringsForLanguage(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.EN -> EnStrings
    AppLanguage.SC -> ScStrings
    AppLanguage.TC -> TcStrings
}

fun languageOptionLabel(preference: LanguagePreference, activeLanguage: AppLanguage): String = when (activeLanguage) {
    AppLanguage.EN -> when (preference) {
        LanguagePreference.SYSTEM -> "System"
        LanguagePreference.EN -> "En"
        LanguagePreference.SC -> "SC"
        LanguagePreference.TC -> "TC"
    }
    AppLanguage.SC -> when (preference) {
        LanguagePreference.SYSTEM -> "系统"
        LanguagePreference.EN -> "英文"
        LanguagePreference.SC -> "简中"
        LanguagePreference.TC -> "繁中"
    }
    AppLanguage.TC -> when (preference) {
        LanguagePreference.SYSTEM -> "系統"
        LanguagePreference.EN -> "英文"
        LanguagePreference.SC -> "簡中"
        LanguagePreference.TC -> "繁中"
    }
}
