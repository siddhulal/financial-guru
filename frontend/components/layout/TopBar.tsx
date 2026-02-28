'use client'

import Link from 'next/link'
import { Bell, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { api } from '@/lib/api'

export function TopBar() {
  const [unreadCount, setUnreadCount] = useState(0)

  useEffect(() => {
    api.alerts.unreadCount()
      .then(d => setUnreadCount(d.count))
      .catch(() => {})
  }, [])

  return (
    <header className="h-16 border-b border-border bg-background-secondary/80 backdrop-blur-sm flex items-center px-6 gap-4 flex-shrink-0">
      {/* Search */}
      <div className="flex-1 max-w-md">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
          <input
            type="text"
            placeholder="Search transactions, merchants..."
            className="w-full bg-background-tertiary border border-border rounded-lg pl-9 pr-4 py-2 text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-gold-500/50 focus:ring-1 focus:ring-gold-500/20 transition-all"
          />
        </div>
      </div>

      {/* Right section */}
      <div className="flex items-center gap-3">
        {/* Alerts bell */}
        <Link
          href="/alerts"
          className="relative p-2 rounded-lg hover:bg-background-tertiary transition-colors"
        >
          <Bell className="w-5 h-5 text-text-secondary" />
          {unreadCount > 0 && (
            <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-gold-500 rounded-full text-[10px] font-bold text-black flex items-center justify-center">
              {unreadCount > 9 ? '9+' : unreadCount}
            </span>
          )}
        </Link>

        {/* Separator */}
        <div className="w-px h-6 bg-border" />

        {/* Local AI status */}
        <div className="flex items-center gap-2 text-xs text-text-muted">
          <div className="w-1.5 h-1.5 rounded-full bg-success" />
          <span>Ollama</span>
        </div>
      </div>
    </header>
  )
}
