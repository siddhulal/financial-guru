'use client'

import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { Subscription } from '@/lib/types'
import { formatCurrency, formatDate, cn } from '@/lib/utils'
import { RefreshCw, AlertTriangle, CheckCircle, ToggleLeft, ToggleRight } from 'lucide-react'

export default function SubscriptionsPage() {
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([])
  const [loading, setLoading] = useState(true)

  const load = async () => {
    setLoading(true)
    try {
      setSubscriptions(await api.subscriptions.list())
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const toggleActive = async (sub: Subscription) => {
    await api.subscriptions.update(sub.id, { isActive: !sub.isActive })
    load()
  }

  const duplicates = subscriptions.filter(s => s.isDuplicate)
  const active = subscriptions.filter(s => s.isActive)
  const totalMonthly = active.reduce((sum, s) => {
    if (!s.amount) return sum
    const monthly = s.frequency === 'MONTHLY' ? s.amount :
      s.frequency === 'ANNUAL' ? s.amount / 12 :
      s.frequency === 'QUARTERLY' ? s.amount / 3 :
      s.amount * 4.33
    return sum + monthly
  }, 0)

  const totalAnnual = active.reduce((sum, s) => sum + (s.annualCost || 0), 0)

  return (
    <div className="space-y-5 animate-slide-up">
      <div>
        <h1 className="text-xl font-semibold text-text-primary">Subscription Audit</h1>
        <p className="text-sm text-text-muted mt-0.5">Detected recurring charges across all accounts</p>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="glass-card rounded-xl p-4 shadow-card">
          <p className="text-xs text-text-muted mb-1">Monthly Cost</p>
          <p className="text-2xl font-num font-semibold text-text-primary">{formatCurrency(totalMonthly)}</p>
          <p className="text-xs text-text-muted mt-0.5">/month</p>
        </div>
        <div className="glass-card rounded-xl p-4 shadow-card">
          <p className="text-xs text-text-muted mb-1">Annual Cost</p>
          <p className="text-2xl font-num font-semibold text-gold-500">{formatCurrency(totalAnnual)}</p>
          <p className="text-xs text-text-muted mt-0.5">/year</p>
        </div>
        <div className="glass-card rounded-xl p-4 shadow-card">
          <p className="text-xs text-text-muted mb-1">Active Subscriptions</p>
          <p className="text-2xl font-num font-semibold text-text-primary">{active.length}</p>
          <p className="text-xs text-text-muted mt-0.5">detected</p>
        </div>
        <div className={cn(
          'glass-card rounded-xl p-4 shadow-card',
          duplicates.length > 0 && 'border-orange-500/30 bg-orange-500/5'
        )}>
          <p className="text-xs text-text-muted mb-1">Duplicates Found</p>
          <p className={cn(
            'text-2xl font-num font-semibold',
            duplicates.length > 0 ? 'text-orange-400' : 'text-text-primary'
          )}>{duplicates.length}</p>
          <p className="text-xs text-text-muted mt-0.5">
            {duplicates.length > 0 ? '⚠️ Same service on multiple cards' : 'All good'}
          </p>
        </div>
      </div>

      {/* Duplicate warning */}
      {duplicates.length > 0 && (
        <div className="glass-card rounded-xl p-4 border border-orange-500/30 bg-orange-500/5">
          <div className="flex items-center gap-2 mb-2">
            <AlertTriangle className="w-4 h-4 text-orange-400" />
            <h3 className="text-sm font-semibold text-orange-400">Duplicate Subscriptions Detected</h3>
          </div>
          <p className="text-xs text-text-secondary mb-3">
            You may be paying for the same service on multiple credit cards.
          </p>
          <div className="space-y-2">
            {duplicates.map(s => (
              <div key={s.id} className="flex items-center justify-between text-xs">
                <span className="text-text-secondary">{s.merchantName}</span>
                <span className="text-orange-400 font-num">{formatCurrency(s.amount)}/mo</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Subscription list */}
      {loading ? (
        <div className="text-center py-16 text-text-muted">
          <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2" />
          Detecting subscriptions...
        </div>
      ) : subscriptions.length === 0 ? (
        <div className="text-center py-16">
          <RefreshCw className="w-10 h-10 text-text-muted/30 mx-auto mb-3" />
          <p className="text-text-muted">No subscriptions detected yet</p>
          <p className="text-text-muted/60 text-sm mt-1">Upload statements to detect recurring charges</p>
        </div>
      ) : (
        <div className="glass-card rounded-xl shadow-card overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b border-border">
                <th className="text-left px-5 py-3 text-xs font-medium text-text-muted uppercase tracking-wider">Service</th>
                <th className="text-left px-5 py-3 text-xs font-medium text-text-muted uppercase tracking-wider">Frequency</th>
                <th className="text-left px-5 py-3 text-xs font-medium text-text-muted uppercase tracking-wider">Account</th>
                <th className="text-left px-5 py-3 text-xs font-medium text-text-muted uppercase tracking-wider">Last Charged</th>
                <th className="text-right px-5 py-3 text-xs font-medium text-text-muted uppercase tracking-wider">Monthly</th>
                <th className="text-right px-5 py-3 text-xs font-medium text-text-muted uppercase tracking-wider">Annual</th>
                <th className="text-center px-5 py-3 text-xs font-medium text-text-muted uppercase tracking-wider">Active</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {subscriptions.map((s) => {
                const monthly = !s.amount ? 0 :
                  s.frequency === 'MONTHLY' ? s.amount :
                  s.frequency === 'ANNUAL' ? s.amount / 12 :
                  s.frequency === 'QUARTERLY' ? s.amount / 3 :
                  s.amount * 4.33

                return (
                  <tr key={s.id} className={cn(
                    'hover:bg-background-tertiary/50 transition-colors',
                    s.isDuplicate && 'bg-orange-500/5',
                    !s.isActive && 'opacity-50'
                  )}>
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-2">
                        {s.isDuplicate && (
                          <AlertTriangle className="w-3.5 h-3.5 text-orange-400 flex-shrink-0" />
                        )}
                        <div>
                          <p className="text-sm font-medium text-text-primary">{s.merchantName}</p>
                          {s.category && (
                            <p className="text-xs text-text-muted">{s.category}</p>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-3.5">
                      <span className="text-xs bg-background-tertiary border border-border rounded px-2 py-0.5 text-text-secondary capitalize">
                        {s.frequency?.toLowerCase() || '—'}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-xs text-text-muted">{s.accountId || '—'}</td>
                    <td className="px-5 py-3.5 text-xs text-text-muted font-num">
                      {formatDate(s.lastChargedDate)}
                    </td>
                    <td className="px-5 py-3.5 text-right text-sm font-num font-medium text-text-primary">
                      {formatCurrency(monthly)}
                    </td>
                    <td className="px-5 py-3.5 text-right text-sm font-num font-semibold text-gold-500">
                      {formatCurrency(s.annualCost)}
                    </td>
                    <td className="px-5 py-3.5 text-center">
                      <button onClick={() => toggleActive(s)} className="transition-colors">
                        {s.isActive ? (
                          <ToggleRight className="w-5 h-5 text-success" />
                        ) : (
                          <ToggleLeft className="w-5 h-5 text-text-muted" />
                        )}
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
