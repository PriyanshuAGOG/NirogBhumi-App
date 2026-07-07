import { httpsCallable } from 'firebase/functions'
import { functions } from './firebase'

export interface BulkOnboardRow {
  contact: string
  programId: string
}

export interface BulkOnboardResult {
  contact: string
  status: 'enrolled' | 'invited' | 'error'
  message?: string
}

export async function callBulkOnboard(rows: BulkOnboardRow[]): Promise<BulkOnboardResult[]> {
  const bulkOnboard = httpsCallable<{ rows: BulkOnboardRow[] }, { results: BulkOnboardResult[] }>(
    functions,
    'bulkOnboard',
  )
  const res = await bulkOnboard({ rows })
  return res.data.results
}

export function summarizeBulkResults(results: BulkOnboardResult[]): string {
  const enrolled = results.filter((r) => r.status === 'enrolled').length
  const invited = results.filter((r) => r.status === 'invited').length
  const errors = results.filter((r) => r.status === 'error')
  let summary = `${enrolled} enrolled immediately, ${invited} invited (will join automatically on signup).`
  if (errors.length) {
    summary += ` ${errors.length} failed: ${errors.map((e) => `${e.contact} (${e.message ?? 'error'})`).join('; ')}`
  }
  return summary
}
