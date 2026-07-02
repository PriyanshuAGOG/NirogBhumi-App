import { FirebaseError } from 'firebase/app'

/** Turn an unknown thrown value into a short, human message with a fallback. */
export function errText(err: unknown, fallback: string): string {
  if (err instanceof FirebaseError) return `${fallback} (${err.code}).`
  if (err instanceof Error && err.message) return `${fallback} (${err.message}).`
  return `${fallback}.`
}
