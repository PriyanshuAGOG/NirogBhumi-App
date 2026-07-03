import { NavLink, Outlet } from 'react-router-dom'
import { useAuth, type Permission } from '../auth/AuthProvider'
import './AppShell.css'

interface NavItem {
  to: string
  label: string
  icon: string
  /** If set, only admin/super_admin see the item - never scopable to a coach. */
  adminOnly?: boolean
  /** If set, a coach only sees the item when this permission is in their scope. */
  permission?: Permission
}

const NAV: NavItem[] = [
  { to: '/dashboard', label: 'Overview', icon: '🏠' },
  { to: '/moderation', label: 'Moderation', icon: '🛡️', permission: 'moderation' },
  { to: '/members', label: 'Members', icon: '💚', permission: 'members' },
  { to: '/batches', label: 'Batches', icon: '👥', permission: 'batches' },
  { to: '/announcements', label: 'Announcements', icon: '📣', permission: 'announcements' },
  { to: '/calendar', label: 'Calendar', icon: '🗓️', permission: 'calendar' },
  { to: '/programs', label: 'Programs', icon: '🌱', permission: 'programs' },
  { to: '/content', label: 'Content', icon: '📚', adminOnly: true },
  { to: '/consultations', label: 'Consultations', icon: '🩺', permission: 'consultations' },
  { to: '/support', label: 'Support', icon: '💬', permission: 'support' },
  { to: '/users', label: 'Users & Roles', icon: '🧑‍🤝‍🧑', adminOnly: true },
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
  const { user, role, signOut, hasPermission } = useAuth()
  const isAdmin = role === 'admin' || role === 'super_admin'
  const nav = NAV.filter(
    (item) => (!item.adminOnly || isAdmin) && (!item.permission || hasPermission(item.permission)),
  )

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="sidebar-overline">Nirog Bhumi</span>
          <span className="sidebar-title">Staff Console</span>
        </div>

        <nav className="sidebar-nav">
          {nav.map((item) => (
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
