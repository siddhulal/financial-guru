'use client'

import { useState, useEffect } from 'react'
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'
import { TrendingUp, TrendingDown, Plus, X, Trash2, Camera, Loader2 } from 'lucide-react'
import { api } from '@/lib/api'
import { NetWorthResponse, NetWorthSnapshot, ManualAsset } from '@/lib/types'
import { cn } from '@/lib/utils'

function formatCurrency(n: number) {
  return '$' + Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatShort(n: number) {
  const abs = Math.abs(n)
  if (abs >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (abs >= 1_000) return (n / 1_000).toFixed(0) + 'K'
  return n.toFixed(0)
}

const ASSET_CLASSES = [
  { value: 'REAL_ESTATE', label: 'Real Estate' },
  { value: 'VEHICLE', label: 'Vehicle' },
  { value: 'INVESTMENT', label: 'Investment' },
  { value: 'LOAN', label: 'Loan' },
  { value: 'RETIREMENT', label: 'Retirement' },
  { value: 'OTHER', label: 'Other' },
]

function assetClassLabel(cls: string) {
  return ASSET_CLASSES.find(a => a.value === cls)?.label ?? cls
}

export default function NetWorthPage() {
  const [data, setData] = useState<NetWorthResponse | null>(null)
  const [history, setHistory] = useState<NetWorthSnapshot[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showModal, setShowModal] = useState(false)
  const [snapshotting, setSnapshotting] = useState(false)

  // Asset form
  const [assetName, setAssetName] = useState('')
  const [assetType, setAssetType] = useState<'ASSET' | 'LIABILITY'>('ASSET')
  const [assetClass, setAssetClass] = useState('REAL_ESTATE')
  const [assetValue, setAssetValue] = useState('')
  const [assetNotes, setAssetNotes] = useState('')
  const [savingAsset, setSavingAsset] = useState(false)

  function resetModal() {
    setAssetName('')
    setAssetType('ASSET')
    setAssetClass('REAL_ESTATE')
    setAssetValue('')
    setAssetNotes('')
  }

  useEffect(() => {
    Promise.all([api.networth.get(), api.networth.history()])
      .then(([d, h]) => {
        setData(d)
        setHistory(h)
      })
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  async function handleAddAsset() {
    const val = parseFloat(assetValue)
    if (!assetName || isNaN(val) || val < 0) return
    setSavingAsset(true)
    try {
      await api.networth.assets.create({
        name: assetName,
        assetType,
        assetClass,
        currentValue: val,
        notes: assetNotes || undefined,
      })
      const [d, h] = await Promise.all([api.networth.get(), api.networth.history()])
      setData(d)
      setHistory(h)
      setShowModal(false)
      resetModal()
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : 'Failed to add asset')
    } finally {
      setSavingAsset(false)
    }
  }

  async function handleDeleteAsset(id: string) {
    if (!confirm('Delete this asset?')) return
    try {
      await api.networth.assets.delete(id)
      const [d, h] = await Promise.all([api.networth.get(), api.networth.history()])
      setData(d)
      setHistory(h)
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : 'Failed to delete asset')
    }
  }

  async function handleSnapshot() {
    setSnapshotting(true)
    try {
      await api.networth.captureSnapshot()
      const h = await api.networth.history()
      setHistory(h)
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : 'Failed to capture snapshot')
    } finally {
      setSnapshotting(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-gold-500" />
      </div>
    )
  }

  if (error) return <div className="p-6 text-error">{error}</div>
  if (!data) return null

  const netPositive = data.netWorth >= 0
  const monthlyPositive = data.monthlyChange >= 0
  const yearlyPositive = data.yearlyChange >= 0

  const chartData = history.map(h => ({
    date: new Date(h.snapshotDate).toLocaleDateString('en-US', { month: 'short', year: '2-digit' }),
    netWorth: h.netWorth,
  }))

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Net Worth Tracker</h1>
          <p className="text-text-secondary text-sm mt-1">Your complete financial picture</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={handleSnapshot}
            disabled={snapshotting}
            className="flex items-center gap-2 px-3 py-2 border border-border hover:border-gold-500/40 text-text-secondary hover:text-gold-500 text-sm rounded-lg transition-colors disabled:opacity-50"
          >
            {snapshotting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Camera className="w-4 h-4" />}
            Capture Snapshot
          </button>
          <button
            onClick={() => setShowModal(true)}
            className="flex items-center gap-2 px-4 py-2 bg-gold-500 hover:bg-gold-600 text-black font-semibold rounded-lg transition-colors text-sm"
          >
            <Plus className="w-4 h-4" />
            Add Asset/Liability
          </button>
        </div>
      </div>

      {/* Net worth hero */}
      <div className="bg-background-secondary rounded-xl border border-border p-6 space-y-4">
        <div className="flex flex-col md:flex-row md:items-end gap-4">
          <div>
            <p className="text-text-muted text-sm">Total Net Worth</p>
            <p className={cn('font-mono font-bold text-4xl mt-1', netPositive ? 'text-gold-500' : 'text-error')}>
              {netPositive ? '' : '-'}{formatCurrency(data.netWorth)}
            </p>
          </div>
          <div className="flex gap-5 pb-1">
            <div className="flex items-center gap-1.5">
              {monthlyPositive ? (
                <TrendingUp className="w-4 h-4 text-success" />
              ) : (
                <TrendingDown className="w-4 h-4 text-error" />
              )}
              <span className={cn('text-sm font-mono font-semibold', monthlyPositive ? 'text-success' : 'text-error')}>
                {monthlyPositive ? '+' : '-'}{formatCurrency(data.monthlyChange)} this month
              </span>
            </div>
            <div className="flex items-center gap-1.5">
              {yearlyPositive ? (
                <TrendingUp className="w-4 h-4 text-success" />
              ) : (
                <TrendingDown className="w-4 h-4 text-error" />
              )}
              <span className={cn('text-sm font-mono font-semibold', yearlyPositive ? 'text-success' : 'text-error')}>
                {yearlyPositive ? '+' : '-'}{formatCurrency(data.yearlyChange)} this year
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Chart */}
      {chartData.length > 0 && (
        <div className="bg-background-secondary rounded-xl border border-border p-5 space-y-3">
          <h2 className="font-bold text-text-primary">12-Month Trend</h2>
          <div className="h-56">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 5, right: 15, left: 15, bottom: 5 }}>
                <defs>
                  <linearGradient id="nwGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#F59E0B" stopOpacity={0.25} />
                    <stop offset="95%" stopColor="#F59E0B" stopOpacity={0.02} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#ffffff08" />
                <XAxis dataKey="date" tick={{ fill: '#6B7280', fontSize: 11 }} stroke="#ffffff10" />
                <YAxis
                  tickFormatter={v => '$' + formatShort(v)}
                  tick={{ fill: '#6B7280', fontSize: 11 }}
                  stroke="#ffffff10"
                />
                <Tooltip
                  contentStyle={{ backgroundColor: '#111118', border: '1px solid #2D2D3F', borderRadius: '8px', color: '#E5E7EB' }}
                  formatter={(val: number) => [formatCurrency(val), 'Net Worth']}
                />
                <Area
                  type="monotone"
                  dataKey="netWorth"
                  stroke="#F59E0B"
                  strokeWidth={2}
                  fill="url(#nwGradient)"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Breakdown cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-background-secondary rounded-xl border border-border p-4 space-y-1">
          <p className="text-xs text-text-muted">Liquid Assets</p>
          <p className="font-mono font-bold text-lg text-success">{formatCurrency(data.liquidAssets)}</p>
          <p className="text-xs text-text-muted">Checking & savings</p>
        </div>
        <div className="bg-background-secondary rounded-xl border border-border p-4 space-y-1">
          <p className="text-xs text-text-muted">Manual Assets</p>
          <p className="font-mono font-bold text-lg text-blue-400">{formatCurrency(data.manualAssetsTotal)}</p>
          <p className="text-xs text-text-muted">Real estate, investments</p>
        </div>
        <div className="bg-background-secondary rounded-xl border border-border p-4 space-y-1">
          <p className="text-xs text-text-muted">CC Debt</p>
          <p className="font-mono font-bold text-lg text-error">-{formatCurrency(data.creditCardDebt)}</p>
          <p className="text-xs text-text-muted">Credit card balances</p>
        </div>
        <div className="bg-background-secondary rounded-xl border border-border p-4 space-y-1">
          <p className="text-xs text-text-muted">Manual Liabilities</p>
          <p className="font-mono font-bold text-lg text-error">-{formatCurrency(data.manualLiabilities)}</p>
          <p className="text-xs text-text-muted">Loans & other debts</p>
        </div>
      </div>

      {/* Manual assets table */}
      {data.assets.length > 0 && (
        <div className="bg-background-secondary rounded-xl border border-border p-5 space-y-3">
          <h2 className="font-bold text-text-primary">Manual Assets & Liabilities</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border">
                  <th className="text-left py-2 px-3 text-text-muted font-medium">Name</th>
                  <th className="text-left py-2 px-3 text-text-muted font-medium">Type</th>
                  <th className="text-left py-2 px-3 text-text-muted font-medium">Class</th>
                  <th className="text-right py-2 px-3 text-text-muted font-medium">Value</th>
                  <th className="text-right py-2 px-3 text-text-muted font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.assets.map((asset, i) => (
                  <tr key={asset.id} className={cn('border-b border-border/50', i % 2 === 0 ? 'bg-background-tertiary/20' : '')}>
                    <td className="py-2.5 px-3 text-text-primary font-medium">{asset.name}</td>
                    <td className="py-2.5 px-3">
                      <span className={cn(
                        'text-xs px-2 py-0.5 rounded font-semibold',
                        asset.assetType === 'ASSET'
                          ? 'bg-success/15 text-success'
                          : 'bg-error/15 text-error'
                      )}>
                        {asset.assetType}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-text-secondary text-xs">{assetClassLabel(asset.assetClass)}</td>
                    <td className={cn('py-2.5 px-3 text-right font-mono font-semibold', asset.assetType === 'LIABILITY' ? 'text-error' : 'text-text-primary')}>
                      {asset.assetType === 'LIABILITY' ? '-' : ''}{formatCurrency(asset.currentValue)}
                    </td>
                    <td className="py-2.5 px-3 text-right">
                      <button
                        onClick={() => handleDeleteAsset(asset.id)}
                        className="text-text-muted hover:text-error transition-colors p-1"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Add Asset Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-background-secondary border border-border rounded-xl p-6 w-full max-w-md space-y-4 shadow-xl">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-text-primary">Add Asset / Liability</h2>
              <button onClick={() => { setShowModal(false); resetModal() }} className="text-text-muted hover:text-text-primary">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3">
              <div className="space-y-1.5">
                <label className="text-sm text-text-secondary">Name</label>
                <input
                  type="text"
                  value={assetName}
                  onChange={e => setAssetName(e.target.value)}
                  placeholder="e.g. Primary Residence"
                  className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-text-primary text-sm focus:outline-none focus:border-gold-500/50"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-sm text-text-secondary">Type</label>
                <div className="flex gap-2">
                  {(['ASSET', 'LIABILITY'] as const).map(t => (
                    <button
                      key={t}
                      onClick={() => setAssetType(t)}
                      className={cn(
                        'flex-1 py-2 rounded-lg text-sm font-medium border transition-colors',
                        assetType === t
                          ? t === 'ASSET'
                            ? 'bg-success/20 border-success/40 text-success'
                            : 'bg-error/20 border-error/40 text-error'
                          : 'border-border text-text-muted hover:text-text-secondary'
                      )}
                    >
                      {t}
                    </button>
                  ))}
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-sm text-text-secondary">Class</label>
                <select
                  value={assetClass}
                  onChange={e => setAssetClass(e.target.value)}
                  className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-text-primary text-sm focus:outline-none focus:border-gold-500/50"
                >
                  {ASSET_CLASSES.map(c => (
                    <option key={c.value} value={c.value}>{c.label}</option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <label className="text-sm text-text-secondary">Current Value ($)</label>
                <input
                  type="number"
                  value={assetValue}
                  onChange={e => setAssetValue(e.target.value)}
                  placeholder="e.g. 350000"
                  className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-text-primary font-mono text-sm focus:outline-none focus:border-gold-500/50"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-sm text-text-secondary">Notes (optional)</label>
                <input
                  type="text"
                  value={assetNotes}
                  onChange={e => setAssetNotes(e.target.value)}
                  placeholder="Optional notes..."
                  className="w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-text-primary text-sm focus:outline-none focus:border-gold-500/50"
                />
              </div>
            </div>

            <div className="flex gap-3 pt-1">
              <button
                onClick={() => { setShowModal(false); resetModal() }}
                className="flex-1 px-4 py-2 border border-border text-text-secondary hover:text-text-primary rounded-lg text-sm transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleAddAsset}
                disabled={savingAsset || !assetName || !assetValue}
                className="flex-1 flex items-center justify-center gap-2 px-4 py-2 bg-gold-500 hover:bg-gold-600 text-black font-semibold rounded-lg text-sm transition-colors disabled:opacity-60"
              >
                {savingAsset ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                Save
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
