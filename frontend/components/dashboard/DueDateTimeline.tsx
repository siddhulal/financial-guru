'use client'

import { formatCurrency, formatDate } from '@/lib/utils'
import { Calendar, AlertTriangle } from 'lucide-react'
import { cn } from '@/lib/utils'

interface Payment {
  accountId: string
  accountName: string
  dueDate: string
  daysUntilDue: number
  balance: number
  minPayment: number
}

interface Props {
  payments: Payment[]
}

export function DueDateTimeline({ payments }: Props) {
  return (
    <div className="glass-card rounded-xl p-5 shadow-card">
      <div className="flex items-center gap-2 mb-4">
        <Calendar className="w-4 h-4 text-text-muted" />
        <h3 className="text-sm font-semibold text-text-primary">Upcoming Payments</h3>
      </div>

      {payments.length === 0 ? (
        <p className="text-text-muted text-sm text-center py-4">No upcoming payments</p>
      ) : (
        <div className="space-y-3">
          {payments.map((p) => {
            const isUrgent = p.daysUntilDue <= 3
            const isWarning = p.daysUntilDue <= 7
            return (
              <div key={p.accountId} className={cn(
                'flex items-center justify-between p-3 rounded-lg border transition-colors',
                isUrgent
                  ? 'border-red-500/30 bg-red-500/5'
                  : isWarning
                  ? 'border-yellow-500/30 bg-yellow-500/5'
                  : 'border-border bg-background-tertiary/50'
              )}>
                <div className="flex items-center gap-3">
                  {isUrgent && <AlertTriangle className="w-4 h-4 text-red-400 flex-shrink-0" />}
                  <div>
                    <p className="text-sm font-medium text-text-primary">{p.accountName}</p>
                    <p className="text-xs text-text-muted">
                      Due {formatDate(p.dueDate)} · {p.daysUntilDue === 0 ? 'Today!' : `${p.daysUntilDue}d`}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-sm font-num font-semibold text-text-primary">
                    {formatCurrency(p.balance)}
                  </p>
                  <p className="text-xs text-text-muted">
                    Min: {formatCurrency(p.minPayment)}
                  </p>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
