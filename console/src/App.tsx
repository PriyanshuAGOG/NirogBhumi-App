import { lazy, Suspense, type ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, isStaffRole, useAuth, type Permission } from './auth/AuthProvider'
import SignIn from './auth/SignIn'
import AppShell from './shell/AppShell'
import StaffOnly from './shell/StaffOnly'

// Route-level code splitting: each page ships as its own chunk so the initial
// bundle only carries the shell + sign-in, not every page in the console.
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Moderation = lazy(() => import('./pages/Moderation'))
const Members = lazy(() => import('./pages/Members'))
const MemberDetail = lazy(() => import('./pages/MemberDetail'))
const Batches = lazy(() => import('./pages/Batches'))
const Announcements = lazy(() => import('./pages/Announcements'))
const Calendar = lazy(() => import('./pages/Calendar'))
const Programs = lazy(() => import('./pages/Programs'))
const Content = lazy(() => import('./pages/Content'))
const Consultations = lazy(() => import('./pages/Consultations'))
const Support = lazy(() => import('./pages/Support'))
const Users = lazy(() => import('./pages/Users'))
const Settings = lazy(() => import('./pages/Settings'))

function FullScreenLoader() {
  return (
    <div className="boot-screen">
      <div className="spin boot-spinner" aria-hidden />
      <p>Loading…</p>
    </div>
  )
}

/** Gate a route behind admin-only or a scoped coach permission; falls back to Dashboard. */
function Protected({
  children,
  adminOnly,
  permission,
}: {
  children: ReactNode
  adminOnly?: boolean
  permission?: Permission
}) {
  const { role, hasPermission } = useAuth()
  const isAdmin = role === 'admin' || role === 'super_admin'
  const allowed = (!adminOnly || isAdmin) && (!permission || hasPermission(permission))
  if (!allowed) return <Navigate to="/dashboard" replace />
  return <>{children}</>
}

function Gate() {
  const { user, role, loading } = useAuth()

  if (loading) return <FullScreenLoader />
  if (!user) return <SignIn />
  if (!isStaffRole(role)) return <StaffOnly />

  return (
    <Suspense fallback={<FullScreenLoader />}>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route
            path="/moderation"
            element={
              <Protected permission="moderation">
                <Moderation />
              </Protected>
            }
          />
          <Route
            path="/members"
            element={
              <Protected permission="members">
                <Members />
              </Protected>
            }
          />
          <Route
            path="/members/:uid"
            element={
              <Protected permission="members">
                <MemberDetail />
              </Protected>
            }
          />
          <Route
            path="/batches"
            element={
              <Protected permission="batches">
                <Batches />
              </Protected>
            }
          />
          <Route
            path="/announcements"
            element={
              <Protected permission="announcements">
                <Announcements />
              </Protected>
            }
          />
          <Route
            path="/calendar"
            element={
              <Protected permission="calendar">
                <Calendar />
              </Protected>
            }
          />
          <Route
            path="/programs"
            element={
              <Protected permission="programs">
                <Programs />
              </Protected>
            }
          />
          <Route
            path="/content"
            element={
              <Protected adminOnly>
                <Content />
              </Protected>
            }
          />
          <Route
            path="/consultations"
            element={
              <Protected permission="consultations">
                <Consultations />
              </Protected>
            }
          />
          <Route
            path="/support"
            element={
              <Protected permission="support">
                <Support />
              </Protected>
            }
          />
          <Route
            path="/users"
            element={
              <Protected adminOnly>
                <Users />
              </Protected>
            }
          />
          <Route path="/settings" element={<Settings />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Route>
      </Routes>
    </Suspense>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Gate />
      </BrowserRouter>
    </AuthProvider>
  )
}
