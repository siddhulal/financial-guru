'use client'

import { useState, useEffect } from 'react'
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, ReferenceLine,
} from 'recharts'
import {
  Brain, TrendingUp, AlertTriangle, CheckCircle,
  Target, Bot, ChevronDown, ChevronUp,
  Flame, Shield, Sparkles,
} from 'lucide-react'
import { api } from '@/lib/api'
import { BrainReportResponse, ActionItem, ScenarioResult } from '@/lib/types'
import { formatCurrency } from '@/lib/utils'

// ── Readiness Gauge ─────────────────────────────────────────────────────────
function ReadinessGauge({ score, grade }: { score: number; grade: string }) {
  const color = score >= 70 ? '#22c55e' : score >= 50 ? '#F59E0B' : '#ef4444'
  const radius = 54
  const circumference = Math.PI * radius // half-circle

  return (
    <div className="flex flex-col items-center">
      <div className="relative w-36 h-20 overflow-hidden">
        <svg viewBox="0 0 120 60" className="w-full h-full">
          {/* Track */}
          <path d="M 10 60 A 50 50 0 0 1 110 60" fill="none" stroke="#2D2D3F" strokeWidth="10" strokeLinecap="round" />
          {/* Progress */}
          <path
            d="M 10 60 A 50 50 0 0 1 110 60"
            fill="none"
            stroke={color}
            strokeWidth="10"
            strokeLinecap="round"
            strokeDasharray={`${circumference}`}
            strokeDashoffset={`${circumference * (1 - score / 100)}`}
            style={{ transition: 'stroke-dashoffset 1s ease' }}
          />
        </svg>
        <div className="absolute bottom-0 left-1/2 -translate-x-1/2 text-center">
          <div className="text-2xl font-bold font-mono" style={{ color }}>{score}</div>
        </div>
      </div>
      <div className="text-3xl font-bold mt-1" style={{ color }}>{grade}</div>
      <div className="text-xs text-text-muted mt-0.5">Retirement Readiness</div>
    </div>
  )
}

// ── Trajectory Chart ────────────────────────────────────────────────────────
function TrajectoryChart({ data, fiNumber, targetAge }: {
  data: BrainReportResponse
  fiNumber: number
  targetAge: number
}) {
  const chartData = data.currentPathProjections.map((p, i) => ({
    age: p.age,
    current: p.portfolioValue,
    optimal: data.optimalPathProjections[i]?.portfolioValue ?? 0,
  }))

  const fmtY = (v: number) => {
    if (v >= 1_000_000) return `$${(v / 1_000_000).toFixed(1)}M`
    if (v >= 1_000) return `$${(v / 1_000).toFixed(0)}K`
    return `$${v}`
  }

  return (
    <ResponsiveContainer width="100%" height={240}>
      <AreaChart data={chartData} margin={{ top: 10, right: 10, left: 10, bottom: 0 }}>
        <defs>
          <linearGradient id="currentGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor="#ef4444" stopOpacity={0.3} />
            <stop offset="95%" stopColor="#ef4444" stopOpacity={0} />
          </linearGradient>
          <linearGradient id="optimalGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor="#22c55e" stopOpacity={0.3} />
            <stop offset="95%" stopColor="#22c55e" stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="#ffffff08" />
        <XAxis
          dataKey="age"
          tick={{ fill: '#6B7280', fontSize: 10 }}
          stroke="#ffffff10"
          label={{ value: 'Age', position: 'insideBottomRight', offset: -5, fill: '#6B7280', fontSize: 10 }}
        />
        <YAxis tickFormatter={fmtY} tick={{ fill: '#6B7280', fontSize: 10 }} stroke="#ffffff10" width={55} />
        <Tooltip
          contentStyle={{ backgroundColor: '#111118', border: '1px solid #2D2D3F', borderRadius: '8px' }}
          formatter={(val: number, name: string) => [fmtY(val), name === 'current' ? 'Current Path' : 'Optimal (20% savings)']}
          labelFormatter={(l) => `Age ${l}`}
        />
        <ReferenceLine
          y={fiNumber}
          stroke="#F59E0B"
          strokeDasharray="5 3"
          label={{ value: 'FI Target', position: 'right', fill: '#F59E0B', fontSize: 10 }}
        />
        <ReferenceLine
          x={targetAge}
          stroke="#6B7280"
          strokeDasharray="3 3"
          label={{ value: `Goal: ${targetAge}`, position: 'top', fill: '#6B7280', fontSize: 10 }}
        />
        <Area type="monotone" dataKey="optimal" stroke="#22c55e" strokeWidth={2}
          fill="url(#optimalGrad)" dot={false} />
        <Area type="monotone" dataKey="current" stroke="#ef4444" strokeWidth={2}
          fill="url(#currentGrad)" dot={false} />
      </AreaChart>
    </ResponsiveContainer>
  )
}

