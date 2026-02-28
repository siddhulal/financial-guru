'use client'

import { useEffect, useRef, useState } from 'react'
import { api } from '@/lib/api'
import { Send, Bot, User, Sparkles, RefreshCw } from 'lucide-react'
import { cn } from '@/lib/utils'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
}

export default function AdvisorPage() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content: "Hi! I'm your personal financial advisor powered by a local AI model. I have access to your account balances, spending patterns, and subscription data. Ask me anything about your finances — I'll keep everything private and on your device.",
      timestamp: new Date(),
    }
  ])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [loadingSeconds, setLoadingSeconds] = useState(0)
  const [suggestions, setSuggestions] = useState<string[]>([])
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    api.chat.suggestions().then(setSuggestions).catch(() => {})
  }, [])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const sendMessage = async (text = input.trim()) => {
    if (!text || loading) return

    const userMsg: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: text,
      timestamp: new Date(),
    }

    setMessages(prev => [...prev, userMsg])
    setInput('')
    setLoading(true)
    setLoadingSeconds(0)
    const timer = setInterval(() => setLoadingSeconds(s => s + 1), 1000)

    try {
      const controller = new AbortController()
      const timeout = setTimeout(() => controller.abort(), 120_000)
      const res = await api.chat.send(text)
      clearTimeout(timeout)
      const assistantMsg: Message = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: res.response || "I couldn't get a response. Make sure Ollama is running with llama3.1:13b.",
        timestamp: new Date(),
      }
      setMessages(prev => [...prev, assistantMsg])
    } catch (e) {
      const errMsg: Message = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: '⚠️ Could not reach the AI. Make sure Ollama is running: `ollama serve` and the model is loaded: `ollama pull gemma3:4b`',
        timestamp: new Date(),
      }
      setMessages(prev => [...prev, errMsg])
    } finally {
      clearInterval(timer)
      setLoading(false)
      setLoadingSeconds(0)
      inputRef.current?.focus()
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const clearChat = () => {
    setMessages([{
      id: 'welcome',
      role: 'assistant',
      content: "Chat cleared. How can I help you with your finances?",
      timestamp: new Date(),
    }])
  }

  return (
    <div className="flex flex-col h-[calc(100vh-7rem)] animate-slide-up">
      {/* Header */}
      <div className="flex items-center justify-between mb-4 flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-gold-500/20 to-electric-indigo/20 border border-gold-500/30 flex items-center justify-center">
            <Sparkles className="w-4 h-4 text-gold-500" />
          </div>
          <div>
            <h1 className="text-base font-semibold text-text-primary">AI Financial Advisor</h1>
            <p className="text-xs text-text-muted">Powered by Ollama · 100% local</p>
          </div>
        </div>
        <button
          onClick={clearChat}
          className="flex items-center gap-1.5 px-3 py-1.5 border border-border text-text-muted text-xs rounded-lg hover:bg-background-tertiary transition-colors"
        >
          <RefreshCw className="w-3 h-3" /> Clear chat
        </button>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto space-y-4 pr-2">
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={cn(
              'flex gap-3',
              msg.role === 'user' ? 'justify-end' : 'justify-start'
            )}
          >
            {msg.role === 'assistant' && (
              <div className="w-7 h-7 rounded-lg bg-gold-500/10 border border-gold-500/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                <Bot className="w-3.5 h-3.5 text-gold-500" />
              </div>
            )}

            <div className={cn(
              'max-w-[75%] rounded-2xl px-4 py-3 text-sm leading-relaxed',
              msg.role === 'user'
                ? 'bg-gold-500/10 border border-gold-500/20 text-text-primary rounded-tr-sm'
                : 'glass-card border border-border text-text-secondary rounded-tl-sm'
            )}>
              {/* Render markdown-like formatting */}
              <MessageContent content={msg.content} />
            </div>

            {msg.role === 'user' && (
              <div className="w-7 h-7 rounded-lg bg-background-tertiary border border-border flex items-center justify-center flex-shrink-0 mt-0.5">
                <User className="w-3.5 h-3.5 text-text-muted" />
              </div>
            )}
          </div>
        ))}

        {loading && (
          <div className="flex gap-3">
            <div className="w-7 h-7 rounded-lg bg-gold-500/10 border border-gold-500/20 flex items-center justify-center flex-shrink-0">
              <Bot className="w-3.5 h-3.5 text-gold-500" />
            </div>
            <div className="glass-card border border-border rounded-2xl rounded-tl-sm px-4 py-3">
              <div className="flex items-center gap-2">
                <div className="flex gap-1">
                  {[0, 1, 2].map(i => (
                    <div
                      key={i}
                      className="w-1.5 h-1.5 rounded-full bg-gold-500 animate-pulse"
                      style={{ animationDelay: `${i * 200}ms` }}
                    />
                  ))}
                </div>
                {loadingSeconds > 3 && (
                  <span className="text-xs text-text-muted">
                    Thinking... {loadingSeconds}s {loadingSeconds > 15 ? '(local AI can take 30-60s)' : ''}
                  </span>
                )}
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Quick prompts */}
      {messages.length <= 1 && suggestions.length > 0 && (
        <div className="flex-shrink-0 mt-3 mb-3">
          <p className="text-xs text-text-muted mb-2">Suggested questions:</p>
          <div className="flex flex-wrap gap-2">
            {suggestions.slice(0, 5).map((s) => (
              <button
                key={s}
                onClick={() => sendMessage(s)}
                className="text-xs px-3 py-1.5 border border-border rounded-lg text-text-secondary hover:border-gold-500/30 hover:text-text-primary hover:bg-background-tertiary transition-all"
              >
                {s}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Input */}
      <div className="flex-shrink-0 mt-3">
        <div className="flex gap-2 items-end">
          <div className="flex-1 glass-card border border-border rounded-xl overflow-hidden focus-within:border-gold-500/50 transition-colors">
            <textarea
              ref={inputRef}
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Ask about your finances... (Enter to send, Shift+Enter for new line)"
              rows={1}
              className="w-full bg-transparent px-4 py-3 text-sm text-text-primary placeholder:text-text-muted resize-none focus:outline-none min-h-[44px] max-h-[120px]"
              style={{ height: 'auto' }}
              onInput={e => {
                const t = e.target as HTMLTextAreaElement
                t.style.height = 'auto'
                t.style.height = Math.min(t.scrollHeight, 120) + 'px'
              }}
            />
          </div>
          <button
            onClick={() => sendMessage()}
            disabled={!input.trim() || loading}
            className="w-10 h-10 flex-shrink-0 rounded-xl bg-gold-500 hover:bg-gold-600 disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center transition-colors"
          >
            <Send className="w-4 h-4 text-black" />
          </button>
        </div>
        <p className="text-xs text-text-muted mt-2 text-center">
          🔒 Your financial data never leaves your device
        </p>
      </div>
    </div>
  )
}

function MessageContent({ content }: { content: string }) {
  // Simple markdown-ish rendering
  const lines = content.split('\n')
  return (
    <div className="space-y-1">
      {lines.map((line, i) => {
        if (line.startsWith('**') && line.endsWith('**')) {
          return <p key={i} className="font-semibold text-text-primary">{line.slice(2, -2)}</p>
        }
        if (line.startsWith('- ') || line.startsWith('• ')) {
          return <p key={i} className="pl-3">· {line.slice(2)}</p>
        }
        if (line.startsWith('#')) {
          return <p key={i} className="font-semibold text-text-primary">{line.replace(/^#+\s/, '')}</p>
        }
        if (line === '') return <br key={i} />
        return <p key={i}>{line}</p>
      })}
    </div>
  )
}
