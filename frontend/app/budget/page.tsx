'use client'

import { useState, useEffect } from 'react'
import { Trash2, Plus, X, Loader2, TrendingUp, AlertTriangle, CheckCircle, Target } from 'lucide-react'
import { api } from '@/lib/api'
import { Budget, BudgetStatus, FinancialProfile } from '@/lib/types'
import { cn } from '@/lib/utils'

const CATEGORIES = [
  'Dining',
  'Groceries',
  'Entertainment',
  'Shopping',
  'Transportation',
  'Gas',
  'Travel',
  'Health',
  'Utilities',
  'Phone',
  'Internet',
  'Personal Care',
  'Education',
  'Other',
]

const PAY_FREQUENCIES = ['Monthly', 'Biweekly', 'Weekly']

function formatCurrency(n: number) {
  return '$' + n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function statusColor(status: BudgetStatus) {
  switch (status) {
    case 'GREEN': return 'bg-success'
    case 'YELLOW': return 'bg-gold-500'
    case 'RED': return 'bg-error'
  }
}

function statusBarColor(status: BudgetStatus) {
  switch (status) {
    case 'GREEN': return 'bg-success'
    case 'YELLOW': return 'bg-gold-500'
    case 'RED': return 'bg-error'
  }
}

function statusTextColor(status: BudgetStatus) {
  switch (status) {
    case 'GREEN': return 'text-success'
    case 'YELLOW': return 'text-gold-500'
    case 'RED': return 'text-error'
  }
}

export default function BudgetPage() {
  const [budgets, setBudgets] = useState<Budget[]>([])
  const [profile, setProfile] = useState<FinancialProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Modal
  const [showModal, setShowModal] = useState(false)
  const [newCategory, setNewCategory] = useState(CATEGORIES[0])
  const [newLimit, setNewLimit] = useState('')
  const [saving, setSaving] = useState(false)

  // Profile form
  const [incomeInput, setIncomeInput] = useState('')
  const [payFrequency, setPayFrequency] = useState('Monthly')
  const [emergencyMonths, setEmergencyMonths] = useState(6)
  const [ageInput, setAgeInput] = useState('')
  const [targetRetirementAgeInput, setTargetRetirementAgeInput] = useState('65')
  const [currentInvestmentsInput, setCurrentInvestmentsInput] = useState('')
  const [savingProfile, setSavingProfile] = useState(false)
  const [detectingIncome, setDetectingIncome] = useState(false)

  useEffect(() => {
    Promise.all([api.budgets.list(), api.profile.get()])
      .then(([b, p]) => {
        setBudgets(b)
        setProfile(p)
        if (p.monthlyIncome !== null) setIncomeInput(String(p.monthlyIncome))
        setPayFrequency(p.payFrequency || 'Monthly')
        setEmergencyMonths(p.emergencyFundTargetMonths || 6)
        if (p.age != null) setAgeInput(String(p.age))
        if (p.targetRetirementAge != null) setTargetRetirementAgeInput(String(p.targetRetirementAge))
        if (p.currentInvestments != null) setCurrentInvestmentsInput(String(p.currentInvestments))
      })
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  const overBudget = budgets.filter(b => b.status === 'RED')
  const overspend = overBudget.reduce((sum, b) => sum + Math.max(0, b.actualSpend - b.monthlyLimit), 0)

  async function handleAddBudget() {
    const limit = parseFloat(newLimit)
    if (isNaN(limit) || limit <= 0) return
    setSaving(true)
    try {
      const b = await api.budgets.upsert(newCategory, limit)
      setBudgets(prev => {
        const exists = prev.find(x => x.id === b.id)
        if (exists) return prev.map(x => x.id === b.id ? b : x)
        return [...prev, b].sort((a, z) => {
          const order: Record<BudgetStatus, number> = { RED: 0, YELLOW: 1, GREEN: 2 }
          return order[a.status] - order[z.status]
        })
      })
      setShowModal(false)
      setNewLimit('')
      setNewCategory(CATEGORIES[0])
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : 'Failed to save budget')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this budget?')) return
    try {
      await api.budgets.delete(id)
      setBudgets(prev => prev.filter(b => b.id !== id))
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : 'Failed to delete budget')
    }
  }

  async function handleSaveProfile() {
    setSavingProfile(true)
    try {
      const updated = await api.profile.update({
        monthlyIncome: incomeInput ? parseFloat(incomeInput) : null,
        payFrequency,
        emergencyFundTargetMonths: emergencyMonths,
        age: ageInput ? parseInt(ageInput, 10) : undefined,
        targetRetirementAge: targetRetirementAgeInput ? parseInt(targetRetirementAgeInput, 10) : undefined,
        currentInvestments: currentInvestmentsInput ? parseFloat(currentInvestmentsInput) : undefined,
      })
      setProfile(updated)
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : 'Failed to save profile')
    } finally {
      setSavingProfile(false)
    }
  }

  async function handleDetectIncome() {
    setDetectingIncome(true)
    try {
      const result = await api.profile.detectIncome()
      setIncomeInput(String(result.monthlyIncome))
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : 'Could not detect income')
    } finally {
      setDetectingIncome(false)
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

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Budget Manager</h1>
          <p className="text-text-secondary text-sm mt-1">Track spending against monthly limits</p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-4 py-2 bg-gold-500 hover:bg-gold-600 text-black font-semibold rounded-lg transition-colors text-sm"
        >
          <Plus className="w-4 h-4" />
          Add Budget
        </button>
      </div>

      {/* Summary bar */}
      <div className="bg-background-secondary rounded-xl border border-border p-4 flex flex-wrap gap-6">
        <div className="flex items-center gap-2">
          <CheckCircle className="w-4 h-4 text-success" />
          <span className="text-text-secondary text-sm">
            <span className="font-mono font-bold text-text-primary">{budgets.filter(b => b.isActive).length}</span> budgets active
          </span>
        </div>
        <div className="flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 text-error" />
          <span className="text-text-secondary text-sm">
            <span className="font-mono font-bold text-error">{overBudget.length}</span> over budget
          </span>
        </div>
        <div className="flex items-center gap-2">
          <TrendingUp className="w-4 h-4 text-gold-500" />
          <span className="text-text-secondary text-sm">
            <span className="font-mono font-bold text-gold-500">{formatCurrency(overspend)}</span> overspend
          </span>
        </div>
      </div>

      {/* Budget cards */}
      {budgets.length === 0 ? (
        <div className="bg-background-secondary rounded-xl border border-border p-12 text-center">
          <Target className="w-12 h-12 text-text-muted mx-auto mb-4" />
          <p className="text-text-secondary">No budgets yet. Add your first budget above.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {budgets.map(budget => (
            <div
              key={budget.id}
              className={cn(
                'bg-background-secondary rounded-xl border p-5 space-y-3',
                budget.status === 'RED' ? 'border-error/40' : 'border-border'
              )}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className={cn('w-2.5 h-2.5 rounded-full', statusColor(budget.status))} />
                  <span className="font-semibold text-text-primary">{budget.category}</span>
                </div>
                <button
                  onClick={() => handleDelete(budget.id)}
                  className="text-text-muted hover:text-error transition-colors p-1"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>

              {/* Progress bar */}
              <div className="space-y-1.5">
                <div className="w-full bg-background-tertiary rounded-full h-2">
                  <div
                    className={cn('h-2 rounded-full transition-all', statusBarColor(budget.status))}
                    style={{ width: `${Math.min(budget.percentUsed, 100)}%` }}
                  />
                </div>
                <div className="flex justify-between items-center">
                  <span className={cn('text-xs font-mono font-bold', statusTextColor(budget.status))}>
                    {budget.percentUsed.toFixed(0)}% used
                  </span>
                  <span className="text-xs text-text-muted font-mono">
                    {formatCurrency(budget.actualSpend)} / {formatCurrency(budget.monthlyLimit)}
                  </span>
                </div>
              </div>

              {/* Projected */}
              <div className="flex items-center justify-between text-xs">
                <span className="text-text-muted">Projected end of month:</span>
                <span className={cn('font-mono font-semibold', budget.projectedMonthEnd > budget.monthlyLimit ? 'text-error' : 'text-text-secondary')}>
                  {formatCurrency(budget.projectedMonthEnd)}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Financial Profile */}
      <div className="bg-background-secondary rounded-xl border border-border p-6 space-y-5">
        <h2 className="text-lg font-bold text-text-primary">Financial Profile</h2>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          {/* Monthly Income */}
          <div className="space-y-2">
            <label className="text-sm text-text-secondary font-medium">Monthly Income (after tax)</label>
            <div className="flex gap-2">
              <input
                type="number"
                value={incomeInput}
                onChange={e => setIncomeInput(e.target.value)}
                placeholder="e.g. 5000"
                className="flex-1 bg-background-tertiary border border-border rounded-lg px-3 py-2 text-text-primary font-mono text-sm focus:outline-none focus:border-gold-500/50"
              />
              <button
                onClick={handleDetectIncome}
                disabled={detectingIncome}
                className="px-3 py-2 bg-background-tertiary border border-border hover:border-gold-500/40 text-text-secondary hover:text-gold-500 text-sm rounded-lg transition-colors whitespace-nowrap disabled:opacity-50 flex items-center gap-1"
              >
                {detectingIncome ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : null}
                Auto-detect
              </button>
            </div>
          </div>

          {/* Pay Frequency */}
          <div className="space-y-2">
            <label className="text-sm text-text-secondary font-medium">Pay Frequency</label>
            <select
              value={payFrequency}
              onChange={e => setPayFrequency(e.target.value)}
              className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-text-primary text-sm focus:outline-none focus:border-gold-500/50"
            >
              {PAY_FREQUENCIES.map(f => (
                <option key={f} value={f}>{f}</option>
              ))}
            </select>
          </div>

          {/* Age */}
          <div className="space-y-2">
            <label className="text-sm text-text-secondary font-medium">Your Age</label>
            <input
              type="number"
              value={ageInput}
              onChange={e => setAgeInput(e.target.value)}
              placeholder="e.g. 30"
              min={18}
              max={100}
              className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-text-primary font-mono text-sm focus:outline-none focus:border-gold-500/50"
            />
          </div>

          {/* Target Retirement Age */}
          <div className="space-y-2">
            <label className="text-sm text-text-secondary font-medium">Target Retirement Age</label>
            <input
              type="number"
              value={targetRetirementAgeInput}
              onChange={e => setTargetRetirementAgeInput(e.target.value)}
              placeholder="e.g. 65"
              min={40}
              max={80}
              className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-text-primary font-mono text-sm focus:outline-none focus:border-gold-500/50"
            />
          </div>

          {/* Current Investments */}
          <div className="space-y-2 md:col-span-2">
            <label className="text-sm text-text-secondary font-medium">Current Investments (401k, IRA, Brokerage)</label>
            <input
              type="number"
              value={currentInvestmentsInput}
              onChange={e => setCurrentInvestmentsInput(e.target.value)}
              placeholder="e.g. 50000"
              min={0}
              className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-text-primary font-mono text-sm focus:outline-none focus:border-gold-500/50"
            />
          </div>

          {/* Emergency Fund */}
          <div className="space-y-2 md:col-span-2">
            <label className="text-sm text-text-secondary font-medium">
              Emergency Fund Target: <span className="text-gold-500 font-mono font-bold">{emergencyMonths} months</span>
            </label>
            <input
              type="range"
              min={1}
              max={12}
              value={emergencyMonths}
              onChange={e => setEmergencyMonths(Number(e.target.value))}
              className="w-full accent-gold-500"
            />
            <div className="flex justify-between text-xs text-text-muted">
              <span>1 month</span>
              <span className="text-text-secondary">Recommended: 3–6 months</span>
              <span>12 months</span>
            </div>
          </div>
        </div>

        <div className="flex justify-end">
          <button
            onClick={handleSaveProfile}
            disabled={savingProfile}
            className="flex items-center gap-2 px-5 py-2 bg-gold-500 hover:bg-gold-600 text-black font-semibold rounded-lg transition-colors text-sm disabled:opacity-60"
          >
            {savingProfile ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
            Save Profile
          </button>
        </div>

        {profile && (
          <div className="text-xs text-text-muted border-t border-border pt-3">
            Last updated: {new Date(profile.updatedAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
          </div>
        )}
      </div>

      {/* Add Budget Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-background-secondary border border-border rounded-xl p-6 w-full max-w-md space-y-4 shadow-xl">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-text-primary">Add Budget</h2>
              <button onClick={() => setShowModal(false)} className="text-text-muted hover:text-text-primary">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3">
              <div className="space-y-1.5">
                <label className="text-sm text-text-secondary">Category</label>
                <select
                  value={newCategory}
                  onChange={e => setNewCategory(e.target.value)}
                  className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-text-primary text-sm focus:outline-none focus:border-gold-500/50"
                >
                  {CATEGORIES.map(c => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <label className="text-sm text-text-secondary">Monthly Limit ($)</label>
                <input
                  type="number"
                  value={newLimit}
                  onChange={e => setNewLimit(e.target.value)}
                  placeholder="e.g. 500"
                  className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-text-primary font-mono text-sm focus:outline-none focus:border-gold-500/50"
                />
              </div>
            </div>

            <div className="flex gap-3 pt-1">
              <button
                onClick={() => setShowModal(false)}
                className="flex-1 px-4 py-2 border border-border text-text-secondary hover:text-text-primary hover:border-text-muted rounded-lg text-sm transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleAddBudget}
                disabled={saving || !newLimit}
                className="flex-1 flex items-center justify-center gap-2 px-4 py-2 bg-gold-500 hover:bg-gold-600 text-black font-semibold rounded-lg text-sm transition-colors disabled:opacity-60"
              >
                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                Save Budget
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