// ── Scenario Card ────────────────────────────────────────────────────────────
function ScenarioCard({ s, fiNumber }: { s: ScenarioResult; fiNumber: number }) {
  const colorClasses =
    s.color === 'green'
      ? 'text-green-400 border-green-500/20 bg-green-500/5'
      : s.color === 'blue'
      ? 'text-blue-400 border-blue-500/20 bg-blue-500/5'
      : 'text-gold-500 border-gold-500/20 bg-gold-500/5'
  const [textColor, ...borderBgClasses] = colorClasses.split(' ')
  const pct = fiNumber > 0 ? Math.min(100, (s.portfolioAtTargetAge / fiNumber) * 100) : 0
  const barColor =
    s.color === 'green' ? 'bg-green-500' : s.color === 'blue' ? 'bg-blue-500' : 'bg-gold-500'

  return (
    <div className={`glass-card rounded-xl p-4 border ${borderBgClasses.join(' ')}`}>
      <p className="text-xs text-text-muted mb-1">{s.description}</p>
      <p className={`text-base font-bold ${textColor} leading-tight`}>{s.headline}</p>
      <div className="mt-3">
        <div className="flex justify-between text-xs text-text-muted mb-1">
          <span>Portfolio at {s.projectedRetirementAge > 80 ? '65' : 'retirement'}</span>
          <span className="font-mono">{pct.toFixed(0)}% of target</span>
        </div>
        <div className="h-1.5 bg-border rounded-full">
          <div
            className={`h-full rounded-full transition-all duration-700 ${barColor}`}
            style={{ width: `${pct}%` }}
          />
        </div>
      </div>
    </div>
  )
}

// ── Action Item ──────────────────────────────────────────────────────────────
function ActionCard({ action }: { action: ActionItem }) {
  const [open, setOpen] = useState(false)
  const typeConfig: Record<ActionItem['type'], { icon: React.ElementType; color: string }> = {
    SPENDING: { icon: Flame, color: 'text-red-400' },
    SAVING: { icon: Shield, color: 'text-green-400' },
    INVESTING: { icon: TrendingUp, color: 'text-blue-400' },
    DEBT: { icon: AlertTriangle, color: 'text-yellow-400' },
  }
  const cfg = typeConfig[action.type]
  const Icon = cfg.icon

  return (
    <div className="glass-card rounded-xl border border-border overflow-hidden">
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center gap-3 p-4 hover:bg-background-tertiary/50 transition-colors text-left"
      >
        <div className="w-7 h-7 rounded-lg bg-background-tertiary flex items-center justify-center flex-shrink-0">
          <span className="text-xs font-bold text-text-muted">#{action.rank}</span>
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-text-primary">{action.title}</p>
          <p className="text-xs text-text-muted mt-0.5 flex items-center gap-2">
            <Icon className={`w-3 h-3 ${cfg.color}`} />
            {action.type.toLowerCase().replace('_', ' ')}
            {action.monthlyImpact > 0 && (
              <span className="text-green-400 font-mono">+{formatCurrency(action.monthlyImpact)}/mo</span>
            )}
            {action.yearsEarlierRetirement > 0 && (
              <span className="text-gold-500">→ {action.yearsEarlierRetirement}yr earlier</span>
            )}
          </p>
        </div>
        {open
          ? <ChevronUp className="w-4 h-4 text-text-muted flex-shrink-0" />
          : <ChevronDown className="w-4 h-4 text-text-muted flex-shrink-0" />}
      </button>
      {open && (
        <div className="border-t border-border px-4 pb-4 pt-3">
          <p className="text-sm text-text-muted leading-relaxed">{action.detail}</p>
        </div>
      )}
    </div>
  )
}

// ── Profile Setup Banner ─────────────────────────────────────────────────────
function ProfileSetupBanner() {
  return (
    <div className="flex items-start gap-3 p-4 bg-gold-500/8 border border-gold-500/30 rounded-xl">
      <Brain className="w-5 h-5 text-gold-500 flex-shrink-0 mt-0.5" />
      <div>
        <p className="text-sm font-semibold text-gold-500">Complete Your Profile for Full Analysis</p>
        <p className="text-xs text-text-muted mt-1">
          Go to <strong>Budget → Profile</strong> and set your <strong>age</strong>, <strong>target retirement age</strong>,
          and <strong>monthly income</strong>. With that data, the brain can tell you exactly when you&apos;ll retire and what to change.
        </p>
      </div>
    </div>
  )
}

