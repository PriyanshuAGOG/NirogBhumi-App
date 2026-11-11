import { formatDateTime, toDate } from './time'

export interface ProfileRow {
  label: string
  value: string
}

export interface ProfileSection {
  title: string
  rows: ProfileRow[]
}

/** Fields that are internal plumbing or secrets, never shown to staff. */
const HIDDEN = new Set(['userId', 'profileId', 'fcmToken', 'fcmTokens', 'deviceToken', 'deviceTokens', 'pushToken', 'latestMetrics', 'consent'])

const LABELS: Record<string, string> = {
  fullName: 'Name',
  name: 'Name',
  email: 'Email',
  phone: 'Phone',
  city: 'City',
  preferredLanguage: 'Language',
  age: 'Age',
  gender: 'Gender',
  heightCm: 'Height (cm)',
  weightKg: 'Weight (kg)',
  diabetesType: 'Diabetes type',
  diabetesTypeOther: 'Diabetes type (their words)',
  goals: 'Goals',
  selectedGoals: 'Goals',
  healthProfileCompleted: 'Health profile filled in',
  programActive: 'In a program',
  activeProgramName: 'Program',
  activeProgramId: 'Program id',
  programDurationDays: 'Program length (days)',
  programStartedAt: 'Program started',
  role: 'Role',
  status: 'Account status',
  createdAt: 'Joined',
  updatedAt: 'Profile last updated',
  lastCheckinAt: 'Last check-in',
  checkinStreak: 'Check-in streak (days)',
  checkinHourHint: 'Usual check-in hour',
}

const GROUPS: { title: string; keys: string[] }[] = [
  { title: 'Contact', keys: ['fullName', 'name', 'email', 'phone', 'city', 'preferredLanguage'] },
  { title: 'About them', keys: ['age', 'gender', 'heightCm', 'weightKg', 'diabetesType', 'diabetesTypeOther', 'goals', 'selectedGoals', 'healthProfileCompleted'] },
  { title: 'Program', keys: ['programActive', 'activeProgramName', 'programDurationDays', 'programStartedAt'] },
  { title: 'Account', keys: ['role', 'status', 'createdAt', 'updatedAt', 'lastCheckinAt', 'checkinStreak', 'checkinHourHint'] },
]

export function humanize(key: string): string {
  const spaced = key.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim()
  return spaced.charAt(0).toUpperCase() + spaced.slice(1).toLowerCase()
}

/** A value as plain text. Dates read as dates, yes/no as Yes/No, lists and small objects as one line. */
export function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? 'Yes' : 'No'
  if (typeof value === 'number') return String(value)
  if (typeof value === 'string') return value
  const date = toDate(value)
  if (date) return formatDateTime(date)
  if (Array.isArray(value)) return value.length === 0 ? '—' : value.map(formatValue).join(', ')
  if (typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>).filter(([k]) => !HIDDEN.has(k))
    return entries.length === 0 ? '—' : entries.map(([k, v]) => `${humanize(k)}: ${formatValue(v)}`).join(' · ')
  }
  return String(value)
}

/**
 * Everything stored on a member's profile, in plain sections: contact, about them, program, account, then consent and
 * anything else the app saved. Nothing is dropped except secrets (push tokens) and internal ids.
 */
export function buildProfileSections(profile: Record<string, unknown>): ProfileSection[] {
  const used = new Set<string>()
  const sections: ProfileSection[] = []
  for (const group of GROUPS) {
    const rows: ProfileRow[] = []
    for (const key of group.keys) {
      if (!(key in profile) || HIDDEN.has(key)) continue
      used.add(key)
      // "name" and "fullName" are the same thing: show it once.
      if (key === 'name' && 'fullName' in profile) continue
      rows.push({ label: LABELS[key] ?? humanize(key), value: formatValue(profile[key]) })
    }
    if (rows.length > 0) sections.push({ title: group.title, rows })
  }

  const consent = profile.consent
  if (consent && typeof consent === 'object') {
    const c = consent as Record<string, unknown>
    const rows: ProfileRow[] = [
      ['healthData', 'Health data stored'],
      ['expertReview', 'Expert review'],
      ['medicalDisclaimer', 'Medical disclaimer accepted'],
      ['research', 'Anonymised research (optional)'],
      ['marketing', 'Product updates (optional)'],
      ['version', 'Notice version they accepted'],
      ['acceptedAt', 'Accepted on'],
    ].filter(([k]) => k in c).map(([k, label]) => ({ label, value: formatValue(c[k]) }))
    if (rows.length > 0) sections.push({ title: 'Consent and privacy', rows })
  }

  const other: ProfileRow[] = Object.keys(profile)
    .filter((k) => !used.has(k) && !HIDDEN.has(k))
    .sort()
    .map((k) => ({ label: LABELS[k] ?? humanize(k), value: formatValue(profile[k]) }))
  if (other.length > 0) sections.push({ title: 'Other details they saved', rows: other })
  return sections
}
