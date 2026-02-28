'use client'

import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { SavingsGoal, GoalCategory } from '@/lib/types'
import { cn } from '@/lib/utils'
import { Plus, Pencil, Trash2, X, Check, TrendingUp, TrendingDown, Minus } from 'lucide-react'

const CATEGORY_CONFIG: Record<GoalCategory, { emoji: string; label: string }> = {
  EMERGENCY_FUND: { emoji: '🚨', label: 'Emergency Fund' },
  VACATION:       { emoji: '✈️', label: 'Vacation' },
  DOWN_PAYMENT:   { emoji: '🏠', label: 'Down Payment' },
  CAR:            { emoji: '🚗', label: 'Car' },
  RETIREMENT:     { emoji: '🎯', label: 'Retirement' },
  EDUCATION:      { emoji: '📚', label: 'Education' },
  OTHER:          { emoji: '💰', label: 'Other' },
}

const PRESET_COLORS = ['#F59E0B', '#3B82F6', '#10B981', '#8B5CF6', '#EF4444', '#F97316']

function CircularProgress({ percent, color }: { percent: number; color: string }) {
  const radius = 40
  const circumference = 2 * Math.PI * radius
  const strokeDashoffset = circumference - (Math.min(percent, 100) / 100) * circumference

  return (
    <svg width="100" height="100" viewBox="0 0 100 100">
      {/* Background circle */}
      <circle
        cx="50" cy="50" r={radius}
        fill="none"
        stroke="#1A1A24"
        strokeWidth="8"
      />
      {/* Progress circle */}
      <circle
        cx="50" cy="50" r={radius}
        fill="none"
        stroke={color || '#F59E0B'}
        strokeWidth="8"
        strokeLinecap="round"
        strokeDasharray={circumference}
        strokeDashoffset={strokeDashoffset}
        transform="rotate(-90 50 50)"
        style={{ transition: 'stroke-dashoffset 0.5s ease' }}
      />
      {/* Percent text */}
      <text
        x="50" y="50"
        textAnchor="middle"
        dominantBaseline="central"
        fill="white"
        fontSize="16"
        fontWeight="bold"
        fontFamily="monospace"
      >
        {Math.round(percent)}%
      </text>
    </svg>
  )
}

function formatMonth(dateStr: string | null): string {
  if (!dateStr) return '—'
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', { month: 'short', year: 'numeric' })
}

interface GoalFormData {
  name: string
  category: GoalCategory
  targetAmount: string
  currentAmount: string
  targetDate: string
  color: string
  notes: string
}

const defaultForm: GoalFormData = {
  name: '',
  category: 'OTHER',
  targetAmount: '',
  currentAmount: '',
  targetDate: '',
  color: '#F59E0B',
  notes: '',
}

