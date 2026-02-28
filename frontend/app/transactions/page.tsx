'use client'

import { useEffect, useState, useCallback } from 'react'
import { api } from '@/lib/api'
import { Transaction, Page, Account } from '@/lib/types'
import { formatCurrency, formatDate, getCategoryEmoji, cn } from '@/lib/utils'
import { Search, Filter, Download, AlertTriangle, RefreshCw, CheckSquare, Square, Tag } from 'lucide-react'

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

  // Bulk edit state
  const [bulkMode, setBulkMode] = useState(false)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [bulkCategory, setBulkCategory] = useState('')
  const [recategorizing, setRecategorizing] = useState(false)

  // Export state
  const [exporting, setExporting] = useState(false)

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

  // Reset selection when filters or page changes
  useEffect(() => {
    setSelected(new Set())
  }, [filters])

  const exportCsv = async () => {
    const from = filters.startDate || '2020-01-01'
    const to = filters.endDate || new Date().toISOString().slice(0, 10)
    setExporting(true)
    try {
      const res = await api.export.transactionsCSV(
        from,
        to,
        filters.accountId || undefined,
        filters.category || undefined,
      )
      if (res.ok) {
        const blob = await res.blob()
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `transactions-${from}-to-${to}.csv`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
      } else {
        // Fallback: build CSV from current page data
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
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
      }
    } catch {
      // Fallback on error
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
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    } finally {
      setExporting(false)
    }
  }

  function toggleSelect(id: string) {
    setSelected(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function toggleSelectAll() {
    if (!data) return
    if (selected.size === data.content.length) {
      setSelected(new Set())
    } else {
      setSelected(new Set(data.content.map(t => t.id)))
    }
  }

  async function handleBulkCategorize() {
    if (!bulkCategory || selected.size === 0) return
    setRecategorizing(true)
    try {
      const updates = Array.from(selected).map(id => ({ id, category: bulkCategory }))
      await api.transactions.bulkCategorize(updates)
      await load()
      setSelected(new Set())
      setBulkCategory('')
    } catch {
      // ignore
    } finally {
      setRecategorizing(false)
    }
  }

  const allSelected = data ? selected.size === data.content.length && data.content.length > 0 : false

  return (
    <div className="space-y-5 animate-slide-up">
      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-semibold text-text-primary">Transactions</h1>
          <p className="text-sm text-text-muted mt-0.5">
            {data ? `${data.totalElements.toLocaleString()} transactions` : '—'}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => { setBulkMode(m => !m); setSelected(new Set()) }}
            className={cn(
              'flex items-center gap-2 px-3 py-2 text-sm rounded-lg border transition-colors',
              bulkMode
                ? 'bg-gold-500/10 border-gold-500/30 text-gold-500'
                : 'border-border text-text-secondary hover:bg-background-tertiary'
            )}
          >
            <CheckSquare className="w-4 h-4" />
            {bulkMode ? 'Exit Bulk' : 'Bulk Edit'}
          </button>
          <button
            onClick={exportCsv}
            disabled={exporting}
            className="flex items-center gap-2 px-3 py-2 border border-border text-text-secondary text-sm rounded-lg hover:bg-background-tertiary disabled:opacity-50 transition-colors"
          >
            {exporting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
            Export CSV
          </button>
        </div>
      </div>

      {/* Bulk edit bar */}
      {bulkMode && (
        <div className="bg-gold-500/5 border border-gold-500/20 rounded-xl px-4 py-3 flex items-center gap-4 flex-wrap">
          <div className="flex items-center gap-2">
            <button onClick={toggleSelectAll} className="text-gold-500 hover:text-gold-400 transition-colors">
              {allSelected ? <CheckSquare className="w-4 h-4" /> : <Square className="w-4 h-4" />}
            </button>
            <span className="text-sm text-text-secondary">
              {selected.size > 0 ? `${selected.size} selected` : 'Select transactions to bulk recategorize'}
            </span>
          </div>
          {selected.size > 0 && (
            <div className="flex items-center gap-2">
              <Tag className="w-4 h-4 text-text-muted" />
              <select
                value={bulkCategory}
                onChange={e => setBulkCategory(e.target.value)}
                className="bg-background-tertiary border border-border rounded-lg px-3 py-1.5 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
              >
                <option value="">Select category...</option>
                {CATEGORIES.map(c => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
              <button
                onClick={handleBulkCategorize}
                disabled={!bulkCategory || recategorizing}
                className="px-3 py-1.5 bg-gold-500 text-black text-sm font-semibold rounded-lg hover:bg-gold-400 disabled:opacity-50 transition-colors"
              >
                {recategorizing ? 'Updating...' : 'Recategorize Selected'}
              </button>
            </div>
          )}
        </div>
      )}

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
                  {bulkMode && (
                    <th className="px-4 py-3 w-10">
                      <button onClick={toggleSelectAll} className="text-gold-500 hover:text-gold-400 transition-colors">
                        {allSelected ? <CheckSquare className="w-4 h-4" /> : <Square className="w-4 h-4" />}
                      </button>
                    </th>
                  )}
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
                  <TransactionRow
                    key={t.id}
                    transaction={t}
                    onUpdate={load}
                    bulkMode={bulkMode}
                    isSelected={selected.has(t.id)}
                    onToggleSelect={() => toggleSelect(t.id)}
                  />
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

function TransactionRow({
  transaction: t,
  onUpdate,
  bulkMode,
  isSelected,
  onToggleSelect,
}: {
  transaction: Transaction
  onUpdate: () => void
  bulkMode: boolean
  isSelected: boolean
  onToggleSelect: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [category, setCategory] = useState(t.category || '')

  const saveCategory = async () => {
    await api.transactions.update(t.id, { category })
    setEditing(false)
    onUpdate()
  }

  const isDebit = t.type === 'DEBIT' || t.type === 'FEE' || t.type === 'INTEREST'

  return (
    <tr
      className={cn(
        'hover:bg-background-tertiary/50 transition-colors',
        t.isFlagged && 'bg-red-500/5',
        isSelected && 'bg-gold-500/5',
        bulkMode && 'cursor-pointer'
      )}
      onClick={() => bulkMode && onToggleSelect()}
    >
      {bulkMode && (
        <td className="px-4 py-3 w-10">
          <button
            onClick={e => { e.stopPropagation(); onToggleSelect() }}
            className="text-gold-500 hover:text-gold-400 transition-colors"
          >
            {isSelected ? <CheckSquare className="w-4 h-4" /> : <Square className="w-4 h-4" />}
          </button>
        </td>
      )}
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
      <td className="px-4 py-3" onClick={e => e.stopPropagation()}>
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
            onClick={() => !bulkMode && setEditing(true)}
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
