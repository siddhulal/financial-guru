'use client'

import { DashboardData } from '@/lib/types'
import { formatCurrency } from '@/lib/utils'

interface Props {
  data: DashboardData
}

const BUCKET_COLORS: Record<string, string> = {
  NECESSITIES: 'bg-blue-500',
  EXPERIENCES:  'bg-emerald-500',
  THINGS:       'bg-amber-500',
  OTHER:        'bg-text-muted',
}

const BUCKET_LABELS: Record<string, string> = {
  NECESSITIES: 'Necessities',
  EXPERIENCES:  'Experiences',
  THINGS:       'Things (material)',
  OTHER:        'Other',
}

export function PaycheckBreakdown({ data }: Props) {
  const income = data.estimatedMonthlyIncome ?? 0
  if (income <= 0 || !data.paycheckBreakdown?.length) return null

  // Add "Savings" row
  const totalSpend = data.currentMonthSpend ?? 0
  const savings = income - totalSpend
  const savingsPct = income > 0 ? (savings / income) * 100 : 0

  const allRows = [
    ...data.paycheckBreakdown,
    {
      label: 'Savings / Freedom',
      amount: savings,
      pctOfIncome: savingsPct,
      bucket: 'FREEDOM' as const,
    },
  ]

  return (
    <div className="glass-card rounded-xl p-5 shadow-card">
      <div className="flex items-center justify-between mb-1">
        <h3 className="text-sm font-semibold text-text-primary">For Every $100 You Earn</h3>
        <span className="text-xs text-text-muted">Based on {formatCurrency(income)}/mo income</span>
      </div>
      <p className="text-xs text-text-muted mb-4">Where does it go?</p>

      <div className="space-y-2.5">
        {allRows
          .filter(r => r.amount > 0 && Math.abs(r.pctOfIncome) > 0.5)
          .sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount))
          .map((row) => {
            const pct = Math.abs(row.pctOfIncome)
            const barColor = row.bucket === 'FREEDOM'
              ? (savings >= 0 ? 'bg-green-500' : 'bg-red-500')
              : BUCKET_COLORS[row.bucket] ?? 'bg-text-muted'
            const label = row.bucket === 'FREEDOM' ? 'Savings / Freedom' : row.label
            const target = label === 'Savings / Freedom' ? 20 : null

            return (
              <div key={row.label} className="flex items-center gap-3">
                <div className="w-32 flex-shrink-0">
                  <p className="text-xs text-text-primary truncate">{label}</p>
                </div>
                <div className="flex-1 h-4 bg-border rounded-full overflow-hidden relative">
                  <div
                    className={`h-full rounded-full ${barColor} transition-all duration-500`}
                    style={{ width: `${Math.min(pct, 100)}%` }}
                  />
                  {target && (
                    <div
                      className="absolute top-0 bottom-0 w-px bg-gold-500/60"
                      style={{ left: `${Math.min(target, 100)}%` }}
                    />
                  )}
                </div>
                <div className="w-20 text-right flex-shrink-0">
                  <span className="text-xs font-mono text-text-muted">
                    ${(pct).toFixed(0)} · {formatCurrency(row.amount)}
                  </span>
                </div>
              </div>
            )
          })}
      </div>

      {/* Things / Experiences / Freedom summary */}
      <div className="mt-4 pt-4 border-t border-border grid grid-cols-3 gap-3 text-center">
        <div>
          <p className="text-xs text-amber-400 font-medium">Things</p>
          <p className="text-sm font-mono font-bold text-text-primary">{formatCurrency(data.thingsSpend)}</p>
          <p className="text-xs text-text-muted">material goods</p>
        </div>
        <div>
          <p className="text-xs text-emerald-400 font-medium">Experiences</p>
          <p className="text-sm font-mono font-bold text-text-primary">{formatCurrency(data.experiencesSpend)}</p>
          <p className="text-xs text-text-muted">dining + travel</p>
        </div>
        <div>
          <p className={`text-xs font-medium ${savings >= 0 ? 'text-green-400' : 'text-red-400'}`}>Freedom</p>
          <p className={`text-sm font-mono font-bold ${savings >= 0 ? 'text-green-400' : 'text-red-400'}`}>
            {formatCurrency(Math.abs(savings))}
          </p>
          <p className="text-xs text-text-muted">{savings >= 0 ? 'saved' : 'overspent'}</p>
        </div>
      </div>
    </div>
  )
}
