'use client'

import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { DashboardData } from '@/lib/types'
import { NetWorthCard } from '@/components/dashboard/NetWorthCard'
import { SpendingChart } from '@/components/dashboard/SpendingChart'
import { CategoryDonut } from '@/components/dashboard/CategoryDonut'
import { AlertSummary } from '@/components/dashboard/AlertSummary'
import { DueDateTimeline } from '@/components/dashboard/DueDateTimeline'
import { AccountCards } from '@/components/dashboard/AccountCards'
import { WealthKPIWidget } from '@/components/dashboard/WealthKPIWidget'
import { PaycheckBreakdown } from '@/components/dashboard/PaycheckBreakdown'
import { formatCurrency } from '@/lib/utils'
import { RefreshCw } from 'lucide-react'

export default function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      const d = await api.dashboard.get()
      setData(d)
    } catch (e) {
      setError('Could not connect to backend. Make sure Spring Boot is running on :8080')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-center">
          <div className="w-8 h-8 border-2 border-gold-500 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
          <p className="text-text-muted text-sm">Loading dashboard...</p>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-center max-w-md">
          <div className="w-12 h-12 bg-red-500/10 rounded-full flex items-center justify-center mx-auto mb-3">
            <span className="text-2xl">⚠️</span>
          </div>
          <p className="text-text-primary font-medium mb-2">Backend not connected</p>
          <p className="text-text-muted text-sm mb-4">{error}</p>
          <button
            onClick={load}
            className="flex items-center gap-2 mx-auto px-4 py-2 bg-gold-500/10 border border-gold-500/30 text-gold-500 rounded-lg text-sm hover:bg-gold-500/20 transition-colors"
          >
            <RefreshCw className="w-4 h-4" /> Retry
          </button>
        </div>
      </div>
    )
  }

  if (!data) return null

  return (
    <div className="space-y-5 animate-slide-up">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-text-primary">Dashboard</h1>
          <p className="text-sm text-text-muted mt-0.5">Your financial overview</p>
        </div>
        <button
          onClick={load}
          className="p-2 hover:bg-background-tertiary rounded-lg transition-colors"
          title="Refresh"
        >
          <RefreshCw className="w-4 h-4 text-text-muted" />
        </button>
      </div>

      {/* 3-Number Wealth Dashboard */}
      <WealthKPIWidget data={data} />

      {/* Promo APR warnings */}
      {data.expiringPromoAprs.length > 0 && (
        <div className="glass-card rounded-xl p-4 border-yellow-500/30 bg-yellow-500/5">
          <p className="text-sm font-medium text-yellow-400 mb-2">
            ⚡ Promo APR Expiring Soon
          </p>
          <div className="grid gap-2 sm:grid-cols-2">
            {data.expiringPromoAprs.map((a) => (
              <div key={a.accountId} className="text-xs text-text-secondary">
                <span className="font-medium text-text-primary">{a.accountName}</span>
                {' — '}{a.promoApr}% APR ends in {a.daysLeft} days
                {' · '}Balance: {formatCurrency(a.balance)}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Net worth / balance cards */}
      <NetWorthCard data={data} />

      {/* Charts row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <div className="lg:col-span-2">
          <SpendingChart data={data.monthlySpendingTrend} />
        </div>
        <CategoryDonut data={data.categoryBreakdown} />
      </div>

      {/* Bottom row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <AlertSummary alerts={data.recentAlerts} count={data.unreadAlertCount} />
        <DueDateTimeline payments={data.upcomingPayments} />
        <AccountCards accounts={data.accounts} />
      </div>

      {/* Paycheck Breakdown */}
      {data.estimatedMonthlyIncome > 0 && <PaycheckBreakdown data={data} />}

      {/* Top merchants */}
      {data.topMerchants.length > 0 && (
        <div className="glass-card rounded-xl p-5 shadow-card">
          <h3 className="text-sm font-semibold text-text-primary mb-4">Top Merchants (Year to Date)</h3>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
            {data.topMerchants.map((m) => (
              <div key={m.merchant} className="bg-background-tertiary rounded-lg p-3 text-center">
                <p className="text-xs text-text-muted mb-1 truncate">{m.merchant}</p>
                <p className="text-sm font-num font-semibold text-text-primary">
                  {formatCurrency(m.amount)}
                </p>
                <p className="text-xs text-text-muted">{m.count} txns</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
