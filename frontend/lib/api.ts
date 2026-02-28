import { Account, Alert, DashboardData, Page, Statement, Subscription, Transaction } from './types'

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
}
