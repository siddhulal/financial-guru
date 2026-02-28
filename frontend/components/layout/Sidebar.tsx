'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import {
  LayoutDashboard,
  CreditCard,
  Receipt,
  FileText,
  Bell,
  RefreshCw,
  MessageSquare,
  Shield,
  ChevronRight,
} from 'lucide-react'
import { cn } from '@/lib/utils'

const navItems = [
  { href: '/', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/accounts', label: 'Accounts', icon: CreditCard },
  { href: '/transactions', label: 'Transactions', icon: Receipt },
  { href: '/statements', label: 'Statements', icon: FileText },
  { href: '/alerts', label: 'Alerts', icon: Bell },
  { href: '/subscriptions', label: 'Subscriptions', icon: RefreshCw },
  { href: '/advisor', label: 'AI Advisor', icon: MessageSquare },
]

export function Sidebar() {
  const pathname = usePathname()

  return (
    <aside className="w-60 flex-shrink-0 bg-background-secondary border-r border-border flex flex-col">
      {/* Logo */}
      <div className="h-16 flex items-center px-5 border-b border-border">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-gold-gradient flex items-center justify-center shadow-gold-glow">
            <Shield className="w-4 h-4 text-black" />
          </div>
          <div>
            <span className="text-sm font-semibold text-text-primary tracking-tight">
              Financial
            </span>
            <span className="text-sm font-semibold text-gold-500 tracking-tight">
              Guru
            </span>
          </div>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 py-4 space-y-0.5">
        {navItems.map((item) => {
          const Icon = item.icon
          const isActive = pathname === item.href ||
            (item.href !== '/' && pathname.startsWith(item.href))

          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 group',
                isActive
                  ? 'bg-gold-500/10 text-gold-500 border border-gold-500/20'
                  : 'text-text-secondary hover:text-text-primary hover:bg-background-tertiary'
              )}
            >
              <Icon className={cn(
                'w-4 h-4 flex-shrink-0 transition-colors',
                isActive ? 'text-gold-500' : 'text-text-muted group-hover:text-text-secondary'
              )} />
              <span className="flex-1">{item.label}</span>
              {isActive && (
                <ChevronRight className="w-3 h-3 text-gold-500/50" />
              )}
            </Link>
          )
        })}
      </nav>

      {/* Footer */}
      <div className="p-4 border-t border-border">
        <div className="flex items-center gap-2 px-2">
          <div className="w-2 h-2 rounded-full bg-success animate-pulse" />
          <span className="text-xs text-text-muted">All systems local</span>
        </div>
        <p className="text-xs text-text-muted/60 mt-1 px-2">
          🔒 Your data stays on device
        </p>
      </div>
    </aside>
  )
}
