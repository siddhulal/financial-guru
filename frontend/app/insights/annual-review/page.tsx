'use client'

import { useState, useEffect } from 'react'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'
import { TrendingUp, TrendingDown, Info } from 'lucide-react'
import { api } from '@/lib/api'
import { AnnualReviewResponse } from '@/lib/types'
import { cn } from '@/lib/utils'

function formatCurrency(n: number) {
  return '$' + Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
}

const CURRENT_YEAR = new Date().getFullYear()
const YEAR_OPTIONS = [CURRENT_YEAR, CURRENT_YEAR - 1, CURRENT_YEAR - 2, CURRENT_YEAR - 3]

const FALLBACK_RECOMMENDATIONS = [
  'Build an emergency fund of 3–6 months of expenses to protect against unexpected events.',
  'Review and cancel unused subscriptions — small recurring charges add up significantly over time.',
  'Pay down high-interest credit card debt using the avalanche method to minimize interest costs.',
]

export default function AnnualReviewPage() {
  const [selectedYear, setSelectedYear] = useState(CURRENT_YEAR)
  const [data, setData] = useState<AnnualReviewResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showTooltip, setShowTooltip] = useState(false)

  useEffect(() => {
    setLoading(true)
    setError(null)
    api.insights.annualReview(selectedYear)
      .then(setData)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [selectedYear])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-gold-500" />
      </div>
    )
  }

  if (error) return <div className="p-6 text-error">{error}</div>
  if (!data) return null

  const savingsPositive = data.savingsRate >= 0
  const nwPositive = data.netWorthChange >= 0

  const topCategories = [...data.categoryBreakdown]
    .sort((a, b) => b.amount - a.amount)
    .slice(0, 10)

  const recommendations =
    data.aiRecommendations && data.aiRecommendations.length > 0
      ? data.aiRecommendations
      : FALLBACK_RECOMMENDATIONS

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Annual Review</h1>
          <p className="text-text-secondary text-sm mt-1">Your year in numbers</p>
        </div>
        <select
          value={selectedYear}
          onChange={e => setSelectedYear(Number(e.target.value))}
          className="bg-background-secondary border border-border rounded-lg px-3 py-2 text-text-primary text-sm focus:outline-none focus:border-gold-500/50"
        >
          {YEAR_OPTIONS.map(y => (
            <option key={y} value={y}>{y}</option>
          ))}
        </select>
      </div>

      {/* Key metrics */}
      <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
        {/* Total Income */}
        <div className="bg-background-secondary rounded-xl border border-border p-4 space-y-1">
          <p className="text-xs text-text-muted">Total Income</p>
          {data.estimatedIncome > 0 ? (
            <p className="font-mono font-bold text-xl text-success">{formatCurrency(data.estimatedIncome)}</p>
          ) : (
            <p className="font-mono font-bold text-xl text-text-muted">Not set</p>
          )}
          <p className="text-xs text-text-muted">Annual</p>
        </div>

        {/* Total Spending */}
        <div className="bg-background-secondary rounded-xl border border-border p-4 space-y-1">
          <p className="text-xs text-text-muted">Total Spending</p>
          <p className="font-mono font-bold text-xl text-text-primary">{formatCurrency(data.totalSpending)}</p>
          <p className="text-xs text-text-muted">Annual</p>
        </div>

        {/* Savings Rate */}
        <div className="bg-background-secondary rounded-xl border border-border p-4 space-y-1">
          <p className="text-xs text-text-muted">Savings Rate</p>
          <p className={cn('font-mono font-bold text-xl', savingsPositive ? 'text-success' : 'text-error')}>
            {savingsPositive ? '' : '-'}{Math.abs(data.savingsRate).toFixed(1)}%
          </p>
          <p className="text-xs text-text-muted">{savingsPositive ? 'Great — target 20%+' : 'Spending exceeded income'}</p>
        </div>

        {/* Interest Lost */}
        <div className="bg-background-secondary rounded-xl border border-border p-4 space-y-1">
          <div className="flex items-center justify-between">
            <p className="text-xs text-text-muted">Interest Paid</p>
            <div className="relative">
              <button
                onMouseEnter={() => setShowTooltip(true)}
                onMouseLeave={() => setShowTooltip(false)}
                className="text-text-muted hover:text-gold-500 transition-colors"
              >
                <Info className="w-3.5 h-3.5" />
              </button>
              {showTooltip && (
                <div className="absolute right-0 bottom-6 bg-background-tertiary border border-border rounded-lg p-2.5 text-xs text-text-secondary w-48 z-10 shadow-lg">
                  This is money paid to lenders — it does not build equity or savings.
                </div>
              )}
            </div>
          </div>
          <p className="font-mono font-bold text-xl text-gold-500">{formatCurrency(data.interestPaid)}</p>
          <p className="text-xs text-text-muted">To lenders this year</p>
        </div>

        {/* Fees Paid */}
        <div className="bg-background-secondary rounded-xl border border-border p-4 space-y-1">
          <p className="text-xs text-text-muted">Fees Paid</p>
          <p className="font-mono font-bold text-xl text-error">{formatCurrency(data.feesPaid)}</p>
          <p className="text-xs text-text-muted">Annual fees, late fees, ATM fees</p>
        </div>

        {/* Subscription Cost */}
        <div className="bg-background-secondary rounded-xl border border-border p-4 space-y-1">
          <p className="text-xs text-text-muted">Subscription Cost</p>
          <p className="font-mono font-bold text-xl text-text-primary">{formatCurrency(data.subscriptionAnnualCost)}</p>
          <p className="text-xs text-text-muted">Recurring charges annualized</p>
        </div>
      </div>

      {/* Net Worth Change */}
      <div className={cn(
        'rounded-xl border p-5 flex items-center justify-between',
        nwPositive
          ? 'bg-gradient-to-r from-success/10 to-success/5 border-success/30'
          : 'bg-gradient-to-r from-error/10 to-error/5 border-error/30'
      )}>
        <div>
          <p className="text-sm text-text-secondary">Net Worth Change in {selectedYear}</p>
          <p className={cn('font-mono font-bold text-3xl mt-1', nwPositive ? 'text-success' : 'text-error')}>
            {nwPositive ? '+' : '-'}{formatCurrency(data.netWorthChange)}
          </p>
        </div>
        {nwPositive ? (
          <TrendingUp className="w-10 h-10 text-success opacity-60" />
        ) : (
          <TrendingDown className="w-10 h-10 text-error opacity-60" />
        )}
      </div>

      {/* Category breakdown chart */}
      {topCategories.length > 0 && (
        <div className="bg-background-secondary rounded-xl border border-border p-5 space-y-4">
          <div>
            <h2 className="font-bold text-text-primary">Top Spending Categories</h2>
            <p className="text-xs text-text-muted">Top 10 by annual spend</p>
          </div>
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={topCategories}
                layout="vertical"
                margin={{ top: 0, right: 30, left: 90, bottom: 0 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="#ffffff08" horizontal={false} />
                <XAxis
                  type="number"
                  tickFormatter={v => {
                    const abs = Math.abs(v)
                    const sign = v < 0 ? '-' : ''
                    if (abs >= 1000) return `${sign}$${(abs / 1000).toFixed(1)}K`
                    return `${sign}$${abs.toFixed(0)}`
                  }}
                  tick={{ fill: '#6B7280', fontSize: 11 }}
                  stroke="#ffffff10"
                />
                <YAxis
                  type="category"
                  dataKey="category"
                  tick={{ fill: '#9CA3AF', fontSize: 11 }}
                  stroke="#ffffff10"
                  width={85}
                />
                <Tooltip
                  contentStyle={{ backgroundColor: '#111118', border: '1px solid #2D2D3F', borderRadius: '8px', color: '#E5E7EB' }}
                  formatter={(val: number) => [formatCurrency(val), 'Spent']}
                  cursor={{ fill: '#ffffff05' }}
                />
                <Bar dataKey="amount" fill="#F59E0B" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* AI Recommendations */}
      <div className="space-y-3">
        <div>
          <h2 className="font-bold text-text-primary">AI Recommendations</h2>
          <p className="text-xs text-text-muted mt-0.5">
            {data.aiRecommendations && data.aiRecommendations.length > 0
              ? 'Personalized insights from your spending data'
              : 'General best-practice tips (run annual review with more data for personalized insights)'}
          </p>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {recommendations.map((rec, i) => (
            <div
              key={i}
              className="bg-gradient-to-r from-gold-500/10 to-gold-600/5 border border-gold-500/30 rounded-xl p-4 space-y-2"
            >
              <div className="flex items-center gap-2">
                <div className="w-6 h-6 rounded-full bg-gold-500/20 flex items-center justify-center flex-shrink-0">
                  <span className="text-gold-500 font-bold font-mono text-xs">{i + 1}</span>
                </div>
                <span className="text-xs font-semibold text-gold-400 uppercase tracking-wide">Action Item</span>
              </div>
              <p className="text-sm text-text-secondary leading-relaxed">{rec}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
