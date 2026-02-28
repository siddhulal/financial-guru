'use client'

import { DashboardData } from '@/lib/types'
import { formatCurrency } from '@/lib/utils'
import Link from 'next/link'
import { TrendingUp, TrendingDown, Minus } from 'lucide-react'

interface Props {
  data: DashboardData
}

function SavingsRateBar({ rate, target = 20 }: { rate: number; target?: number }) {
  const pct = Math.max(0, Math.min(rate, 60))
  const targetPct = Math.min(target, 60)
  const color = rate >= target ? 'bg-green-500' : rate >= target * 0.75 ? 'bg-yellow-500' : 'bg-red-500'
  return (
    <div className="relative h-2 bg-border rounded-full overflow-visible mt-2">
      <div className={`h-full rounded-full transition-all duration-500 ${color}`} style={{ width: `${(pct / 60) * 100}%` }} />
      {/* Target marker */}
      <div
        className="absolute top-1/2 -translate-y-1/2 w-0.5 h-4 bg-gold-500/70 rounded"
        style={{ left: `${(targetPct / 60) * 100}%` }}
      />
    </div>
  )
}

export function WealthKPIWidget({ data }: Props) {
  const sr = data.monthlySavingsRate ?? 0
  const fm = data.freedomMonths ?? 0
  const fmTrend = data.freedomMonthsTrend ?? 0
  const material = data.materialSpendThisMonth ?? 0
  const materialLast = data.materialSpendLastMonth ?? 0
  const materialDiff = material - materialLast

  const years = data.yearsToRetirementAtCurrentRate
  const avg6 = data.avgSavingsRate6Month ?? 0

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
      {/* ── Card 1: Savings Rate ────────────────────────── */}
      <div className="glass-card rounded-xl p-5 shadow-card border border-border">
        <p className="text-xs text-text-muted uppercase tracking-wider mb-1">Savings Rate This Month</p>
        <div className="flex items-baseline gap-2">
          <span className={`text-4xl font-mono font-bold ${
            sr >= 20 ? 'text-green-400' : sr >= 10 ? 'text-yellow-400' : 'text-red-400'
          }`}>
            {sr.toFixed(1)}%
          </span>
          <span className="text-text-muted text-sm">target: 20%</span>
        </div>
        <SavingsRateBar rate={sr} />
        <p className="text-xs text-text-muted mt-2">
          {avg6 > 0 && (
            <>6-mo avg: <span className="font-num text-text-primary">{avg6.toFixed(1)}%</span> · </>
          )}
          {years !== null && years !== undefined
            ? `at this rate: retire in ~${years} yrs`
            : 'Set income in Budget → Profile to track'}
        </p>
        {sr < 20 && sr > 0 && (
          <p className="text-xs text-gold-500 mt-1.5">
            +{(20 - sr).toFixed(1)}% more = retire ~{Math.round((20 - sr) * 1.5)} years earlier
          </p>
        )}
      </div>

      {/* ── Card 2: Freedom Number ───────────────────────── */}
      <div className="glass-card rounded-xl p-5 shadow-card border border-border">
        <p className="text-xs text-text-muted uppercase tracking-wider mb-1">Freedom Number</p>
        <div className="flex items-baseline gap-2">
          <span className={`text-4xl font-mono font-bold ${
            fm >= 6 ? 'text-green-400' : fm >= 3 ? 'text-yellow-400' : 'text-red-400'
          }`}>
            {fm.toFixed(1)}
          </span>
          <span className="text-text-muted text-sm">months</span>
        </div>
        <p className="text-sm text-text-muted mt-1">
          You could live <span className="text-text-primary font-medium">{fm.toFixed(1)} months</span> without income
        </p>
        <div className="flex items-center gap-1.5 mt-2 text-xs">
          {fmTrend > 0 ? (
            <><TrendingUp className="w-3.5 h-3.5 text-green-400" />
            <span className="text-green-400">+{formatCurrency(fmTrend)} this month → runway growing</span></>
          ) : fmTrend < 0 ? (
            <><TrendingDown className="w-3.5 h-3.5 text-red-400" />
            <span className="text-red-400">{formatCurrency(fmTrend)} this month → runway shrinking</span></>
          ) : (
            <><Minus className="w-3.5 h-3.5 text-text-muted" />
            <span className="text-text-muted">No income data yet — set it in Budget → Profile</span></>
          )}
        </div>
        <Link href="/insights/health-score" className="text-xs text-gold-500 hover:text-gold-400 mt-2 block">
          View health score →
        </Link>
      </div>

      {/* ── Card 3: Material Spending ────────────────────── */}
      <div className="glass-card rounded-xl p-5 shadow-card border border-border">
        <p className="text-xs text-text-muted uppercase tracking-wider mb-1">Things You Bought This Month</p>
        <div className="flex items-baseline gap-2">
          <span className="text-4xl font-mono font-bold text-text-primary">
            {formatCurrency(material)}
          </span>
        </div>
        <p className="text-xs text-text-muted mt-1">Shopping · Clothing · Electronics</p>
        <div className="flex items-center gap-1.5 mt-2 text-xs">
          {materialDiff > 10 ? (
            <><TrendingUp className="w-3.5 h-3.5 text-red-400" />
            <span className="text-red-400">Up {formatCurrency(materialDiff)} vs last month — what did you buy?</span></>
          ) : materialDiff < -10 ? (
            <><TrendingDown className="w-3.5 h-3.5 text-green-400" />
            <span className="text-green-400">Down {formatCurrency(Math.abs(materialDiff))} vs last month</span></>
          ) : (
            <span className="text-text-muted">Similar to last month ({formatCurrency(materialLast)})</span>
          )}
        </div>
        <Link href="/insights/spending-heatmap" className="text-xs text-gold-500 hover:text-gold-400 mt-2 block">
          View spending breakdown →
        </Link>
      </div>
    </div>
  )
}
