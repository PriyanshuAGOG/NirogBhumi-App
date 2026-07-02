import { useAuth } from '../auth/AuthProvider'
import './Settings.css'

const APP_VERSION = '0.1.0'

function roleLabel(role: string | null): string {
  switch (role) {
    case 'super_admin':
      return 'Super admin'
    case 'admin':
      return 'Admin'
    case 'coach':
      return 'Coach'
    default:
      return 'Staff'
  }
}

export default function Settings() {
  const { user, role, signOut } = useAuth()

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Settings</span>
        <h1>Settings</h1>
        <p className="page-lede">Your console account and app details.</p>
      </header>

      <div className="card set-card">
        <span className="overline">Signed in as</span>
        <div className="set-rows">
          <div className="set-row">
            <span className="set-key">Email</span>
            <span className="set-val">{user?.email ?? '—'}</span>
          </div>
          <div className="set-row">
            <span className="set-key">Role</span>
            <span className="set-val">
              <span className="tag tag-good">{roleLabel(role)}</span>
            </span>
          </div>
          <div className="set-row">
            <span className="set-key">User ID</span>
            <span className="set-val set-mono">{user?.uid ?? '—'}</span>
          </div>
        </div>
        <div className="set-actions">
          <button className="btn btn-ghost" onClick={() => void signOut()}>
            Sign out
          </button>
        </div>
      </div>

      <div className="card set-card">
        <span className="overline">About</span>
        <div className="set-rows">
          <div className="set-row">
            <span className="set-key">App</span>
            <span className="set-val">Nirog Bhumi — Staff Console</span>
          </div>
          <div className="set-row">
            <span className="set-key">Version</span>
            <span className="set-val">{APP_VERSION}</span>
          </div>
          <div className="set-row">
            <span className="set-key">Region</span>
            <span className="set-val">asia-south1</span>
          </div>
        </div>
      </div>
    </section>
  )
}
