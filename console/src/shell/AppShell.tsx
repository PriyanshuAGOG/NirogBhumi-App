import { NavLink, Outlet } from 'react-router-dom'
import { useAuth, type Permission } from '../auth/AuthProvider'
import './AppShell.css'

interface NavItem {
  to: string
  label: string
  icon: string
  /** Groups the sidebar into labeled sections instead of one flat list. */
  section: 'Overview' | 'Care+' | 'Support' | 'Platform' | 'Account'
  /** If set, only admin/super_admin see the item - never scopable to a coach. */
  adminOnly?: boolean
  /** If set, a coach only sees the item when this permission is in their scope. */
  permission?: Permission
}

const NAV: NavItem[] = [
  { to: '/dashboard', label: 'Overview', icon: '🏠', section: 'Overview' },
  { to: '/members', label: 'Members', icon: '💚', section: 'Care+', permission: 'members' },
  { to: '/batches', label: 'Batches', icon: '👥', section: 'Care+', permission: 'batches' },
  { to: '/announcements', label: 'Announcements', icon: '📣', section: 'Care+', permission: 'announcements' },
  { to: '/calendar', label: 'Calendar', icon: '🗓️', section: 'Care+', permission: 'calendar' },
  { to: '/programs', label: 'Programs', icon: '🌱', section: 'Care+', permission: 'programs' },
  { to: '/consultations', label: 'Consultations', icon: '🩺', section: 'Care+', permission: 'consultations' },
  { to: '/moderation', label: 'Moderation', icon: '🛡️', section: 'Support', permission: 'moderation' },
  { to: '/support', label: 'Support', icon: '💬', section: 'Support', permission: 'support' },
  { to: '/error-reports', label: 'Error Reports', icon: '🚨', section: 'Support', adminOnly: true },
  { to: '/content', label: 'Content', icon: '📚', section: 'Platform', adminOnly: true },
  { to: '/users', label: 'Users & Roles', icon: '🧑‍🤝‍🧑', section: 'Platform', adminOnly: true },
  { to: '/settings', label: 'Settings', icon: '⚙️', section: 'Account' },
]

const NAV_SECTIONS: NavItem['section'][] = ['Overview', 'Care+', 'Support', 'Platform', 'Account']

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
  const sections = NAV_SECTIONS.map((section) => ({
    section,
    items: nav.filter((item) => item.section === section),
  })).filter((group) => group.items.length > 0)

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="sidebar-overline">Nirog Bhumi</span>
          <span className="sidebar-title">Staff Console</span>
        </div>

        <nav className="sidebar-nav">
          {sections.map(({ section, items }) => (
            <div key={section} className="sidebar-group">
              {section !== 'Overview' && <span className="sidebar-group-label">{section}</span>}
              {items.map((item) => (
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
            </div>
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
