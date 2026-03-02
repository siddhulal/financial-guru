'use client'

import { useState, useEffect } from 'react'
import {
  Briefcase, RefreshCw, Loader2, TrendingUp, TrendingDown,
  AlertTriangle, CheckCircle, Minus, Bot, Cpu, ArrowRight,
  MapPin, Clock, Code,
} from 'lucide-react'
import Link from 'next/link'
import { api } from '@/lib/api'
import { CareerAdviceResponse } from '@/lib/types'
import { formatCurrency } from '@/lib/utils'

function SalaryBar({ current, p25, p50, p75 }: { current: number; p25: number; p50: number; p75: number }) {
  const min = p25 * 0.85
  const max = p75 * 1.1
  const range = max - min
  const toPercent = (v: number) => Math.max(0, Math.min(100, ((v - min) / range) * 100))

  const p25pct = toPercent(p25)
  const p50pct = toPercent(p50)
  const p75pct = toPercent(p75)
  const curPct = toPercent(current)

  return (
    <div className="space-y-3">
      <div className="relative h-3 bg-background-tertiary rounded-full overflow-visible">
        {/* Market range band */}
        <div
          className="absolute h-full bg-blue-500/20 rounded-full"
          style={{ left: `${p25pct}%`, width: `${p75pct - p25pct}%` }}
        />
        {/* P25 marker */}
        <div className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 w-0.5 h-5 bg-text-muted/40" style={{ left: `${p25pct}%` }} />
        {/* P50 marker */}
        <div className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 w-0.5 h-5 bg-blue-400/60" style={{ left: `${p50pct}%` }} />
        {/* P75 marker */}
        <div className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 w-0.5 h-5 bg-text-muted/40" style={{ left: `${p75pct}%` }} />
        {/* Current salary marker */}
        <div
          className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 w-3.5 h-3.5 rounded-full bg-gold-500 border-2 border-background-primary shadow-gold-glow z-10"
          style={{ left: `${curPct}%` }}
        />
      </div>
      <div className="flex justify-between text-xs text-text-muted">
        <span>P25: {formatCurrency(p25)}</span>
        <span className="text-blue-400">Median: {formatCurrency(p50)}</span>
        <span>P75: {formatCurrency(p75)}</span>
      </div>
      {current > 0 && (
        <div className="text-center">
          <span className="text-xs text-text-muted">
            You: <span className="text-text-primary font-mono font-semibold">{formatCurrency(current)}</span>
          </span>
        </div>
      )}
    </div>
  )
}

function RecommendationBadge({ rec }: { rec: string }) {
  if (rec === 'RECOMMENDED') return (
    <div className="flex items-center gap-2 px-3 py-1.5 bg-amber-500/10 border border-amber-500/30 rounded-full">
      <AlertTriangle className="w-4 h-4 text-amber-400" />
      <span className="text-sm font-semibold text-amber-400">Job Change Recommended</span>
    </div>
  )
  if (rec === 'NOT_NOW') return (
    <div className="flex items-center gap-2 px-3 py-1.5 bg-green-500/10 border border-green-500/30 rounded-full">
      <CheckCircle className="w-4 h-4 text-green-400" />
      <span className="text-sm font-semibold text-green-400">Stay for Now</span>
    </div>
  )
  return (
    <div className="flex items-center gap-2 px-3 py-1.5 bg-blue-500/10 border border-blue-500/30 rounded-full">
      <Minus className="w-4 h-4 text-blue-400" />
      <span className="text-sm font-semibold text-blue-400">Neutral — Monitor Market</span>
    </div>
  )
}

