'use client'

import { useState, useEffect } from 'react'
import {
  User, Briefcase, Heart, ChevronDown, ChevronUp,
  Save, Loader2, CheckCircle, DollarSign, MapPin,
} from 'lucide-react'
import { api } from '@/lib/api'
import { LifeProfile } from '@/lib/types'
import { formatCurrency } from '@/lib/utils'

function Section({
  title, icon: Icon, children, defaultOpen = true,
}: {
  title: string
  icon: React.ElementType
  children: React.ReactNode
  defaultOpen?: boolean
}) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <div className="bg-background-secondary border border-border rounded-xl overflow-hidden">
      <button
        onClick={() => setOpen(o => !o)}
        className="w-full flex items-center justify-between px-6 py-4 hover:bg-background-tertiary transition-colors"
      >
        <div className="flex items-center gap-3">
          <Icon className="w-5 h-5 text-gold-500" />
          <span className="text-sm font-semibold text-text-primary">{title}</span>
        </div>
        {open ? <ChevronUp className="w-4 h-4 text-text-muted" /> : <ChevronDown className="w-4 h-4 text-text-muted" />}
      </button>
      {open && <div className="px-6 pb-6 space-y-4">{children}</div>}
    </div>
  )
}

function Field({
  label, children,
}: {
  label: string
  children: React.ReactNode
}) {
  return (
    <div className="space-y-1">
      <label className="text-xs font-medium text-text-muted uppercase tracking-wider">{label}</label>
      {children}
    </div>
  )
}

const inputCls = 'w-full bg-background-tertiary border border-border rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:border-gold-500/50 transition-colors'
const selectCls = inputCls

