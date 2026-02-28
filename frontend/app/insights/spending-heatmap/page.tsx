'use client'

import { useEffect, useState, useCallback } from 'react'
import { api } from '@/lib/api'
import { SpendingHeatmapResponse, HeatmapDay, MerchantTrendResponse, DuplicateTransactionGroup } from '@/lib/types'
import { cn } from '@/lib/utils'
import { TrendingUp, TrendingDown, Minus, AlertTriangle, ChevronDown, ChevronUp } from 'lucide-react'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts'

const INTENSITY_CLASSES = [
  'bg-background-tertiary',
  'bg-green-900',
  'bg-green-700',
  'bg-green-500',
  'bg-green-300',
]

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const DAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

function buildWeekGrid(days: HeatmapDay[], year: number) {
  // Build a map of date -> day
  const dayMap = new Map<string, HeatmapDay>()
  for (const d of days) dayMap.set(d.date, d)

  // Figure out the first day of year
  const jan1 = new Date(year, 0, 1)
  const startDow = jan1.getDay() // 0=Sun

  // We build up to 53 weeks x 7 days
  const weeks: (HeatmapDay | null)[][] = []
  let week: (HeatmapDay | null)[] = new Array(startDow).fill(null)

  const end = new Date(year, 11, 31)
  const cur = new Date(year, 0, 1)
  while (cur <= end) {
    const isoDate = cur.toISOString().slice(0, 10)
    week.push(dayMap.get(isoDate) || { date: isoDate, totalSpend: 0, transactionCount: 0, intensity: 0 })
    if (week.length === 7) {
      weeks.push(week)
      week = []
    }
    cur.setDate(cur.getDate() + 1)
  }
  // pad last week
  if (week.length > 0) {
    while (week.length < 7) week.push(null)
    weeks.push(week)
  }
  return weeks
}

function getMonthPositions(year: number): { label: string; col: number }[] {
  const positions: { label: string; col: number }[] = []
  const seen = new Set<number>()
  const jan1 = new Date(year, 0, 1)
  const startDow = jan1.getDay()
  let col = 0
  let dayCount = startDow

  for (let month = 0; month < 12; month++) {
    const daysInMonth = new Date(year, month + 1, 0).getDate()
    const startCol = Math.floor(dayCount / 7)
    if (!seen.has(startCol)) {
      positions.push({ label: MONTH_NAMES[month], col: startCol })
      seen.add(startCol)
    }
    dayCount += daysInMonth
  }
  return positions
}

interface TooltipState {
  day: HeatmapDay
  x: number
  y: number
}

