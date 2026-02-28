'use client'

import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts'
import { formatCurrency, formatPercent, getCategoryEmoji, CATEGORY_COLORS } from '@/lib/utils'

interface Props {
  data: { category: string; amount: number; percent: number }[]
}

const CustomTooltip = ({ active, payload }: any) => {
  if (active && payload && payload.length) {
    const d = payload[0].payload
    return (
      <div className="glass-card rounded-lg p-3 shadow-card">
        <p className="text-xs text-text-muted mb-1">
          {getCategoryEmoji(d.category)} {d.category}
        </p>
        <p className="text-sm font-num font-semibold text-text-primary">{formatCurrency(d.amount)}</p>
        <p className="text-xs text-text-muted">{formatPercent(d.percent)} of spending</p>
      </div>
    )
  }
  return null
}

export function CategoryDonut({ data }: Props) {
  const top5 = data.slice(0, 5)
  const rest = data.slice(5)
  const otherAmount = rest.reduce((s, d) => s + d.amount, 0)
  const chartData = otherAmount > 0
    ? [...top5, { category: 'Other', amount: otherAmount, percent: 0 }]
    : top5

  return (
    <div className="glass-card rounded-xl p-5 shadow-card">
      <div className="flex items-baseline justify-between mb-4">
        <h3 className="text-sm font-semibold text-text-primary">Spending by Category</h3>
        <span className="text-xs text-text-muted">YTD</span>
      </div>
      <div className="flex items-center gap-4">
        <div className="flex-shrink-0">
          <ResponsiveContainer width={120} height={120}>
            <PieChart>
              <Pie
                data={chartData}
                cx="50%"
                cy="50%"
                innerRadius={35}
                outerRadius={55}
                paddingAngle={2}
                dataKey="amount"
              >
                {chartData.map((_, index) => (
                  <Cell
                    key={index}
                    fill={CATEGORY_COLORS[index % CATEGORY_COLORS.length]}
                    stroke="transparent"
                  />
                ))}
              </Pie>
              <Tooltip content={<CustomTooltip />} />
            </PieChart>
          </ResponsiveContainer>
        </div>
        <div className="flex-1 space-y-1.5">
          {chartData.slice(0, 5).map((d, i) => (
            <div key={d.category} className="flex items-center gap-2">
              <div
                className="w-2 h-2 rounded-full flex-shrink-0"
                style={{ backgroundColor: CATEGORY_COLORS[i % CATEGORY_COLORS.length] }}
              />
              <span className="text-xs text-text-secondary flex-1 truncate">
                {getCategoryEmoji(d.category)} {d.category}
              </span>
              <span className="text-xs font-num text-text-muted">
                {formatCurrency(d.amount, true)}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
