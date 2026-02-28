'use client'

import { useState } from 'react'
import { api } from '@/lib/api'
import { SavingsPlanResponse, CategoryRecommendation } from '@/lib/types'
import { formatCurrency } from '@/lib/utils'
import {
  TrendingUp, Sparkles, Target, ChevronRight, ArrowRight,
  CheckCircle, AlertCircle, Zap, Clock, XCircle, Bot,
  DollarSign, BarChart3
} from 'lucide-react'

const CATEGORY_EMOJI: Record<string, string> = {
  DINING: '🍽️', RESTAURANTS: '🍽️', FOOD: '🛒', GROCERIES: '🛒',
  SHOPPING: '🛍️', AMAZON: '📦', CLOTHING: '👗', ELECTRONICS: '💻',
  SUBSCRIPTIONS: '📱', ENTERTAINMENT: '🎬', COFFEE: '☕', CAFE: '☕',
  TRANSPORTATION: '🚗', AUTO: '🚗', GAS: '⛽',
  UTILITIES: '💡', HOUSING: '🏠', RENT: '🏠',
  FITNESS: '💪', HEALTHCARE: '🏥', TRAVEL: '✈️',
  PERSONAL_CARE: '💆', PHONE: '📱', INTERNET: '🌐',
}

const DIFFICULTY_CONFIG = {
  EASY: { label: 'Easy Win', color: 'text-green-400', bg: 'bg-green-500/10 border-green-500/20', icon: Zap },
  MEDIUM: { label: 'Some Effort', color: 'text-yellow-400', bg: 'bg-yellow-500/10 border-yellow-500/20', icon: Clock },
  HARD: { label: 'Harder', color: 'text-red-400', bg: 'bg-red-500/10 border-red-500/20', icon: AlertCircle },
}

function ProgressBar({ current, target, max }: { current: number; target: number; max: number }) {
  const currentPct = Math.min((current / max) * 100, 100)
  const targetPct = Math.min((target / max) * 100, 100)
  return (
    <div className="relative h-3 bg-border rounded-full overflow-hidden">
      <div className="absolute inset-0 bg-red-500/20 rounded-full" style={{ width: `${currentPct}%` }} />
      <div className="absolute inset-0 bg-green-500/40 rounded-full transition-all duration-700" style={{ width: `${targetPct}%` }} />
      <div className="absolute top-0 bottom-0 w-0.5 bg-white/30" style={{ left: `${targetPct}%` }} />
    </div>
  )
}

