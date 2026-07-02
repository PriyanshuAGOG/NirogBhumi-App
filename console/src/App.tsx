import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, isStaffRole, useAuth } from './auth/AuthProvider'
import SignIn from './auth/SignIn'
import AppShell from './shell/AppShell'
import StaffOnly from './shell/StaffOnly'
import Dashboard from './pages/Dashboard'
import Moderation from './pages/Moderation'
import Batches from './pages/Batches'
import Announcements from './pages/Announcements'
import Calendar from './pages/Calendar'
import Programs from './pages/Programs'
import Content from './pages/Content'
import Consultations from './pages/Consultations'
import Support from './pages/Support'
import Users from './pages/Users'
import Settings from './pages/Settings'

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
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/moderation" element={<Moderation />} />
        <Route path="/batches" element={<Batches />} />
        <Route path="/announcements" element={<Announcements />} />
        <Route path="/calendar" element={<Calendar />} />
        <Route path="/programs" element={<Programs />} />
        <Route path="/content" element={<Content />} />
        <Route path="/consultations" element={<Consultations />} />
        <Route path="/support" element={<Support />} />
        <Route path="/users" element={<Users />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
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
