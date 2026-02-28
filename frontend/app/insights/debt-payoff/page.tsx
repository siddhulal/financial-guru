'use client'

import { useState, useEffect, useCallback, useRef } from 'react'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  ReferenceLine,
} from 'recharts'
import { api } from '@/lib/api'
import { DebtPayoffResponse, WhatIfDataPoint, CardPayoffDetail } from '@/lib/types'
import { cn } from '@/lib/utils'

function formatCurrency(n: number) {
  return '$' + n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatDate(s: string) {
  return new Date(s).toLocaleDateString('en-US', { month: 'short', year: 'numeric' })
}

function PayoffTable({ cards }: { cards: CardPayoffDetail[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border">
            <th className="text-left py-2 px-3 text-text-muted font-medium">Order</th>
            <th className="text-left py-2 px-3 text-text-muted font-medium">Card</th>
            <th className="text-right py-2 px-3 text-text-muted font-medium">Balance</th>
            <th className="text-right py-2 px-3 text-text-muted font-medium">APR</th>
            <th className="text-right py-2 px-3 text-text-muted font-medium">Payoff Date</th>
            <th className="text-right py-2 px-3 text-text-muted font-medium">Interest Paid</th>
          </tr>
        </thead>
        <tbody>
          {cards.map((card, i) => (
            <tr key={card.accountId} className={cn('border-b border-border/50', i % 2 === 0 ? 'bg-background-tertiary/30' : '')}>
              <td className="py-2.5 px-3 font-mono text-text-muted">#{card.payoffOrder}</td>
              <td className="py-2.5 px-3 text-text-primary font-medium">{card.accountName}</td>
              <td className="py-2.5 px-3 text-right font-mono text-text-primary">{formatCurrency(card.currentBalance)}</td>
              <td className="py-2.5 px-3 text-right font-mono text-gold-500">{card.apr.toFixed(2)}%</td>
              <td className="py-2.5 px-3 text-right font-mono text-text-secondary">{formatDate(card.payoffDate)}</td>
              <td className="py-2.5 px-3 text-right font-mono text-error">{formatCurrency(card.interestPaid)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default function DebtPayoffPage() {
  const [data, setData] = useState<DebtPayoffResponse | null>(null)
  const [whatIf, setWhatIf] = useState<WhatIfDataPoint[]>([])
  const [extra, setExtra] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [recalculating, setRecalculating] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    Promise.all([api.insights.debtPayoff(0), api.insights.debtPayoffWhatIf()])
      .then(([d, w]) => {
        setData(d)
        setWhatIf(w)
      })
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  const fetchWithExtra = useCallback((val: number) => {
    setRecalculating(true)
    api.insights.debtPayoff(val)
      .then(setData)
      .catch(e => alert(e.message))
      .finally(() => setRecalculating(false))
  }, [])

  function handleSlider(val: number) {
    setExtra(val)
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => fetchWithExtra(val), 400)
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-gold-500" />
      </div>
    )
  }

  if (error) return <div className="p-6 text-error">{error}</div>
  if (!data) return null

  const avalancheSaves = data.snowball.totalInterest - data.avalanche.totalInterest
  const snowballSaves = data.avalanche.totalInterest - data.snowball.totalInterest
  const avalancheBetter = data.avalanche.totalInterest <= data.snowball.totalInterest

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-text-primary">Debt Payoff Optimizer</h1>
        <p className="text-text-secondary text-sm mt-1">
          Total current debt: <span className="font-mono font-bold text-text-primary">{formatCurrency(data.totalCurrentDebt)}</span>
        </p>
      </div>

      {/* Extra payment slider */}
      <div className="bg-background-secondary rounded-xl border border-border p-5 space-y-3">
        <div className="flex items-center justify-between">
          <label className="text-sm font-semibold text-text-primary">Extra Monthly Payment</label>
          <span className="font-mono font-bold text-gold-500 text-lg">{formatCurrency(extra)}</span>
        </div>
        <input
          type="range"
          min={0}
          max={2000}
          step={25}
          value={extra}
          onChange={e => handleSlider(Number(e.target.value))}
          className="w-full accent-gold-500"
        />
        <div className="flex justify-between text-xs text-text-muted">
          <span>$0</span>
          <span>$500</span>
          <span>$1,000</span>
          <span>$1,500</span>
          <span>$2,000</span>
        </div>
        {recalculating && (
          <p className="text-xs text-gold-500 animate-pulse">Recalculating...</p>
        )}
      </div>

      {/* Strategy comparison */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Avalanche */}
        <div className={cn(
          'rounded-xl border p-5 space-y-4',
          avalancheBetter
            ? 'bg-gradient-to-r from-gold-500/10 to-gold-600/5 border-gold-500/30'
            : 'bg-background-secondary border-border'
        )}>
          <div className="flex items-center justify-between">
            <div>
              <h2 className="font-bold text-text-primary text-base">Avalanche</h2>
              <p className="text-xs text-text-muted">Highest APR first — saves most interest</p>
            </div>
            {avalancheBetter && (
              <span className="px-2 py-0.5 bg-gold-500/20 text-gold-400 text-xs font-bold rounded border border-gold-500/30">
                RECOMMENDED
              </span>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <p className="text-xs text-text-muted">Payoff Date</p>
              <p className="font-mono font-bold text-text-primary">{formatDate(data.avalanche.payoffDate)}</p>
            </div>
            <div>
              <p className="text-xs text-text-muted">Months</p>
              <p className="font-mono font-bold text-text-primary">{data.avalanche.totalMonths}</p>
            </div>
            <div>
              <p className="text-xs text-text-muted">Total Interest</p>
              <p className="font-mono font-bold text-error">{formatCurrency(data.avalanche.totalInterest)}</p>
            </div>
            <div>
              <p className="text-xs text-text-muted">Money Saved vs Snowball</p>
              <p className={cn('font-mono font-bold', avalancheSaves >= 0 ? 'text-success' : 'text-text-muted')}>
                {avalancheSaves >= 0 ? '+' + formatCurrency(avalancheSaves) : '—'}
              </p>
            </div>
          </div>
        </div>

        {/* Snowball */}
        <div className={cn(
          'rounded-xl border p-5 space-y-4',
          !avalancheBetter
            ? 'bg-gradient-to-r from-gold-500/10 to-gold-600/5 border-gold-500/30'
            : 'bg-background-secondary border-border'
        )}>
          <div className="flex items-center justify-between">
            <div>
              <h2 className="font-bold text-text-primary text-base">Snowball</h2>
              <p className="text-xs text-text-muted">Smallest balance first — quick wins</p>
            </div>
            {!avalancheBetter && (
              <span className="px-2 py-0.5 bg-gold-500/20 text-gold-400 text-xs font-bold rounded border border-gold-500/30">
                RECOMMENDED
              </span>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <p className="text-xs text-text-muted">Payoff Date</p>
              <p className="font-mono font-bold text-text-primary">{formatDate(data.snowball.payoffDate)}</p>
            </div>
            <div>
              <p className="text-xs text-text-muted">Months</p>
              <p className="font-mono font-bold text-text-primary">{data.snowball.totalMonths}</p>
            </div>
            <div>
              <p className="text-xs text-text-muted">Total Interest</p>
              <p className="font-mono font-bold text-error">{formatCurrency(data.snowball.totalInterest)}</p>
            </div>
            <div>
              <p className="text-xs text-text-muted">Money Saved vs Avalanche</p>
              <p className={cn('font-mono font-bold', snowballSaves >= 0 ? 'text-success' : 'text-text-muted')}>
                {snowballSaves >= 0 ? '+' + formatCurrency(snowballSaves) : '—'}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Avalanche per-card table */}
      <div className="bg-background-secondary rounded-xl border border-border p-5 space-y-3">
        <h2 className="font-bold text-text-primary">Avalanche — Payoff Order</h2>
        <PayoffTable cards={data.avalanche.cardOrder} />
      </div>

      {/* Snowball per-card table */}
      <div className="bg-background-secondary rounded-xl border border-border p-5 space-y-3">
        <h2 className="font-bold text-text-primary">Snowball — Payoff Order</h2>
        <PayoffTable cards={data.snowball.cardOrder} />
      </div>

      {/* What-If Chart */}
      {whatIf.length > 0 && (
        <div className="bg-background-secondary rounded-xl border border-border p-5 space-y-4">
          <div>
            <h2 className="font-bold text-text-primary">What-If Analysis</h2>
            <p className="text-sm text-text-muted">How extra monthly payments reduce time to payoff</p>
          </div>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={whatIf} margin={{ top: 5, right: 20, left: 10, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#ffffff08" />
                <XAxis
                  dataKey="extraPayment"
                  tickFormatter={v => `$${v}`}
                  tick={{ fill: '#6B7280', fontSize: 11 }}
                  stroke="#ffffff10"
                />
                <YAxis
                  label={{ value: 'Months', angle: -90, position: 'insideLeft', fill: '#6B7280', fontSize: 11 }}
                  tick={{ fill: '#6B7280', fontSize: 11 }}
                  stroke="#ffffff10"
                />
                <Tooltip
                  contentStyle={{ backgroundColor: '#111118', border: '1px solid #2D2D3F', borderRadius: '8px', color: '#E5E7EB' }}
                  formatter={(val: number, name: string) => [val + ' months', name === 'avalancheMonths' ? 'Avalanche' : 'Snowball']}
                  labelFormatter={l => `Extra: $${l}/mo`}
                />
                <ReferenceLine x={extra} stroke="#F59E0B" strokeDasharray="4 2" label={{ value: 'Current', fill: '#F59E0B', fontSize: 10 }} />
                <Line
                  type="monotone"
                  dataKey="avalancheMonths"
                  stroke="#F59E0B"
                  strokeWidth={2}
                  dot={false}
                  name="Avalanche"
                />
                <Line
                  type="monotone"
                  dataKey="snowballMonths"
                  stroke="#3B82F6"
                  strokeWidth={2}
                  dot={false}
                  name="Snowball"
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <div className="flex gap-5 text-xs text-text-muted">
            <div className="flex items-center gap-2">
              <div className="w-4 h-0.5 bg-gold-500" />
              <span>Avalanche</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-4 h-0.5 bg-blue-500" />
              <span>Snowball</span>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
