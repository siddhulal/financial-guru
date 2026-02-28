'use client'

import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { Alert, AlertSeverity } from '@/lib/types'
import {
  getSeverityColor, getSeverityDot, getAlertTypeIcon,
  formatDate, cn
} from '@/lib/utils'
import { CheckCircle, Trash2, Eye, Filter } from 'lucide-react'

export default function AlertsPage() {
  const [alerts, setAlerts] = useState<Alert[]>([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState<'ALL' | AlertSeverity>('ALL')

  const load = async () => {
    setLoading(true)
    try {
      setAlerts(await api.alerts.list())
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const markRead = async (id: string) => {
    await api.alerts.markRead(id)
    setAlerts(prev => prev.map(a => a.id === id ? { ...a, isRead: true } : a))
  }

  const resolve = async (id: string) => {
    await api.alerts.markResolved(id)
    setAlerts(prev => prev.filter(a => a.id !== id))
  }

  const dismiss = async (id: string) => {
    await api.alerts.delete(id)
    setAlerts(prev => prev.filter(a => a.id !== id))
  }

  const filtered = filter === 'ALL'
    ? alerts
    : alerts.filter(a => a.severity === filter)

  const counts = {
    CRITICAL: alerts.filter(a => a.severity === 'CRITICAL').length,
    HIGH: alerts.filter(a => a.severity === 'HIGH').length,
    MEDIUM: alerts.filter(a => a.severity === 'MEDIUM').length,
    LOW: alerts.filter(a => a.severity === 'LOW').length,
  }

  return (
    <div className="space-y-5 animate-slide-up">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-text-primary">Alert Center</h1>
          <p className="text-sm text-text-muted mt-0.5">
            {alerts.filter(a => !a.isRead).length} unread · {alerts.length} total
          </p>
        </div>
        <button
          onClick={() => Promise.all(alerts.map(a => a.isRead ? null : api.alerts.markRead(a.id))).then(load)}
          className="flex items-center gap-2 px-3 py-2 border border-border text-text-secondary text-sm rounded-lg hover:bg-background-tertiary transition-colors"
        >
          <Eye className="w-4 h-4" /> Mark All Read
        </button>
      </div>

      {/* Severity filter chips */}
      <div className="flex items-center gap-2 flex-wrap">
        {(['ALL', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const).map((s) => (
          <button
            key={s}
            onClick={() => setFilter(s)}
            className={cn(
              'px-3 py-1.5 rounded-full text-xs font-medium border transition-all',
              filter === s
                ? s === 'ALL'
                  ? 'bg-text-primary text-background border-text-primary'
                  : getSeverityColor(s as AlertSeverity)
                : 'border-border text-text-muted hover:border-border/80'
            )}
          >
            {s === 'ALL' ? 'All' : s}
            {s !== 'ALL' && counts[s] > 0 && (
              <span className="ml-1.5 font-num">{counts[s]}</span>
            )}
          </button>
        ))}
      </div>

      {/* Alerts list */}
      {loading ? (
        <div className="text-center py-16 text-text-muted">Loading alerts...</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16">
          <CheckCircle className="w-12 h-12 text-success/30 mx-auto mb-3" />
          <p className="text-text-muted">No alerts to show</p>
          <p className="text-text-muted/60 text-sm mt-1">
            {filter !== 'ALL' ? 'Try a different severity filter' : "You're all clear!"}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map((alert) => (
            <AlertCard
              key={alert.id}
              alert={alert}
              onRead={() => markRead(alert.id)}
              onResolve={() => resolve(alert.id)}
              onDismiss={() => dismiss(alert.id)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function AlertCard({
  alert,
  onRead,
  onResolve,
  onDismiss,
}: {
  alert: Alert
  onRead: () => void
  onResolve: () => void
  onDismiss: () => void
}) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div
      className={cn(
        'glass-card rounded-xl p-4 shadow-card border transition-all',
        !alert.isRead ? 'border-l-4' : 'border border-border',
        !alert.isRead && alert.severity === 'CRITICAL' ? 'border-l-red-500' :
        !alert.isRead && alert.severity === 'HIGH' ? 'border-l-orange-500' :
        !alert.isRead && alert.severity === 'MEDIUM' ? 'border-l-yellow-500' :
        !alert.isRead ? 'border-l-blue-500' : ''
      )}
    >
      <div className="flex items-start gap-3">
        {/* Severity indicator */}
        <div className={`w-2 h-2 rounded-full mt-1.5 flex-shrink-0 ${getSeverityDot(alert.severity)}`} />

        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-2">
            <div className="flex-1">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-base leading-none">{getAlertTypeIcon(alert.type)}</span>
                <h3 className={cn(
                  'text-sm font-semibold',
                  alert.isRead ? 'text-text-secondary' : 'text-text-primary'
                )}>
                  {alert.title}
                </h3>
                <span className={cn(
                  'text-xs px-2 py-0.5 rounded-full border',
                  getSeverityColor(alert.severity)
                )}>
                  {alert.severity}
                </span>
                {!alert.isRead && (
                  <span className="text-xs bg-gold-500 text-black rounded-full px-1.5 py-0.5 font-semibold">
                    NEW
                  </span>
                )}
              </div>
              <p className="text-sm text-text-secondary mt-1">{alert.message}</p>
              {alert.accountName && (
                <p className="text-xs text-text-muted mt-1">📊 {alert.accountName}</p>
              )}
            </div>
            <span className="text-xs text-text-muted flex-shrink-0">{formatDate(alert.createdAt)}</span>
          </div>

          {/* AI explanation (expandable) */}
          {alert.aiExplanation && (
            <div className="mt-2">
              <button
                onClick={() => setExpanded(!expanded)}
                className="text-xs text-gold-500 hover:text-gold-400 transition-colors"
              >
                {expanded ? '▲ Hide AI insight' : '▼ Show AI insight'}
              </button>
              {expanded && (
                <div className="mt-2 p-3 bg-background-tertiary rounded-lg border border-border">
                  <p className="text-xs text-text-secondary leading-relaxed">
                    🤖 {alert.aiExplanation}
                  </p>
                </div>
              )}
            </div>
          )}

          {/* Actions */}
          <div className="flex items-center gap-2 mt-3">
            {!alert.isRead && (
              <button
                onClick={onRead}
                className="flex items-center gap-1 text-xs text-text-muted hover:text-text-primary transition-colors"
              >
                <Eye className="w-3 h-3" /> Mark read
              </button>
            )}
            <button
              onClick={onResolve}
              className="flex items-center gap-1 text-xs text-success hover:text-success/80 transition-colors"
            >
              <CheckCircle className="w-3 h-3" /> Resolve
            </button>
            <button
              onClick={onDismiss}
              className="flex items-center gap-1 text-xs text-text-muted hover:text-danger transition-colors"
            >
              <Trash2 className="w-3 h-3" /> Dismiss
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
