// Account types
export type AccountType = 'CHECKING' | 'SAVINGS' | 'CREDIT_CARD' | 'LOAN'

export interface Account {
  id: string
  name: string
  institution: string | null
  type: AccountType
  last4: string | null
  creditLimit: number | null
  currentBalance: number | null
  availableCredit: number | null
  apr: number | null
  promoApr: number | null
  promoAprEndDate: string | null
  paymentDueDay: number | null
  minPayment: number | null
  rewardsProgram: string | null
  color: string | null
  isActive: boolean
  utilizationPercent: number | null
  daysUntilPromoAprExpiry: number | null
  createdAt: string
  updatedAt: string
}

// Transaction types
export type TransactionType = 'DEBIT' | 'CREDIT' | 'PAYMENT' | 'FEE' | 'INTEREST'

export interface Transaction {
  id: string
  accountId: string | null
  accountName: string | null
  statementId: string | null
  transactionDate: string
  postDate: string | null
  description: string | null
  merchantName: string | null
  category: string | null
  subcategory: string | null
  amount: number
  type: TransactionType | null
  referenceNumber: string | null
  isRecurring: boolean
  isFlagged: boolean
  flagReason: string | null
  notes: string | null
  createdAt: string
}

// Alert types
export type AlertType =
  | 'DUE_DATE'
  | 'APR_EXPIRY'
  | 'DUPLICATE_CHARGE'
  | 'ANOMALY'
  | 'SUBSCRIPTION'
  | 'HIGH_UTILIZATION'
  | 'OVERCHARGE'
  | 'UNUSUAL_MERCHANT'
  | 'LARGE_TRANSACTION'

export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface Alert {
  id: string
  type: AlertType
  severity: AlertSeverity
  title: string
  message: string
  aiExplanation: string | null
  accountId: string | null
  accountName: string | null
  transactionId: string | null
  isRead: boolean
  isResolved: boolean
  resolvedAt: string | null
  createdAt: string
}

// Statement types
export type StatementStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface Statement {
  id: string
  accountId: string | null
  account: { id: string; name: string } | null
  fileName: string
  filePath: string
  statementMonth: string | null
  startDate: string | null
  endDate: string | null
  openingBalance: number | null
  closingBalance: number | null
  totalCredits: number | null
  totalDebits: number | null
  minimumPayment: number | null
  paymentDueDate: string | null
  ytdTotalFees: number | null
  ytdTotalInterest: number | null
  ytdYear: number | null
  status: StatementStatus
  errorMessage: string | null
  createdAt: string
}

// Subscription types
export type SubscriptionFrequency = 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'ANNUAL'

export interface Subscription {
  id: string
  merchantName: string
  normalizedName: string | null
  amount: number | null
  frequency: SubscriptionFrequency | null
  accountId: string | null
  firstSeenDate: string | null
  lastChargedDate: string | null
  nextExpectedDate: string | null
  timesCharged: number
  annualCost: number | null
  category: string | null
  isActive: boolean
  isDuplicate: boolean
  notes: string | null
}

// Dashboard types
export interface DashboardData {
  totalCreditCardBalance: number
  totalCreditLimit: number
  totalAvailableCredit: number
  overallUtilizationPercent: number
  totalCheckingBalance: number
  totalSavingsBalance: number
  unreadAlertCount: number
  recentAlerts: Alert[]
  currentMonthSpend: number
  lastMonthSpend: number
  spendingChangePercent: number
  monthlySpendingTrend: { month: string; amount: number }[]
  categoryBreakdown: { category: string; amount: number; percent: number }[]
  topMerchants: { merchant: string; amount: number; count: number }[]
  accounts: Account[]
  upcomingPayments: {
    accountId: string
    accountName: string
    dueDate: string
    daysUntilDue: number
    balance: number
    minPayment: number
  }[]
  expiringPromoAprs: {
    accountId: string
    accountName: string
    promoApr: number
    regularApr: number
    endDate: string
    daysLeft: number
    balance: number
  }[]
  monthlySubscriptionCost: number
  activeSubscriptionCount: number
  duplicateSubscriptionCount: number
}

