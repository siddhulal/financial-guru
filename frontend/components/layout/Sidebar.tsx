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
  Target,
  Lightbulb,
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
  { href: '/budget', label: 'Budget', icon: Target },
  { href: '/insights', label: 'Insights', icon: Lightbulb },
]

const insightsSubItems = [
  { href: '/insights/debt-payoff', label: 'Debt Payoff' },
  { href: '/insights/health-score', label: 'Health Score' },
  { href: '/insights/net-worth', label: 'Net Worth' },
  { href: '/insights/cash-flow', label: 'Cash Flow' },
  { href: '/insights/annual-review', label: 'Annual Review' },
]

export function Sidebar() {
  const pathname = usePathname()
  const isInsightsActive = pathname.startsWith('/insights')

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
      <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto">
        {navItems.map((item) => {
          const Icon = item.icon
          const isActive =
            item.href === '/insights'
              ? pathname === '/insights' || pathname.startsWith('/insights')
              : pathname === item.href ||
                (item.href !== '/' && pathname.startsWith(item.href))

          return (
            <div key={item.href}>
              <Link
                href={item.href}
                className={cn(
                  'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 group',
                  isActive
                    ? 'bg-gold-500/10 text-gold-500 border border-gold-500/20'
                    : 'text-text-secondary hover:text-text-primary hover:bg-background-tertiary'
                )}
              >
                <Icon
                  className={cn(
                    'w-4 h-4 flex-shrink-0 transition-colors',
                    isActive
                      ? 'text-gold-500'
                      : 'text-text-muted group-hover:text-text-secondary'
                  )}
                />
                <span className="flex-1">{item.label}</span>
                {isActive && (
                  <ChevronRight className="w-3 h-3 text-gold-500/50" />
                )}
              </Link>

              {/* Insights sub-items */}
              {item.href === '/insights' && isInsightsActive && (
                <div className="mt-0.5 space-y-0.5">
                  {insightsSubItems.map((sub) => {
                    const isSubActive = pathname === sub.href
                    return (
                      <Link
                        key={sub.href}
                        href={sub.href}
                        className={cn(
                          'flex items-center gap-3 pl-9 pr-3 py-2 rounded-lg text-xs font-medium transition-all duration-150',
                          isSubActive
                            ? 'bg-gold-500/10 text-gold-400 border border-gold-500/20'
                            : 'text-text-muted hover:text-text-secondary hover:bg-background-tertiary'
                        )}
                      >
                        <span className="flex-1">{sub.label}</span>
                        {isSubActive && (
                          <ChevronRight className="w-3 h-3 text-gold-500/50" />
                        )}
                      </Link>
                    )
                  })}
                </div>
              )}
            </div>
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
          Your data stays on device
        </p>
      </div>
    </aside>
  )
}
