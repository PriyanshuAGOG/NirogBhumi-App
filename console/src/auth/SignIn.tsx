import { useState, type FormEvent } from 'react'
import { signInWithEmailAndPassword, sendPasswordResetEmail } from 'firebase/auth'
import { FirebaseError } from 'firebase/app'
import { auth } from '../lib/firebase'
import './SignIn.css'

function messageFor(err: unknown): string {
  if (err instanceof FirebaseError) {
    switch (err.code) {
      case 'auth/invalid-email':
        return 'That email address does not look right.'
      case 'auth/invalid-credential':
      case 'auth/wrong-password':
      case 'auth/user-not-found':
        return 'Email or password is incorrect.'
      case 'auth/too-many-requests':
        return 'Too many attempts. Please wait a moment and try again.'
      case 'auth/network-request-failed':
        return 'Network problem — check your connection and try again.'
      default:
        return 'Could not sign in. Please try again.'
    }
  }
  return 'Could not sign in. Please try again.'
}

function resetMessageFor(err: unknown): string {
  if (err instanceof FirebaseError && err.code === 'auth/invalid-email') {
    return 'That email address does not look right.'
  }
  // Deliberately the same message on "no such account" as on success - a
  // reset form that reveals which emails exist is a real enumeration leak,
  // and a newly-created staff account should just be told to check email
  // either way.
  return 'If that email has a console account, a reset link is on its way.'
}

export default function SignIn() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showReset, setShowReset] = useState(false)
  const [resetBusy, setResetBusy] = useState(false)
  const [resetMessage, setResetMessage] = useState<string | null>(null)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await signInWithEmailAndPassword(auth, email.trim(), password)
      // AuthProvider picks up the session and routes onward.
    } catch (err) {
      setError(messageFor(err))
      setBusy(false)
    }
  }

  async function onReset(e: FormEvent) {
    e.preventDefault()
    setResetBusy(true)
    setResetMessage(null)
    try {
      await sendPasswordResetEmail(auth, email.trim())
      setResetMessage('If that email has a console account, a reset link is on its way.')
    } catch (err) {
      setResetMessage(resetMessageFor(err))
    } finally {
      setResetBusy(false)
    }
  }

  return (
    <div className="signin-screen">
      <div className="signin-card card">
        <div className="signin-brand">
          <span className="overline">Nirog Bhumi</span>
          <h1>Staff Console</h1>
          <p className="signin-sub">
            {showReset
              ? "Enter your email and we'll send a reset link — useful right after a new account is created for you."
              : 'Sign in with your coach or admin account to continue.'}
          </p>
        </div>

        {showReset ? (
          <form className="stack" onSubmit={onReset}>
            <label className="field">
              <span>Email</span>
              <input
                type="email"
                autoComplete="username"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@nirogbhumi.app"
                required
              />
            </label>

            {resetMessage && <p className="signin-error" role="status">{resetMessage}</p>}

            <button className="btn btn-forest" type="submit" disabled={resetBusy}>
              {resetBusy ? 'Sending…' : 'Send reset link'}
            </button>
            <button
              className="btn btn-ghost"
              type="button"
              onClick={() => {
                setShowReset(false)
                setResetMessage(null)
              }}
            >
              Back to sign in
            </button>
          </form>
        ) : (
          <form className="stack" onSubmit={onSubmit}>
            <label className="field">
              <span>Email</span>
              <input
                type="email"
                autoComplete="username"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@nirogbhumi.app"
                required
              />
            </label>

            <label className="field">
              <span>Password</span>
              <input
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </label>

            {error && <p className="signin-error" role="alert">{error}</p>}

            <button className="btn btn-forest" type="submit" disabled={busy}>
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
            <button className="btn btn-ghost" type="button" onClick={() => setShowReset(true)}>
              Forgot password?
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
