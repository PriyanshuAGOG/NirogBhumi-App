import { httpsCallable } from 'firebase/functions'
import { functions } from './firebase'

export type AudienceScope = 'program' | 'all_enrolled' | 'all_users' | 'inactive' | 'non_enrolled'

export interface AnnouncementAudience {
  scope: AudienceScope
  programIds: string[]
  inactiveDays: number
}

export interface AnnouncementChannels {
  inApp: boolean
  push: boolean
  email: boolean
}

export interface CreateAnnouncementInput {
  title: string
  body: string
  audience: AnnouncementAudience
  channels: AnnouncementChannels
  expiresInHours: number
}

export const AUDIENCE_LABELS: Record<AudienceScope, string> = {
  program: 'Specific program(s)/batch(es)',
  all_enrolled: 'All enrolled (Care+) members',
  all_users: 'All app users',
  inactive: 'Inactive members',
  non_enrolled: 'Not enrolled in any program',
}

/** Short human summary for the "past announcements" list, e.g. "2 programs · inactive 4+ days". */
export function describeAudience(audience: AnnouncementAudience | undefined): string {
  if (!audience) return 'Program'
  if (audience.scope === 'program') {
    const count = audience.programIds?.length ?? 0
    return count === 1 ? '1 program' : `${count} programs`
  }
  if (audience.scope === 'inactive') return `Inactive ${audience.inactiveDays ?? 4}+ days`
  return AUDIENCE_LABELS[audience.scope] ?? audience.scope
}

export async function callCreateAnnouncement(
  input: CreateAnnouncementInput,
): Promise<{ announcementId: string; recipientCount: number }> {
  const fn = httpsCallable<CreateAnnouncementInput, { announcementId: string; recipientCount: number }>(
    functions,
    'createAnnouncement',
  )
  const res = await fn(input)
  return res.data
}

export async function callPreviewAnnouncementAudience(audience: AnnouncementAudience): Promise<number> {
  const fn = httpsCallable<{ audience: AnnouncementAudience }, { count: number }>(
    functions,
    'previewAnnouncementAudience',
  )
  const res = await fn({ audience })
  return res.data.count
}

export async function callDeleteAnnouncement(id: string): Promise<boolean> {
  const fn = httpsCallable<{ id: string }, { deleted: boolean }>(functions, 'deleteAnnouncement')
  const res = await fn({ id })
  return res.data.deleted
}
