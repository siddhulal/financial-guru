import {
  Account,
  Alert,
  AlertRule,
  AccountBalanceSnapshot,
  Budget,
  CashFlowResponse,
  AnnualReviewResponse,
  CreditScoreResponse,
  DebtPayoffResponse,
  DashboardData,
  DuplicateTransactionGroup,
  FireCalculatorResponse,
  FinancialProfile,
  HealthScoreResponse,
  Insight,
  ManualAsset,
  MerchantTrendResponse,
  NetWorthResponse,
  NetWorthSnapshot,
  Page,
  SavingsGoal,
  SearchResult,
  SpendingHeatmapResponse,
  Statement,
  Subscription,
  Transaction,
  WhatIfDataPoint,
  WeeklyDigestResponse,
} from './types'

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    ...options,
  })

  if (!res.ok) {
    const error = await res.text()
    throw new Error(`API Error ${res.status}: ${error}`)
  }

  return res.json()
}

// Dashboard
export const api = {
  dashboard: {
    get: () => apiFetch<DashboardData>('/api/dashboard'),
  },

  accounts: {
    list: () => apiFetch<Account[]>('/api/accounts'),
    get: (id: string) => apiFetch<Account>(`/api/accounts/${id}`),
    create: (data: Partial<Account>) =>
      apiFetch<Account>('/api/accounts', {
        method: 'POST',
        body: JSON.stringify(data),
      }),
    update: (id: string, data: Partial<Account>) =>
      apiFetch<Account>(`/api/accounts/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),
    delete: (id: string) =>
      apiFetch<void>(`/api/accounts/${id}`, { method: 'DELETE' }),
    transactions: (id: string, params?: Record<string, string>) => {
      const qs = params ? '?' + new URLSearchParams(params).toString() : ''
      return apiFetch<Page<Transaction>>(`/api/accounts/${id}/transactions${qs}`)
    },
  },

  statements: {
    list: () => apiFetch<Statement[]>('/api/statements'),
    get: (id: string) => apiFetch<Statement>(`/api/statements/${id}`),
    upload: (file: File, accountId?: string) => {
      const form = new FormData()
      form.append('file', file)
      if (accountId) form.append('accountId', accountId)
      return fetch(`${API_URL}/api/statements/upload`, {
        method: 'POST',
        body: form,
      }).then(r => r.json() as Promise<Statement>)
    },
    reprocess: (id: string) =>
      apiFetch<void>(`/api/statements/${id}/reprocess`, { method: 'POST' }),
    assignAccount: (statementId: string, accountId: string) =>
      apiFetch<Statement>(`/api/statements/${statementId}/assign-account/${accountId}`, { method: 'PUT' }),
  },

  transactions: {
    list: (params?: Record<string, string>) => {
      const qs = params ? '?' + new URLSearchParams(params).toString() : ''
      return apiFetch<Page<Transaction>>(`/api/transactions${qs}`)
    },
    get: (id: string) => apiFetch<Transaction>(`/api/transactions/${id}`),
    update: (id: string, data: Record<string, unknown>) =>
      apiFetch<Transaction>(`/api/transactions/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),
    anomalies: () => apiFetch<Transaction[]>('/api/transactions/anomalies'),
    search: (q: string) =>
      apiFetch<Page<Transaction>>(`/api/transactions/search?q=${encodeURIComponent(q)}`),
    bulkCategorize: (updates: { id: string; category: string }[]) =>
      apiFetch<void>('/api/transactions/bulk-categorize', { method: 'POST', body: JSON.stringify(updates) }),
  },

  alerts: {
    list: () => apiFetch<Alert[]>('/api/alerts'),
    unreadCount: () => apiFetch<{ count: number }>('/api/alerts/unread-count'),
    markRead: (id: string) =>
      apiFetch<Alert>(`/api/alerts/${id}/read`, { method: 'PUT' }),
    markResolved: (id: string) =>
      apiFetch<Alert>(`/api/alerts/${id}/resolve`, { method: 'PUT' }),
    delete: (id: string) =>
      apiFetch<void>(`/api/alerts/${id}`, { method: 'DELETE' }),
  },

  subscriptions: {
    list: () => apiFetch<Subscription[]>('/api/subscriptions'),
    duplicates: () => apiFetch<Subscription[]>('/api/subscriptions/duplicates'),
    update: (id: string, data: Record<string, unknown>) =>
      apiFetch<Subscription>(`/api/subscriptions/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),
  },

  analysis: {
    run: (statementId: string) =>
      apiFetch<{ status: string; message: string }>(
        `/api/analysis/run/${statementId}`,
        { method: 'POST' }
      ),
    get: (statementId: string) =>
      apiFetch<unknown[]>(`/api/analysis/${statementId}`),
  },

  chat: {
    send: (message: string) =>
      apiFetch<{ message: string; response: string }>('/api/chat', {
        method: 'POST',
        body: JSON.stringify({ message }),
      }),
    suggestions: () => apiFetch<string[]>('/api/chat/suggestions'),
  },

  insights: {
    debtPayoff: (extra?: number) =>
      apiFetch<DebtPayoffResponse>(`/api/insights/debt-payoff${extra !== undefined ? `?extra=${extra}` : ''}`),
    debtPayoffWhatIf: () =>
      apiFetch<WhatIfDataPoint[]>('/api/insights/debt-payoff/what-if'),
    healthScore: () => apiFetch<HealthScoreResponse>('/api/insights/health-score'),
    cashFlow: (year: number, month: number) =>
      apiFetch<CashFlowResponse>(`/api/insights/cash-flow?year=${year}&month=${month}`),
    annualReview: (year: number) =>
      apiFetch<AnnualReviewResponse>(`/api/insights/annual-review?year=${year}`),
    list: () => apiFetch<Insight[]>('/api/insights'),
    runEngine: () => apiFetch<{ count: number }>('/api/insights/run', { method: 'POST' }),
    dismiss: (id: string) =>
      apiFetch<Insight>(`/api/insights/${id}/dismiss`, { method: 'PUT' }),
    spendingHeatmap: (year?: number) =>
      apiFetch<SpendingHeatmapResponse>(`/api/insights/spending-heatmap${year ? `?year=${year}` : ''}`),
    merchantTrend: (merchant: string) =>
      apiFetch<MerchantTrendResponse>(`/api/insights/merchant-trend?merchant=${encodeURIComponent(merchant)}`),
    creditScore: () => apiFetch<CreditScoreResponse>('/api/insights/credit-score'),
    fireCalculator: (params: { age?: number; targetRetirementAge?: number; currentInvestments?: number; monthlyExpenses?: number }) => {
      const qs = new URLSearchParams()
      if (params.age) qs.set('age', String(params.age))
      if (params.targetRetirementAge) qs.set('targetRetirementAge', String(params.targetRetirementAge))
      if (params.currentInvestments) qs.set('currentInvestments', String(params.currentInvestments))
      if (params.monthlyExpenses) qs.set('monthlyExpenses', String(params.monthlyExpenses))
      return apiFetch<FireCalculatorResponse>(`/api/insights/fire-calculator?${qs}`)
    },
    duplicates: () => apiFetch<DuplicateTransactionGroup[]>('/api/insights/duplicates'),
  },

  budgets: {
    list: () => apiFetch<Budget[]>('/api/budgets'),
    upsert: (category: string, monthlyLimit: number) =>
      apiFetch<Budget>('/api/budgets', {
        method: 'POST',
        body: JSON.stringify({ category, monthlyLimit }),
      }),
    update: (id: string, data: { category?: string; monthlyLimit?: number }) =>
      apiFetch<Budget>(`/api/budgets/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),
    delete: (id: string) =>
      apiFetch<void>(`/api/budgets/${id}`, { method: 'DELETE' }),
  },

  networth: {
    get: () => apiFetch<NetWorthResponse>('/api/networth'),
    history: () => apiFetch<NetWorthSnapshot[]>('/api/networth/history'),
    captureSnapshot: () => apiFetch<NetWorthSnapshot>('/api/networth/snapshot', { method: 'POST' }),
    assets: {
      list: () => apiFetch<ManualAsset[]>('/api/networth/assets'),
      create: (data: { name: string; assetType: string; assetClass: string; currentValue: number; notes?: string }) =>
        apiFetch<ManualAsset>('/api/networth/assets', { method: 'POST', body: JSON.stringify(data) }),
      update: (id: string, data: Partial<{ name: string; assetType: string; assetClass: string; currentValue: number; notes: string }>) =>
        apiFetch<ManualAsset>(`/api/networth/assets/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
      delete: (id: string) =>
        apiFetch<void>(`/api/networth/assets/${id}`, { method: 'DELETE' }),
    },
  },

  profile: {
    get: () => apiFetch<FinancialProfile>('/api/profile'),
    update: (data: Partial<FinancialProfile>) =>
      apiFetch<FinancialProfile>('/api/profile', { method: 'PUT', body: JSON.stringify(data) }),
    detectIncome: () =>
      apiFetch<{ monthlyIncome: number }>('/api/profile/detect-income', { method: 'POST' }),
  },

  goals: {
    list: () => apiFetch<SavingsGoal[]>('/api/goals'),
    create: (data: Partial<SavingsGoal>) =>
      apiFetch<SavingsGoal>('/api/goals', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: string, data: Partial<SavingsGoal>) =>
      apiFetch<SavingsGoal>(`/api/goals/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id: string) =>
      apiFetch<void>(`/api/goals/${id}`, { method: 'DELETE' }),
    addProgress: (id: string, amount: number) =>
      apiFetch<SavingsGoal>(`/api/goals/${id}/progress`, { method: 'POST', body: JSON.stringify({ amount }) }),
  },

  search: (q: string) => apiFetch<SearchResult>(`/api/search?q=${encodeURIComponent(q)}`),

  alertRules: {
    list: () => apiFetch<AlertRule[]>('/api/alert-rules'),
    create: (data: Partial<AlertRule>) =>
      apiFetch<AlertRule>('/api/alert-rules', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: string, data: Partial<AlertRule>) =>
      apiFetch<AlertRule>(`/api/alert-rules/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id: string) =>
      apiFetch<void>(`/api/alert-rules/${id}`, { method: 'DELETE' }),
  },

  digest: () => apiFetch<WeeklyDigestResponse>('/api/digest'),

  export: {
    transactionsCSV: (from: string, to: string, accountId?: string, category?: string) => {
      const params = new URLSearchParams({ from, to })
      if (accountId) params.set('accountId', accountId)
      if (category) params.set('category', category)
      return fetch(`${API_URL}/api/export/transactions/csv?${params}`)
    },
    monthlyPDF: (year: number, month: number) =>
      fetch(`${API_URL}/api/export/monthly-pdf?year=${year}&month=${month}`),
  },

  balanceHistory: (accountId: string, days?: number) =>
    apiFetch<AccountBalanceSnapshot[]>(`/api/accounts/${accountId}/balance-history${days ? `?days=${days}` : ''}`),

  captureBalances: () => apiFetch<void>('/api/accounts/capture-balances', { method: 'POST' }),
}
