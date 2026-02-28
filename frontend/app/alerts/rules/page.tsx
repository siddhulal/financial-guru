'use client'

import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { AlertRule, AlertRuleType } from '@/lib/types'
import { cn } from '@/lib/utils'
import { Plus, Trash2, X, Bell, BellOff, Clock } from 'lucide-react'

const RULE_TYPE_LABELS: Record<AlertRuleType, string> = {
  TRANSACTION_AMOUNT: 'Transaction Amount',
  MONTHLY_CATEGORY_SPEND: 'Monthly Category Spend',
  BALANCE_BELOW: 'Balance Below',
  UTILIZATION_ABOVE: 'Utilization Above',
}

const RULE_TYPE_DESCRIPTIONS: Record<AlertRuleType, string> = {
  TRANSACTION_AMOUNT: 'Alert when a single transaction exceeds threshold',
  MONTHLY_CATEGORY_SPEND: 'Alert when monthly spending in a category exceeds threshold',
  BALANCE_BELOW: 'Alert when account balance drops below threshold',
  UTILIZATION_ABOVE: 'Alert when credit utilization exceeds threshold',
}

const OPERATOR_LABELS: Record<string, string> = {
  'GREATER_THAN': '>',
  'LESS_THAN': '<',
  'EQUALS': '=',
  'GREATER_THAN_OR_EQUAL': '>=',
  'LESS_THAN_OR_EQUAL': '<=',
}

const COMMON_CATEGORIES = [
  'Dining', 'Groceries', 'Gas', 'Entertainment', 'Shopping', 'Travel',
  'Healthcare', 'Utilities', 'Subscriptions', 'Personal Care', 'Other',
]

function timeAgo(dateStr: string): string {
  const ms = Date.now() - new Date(dateStr).getTime()
  const days = Math.floor(ms / 86400000)
  if (days === 0) return 'Today'
  if (days === 1) return 'Yesterday'
  return `${days} days ago`
}

interface RuleFormData {
  name: string
  ruleType: AlertRuleType
  conditionOperator: string
  thresholdAmount: string
  category: string
}

const defaultForm: RuleFormData = {
  name: '',
  ruleType: 'TRANSACTION_AMOUNT',
  conditionOperator: 'GREATER_THAN',
  thresholdAmount: '',
  category: '',
}