export default function SpendingHeatmapPage() {
  const currentYear = new Date().getFullYear()
  const [year, setYear] = useState(currentYear)
  const [data, setData] = useState<SpendingHeatmapResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [tooltip, setTooltip] = useState<TooltipState | null>(null)

  // Merchant trends
  const [topMerchants, setTopMerchants] = useState<string[]>([])
  const [selectedMerchant, setSelectedMerchant] = useState<string | null>(null)
  const [merchantTrend, setMerchantTrend] = useState<MerchantTrendResponse | null>(null)
  const [trendLoading, setTrendLoading] = useState(false)

  // Duplicates
  const [duplicates, setDuplicates] = useState<DuplicateTransactionGroup[]>([])
  const [dismissedGroups, setDismissedGroups] = useState<Set<string>>(new Set())

  useEffect(() => {
    loadHeatmap()
    loadDuplicates()
  }, [year])

  async function loadHeatmap() {
    setLoading(true)
    setError(null)
    try {
      const result = await api.insights.spendingHeatmap(year)
      setData(result)
      // Extract top merchants from transaction data — just show placeholder merchant list
      // In real app the backend should return top merchants; here we derive from heatmap
      const merchantSet = new Set<string>()
      // We'll just set some common ones if none available
    } catch {
      setError('Failed to load spending heatmap')
    } finally {
      setLoading(false)
    }
  }

  async function loadDuplicates() {
    try {
      const dups = await api.insights.duplicates()
      setDuplicates(dups)
    } catch {
      // ignore
    }
  }

  async function loadMerchantTrend(merchant: string) {
    setSelectedMerchant(merchant)
    setTrendLoading(true)
    try {
      const trend = await api.insights.merchantTrend(merchant)
      setMerchantTrend(trend)
    } catch {
      setMerchantTrend(null)
    } finally {
      setTrendLoading(false)
    }
  }

  const weeks = data ? buildWeekGrid(data.days, year) : []
  const monthPositions = data ? getMonthPositions(year) : []

  const yearOptions = [currentYear - 2, currentYear - 1, currentYear]

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-gold-500" />
      </div>
    )
  }
  if (error) return <div className="p-6 text-red-400">{error}</div>

  const visibleDuplicates = duplicates.filter(g => !dismissedGroups.has(`${g.merchantName}-${g.amount}`))

  return (
    <div className="space-y-6 animate-slide-up">
      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Spending Heatmap</h1>
          <p className="text-text-muted text-sm mt-1">Daily spending patterns for {year}</p>
        </div>
        <div className="flex gap-1">
          {yearOptions.map(y => (
            <button
              key={y}
              onClick={() => setYear(y)}
              className={cn(
                'px-4 py-2 text-sm rounded-lg transition-colors',
                year === y
                  ? 'bg-gold-500 text-black font-semibold'
                  : 'bg-background-secondary border border-border text-text-secondary hover:bg-background-tertiary'
              )}
            >
              {y}
            </button>
          ))}
        </div>
      </div>

      {/* Stats */}
      {data && (
        <div className="grid grid-cols-2 gap-4">
          <div className="bg-background-secondary rounded-xl border border-border p-5">
            <p className="text-xs text-text-muted uppercase tracking-wide mb-1">Total Annual Spend</p>
            <p className="text-3xl font-mono font-bold text-text-primary">
              ${data.totalAnnualSpend.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </p>
          </div>
          <div className="bg-background-secondary rounded-xl border border-border p-5">
            <p className="text-xs text-text-muted uppercase tracking-wide mb-1">Peak Daily Spend</p>
            <p className="text-3xl font-mono font-bold text-gold-500">
              ${data.maxDailySpend.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </p>
          </div>
        </div>
      )}

      {/* Legend */}
      <div className="flex items-center gap-3 text-xs text-text-muted">
        <span>Less</span>
        {INTENSITY_CLASSES.map((cls, i) => (
          <div key={i} className={cn('w-3 h-3 rounded-sm', cls)} />
        ))}
        <span>More</span>
      </div>

      {/* Heatmap grid */}
      {data && (
        <div className="bg-background-secondary rounded-xl border border-border p-6 overflow-x-auto">
          <div className="relative" style={{ minWidth: `${weeks.length * 14 + 32}px` }}>
            {/* Month labels */}
            <div className="flex mb-1 ml-8">
              {monthPositions.map(({ label, col }) => (
                <div
                  key={label}
                  className="absolute text-xs text-text-muted"
                  style={{ left: `${32 + col * 14}px` }}
                >
                  {label}
                </div>
              ))}
            </div>

            <div className="flex mt-5">
              {/* Day labels */}
              <div className="flex flex-col gap-0.5 mr-2 flex-shrink-0">
                {DAY_LABELS.map((day, i) => (
                  <div key={day} className="h-3 text-[9px] text-text-muted flex items-center" style={{ height: '14px' }}>
                    {i % 2 === 1 ? day.slice(0, 1) : ''}
                  </div>
                ))}
              </div>

              {/* Weeks */}
              <div className="flex gap-0.5">
                {weeks.map((week, wi) => (
                  <div key={wi} className="flex flex-col gap-0.5">
                    {week.map((day, di) => (
                      <div
                        key={di}
                        className={cn(
                          'w-3 h-3 rounded-sm cursor-pointer transition-all hover:ring-1 hover:ring-white/30',
                          day ? INTENSITY_CLASSES[day.intensity] : 'bg-transparent'
                        )}
                        style={{ width: '12px', height: '12px' }}
                        onMouseEnter={e => {
                          if (day && day.totalSpend > 0) {
                            const rect = (e.target as HTMLElement).getBoundingClientRect()
                            setTooltip({ day, x: rect.left + window.scrollX, y: rect.top + window.scrollY })
                          }
                        }}
                        onMouseLeave={() => setTooltip(null)}
                      />
                    ))}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Tooltip */}
      {tooltip && (
        <div
          className="fixed z-50 bg-background-secondary border border-border rounded-lg px-3 py-2 text-xs shadow-xl pointer-events-none"
          style={{ left: tooltip.x + 16, top: tooltip.y - 40 }}
        >
          <p className="font-semibold text-text-primary">{tooltip.day.date}</p>
          <p className="text-gold-500 font-mono">${tooltip.day.totalSpend.toFixed(2)}</p>
          <p className="text-text-muted">{tooltip.day.transactionCount} transaction{tooltip.day.transactionCount !== 1 ? 's' : ''}</p>
        </div>
      )}

      {/* Merchant Trends */}
      <div className="bg-background-secondary rounded-xl border border-border p-6 space-y-4">
        <h2 className="text-base font-semibold text-text-primary">Merchant Trends</h2>
        <p className="text-xs text-text-muted">Click a merchant to see their 12-month spending trend</p>

        {/* Merchant input */}
        <div className="flex gap-2">
          <input
            type="text"
            placeholder="Enter merchant name..."
            className="flex-1 bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
            onKeyDown={e => {
              if (e.key === 'Enter') {
                const val = (e.target as HTMLInputElement).value.trim()
                if (val) loadMerchantTrend(val)
              }
            }}
          />
          <button
            className="px-4 py-2 bg-gold-500 text-black text-sm font-semibold rounded-lg hover:bg-gold-400 transition-colors"
            onClick={(e) => {
              const input = (e.currentTarget.previousElementSibling as HTMLInputElement)
              if (input?.value.trim()) loadMerchantTrend(input.value.trim())
            }}
          >
            Search
          </button>
        </div>

        {/* Trend result */}
        {trendLoading && (
          <div className="flex items-center justify-center h-32">
            <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-gold-500" />
          </div>
        )}

        {merchantTrend && !trendLoading && (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-text-primary">{merchantTrend.merchantName}</h3>
              <div className={cn(
                'flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium',
                merchantTrend.trend === 'INCREASING' ? 'bg-red-500/10 text-red-400' :
                merchantTrend.trend === 'DECREASING' ? 'bg-green-500/10 text-green-400' :
                'bg-background-tertiary text-text-muted'
              )}>
                {merchantTrend.trend === 'INCREASING' ? <TrendingUp className="w-3 h-3" /> :
                 merchantTrend.trend === 'DECREASING' ? <TrendingDown className="w-3 h-3" /> :
                 <Minus className="w-3 h-3" />}
                {merchantTrend.trend}
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <p className="text-text-muted text-xs">Total Annual</p>
                <p className="font-mono font-semibold text-text-primary">${merchantTrend.totalAnnual.toFixed(2)}</p>
              </div>
              <div>
                <p className="text-text-muted text-xs">Avg Monthly</p>
                <p className="font-mono font-semibold text-text-primary">${merchantTrend.avgMonthly.toFixed(2)}</p>
              </div>
            </div>
            <div className="h-48">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={merchantTrend.months}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1A1A24" />
                  <XAxis dataKey="month" tick={{ fill: '#6B7280', fontSize: 11 }} />
                  <YAxis tick={{ fill: '#6B7280', fontSize: 11 }} />
                  <Tooltip
                    contentStyle={{ backgroundColor: '#111118', border: '1px solid #2A2A35', borderRadius: '8px', color: '#F9FAFB' }}
                    formatter={(v: number) => [`$${v.toFixed(2)}`, 'Spend']}
                  />
                  <Line type="monotone" dataKey="amount" stroke="#F59E0B" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}
      </div>

      {/* Duplicate Detection */}
      <div className="bg-background-secondary rounded-xl border border-border p-6 space-y-4">
        <div className="flex items-center gap-2">
          <AlertTriangle className="w-5 h-5 text-amber-400" />
          <h2 className="text-base font-semibold text-text-primary">Potential Duplicates</h2>
          {visibleDuplicates.length > 0 && (
            <span className="ml-2 px-2 py-0.5 text-xs font-medium bg-amber-500/10 text-amber-400 rounded-full">
              {visibleDuplicates.length}
            </span>
          )}
        </div>

        {visibleDuplicates.length === 0 && (
          <p className="text-text-muted text-sm">No potential duplicate charges detected.</p>
        )}

        {visibleDuplicates.map(group => {
          const key = `${group.merchantName}-${group.amount}`
          return (
            <div key={key} className="border border-amber-500/20 rounded-xl p-4 space-y-3 bg-amber-500/5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-semibold text-text-primary">{group.merchantName}</p>
                  <p className="text-sm text-text-muted">
                    ${group.amount.toFixed(2)} charged {group.transactions.length}x within {group.withinDays} days
                  </p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => setDismissedGroups(prev => new Set(Array.from(prev).concat(key)))}
                    className="px-3 py-1 text-xs text-text-muted border border-border rounded hover:bg-background-tertiary transition-colors"
                  >
                    Dismiss
                  </button>
                  <button className="px-3 py-1 text-xs bg-amber-500/20 text-amber-400 border border-amber-500/30 rounded hover:bg-amber-500/30 transition-colors">
                    Flag
                  </button>
                </div>
              </div>
              <div className="space-y-1">
                {group.transactions.map(t => (
                  <div key={t.id} className="flex items-center justify-between text-xs">
                    <span className="text-text-muted">{t.transactionDate}</span>
                    <span className="text-text-primary font-mono">${t.amount.toFixed(2)}</span>
                  </div>
                ))}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
