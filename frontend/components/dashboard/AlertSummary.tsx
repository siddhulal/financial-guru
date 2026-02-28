'use client'

import Link from 'next/link'
import { Alert } from '@/lib/types'
import { getSeverityDot, getAlertTypeIcon, formatDate } from '@/lib/utils'
import { ArrowRight } from 'lucide-react'

interface Props {
  alerts: Alert[]
  count: number
}

export function AlertSummary({ alerts, count }: Props) {
  return (
    <div className="glass-card rounded-xl p-5 shadow-card">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-semibold text-text-primary">Recent Alerts</h3>
        {count > 0 && (
          <span className="text-xs bg-gold-500/10 text-gold-500 border border-gold-500/20 rounded-full px-2 py-0.5 font-num">
            {count} unread
          </span>
        )}
      </div>

      {alerts.length === 0 ? (
        <div className="text-center py-6">
          <p className="text-text-muted text-sm">No active alerts</p>
          <p className="text-text-muted/60 text-xs mt-1">All clear ✓</p>
        </div>
      ) : (
        <div className="space-y-2">
          {alerts.slice(0, 5).map((alert) => (
            <div key={alert.id} className="flex items-start gap-3 p-2.5 rounded-lg hover:bg-background-tertiary transition-colors">
              <div className={`w-2 h-2 rounded-full mt-1.5 flex-shrink-0 ${getSeverityDot(alert.severity)}`} />
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-1">
                  <span className="text-xs">{getAlertTypeIcon(alert.type)}</span>
                  <p className="text-xs font-medium text-text-primary truncate">{alert.title}</p>
                </div>
                <p className="text-xs text-text-muted truncate mt-0.5">{alert.message}</p>
              </div>
              <span className="text-xs text-text-muted flex-shrink-0">{formatDate(alert.createdAt)}</span>
            </div>
          ))}
        </div>
      )}

      <Link
        href="/alerts"
        className="flex items-center justify-center gap-1.5 mt-4 text-xs text-text-muted hover:text-gold-500 transition-colors"
      >
        View all alerts <ArrowRight className="w-3 h-3" />
      </Link>
    </div>
  )
}
