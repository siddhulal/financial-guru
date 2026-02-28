'use client'

import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { WeeklyDigestResponse, Budget } from '@/lib/types'
import { cn } from '@/lib/utils'
import { TrendingUp, TrendingDown, CreditCard, Calendar, Lightbulb, Receipt } from 'lucide-react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell
} from 'recharts'

function formatDateRange(start: string, end: string): string {
  const s = new Date(start)
  const e = new Date(end)
  const fmt = (d: Date) => d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  return `${fmt(s)} – ${fmt(e)}`
}

function BudgetTrafficLight({ budget }: { budget: Budget }) {
  const color =
    budget.status === 'GREEN' ? 'bg-green-500' :
    budget.status === 'YELLOW' ? 'bg-yellow-500' : 'bg-red-500'
  const textColor =
    budget.status === 'GREEN' ? 'text-green-400' :
    budget.status === 'YELLOW' ? 'text-yellow-400' : 'text-red-400'

  return (
    <div className="flex items-center justify-between py-2 border-b border-border/50 last:border-0">
      <div className="flex items-center gap-2">
        <div className={cn('w-2 h-2 rounded-full flex-shrink-0', color)} />
        <span className="text-sm text-text-primary">{budget.category}</span>
      </div>
      <div className="flex items-center gap-3 text-xs">
        <span className="text-text-muted font-mono">
          ${budget.actualSpend.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}
          {' / '}
          ${budget.monthlyLimit.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}
        </span>
        <span className={cn('font-semibold', textColor)}>{budget.percentUsed.toFixed(0)}%</span>
      </div>
    </div>
  )
}

