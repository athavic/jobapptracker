import { Navigate, Route, Routes } from 'react-router-dom'
import { SignInGate } from './features/auth/SignInGate'
import { ApplicationsPage } from './features/applications/ApplicationsPage'

/**
 * Routes are set up now even though there is only one real page, so phase 4's
 * dashboard and detail views drop in without restructuring anything.
 *
 * The gate wraps every route rather than each page individually: there is no
 * page here that makes sense without a workspace, and a per-page check would be
 * a rule you have to remember on the next page you add.
 */
export default function App() {
  return (
    <SignInGate>
      <Routes>
        <Route path="/" element={<Navigate to="/applications" replace />} />
        <Route path="/applications" element={<ApplicationsPage />} />
        <Route path="*" element={<Navigate to="/applications" replace />} />
      </Routes>
    </SignInGate>
  )
}
