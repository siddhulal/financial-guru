'use client'

import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { CreditScoreResponse } from '@/lib/types'
import { cn } from '@/lib/utils'
import { Info, TrendingUp, AlertTriangle, CheckCircle } from 'lucide-react'

function getScoreColor(score: number): string {
  if (score < 580) return 'text-red-400'
  if (score < 670) return 'text-orange-400'
  if (score < 740) return 'text-yellow-400'
  if (score < 800) return 'text-green-400'
  return 'text-emerald-400'
}

function getScoreLabel(score: number): string {
  if (score < 580) return 'Poor'
  if (score < 670) return 'Fair'
  if (score < 740) return 'Good'
  if (score < 800) return 'Very Good'
  return 'Exceptional'
}

function getScoreBgColor(score: number): string {
  if (score < 580) return '#EF4444'
  if (score < 670) return '#F97316'
  if (score < 740) return '#EAB308'
  if (score < 800) return '#22C55E'
  return '#10B981'
}

function getUtilBg(pct: number): string {
  if (pct > 30) return 'bg-red-500'
  if (pct > 10) return 'bg-yellow-500'
  return 'bg-green-500'
}

function getUtilText(pct: number): string {
  if (pct > 30) return 'text-red-400'
  if (pct > 10) return 'text-yellow-400'
  return 'text-green-400'
}

// SVG speedometer
function ScoreGauge({ score }: { score: number }) {
  const min = 300
  const max = 850
  const pct = (score - min) / (max - min)
  // Arc: semicircle from 180deg to 0deg
  const angleRange = 180
  const angle = 180 - pct * angleRange // 180 = left, 0 = right
  const rad = (angle * Math.PI) / 180
  const cx = 100
  const cy = 90
  const r = 70

  // Needle tip
  const nx = cx + r * Math.cos(rad)
  const ny = cy - r * Math.sin(rad)

  const color = getScoreBgColor(score)

  return (
    <svg viewBox="0 0 200 110" className="w-full max-w-xs mx-auto">
      {/* Background arc */}
      <path
        d={`M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`}
        fill="none"
        stroke="#1A1A24"
        strokeWidth="16"
        strokeLinecap="round"
      />
      {/* Colored arc segments */}
      {[
        { pct: 0.18, color: '#EF4444' },    // Poor: 300-580
        { pct: 0.16, color: '#F97316' },    // Fair: 580-669
        { pct: 0.13, color: '#EAB308' },    // Good: 670-739
        { pct: 0.11, color: '#22C55E' },    // Very Good: 740-799
        { pct: 0.42, color: '#10B981' },    // Exceptional: 800+
      ].reduce((acc, seg, i) => {
        const startAngle = 180 - acc.total * 180
        const endAngle = 180 - (acc.total + seg.pct) * 180
        const startRad = (startAngle * Math.PI) / 180
        const endRad = (endAngle * Math.PI) / 180
        const x1 = cx + r * Math.cos(startRad)
        const y1 = cy - r * Math.sin(startRad)
        const x2 = cx + r * Math.cos(endRad)
        const y2 = cy - r * Math.sin(endRad)
        acc.paths.push(
          <path
            key={i}
            d={`M ${x1} ${y1} A ${r} ${r} 0 0 1 ${x2} ${y2}`}
            fill="none"
            stroke={seg.color}
            strokeWidth="16"
            strokeLinecap="butt"
            opacity="0.7"
          />
        )
        acc.total += seg.pct
        return acc
      }, { total: 0, paths: [] as React.ReactNode[] }).paths}

      {/* Needle */}
      <line
        x1={cx} y1={cy}
        x2={nx} y2={ny}
        stroke={color}
        strokeWidth="3"
        strokeLinecap="round"
      />
      {/* Needle hub */}
      <circle cx={cx} cy={cy} r="6" fill={color} />
      <circle cx={cx} cy={cy} r="3" fill="#0A0A0F" />

      {/* Score text */}
      <text x={cx} y={cy - 15} textAnchor="middle" fontSize="22" fontWeight="bold" fill="white" fontFamily="monospace">
        {score}
      </text>
    </svg>
  )
}