export default function GoalsPage() {
  const [goals, setGoals] = useState<SavingsGoal[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showAddModal, setShowAddModal] = useState(false)
  const [editingGoal, setEditingGoal] = useState<SavingsGoal | null>(null)
  const [form, setForm] = useState<GoalFormData>(defaultForm)
  const [saving, setSaving] = useState(false)
  const [progressInputs, setProgressInputs] = useState<Record<string, string>>({})
  const [progressOpen, setProgressOpen] = useState<Record<string, boolean>>({})
  const [addingProgress, setAddingProgress] = useState<Record<string, boolean>>({})

  useEffect(() => {
    loadGoals()
  }, [])

  async function loadGoals() {
    setLoading(true)
    try {
      const data = await api.goals.list()
      setGoals(data)
    } catch (e) {
      setError('Failed to load goals')
    } finally {
      setLoading(false)
    }
  }

  function openAdd() {
    setForm(defaultForm)
    setEditingGoal(null)
    setShowAddModal(true)
  }

  function openEdit(goal: SavingsGoal) {
    setForm({
      name: goal.name,
      category: goal.category,
      targetAmount: String(goal.targetAmount),
      currentAmount: String(goal.currentAmount),
      targetDate: goal.targetDate ? goal.targetDate.slice(0, 10) : '',
      color: goal.color || '#F59E0B',
      notes: goal.notes || '',
    })
    setEditingGoal(goal)
    setShowAddModal(true)
  }

  async function handleSave() {
    if (!form.name.trim()) return
    const target = parseFloat(form.targetAmount)
    if (isNaN(target) || target <= 0) return
    const current = parseFloat(form.currentAmount) || 0

    setSaving(true)
    try {
      const payload: Partial<SavingsGoal> = {
        name: form.name.trim(),
        category: form.category,
        targetAmount: target,
        currentAmount: current,
        targetDate: form.targetDate || null,
        color: form.color,
        notes: form.notes || null,
      }
      if (editingGoal) {
        const updated = await api.goals.update(editingGoal.id, payload)
        setGoals(prev => prev.map(g => g.id === updated.id ? updated : g))
      } else {
        const created = await api.goals.create(payload)
        setGoals(prev => [...prev, created])
      }
      setShowAddModal(false)
    } catch {
      // ignore
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this goal?')) return
    try {
      await api.goals.delete(id)
      setGoals(prev => prev.filter(g => g.id !== id))
    } catch {
      // ignore
    }
  }

  async function handleAddProgress(goalId: string) {
    const amount = parseFloat(progressInputs[goalId] || '0')
    if (isNaN(amount) || amount === 0) return
    setAddingProgress(prev => ({ ...prev, [goalId]: true }))
    try {
      const updated = await api.goals.addProgress(goalId, amount)
      setGoals(prev => prev.map(g => g.id === updated.id ? updated : g))
      setProgressInputs(prev => ({ ...prev, [goalId]: '' }))
      setProgressOpen(prev => ({ ...prev, [goalId]: false }))
    } catch {
      // ignore
    } finally {
      setAddingProgress(prev => ({ ...prev, [goalId]: false }))
    }
  }

  const totalSaved = goals.reduce((s, g) => s + g.currentAmount, 0)
  const totalTarget = goals.reduce((s, g) => s + g.targetAmount, 0)

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-gold-500" />
      </div>
    )
  }
  if (error) return <div className="p-6 text-red-400">{error}</div>

  return (
    <div className="space-y-6 animate-slide-up">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Savings Goals</h1>
          {goals.length > 0 && (
            <p className="text-text-muted text-sm mt-1">
              {goals.length} {goals.length === 1 ? 'goal' : 'goals'} ·{' '}
              <span className="text-gold-500 font-mono">${totalSaved.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
              {' '}saved toward{' '}
              <span className="font-mono">${totalTarget.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
              {' '}total
            </p>
          )}
        </div>
        <button
          onClick={openAdd}
          className="flex items-center gap-2 px-4 py-2 bg-gold-500 text-black font-semibold text-sm rounded-lg hover:bg-gold-400 transition-colors"
        >
          <Plus className="w-4 h-4" />
          Add Goal
        </button>
      </div>

      {/* Empty state */}
      {goals.length === 0 && (
        <div className="bg-background-secondary rounded-xl border border-border p-16 text-center">
          <p className="text-4xl mb-4">🎯</p>
          <h3 className="text-lg font-semibold text-text-primary mb-2">No goals yet</h3>
          <p className="text-text-muted text-sm mb-6">Set your first financial goal and start tracking your progress.</p>
          <button
            onClick={openAdd}
            className="px-6 py-2.5 bg-gold-500 text-black font-semibold text-sm rounded-lg hover:bg-gold-400 transition-colors"
          >
            Create Your First Goal
          </button>
        </div>
      )}

      {/* Goals grid */}
      {goals.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">
          {goals.map(goal => {
            const cfg = CATEGORY_CONFIG[goal.category] || CATEGORY_CONFIG.OTHER
            const color = goal.color || '#F59E0B'
            const progressPct = Math.min(goal.percentComplete, 100)
            const isOverdue = goal.targetDate && new Date(goal.targetDate) < new Date() && progressPct < 100

            const progressBarColor = isOverdue
              ? 'bg-red-500'
              : goal.isOnTrack
              ? 'bg-green-500'
              : 'bg-amber-500'

            return (
              <div key={goal.id} className="bg-background-secondary rounded-xl border border-border p-6 flex flex-col gap-4">
                {/* Top row */}
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <CircularProgress percent={progressPct} color={color} />
                    <div>
                      <h3 className="font-semibold text-text-primary leading-tight">{goal.name}</h3>
                      <span
                        className="inline-flex items-center gap-1 mt-1 px-2 py-0.5 rounded-full text-xs font-medium"
                        style={{ backgroundColor: color + '20', color }}
                      >
                        {cfg.emoji} {cfg.label}
                      </span>
                    </div>
                  </div>
                  <div className="flex items-center gap-1 flex-shrink-0">
                    <button onClick={() => openEdit(goal)} className="p-1.5 rounded hover:bg-background-tertiary text-text-muted hover:text-text-primary transition-colors">
                      <Pencil className="w-3.5 h-3.5" />
                    </button>
                    <button onClick={() => handleDelete(goal.id)} className="p-1.5 rounded hover:bg-background-tertiary text-text-muted hover:text-red-400 transition-colors">
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>

                {/* Amount */}
                <div>
                  <div className="flex items-end justify-between mb-1.5">
                    <span className="text-xs text-text-muted">Progress</span>
                    <span className="text-xs text-text-muted font-mono">
                      <span className="text-text-primary font-semibold">${goal.currentAmount.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}</span>
                      {' / '}
                      ${goal.targetAmount.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}
                    </span>
                  </div>
                  <div className="h-2 bg-background-tertiary rounded-full overflow-hidden">
                    <div
                      className={cn('h-full rounded-full transition-all', progressBarColor)}
                      style={{ width: `${progressPct}%` }}
                    />
                  </div>
                </div>

                {/* Stats row */}
                <div className="grid grid-cols-2 gap-3 text-xs">
                  <div>
                    <p className="text-text-muted">Monthly Required</p>
                    <p className="text-gold-500 font-mono font-semibold">
                      {goal.monthlyRequired > 0
                        ? `$${goal.monthlyRequired.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
                        : '—'}
                    </p>
                  </div>
                  <div>
                    <p className="text-text-muted">
                      {goal.targetDate ? 'Target Date' : 'Projected'}
                    </p>
                    <p className="text-text-primary font-mono">
                      {goal.targetDate ? formatMonth(goal.targetDate) : formatMonth(goal.projectedDate)}
                    </p>
                  </div>
                </div>

                {/* On track badge */}
                <div className="flex items-center justify-between">
                  <div className={cn(
                    'flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium',
                    isOverdue
                      ? 'bg-red-500/10 text-red-400'
                      : goal.isOnTrack
                      ? 'bg-green-500/10 text-green-400'
                      : 'bg-amber-500/10 text-amber-400'
                  )}>
                    {isOverdue ? (
                      <><TrendingDown className="w-3 h-3" /> Overdue</>
                    ) : goal.isOnTrack ? (
                      <><Check className="w-3 h-3" /> On track</>
                    ) : (
                      <><Minus className="w-3 h-3" /> Off track</>
                    )}
                  </div>
                  {goal.monthsRemaining > 0 && (
                    <span className="text-xs text-text-muted">{goal.monthsRemaining} mo left</span>
                  )}
                </div>

                {/* Add progress */}
                {progressOpen[goal.id] ? (
                  <div className="flex items-center gap-2 pt-1 border-t border-border">
                    <span className="text-xs text-text-muted">$</span>
                    <input
                      type="number"
                      value={progressInputs[goal.id] || ''}
                      onChange={e => setProgressInputs(prev => ({ ...prev, [goal.id]: e.target.value }))}
                      onKeyDown={e => {
                        if (e.key === 'Enter') handleAddProgress(goal.id)
                        if (e.key === 'Escape') setProgressOpen(prev => ({ ...prev, [goal.id]: false }))
                      }}
                      placeholder="Amount"
                      className="flex-1 bg-background-tertiary border border-border rounded px-2 py-1 text-sm text-text-primary outline-none focus:border-gold-500"
                      autoFocus
                    />
                    <button
                      onClick={() => handleAddProgress(goal.id)}
                      disabled={addingProgress[goal.id]}
                      className="px-3 py-1 bg-gold-500 text-black text-xs font-semibold rounded hover:bg-gold-400 disabled:opacity-50 transition-colors"
                    >
                      {addingProgress[goal.id] ? '...' : 'Add'}
                    </button>
                    <button
                      onClick={() => setProgressOpen(prev => ({ ...prev, [goal.id]: false }))}
                      className="p-1 text-text-muted hover:text-text-primary transition-colors"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ) : (
                  <button
                    onClick={() => setProgressOpen(prev => ({ ...prev, [goal.id]: true }))}
                    className="w-full py-1.5 text-xs font-medium text-text-muted border border-border rounded-lg hover:border-gold-500/40 hover:text-gold-500 transition-colors"
                  >
                    + Add Progress
                  </button>
                )}

                {/* Notes */}
                {goal.notes && (
                  <p className="text-xs text-text-muted italic border-t border-border pt-2">{goal.notes}</p>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Add / Edit Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setShowAddModal(false)} />
          <div className="relative w-full max-w-md bg-background-secondary border border-border rounded-2xl shadow-2xl p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-text-primary">
                {editingGoal ? 'Edit Goal' : 'New Savings Goal'}
              </h2>
              <button onClick={() => setShowAddModal(false)} className="text-text-muted hover:text-text-primary transition-colors">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3">
              {/* Name */}
              <div>
                <label className="block text-xs font-medium text-text-muted mb-1">Goal Name *</label>
                <input
                  type="text"
                  value={form.name}
                  onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                  placeholder="e.g. Europe Trip"
                  className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
                />
              </div>

              {/* Category */}
              <div>
                <label className="block text-xs font-medium text-text-muted mb-1">Category *</label>
                <select
                  value={form.category}
                  onChange={e => setForm(f => ({ ...f, category: e.target.value as GoalCategory }))}
                  className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
                >
                  {Object.entries(CATEGORY_CONFIG).map(([key, { emoji, label }]) => (
                    <option key={key} value={key}>{emoji} {label}</option>
                  ))}
                </select>
              </div>

              {/* Target amount */}
              <div>
                <label className="block text-xs font-medium text-text-muted mb-1">Target Amount *</label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted text-sm">$</span>
                  <input
                    type="number"
                    min="1"
                    step="0.01"
                    value={form.targetAmount}
                    onChange={e => setForm(f => ({ ...f, targetAmount: e.target.value }))}
                    placeholder="5000"
                    className="w-full bg-background-tertiary border border-border rounded-lg pl-7 pr-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
                  />
                </div>
              </div>

              {/* Current amount */}
              <div>
                <label className="block text-xs font-medium text-text-muted mb-1">Current Amount (optional)</label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted text-sm">$</span>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.currentAmount}
                    onChange={e => setForm(f => ({ ...f, currentAmount: e.target.value }))}
                    placeholder="0"
                    className="w-full bg-background-tertiary border border-border rounded-lg pl-7 pr-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
                  />
                </div>
              </div>

              {/* Target date */}
              <div>
                <label className="block text-xs font-medium text-text-muted mb-1">Target Date (optional)</label>
                <input
                  type="date"
                  value={form.targetDate}
                  onChange={e => setForm(f => ({ ...f, targetDate: e.target.value }))}
                  className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
                />
              </div>

              {/* Color */}
              <div>
                <label className="block text-xs font-medium text-text-muted mb-2">Color</label>
                <div className="flex gap-2">
                  {PRESET_COLORS.map(c => (
                    <button
                      key={c}
                      onClick={() => setForm(f => ({ ...f, color: c }))}
                      className={cn(
                        'w-8 h-8 rounded-full transition-all',
                        form.color === c ? 'ring-2 ring-white ring-offset-2 ring-offset-background-secondary scale-110' : 'hover:scale-105'
                      )}
                      style={{ backgroundColor: c }}
                    />
                  ))}
                </div>
              </div>

              {/* Notes */}
              <div>
                <label className="block text-xs font-medium text-text-muted mb-1">Notes (optional)</label>
                <textarea
                  value={form.notes}
                  onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
                  placeholder="Any additional notes..."
                  rows={2}
                  className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors resize-none"
                />
              </div>
            </div>

            <div className="flex gap-3 pt-2">
              <button
                onClick={() => setShowAddModal(false)}
                className="flex-1 py-2 border border-border rounded-lg text-sm text-text-secondary hover:bg-background-tertiary transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSave}
                disabled={saving || !form.name.trim() || !form.targetAmount}
                className="flex-1 py-2 bg-gold-500 text-black font-semibold text-sm rounded-lg hover:bg-gold-400 disabled:opacity-50 transition-colors"
              >
                {saving ? 'Saving...' : editingGoal ? 'Save Changes' : 'Create Goal'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
