'use client'

import { useState, useEffect } from 'react'
import { X, Loader2, Lightbulb, RefreshCw } from 'lucide-react'
import { api } from '@/lib/api'
import { Insight, InsightSeverity } from '@/lib/types'
import { cn } from '@/lib/utils'

function formatCurrency(n: number) {
  return '$' + n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function severityLabel(s: InsightSeverity) {
  return s.charAt(0) + s.slice(1).toLowerCase()
}

function severityClasses(s: InsightSeverity): { badge: string; border: string } {
  switch (s) {
    case 'INFO':
      return { badge: 'bg-blue-500/20 text-blue-400 border border-blue-500/30', border: 'border-blue-500/20' }
    case 'WARNING':
      return { badge: 'bg-gold-500/20 text-gold-400 border border-gold-500/30', border: 'border-gold-500/20' }
    case 'OPPORTUNITY':
      return { badge: 'bg-success/20 text-success border border-success/30', border: 'border-success/20' }
    case 'CRITICAL':
      return { badge: 'bg-error/20 text-error border border-error/30', border: 'border-error/20' }
  }
}

type Tab = 'ALL' | 'SAVINGS' | 'WARNINGS' | 'OPPORTUNITIES'

const TABS: Tab[] = ['ALL', 'SAVINGS', 'WARNINGS', 'OPPORTUNITIES']

function filterBySeverity(insights: Insight[], tab: Tab): Insight[] {
  switch (tab) {
    case 'ALL': return insights
    case 'SAVINGS': return insights.filter(i => i.severity === 'INFO')
    case 'WARNINGS': return insights.filter(i => i.severity === 'WARNING' || i.severity === 'CRITICAL')
    case 'OPPORTUNITIES': return insights.filter(i => i.severity === 'OPPORTUNITY')
  }
}

export default function InsightsPage() {
  const [insights, setInsights] = useState<Insight[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [running, setRunning] = useState(false)
  const [tab, setTab] = useState<Tab>('ALL')

  useEffect(() => {
    api.insights.list()
      .then(data => setInsights(data.filter(i => !i.isDismissed)))
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  async function handleRunEngine() {
    setRunning(true)
    try {
      await api.insights.runEngine()
      const fresh = await api.insights.list()
      setInsights(fresh.filter(i => !i.isDismissed))
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : 'Failed to run insights engine')
    } finally {
      setRunning(false)
    }
  }

  async function handleDismiss(id: string) {
    try {
      await api.insights.dismiss(id)
      setInsights(prev => prev.filter(i => i.id !== id))
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : 'Failed to dismiss insight')
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-gold-500" />
      </div>
    )
  }

  if (error) {
    return <div className="p-6 text-error">{error}</div>
  }

  const visible = filterBySeverity(insights, tab)
  const newCount = insights.filter(i => i.severity === 'CRITICAL' || i.severity === 'WARNING').length

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold text-text-primary">Insights Hub</h1>
          {newCount > 0 && (
            <span className="px-2.5 py-0.5 bg-error/20 text-error text-xs font-bold rounded-full border border-error/30">
              {newCount} new
            </span>
          )}
        </div>
        <button
          onClick={handleRunEngine}
          disabled={running}
          className="flex items-center gap-2 px-4 py-2 bg-gold-500 hover:bg-gold-600 text-black font-semibold rounded-lg transition-colors text-sm disabled:opacity-60"
        >
          {running ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : (
            <RefreshCw className="w-4 h-4" />
          )}
          {running ? 'Running...' : 'Run Insights Now'}
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-background-secondary rounded-lg border border-border p-1 w-fit">
        {TABS.map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={cn(
              'px-4 py-1.5 rounded-md text-sm font-medium transition-all',
              tab === t
                ? 'bg-gold-500/20 text-gold-400 border border-gold-500/30'
                : 'text-text-muted hover:text-text-secondary'
            )}
          >
            {t.charAt(0) + t.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {/* Insights grid */}
      {visible.length === 0 ? (
        <div className="bg-background-secondary rounded-xl border border-border p-16 text-center">
          <Lightbulb className="w-12 h-12 text-text-muted mx-auto mb-4" />
          <p className="text-text-secondary text-lg mb-1">No insights yet</p>
          <p className="text-text-muted text-sm">Click "Run Insights Now" to analyze your spending.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {visible.map(insight => {
            const styles = severityClasses(insight.severity)
            return (
              <div
                key={insight.id}
                className={cn(
                  'bg-background-secondary rounded-xl border p-5 space-y-3 relative',
                  styles.border
                )}
              >
                <button
                  onClick={() => handleDismiss(insight.id)}
                  className="absolute top-4 right-4 text-text-muted hover:text-text-primary transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>

                <div className="pr-6">
                  <span className={cn('inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold', styles.badge)}>
                    {severityLabel(insight.severity)}
                  </span>
                </div>

                <div>
                  <h3 className="font-bold text-text-primary text-sm leading-snug">{insight.title}</h3>
                  <p className="text-text-secondary text-sm mt-1 leading-relaxed">{insight.description}</p>
                </div>

                {insight.impactAmount !== null && (
                  <div className="text-sm font-mono font-semibold text-gold-500">
                    Annual impact: {formatCurrency(insight.impactAmount)}
                  </div>
                )}

                {insight.actionText && (
                  <p className="text-xs text-text-muted italic">{insight.actionText}</p>
                )}

                <div className="text-xs text-text-muted pt-1">
                  {new Date(insight.generatedAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