export default function ProfilePage() {
  const [profile, setProfile] = useState<LifeProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  // Form state mirrors LifeProfile fields
  const [form, setForm] = useState({
    firstName: '',
    birthYear: '',
    city: '',
    state: '',
    jobTitle: '',
    company: '',
    industry: '',
    employmentType: 'FULL_TIME',
    yearsAtCurrentJob: '',
    totalYearsExperience: '',
    annualSalary: '',
    annualBonus: '',
    equityAnnualValue: '',
    skills: '',
    isMarried: false,
    spouseEmployed: false,
    spouseJobTitle: '',
    spouseAnnualIncome: '',
    numberOfKids: '0',
    kidsAges: '',
    notes: '',
  })

  useEffect(() => {
    api.lifeProfile.get().then(p => {
      setProfile(p)
      setForm({
        firstName: p.firstName ?? '',
        birthYear: p.birthYear?.toString() ?? '',
        city: p.city ?? '',
        state: p.state ?? '',
        jobTitle: p.jobTitle ?? '',
        company: p.company ?? '',
        industry: p.industry ?? '',
        employmentType: p.employmentType ?? 'FULL_TIME',
        yearsAtCurrentJob: p.yearsAtCurrentJob?.toString() ?? '',
        totalYearsExperience: p.totalYearsExperience?.toString() ?? '',
        annualSalary: p.annualSalary?.toString() ?? '',
        annualBonus: p.annualBonus?.toString() ?? '',
        equityAnnualValue: p.equityAnnualValue?.toString() ?? '',
        skills: p.skills ?? '',
        isMarried: p.isMarried ?? false,
        spouseEmployed: p.spouseEmployed ?? false,
        spouseJobTitle: p.spouseJobTitle ?? '',
        spouseAnnualIncome: p.spouseAnnualIncome?.toString() ?? '',
        numberOfKids: p.numberOfKids?.toString() ?? '0',
        kidsAges: p.kidsAges ?? '',
        notes: p.notes ?? '',
      })
      setLoading(false)
    }).catch(() => setLoading(false))
  }, [])

  const set = (key: string, value: string | boolean) =>
    setForm(f => ({ ...f, [key]: value }))

  const householdIncome = (() => {
    const salary = parseFloat(form.annualSalary) || 0
    const bonus = parseFloat(form.annualBonus) || 0
    const spouse = form.spouseEmployed ? (parseFloat(form.spouseAnnualIncome) || 0) : 0
    return salary + bonus + spouse
  })()

  const currentYear = new Date().getFullYear()
  const age = form.birthYear ? currentYear - parseInt(form.birthYear) : null

  const handleSave = async () => {
    setSaving(true)
    setSaved(false)
    try {
      const payload: Record<string, unknown> = {
        firstName: form.firstName || null,
        birthYear: form.birthYear ? parseInt(form.birthYear) : null,
        city: form.city || null,
        state: form.state || null,
        jobTitle: form.jobTitle || null,
        company: form.company || null,
        industry: form.industry || null,
        employmentType: form.employmentType,
        yearsAtCurrentJob: form.yearsAtCurrentJob ? parseInt(form.yearsAtCurrentJob) : null,
        totalYearsExperience: form.totalYearsExperience ? parseInt(form.totalYearsExperience) : null,
        annualSalary: form.annualSalary ? parseFloat(form.annualSalary) : null,
        annualBonus: form.annualBonus ? parseFloat(form.annualBonus) : null,
        equityAnnualValue: form.equityAnnualValue ? parseFloat(form.equityAnnualValue) : null,
        skills: form.skills || null,
        isMarried: form.isMarried,
        spouseEmployed: form.spouseEmployed,
        spouseJobTitle: form.spouseJobTitle || null,
        spouseAnnualIncome: form.spouseAnnualIncome ? parseFloat(form.spouseAnnualIncome) : null,
        numberOfKids: parseInt(form.numberOfKids) || 0,
        kidsAges: form.kidsAges || null,
        notes: form.notes || null,
      }
      const updated = await api.lifeProfile.update(payload as Partial<LifeProfile>)
      setProfile(updated)
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
    } catch (e) {
      console.error(e)
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 className="w-8 h-8 text-gold-500 animate-spin" />
      </div>
    )
  }

  return (
    <div className="p-6 max-w-3xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text-primary">My Life Profile</h1>
          <p className="text-sm text-text-muted mt-1">
            Powers your Career Advisor and Life Coach with personal context
          </p>
        </div>
        <button
          onClick={handleSave}
          disabled={saving}
          className="flex items-center gap-2 px-5 py-2.5 bg-gold-500 text-black text-sm font-semibold rounded-lg hover:bg-gold-400 transition-colors disabled:opacity-50"
        >
          {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : saved ? <CheckCircle className="w-4 h-4" /> : <Save className="w-4 h-4" />}
          {saved ? 'Saved!' : 'Save Profile'}
        </button>
      </div>

      {/* Personal */}
      <Section title="Personal" icon={User}>
        <div className="grid grid-cols-2 gap-4">
          <Field label="First Name">
            <input
              className={inputCls}
              value={form.firstName}
              onChange={e => set('firstName', e.target.value)}
              placeholder="Your name"
            />
          </Field>
          <Field label={`Birth Year${age ? ` — Age: ${age}` : ''}`}>
            <input
              className={inputCls}
              type="number"
              value={form.birthYear}
              onChange={e => set('birthYear', e.target.value)}
              placeholder="e.g. 1990"
              min={1940}
              max={2010}
            />
          </Field>
          <Field label="City">
            <input
              className={inputCls}
              value={form.city}
              onChange={e => set('city', e.target.value)}
              placeholder="San Francisco"
            />
          </Field>
          <Field label="State">
            <input
              className={inputCls}
              value={form.state}
              onChange={e => set('state', e.target.value)}
              placeholder="CA"
              maxLength={2}
            />
          </Field>
        </div>
      </Section>

      {/* Career */}
      <Section title="Career" icon={Briefcase}>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Job Title">
            <input
              className={inputCls}
              value={form.jobTitle}
              onChange={e => set('jobTitle', e.target.value)}
              placeholder="Senior Software Engineer"
            />
          </Field>
          <Field label="Company">
            <input
              className={inputCls}
              value={form.company}
              onChange={e => set('company', e.target.value)}
              placeholder="Acme Corp"
            />
          </Field>
          <Field label="Industry">
            <input
              className={inputCls}
              value={form.industry}
              onChange={e => set('industry', e.target.value)}
              placeholder="Technology"
            />
          </Field>
          <Field label="Employment Type">
            <select
              className={selectCls}
              value={form.employmentType}
              onChange={e => set('employmentType', e.target.value)}
            >
              <option value="FULL_TIME">Full-Time</option>
              <option value="PART_TIME">Part-Time</option>
              <option value="CONTRACT">Contract</option>
              <option value="SELF_EMPLOYED">Self-Employed</option>
              <option value="FREELANCE">Freelance</option>
            </select>
          </Field>
          <Field label="Years at Current Job">
            <input
              className={inputCls}
              type="number"
              value={form.yearsAtCurrentJob}
              onChange={e => set('yearsAtCurrentJob', e.target.value)}
              placeholder="2"
              min={0}
            />
          </Field>
          <Field label="Total Years of Experience">
            <input
              className={inputCls}
              type="number"
              value={form.totalYearsExperience}
              onChange={e => set('totalYearsExperience', e.target.value)}
              placeholder="7"
              min={0}
            />
          </Field>
          <Field label="Annual Salary ($)">
            <input
              className={inputCls}
              type="number"
              value={form.annualSalary}
              onChange={e => set('annualSalary', e.target.value)}
              placeholder="150000"
            />
          </Field>
          <Field label="Annual Bonus ($)">
            <input
              className={inputCls}
              type="number"
              value={form.annualBonus}
              onChange={e => set('annualBonus', e.target.value)}
              placeholder="20000"
            />
          </Field>
          <div className="col-span-2">
            <Field label="Equity / RSU Annual Value ($)">
              <input
                className={inputCls}
                type="number"
                value={form.equityAnnualValue}
                onChange={e => set('equityAnnualValue', e.target.value)}
                placeholder="30000"
              />
            </Field>
          </div>
          <div className="col-span-2">
            <Field label="Skills (comma-separated)">
              <input
                className={inputCls}
                value={form.skills}
                onChange={e => set('skills', e.target.value)}
                placeholder="Java, Spring Boot, React, AWS, Kubernetes"
              />
              {form.skills && (
                <div className="flex flex-wrap gap-1.5 mt-2">
                  {form.skills.split(',').map(s => s.trim()).filter(Boolean).map(skill => (
                    <span key={skill} className="px-2 py-0.5 bg-gold-500/10 text-gold-400 text-xs rounded-full border border-gold-500/20">
                      {skill}
                    </span>
                  ))}
                </div>
              )}
            </Field>
          </div>
        </div>
      </Section>

      {/* Family */}
      <Section title="Family" icon={Heart}>
        <div className="space-y-4">
          {/* Married toggle */}
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-text-primary">Married</p>
              <p className="text-xs text-text-muted">Enables household income calculation</p>
            </div>
            <button
              onClick={() => set('isMarried', !form.isMarried)}
              className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${form.isMarried ? 'bg-gold-500' : 'bg-background-tertiary border border-border'}`}
            >
              <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${form.isMarried ? 'translate-x-6' : 'translate-x-1'}`} />
            </button>
          </div>

          {form.isMarried && (
            <div className="grid grid-cols-2 gap-4 pl-0 border-l-2 border-gold-500/20 pl-4">
              <div className="col-span-2 flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-text-primary">Spouse Employed</p>
                </div>
                <button
                  onClick={() => set('spouseEmployed', !form.spouseEmployed)}
                  className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${form.spouseEmployed ? 'bg-gold-500' : 'bg-background-tertiary border border-border'}`}
                >
                  <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${form.spouseEmployed ? 'translate-x-6' : 'translate-x-1'}`} />
                </button>
              </div>
              {form.spouseEmployed && (
                <>
                  <Field label="Spouse Job Title">
                    <input
                      className={inputCls}
                      value={form.spouseJobTitle}
                      onChange={e => set('spouseJobTitle', e.target.value)}
                      placeholder="Product Manager"
                    />
                  </Field>
                  <Field label="Spouse Annual Income ($)">
                    <input
                      className={inputCls}
                      type="number"
                      value={form.spouseAnnualIncome}
                      onChange={e => set('spouseAnnualIncome', e.target.value)}
                      placeholder="120000"
                    />
                  </Field>
                </>
              )}
            </div>
          )}

          {/* Kids */}
          <div className="grid grid-cols-2 gap-4">
            <Field label={`Number of Kids: ${form.numberOfKids}`}>
              <input
                type="range"
                min={0}
                max={6}
                value={form.numberOfKids}
                onChange={e => set('numberOfKids', e.target.value)}
                className="w-full accent-gold-500"
              />
            </Field>
            {parseInt(form.numberOfKids) > 0 && (
              <Field label="Kids Ages (comma-separated)">
                <input
                  className={inputCls}
                  value={form.kidsAges}
                  onChange={e => set('kidsAges', e.target.value)}
                  placeholder="3, 7, 12"
                />
              </Field>
            )}
          </div>
        </div>
      </Section>

      {/* Household Income Summary */}
      {householdIncome > 0 && (
        <div className="bg-gold-500/5 border border-gold-500/20 rounded-xl p-5">
          <div className="flex items-center gap-2 mb-3">
            <DollarSign className="w-5 h-5 text-gold-500" />
            <h3 className="text-sm font-semibold text-gold-400">Household Income Summary</h3>
          </div>
          <div className="grid grid-cols-3 gap-4 text-center">
            {form.annualSalary && (
              <div>
                <p className="text-lg font-bold font-mono text-text-primary">{formatCurrency(parseFloat(form.annualSalary))}</p>
                <p className="text-xs text-text-muted">Your Salary</p>
              </div>
            )}
            {form.annualBonus && (
              <div>
                <p className="text-lg font-bold font-mono text-text-primary">{formatCurrency(parseFloat(form.annualBonus))}</p>
                <p className="text-xs text-text-muted">Bonus</p>
              </div>
            )}
            {form.spouseEmployed && form.spouseAnnualIncome && (
              <div>
                <p className="text-lg font-bold font-mono text-text-primary">{formatCurrency(parseFloat(form.spouseAnnualIncome))}</p>
                <p className="text-xs text-text-muted">Spouse</p>
              </div>
            )}
          </div>
          <div className="mt-4 pt-4 border-t border-gold-500/20 text-center">
            <p className="text-2xl font-bold font-mono text-gold-400">{formatCurrency(householdIncome)}</p>
            <p className="text-xs text-text-muted mt-1">Total Household Income</p>
          </div>
        </div>
      )}

      {/* Notes */}
      <div className="bg-background-secondary border border-border rounded-xl p-6">
        <Field label="Notes / Goals">
          <textarea
            className={`${inputCls} h-24 resize-none`}
            value={form.notes}
            onChange={e => set('notes', e.target.value)}
            placeholder="Career goals, life milestones, anything you want the AI to know..."
          />
        </Field>
      </div>

      {/* Save button (bottom) */}
      <div className="flex justify-end">
        <button
          onClick={handleSave}
          disabled={saving}
          className="flex items-center gap-2 px-6 py-3 bg-gold-500 text-black text-sm font-semibold rounded-lg hover:bg-gold-400 transition-colors disabled:opacity-50"
        >
          {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : saved ? <CheckCircle className="w-4 h-4" /> : <Save className="w-4 h-4" />}
          {saved ? 'Profile Saved!' : 'Save Profile'}
        </button>
      </div>
    </div>
  )
}
