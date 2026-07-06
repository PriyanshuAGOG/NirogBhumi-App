import { lazy, Suspense, type ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, isStaffRole, useAuth, type Permission } from './auth/AuthProvider'
import { firebaseConfigError } from './lib/firebase'
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
const ErrorReports = lazy(() => import('./pages/ErrorReports'))

function FullScreenLoader() {
  return (
    <div className="boot-screen">
      <div className="spin boot-spinner" aria-hidden />
      <p>Loading…</p>
    </div>
  )
}

/**
 * Every page in this console reads Firestore/Storage/Functions - if the
 * Firebase web config wasn't baked in at build time (this build wasn't
 * produced by this repo's GitHub Actions workflow, or that workflow's env
 * changed), sign-in and every single query fail with a bare
 * "permission-denied" and nothing in the UI explains why - every page just
 * quietly looks like it "has no data". This is checked before AuthProvider
 * even mounts, since sign-in itself is equally broken in this state.
 */
function ConfigErrorScreen({ message }: { message: string }) {
  return (
    <div className="boot-screen">
      <p style={{ maxWidth: 480, textAlign: 'center', color: '#B4472F', fontWeight: 600 }}>
        Configuration error
      </p>
      <p style={{ maxWidth: 480, textAlign: 'center' }}>{message}</p>
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
          <Route
            path="/error-reports"
            element={
              <Protected adminOnly>
                <ErrorReports />
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
  if (firebaseConfigError) return <ConfigErrorScreen message={firebaseConfigError} />
  return (
    <AuthProvider>
      <BrowserRouter>
        <Gate />
      </BrowserRouter>
    </AuthProvider>
  )
}
