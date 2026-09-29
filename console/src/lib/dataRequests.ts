import { httpsCallable } from 'firebase/functions'
import { functions } from './firebase'

export type DeletionStatus =
  | 'scheduled'
  | 'approved'
  | 'processing'
  | 'completed'
  | 'cancelled'
  | 'rejected'
  | 'failed'
  | 'requested'
  | 'awaiting_verification'

interface ManageResult {
  requestId: string
  status: string
}

const manage = httpsCallable<Record<string, string>, ManageResult>(functions, 'adminManageDeletion')

/** Skip the grace period and erase now (identity already verified out of band). */
export async function approveDeletion(requestId: string): Promise<ManageResult> {
  return (await manage({ action: 'approve', requestId })).data
}

export async function rejectDeletion(requestId: string): Promise<ManageResult> {
  return (await manage({ action: 'reject', requestId })).data
}

/** Start (and immediately approve) a deletion for a member who asked by email/phone. */
export async function startDeletion(contact: { email?: string; phone?: string; uid?: string }): Promise<ManageResult> {
  const payload: Record<string, string> = { action: 'start' }
  if (contact.uid) payload.uid = contact.uid
  if (contact.email) payload.email = contact.email
  if (contact.phone) payload.phone = contact.phone
  return (await manage(payload)).data
}