// ── Main Page ────────────────────────────────────────────────────────────────
export default function BrainPage() {
  const [data, setData] = useState<BrainReportResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.brain
      .report()
      .then(setData)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  if (loading)
    return (
      <div className="flex flex-col items-center justify-center h-64 gap-4">
        <div className="w-12 h-12 border-2 border-gold-500 border-t-transparent rounded-full animate-spin" />
        <p className="text-text-muted text-sm">Analyzing your financial trajectory...</p>
      </div>
    )

  if (error) return <div className="p-6 text-error">{error}</div>
  if (!data) return null

  const trajectoryColor = data.onTrack ? 'text-green-400' : 'text-red-400'
  const trajectoryBg = data.onTrack
    ? 'bg-green-500/8 border-green-500/20'
    : 'bg-red-500/8 border-red-500/20'

  return (
    <div className="space-y-6 animate-slide-up max-w-5xl">

      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text-primary flex items-center gap-2">
            <Brain className="w-6 h-6 text-gold-500" />
            Financial Brain
          </h1>
          <p className="text-text-muted text-sm mt-1">
            Your complete retirement trajectory — based on actual spending, not guesses
          </p>
        </div>
        <ReadinessGauge score={data.retirementReadinessScore} grade={data.retirementReadinessGrade} />
      </div>

      {/* Setup banner */}
      {!data.profileComplete && <ProfileSetupBanner />}

      {/* Trajectory headline */}
      <div className={`p-5 rounded-xl border ${trajectoryBg}`}>
        <div className="flex items-start gap-3">
          {data.onTrack
            ? <CheckCircle className="w-5 h-5 text-green-400 flex-shrink-0 mt-0.5" />
            : <AlertTriangle className="w-5 h-5 text-red-400 flex-shrink-0 mt-0.5" />}
          <div>
            <p className={`text-lg font-bold ${trajectoryColor}`}>{data.trajectoryHeadline}</p>
            {!data.onTrack && data.monthlyGapToTarget > 0 && (
              <p className="text-sm text-text-muted mt-1">
                To retire at <strong>{data.targetRetirementAge}</strong>, you need{' '}
                <span className="text-gold-500 font-mono font-semibold">
                  +{formatCurrency(data.monthlyGapToTarget)}/month
                </span>{' '}
                more in savings. That&apos;s{' '}
                <span className="text-gold-500 font-semibold">
                  {data.monthlyIncome > 0
                    ? ((data.monthlyGapToTarget / data.monthlyIncome) * 100).toFixed(1)
                    : '?'}%
                </span>{' '}
                of your income — achievable with the cuts below.
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Key numbers */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {[
          { label: 'Monthly Income', val: formatCurrency(data.monthlyIncome), sub: null },
          { label: 'Monthly Savings', val: formatCurrency(data.monthlySavings), sub: `${data.savingsRatePct.toFixed(1)}% rate` },
          { label: 'Retirement Target', val: formatCurrency(data.fiNumber), sub: '25× annual expenses' },
          {
            label: 'Projected at ' + data.targetRetirementAge,
            val: formatCurrency(data.projectedCorpusAtTargetAge),
            sub: data.fiNumber > 0
              ? `${Math.min(100, Math.round(data.projectedCorpusAtTargetAge / data.fiNumber * 100))}% of target`
              : null,
          },
        ].map(item => (
          <div key={item.label} className="glass-card rounded-xl p-4 border border-border">
            <p className="text-xs text-text-muted mb-1">{item.label}</p>
            <p className="text-lg font-mono font-bold text-text-primary">{item.val}</p>
            {item.sub && <p className="text-xs text-text-muted mt-0.5">{item.sub}</p>}
          </div>
        ))}
      </div>

      {/* Trajectory chart */}
      <div className="glass-card rounded-xl p-5 border border-border shadow-card">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="font-bold text-text-primary">Wealth Trajectory to Age 85</h2>
            <p className="text-xs text-text-muted mt-0.5">
              <span className="inline-block w-3 h-0.5 bg-red-500 mr-1 align-middle" />Current path &nbsp;
              <span className="inline-block w-3 h-0.5 bg-green-500 mr-1 align-middle" />Optimal (20% savings) &nbsp;
              <span className="inline-block w-3 h-0.5 bg-gold-500 mr-1 align-middle" />FI target
            </p>
          </div>
        </div>
        <TrajectoryChart data={data} fiNumber={data.fiNumber} targetAge={data.targetRetirementAge} />
      </div>

      {/* Scenarios */}
      <div>
        <h2 className="font-bold text-text-primary mb-3 flex items-center gap-2">
          <Sparkles className="w-4 h-4 text-gold-500" />
          What If You Saved More?
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          {data.scenarios.map(s => (
            <ScenarioCard key={s.id} s={s} fiNumber={data.fiNumber} />
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

        {/* AI Narrative */}
        <div className="glass-card rounded-xl p-5 border border-gold-500/20 shadow-card">
          <div className="flex items-center gap-2 mb-3">
            <Bot className="w-5 h-5 text-gold-500" />
            <span className="text-sm font-semibold text-gold-500">
              {data.aiAvailable ? 'AI Advisor Analysis' : 'Financial Analysis'}
            </span>
          </div>
          <p className="text-sm text-text-primary leading-relaxed whitespace-pre-wrap">{data.aiNarrative}</p>
        </div>

        {/* Action Roadmap */}
        <div>
          <h2 className="font-bold text-text-primary mb-3 flex items-center gap-2">
            <Target className="w-4 h-4 text-gold-500" />
            Your Action Roadmap
            <span className="text-xs text-text-muted font-normal">ranked by retirement impact</span>
          </h2>
          <div className="space-y-2">
            {data.actionRoadmap.length === 0 ? (
              <div className="glass-card rounded-xl p-5 text-center text-text-muted text-sm">
                Your spending is well-optimized! Focus on growing your income or increasing investment contributions.
              </div>
            ) : (
              data.actionRoadmap.map(action => (
                <ActionCard key={action.rank} action={action} />
              ))
            )}
          </div>
        </div>
      </div>

      {/* Spending categories table */}
      {data.topCategories.length > 0 && (
        <div className="glass-card rounded-xl border border-border shadow-card overflow-hidden">
          <div className="p-4 border-b border-border">
            <h2 className="font-bold text-text-primary">Spending Impact Analysis</h2>
            <p className="text-xs text-text-muted mt-0.5">
              How your spending categories affect your retirement date (10-year projection at 7%)
            </p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border">
                  <th className="text-left py-2.5 px-4 text-xs font-semibold text-text-muted uppercase tracking-wider">Category</th>
                  <th className="text-right py-2.5 px-4 text-xs font-semibold text-text-muted uppercase tracking-wider">Monthly</th>
                  <th className="text-right py-2.5 px-4 text-xs font-semibold text-text-muted uppercase tracking-wider">% Income</th>
                  <th className="text-right py-2.5 px-4 text-xs font-semibold text-text-muted uppercase tracking-wider">Benchmark</th>
                  <th className="text-right py-2.5 px-4 text-xs font-semibold text-text-muted uppercase tracking-wider">10yr Cost</th>
                  <th className="text-center py-2.5 px-4 text-xs font-semibold text-text-muted uppercase tracking-wider">Status</th>
                </tr>
              </thead>
              <tbody>
                {data.topCategories.map(cat => (
                  <tr key={cat.category} className="border-b border-border/50 hover:bg-background-tertiary/30 transition-colors">
                    <td className="py-2.5 px-4 font-medium text-text-primary capitalize">
                      {cat.category.toLowerCase().replace(/_/g, ' ')}
                    </td>
                    <td className="py-2.5 px-4 text-right font-mono text-text-primary">
                      {formatCurrency(cat.monthlyAvg)}
                    </td>
                    <td className="py-2.5 px-4 text-right font-mono text-text-muted">
                      {cat.pctOfIncome.toFixed(1)}%
                    </td>
                    <td className="py-2.5 px-4 text-right font-mono text-text-muted">
                      {cat.benchmarkPct.toFixed(0)}%
                    </td>
                    <td className="py-2.5 px-4 text-right font-mono text-gold-500 font-semibold">
                      {formatCurrency(cat.retirementImpact10yr)}
                    </td>
                    <td className="py-2.5 px-4 text-center">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                        cat.status === 'OVER'
                          ? 'bg-red-500/15 text-red-400'
                          : cat.status === 'OK'
                          ? 'bg-yellow-500/15 text-yellow-400'
                          : 'bg-green-500/15 text-green-400'
                      }`}>
                        {cat.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

    </div>
  )
}