// Pagination
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// Budget types
export type BudgetStatus = 'GREEN' | 'YELLOW' | 'RED'

export interface Budget {
  id: string
  category: string
  monthlyLimit: number
  actualSpend: number
  percentUsed: number
  status: BudgetStatus
  projectedMonthEnd: number
  isActive: boolean
}

// Financial Profile
export interface FinancialProfile {
  id: string
  monthlyIncome: number | null
  incomeSource: string
  payFrequency: string
  emergencyFundTargetMonths: number
  notes: string | null
  createdAt: string
  updatedAt: string
}

// Health Score
export interface ScorePillar {
  name: string
  score: number
  maxScore: number
  explanation: string
}

export interface HealthScoreResponse {
  totalScore: number
  grade: string
  pillars: ScorePillar[]
  emergencyFundMonths: number
  emergencyFundTarget: number
  utilizationPercent: number
  savingsRate: number
}

// Debt Payoff
export interface CardPayoffDetail {
  accountId: string
  accountName: string
  currentBalance: number
  apr: number
  minPayment: number | null
  payoffDate: string
  interestPaid: number
  payoffOrder: number
}

export interface PayoffStrategy {
  strategy: string
  totalMonths: number
  payoffDate: string
  totalInterest: number
  totalPaid: number
  cardOrder: CardPayoffDetail[]
}

export interface DebtPayoffResponse {
  extraPayment: number
  totalCurrentDebt: number
  avalanche: PayoffStrategy
  snowball: PayoffStrategy
}

export interface WhatIfDataPoint {
  extraPayment: number
  avalancheMonths: number
  avalancheTotalInterest: number
  avalanchePayoffDate: string
  snowballMonths: number
}

// Net Worth
export interface ManualAsset {
  id: string
  name: string
  assetType: 'ASSET' | 'LIABILITY'
  assetClass: 'REAL_ESTATE' | 'VEHICLE' | 'INVESTMENT' | 'LOAN' | 'RETIREMENT' | 'OTHER'
  currentValue: number
  notes: string | null
  createdAt: string
  updatedAt: string
}

export interface NetWorthSnapshot {
  id: string
  snapshotDate: string
  liquidAssets: number
  creditCardDebt: number
  manualAssets: number
  manualLiabilities: number
  netWorth: number
  createdAt: string
}

export interface NetWorthResponse {
  netWorth: number
  liquidAssets: number
  creditCardDebt: number
  manualAssetsTotal: number
  manualLiabilities: number
  monthlyChange: number
  yearlyChange: number
  assets: ManualAsset[]
}

// Cash Flow
export interface CashFlowEvent {
  date: string
  type: 'INCOME' | 'PAYMENT' | 'SUBSCRIPTION'
  description: string
  amount: number
  runningBalance: number
  isDangerDay: boolean
}

export interface CashFlowResponse {
  year: number
  month: number
  startingBalance: number
  events: CashFlowEvent[]
}

// Annual Review
export interface AnnualReviewResponse {
  year: number
  totalSpending: number
  estimatedIncome: number
  savingsRate: number
  interestPaid: number
  feesPaid: number
  subscriptionAnnualCost: number
  netWorthChange: number
  categoryBreakdown: { category: string; amount: number }[]
  aiRecommendations: string[]
}

// Insight
export type InsightType = 'PRICE_INCREASE' | 'DUPLICATE_CROSS_CARD' | 'SUBSCRIPTION_CREEP' | 'ATM_FEE_WASTE' | 'REWARDS_OPPORTUNITY' | 'CATEGORY_YOY_SPIKE' | 'BILL_INCREASE'
export type InsightSeverity = 'INFO' | 'WARNING' | 'OPPORTUNITY' | 'CRITICAL'

