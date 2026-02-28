'use client'

import { useState, useEffect, useRef } from 'react'
import { Search, X, CreditCard, Receipt, Store } from 'lucide-react'
import { api } from '@/lib/api'
import { SearchResult } from '@/lib/types'
import Link from 'next/link'

export function GlobalSearch() {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<SearchResult | null>(null)
  const [loading, setLoading] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  // Cmd+K / Ctrl+K opens modal
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault()
        setOpen(o => !o)
      }
      if (e.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  useEffect(() => {
    if (open) setTimeout(() => inputRef.current?.focus(), 50)
    else { setQuery(''); setResults(null) }
  }, [open])

  // Debounced search
  useEffect(() => {
    if (!query.trim() || query.length < 2) { setResults(null); return }
    const t = setTimeout(() => {
      setLoading(true)
      api.search(query).then(setResults).catch(() => {}).finally(() => setLoading(false))
    }, 300)
    return () => clearTimeout(t)
  }, [query])

  if (!open) return (
    <button
      onClick={() => setOpen(true)}
      className="flex items-center gap-2 px-3 py-1.5 bg-background-tertiary border border-border rounded-lg text-text-muted text-sm hover:border-gold-500/40 transition-colors"
    >
      <Search className="w-3.5 h-3.5" />
      <span>Search</span>
      <kbd className="ml-2 px-1.5 py-0.5 text-xs bg-background rounded border border-border">⌘K</kbd>
    </button>
  )

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-[15vh]">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setOpen(false)} />

      {/* Modal */}
      <div className="relative w-full max-w-xl mx-4 bg-background-secondary border border-border rounded-2xl shadow-2xl overflow-hidden">
        {/* Search input */}
        <div className="flex items-center gap-3 px-4 py-3 border-b border-border">
          <Search className="w-4 h-4 text-text-muted flex-shrink-0" />
          <input
            ref={inputRef}
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="Search transactions, merchants, accounts..."
            className="flex-1 bg-transparent text-text-primary placeholder-text-muted outline-none text-sm"
          />
          {loading && <div className="w-4 h-4 border-t-2 border-gold-500 rounded-full animate-spin" />}
          <button onClick={() => setOpen(false)}>
            <X className="w-4 h-4 text-text-muted hover:text-text-primary" />
          </button>
        </div>

        {/* Results */}
        <div className="max-h-96 overflow-y-auto">
          {!results && !loading && (
            <div className="px-4 py-6 text-center text-text-muted text-sm">
              Start typing to search...
            </div>
          )}

          {results && results.totalResults === 0 && (
            <div className="px-4 py-6 text-center text-text-muted text-sm">
              No results for "{query}"
            </div>
          )}

          {results && results.transactions.length > 0 && (
            <div className="px-4 py-2">
              <p className="text-xs font-semibold text-text-muted uppercase tracking-wider py-2">Transactions</p>
              {results.transactions.map(t => (
                <Link key={t.id} href={`/transactions?search=${encodeURIComponent(t.merchantName || '')}`}
                  onClick={() => setOpen(false)}
                  className="flex items-center gap-3 py-2 px-2 rounded-lg hover:bg-background-tertiary transition-colors"
                >
                  <Receipt className="w-4 h-4 text-text-muted flex-shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-text-primary truncate">{t.merchantName || t.description}</p>
                    <p className="text-xs text-text-muted">{t.category} · {t.transactionDate}</p>
                  </div>
                  <span className="text-sm font-mono text-text-primary">${t.amount.toFixed(2)}</span>
                </Link>
              ))}
            </div>
          )}

          {results && results.accounts.length > 0 && (
            <div className="px-4 py-2 border-t border-border">
              <p className="text-xs font-semibold text-text-muted uppercase tracking-wider py-2">Accounts</p>
              {results.accounts.map(a => (
                <Link key={a.id} href={`/accounts/${a.id}`}
                  onClick={() => setOpen(false)}
                  className="flex items-center gap-3 py-2 px-2 rounded-lg hover:bg-background-tertiary transition-colors"
                >
                  <CreditCard className="w-4 h-4 text-text-muted flex-shrink-0" />
                  <div className="flex-1">
                    <p className="text-sm text-text-primary">{a.name}</p>
                    <p className="text-xs text-text-muted">{a.institution} · {a.type}</p>
                  </div>
                  {a.currentBalance !== null && (
                    <span className="text-sm font-mono text-text-primary">${a.currentBalance?.toFixed(2)}</span>
                  )}
                </Link>
              ))}
            </div>
          )}

          {results && results.merchants.length > 0 && (
            <div className="px-4 py-2 border-t border-border">
              <p className="text-xs font-semibold text-text-muted uppercase tracking-wider py-2">Merchants</p>
              {results.merchants.map(m => (
                <Link key={m} href={`/transactions?search=${encodeURIComponent(m)}`}
                  onClick={() => setOpen(false)}
                  className="flex items-center gap-3 py-2 px-2 rounded-lg hover:bg-background-tertiary transition-colors"
                >
                  <Store className="w-4 h-4 text-text-muted flex-shrink-0" />
                  <p className="text-sm text-text-primary">{m}</p>
                </Link>
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-4 py-2 border-t border-border flex items-center gap-4 text-xs text-text-muted">
          <span><kbd className="px-1 bg-background border border-border rounded">↵</kbd> Select</span>
          <span><kbd className="px-1 bg-background border border-border rounded">Esc</kbd> Close</span>
        </div>
      </div>
    </div>
  )
}
