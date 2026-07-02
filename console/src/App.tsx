import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, isStaffRole, useAuth } from './auth/AuthProvider'
import SignIn from './auth/SignIn'
import AppShell from './shell/AppShell'
import StaffOnly from './shell/StaffOnly'
import Placeholder from './shell/Placeholder'
import Moderation from './pages/Moderation'

function FullScreenLoader() {
  return (
    <div className="boot-screen">
      <div className="spin boot-spinner" aria-hidden />
      <p>Loading…</p>
    </div>
  )
}

function Gate() {
  const { user, role, loading } = useAuth()

  if (loading) return <FullScreenLoader />
  if (!user) return <SignIn />
  if (!isStaffRole(role)) return <StaffOnly />

  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Navigate to="/moderation" replace />} />
        <Route path="/moderation" element={<Moderation />} />
        <Route path="/batches" element={<Placeholder title="Batches" />} />
        <Route path="/announcements" element={<Placeholder title="Announcements" />} />
        <Route path="/calendar" element={<Placeholder title="Calendar" />} />
        <Route path="/programs" element={<Placeholder title="Programs" />} />
        <Route path="/content" element={<Placeholder title="Content" />} />
        <Route path="/settings" element={<Placeholder title="Settings" />} />
        <Route path="*" element={<Navigate to="/moderation" replace />} />
      </Route>
    </Routes>
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
