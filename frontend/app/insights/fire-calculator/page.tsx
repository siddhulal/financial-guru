'use client'

import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { FireCalculatorResponse } from '@/lib/types'
import { cn } from '@/lib/utils'
import { Info, Flame, TrendingUp, Calendar, DollarSign } from 'lucide-react'
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  ReferenceLine
} from 'recharts'

interface FIREInputs {
  age: string
  targetRetirementAge: string
  currentInvestments: string
  monthlyExpenses: string
}

function formatFireDate(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
}

function formatLargeNum(n: number): string {
  if (n >= 1_000_000) return `$${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `$${(n / 1_000).toFixed(0)}K`
  return `$${n.toFixed(0)}`
}

export default function FireCalculatorPage() {
  const [inputs, setInputs] = useState<FIREInputs>({
    age: '30',
    targetRetirementAge: '50',
    currentInvestments: '',
    monthlyExpenses: '',
  })
  const [data, setData] = useState<FireCalculatorResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dashboardLoaded, setDashboardLoaded] = useState(false)

  // Auto-populate monthly expenses from dashboard
  useEffect(() => {
    api.dashboard.get()
      .then(d => {
        if (d.currentMonthSpend > 0 && !inputs.monthlyExpenses) {
          setInputs(prev => ({ ...prev, monthlyExpenses: d.currentMonthSpend.toFixed(0) }))
        }
        setDashboardLoaded(true)
      })
      .catch(() => setDashboardLoaded(true))
  }, [])

  async function calculate() {
    const age = parseInt(inputs.age)
    const targetRetirementAge = parseInt(inputs.targetRetirementAge)
    const currentInvestments = parseFloat(inputs.currentInvestments) || 0
    const monthlyExpenses = parseFloat(inputs.monthlyExpenses) || 0

    if (isNaN(age) || age < 18 || age > 100) return
    if (isNaN(targetRetirementAge) || targetRetirementAge <= age) return
    if (monthlyExpenses <= 0) return

    setLoading(true)
    setError(null)
    try {
      const result = await api.insights.fireCalculator({
        age,
        targetRetirementAge,
        currentInvestments,
        monthlyExpenses,
      })
      setData(result)
    } catch {
      setError('Failed to calculate FIRE projections')
    } finally {
      setLoading(false)
    }
  }

  // Build chart data by merging projections with monte carlo
  const chartData = data
    ? data.projections.map((p, i) => ({
        year: p.year,
        median: data.monteCarloP50[i] || p.portfolioValue,
        pessimistic: data.monteCarloP10[i] || undefined,
        optimistic: data.monteCarloP90[i] || undefined,
        main: p.portfolioValue,
      }))
    : []

  const fireYear = data ? new Date(data.fireDate).getFullYear() : null

  return (
    <div className="space-y-6 animate-slide-up">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Flame className="w-7 h-7 text-orange-400" />
        <div>
          <h1 className="text-2xl font-bold text-text-primary">FIRE Calculator</h1>
          <p className="text-text-muted text-sm">Financial Independence, Retire Early</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        {/* Input panel */}
        <div className="lg:col-span-2 bg-background-secondary rounded-xl border border-border p-6 space-y-5 h-fit">
          <h2 className="text-base font-semibold text-text-primary">Your Numbers</h2>

          <div>
            <label className="block text-xs font-medium text-text-muted mb-1">Current Age</label>
            <input
              type="number"
              min="18" max="100"
              value={inputs.age}
              onChange={e => setInputs(p => ({ ...p, age: e.target.value }))}
              className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-text-muted mb-1">Target Retirement Age</label>
            <input
              type="number"
              min="20" max="80"
              value={inputs.targetRetirementAge}
              onChange={e => setInputs(p => ({ ...p, targetRetirementAge: e.target.value }))}
              className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-text-muted mb-1">Current Investments / Savings ($)</label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted text-sm">$</span>
              <input
                type="number"
                min="0"
                step="1000"
                value={inputs.currentInvestments}
                onChange={e => setInputs(p => ({ ...p, currentInvestments: e.target.value }))}
                placeholder="50000"
                className="w-full bg-background-tertiary border border-border rounded-lg pl-7 pr-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-text-muted mb-1">
              Monthly Expenses ($)
              {dashboardLoaded && inputs.monthlyExpenses && (
                <span className="ml-2 text-gold-500 font-normal">auto-populated from spending</span>
              )}
            </label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted text-sm">$</span>
              <input
                type="number"
                min="1"
                step="100"
                value={inputs.monthlyExpenses}
                onChange={e => setInputs(p => ({ ...p, monthlyExpenses: e.target.value }))}
                placeholder="3000"
                className="w-full bg-background-tertiary border border-border rounded-lg pl-7 pr-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
              />
            </div>
          </div>

          <button
            onClick={calculate}
            disabled={loading}
            className="w-full py-3 bg-gold-500 text-black font-bold text-sm rounded-lg hover:bg-gold-400 disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
          >
            {loading ? (
              <div className="w-4 h-4 border-t-2 border-black rounded-full animate-spin" />
            ) : (
              <><Flame className="w-4 h-4" /> Calculate FIRE Date</>
            )}
          </button>

          {/* Assumptions */}
          <div className="flex gap-2 bg-background-tertiary rounded-lg p-3">
            <Info className="w-4 h-4 text-text-muted flex-shrink-0 mt-0.5" />
            <p className="text-xs text-text-muted">
              Assumes 7% annual return, 3% inflation, 4% safe withdrawal rate (25x annual expenses)
            </p>
          </div>
        </div>

        {/* Results panel */}
        <div className="lg:col-span-3 space-y-5">
          {error && <div className="p-4 text-red-400 bg-red-500/10 rounded-xl border border-red-500/20 text-sm">{error}</div>}

          {!data && !loading && !error && (
            <div className="bg-background-secondary rounded-xl border border-border p-12 text-center">
              <Flame className="w-12 h-12 text-orange-400/30 mx-auto mb-4" />
              <p className="text-text-muted">Enter your numbers and click Calculate to see your FIRE projection</p>
            </div>
          )}

          {data && (
            <>
              {/* Big stats */}
              <div className="grid grid-cols-2 gap-4">
                <div className="bg-background-secondary rounded-xl border border-gold-500/20 p-5 bg-gold-500/5">
                  <p className="text-xs text-text-muted uppercase tracking-wide mb-1">FI Number</p>
                  <p className="text-3xl font-mono font-bold text-gold-500">
                    {formatLargeNum(data.fiNumber)}
                  </p>
                  <p className="text-xs text-text-muted mt-1">25x annual expenses</p>
                </div>
                <div className="bg-background-secondary rounded-xl border border-border p-5">
                  <p className="text-xs text-text-muted uppercase tracking-wide mb-1">Years to FIRE</p>
                  <p className="text-3xl font-mono font-bold text-text-primary">
                    {data.yearsToFire.toFixed(1)}
                  </p>
                  <p className="text-xs text-text-muted mt-1">{formatFireDate(data.fireDate)}</p>
                </div>
                <div className="bg-background-secondary rounded-xl border border-border p-5">
                  <p className="text-xs text-text-muted uppercase tracking-wide mb-1">Savings Rate</p>
                  <p className={cn(
                    'text-3xl font-mono font-bold',
                    data.savingsRate >= 50 ? 'text-green-400' :
                    data.savingsRate >= 25 ? 'text-yellow-400' : 'text-red-400'
                  )}>
                    {data.savingsRate.toFixed(1)}%
                  </p>
                  <p className="text-xs text-text-muted mt-1">of estimated income</p>
                </div>
                <div className="bg-background-secondary rounded-xl border border-border p-5">
                  <p className="text-xs text-text-muted uppercase tracking-wide mb-1">Monthly Savings</p>
                  <p className="text-3xl font-mono font-bold text-text-primary">
                    {formatLargeNum(data.monthlySavings)}
                  </p>
                  {data.monthlySavingsGap > 0 && (
                    <p className="text-xs text-amber-400 mt-1">
                      Gap: ${data.monthlySavingsGap.toFixed(0)}/mo to hit target
                    </p>
                  )}
                </div>
              </div>

              {/* Chart */}
              {chartData.length > 0 && (
                <div className="bg-background-secondary rounded-xl border border-border p-6">
                  <h2 className="text-base font-semibold text-text-primary mb-1">Portfolio Projection</h2>
                  <p className="text-xs text-text-muted mb-4">Shaded area shows Monte Carlo range (P10–P90)</p>
                  <div className="h-64">
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                        <defs>
                          <linearGradient id="rangeGrad" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="#F59E0B" stopOpacity={0.15} />
                            <stop offset="95%" stopColor="#F59E0B" stopOpacity={0.02} />
                          </linearGradient>
                          <linearGradient id="medianGrad" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="#F59E0B" stopOpacity={0.3} />
                            <stop offset="95%" stopColor="#F59E0B" stopOpacity={0} />
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 3" stroke="#1A1A24" />
                        <XAxis
                          dataKey="year"
                          tick={{ fill: '#6B7280', fontSize: 11 }}
                          tickLine={false}
                        />
                        <YAxis
                          tick={{ fill: '#6B7280', fontSize: 11 }}
                          tickFormatter={v => formatLargeNum(v).replace('$', '')}
                          tickLine={false}
                          axisLine={false}
                        />
                        <Tooltip
                          contentStyle={{ backgroundColor: '#111118', border: '1px solid #2A2A35', borderRadius: '8px', color: '#F9FAFB' }}
                          formatter={(v: number, name: string) => [formatLargeNum(v), name]}
                        />
                        {/* Monte Carlo range */}
                        <Area
                          type="monotone"
                          dataKey="optimistic"
                          stroke="none"
                          fill="url(#rangeGrad)"
                          fillOpacity={1}
                          name="Optimistic (P90)"
                        />
                        <Area
                          type="monotone"
                          dataKey="pessimistic"
                          stroke="none"
                          fill="transparent"
                          name="Pessimistic (P10)"
                        />
                        {/* Median */}
                        <Area
                          type="monotone"
                          dataKey="median"
                          stroke="#F59E0B"
                          strokeWidth={2}
                          fill="url(#medianGrad)"
                          name="Median (P50)"
                          dot={false}
                        />
                        {/* FI Number reference line */}
                        <ReferenceLine
                          y={data.fiNumber}
                          stroke="#F59E0B"
                          strokeDasharray="6 3"
                          strokeOpacity={0.6}
                          label={{ value: 'FI Number', fill: '#F59E0B', fontSize: 11, position: 'insideTopRight' }}
                        />
                        {/* FIRE year reference line */}
                        {fireYear && (
                          <ReferenceLine
                            x={fireYear}
                            stroke="#10B981"
                            strokeDasharray="6 3"
                            strokeOpacity={0.6}
                            label={{ value: 'FIRE', fill: '#10B981', fontSize: 11, position: 'insideTopLeft' }}
                          />
                        )}
                      </AreaChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  )
}
