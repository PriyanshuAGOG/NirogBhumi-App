import { useAuth } from '../auth/AuthProvider'
import './StaffOnly.css'

export default function StaffOnly() {
  const { user, signOut } = useAuth()
  return (
    <div className="gate-screen">
      <div className="gate-card card">
        <span className="overline">Nirog Bhumi</span>
        <h1>This console is for staff only</h1>
        <p className="gate-sub">
          {user?.email ? (
            <>
              You are signed in as <strong>{user.email}</strong>, but this
              account does not have coach or admin access.
            </>
          ) : (
            'This account does not have coach or admin access.'
          )}
        </p>
        <p className="gate-note">
          If you believe this is a mistake, contact your program administrator.
        </p>
        <button className="btn btn-forest" onClick={() => void signOut()}>
          Sign out
        </button>
      </div>
    </div>
  )
}