export default function DigestPage() {
  const [data, setData] = useState<WeeklyDigestResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.digest()
      .then(setData)
      .catch(() => setError('Failed to load weekly digest'))
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-gold-500" />
      </div>
    )
  }
  if (error) return <div className="p-6 text-red-400">{error}</div>
  if (!data) return null

  const spendChange = data.spendingChangePercent
  const isUp = spendChange > 0
  const isDown = spendChange < 0

  const categoryChartData = [...data.categoryBreakdown]
    .sort((a, b) => b.amount - a.amount)
    .slice(0, 8)

  return (
    <div className="space-y-6 animate-slide-up">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-text-primary">Weekly Digest</h1>
        <p className="text-text-muted text-sm mt-1">
          {data.weekStart && data.weekEnd ? formatDateRange(data.weekStart, data.weekEnd) : 'This week'}
        </p>
      </div>

      {/* Spending hero card */}
      <div className={cn(
        'rounded-xl border p-8 text-center',
        isUp ? 'bg-red-500/5 border-red-500/20' :
        isDown ? 'bg-green-500/5 border-green-500/20' :
        'bg-background-secondary border-border'
      )}>
        <p className="text-xs text-text-muted uppercase tracking-wider mb-2">This Week's Spending</p>
        <p className="text-5xl font-mono font-bold text-text-primary">
          ${data.totalSpend.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </p>
        <div className={cn(
          'flex items-center justify-center gap-1.5 mt-3 text-base font-semibold',
          isUp ? 'text-red-400' : isDown ? 'text-green-400' : 'text-text-muted'
        )}>
          {isUp ? <TrendingUp className="w-5 h-5" /> : isDown ? <TrendingDown className="w-5 h-5" /> : null}
          <span>
            {isUp ? '+' : ''}{spendChange.toFixed(1)}% vs prior week
          </span>
        </div>
        <p className="text-xs text-text-muted mt-1">
          Prior week: ${data.priorWeekSpend.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </p>
      </div>

      {/* 4 stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-background-secondary rounded-xl border border-border p-5">
          <div className="flex items-center gap-2 mb-2">
            <Receipt className="w-4 h-4 text-text-muted" />
            <p className="text-xs text-text-muted">Total Spend</p>
          </div>
          <p className="text-2xl font-mono font-bold text-text-primary">
            ${data.totalSpend.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}
          </p>
        </div>

        <div className="bg-background-secondary rounded-xl border border-border p-5">
          <div className="flex items-center gap-2 mb-2">
            <div className={cn(
              'w-2 h-2 rounded-full',
              data.budgetStatuses.filter(b => b.status === 'RED').length > 0 ? 'bg-red-500' :
              data.budgetStatuses.filter(b => b.status === 'YELLOW').length > 0 ? 'bg-yellow-500' : 'bg-green-500'
            )} />
            <p className="text-xs text-text-muted">Budget Status</p>
          </div>
          <p className="text-2xl font-mono font-bold text-text-primary">
            {data.budgetStatuses.filter(b => b.status === 'GREEN').length}
            <span className="text-sm text-text-muted font-normal ml-1">green</span>
          </p>
          <p className="text-xs text-text-muted mt-1">
            {data.budgetStatuses.filter(b => b.status === 'RED').length} over budget
          </p>
        </div>

        <div className="bg-background-secondary rounded-xl border border-border p-5">
          <div className="flex items-center gap-2 mb-2">
            <Calendar className="w-4 h-4 text-text-muted" />
            <p className="text-xs text-text-muted">Upcoming Payments</p>
          </div>
          <p className="text-2xl font-mono font-bold text-text-primary">
            {data.upcomingPayments.length}
          </p>
          <p className="text-xs text-text-muted mt-1">due this week</p>
        </div>

        <div className="bg-background-secondary rounded-xl border border-border p-5">
          <div className="flex items-center gap-2 mb-2">
            <Lightbulb className="w-4 h-4 text-text-muted" />
            <p className="text-xs text-text-muted">Unread Insights</p>
          </div>
          <p className="text-2xl font-mono font-bold text-gold-500">
            {data.unreadInsightCount}
          </p>
          <p className="text-xs text-text-muted mt-1">new this week</p>
        </div>
      </div>

      {/* Top Transactions */}
      <div className="bg-background-secondary rounded-xl border border-border p-6">
        <h2 className="text-base font-semibold text-text-primary mb-4">Top Transactions This Week</h2>
        {data.topTransactions.length === 0 ? (
          <p className="text-text-muted text-sm">No transactions this week.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border">
                  <th className="text-left py-2 text-xs text-text-muted font-medium">Merchant</th>
                  <th className="text-left py-2 text-xs text-text-muted font-medium">Category</th>
                  <th className="text-left py-2 text-xs text-text-muted font-medium">Date</th>
                  <th className="text-right py-2 text-xs text-text-muted font-medium">Amount</th>
                </tr>
              </thead>
              <tbody>
                {data.topTransactions.slice(0, 5).map((t, i) => (
                  <tr key={i} className="border-b border-border/50">
                    <td className="py-3 text-text-primary font-medium">{t.merchant}</td>
                    <td className="py-3">
                      <span className="px-2 py-0.5 bg-background-tertiary text-text-muted text-xs rounded-full">{t.category}</span>
                    </td>
                    <td className="py-3 text-text-muted">{t.date}</td>
                    <td className="py-3 text-right font-mono font-semibold text-text-primary">
                      ${t.amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Budget Status */}
        <div className="bg-background-secondary rounded-xl border border-border p-6">
          <h2 className="text-base font-semibold text-text-primary mb-4">Budget Status</h2>
          {data.budgetStatuses.length === 0 ? (
            <p className="text-text-muted text-sm">No budgets configured.</p>
          ) : (
            <div>
              {data.budgetStatuses.map(b => (
                <BudgetTrafficLight key={b.id} budget={b} />
              ))}
            </div>
          )}
        </div>

        {/* Upcoming Payments */}
        <div className="bg-background-secondary rounded-xl border border-border p-6">
          <h2 className="text-base font-semibold text-text-primary mb-4">Upcoming Payments</h2>
          {data.upcomingPayments.length === 0 ? (
            <p className="text-text-muted text-sm">No payments due this week.</p>
          ) : (
            <div className="space-y-3">
              {data.upcomingPayments.map((p, i) => (
                <div key={i} className="flex items-center justify-between py-2 border-b border-border/50 last:border-0">
                  <div className="flex items-center gap-2">
                    <CreditCard className="w-4 h-4 text-text-muted flex-shrink-0" />
                    <div>
                      <p className="text-sm text-text-primary font-medium">{p.account}</p>
                      <p className="text-xs text-text-muted">Due: {p.dueDate}</p>
                    </div>
                  </div>
                  <p className="font-mono text-sm font-semibold text-gold-500">
                    ${p.balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Category breakdown bar chart */}
      {categoryChartData.length > 0 && (
        <div className="bg-background-secondary rounded-xl border border-border p-6">
          <h2 className="text-base font-semibold text-text-primary mb-4">This Week by Category</h2>
          <div className="h-56">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                layout="vertical"
                data={categoryChartData}
                margin={{ top: 0, right: 16, left: 0, bottom: 0 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="#1A1A24" horizontal={false} />
                <XAxis
                  type="number"
                  tick={{ fill: '#6B7280', fontSize: 11 }}
                  tickFormatter={v => `$${v}`}
                  tickLine={false}
                  axisLine={false}
                />
                <YAxis
                  type="category"
                  dataKey="category"
                  tick={{ fill: '#9CA3AF', fontSize: 11 }}
                  width={90}
                  tickLine={false}
                  axisLine={false}
                />
                <Tooltip
                  contentStyle={{ backgroundColor: '#111118', border: '1px solid #2A2A35', borderRadius: '8px', color: '#F9FAFB' }}
                  formatter={(v: number) => [`$${v.toFixed(2)}`, 'Spend']}
                />
                <Bar dataKey="amount" radius={[0, 4, 4, 0]}>
                  {categoryChartData.map((_, i) => (
                    <Cell key={i} fill={i === 0 ? '#F59E0B' : '#3B82F6'} fillOpacity={1 - i * 0.08} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}
    </div>
  )
}
