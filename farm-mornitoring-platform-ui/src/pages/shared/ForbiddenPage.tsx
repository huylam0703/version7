import { Link } from 'react-router-dom'
import { homePathByRole, useAuth } from '../../AuthContext'
import { Card, PageHeader } from '../../components/Ui'

export function ForbiddenPage() {
  const { user } = useAuth()

  return (
    <div>
      <PageHeader
        title="403 – Không có quyền truy cập"
        description="Tài khoản của bạn không được phép xem trang này."
      />
      <Card>
        <Link
          to={homePathByRole(user?.role)}
          className="text-sm font-medium text-brand"
        >
          Về trang chủ
        </Link>
      </Card>
    </div>
  )
}