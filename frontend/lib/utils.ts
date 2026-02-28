import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'
import { AlertSeverity, AlertType } from './types'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatCurrency(amount: number | null | undefined, compact = false): string {
  if (amount === null || amount === undefined) return '$—'
  const formatter = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: compact && Math.abs(amount) >= 10000 ? 'compact' : 'standard',
    maximumFractionDigits: compact && Math.abs(amount) >= 10000 ? 1 : 2,
  })
  return formatter.format(amount)
}

export function formatPercent(value: number | null | undefined, decimals = 1): string {
  if (value === null || value === undefined) return '—%'
  return `${value.toFixed(decimals)}%`
}

export function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return '—'
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

export function formatDateShort(dateStr: string | null | undefined): string {
  if (!dateStr) return '—'
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

export function formatMonthYear(monthStr: string): string {
  const [year, month] = monthStr.split('-')
  const date = new Date(parseInt(year), parseInt(month) - 1, 1)
  return date.toLocaleDateString('en-US', { month: 'short', year: '2-digit' })
}

export function getSeverityColor(severity: AlertSeverity): string {
  switch (severity) {
    case 'CRITICAL': return 'text-red-400 bg-red-400/10 border-red-400/20'
    case 'HIGH': return 'text-orange-400 bg-orange-400/10 border-orange-400/20'
    case 'MEDIUM': return 'text-yellow-400 bg-yellow-400/10 border-yellow-400/20'
    case 'LOW': return 'text-blue-400 bg-blue-400/10 border-blue-400/20'
    default: return 'text-gray-400 bg-gray-400/10 border-gray-400/20'
  }
}

export function getSeverityDot(severity: AlertSeverity): string {
  switch (severity) {
    case 'CRITICAL': return 'bg-red-500'
    case 'HIGH': return 'bg-orange-500'
    case 'MEDIUM': return 'bg-yellow-500'
    case 'LOW': return 'bg-blue-500'
    default: return 'bg-gray-500'
  }
}

export function getAlertTypeIcon(type: AlertType): string {
  switch (type) {
    case 'DUE_DATE': return '📅'
    case 'APR_EXPIRY': return '⚡'
    case 'DUPLICATE_CHARGE': return '⚠️'
    case 'ANOMALY': return '🔍'
    case 'SUBSCRIPTION': return '🔄'
    case 'HIGH_UTILIZATION': return '📊'
    case 'OVERCHARGE': return '💸'
    case 'UNUSUAL_MERCHANT': return '🏪'
    case 'LARGE_TRANSACTION': return '💳'
    default: return '🔔'
  }
}

export function getUtilizationColor(percent: number): string {
  if (percent >= 70) return 'text-red-400'
  if (percent >= 30) return 'text-yellow-400'
  return 'text-green-400'
}

export function getUtilizationBarColor(percent: number): string {
  if (percent >= 70) return 'bg-red-500'
  if (percent >= 30) return 'bg-yellow-500'
  return 'bg-green-500'
}

export function getCategoryEmoji(category: string): string {
  const map: Record<string, string> = {
    Dining: '🍽️',
    Groceries: '🛒',
    Shopping: '🛍️',
    Travel: '✈️',
    Gas: '⛽',
    Entertainment: '🎬',
    Utilities: '💡',
    Healthcare: '🏥',
    Subscriptions: '📱',
    Education: '📚',
    'Personal Care': '💆',
    Home: '🏠',
    Automotive: '🚗',
    Insurance: '🛡️',
    Investments: '📈',
    Fees: '💰',
    Other: '📦',
  }
  return map[category] || '💳'
}

export const CATEGORY_COLORS = [
  '#F59E0B', '#3B82F6', '#10B981', '#8B5CF6',
  '#EF4444', '#6366F1', '#EC4899', '#14B8A6',
]
