'use client'

import { DashboardData } from '@/lib/types'
import { formatCurrency, formatPercent, getUtilizationColor, getUtilizationBarColor } from '@/lib/utils'
import { TrendingUp, TrendingDown, Minus } from 'lucide-react'

interface Props {
  data: DashboardData
}

export function NetWorthCard({ data }: Props) {
  const spendChange = data.spendingChangePercent
  const TrendIcon = spendChange > 5 ? TrendingUp : spendChange < -5 ? TrendingDown : Minus
  const trendColor = spendChange > 5 ? 'text-red-400' : spendChange < -5 ? 'text-green-400' : 'text-text-secondary'

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
      {/* Credit Card Balance */}
      <div className="glass-card rounded-xl p-5 col-span-2 shadow-card">
        <div className="flex items-start justify-between mb-4">
          <div>
            <p className="text-xs text-text-muted uppercase tracking-wider mb-1">Total Credit Debt</p>
            <p className="text-3xl font-semibold font-num text-text-primary">
              {formatCurrency(data.totalCreditCardBalance)}
            </p>
            <p className="text-xs text-text-muted mt-1">
              of {formatCurrency(data.totalCreditLimit)} limit
            </p>
          </div>
          <div className="text-right">
            <p className="text-xs text-text-muted mb-1">Utilization</p>
            <p className={`text-2xl font-num font-semibold ${getUtilizationColor(data.overallUtilizationPercent)}`}>
              {formatPercent(data.overallUtilizationPercent)}
            </p>
          </div>
        </div>
        {/* Utilization bar */}
        <div className="h-1.5 bg-border rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all ${getUtilizationBarColor(data.overallUtilizationPercent)}`}
            style={{ width: `${Math.min(data.overallUtilizationPercent, 100)}%` }}
          />
        </div>
        <div className="flex justify-between mt-1.5">
          <span className="text-xs text-text-muted">Available: {formatCurrency(data.totalAvailableCredit)}</span>
          <span className="text-xs text-text-muted">Healthy: &lt;30%</span>
        </div>
      </div>

      {/* Checking + Savings */}
      <div className="glass-card rounded-xl p-5 shadow-card">
        <p className="text-xs text-text-muted uppercase tracking-wider mb-2">Checking</p>
        <p className="text-2xl font-num font-semibold text-text-primary">
          {formatCurrency(data.totalCheckingBalance)}
        </p>
        <div className="mt-4">
          <p className="text-xs text-text-muted uppercase tracking-wider mb-2">Savings</p>
          <p className="text-xl font-num font-medium text-success">
            {formatCurrency(data.totalSavingsBalance)}
          </p>
        </div>
      </div>

      {/* This month spending */}
      <div className="glass-card rounded-xl p-5 shadow-card">
        <p className="text-xs text-text-muted uppercase tracking-wider mb-2">This Month</p>
        <p className="text-2xl font-num font-semibold text-text-primary">
          {formatCurrency(data.currentMonthSpend)}
        </p>
        <div className="flex items-center gap-1 mt-2">
          <TrendIcon className={`w-3.5 h-3.5 ${trendColor}`} />
          <span className={`text-xs font-num ${trendColor}`}>
            {spendChange > 0 ? '+' : ''}{formatPercent(spendChange)} vs last month
          </span>
        </div>
        <div className="mt-3">
          <p className="text-xs text-text-muted">Subscriptions</p>
          <p className="text-sm font-num text-text-secondary mt-0.5">
            {formatCurrency(data.monthlySubscriptionCost)}/mo
          </p>
        </div>
      </div>
    </div>
  )
}
