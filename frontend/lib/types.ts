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
