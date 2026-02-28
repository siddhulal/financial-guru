'use client'

import { useEffect, useState, useCallback } from 'react'
import { api } from '@/lib/api'
import { Transaction, Page, Account } from '@/lib/types'
import { formatCurrency, formatDate, getCategoryEmoji, cn } from '@/lib/utils'
import { Search, Filter, Download, AlertTriangle, RefreshCw } from 'lucide-react'

const CATEGORIES = [
  'Dining', 'Groceries', 'Shopping', 'Travel', 'Gas', 'Entertainment',
  'Utilities', 'Healthcare', 'Subscriptions', 'Education', 'Personal Care',
  'Home', 'Automotive', 'Insurance', 'Investments', 'Fees', 'Other',
]

export default function TransactionsPage() {
  const [data, setData] = useState<Page<Transaction> | null>(null)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(true)
  const [filters, setFilters] = useState({
    search: '',
    accountId: '',
    category: '',
    startDate: '',
    endDate: '',
    page: 0,
  })

  const setFilter = (key: string, value: string) =>
    setFilters(f => ({ ...f, [key]: value, page: 0 }))

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const params: Record<string, string> = { page: filters.page.toString(), size: '50' }
      if (filters.search) params.search = filters.search
      if (filters.accountId) params.accountId = filters.accountId
      if (filters.category) params.category = filters.category
      if (filters.startDate) params.startDate = filters.startDate
      if (filters.endDate) params.endDate = filters.endDate

      const [txns, accts] = await Promise.all([
        api.transactions.list(params),
        api.accounts.list(),
      ])
      setData(txns)
      setAccounts(accts)
    } finally {
      setLoading(false)
    }
  }, [filters])

  useEffect(() => { load() }, [load])

  const exportCsv = () => {
    if (!data) return
    const rows = [
      ['Date', 'Merchant', 'Category', 'Amount', 'Type', 'Account', 'Flagged'],
      ...data.content.map(t => [
        t.transactionDate,
        t.merchantName || t.description || '',
        t.category || '',
        t.amount.toString(),
        t.type || '',
        t.accountName || '',
        t.isFlagged ? 'Yes' : 'No',
      ])
    ]
    const csv = rows.map(r => r.join(',')).join('\n')
    const blob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'transactions.csv'
    a.click()
  }

  return (
    <div className="space-y-5 animate-slide-up">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-text-primary">Transactions</h1>
          <p className="text-sm text-text-muted mt-0.5">
            {data ? `${data.totalElements.toLocaleString()} transactions` : '—'}
          </p>
        </div>
        <button
          onClick={exportCsv}
          className="flex items-center gap-2 px-3 py-2 border border-border text-text-secondary text-sm rounded-lg hover:bg-background-tertiary transition-colors"
        >
          <Download className="w-4 h-4" /> Export CSV
        </button>
      </div>

      {/* Filters */}
      <div className="glass-card rounded-xl p-4 shadow-card">
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
          {/* Search */}
          <div className="col-span-2 md:col-span-1 relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-text-muted" />
            <input
              type="text"
              placeholder="Search..."
              value={filters.search}
              onChange={e => setFilter('search', e.target.value)}
              className="w-full pl-8 pr-3 py-2 bg-background-tertiary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-gold-500/50 transition-colors"
            />
          </div>

          {/* Account */}
          <select
            value={filters.accountId}
            onChange={e => setFilter('accountId', e.target.value)}
            className="bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:border-gold-500/50 transition-colors"
          >
            <option value="">All Accounts</option>
            {accounts.map(a => (
              <option key={a.id} value={a.id}>{a.name}</option>
            ))}
          </select>

          {/* Category */}
          <select
            value={filters.category}
            onChange={e => setFilter('category', e.target.value)}
            className="bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:border-gold-500/50 transition-colors"
          >
            <option value="">All Categories</option>
            {CATEGORIES.map(c => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>

          {/* Date range */}
          <input
            type="date"
            value={filters.startDate}
            onChange={e => setFilter('startDate', e.target.value)}
            className="bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:border-gold-500/50 transition-colors"
          />
          <input
            type="date"
            value={filters.endDate}
            onChange={e => setFilter('endDate', e.target.value)}
            className="bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:border-gold-500/50 transition-colors"
          />
        </div>
      </div>

      {/* Transaction table */}
      <div className="glass-card rounded-xl shadow-card overflow-hidden">
        {loading ? (
          <div className="text-center py-16 text-text-muted">
            <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2" />
            Loading transactions...
          </div>
        ) : !data || data.content.length === 0 ? (
          <div className="text-center py-16 text-text-muted">
            <p>No transactions found</p>
            <p className="text-sm mt-1 text-text-muted/60">Upload a statement to get started</p>
          </div>
        ) : (
          <>
            <table className="w-full">
              <thead>
                <tr className="border-b border-border">
                  <th className="text-left px-4 py-3 text-xs font-medium text-text-muted uppercase tracking-wider">Date</th>
                  <th className="text-left px-4 py-3 text-xs font-medium text-text-muted uppercase tracking-wider">Merchant</th>
                  <th className="text-left px-4 py-3 text-xs font-medium text-text-muted uppercase tracking-wider">Category</th>
                  <th className="text-left px-4 py-3 text-xs font-medium text-text-muted uppercase tracking-wider">Account</th>
                  <th className="text-right px-4 py-3 text-xs font-medium text-text-muted uppercase tracking-wider">Amount</th>
                  <th className="text-center px-4 py-3 text-xs font-medium text-text-muted uppercase tracking-wider">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.content.map((t) => (
                  <TransactionRow key={t.id} transaction={t} onUpdate={load} />
                ))}
              </tbody>
            </table>

            {/* Pagination */}
            {data.totalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t border-border">
                <p className="text-xs text-text-muted">
                  Page {data.number + 1} of {data.totalPages} · {data.totalElements} total
                </p>
                <div className="flex gap-2">
                  <button
                    disabled={data.number === 0}
                    onClick={() => setFilters(f => ({ ...f, page: f.page - 1 }))}
                    className="px-3 py-1.5 text-xs border border-border rounded-lg hover:bg-background-tertiary disabled:opacity-40 transition-colors"
                  >
                    Prev
                  </button>
                  <button
                    disabled={data.number >= data.totalPages - 1}
                    onClick={() => setFilters(f => ({ ...f, page: f.page + 1 }))}
                    className="px-3 py-1.5 text-xs border border-border rounded-lg hover:bg-background-tertiary disabled:opacity-40 transition-colors"
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}

function TransactionRow({ transaction: t, onUpdate }: { transaction: Transaction; onUpdate: () => void }) {
  const [editing, setEditing] = useState(false)
  const [category, setCategory] = useState(t.category || '')

  const saveCategory = async () => {
    await api.transactions.update(t.id, { category })
    setEditing(false)
    onUpdate()
  }

  const isDebit = t.type === 'DEBIT' || t.type === 'FEE' || t.type === 'INTEREST'

  return (
    <tr className={cn(
      'hover:bg-background-tertiary/50 transition-colors',
      t.isFlagged && 'bg-red-500/5'
    )}>
      <td className="px-4 py-3 text-xs font-num text-text-muted whitespace-nowrap">
        {formatDate(t.transactionDate)}
      </td>
      <td className="px-4 py-3">
        <p className="text-sm text-text-primary font-medium truncate max-w-[200px]">
          {t.merchantName || t.description || '—'}
        </p>
        {t.description && t.merchantName && t.description !== t.merchantName && (
          <p className="text-xs text-text-muted truncate max-w-[200px]">{t.description}</p>
        )}
      </td>
      <td className="px-4 py-3">
        {editing ? (
          <div className="flex gap-1">
            <select
              value={category}
              onChange={e => setCategory(e.target.value)}
              autoFocus
              className="text-xs bg-background-tertiary border border-gold-500/50 rounded px-2 py-1 text-text-primary"
            >
              <option value="">Uncategorized</option>
              {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
            <button onClick={saveCategory} className="text-xs text-gold-500 hover:text-gold-400 px-1">✓</button>
            <button onClick={() => setEditing(false)} className="text-xs text-text-muted px-1">✗</button>
          </div>
        ) : (
          <button
            onClick={() => setEditing(true)}
            className="text-xs text-text-secondary hover:text-text-primary transition-colors"
          >
            {t.category ? (
              <span>{getCategoryEmoji(t.category)} {t.category}</span>
            ) : (
              <span className="text-text-muted italic">Uncategorized</span>
            )}
          </button>
        )}
      </td>
      <td className="px-4 py-3 text-xs text-text-muted truncate max-w-[120px]">
        {t.accountName || '—'}
      </td>
      <td className="px-4 py-3 text-right">
        <span className={cn(
          'text-sm font-num font-semibold',
          isDebit ? 'text-text-primary' : 'text-green-400'
        )}>
          {isDebit ? '-' : '+'}{formatCurrency(t.amount)}
        </span>
      </td>
      <td className="px-4 py-3 text-center">
        {t.isFlagged ? (
          <span className="text-red-400" title={t.flagReason || 'Flagged'}>
            <AlertTriangle className="w-3.5 h-3.5 inline" />
          </span>
        ) : t.isRecurring ? (
          <span className="text-xs text-blue-400/70">Recurring</span>
        ) : null}
      </td>
    </tr>
  )
}
