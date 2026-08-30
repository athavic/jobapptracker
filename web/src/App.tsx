import { Navigate, Route, Routes } from 'react-router-dom'
import { ApplicationsPage } from './features/applications/ApplicationsPage'

/**
 * Routes are set up now even though there is only one real page, so phase 4's
 * dashboard and detail views drop in without restructuring anything.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/applications" replace />} />
      <Route path="/applications" element={<ApplicationsPage />} />
      <Route path="*" element={<Navigate to="/applications" replace />} />
    </Routes>
  )
}
