'use client'

import { useEffect, useState } from 'react'
import { useParams } from 'next/navigation'
import { api } from '@/lib/api'
import { Account, Statement, Transaction, Page, AccountBalanceSnapshot } from '@/lib/types'
import {
  formatCurrency, formatPercent, formatDate,
  getUtilizationColor, getUtilizationBarColor, getCategoryEmoji
} from '@/lib/utils'
import { CreditCard, ArrowLeft, AlertTriangle, Pencil, TrendingUp, Camera } from 'lucide-react'
import Link from 'next/link'
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts'

export default function AccountDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [account, setAccount] = useState<Account | null>(null)
  const [transactions, setTransactions] = useState<Page<Transaction> | null>(null)
  const [latestStatement, setLatestStatement] = useState<Statement | null>(null)
  const [loading, setLoading] = useState(true)
  const [editingLast4, setEditingLast4] = useState(false)
  const [last4Input, setLast4Input] = useState('')
  const [savingLast4, setSavingLast4] = useState(false)
  const [editingMinPayment, setEditingMinPayment] = useState(false)
  const [minPaymentInput, setMinPaymentInput] = useState('')
  const [savingMinPayment, setSavingMinPayment] = useState(false)
  const [editingDueDay, setEditingDueDay] = useState(false)
  const [dueDayInput, setDueDayInput] = useState('')
  const [savingDueDay, setSavingDueDay] = useState(false)

  // Balance history state
  const [balanceHistory, setBalanceHistory] = useState<AccountBalanceSnapshot[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [capturingBalance, setCapturingBalance] = useState(false)
  const [captureSuccess, setCaptureSuccess] = useState(false)

  useEffect(() => {
    Promise.all([
      api.accounts.get(id),
      api.accounts.transactions(id, { size: '20' }),
      api.statements.list(),
    ]).then(([acct, txns, stmts]) => {
      setAccount(acct)
      setTransactions(txns)
      // Find the most recent completed statement for this account
      const accountStmts = stmts
        .filter(s => s.account?.id === id && s.status === 'COMPLETED')
        .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      setLatestStatement(accountStmts[0] ?? null)
    }).finally(() => setLoading(false))

    // Load balance history
    loadBalanceHistory()
  }, [id])

  async function loadBalanceHistory() {
    setHistoryLoading(true)
    try {
      const history = await api.balanceHistory(id, 90)
      setBalanceHistory(history)
    } catch {
      // ignore — endpoint may not exist yet
    } finally {
      setHistoryLoading(false)
    }
  }

  async function captureBalance() {
    setCapturingBalance(true)
    setCaptureSuccess(false)
    try {
      await api.captureBalances()
      setCaptureSuccess(true)
      await loadBalanceHistory()
      setTimeout(() => setCaptureSuccess(false), 3000)
    } catch {
      // ignore
    } finally {
      setCapturingBalance(false)
    }
  }

  async function saveDueDay() {
    if (!account) return
    const day = parseInt(dueDayInput, 10)
    if (isNaN(day) || day < 1 || day > 31) return
    setSavingDueDay(true)
    try {
      const updated = await api.accounts.update(id, { paymentDueDay: day })
      setAccount(updated)
      setEditingDueDay(false)
    } catch (e) {
      console.error('Failed to update payment due day', e)
    } finally {
      setSavingDueDay(false)
    }
  }

  async function saveMinPayment() {
    if (!account) return
    const val = parseFloat(minPaymentInput.replace(/[^0-9.]/g, ''))
    if (isNaN(val) || val < 0) return
    setSavingMinPayment(true)
    try {
      const updated = await api.accounts.update(id, { minPayment: val })
      setAccount(updated)
      setEditingMinPayment(false)
    } catch (e) {
      console.error('Failed to update min payment', e)
    } finally {
      setSavingMinPayment(false)
    }
  }

  async function saveLast4() {
    if (!account || !last4Input.trim()) return
    setSavingLast4(true)
    try {
      const updated = await api.accounts.update(id, { last4: last4Input.trim() })
      setAccount(updated)
      setEditingLast4(false)
    } catch (e) {
      console.error('Failed to update last4', e)
    } finally {
      setSavingLast4(false)
    }
  }

  if (loading) {
    return <div className="text-center py-16 text-text-muted">Loading account...</div>
  }
  if (!account) {
    return <div className="text-center py-16 text-text-muted">Account not found</div>
  }

  const util = account.utilizationPercent || 0

  return (
    <div className="space-y-5 animate-slide-up">
      {/* Back */}
      <Link href="/accounts" className="flex items-center gap-2 text-sm text-text-muted hover:text-text-primary transition-colors">
        <ArrowLeft className="w-4 h-4" /> Back to Accounts
      </Link>

      {/* Card hero */}
      <div
        className="rounded-2xl p-6 relative overflow-hidden"
        style={{
          background: account.color
            ? `linear-gradient(135deg, ${account.color}CC, ${account.color}66)`
            : 'linear-gradient(135deg, #1A56DB, #7C3AED)'
        }}
      >
        <div className="absolute top-0 right-0 w-40 h-40 rounded-full opacity-10"
          style={{ background: 'radial-gradient(circle, white, transparent)', transform: 'translate(30%, -30%)' }} />
        <div className="flex justify-between items-start">
          <div>
            <p className="text-white/70 text-sm">{account.institution}</p>
            <h1 className="text-xl font-bold text-white mt-1">{account.name}</h1>
            {editingLast4 ? (
              <div className="flex items-center gap-2 mt-1">
                <span className="text-white/60 text-sm font-mono">•••• •••• ••••</span>
                <input
                  autoFocus
                  maxLength={4}
                  pattern="\d{4}"
                  value={last4Input}
                  onChange={e => setLast4Input(e.target.value.replace(/\D/g, '').slice(0, 4))}
                  onKeyDown={e => { if (e.key === 'Enter') saveLast4(); if (e.key === 'Escape') setEditingLast4(false) }}
                  className="w-16 bg-white/20 text-white font-mono text-sm rounded px-2 py-0.5 outline-none border border-white/40 focus:border-white"
                  placeholder="0000"
                />
                <button onClick={saveLast4} disabled={savingLast4}
                  className="text-xs text-white bg-white/20 hover:bg-white/30 px-2 py-0.5 rounded transition-colors">
                  {savingLast4 ? '...' : 'Save'}
                </button>
                <button onClick={() => setEditingLast4(false)}
                  className="text-xs text-white/60 hover:text-white transition-colors">Cancel</button>
              </div>
            ) : (
              <button
                className="flex items-center gap-1.5 mt-1 group"
                onClick={() => { setLast4Input(account.last4 || ''); setEditingLast4(true) }}
              >
                <span className="text-white/60 text-sm font-mono">•••• •••• •••• {account.last4 || '????'}</span>
                <Pencil className="w-3 h-3 text-white/40 group-hover:text-white/80 transition-colors" />
              </button>
            )}
          </div>
          <CreditCard className="w-8 h-8 text-white/50" />
        </div>
        <div className="mt-6 flex items-end justify-between">
          <div>
            <p className="text-white/60 text-xs">Current Balance</p>
            <p className="text-3xl font-bold font-num text-white">{formatCurrency(account.currentBalance)}</p>
          </div>
          {account.type === 'CREDIT_CARD' && (
            <div className="text-right">
              <p className="text-white/60 text-xs">Credit Limit</p>
              <p className="text-xl font-num font-bold text-white">{formatCurrency(account.creditLimit)}</p>
            </div>
          )}
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {account.type === 'CREDIT_CARD' && (
          <>
            <div className="glass-card rounded-xl p-4 shadow-card">
              <p className="text-xs text-text-muted mb-1">Utilization</p>
              <p className={`text-2xl font-num font-semibold ${getUtilizationColor(util)}`}>
                {formatPercent(util)}
              </p>
              <div className="mt-2 h-1 bg-border rounded-full">
                <div className={`h-full rounded-full ${getUtilizationBarColor(util)}`}
                  style={{ width: `${Math.min(util, 100)}%` }} />
              </div>
            </div>
            <div className="glass-card rounded-xl p-4 shadow-card">
              {(() => {
                const promoActive = account.promoApr != null &&
                  account.daysUntilPromoAprExpiry != null &&
                  account.daysUntilPromoAprExpiry > 0
                const currentApr = promoActive ? account.promoApr : account.apr
                const daysLeft = account.daysUntilPromoAprExpiry ?? 0
                const urgent = daysLeft <= 60 && daysLeft > 0
                return (
                  <>
                    <p className="text-xs text-text-muted mb-1">Current APR</p>
                    <p className={`text-2xl font-num font-semibold ${promoActive ? 'text-green-400' : 'text-text-primary'}`}>
                      {currentApr}%
                    </p>
                    {promoActive ? (
                      <div className="mt-1 space-y-0.5">
                        <p className="text-xs text-text-muted">Regular rate: {account.apr}%</p>
                        <p className={`text-xs font-medium ${urgent ? 'text-yellow-400' : 'text-text-muted'}`}>
                          Promo ends {formatDate(account.promoAprEndDate!)}
                          {' '}
                          <span className={urgent ? 'text-yellow-400' : 'text-text-muted/60'}>
                            ({daysLeft}d left)
                          </span>
                        </p>
                      </div>
                    ) : (
                      account.promoApr != null && (
                        <p className="text-xs text-text-muted mt-1">Promo expired ({account.promoApr}%)</p>
                      )
                    )}
                  </>
                )
              })()}
            </div>
            <div className="glass-card rounded-xl p-4 shadow-card">
              <p className="text-xs text-text-muted mb-1">Payment Due</p>
              {editingDueDay ? (
                <div className="flex items-center gap-1.5 mb-1">
                  <span className="text-xs text-text-muted">Day</span>
                  <input
                    autoFocus
                    value={dueDayInput}
                    onChange={e => setDueDayInput(e.target.value.replace(/\D/g, '').slice(0, 2))}
                    onKeyDown={e => { if (e.key === 'Enter') saveDueDay(); if (e.key === 'Escape') setEditingDueDay(false) }}
                    className="w-12 bg-background-tertiary text-text-primary text-sm font-num rounded px-2 py-0.5 outline-none border border-border focus:border-gold-500"
                    placeholder="14"
                  />
                  <span className="text-xs text-text-muted">of month</span>
                  <button onClick={saveDueDay} disabled={savingDueDay}
                    className="text-xs text-gold-500 hover:text-gold-400 transition-colors">
                    {savingDueDay ? '...' : 'Save'}
                  </button>
                  <button onClick={() => setEditingDueDay(false)}
                    className="text-xs text-text-muted hover:text-text-primary transition-colors">✕</button>
                </div>
              ) : (
                <button
                  className="flex items-center gap-1.5 mb-1 group"
                  onClick={() => {
                    setDueDayInput(account.paymentDueDay?.toString() ?? '')
                    setEditingDueDay(true)
                  }}
                >
                  <p className="text-2xl font-num font-semibold text-text-primary">
                    {latestStatement?.paymentDueDate
                      ? formatDate(latestStatement.paymentDueDate)
                      : account.paymentDueDay ? `Day ${account.paymentDueDay}` : '—'}
                  </p>
                  <Pencil className="w-3.5 h-3.5 text-text-muted/40 group-hover:text-gold-500 transition-colors mt-1" />
                </button>
              )}
              {/* Min payment — show from statement, or account, or editable placeholder */}
              {latestStatement?.minimumPayment != null ? (
                <p className="text-xs text-text-muted mt-1">Min: {formatCurrency(latestStatement.minimumPayment)}</p>
              ) : editingMinPayment ? (
                <div className="flex items-center gap-1.5 mt-1">
                  <span className="text-xs text-text-muted">Min: $</span>
                  <input
                    autoFocus
                    value={minPaymentInput}
                    onChange={e => setMinPaymentInput(e.target.value.replace(/[^0-9.]/g, ''))}
                    onKeyDown={e => { if (e.key === 'Enter') saveMinPayment(); if (e.key === 'Escape') setEditingMinPayment(false) }}
                    className="w-20 bg-background-tertiary text-text-primary text-xs font-num rounded px-2 py-0.5 outline-none border border-border focus:border-gold-500"
                    placeholder="0.00"
                  />
                  <button onClick={saveMinPayment} disabled={savingMinPayment}
                    className="text-xs text-gold-500 hover:text-gold-400 transition-colors">
                    {savingMinPayment ? '...' : 'Save'}
                  </button>
                  <button onClick={() => setEditingMinPayment(false)}
                    className="text-xs text-text-muted hover:text-text-primary transition-colors">✕</button>
                </div>
              ) : (
                <button
                  className="flex items-center gap-1 mt-1 group"
                  onClick={() => { setMinPaymentInput(account.minPayment?.toString() ?? ''); setEditingMinPayment(true) }}
                >
                  <span className="text-xs text-text-muted">
                    Min: {account.minPayment != null ? formatCurrency(account.minPayment) : '—'}
                  </span>
                  <Pencil className="w-3 h-3 text-text-muted/40 group-hover:text-gold-500 transition-colors" />
                </button>
              )}
            </div>
            <div className="glass-card rounded-xl p-4 shadow-card">
              <p className="text-xs text-text-muted mb-1">Available Credit</p>
              <p className="text-2xl font-num font-semibold text-success">
                {formatCurrency(account.availableCredit)}
              </p>
            </div>
          </>
        )}
      </div>

      {/* YTD Summary + Rewards — side by side */}
      {latestStatement && (latestStatement.ytdTotalFees !== null || latestStatement.ytdTotalInterest !== null) && (() => {
        const fees     = latestStatement.ytdTotalFees     ?? 0
        const interest = latestStatement.ytdTotalInterest ?? 0
        const total    = fees + interest
        return (
          <div className={`grid gap-4 ${account.rewardsProgram ? 'grid-cols-3' : 'grid-cols-3'}`}>
            {/* Fees */}
            <div className="glass-card rounded-xl p-5 shadow-card border-l-2 border-red-500/40">
              <p className="text-xs text-text-muted uppercase tracking-wide mb-2">
                YTD Fees {latestStatement.ytdYear && <span className="text-text-muted/50">· {latestStatement.ytdYear}</span>}
              </p>
              <p className={`text-3xl font-num font-bold ${fees > 0 ? 'text-red-400' : 'text-success'}`}>
                {formatCurrency(fees)}
              </p>
              <p className="text-xs text-text-muted mt-1">Annual fees &amp; late charges</p>
            </div>

            {/* Interest */}
            <div className="glass-card rounded-xl p-5 shadow-card border-l-2 border-orange-500/40">
              <p className="text-xs text-text-muted uppercase tracking-wide mb-2">
                YTD Interest {latestStatement.ytdYear && <span className="text-text-muted/50">· {latestStatement.ytdYear}</span>}
              </p>
              <p className={`text-3xl font-num font-bold ${interest > 0 ? 'text-orange-400' : 'text-success'}`}>
                {formatCurrency(interest)}
              </p>
              <p className="text-xs text-text-muted mt-1">Cost of carrying a balance</p>
            </div>

            {/* Total cost OR Rewards if present */}
            {account.rewardsProgram ? (
              <div className="glass-card rounded-xl p-5 shadow-card border-l-2 border-gold-500/40">
                <p className="text-xs text-text-muted uppercase tracking-wide mb-2">Rewards Program</p>
                <p className="text-base font-semibold text-gold-400 leading-snug">🎁 {account.rewardsProgram}</p>
              </div>
            ) : (
              <div className="glass-card rounded-xl p-5 shadow-card border-l-2 border-border">
                <p className="text-xs text-text-muted uppercase tracking-wide mb-2">Total Cost YTD</p>
                <p className={`text-3xl font-num font-bold ${total > 0 ? 'text-red-400' : 'text-success'}`}>
                  {formatCurrency(total)}
                </p>
                <p className="text-xs text-text-muted mt-1">Fees + interest combined</p>
              </div>
            )}
          </div>
        )
      })()}

      {/* Rewards standalone — only if YTD not shown */}
      {account.rewardsProgram && !(latestStatement?.ytdTotalFees !== null || latestStatement?.ytdTotalInterest !== null) && (
        <div className="glass-card rounded-xl p-5 shadow-card border-l-2 border-gold-500/40">
          <p className="text-xs text-text-muted uppercase tracking-wide mb-2">Rewards Program</p>
          <p className="text-base font-semibold text-gold-400">🎁 {account.rewardsProgram}</p>
        </div>
      )}

      {/* Recent transactions */}
      <div className="glass-card rounded-xl shadow-card overflow-hidden">
        <div className="px-5 py-4 border-b border-border flex items-center justify-between">
          <h2 className="text-sm font-semibold text-text-primary">Recent Transactions</h2>
          <Link href={`/transactions?accountId=${account.id}`}
            className="text-xs text-gold-500 hover:text-gold-400 transition-colors">
            View all →
          </Link>
        </div>
        {!transactions || transactions.content.length === 0 ? (
          <div className="text-center py-8 text-text-muted text-sm">No transactions yet</div>
        ) : (
          <div className="divide-y divide-border">
            {transactions.content.map(t => (
              <div key={t.id} className="flex items-center gap-3 px-5 py-3">
                <div className="w-8 h-8 rounded-lg bg-background-tertiary flex items-center justify-center text-sm flex-shrink-0">
                  {getCategoryEmoji(t.category || 'Other')}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-text-primary truncate">{t.merchantName || t.description}</p>
                  <p className="text-xs text-text-muted">{formatDate(t.transactionDate)} · {t.category || 'Uncategorized'}</p>
                </div>
                {t.isFlagged && <AlertTriangle className="w-3.5 h-3.5 text-red-400 flex-shrink-0" />}
                <p className={`text-sm font-num font-semibold flex-shrink-0 ${
                  t.type === 'PAYMENT' || t.type === 'CREDIT' ? 'text-green-400' : 'text-text-primary'
                }`}>
                  {t.type === 'PAYMENT' || t.type === 'CREDIT' ? '+' : '-'}{formatCurrency(t.amount)}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Balance History */}
      <div className="glass-card rounded-xl shadow-card overflow-hidden">
        <div className="px-5 py-4 border-b border-border flex items-center justify-between">
          <div className="flex items-center gap-2">
            <TrendingUp className="w-4 h-4 text-gold-500" />
            <h2 className="text-sm font-semibold text-text-primary">Balance History (90 days)</h2>
          </div>
          <button
            onClick={captureBalance}
            disabled={capturingBalance}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs border border-border rounded-lg text-text-secondary hover:bg-background-tertiary disabled:opacity-50 transition-colors"
          >
            {capturingBalance ? (
              <div className="w-3 h-3 border-t border-current rounded-full animate-spin" />
            ) : (
              <Camera className="w-3.5 h-3.5" />
            )}
            {captureSuccess ? 'Captured!' : 'Capture Today'}
          </button>
        </div>

        <div className="p-5">
          {historyLoading ? (
            <div className="flex items-center justify-center h-32">
              <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-gold-500" />
            </div>
          ) : balanceHistory.length === 0 ? (
            <div className="text-center py-8 text-text-muted text-sm">
              <p>No balance history yet.</p>
              <p className="text-xs mt-1">Click "Capture Today" to start tracking your balance over time.</p>
            </div>
          ) : (
            <div className="h-48">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart
                  data={balanceHistory.map(s => ({
                    date: s.snapshotDate,
                    balance: s.balance,
                  }))}
                  margin={{ top: 4, right: 4, left: 0, bottom: 0 }}
                >
                  <defs>
                    <linearGradient id="balanceGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#F59E0B" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="#F59E0B" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1A1A24" />
                  <XAxis
                    dataKey="date"
                    tick={{ fill: '#6B7280', fontSize: 10 }}
                    tickLine={false}
                    tickFormatter={d => {
                      const dt = new Date(d)
                      return `${dt.getMonth() + 1}/${dt.getDate()}`
                    }}
                  />
                  <YAxis
                    tick={{ fill: '#6B7280', fontSize: 10 }}
                    tickFormatter={v => `$${(v / 1000).toFixed(0)}k`}
                    tickLine={false}
                    axisLine={false}
                  />
                  <Tooltip
                    contentStyle={{ backgroundColor: '#111118', border: '1px solid #2A2A35', borderRadius: '8px', color: '#F9FAFB' }}
                    formatter={(v: number) => [
                      `$${v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
                      'Balance'
                    ]}
                    labelFormatter={label => `Date: ${label}`}
                  />
                  <Area
                    type="monotone"
                    dataKey="balance"
                    stroke="#F59E0B"
                    strokeWidth={2}
                    fill="url(#balanceGrad)"
                    dot={false}
                    activeDot={{ r: 4, fill: '#F59E0B' }}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
