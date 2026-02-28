'use client'

import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'
import { formatCurrency, formatMonthYear } from '@/lib/utils'

interface Props {
  data: { month: string; amount: number }[]
}

const CustomTooltip = ({ active, payload, label }: any) => {
  if (active && payload && payload.length) {
    return (
      <div className="glass-card rounded-lg p-3 shadow-card">
        <p className="text-xs text-text-muted mb-1">{formatMonthYear(label)}</p>
        <p className="text-sm font-num font-semibold text-gold-500">
          {formatCurrency(payload[0].value)}
        </p>
      </div>
    )
  }
  return null
}

export function SpendingChart({ data }: Props) {
  const chartData = data.map(d => ({
    ...d,
    displayMonth: d.month,
  }))

  return (
    <div className="glass-card rounded-xl p-5 shadow-card">
      <div className="flex items-center justify-between mb-5">
        <div>
          <h3 className="text-sm font-semibold text-text-primary">Monthly Spending</h3>
          <p className="text-xs text-text-muted mt-0.5">Last 6 months</p>
        </div>
      </div>
      <ResponsiveContainer width="100%" height={160}>
        <AreaChart data={chartData} margin={{ top: 5, right: 5, left: -20, bottom: 0 }}>
          <defs>
            <linearGradient id="spendGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#F59E0B" stopOpacity={0.15} />
              <stop offset="95%" stopColor="#F59E0B" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#1E1E2E" vertical={false} />
          <XAxis
            dataKey="month"
            tickFormatter={formatMonthYear}
            tick={{ fill: '#9090A8', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            tickFormatter={(v) => formatCurrency(v, true)}
            tick={{ fill: '#9090A8', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip content={<CustomTooltip />} />
          <Area
            type="monotone"
            dataKey="amount"
            stroke="#F59E0B"
            strokeWidth={2}
            fill="url(#spendGradient)"
            dot={{ fill: '#F59E0B', r: 3, strokeWidth: 0 }}
            activeDot={{ r: 5, fill: '#F59E0B', strokeWidth: 0 }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  )
}
