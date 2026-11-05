import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../AuthContext'

export default function SessionExpiredListener() {
  const [visible, setVisible] = useState(false)
  const navigate = useNavigate()
  const { logout } = useAuth()

  useEffect(() => {
    const handleExpired = () => {
      void logout()
      setVisible(true)
    }

    window.addEventListener('session-expired', handleExpired)

    return () => {
      window.removeEventListener('session-expired', handleExpired)
    }
  }, [logout])

  const handleLogin = () => {
    setVisible(false)
    navigate('/login', { replace: true })
  }

  if (!visible) return null

  return (
    <div className="session-modal-overlay">
      <div className="session-modal">
        <h2>Phiên đăng nhập đã hết hạn</h2>

        <p>
          Phiên đăng nhập của bạn đã hết hạn.
          Vui lòng đăng nhập lại để tiếp tục sử dụng hệ thống.
        </p>

        <button onClick={handleLogin}>
          Đăng nhập lại
        </button>
      </div>
    </div>
  )
}