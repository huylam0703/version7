import { useEffect, useState } from 'react'

export default function ForbiddenListener() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    let timer: number | undefined

    const handleForbidden = () => {
      setVisible(true)
      window.clearTimeout(timer)
      timer = window.setTimeout(() => setVisible(false), 4000)
    }

    window.addEventListener('forbidden', handleForbidden)

    return () => {
      window.removeEventListener('forbidden', handleForbidden)
      window.clearTimeout(timer)
    }
  }, [])

  if (!visible) return null

  return (
    <div
      role="alert"
      className="fixed top-4 right-4 z-50 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 shadow-lg"
    >
      Bạn không có quyền thực hiện thao tác này.
    </div>
  )
}