function RecommendationCard({ rec, index }: { rec: CategoryRecommendation; index: number }) {
  const [expanded, setExpanded] = useState(index === 0)
  const emoji = CATEGORY_EMOJI[rec.category] ?? '💰'
  const diff = DIFFICULTY_CONFIG[rec.difficulty] ?? DIFFICULTY_CONFIG.MEDIUM
  const DiffIcon = diff.icon
  const savings10yr = rec.monthlySavings * 12 * ((Math.pow(1.07, 10) - 1) / 0.07)

  return (
    <div className="glass-card rounded-xl border border-border overflow-hidden">
      {/* Header */}
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-4 p-5 hover:bg-background-tertiary/50 transition-colors text-left"
      >
        <div className="text-2xl flex-shrink-0">{emoji}</div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <p className="text-sm font-semibold text-text-primary capitalize">
              {rec.category.toLowerCase().replace(/_/g, ' ')}
            </p>
            <span className={`flex items-center gap-1 text-xs px-2 py-0.5 rounded-full border ${diff.bg} ${diff.color}`}>
              <DiffIcon className="w-3 h-3" />
              {diff.label}
            </span>
          </div>
          <p className="text-xs text-text-muted">{rec.reasoning}</p>
        </div>
        <div className="text-right flex-shrink-0 ml-4">
          <p className="text-lg font-mono font-bold text-green-400">+{formatCurrency(rec.monthlySavings)}</p>
          <p className="text-xs text-text-muted">per month</p>
        </div>
        <ChevronRight className={`w-4 h-4 text-text-muted transition-transform flex-shrink-0 ${expanded ? 'rotate-90' : ''}`} />
      </button>

      {/* Expanded content */}
      {expanded && (
        <div className="border-t border-border px-5 pb-5 pt-4 space-y-4">
          {/* Spend bar: current → target */}
          <div>
            <div className="flex justify-between text-xs text-text-muted mb-2">
              <span>Current: <span className="text-red-400 font-mono font-semibold">{formatCurrency(rec.currentMonthlySpend)}/mo</span></span>
              <span className="flex items-center gap-1">
                <ArrowRight className="w-3 h-3" />
                Target: <span className="text-green-400 font-mono font-semibold ml-1">{formatCurrency(rec.targetMonthlySpend)}/mo</span>
              </span>
              <span>Benchmark: <span className="text-text-muted/60 font-mono">{formatCurrency(rec.benchmarkAmount)}/mo</span></span>
            </div>
            <ProgressBar
              current={rec.currentMonthlySpend}
              target={rec.targetMonthlySpend}
              max={rec.currentMonthlySpend * 1.1}
            />
          </div>

          {/* Opportunity cost */}
          <div className="flex items-start gap-2 bg-gold-500/5 border border-gold-500/20 rounded-lg p-3">
            <Sparkles className="w-4 h-4 text-gold-500 flex-shrink-0 mt-0.5" />
            <p className="text-xs text-text-muted">
              Saving {formatCurrency(rec.monthlySavings)}/month, invested at 7% for 10 years →{' '}
              <span className="text-gold-500 font-semibold font-mono">{formatCurrency(savings10yr)}</span> at retirement.
            </p>
          </div>

          {/* Specific actions */}
          <div>
            <p className="text-xs font-semibold text-text-muted uppercase tracking-wider mb-2">Specific Actions</p>
            <ul className="space-y-2">
              {rec.specificActions.map((action, i) => (
                <li key={i} className="flex items-start gap-2">
                  <CheckCircle className="w-4 h-4 text-green-400 flex-shrink-0 mt-0.5" />
                  <span className="text-sm text-text-primary">{action}</span>
                </li>
              ))}
            </ul>
          </div>

          {/* Top merchants */}
          {rec.topMerchants.length > 0 && (
            <div>
              <p className="text-xs font-semibold text-text-muted uppercase tracking-wider mb-2">Your Top Spending Here</p>
              <div className="flex gap-2 flex-wrap">
                {rec.topMerchants.map((m, i) => (
                  <span key={i} className="text-xs px-2 py-1 bg-background-tertiary rounded-lg text-text-muted">
                    {m.name} · {formatCurrency(m.monthlyAmount)}/mo
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function SpendingRadar({ breakdown }: { breakdown: SavingsPlanResponse['spendingBreakdown'] }) {
  const top8 = breakdown.slice(0, 8)
  return (
    <div className="glass-card rounded-xl p-5 shadow-card">
      <h3 className="text-sm font-semibold text-text-primary mb-4">Your Spending vs Benchmarks</h3>
      <div className="space-y-2">
        {top8.map(cat => {
          const max = Math.max(cat.pctOfIncome, cat.benchmarkPct, 1)
          const statusColor = cat.status === 'OVER' ? 'bg-red-500' : cat.status === 'OK' ? 'bg-yellow-500' : 'bg-green-500'
          return (
            <div key={cat.category} className="flex items-center gap-3">
              <div className="w-24 flex-shrink-0">
                <p className="text-xs text-text-muted truncate capitalize">
                  {cat.category.toLowerCase().replace(/_/g, ' ')}
                </p>
              </div>
              <div className="flex-1 relative h-2 bg-border rounded-full">
                {/* Benchmark line */}
                <div
                  className="absolute top-0 bottom-0 w-0.5 bg-white/20 rounded"
                  style={{ left: `${(cat.benchmarkPct / max) * 100}%` }}
                />
                {/* Actual spend */}
                <div
                  className={`h-full rounded-full ${statusColor}`}
                  style={{ width: `${Math.min((cat.pctOfIncome / max) * 100, 100)}%` }}
                />
              </div>
              <div className="w-20 text-right flex-shrink-0">
                <span className={`text-xs font-mono ${cat.status === 'OVER' ? 'text-red-400' : 'text-text-muted'}`}>
                  {cat.pctOfIncome.toFixed(1)}%
                  {cat.status === 'OVER' && <span className="text-red-400"> ↑</span>}
                </span>
              </div>
            </div>
          )
        })}
      </div>
      <p className="text-xs text-text-muted/60 mt-3">White lines = advisor benchmark. Red bars = over benchmark.</p>
    </div>
  )
}

export default function SavingsPlanPage() {
  const [target, setTarget] = useState('300')
  const [income, setIncome] = useState('')
  const [data, setData] = useState<SavingsPlanResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loadingStep, setLoadingStep] = useState(0)

  const LOADING_STEPS = [
    'Analyzing 3 months of transactions...',
    'Benchmarking your categories against financial standards...',
    'Ranking cuts by ease and impact...',
    'Generating your personalized plan...',
  ]

  const generate = async () => {
    const t = parseFloat(target)
    if (isNaN(t) || t <= 0) return
    setLoading(true)
    setError(null)
    setData(null)
    setLoadingStep(0)

    // Animate loading steps
    const interval = setInterval(() => {
      setLoadingStep(s => (s + 1) % LOADING_STEPS.length)
    }, 2500)

    try {
      const plan = await api.chat.savingsPlan(t)
      setData(plan)
    } catch (e) {
      setError('Could not generate plan. Make sure the backend is running.')
    } finally {
      clearInterval(interval)
      setLoading(false)
    }
  }

  const totalOpportunityCost10yr = data
    ? data.recommendations.reduce((sum, r) => {
        return sum + r.monthlySavings * 12 * ((Math.pow(1.07, 10) - 1) / 0.07)
      }, 0)
    : 0

  return (
    <div className="space-y-6 animate-slide-up max-w-4xl">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-text-primary flex items-center gap-2">
          <Bot className="w-6 h-6 text-gold-500" />
          AI Savings Planner
        </h1>
        <p className="text-text-muted text-sm mt-1">
          Tell the advisor how much more you want to save — it will analyze your actual spending and show you exactly where to cut.
        </p>
      </div>

      {/* Input card */}
      <div className="glass-card rounded-xl p-6 shadow-card">
        <p className="text-sm font-semibold text-text-primary mb-4">What's your savings goal?</p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="text-xs text-text-muted mb-1 block">I want to save an extra</label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted text-sm">$</span>
              <input
                type="number"
                value={target}
                onChange={e => setTarget(e.target.value)}
                placeholder="300"
                className="w-full bg-background-tertiary border border-border rounded-lg pl-7 pr-3 py-2.5 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors font-mono"
              />
            </div>
            <p className="text-xs text-text-muted/60 mt-1">per month, in addition to what you already save</p>
          </div>
          <div className="flex items-end">
            <button
              onClick={generate}
              disabled={loading || !target}
              className="w-full py-2.5 bg-gold-500 text-black font-bold text-sm rounded-lg hover:bg-gold-400 disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
            >
              {loading ? (
                <div className="w-4 h-4 border-t-2 border-black rounded-full animate-spin" />
              ) : (
                <><Sparkles className="w-4 h-4" /> Generate My Plan</>
              )}
            </button>
          </div>
        </div>

        {/* Quick presets */}
        <div className="mt-3 flex gap-2 flex-wrap">
          {[100, 200, 300, 500, 1000].map(v => (
            <button
              key={v}
              onClick={() => setTarget(String(v))}
              className={`px-3 py-1 text-xs rounded-lg border transition-colors ${
                target === String(v)
                  ? 'bg-gold-500/20 border-gold-500/50 text-gold-500'
                  : 'border-border text-text-muted hover:border-gold-500/30'
              }`}
            >
              +${v}/mo
            </button>
          ))}
        </div>
      </div>

      {/* Loading */}
      {loading && (
        <div className="glass-card rounded-xl p-8 text-center">
          <div className="w-10 h-10 border-2 border-gold-500 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
          <p className="text-text-primary font-medium">{LOADING_STEPS[loadingStep]}</p>
          <p className="text-text-muted text-xs mt-1">This may take 10–30 seconds if AI is generating advice</p>
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">
          {error}
        </div>
      )}

      {/* Results */}
      {data && !loading && (
        <div className="space-y-6">

          {/* Current state vs goal */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div className="glass-card rounded-xl p-4 border border-border">
              <p className="text-xs text-text-muted mb-1">Monthly Income</p>
              <p className="text-xl font-mono font-bold text-text-primary">{formatCurrency(data.monthlyIncome)}</p>
            </div>
            <div className="glass-card rounded-xl p-4 border border-border">
              <p className="text-xs text-text-muted mb-1">Currently Saving</p>
              <p className="text-xl font-mono font-bold text-text-primary">{formatCurrency(data.currentMonthlySavings)}</p>
              <p className="text-xs text-text-muted">{data.currentSavingsRatePct.toFixed(1)}% rate</p>
            </div>
            <div className="glass-card rounded-xl p-4 border border-gold-500/20 bg-gold-500/5">
              <p className="text-xs text-text-muted mb-1">Target Savings</p>
              <p className="text-xl font-mono font-bold text-gold-500">{formatCurrency(data.targetMonthlySavings)}</p>
              <p className="text-xs text-text-muted">{data.targetSavingsRatePct.toFixed(1)}% rate</p>
            </div>
            <div className={`glass-card rounded-xl p-4 border ${data.goalAchievable ? 'border-green-500/20 bg-green-500/5' : 'border-yellow-500/20 bg-yellow-500/5'}`}>
              <p className="text-xs text-text-muted mb-1">Plan Covers</p>
              <p className={`text-xl font-mono font-bold ${data.goalAchievable ? 'text-green-400' : 'text-yellow-400'}`}>
                {data.coveragePct.toFixed(0)}%
              </p>
              <p className="text-xs text-text-muted">of your goal</p>
            </div>
          </div>

          {/* AI Narrative */}
          {data.aiNarrative && (
            <div className="glass-card rounded-xl p-6 border border-gold-500/20">
              <div className="flex items-start gap-3">
                <div className="w-9 h-9 rounded-lg bg-gold-500/10 border border-gold-500/30 flex items-center justify-center flex-shrink-0">
                  <Bot className="w-5 h-5 text-gold-500" />
                </div>
                <div>
                  <p className="text-xs font-semibold text-gold-500 mb-2">
                    {data.aiAvailable ? 'AI Advisor Analysis' : 'Financial Analysis'}
                  </p>
                  <p className="text-sm text-text-primary leading-relaxed whitespace-pre-wrap">{data.aiNarrative}</p>
                </div>
              </div>
            </div>
          )}

          {/* 10-year opportunity cost */}
          {totalOpportunityCost10yr > 0 && (
            <div className="flex items-center gap-3 bg-gold-500/5 border border-gold-500/20 rounded-xl p-4">
              <TrendingUp className="w-5 h-5 text-gold-500 flex-shrink-0" />
              <p className="text-sm text-text-muted">
                If you make these cuts and invest {formatCurrency(data.totalRecommendedSavings)}/month at 7% for 10 years →{' '}
                <span className="text-gold-500 font-mono font-bold">{formatCurrency(totalOpportunityCost10yr)}</span> at retirement.
                That's what your spending habits are ACTUALLY costing you.
              </p>
            </div>
          )}

          <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
            {/* Recommendations */}
            <div className="lg:col-span-3 space-y-3">
              <h2 className="text-sm font-semibold text-text-primary flex items-center gap-2">
                <Target className="w-4 h-4 text-gold-500" />
                Your Personalized Cut Plan
              </h2>
              {data.recommendations.length === 0 ? (
                <div className="glass-card rounded-xl p-6 text-center text-text-muted text-sm">
                  Your spending is already close to benchmark across all categories. The fastest win is auditing subscriptions or setting stricter dining limits.
                </div>
              ) : (
                data.recommendations.map((rec, i) => (
                  <RecommendationCard key={rec.category} rec={rec} index={i} />
                ))
              )}
            </div>

            {/* Spending vs benchmarks */}
            <div className="lg:col-span-2">
              {data.spendingBreakdown.length > 0 && (
                <SpendingRadar breakdown={data.spendingBreakdown} />
              )}
            </div>
          </div>

        </div>
      )}
    </div>
  )
}
