import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import './AppShell.css'

interface NavItem {
  to: string
  label: string
  icon: string
}

const NAV: NavItem[] = [
  { to: '/moderation', label: 'Moderation', icon: '🛡️' },
  { to: '/batches', label: 'Batches', icon: '👥' },
  { to: '/announcements', label: 'Announcements', icon: '📣' },
  { to: '/calendar', label: 'Calendar', icon: '🗓️' },
  { to: '/programs', label: 'Programs', icon: '🌱' },
  { to: '/content', label: 'Content', icon: '📚' },
  { to: '/settings', label: 'Settings', icon: '⚙️' },
]

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

export default function AppShell() {
  const { user, role, signOut } = useAuth()

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="sidebar-overline">Nirog Bhumi</span>
          <span className="sidebar-title">Staff Console</span>
        </div>

        <nav className="sidebar-nav">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                'nav-item' + (isActive ? ' nav-item-active' : '')
              }
            >
              <span className="nav-icon" aria-hidden>
                {item.icon}
              </span>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-foot">
          <div className="who">
            <span className="who-name">{user?.email ?? 'Signed in'}</span>
            <span className="who-role">{roleLabel(role)}</span>
          </div>
          <button className="btn btn-ghost sidebar-signout" onClick={() => void signOut()}>
            Sign out
          </button>
        </div>
      </aside>

      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