export default function CreditScorePage() {
  const [data, setData] = useState<CreditScoreResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.insights.creditScore()
      .then(setData)
      .catch(() => setError('Failed to load credit score data'))
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-gold-500" />
      </div>
    )
  }
  if (error) return <div className="p-6 text-red-400">{error}</div>
  if (!data) return null

  const score = data.estimatedScore
  const scoreColor = getScoreColor(score)
  const scoreLabel = getScoreLabel(score)

  return (
    <div className="space-y-6 animate-slide-up">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-text-primary">Credit Score Optimizer</h1>
        <p className="text-text-muted text-sm mt-1">Estimated based on your credit utilization patterns</p>
      </div>

      {/* Score hero */}
      <div className="bg-background-secondary rounded-xl border border-border p-8">
        <div className="max-w-sm mx-auto text-center space-y-4">
          <ScoreGauge score={score} />

          <div>
            <p className={cn('text-5xl font-mono font-bold', scoreColor)}>{score}</p>
            <p className={cn('text-xl font-semibold mt-1', scoreColor)}>{scoreLabel}</p>
            <p className="text-xs text-text-muted mt-2 max-w-xs mx-auto">
              Estimated based on utilization — not a real FICO score
            </p>
          </div>

          <div className={cn(
            'inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium',
            data.utilizationImpact === 'EXCELLENT' ? 'bg-emerald-500/10 text-emerald-400' :
            data.utilizationImpact === 'GOOD' ? 'bg-green-500/10 text-green-400' :
            data.utilizationImpact === 'FAIR' ? 'bg-yellow-500/10 text-yellow-400' :
            'bg-red-500/10 text-red-400'
          )}>
            Utilization Impact: {data.utilizationImpact}
          </div>
        </div>
      </div>

      {/* Score range legend */}
      <div className="bg-background-secondary rounded-xl border border-border p-4">
        <div className="flex items-center justify-between text-xs">
          {[
            { label: 'Poor', range: '300–579', color: 'text-red-400' },
            { label: 'Fair', range: '580–669', color: 'text-orange-400' },
            { label: 'Good', range: '670–739', color: 'text-yellow-400' },
            { label: 'Very Good', range: '740–799', color: 'text-green-400' },
            { label: 'Exceptional', range: '800+', color: 'text-emerald-400' },
          ].map(item => (
            <div key={item.label} className="text-center">
              <p className={cn('font-semibold', item.color)}>{item.label}</p>
              <p className="text-text-muted">{item.range}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Per-card breakdown */}
      <div className="bg-background-secondary rounded-xl border border-border p-6">
        <h2 className="text-base font-semibold text-text-primary mb-4">Card Utilization Breakdown</h2>
        {data.cards.length === 0 ? (
          <p className="text-text-muted text-sm">No credit card data available.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border">
                  <th className="text-left py-2 text-xs text-text-muted font-medium">Card</th>
                  <th className="text-right py-2 text-xs text-text-muted font-medium">Balance</th>
                  <th className="text-right py-2 text-xs text-text-muted font-medium">Limit</th>
                  <th className="text-right py-2 text-xs text-text-muted font-medium">Utilization</th>
                  <th className="text-right py-2 text-xs text-text-muted font-medium">Rec. Payment</th>
                </tr>
              </thead>
              <tbody>
                {data.cards.map(card => (
                  <tr key={card.accountId} className="border-b border-border/50">
                    <td className="py-3 text-text-primary font-medium">{card.accountName}</td>
                    <td className="py-3 text-right font-mono text-text-primary">
                      ${card.balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                    </td>
                    <td className="py-3 text-right font-mono text-text-muted">
                      ${card.creditLimit.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}
                    </td>
                    <td className="py-3 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <div className="w-20 h-1.5 bg-background-tertiary rounded-full overflow-hidden">
                          <div
                            className={cn('h-full rounded-full', getUtilBg(card.utilizationPct))}
                            style={{ width: `${Math.min(card.utilizationPct, 100)}%` }}
                          />
                        </div>
                        <span className={cn('font-mono font-semibold', getUtilText(card.utilizationPct))}>
                          {card.utilizationPct.toFixed(1)}%
                        </span>
                      </div>
                    </td>
                    <td className="py-3 text-right font-mono text-gold-500">
                      {card.recommendedPayment > 0
                        ? `$${card.recommendedPayment.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
                        : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* What-If Scenarios */}
      {data.whatIfScenarios.length > 0 && (
        <div className="bg-background-secondary rounded-xl border border-border p-6">
          <h2 className="text-base font-semibold text-text-primary mb-4">What-If Scenarios</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {data.whatIfScenarios.map((scenario, i) => (
              <div key={i} className="border border-gold-500/20 rounded-xl p-4 bg-gold-500/5 space-y-2">
                <p className="text-sm font-medium text-text-primary">{scenario.description}</p>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-text-muted">Payment</span>
                  <span className="font-mono text-gold-500 font-semibold">
                    ${scenario.paymentAmount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </span>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-text-muted">New Utilization</span>
                  <span className={cn('font-mono font-semibold', getUtilText(scenario.newUtilizationPct))}>
                    {scenario.newUtilizationPct.toFixed(1)}%
                  </span>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-text-muted">Score Impact</span>
                  <span className={cn(
                    'font-mono font-semibold',
                    scenario.estimatedScoreImpact > 0 ? 'text-green-400' : scenario.estimatedScoreImpact < 0 ? 'text-red-400' : 'text-text-muted'
                  )}>
                    {scenario.estimatedScoreImpact > 0 ? '+' : ''}{scenario.estimatedScoreImpact} pts
                  </span>
                </div>
                <button className="w-full mt-1 py-1.5 text-xs font-medium border border-gold-500/30 text-gold-500 rounded hover:bg-gold-500/10 transition-colors">
                  Apply This Scenario
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Recommendations */}
      {data.recommendations.length > 0 && (
        <div className="bg-background-secondary rounded-xl border border-border p-6">
          <h2 className="text-base font-semibold text-text-primary mb-4">Recommendations</h2>
          <ul className="space-y-3">
            {data.recommendations.map((rec, i) => (
              <li key={i} className="flex items-start gap-3">
                <CheckCircle className="w-4 h-4 text-gold-500 flex-shrink-0 mt-0.5" />
                <p className="text-sm text-text-secondary">{rec}</p>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Pay before statement close info */}
      <div className="bg-blue-500/5 border border-blue-500/20 rounded-xl p-5 flex gap-3">
        <Info className="w-5 h-5 text-blue-400 flex-shrink-0 mt-0.5" />
        <div>
          <p className="text-sm font-semibold text-blue-400 mb-1">Pay Before Your Statement Closes</p>
          <p className="text-xs text-text-muted">
            Credit bureaus receive your balance at the statement close date, not the due date.
            Paying down your balance before your statement closes each month can significantly lower your reported utilization
            and improve your credit score — even if you always pay on time.
          </p>
        </div>
      </div>
    </div>
  )
}
