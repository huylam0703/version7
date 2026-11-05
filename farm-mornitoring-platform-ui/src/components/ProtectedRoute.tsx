
import { Navigate, Outlet, useLocation } from 'react-router-dom'

interface ProtectedRouteProps {
  allowedRoles?: string[]
}

const getUser = () => {
  const raw =
    localStorage.getItem('user') ||
    sessionStorage.getItem('user')

  if (!raw) return null

  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

const getHomeByRole = (role: string) => {
  switch (role?.toUpperCase()) {
    case 'ADMIN':
      return '/admin'

    case 'OPERATOR':
      return '/operator'

    default:
      return '/login'
  }
}

export default function ProtectedRoute({
  allowedRoles,
}: ProtectedRouteProps) {
  const location = useLocation()
  const token =
    localStorage.getItem('token') ||
    sessionStorage.getItem('token')

  const user = getUser()

  // Chưa đăng nhập
  if (!token || !user) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location }}
      />
    )
  }

  const role = user.role || user.Role

  // Đã đăng nhập nhưng không có quyền
  if (
    allowedRoles &&
    !allowedRoles.includes(role?.toUpperCase())
  ) {
    return (
      <Navigate
        to={getHomeByRole(role)}
        replace
      />
    )
  }

  return <Outlet />
}
