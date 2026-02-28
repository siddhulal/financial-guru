'use client'

import { useState, useEffect } from 'react'
import { api } from '@/lib/api'
import { HealthScoreResponse, ScorePillar } from '@/lib/types'
import { cn } from '@/lib/utils'

function gradeColor(grade: string) {
  switch (grade) {
    case 'A': return 'text-success'
    case 'B': return 'text-blue-400'
    case 'C': return 'text-gold-500'
    case 'D': return 'text-orange-500'
    default: return 'text-error'
  }
}

function scoreColor(score: number, max: number) {
  const pct = max > 0 ? (score / max) * 100 : 0
  if (pct >= 70) return { bar: 'bg-success', text: 'text-success' }
  if (pct >= 40) return { bar: 'bg-gold-500', text: 'text-gold-500' }
  return { bar: 'bg-error', text: 'text-error' }
}

function totalScoreColor(score: number) {
  if (score >= 70) return 'text-success'
  if (score >= 50) return 'text-gold-500'
  return 'text-error'
}

function totalScoreBarColor(score: number) {
  if (score >= 70) return '#22C55E'
  if (score >= 50) return '#F59E0B'
  return '#EF4444'
}

function PillarCard({ pillar }: { pillar: ScorePillar }) {
  const colors = scoreColor(pillar.score, pillar.maxScore)
  const pct = pillar.maxScore > 0 ? (pillar.score / pillar.maxScore) * 100 : 0
  return (
    <div className="bg-background-secondary rounded-xl border border-border p-4 space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold text-text-primary text-sm">{pillar.name}</h3>
        <span className={cn('font-mono font-bold text-sm', colors.text)}>
          {pillar.score} / {pillar.maxScore}
        </span>
      </div>
      <div className="w-full bg-background-tertiary rounded-full h-2">
        <div
          className={cn('h-2 rounded-full transition-all duration-700', colors.bar)}
          style={{ width: `${pct}%` }}
        />
      </div>
      <p className="text-xs text-text-muted leading-relaxed">{pillar.explanation}</p>
    </div>
  )
}

export default function HealthScorePage() {
  const [data, setData] = useState<HealthScoreResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.insights.healthScore()
      .then(setData)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-gold-500" />
      </div>
    )
  }

  if (error) return <div className="p-6 text-error">{error}</div>
  if (!data) return null

  const circumference = 2 * Math.PI * 54
  const strokeDash = (data.totalScore / 100) * circumference
  const strokeColor = totalScoreBarColor(data.totalScore)

  const efPct = data.emergencyFundTarget > 0
    ? Math.min((data.emergencyFundMonths / data.emergencyFundTarget) * 100, 100)
    : 0
  const efColor = efPct >= 100 ? 'bg-success' : efPct >= 50 ? 'bg-gold-500' : 'bg-error'
  const efTextColor = efPct >= 100 ? 'text-success' : efPct >= 50 ? 'text-gold-500' : 'text-error'

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-text-primary">Financial Health Score</h1>
        <p className="text-text-secondary text-sm mt-1">A holistic view of your financial well-being</p>
      </div>

      {/* Score circle + stats row */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Circle */}
        <div className="bg-background-secondary rounded-xl border border-border p-6 flex flex-col items-center justify-center gap-2">
          <svg width="140" height="140" viewBox="0 0 140 140">
            <circle cx="70" cy="70" r="54" fill="none" stroke="#2D2D3F" strokeWidth="12" />
            <circle
              cx="70"
              cy="70"
              r="54"
              fill="none"
              stroke={strokeColor}
              strokeWidth="12"
              strokeLinecap="round"
              strokeDasharray={`${strokeDash} ${circumference}`}
              strokeDashoffset={circumference * 0.25}
              transform="rotate(-90 70 70)"
              style={{ transition: 'stroke-dasharray 1s ease' }}
            />
            <text x="70" y="65" textAnchor="middle" className="font-mono" fill={strokeColor} fontSize="28" fontWeight="bold">
              {data.totalScore}
            </text>
            <text x="70" y="82" textAnchor="middle" fill="#9CA3AF" fontSize="11">
              out of 100
            </text>
          </svg>
          <div className={cn('text-4xl font-bold font-mono', gradeColor(data.grade))}>
            {data.grade}
          </div>
          <p className="text-xs text-text-muted">Overall Grade</p>
        </div>

        {/* Stats */}
        <div className="md:col-span-2 grid grid-cols-2 gap-4">
          <div className="bg-background-secondary rounded-xl border border-border p-4 space-y-1">
            <p className="text-xs text-text-muted">Credit Utilization</p>
            <p className={cn(
              'font-mono font-bold text-2xl',
              data.utilizationPercent <= 30 ? 'text-success' : data.utilizationPercent <= 50 ? 'text-gold-500' : 'text-error'
            )}>
              {data.utilizationPercent.toFixed(1)}%
            </p>
            <p className="text-xs text-text-muted">
              {data.utilizationPercent <= 30 ? 'Excellent' : data.utilizationPercent <= 50 ? 'Fair' : 'High — aim for under 30%'}
            </p>
          </div>

          <div className="bg-background-secondary rounded-xl border border-border p-4 space-y-1">
            <p className="text-xs text-text-muted">Savings Rate</p>
            <p className={cn(
              'font-mono font-bold text-2xl',
              data.savingsRate >= 20 ? 'text-success' : data.savingsRate >= 10 ? 'text-gold-500' : 'text-error'
            )}>
              {data.savingsRate.toFixed(1)}%
            </p>
            <p className="text-xs text-text-muted">
              {data.savingsRate >= 20 ? 'Excellent' : data.savingsRate >= 10 ? 'Good — target 20%' : 'Low — target 20%+'}
            </p>
          </div>

          {/* Emergency fund gauge */}
          <div className="col-span-2 bg-background-secondary rounded-xl border border-border p-4 space-y-3">
            <div className="flex items-center justify-between">
              <p className="text-sm font-semibold text-text-primary">Emergency Fund</p>
              <p className="text-xs text-text-muted">Target: {data.emergencyFundTarget} months</p>
            </div>
            <div className="w-full bg-background-tertiary rounded-full h-3">
              <div
                className={cn('h-3 rounded-full transition-all duration-700', efColor)}
                style={{ width: `${efPct}%` }}
              />
            </div>
            <div className="flex items-center justify-between">
              <span className={cn('font-mono font-bold', efTextColor)}>
                {data.emergencyFundMonths.toFixed(1)} months covered
              </span>
              <span className="text-xs text-text-muted">
                {efPct >= 100 ? 'Fully funded!' : `${efPct.toFixed(0)}% of target`}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Pillars */}
      <div>
        <h2 className="font-bold text-text-primary mb-3">Score Breakdown</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {data.pillars.map(pillar => (
            <PillarCard key={pillar.name} pillar={pillar} />
          ))}
        </div>
      </div>
    </div>
  )
}
