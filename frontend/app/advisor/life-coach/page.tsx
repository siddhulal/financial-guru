'use client'

import { useState, useEffect } from 'react'
import {
  Compass, Loader2, Sparkles, Bot, Cpu, X, ChevronDown,
  Briefcase, DollarSign, Heart, TrendingUp, Calendar,
} from 'lucide-react'
import { api } from '@/lib/api'
import { LifeGuidanceResponse, LifeGuidance } from '@/lib/types'

function GuidanceCard({ guidance, onDismiss }: { guidance: LifeGuidance; onDismiss?: () => void }) {
  const [open, setOpen] = useState(false)
  let actionItems: string[] = []
  if (guidance.actionItems) {
    try {
      actionItems = JSON.parse(guidance.actionItems)
    } catch {
      actionItems = []
    }
  }

  return (
    <div className="bg-background-secondary border border-border rounded-xl overflow-hidden">
      <div className="p-5 space-y-3">
        {/* Header */}
        <div className="flex items-start justify-between gap-2">
          <div className="space-y-1 flex-1">
            <div className="flex items-center gap-2">
              <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${
                guidance.source === 'GEMINI' ? 'bg-blue-500/10 text-blue-300 border border-blue-500/20' :
                guidance.source === 'OLLAMA' ? 'bg-gold-500/10 text-gold-400 border border-gold-500/20' :
                'bg-background-tertiary text-text-muted border border-border'
              }`}>
                {guidance.source === 'GEMINI' ? <span className="flex items-center gap-1"><Bot className="w-3 h-3 inline" /> Gemini</span> :
                 guidance.source === 'OLLAMA' ? <span className="flex items-center gap-1"><Cpu className="w-3 h-3 inline" /> Local AI</span> :
                 'Rule-based'}
              </span>
              <span className="text-xs text-text-muted">
                {new Date(guidance.generatedAt).toLocaleDateString('en-US', { month: 'short', year: 'numeric' })}
              </span>
            </div>
            <h3 className="text-sm font-semibold text-text-primary">{guidance.title}</h3>
          </div>
          {onDismiss && (
            <button onClick={onDismiss} className="text-text-muted hover:text-text-primary transition-colors mt-0.5">
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        {/* Preview (first paragraph) */}
        <p className="text-sm text-text-secondary leading-relaxed line-clamp-3">
          {guidance.content.split('\n\n')[0].replace(/^#+\s*/, '')}
        </p>

        {/* Action Items */}
        {actionItems.length > 0 && (
          <div className="space-y-1.5 pt-1">
            <p className="text-xs font-medium text-text-muted uppercase tracking-wider">Action Items</p>
            {actionItems.slice(0, 5).map((item, i) => (
              <div key={i} className="flex items-start gap-2">
                <span className="w-5 h-5 flex-shrink-0 rounded-full bg-gold-500/10 text-gold-400 text-xs flex items-center justify-center font-mono font-bold">
                  {i + 1}
                </span>
                <span className="text-xs text-text-secondary leading-relaxed">{item}</span>
              </div>
            ))}
          </div>
        )}

        {/* Expand full */}
        <button
          onClick={() => setOpen(o => !o)}
          className="flex items-center gap-1 text-xs text-text-muted hover:text-gold-400 transition-colors"
        >
          <ChevronDown className={`w-3.5 h-3.5 transition-transform ${open ? 'rotate-180' : ''}`} />
          {open ? 'Show less' : 'Read full guidance'}
        </button>

        {open && (
          <div className="pt-2 border-t border-border">
            <div className="prose prose-sm prose-invert max-w-none space-y-2">
              {guidance.content.split('\n\n').map((para, i) => (
                <p key={i} className="text-sm text-text-secondary leading-relaxed">{para.replace(/^#+\s*/, '')}</p>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function QuadrantGrid({ guidance }: { guidance: LifeGuidance }) {
  const quadrants = [
    { icon: Briefcase, label: 'Career', color: 'text-blue-400', bg: 'bg-blue-500/5 border-blue-500/20' },
    { icon: DollarSign, label: 'Financial', color: 'text-green-400', bg: 'bg-green-500/5 border-green-500/20' },
    { icon: Heart, label: 'Family', color: 'text-rose-400', bg: 'bg-rose-500/5 border-rose-500/20' },
    { icon: TrendingUp, label: 'Growth', color: 'text-purple-400', bg: 'bg-purple-500/5 border-purple-500/20' },
  ]

  // Extract sections from content by looking for keywords
  const content = guidance.content
  const paragraphs = content.split('\n\n').filter(p => p.trim())

  const getSection = (keyword: string) => {
    const para = paragraphs.find(p =>
      p.toLowerCase().includes(keyword.toLowerCase()) &&
      (p.startsWith('**') || p.startsWith('#') || p.toLowerCase().startsWith(keyword.toLowerCase()))
    )
    if (para) return para.replace(/^\*\*[^*]+\*\*:?\s*/, '').replace(/^#+\s*[^\n]+\n/, '').trim()
    return paragraphs[quadrants.findIndex(q => q.label === keyword)] ?? paragraphs[0]
  }

  const sections = ['Career', 'Financial', 'Family', 'Growth'].map(k => getSection(k))

  return (
    <div className="grid grid-cols-2 gap-3">
      {quadrants.map(({ icon: Icon, label, color, bg }, i) => (
        <div key={label} className={`rounded-xl border p-4 space-y-2 ${bg}`}>
          <div className="flex items-center gap-2">
            <Icon className={`w-4 h-4 ${color}`} />
            <span className={`text-xs font-semibold ${color}`}>{label}</span>
          </div>
          <p className="text-xs text-text-secondary leading-relaxed line-clamp-4">
            {sections[i] ?? '—'}
          </p>
        </div>
      ))}
    </div>
  )
}

export default function LifeCoachPage() {
  const [data, setData] = useState<LifeGuidanceResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)

  const load = () => {
    setLoading(true)
    api.lifeCoach.get().then(setData).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const handleGenerate = async () => {
    setGenerating(true)
    try {
      await api.lifeCoach.generate()
      load()
    } finally {
      setGenerating(false)
    }
  }

  const handleDismiss = async (id: string) => {
    await api.lifeCoach.dismiss(id)
    load()
  }

  const now = new Date()
  const monthLabel = now.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 className="w-8 h-8 text-gold-500 animate-spin" />
      </div>
    )
  }

  return (
    <div className="p-6 max-w-3xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Life Coach</h1>
          <div className="flex items-center gap-2 mt-1">
            <Calendar className="w-3.5 h-3.5 text-text-muted" />
            <span className="text-sm text-text-muted">{monthLabel}</span>
            {data?.geminiAvailable ? (
              <span className="flex items-center gap-1 text-xs text-blue-300 bg-blue-500/10 border border-blue-500/20 px-2 py-0.5 rounded-full">
                <Bot className="w-3 h-3" /> Gemini powered
              </span>
            ) : (
              <span className="flex items-center gap-1 text-xs text-gold-400 bg-gold-500/10 border border-gold-500/20 px-2 py-0.5 rounded-full">
                <Cpu className="w-3 h-3" /> Local AI
              </span>
            )}
          </div>
        </div>
        <button
          onClick={handleGenerate}
          disabled={generating}
          className="flex items-center gap-2 px-4 py-2.5 bg-gold-500 text-black text-sm font-semibold rounded-lg hover:bg-gold-400 transition-colors disabled:opacity-50"
        >
          {generating ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4" />}
          {generating ? 'Generating...' : 'Generate New Guidance'}
        </button>
      </div>

      {/* This Month's Guidance */}
      {data?.thisMonth ? (
        <div className="space-y-4">
          <h2 className="text-sm font-semibold text-text-muted uppercase tracking-wider">This Month</h2>

          {/* Quadrant overview */}
          <QuadrantGrid guidance={data.thisMonth} />

          {/* Full guidance card */}
          <GuidanceCard
            guidance={data.thisMonth}
            onDismiss={() => handleDismiss(data.thisMonth!.id)}
          />
        </div>
      ) : (
        <div className="bg-background-secondary border border-border rounded-xl p-8 text-center space-y-3">
          <Compass className="w-10 h-10 text-gold-500 mx-auto" />
          <h2 className="text-base font-semibold text-text-primary">No Guidance Yet</h2>
          <p className="text-sm text-text-muted">
            Click &ldquo;Generate New Guidance&rdquo; to get personalized life coaching for {monthLabel}.
          </p>
        </div>
      )}

      {/* Career Items */}
      {data?.careerItems && data.careerItems.length > 0 && (
        <div className="space-y-3">
          <h2 className="text-sm font-semibold text-text-muted uppercase tracking-wider">Career Guidance</h2>
          {data.careerItems.map(item => (
            <GuidanceCard
              key={item.id}
              guidance={item}
              onDismiss={() => handleDismiss(item.id)}
            />
          ))}
        </div>
      )}

      {/* History */}
      {data?.history && data.history.length > 1 && (
        <div className="space-y-3">
          <h2 className="text-sm font-semibold text-text-muted uppercase tracking-wider">Past Guidance</h2>
          <div className="space-y-2">
            {data.history.slice(1).map(item => (
              <GuidanceCard key={item.id} guidance={item} />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
