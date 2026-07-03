import { FirebaseError } from 'firebase/app'

/** Turn an unknown thrown value into a short, human message with a fallback. */
export function errText(err: unknown, fallback: string): string {
  // Callable Cloud Function errors carry a developer-written, user-safe
  // message (HttpsError's second argument) - surface it instead of just the
  // code, e.g. "Only a super admin can grant the admin role" instead of a
  // bare "(permission-denied)".
  if (err instanceof FirebaseError) {
    return err.code.startsWith('functions/') && err.message ? err.message : `${fallback} (${err.code}).`
  }
  if (err instanceof Error && err.message) return `${fallback} (${err.message}).`
  return `${fallback}.`
}
