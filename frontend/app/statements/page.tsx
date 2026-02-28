'use client'

import { useEffect, useState, useCallback } from 'react'
import { useDropzone } from 'react-dropzone'
import { api } from '@/lib/api'
import { Statement, Account } from '@/lib/types'
import { formatCurrency, formatDate, cn } from '@/lib/utils'
import { Upload, FileText, CheckCircle, XCircle, Clock, RefreshCw, Zap, Link2 } from 'lucide-react'

export default function StatementsPage() {
  const [statements, setStatements] = useState<Statement[]>([])
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [selectedAccount, setSelectedAccount] = useState('')
  const [uploadError, setUploadError] = useState('')

  const load = async () => {
    setLoading(true)
    try {
      const [stmts, accts] = await Promise.all([
        api.statements.list(),
        api.accounts.list(),
      ])
      setStatements(stmts)
      setAccounts(accts)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  // Auto-refresh to catch status updates
  useEffect(() => {
    const hasProcessing = statements.some(s => s.status === 'PROCESSING' || s.status === 'PENDING')
    if (!hasProcessing) return
    const interval = setInterval(load, 3000)
    return () => clearInterval(interval)
  }, [statements])

  const onDrop = useCallback(async (files: File[]) => {
    const file = files[0]
    if (!file) return
    setUploading(true)
    setUploadError('')
    try {
      await api.statements.upload(file, selectedAccount || undefined)
      await load()
    } catch (e: any) {
      setUploadError(e.message || 'Upload failed')
    } finally {
      setUploading(false)
    }
  }, [selectedAccount])

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { 'application/pdf': ['.pdf'] },
    maxFiles: 1,
    disabled: uploading,
  })

  const runAnalysis = async (id: string) => {
    await api.analysis.run(id)
    alert('AI analysis started! Check back in a minute.')
  }

  return (
    <div className="space-y-5 animate-slide-up">
      <div>
        <h1 className="text-xl font-semibold text-text-primary">Statements</h1>
        <p className="text-sm text-text-muted mt-0.5">Upload PDF bank statements for automatic parsing</p>
      </div>

      {/* Upload area */}
      <div className="glass-card rounded-xl p-5 shadow-card">
        <h2 className="text-sm font-semibold text-text-primary mb-4">Upload Statement</h2>

        {/* Account selector */}
        <div className="mb-3">
          <label className="text-xs text-text-muted mb-1 block">Associate with account (optional)</label>
          <select
            value={selectedAccount}
            onChange={e => setSelectedAccount(e.target.value)}
            className="bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:border-gold-500/50 transition-colors w-full max-w-xs"
          >
            <option value="">Auto-detect</option>
            {accounts.map(a => (
              <option key={a.id} value={a.id}>{a.name}</option>
            ))}
          </select>
        </div>

        {/* Dropzone */}
        <div
          {...getRootProps()}
          className={cn(
            'border-2 border-dashed rounded-xl p-10 text-center cursor-pointer transition-all',
            isDragActive
              ? 'border-gold-500 bg-gold-500/5'
              : 'border-border hover:border-gold-500/50 hover:bg-background-tertiary/50',
            uploading && 'opacity-50 cursor-not-allowed'
          )}
        >
          <input {...getInputProps()} />
          {uploading ? (
            <div>
              <RefreshCw className="w-8 h-8 text-gold-500 animate-spin mx-auto mb-3" />
              <p className="text-sm text-text-secondary">Uploading and processing...</p>
            </div>
          ) : isDragActive ? (
            <div>
              <Upload className="w-8 h-8 text-gold-500 mx-auto mb-3" />
              <p className="text-sm text-gold-500 font-medium">Drop it here!</p>
            </div>
          ) : (
            <div>
              <FileText className="w-8 h-8 text-text-muted mx-auto mb-3" />
              <p className="text-sm text-text-secondary mb-1">
                Drag & drop a PDF statement, or <span className="text-gold-500">browse</span>
              </p>
              <p className="text-xs text-text-muted">
                Supports Chase, Amex, BofA, Citi, Wells Fargo, and more
              </p>
            </div>
          )}
        </div>

        {uploadError && (
          <p className="mt-3 text-sm text-red-400 bg-red-400/10 rounded-lg px-3 py-2">{uploadError}</p>
        )}
      </div>

      {/* Statement list */}
      <div className="glass-card rounded-xl shadow-card overflow-hidden">
        <div className="px-5 py-4 border-b border-border">
          <h2 className="text-sm font-semibold text-text-primary">
            {statements.length} Statement{statements.length !== 1 ? 's' : ''}
          </h2>
        </div>

        {loading ? (
          <div className="text-center py-12 text-text-muted">Loading...</div>
        ) : statements.length === 0 ? (
          <div className="text-center py-12">
            <FileText className="w-10 h-10 text-text-muted/30 mx-auto mb-3" />
            <p className="text-text-muted text-sm">No statements yet</p>
          </div>
        ) : (
          <div className="divide-y divide-border">
            {statements.map((s) => (
              <div key={s.id} className="flex items-center gap-4 px-5 py-4 hover:bg-background-tertiary/50 transition-colors">
                <StatusIcon status={s.status} />

                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-text-primary truncate">{s.fileName}</p>
                  <div className="flex items-center gap-3 mt-0.5">
                    <span className="text-xs text-text-muted">
                      Uploaded {formatDate(s.createdAt)}
                    </span>
                    {s.startDate && s.endDate && (
                      <span className="text-xs text-text-muted">
                        {formatDate(s.startDate)} – {formatDate(s.endDate)}
                      </span>
                    )}
                  </div>
                  {s.account && (
                    <span className="text-xs text-gold-500/80">Linked: {s.account.name}</span>
                  )}
                  {(s.minimumPayment !== null || s.paymentDueDate !== null) && (
                    <div className="flex items-center gap-3 mt-1">
                      {s.minimumPayment !== null && (
                        <span className="text-xs text-text-muted">
                          Min Payment: <span className="text-text-secondary font-num">{formatCurrency(s.minimumPayment)}</span>
                        </span>
                      )}
                      {s.paymentDueDate !== null && (
                        <span className="text-xs text-text-muted">
                          Due: <span className="text-yellow-400 font-num">{formatDate(s.paymentDueDate)}</span>
                        </span>
                      )}
                    </div>
                  )}
                  {(s.ytdTotalFees !== null || s.ytdTotalInterest !== null) && (
                    <div className="flex items-center gap-3 mt-1">
                      {s.ytdTotalFees !== null && (
                        <span className="text-xs text-text-muted">
                          YTD Fees: <span className="text-text-secondary font-num">{formatCurrency(s.ytdTotalFees)}</span>
                        </span>
                      )}
                      {s.ytdTotalInterest !== null && (
                        <span className="text-xs text-text-muted">
                          YTD Interest: <span className="text-text-secondary font-num">{formatCurrency(s.ytdTotalInterest)}</span>
                        </span>
                      )}
                      {s.ytdYear && <span className="text-xs text-text-muted/60">{s.ytdYear}</span>}
                    </div>
                  )}
                  {s.errorMessage && (
                    <p className="text-xs text-red-400 mt-1 truncate">{s.errorMessage}</p>
                  )}
                </div>

                {s.closingBalance !== null && (
                  <div className="text-right flex-shrink-0">
                    <p className="text-sm font-num font-semibold text-text-primary">
                      {formatCurrency(s.closingBalance)}
                    </p>
                    <p className="text-xs text-text-muted">Closing balance</p>
                  </div>
                )}

                <div className="flex items-center gap-2 flex-shrink-0">
                  {s.status === 'COMPLETED' && (
                    <>
                      <button
                        onClick={() => runAnalysis(s.id)}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-xs border border-gold-500/30 text-gold-500 rounded-lg hover:bg-gold-500/10 transition-colors"
                        title="Run AI Analysis"
                      >
                        <Zap className="w-3 h-3" /> AI Analysis
                      </button>
                      <AssignAccountButton
                        statementId={s.id}
                        currentAccountId={s.account?.id}
                        accounts={accounts}
                        onAssigned={load}
                      />
                    </>
                  )}
                  {(s.status === 'FAILED' || s.status === 'COMPLETED') && (
                    <button
                      onClick={() => api.statements.reprocess(s.id).then(load)}
                      className="flex items-center gap-1.5 px-3 py-1.5 text-xs border border-border text-text-secondary rounded-lg hover:bg-background-tertiary transition-colors"
                      title="Re-parse this statement"
                    >
                      <RefreshCw className="w-3 h-3" /> Reprocess
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function AssignAccountButton({
  statementId, currentAccountId, accounts, onAssigned
}: {
  statementId: string
  currentAccountId: string | undefined
  accounts: Account[]
  onAssigned: () => void
}) {
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)

  const assign = async (accountId: string) => {
    setSaving(true)
    try {
      await api.statements.assignAccount(statementId, accountId)
      setOpen(false)
      onAssigned()
    } finally {
      setSaving(false)
    }
  }

  if (accounts.length === 0) return null

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className="flex items-center gap-1.5 px-3 py-1.5 text-xs border border-border text-text-secondary rounded-lg hover:bg-background-tertiary transition-colors"
        title="Assign to account"
      >
        <Link2 className="w-3 h-3" />
        {currentAccountId ? 'Reassign' : 'Assign Account'}
      </button>
      {open && (
        <div className="absolute right-0 top-full mt-1 w-52 bg-background-secondary border border-border rounded-lg shadow-xl z-10 py-1">
          {accounts.map(a => (
            <button
              key={a.id}
              disabled={saving}
              onClick={() => assign(a.id)}
              className="w-full text-left px-3 py-2 text-xs text-text-primary hover:bg-background-tertiary transition-colors truncate"
            >
              {a.name}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function StatusIcon({ status }: { status: string }) {
  switch (status) {
    case 'COMPLETED':
      return <CheckCircle className="w-5 h-5 text-success flex-shrink-0" />
    case 'FAILED':
      return <XCircle className="w-5 h-5 text-danger flex-shrink-0" />
    case 'PROCESSING':
      return <RefreshCw className="w-5 h-5 text-gold-500 animate-spin flex-shrink-0" />
    default:
      return <Clock className="w-5 h-5 text-text-muted flex-shrink-0" />
  }
}