export interface Insight {
  id: string
  type: InsightType
  title: string
  description: string
  actionText: string | null
  impactAmount: number | null
  severity: InsightSeverity
  merchantName: string | null
  category: string | null
  isDismissed: boolean
  generatedAt: string
}

// Savings Goals
export type GoalCategory = 'EMERGENCY_FUND' | 'VACATION' | 'DOWN_PAYMENT' | 'CAR' | 'RETIREMENT' | 'EDUCATION' | 'OTHER'

export interface SavingsGoal {
  id: string
  name: string
  category: GoalCategory
  targetAmount: number
  currentAmount: number
  targetDate: string | null
  linkedAccountId: string | null
  color: string | null
  isActive: boolean
  notes: string | null
  percentComplete: number
  monthlyRequired: number
  projectedDate: string | null
  monthsRemaining: number
  isOnTrack: boolean
  createdAt: string
  updatedAt: string
}

// Alert Rules
export type AlertRuleType = 'TRANSACTION_AMOUNT' | 'MONTHLY_CATEGORY_SPEND' | 'BALANCE_BELOW' | 'UTILIZATION_ABOVE'

export interface AlertRule {
  id: string
  name: string
  ruleType: AlertRuleType
  conditionOperator: string
  thresholdAmount: number
  category: string | null
  accountId: string | null
  isActive: boolean
  lastTriggeredAt: string | null
  createdAt: string
  updatedAt: string
}

// Account Balance Snapshot
export interface AccountBalanceSnapshot {
  id: string
  accountId: string
  snapshotDate: string
  balance: number
  createdAt: string
}

// Spending Heatmap
export interface HeatmapDay {
  date: string
  totalSpend: number
  transactionCount: number
  intensity: number  // 0-4
}

export interface SpendingHeatmapResponse {
  year: number
  days: HeatmapDay[]
  maxDailySpend: number
  totalAnnualSpend: number
}

// Merchant Trend
export interface MerchantTrendResponse {
  merchantName: string
  months: { month: string; amount: number }[]
  totalAnnual: number
  avgMonthly: number
  trend: 'INCREASING' | 'STABLE' | 'DECREASING'
}

// Duplicate Transactions
export interface DuplicateTransactionGroup {
  merchantName: string
  amount: number
  transactions: Transaction[]
  withinDays: number
}

// Credit Score
export interface CardUtilizationDetail {
  accountId: string
  accountName: string
  balance: number
  creditLimit: number
  utilizationPct: number
  recommendedPayment: number
  targetUtilization: number
}

export interface WhatIfCreditScenario {
  description: string
  paymentAmount: number
  newUtilizationPct: number
  estimatedScoreImpact: number
}

export interface CreditScoreResponse {
  estimatedScore: number
  utilizationImpact: 'EXCELLENT' | 'GOOD' | 'FAIR' | 'POOR'
  cards: CardUtilizationDetail[]
  recommendations: string[]
  whatIfScenarios: WhatIfCreditScenario[]
}

// FIRE Calculator
export interface YearProjection {
  year: number
  portfolioValue: number
  annualContribution: number
}

export interface FireCalculatorResponse {
  fiNumber: number
  currentSavings: number
  annualExpenses: number
  monthlySavings: number
  yearsToFire: number
  fireDate: string
  savingsRate: number
  monthlySavingsGap: number
  projections: YearProjection[]
  monteCarloP10: number[]
  monteCarloP50: number[]
  monteCarloP90: number[]
}

// Search
export interface SearchResult {
  query: string
  transactions: Transaction[]
  accounts: Account[]
  merchants: string[]
  totalResults: number
}

// Weekly Digest
export interface WeeklyDigestResponse {
  weekStart: string
  weekEnd: string
  totalSpend: number
  priorWeekSpend: number
  spendingChangePercent: number
  topTransactions: { merchant: string; amount: number; date: string; category: string }[]
  budgetStatuses: Budget[]
  upcomingPayments: { account: string; dueDate: string; balance: number }[]
  categoryBreakdown: { category: string; amount: number }[]
  unreadInsightCount: number
}
