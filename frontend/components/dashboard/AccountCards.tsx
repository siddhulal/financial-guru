'use client'

import Link from 'next/link'
import { Account } from '@/lib/types'
import { formatCurrency, formatPercent, getUtilizationColor } from '@/lib/utils'
import { CreditCard } from 'lucide-react'

interface Props {
  accounts: Account[]
}

export function AccountCards({ accounts }: Props) {
  const creditCards = accounts.filter(a => a.type === 'CREDIT_CARD')

  return (
    <div className="glass-card rounded-xl p-5 shadow-card">
      <h3 className="text-sm font-semibold text-text-primary mb-4">Credit Cards</h3>
      <div className="space-y-3">
        {creditCards.length === 0 ? (
          <p className="text-text-muted text-sm text-center py-4">No credit cards added</p>
        ) : (
          creditCards.map((account) => (
            <Link
              key={account.id}
              href={`/accounts/${account.id}`}
              className="block group"
            >
              <div className="flex items-center gap-3 p-3 rounded-lg border border-border hover:border-gold-500/30 hover:bg-background-tertiary transition-all">
                {/* Card icon with color */}
                <div
                  className="w-10 h-7 rounded-md flex items-center justify-center flex-shrink-0"
                  style={{ backgroundColor: account.color || '#1E1E2E' }}
                >
                  <CreditCard className="w-4 h-4 text-white/80" />
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between">
                    <p className="text-sm font-medium text-text-primary truncate group-hover:text-gold-500 transition-colors">
                      {account.name}
                    </p>
                    <p className="text-sm font-num font-semibold text-text-primary ml-2">
                      {formatCurrency(account.currentBalance)}
                    </p>
                  </div>
                  <div className="flex items-center justify-between mt-1">
                    <div className="flex-1 mr-3">
                      <div className="h-1 bg-border rounded-full overflow-hidden">
                        <div
                          className="h-full bg-gold-500/60 rounded-full"
                          style={{ width: `${Math.min(account.utilizationPercent || 0, 100)}%` }}
                        />
                      </div>
                    </div>
                    <span className={`text-xs font-num ${getUtilizationColor(account.utilizationPercent || 0)}`}>
                      {formatPercent(account.utilizationPercent)}
                    </span>
                  </div>
                </div>
              </div>
            </Link>
          ))
        )}
      </div>
    </div>
  )
}
