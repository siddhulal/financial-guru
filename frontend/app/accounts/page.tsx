'use client'

import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { Account } from '@/lib/types'
import {
  formatCurrency, formatPercent, formatDate,
  getUtilizationColor, getUtilizationBarColor
} from '@/lib/utils'
import { Plus, CreditCard, Building, TrendingUp } from 'lucide-react'
import { AddAccountModal } from '@/components/accounts/AddAccountModal'
import { BankLogo, CardNetworkLogo, getBankBgColor } from '@/components/shared/BankLogo'

export default function AccountsPage() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)

  const load = () => {
    setLoading(true)
    api.accounts.list()
      .then(setAccounts)
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const creditCards = accounts.filter(a => a.type === 'CREDIT_CARD')
  const checking = accounts.filter(a => a.type === 'CHECKING')
  const savings = accounts.filter(a => a.type === 'SAVINGS')
  const loans = accounts.filter(a => a.type === 'LOAN')

  return (
    <div className="space-y-6 animate-slide-up">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-text-primary">Accounts</h1>
          <p className="text-sm text-text-muted mt-0.5">{accounts.length} active accounts</p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-4 py-2 bg-gold-500 text-black text-sm font-semibold rounded-lg hover:bg-gold-600 transition-colors"
        >
          <Plus className="w-4 h-4" /> Add Account
        </button>
      </div>

      {loading ? (
        <div className="text-center py-16 text-text-muted">Loading accounts...</div>
      ) : (
        <>
          {/* Credit Cards */}
          {creditCards.length > 0 && (
            <section>
              <h2 className="text-xs font-semibold text-text-muted uppercase tracking-wider mb-3 flex items-center gap-2">
                <CreditCard className="w-3.5 h-3.5" /> Credit Cards ({creditCards.length})
              </h2>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {creditCards.map(account => (
                  <CreditCardTile key={account.id} account={account} />
                ))}
              </div>
            </section>
          )}

          {/* Bank Accounts */}
          {(checking.length > 0 || savings.length > 0) && (
            <section>
              <h2 className="text-xs font-semibold text-text-muted uppercase tracking-wider mb-3 flex items-center gap-2">
                <Building className="w-3.5 h-3.5" /> Bank Accounts
              </h2>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {[...checking, ...savings].map(account => (
                  <BankAccountTile key={account.id} account={account} />
                ))}
              </div>
            </section>
          )}

          {/* Loans */}
          {loans.length > 0 && (
            <section>
              <h2 className="text-xs font-semibold text-text-muted uppercase tracking-wider mb-3 flex items-center gap-2">
                <TrendingUp className="w-3.5 h-3.5" /> Loans
              </h2>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {loans.map(account => (
                  <BankAccountTile key={account.id} account={account} />
                ))}
              </div>
            </section>
          )}

          {accounts.length === 0 && (
            <div className="text-center py-16">
              <CreditCard className="w-12 h-12 text-text-muted/30 mx-auto mb-3" />
              <p className="text-text-muted">No accounts added yet</p>
              <p className="text-text-muted/60 text-sm mt-1">Add your credit cards and bank accounts to get started</p>
              <button
                onClick={() => setShowModal(true)}
                className="mt-4 px-4 py-2 bg-gold-500/10 border border-gold-500/30 text-gold-500 rounded-lg text-sm hover:bg-gold-500/20 transition-colors"
              >
                Add First Account
              </button>
            </div>
          )}
        </>
      )}

      {showModal && (
        <AddAccountModal
          onClose={() => setShowModal(false)}
          onSave={() => { setShowModal(false); load() }}
        />
      )}
    </div>
  )
}

function CreditCardTile({ account }: { account: Account }) {
  const util = account.utilizationPercent || 0
  return (
    <a href={`/accounts/${account.id}`} className="block group h-full">
      <div className="glass-card rounded-xl p-5 shadow-card hover:shadow-card-hover transition-all border border-border hover:border-gold-500/30 h-full flex flex-col">
        {/* Card visual header — styled like a real credit card */}
        <div
          className="h-16 rounded-lg mb-4 flex flex-col justify-between p-3 relative overflow-hidden"
          style={{
            background: account.color
              ? `linear-gradient(135deg, ${account.color}DD, ${account.color}88)`
              : `linear-gradient(135deg, ${getBankBgColor(account.institution)}EE, ${getBankBgColor(account.institution)}99)`
          }}
        >
          {/* Top row: bank logo left, network logo right */}
          <div className="flex items-center justify-between">
            <BankLogo institution={account.institution} size={22} white className="opacity-90" />
            <CardNetworkLogo cardName={account.name} institution={account.institution} size={28} white className="opacity-80" />
          </div>
          {/* Bottom row: masked number */}
          <span className="text-xs text-white/75 font-mono tracking-widest">
            •••• {account.last4 || '????'}
          </span>
        </div>

        <p className="text-sm font-semibold text-text-primary group-hover:text-gold-500 transition-colors truncate">
          {account.name}
        </p>
        <p className="text-xs text-text-muted mt-0.5">{account.institution}</p>

        <div className="mt-3 flex justify-between items-baseline flex-1">
          <span className="text-xl font-num font-semibold text-text-primary">
            {formatCurrency(account.currentBalance)}
          </span>
          <span className={`text-sm font-num ${getUtilizationColor(util)}`}>
            {formatPercent(util)}
          </span>
        </div>

        <div className="mt-2 h-1.5 bg-border rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full ${getUtilizationBarColor(util)}`}
            style={{ width: `${Math.min(util, 100)}%` }}
          />
        </div>
        <p className="text-xs text-text-muted mt-1">
          {formatCurrency(account.availableCredit)} available · Limit {formatCurrency(account.creditLimit)}
        </p>

        <div className="mt-auto pt-3 border-t border-border flex gap-4 text-xs text-text-muted mt-3">
          {account.apr && <span>APR {account.apr}%</span>}
          {account.paymentDueDay && <span>Due day {account.paymentDueDay}</span>}
          {account.promoAprEndDate && account.daysUntilPromoAprExpiry !== null && (
            <span className={account.daysUntilPromoAprExpiry <= 30 ? 'text-yellow-400' : ''}>
              Promo {account.daysUntilPromoAprExpiry}d left
            </span>
          )}
          {!account.apr && !account.paymentDueDay && !account.promoAprEndDate && (
            <span className="text-text-muted/40 italic">No APR info yet</span>
          )}
        </div>

        {account.rewardsProgram && (
          <p className="mt-2 text-xs text-text-muted truncate">🎁 {account.rewardsProgram}</p>
        )}
      </div>
    </a>
  )
}

function BankAccountTile({ account }: { account: Account }) {
  return (
    <a href={`/accounts/${account.id}`} className="block group">
      <div className="glass-card rounded-xl p-5 shadow-card hover:shadow-card-hover transition-all border border-border hover:border-gold-500/30">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-10 h-10 rounded-lg bg-background-tertiary flex items-center justify-center">
            <Building className="w-5 h-5 text-text-muted" />
          </div>
          <div>
            <p className="text-sm font-semibold text-text-primary group-hover:text-gold-500 transition-colors">
              {account.name}
            </p>
            <p className="text-xs text-text-muted">{account.institution}</p>
          </div>
        </div>
        <p className="text-2xl font-num font-semibold text-text-primary">
          {formatCurrency(account.currentBalance)}
        </p>
        <p className="text-xs text-text-muted mt-1 capitalize">{account.type.toLowerCase().replace('_', ' ')}</p>
      </div>
    </a>
  )
}