export default function CareerAdvisorPage() {
  const [data, setData] = useState<CareerAdviceResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)

  useEffect(() => {
    api.career.advice().then(setData).finally(() => setLoading(false))
  }, [])

  const handleRefresh = async () => {
    setRefreshing(true)
    try {
      const updated = await api.career.refresh()
      setData(updated)
    } finally {
      setRefreshing(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 className="w-8 h-8 text-gold-500 animate-spin" />
      </div>
    )
  }

  if (!data?.profileComplete) {
    return (
      <div className="p-6 max-w-2xl mx-auto">
        <div className="bg-background-secondary border border-border rounded-xl p-8 text-center space-y-4">
          <Briefcase className="w-12 h-12 text-gold-500 mx-auto" />
          <h2 className="text-xl font-bold text-text-primary">Complete Your Life Profile</h2>
          <p className="text-text-muted">{data?.profileMessage ?? 'Add your job title, industry, location, and salary to unlock career intelligence.'}</p>
          <Link
            href="/profile"
            className="inline-flex items-center gap-2 px-5 py-2.5 bg-gold-500 text-black text-sm font-semibold rounded-lg hover:bg-gold-400 transition-colors"
          >
            Set Up Profile <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      </div>
    )
  }

  const gap = data.salaryGap ?? 0
  const isAboveMedian = gap < 0

  return (
    <div className="p-6 max-w-3xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Career Intelligence</h1>
          <div className="flex items-center gap-3 mt-1 text-sm text-text-muted flex-wrap">
            <span className="flex items-center gap-1">
              <Briefcase className="w-3.5 h-3.5" />
              {data.jobTitle}
            </span>
            {data.location && (
              <span className="flex items-center gap-1">
                <MapPin className="w-3.5 h-3.5" />
                {data.location}
              </span>
            )}
            <span className="flex items-center gap-1">
              <Clock className="w-3.5 h-3.5" />
              {data.industry}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-3">
          {/* AI source badge */}
          <div className="flex items-center gap-1.5 px-2.5 py-1 bg-background-tertiary border border-border rounded-full text-xs text-text-muted">
            {data.source === 'GEMINI' ? (
              <><Bot className="w-3.5 h-3.5 text-blue-400" /> Gemini</>
            ) : (
              <><Cpu className="w-3.5 h-3.5 text-gold-500" /> Local AI</>
            )}
          </div>
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            className="flex items-center gap-2 px-4 py-2 bg-background-secondary border border-border rounded-lg text-sm text-text-secondary hover:text-text-primary hover:border-gold-500/40 transition-colors"
          >
            <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
            Refresh Analysis
          </button>
        </div>
      </div>

      {/* Salary Benchmark */}
      <div className="bg-background-secondary border border-border rounded-xl p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-text-primary">Market Salary Range (2026)</h2>
          <span className="text-xs text-text-muted font-mono">{data.percentileLabel}</span>
        </div>
        <SalaryBar
          current={data.currentSalary ?? 0}
          p25={data.marketP25 ?? 0}
          p50={data.marketP50 ?? 0}
          p75={data.marketP75 ?? 0}
        />
        {gap !== 0 && (
          <div className={`flex items-center gap-2 text-sm font-medium ${isAboveMedian ? 'text-green-400' : 'text-amber-400'}`}>
            {isAboveMedian ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
            {isAboveMedian
              ? `You're ${formatCurrency(Math.abs(gap))} above market median`
              : `You're ${formatCurrency(Math.abs(gap))} below market median`}
          </div>
        )}
      </div>

      {/* Job Change Recommendation */}
      {data.jobChangeRecommendation && (
        <div className="bg-background-secondary border border-border rounded-xl p-6 space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-text-primary">Job Change Signal</h2>
            <RecommendationBadge rec={data.jobChangeRecommendation} />
          </div>
          {data.jobChangeReasoning && (
            <p className="text-sm text-text-secondary leading-relaxed">{data.jobChangeReasoning}</p>
          )}
        </div>
      )}

      {/* Skills to Develop */}
      {data.skillsToLearn && data.skillsToLearn.length > 0 && (
        <div className="bg-background-secondary border border-border rounded-xl p-6 space-y-3">
          <div className="flex items-center gap-2">
            <Code className="w-4 h-4 text-gold-500" />
            <h2 className="text-sm font-semibold text-text-primary">Skills to Develop</h2>
          </div>
          <div className="flex flex-wrap gap-2">
            {data.skillsToLearn.map(skill => (
              <span key={skill} className="px-3 py-1.5 bg-blue-500/10 text-blue-300 text-xs font-medium rounded-full border border-blue-500/20">
                {skill}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* AI Narrative */}
      {data.aiNarrative && (
        <div className="bg-background-secondary border border-border rounded-xl p-6 space-y-3">
          <div className="flex items-center gap-2">
            {data.source === 'GEMINI' ? (
              <Bot className="w-4 h-4 text-blue-400" />
            ) : (
              <Cpu className="w-4 h-4 text-gold-500" />
            )}
            <h2 className="text-sm font-semibold text-text-primary">
              AI Career Analysis
              <span className="ml-2 text-xs font-normal text-text-muted">
                ({data.source === 'GEMINI' ? 'Powered by Gemini' : 'Local AI'})
              </span>
            </h2>
          </div>
          <div className="prose prose-sm prose-invert max-w-none">
            {data.aiNarrative.split('\n\n').map((para, i) => (
              <p key={i} className="text-sm text-text-secondary leading-relaxed mb-3">{para}</p>
            ))}
          </div>
        </div>
      )}

      {/* Career Path */}
      {data.careerPathAdvice && (
        <div className="bg-gold-500/5 border border-gold-500/20 rounded-xl p-6 space-y-2">
          <h2 className="text-sm font-semibold text-gold-400">3-5 Year Career Path</h2>
          <p className="text-sm text-text-secondary leading-relaxed">{data.careerPathAdvice}</p>
        </div>
      )}
    </div>
  )
}
