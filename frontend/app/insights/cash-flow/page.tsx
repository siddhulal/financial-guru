'use client'

import { useState, useEffect } from 'react'
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
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { api } from '@/lib/api'
import { CashFlowResponse, CashFlowEvent } from '@/lib/types'
import { cn } from '@/lib/utils'

function formatCurrency(n: number) {
  return '$' + n.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
}

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

const DOT_COLORS: Record<CashFlowEvent['type'], string> = {
  INCOME: 'bg-success',
  PAYMENT: 'bg-error',
  SUBSCRIPTION: 'bg-orange-400',
}

function getDaysInMonth(year: number, month: number) {
  return new Date(year, month, 0).getDate()
}

function getFirstDayOfMonth(year: number, month: number) {
  return new Date(year, month - 1, 1).getDay()
}

export default function CashFlowPage() {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [data, setData] = useState<CashFlowResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    api.insights.cashFlow(year, month)
      .then(setData)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [year, month])

  function prevMonth() {
    if (month === 1) { setMonth(12); setYear(y => y - 1) }
    else setMonth(m => m - 1)
  }

  function nextMonth() {
    if (month === 12) { setMonth(1); setYear(y => y + 1) }
    else setMonth(m => m + 1)
  }

  // Group events by day number
  const eventsByDay: Record<number, CashFlowEvent[]> = {}
  const dangerDays = new Set<number>()
  if (data) {
    data.events.forEach(ev => {
      const day = new Date(ev.date).getDate()
      if (!eventsByDay[day]) eventsByDay[day] = []
      eventsByDay[day].push(ev)
      if (ev.isDangerDay) dangerDays.add(day)
    })
  }

  // Running balance chart data (one point per day that has a balance)
  const chartData = data
    ? (() => {
        const days: Record<number, number> = {}
        data.events.forEach(ev => {
          const day = new Date(ev.date).getDate()
          days[day] = ev.runningBalance
        })
        // Fill in gaps with last known balance
        const daysInMonth = getDaysInMonth(year, month)
        const result: { day: number; balance: number }[] = []
        let lastBalance = data.startingBalance
        for (let d = 1; d <= daysInMonth; d++) {
          if (days[d] !== undefined) lastBalance = days[d]
          result.push({ day: d, balance: lastBalance })
        }
        return result
      })()
    : []

  const daysInMonth = getDaysInMonth(year, month)
  const firstDay = getFirstDayOfMonth(year, month)

  // Calendar cells: empty cells for days before the 1st, then days 1..daysInMonth
  const cells: (number | null)[] = []
  for (let i = 0; i < firstDay; i++) cells.push(null)
  for (let d = 1; d <= daysInMonth; d++) cells.push(d)

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-gold-500" />
      </div>
    )
  }

  if (error) return <div className="p-6 text-error">{error}</div>

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-text-primary">Cash Flow Calendar</h1>
        <p className="text-text-secondary text-sm mt-1">
          Visualize income, bills, and balance throughout the month
        </p>
      </div>

      {/* Month navigation */}
      <div className="flex items-center gap-4">
        <button
          onClick={prevMonth}
          className="p-2 rounded-lg border border-border hover:border-gold-500/40 text-text-muted hover:text-gold-500 transition-colors"
        >
          <ChevronLeft className="w-4 h-4" />
        </button>
        <span className="text-lg font-bold text-text-primary min-w-48 text-center">
          {MONTH_NAMES[month - 1]} {year}
        </span>
        <button
          onClick={nextMonth}
          className="p-2 rounded-lg border border-border hover:border-gold-500/40 text-text-muted hover:text-gold-500 transition-colors"
        >
          <ChevronRight className="w-4 h-4" />
        </button>
        {data && (
          <span className="text-sm text-text-muted ml-2">
            Starting balance: <span className="font-mono font-semibold text-text-secondary">{formatCurrency(data.startingBalance)}</span>
          </span>
        )}
      </div>

      {/* Calendar */}
      <div className="bg-background-secondary rounded-xl border border-border p-4">
        {/* Day headers */}
        <div className="grid grid-cols-7 mb-2">
          {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map(d => (
            <div key={d} className="text-center text-xs font-semibold text-text-muted py-2">{d}</div>
          ))}
        </div>

        {/* Calendar grid */}
        <div className="grid grid-cols-7 gap-1">
          {cells.map((day, idx) => {
            if (day === null) {
              return <div key={`empty-${idx}`} className="h-20 rounded-lg" />
            }

            const events = eventsByDay[day] || []
            const isDanger = dangerDays.has(day)
            const isToday = now.getFullYear() === year && (now.getMonth() + 1) === month && now.getDate() === day

            return (
              <div
                key={day}
                className={cn(
                  'h-20 rounded-lg p-1.5 border text-xs overflow-hidden',
                  isDanger ? 'bg-error/10 border-error/30' : 'bg-background-tertiary/50 border-transparent',
                  isToday ? 'ring-1 ring-gold-500/50' : ''
                )}
              >
                <div className={cn(
                  'text-xs font-mono font-bold mb-1',
                  isToday ? 'text-gold-500' : isDanger ? 'text-error' : 'text-text-muted'
                )}>
                  {day}
                </div>
                <div className="space-y-0.5">
                  {events.slice(0, 3).map((ev, i) => (
                    <div key={i} className="flex items-center gap-1">
                      <div className={cn('w-1.5 h-1.5 rounded-full flex-shrink-0', DOT_COLORS[ev.type])} />
                      <span className="text-text-muted truncate" style={{ fontSize: '9px', lineHeight: '1.2' }}>
                        {ev.description.length > 10 ? ev.description.slice(0, 10) + '…' : ev.description}
                      </span>
                    </div>
                  ))}
                  {events.length > 3 && (
                    <span className="text-text-muted" style={{ fontSize: '9px' }}>+{events.length - 3} more</span>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* Legend */}
      <div className="flex flex-wrap gap-5 text-xs text-text-muted">
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-success" />
          <span>Income</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-error" />
          <span>Payment / Bill</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-orange-400" />
          <span>Subscription</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-lg bg-error/15 border border-error/30" />
          <span>Danger day (balance &lt; $500)</span>
        </div>
      </div>

      {/* Running balance chart */}
      {chartData.length > 0 && (
        <div className="bg-background-secondary rounded-xl border border-border p-5 space-y-3">
          <div>
            <h2 className="font-bold text-text-primary">Running Balance</h2>
            <p className="text-xs text-text-muted">Red line = $500 danger threshold</p>
          </div>
          <div className="h-52">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 5, right: 15, left: 15, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#ffffff08" />
                <XAxis
                  dataKey="day"
                  tick={{ fill: '#6B7280', fontSize: 11 }}
                  stroke="#ffffff10"
                  label={{ value: 'Day', position: 'insideBottomRight', offset: -5, fill: '#6B7280', fontSize: 10 }}
                />
                <YAxis
                  tickFormatter={v => '$' + (v / 1000).toFixed(0) + 'K'}
                  tick={{ fill: '#6B7280', fontSize: 11 }}
                  stroke="#ffffff10"
                />
                <Tooltip
                  contentStyle={{ backgroundColor: '#111118', border: '1px solid #2D2D3F', borderRadius: '8px', color: '#E5E7EB' }}
                  formatter={(val: number) => [formatCurrency(val), 'Balance']}
                  labelFormatter={l => `Day ${l}`}
                />
                <ReferenceLine
                  y={500}
                  stroke="#EF4444"
                  strokeDasharray="4 2"
                  label={{ value: '$500', position: 'right', fill: '#EF4444', fontSize: 10 }}
                />
                <Line
                  type="stepAfter"
                  dataKey="balance"
                  stroke="#F59E0B"
                  strokeWidth={2}
                  dot={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Events list */}
      {data && data.events.length > 0 && (
        <div className="bg-background-secondary rounded-xl border border-border p-5 space-y-3">
          <h2 className="font-bold text-text-primary">All Events</h2>
          <div className="space-y-2">
            {data.events.map((ev, i) => (
              <div key={i} className={cn(
                'flex items-center justify-between py-2 px-3 rounded-lg text-sm',
                ev.isDangerDay ? 'bg-error/8 border border-error/20' : 'bg-background-tertiary/40'
              )}>
                <div className="flex items-center gap-3">
                  <div className={cn('w-2 h-2 rounded-full', DOT_COLORS[ev.type])} />
                  <div>
                    <span className="text-text-primary font-medium">{ev.description}</span>
                    <span className="text-text-muted ml-2 text-xs">
                      {new Date(ev.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                    </span>
                  </div>
                </div>
                <div className="flex items-center gap-4 text-right">
                  <span className={cn('font-mono font-semibold', ev.type === 'INCOME' ? 'text-success' : 'text-error')}>
                    {ev.type === 'INCOME' ? '+' : '-'}{formatCurrency(ev.amount)}
                  </span>
                  <span className={cn('font-mono text-xs', ev.runningBalance < 500 ? 'text-error' : 'text-text-muted')}>
                    {formatCurrency(ev.runningBalance)}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
