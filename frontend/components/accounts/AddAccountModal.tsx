'use client'

import { useState } from 'react'
import { api } from '@/lib/api'
import { X } from 'lucide-react'

interface Props {
  onClose: () => void
  onSave: () => void
}

const ACCOUNT_COLORS = [
  '#1A56DB', '#7C3AED', '#DC2626', '#059669',
  '#D97706', '#DB2777', '#0891B2', '#374151',
]

export function AddAccountModal({ onClose, onSave }: Props) {
  const [form, setForm] = useState({
    name: '',
    institution: '',
    type: 'CREDIT_CARD',
    last4: '',
    creditLimit: '',
    currentBalance: '',
    availableCredit: '',
    apr: '',
    promoApr: '',
    promoAprEndDate: '',
    paymentDueDay: '',
    minPayment: '',
    rewardsProgram: '',
    color: ACCOUNT_COLORS[0],
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const set = (key: string, value: string) =>
    setForm(f => ({ ...f, [key]: value }))

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError('')
    try {
      await api.accounts.create({
        name: form.name,
        institution: form.institution || undefined,
        type: form.type as any,
        last4: form.last4 || undefined,
        creditLimit: form.creditLimit ? parseFloat(form.creditLimit) : undefined,
        currentBalance: form.currentBalance ? parseFloat(form.currentBalance) : undefined,
        availableCredit: form.availableCredit ? parseFloat(form.availableCredit) : undefined,
        apr: form.apr ? parseFloat(form.apr) : undefined,
        promoApr: form.promoApr ? parseFloat(form.promoApr) : undefined,
        promoAprEndDate: form.promoAprEndDate || undefined,
        paymentDueDay: form.paymentDueDay ? parseInt(form.paymentDueDay) : undefined,
        minPayment: form.minPayment ? parseFloat(form.minPayment) : undefined,
        rewardsProgram: form.rewardsProgram || undefined,
        color: form.color,
      } as any)
      onSave()
    } catch (e: any) {
      setError(e.message || 'Failed to save account')
    } finally {
      setSaving(false)
    }
  }

  const isCredit = form.type === 'CREDIT_CARD'

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="glass-card rounded-2xl w-full max-w-lg shadow-card border border-border max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-5 border-b border-border">
          <h2 className="text-base font-semibold text-text-primary">Add Account</h2>
          <button onClick={onClose} className="p-1.5 hover:bg-background-tertiary rounded-lg transition-colors">
            <X className="w-4 h-4 text-text-muted" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          {error && (
            <p className="text-sm text-red-400 bg-red-400/10 rounded-lg px-3 py-2">{error}</p>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div className="col-span-2">
              <label className="text-xs text-text-muted mb-1 block">Account Name *</label>
              <input
                required
                value={form.name}
                onChange={e => set('name', e.target.value)}
                placeholder="Chase Sapphire Reserve"
                className="input-field w-full"
              />
            </div>

            <div>
              <label className="text-xs text-text-muted mb-1 block">Institution</label>
              <input
                value={form.institution}
                onChange={e => set('institution', e.target.value)}
                placeholder="Chase"
                className="input-field w-full"
              />
            </div>

            <div>
              <label className="text-xs text-text-muted mb-1 block">Type *</label>
              <select
                value={form.type}
                onChange={e => set('type', e.target.value)}
                className="input-field w-full"
              >
                <option value="CREDIT_CARD">Credit Card</option>
                <option value="CHECKING">Checking</option>
                <option value="SAVINGS">Savings</option>
                <option value="LOAN">Loan</option>
              </select>
            </div>

            <div>
              <label className="text-xs text-text-muted mb-1 block">Last 4 Digits</label>
              <input
                value={form.last4}
                onChange={e => set('last4', e.target.value.slice(0, 4))}
                placeholder="4321"
                maxLength={4}
                className="input-field w-full font-mono"
              />
            </div>

            <div>
              <label className="text-xs text-text-muted mb-1 block">Current Balance ($)</label>
              <input
                type="number"
                step="0.01"
                value={form.currentBalance}
                onChange={e => set('currentBalance', e.target.value)}
                placeholder="0.00"
                className="input-field w-full font-num"
              />
            </div>

            {isCredit && (
              <>
                <div>
                  <label className="text-xs text-text-muted mb-1 block">Credit Limit ($)</label>
                  <input
                    type="number"
                    step="0.01"
                    value={form.creditLimit}
                    onChange={e => set('creditLimit', e.target.value)}
                    placeholder="10000.00"
                    className="input-field w-full font-num"
                  />
                </div>
                <div>
                  <label className="text-xs text-text-muted mb-1 block">APR (%)</label>
                  <input
                    type="number"
                    step="0.01"
                    value={form.apr}
                    onChange={e => set('apr', e.target.value)}
                    placeholder="24.99"
                    className="input-field w-full font-num"
                  />
                </div>
                <div>
                  <label className="text-xs text-text-muted mb-1 block">Promo APR (%)</label>
                  <input
                    type="number"
                    step="0.01"
                    value={form.promoApr}
                    onChange={e => set('promoApr', e.target.value)}
                    placeholder="0.00"
                    className="input-field w-full font-num"
                  />
                </div>
                <div>
                  <label className="text-xs text-text-muted mb-1 block">Promo APR End Date</label>
                  <input
                    type="date"
                    value={form.promoAprEndDate}
                    onChange={e => set('promoAprEndDate', e.target.value)}
                    className="input-field w-full"
                  />
                </div>
                <div>
                  <label className="text-xs text-text-muted mb-1 block">Payment Due Day</label>
                  <input
                    type="number"
                    min="1"
                    max="31"
                    value={form.paymentDueDay}
                    onChange={e => set('paymentDueDay', e.target.value)}
                    placeholder="15"
                    className="input-field w-full font-num"
                  />
                </div>
                <div>
                  <label className="text-xs text-text-muted mb-1 block">Min Payment ($)</label>
                  <input
                    type="number"
                    step="0.01"
                    value={form.minPayment}
                    onChange={e => set('minPayment', e.target.value)}
                    placeholder="35.00"
                    className="input-field w-full font-num"
                  />
                </div>
                <div className="col-span-2">
                  <label className="text-xs text-text-muted mb-1 block">Rewards Program</label>
                  <input
                    value={form.rewardsProgram}
                    onChange={e => set('rewardsProgram', e.target.value)}
                    placeholder="3x dining, 1x everything else"
                    className="input-field w-full"
                  />
                </div>
              </>
            )}

            {/* Color picker */}
            <div className="col-span-2">
              <label className="text-xs text-text-muted mb-2 block">Card Color</label>
              <div className="flex gap-2">
                {ACCOUNT_COLORS.map(color => (
                  <button
                    key={color}
                    type="button"
                    onClick={() => set('color', color)}
                    className="w-8 h-8 rounded-lg border-2 transition-all"
                    style={{
                      backgroundColor: color,
                      borderColor: form.color === color ? '#F59E0B' : 'transparent',
                    }}
                  />
                ))}
              </div>
            </div>
          </div>

          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2 border border-border text-text-secondary text-sm rounded-lg hover:bg-background-tertiary transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saving}
              className="flex-1 px-4 py-2 bg-gold-500 text-black text-sm font-semibold rounded-lg hover:bg-gold-600 disabled:opacity-50 transition-colors"
            >
              {saving ? 'Saving...' : 'Add Account'}
            </button>
          </div>
        </form>
      </div>

      <style jsx>{`
        .input-field {
          background: #16161F;
          border: 1px solid #1E1E2E;
          border-radius: 8px;
          padding: 8px 12px;
          font-size: 13px;
          color: #F0F0F8;
          transition: border-color 0.15s;
        }
        .input-field:focus {
          outline: none;
          border-color: rgba(245, 158, 11, 0.5);
        }
        .input-field::placeholder {
          color: #5A5A72;
        }
        select.input-field option {
          background: #16161F;
        }
      `}</style>
    </div>
  )
}