export default function AlertRulesPage() {
  const [rules, setRules] = useState<AlertRule[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState<RuleFormData>(defaultForm)
  const [saving, setSaving] = useState(false)
  const [toggling, setToggling] = useState<Record<string, boolean>>({})

  useEffect(() => {
    loadRules()
  }, [])

  async function loadRules() {
    setLoading(true)
    try {
      const data = await api.alertRules.list()
      setRules(data)
    } catch {
      setError('Failed to load alert rules')
    } finally {
      setLoading(false)
    }
  }

  async function handleSave() {
    if (!form.name.trim()) return
    const threshold = parseFloat(form.thresholdAmount)
    if (isNaN(threshold) || threshold <= 0) return

    setSaving(true)
    try {
      const created = await api.alertRules.create({
        name: form.name.trim(),
        ruleType: form.ruleType,
        conditionOperator: form.conditionOperator,
        thresholdAmount: threshold,
        category: form.category || null,
        isActive: true,
      })
      setRules(prev => [...prev, created])
      setShowModal(false)
      setForm(defaultForm)
    } catch {
      // ignore
    } finally {
      setSaving(false)
    }
  }

  async function handleToggle(rule: AlertRule) {
    setToggling(prev => ({ ...prev, [rule.id]: true }))
    try {
      const updated = await api.alertRules.update(rule.id, { isActive: !rule.isActive })
      setRules(prev => prev.map(r => r.id === updated.id ? updated : r))
    } catch {
      // ignore
    } finally {
      setToggling(prev => ({ ...prev, [rule.id]: false }))
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this alert rule?')) return
    try {
      await api.alertRules.delete(id)
      setRules(prev => prev.filter(r => r.id !== id))
    } catch {
      // ignore
    }
  }

  const showCategoryField = form.ruleType === 'MONTHLY_CATEGORY_SPEND'

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
          <h1 className="text-2xl font-bold text-text-primary">Alert Rules</h1>
          <p className="text-text-muted text-sm mt-1">
            {rules.length} rule{rules.length !== 1 ? 's' : ''} · {rules.filter(r => r.isActive).length} active
          </p>
        </div>
        <button
          onClick={() => { setForm(defaultForm); setShowModal(true) }}
          className="flex items-center gap-2 px-4 py-2 bg-gold-500 text-black font-semibold text-sm rounded-lg hover:bg-gold-400 transition-colors"
        >
          <Plus className="w-4 h-4" />
          Add Rule
        </button>
      </div>

      {/* Empty state */}
      {rules.length === 0 && (
        <div className="bg-background-secondary rounded-xl border border-border p-16 text-center">
          <Bell className="w-12 h-12 text-text-muted/30 mx-auto mb-4" />
          <h3 className="text-lg font-semibold text-text-primary mb-2">No custom rules yet</h3>
          <p className="text-text-muted text-sm mb-6">Create rules to get personalized alerts for your spending.</p>
          <button
            onClick={() => { setForm(defaultForm); setShowModal(true) }}
            className="px-6 py-2.5 bg-gold-500 text-black font-semibold text-sm rounded-lg hover:bg-gold-400 transition-colors"
          >
            Create Your First Rule
          </button>
        </div>
      )}

      {/* Rules list */}
      {rules.length > 0 && (
        <div className="space-y-3">
          {rules.map(rule => (
            <div
              key={rule.id}
              className={cn(
                'bg-background-secondary rounded-xl border p-5 transition-all',
                rule.isActive ? 'border-border' : 'border-border/40 opacity-60'
              )}
            >
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1 space-y-2">
                  {/* Name + type badge */}
                  <div className="flex items-center gap-3 flex-wrap">
                    <h3 className="font-semibold text-text-primary">{rule.name}</h3>
                    <span className="px-2 py-0.5 text-xs font-medium bg-gold-500/10 text-gold-500 rounded-full border border-gold-500/20">
                      {RULE_TYPE_LABELS[rule.ruleType] || rule.ruleType}
                    </span>
                    {!rule.isActive && (
                      <span className="px-2 py-0.5 text-xs font-medium bg-background-tertiary text-text-muted rounded-full">
                        Inactive
                      </span>
                    )}
                  </div>

                  {/* Condition description */}
                  <p className="text-sm text-text-muted">
                    Alert when <span className="text-text-secondary">{RULE_TYPE_LABELS[rule.ruleType]?.toLowerCase()}</span>
                    {' '}
                    <span className="font-mono text-text-primary">{OPERATOR_LABELS[rule.conditionOperator] || rule.conditionOperator}</span>
                    {' '}
                    <span className="font-mono text-gold-500">${rule.thresholdAmount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                  </p>

                  {/* Category / Account */}
                  <div className="flex items-center gap-4 text-xs text-text-muted">
                    {rule.category && (
                      <span>Category: <span className="text-text-secondary">{rule.category}</span></span>
                    )}
                    {rule.accountId && (
                      <span>Account: <span className="text-text-secondary">{rule.accountId}</span></span>
                    )}
                    <div className="flex items-center gap-1">
                      <Clock className="w-3 h-3" />
                      {rule.lastTriggeredAt
                        ? `Last triggered: ${timeAgo(rule.lastTriggeredAt)}`
                        : 'Never triggered'}
                    </div>
                  </div>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-2 flex-shrink-0">
                  <button
                    onClick={() => handleToggle(rule)}
                    disabled={toggling[rule.id]}
                    className={cn(
                      'p-2 rounded-lg transition-colors',
                      rule.isActive
                        ? 'text-gold-500 hover:bg-gold-500/10'
                        : 'text-text-muted hover:bg-background-tertiary'
                    )}
                    title={rule.isActive ? 'Disable rule' : 'Enable rule'}
                  >
                    {toggling[rule.id] ? (
                      <div className="w-4 h-4 border-t-2 border-current rounded-full animate-spin" />
                    ) : rule.isActive ? (
                      <Bell className="w-4 h-4" />
                    ) : (
                      <BellOff className="w-4 h-4" />
                    )}
                  </button>
                  <button
                    onClick={() => handleDelete(rule.id)}
                    className="p-2 rounded-lg text-text-muted hover:text-red-400 hover:bg-red-500/10 transition-colors"
                    title="Delete rule"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Add Rule Modal */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setShowModal(false)} />
          <div className="relative w-full max-w-md bg-background-secondary border border-border rounded-2xl shadow-2xl p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-text-primary">New Alert Rule</h2>
              <button onClick={() => setShowModal(false)} className="text-text-muted hover:text-text-primary transition-colors">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3">
              {/* Rule name */}
              <div>
                <label className="block text-xs font-medium text-text-muted mb-1">Rule Name *</label>
                <input
                  type="text"
                  value={form.name}
                  onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                  placeholder="e.g. Large Transaction Alert"
                  className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
                />
              </div>

              {/* Rule type */}
              <div>
                <label className="block text-xs font-medium text-text-muted mb-1">Rule Type *</label>
                <select
                  value={form.ruleType}
                  onChange={e => setForm(f => ({ ...f, ruleType: e.target.value as AlertRuleType }))}
                  className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
                >
                  {Object.entries(RULE_TYPE_LABELS).map(([key, label]) => (
                    <option key={key} value={key}>{label}</option>
                  ))}
                </select>
                <p className="text-xs text-text-muted mt-1">
                  {RULE_TYPE_DESCRIPTIONS[form.ruleType]}
                </p>
              </div>

              {/* Threshold */}
              <div>
                <label className="block text-xs font-medium text-text-muted mb-1">Threshold Amount *</label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted text-sm">$</span>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.thresholdAmount}
                    onChange={e => setForm(f => ({ ...f, thresholdAmount: e.target.value }))}
                    placeholder="100.00"
                    className="w-full bg-background-tertiary border border-border rounded-lg pl-7 pr-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
                  />
                </div>
              </div>

              {/* Category — only for MONTHLY_CATEGORY_SPEND */}
              {showCategoryField && (
                <div>
                  <label className="block text-xs font-medium text-text-muted mb-1">Category *</label>
                  <select
                    value={form.category}
                    onChange={e => setForm(f => ({ ...f, category: e.target.value }))}
                    className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary outline-none focus:border-gold-500 transition-colors"
                  >
                    <option value="">Select category...</option>
                    {COMMON_CATEGORIES.map(c => (
                      <option key={c} value={c}>{c}</option>
                    ))}
                  </select>
                </div>
              )}
            </div>

            <div className="flex gap-3 pt-2">
              <button
                onClick={() => setShowModal(false)}
                className="flex-1 py-2 border border-border rounded-lg text-sm text-text-secondary hover:bg-background-tertiary transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSave}
                disabled={saving || !form.name.trim() || !form.thresholdAmount || (showCategoryField && !form.category)}
                className="flex-1 py-2 bg-gold-500 text-black font-semibold text-sm rounded-lg hover:bg-gold-400 disabled:opacity-50 transition-colors"
              >
                {saving ? 'Saving...' : 'Create Rule'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
