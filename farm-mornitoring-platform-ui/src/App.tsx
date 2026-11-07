import type { ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, homePathByRole, useAuth } from './AuthContext'
import { AppLayout, RoleRoute } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { DashboardPage } from './pages/admin/DashboardPage'
import { FarmsPage } from './pages/admin/FarmsPage'
import { ZonesPage } from './pages/admin/ZonesPage'
import { SensorMonitorPage } from './pages/admin/SensorMonitorPage'
import { SensorHistoryPage } from './pages/admin/SensorHistoryPage'
import { ComparePage } from './pages/admin/ComparePage'
import { AlertsPage } from './pages/admin/AlertsPage'
import { ThresholdsPage } from './pages/admin/ThresholdsPage'
import { ReportsPage } from './pages/admin/ReportsPage'
import { UsersPage } from './pages/admin/UsersPage'
import { OperatorDashboardPage } from './pages/operator/OperatorDashboardPage'
import { NodesPage } from './pages/operator/NodesPage'
import { UavsPage } from './pages/operator/UavsPage'
import { GatewaysPage } from './pages/operator/GatewaysPage'
import { MissionsPage } from './pages/operator/MissionsPage'
import { CreateMissionPage } from './pages/operator/CreateMissionPage'
import { MissionMonitorPage } from './pages/operator/MissionMonitorPage'
import { MissionResultsPage } from './pages/operator/MissionResultsPage'
import { NotificationsPage } from './pages/shared/NotificationsPage'
import { SettingsPage } from './pages/shared/SettingsPage'
import SessionExpiredListener from './components/SessionExpiredListener'

function HomeRedirect() {
  const { user } = useAuth()
  return <Navigate to={homePathByRole(user?.role)} replace />
}

function Admin({ children }: { children: ReactNode }) {
  return <RoleRoute roles={['ADMIN']}>{children}</RoleRoute>
}

function Operator({ children }: { children: ReactNode }) {
  return <RoleRoute roles={['OPERATOR']}>{children}</RoleRoute>
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <SessionExpiredListener />

        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<AppLayout />}>
            <Route path="/" element={<HomeRedirect />} />
            <Route path="/dashboard" element={<Admin><DashboardPage /></Admin>} />
            <Route path="/farms" element={<Admin><FarmsPage /></Admin>} />
            <Route path="/zones" element={<Admin><ZonesPage /></Admin>} />
            <Route path="/sensors/monitor" element={<Admin><SensorMonitorPage /></Admin>} />
            <Route path="/sensors/history" element={<Admin><SensorHistoryPage /></Admin>} />
            <Route path="/sensors/compare" element={<Admin><ComparePage /></Admin>} />
            <Route path="/alerts" element={<Admin><AlertsPage /></Admin>} />
            <Route path="/alerts/thresholds" element={<Admin><ThresholdsPage /></Admin>} />
            <Route path="/reports" element={<Admin><ReportsPage /></Admin>} />
            <Route path="/users" element={<Admin><UsersPage /></Admin>} />
            <Route path="/operator" element={<Operator><OperatorDashboardPage /></Operator>} />
            <Route path="/nodes" element={<Operator><NodesPage /></Operator>} />
            <Route path="/uavs" element={<Operator><UavsPage /></Operator>} />
            <Route path="/gateways" element={<Operator><GatewaysPage /></Operator>} />
            <Route path="/missions" element={<Operator><MissionsPage /></Operator>} />
            <Route path="/missions/new" element={<Operator><CreateMissionPage /></Operator>} />
            <Route path="/missions/:id" element={<Operator><MissionMonitorPage /></Operator>} />
            <Route path="/missions/:id/results" element={<Operator><MissionResultsPage /></Operator>} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
