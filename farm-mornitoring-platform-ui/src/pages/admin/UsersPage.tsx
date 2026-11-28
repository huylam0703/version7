import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { getErrorMessage, userApi } from '../../api/'
import { useAuth } from '../../AuthContext'
import type { User, UserRole } from '../../types/auth'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  Empty,
  ErrorBox,
  Input,
  Modal,
  PageHeader,
  Select,
  Table,
} from '../../components/Ui'

const PAGE_SIZE = 10

const initialCreate = {
  fullName: '',
  email: '',
  password: '',
  role: 'OPERATOR' as UserRole,
  phone: '',
}

export function UsersPage() {
  const { user: me } = useAuth()

  const [users, setUsers] = useState<User[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)

  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState(initialCreate)

  const [editing, setEditing] = useState<User | null>(null)
  const [editForm, setEditForm] = useState({
    fullName: '',
    phone: '',
    role: 'OPERATOR' as UserRole,
  })

  // Tài khoản đang chờ xác nhận khóa / mở khóa
  const [lockTarget, setLockTarget] = useState<User | null>(null)

  const load = useCallback(async () => {
    try {
      setError('')
      const res = await userApi.list({
        searchString: search || undefined,
        role: roleFilter || undefined,
        status: statusFilter || undefined,
        page,
        pageSize: PAGE_SIZE,
      })
      setUsers(res.items)
      setTotal(res.total)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }, [search, roleFilter, statusFilter, page])

  useEffect(() => {
    void load()
  }, [load])

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  function onSearch(e: FormEvent) {
    e.preventDefault()
    setPage(1)
    setSearch(searchInput.trim())
  }

  // ----- Khóa / mở khóa -----
  const lockTargetLocked = lockTarget?.status?.toUpperCase() === 'LOCKED'

  async function confirmToggleLock() {
    if (!lockTarget) return
    const u = lockTarget
    const locked = u.status?.toUpperCase() === 'LOCKED'

    try {
      setBusyId(u.userId)
      setError('')
      if (locked) await userApi.unlock(u.userId)
      else await userApi.lock(u.userId)
      setLockTarget(null)
      await load()
    } catch (e) {
      setLockTarget(null)
      setError(getErrorMessage(e))
    } finally {
      setBusyId(null)
    }
  }

  // ----- Tạo -----
  function closeCreate() {
    if (saving) return
    setCreateOpen(false)
    setCreateForm(initialCreate)
    setFormError('')
  }

  async function onCreate(e: FormEvent) {
    e.preventDefault()
    setFormError('')

    if (!createForm.fullName.trim() || !createForm.email.trim() || !createForm.password) {
      setFormError('Vui lòng nhập đầy đủ họ tên, email và mật khẩu.')
      return
    }

    try {
      setSaving(true)
      await userApi.create({
        fullName: createForm.fullName.trim(),
        email: createForm.email.trim(),
        password: createForm.password,
        phone: createForm.phone.trim() || null,
        roleName: createForm.role,
      })
      setCreateOpen(false)
      setCreateForm(initialCreate)
      await load()
    } catch (err) {
      setFormError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  // ----- Sửa -----
  function openEdit(u: User) {
    setFormError('')
    setEditing(u)
    setEditForm({ fullName: u.fullName, phone: u.phone ?? '', role: u.role })
  }

  function closeEdit() {
    if (saving) return
    setEditing(null)
    setFormError('')
  }

  async function onEdit(e: FormEvent) {
    e.preventDefault()
    if (!editing) return
    setFormError('')

    if (!editForm.fullName.trim()) {
      setFormError('Họ tên không được để trống.')
      return
    }

    try {
      setSaving(true)
      await userApi.update(editing.userId, {
        fullName: editForm.fullName.trim(),
        phone: editForm.phone.trim() || null,
        roleName: editForm.role,
      })
      setEditing(null)
      await load()
    } catch (err) {
      setFormError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const editingSelf = editing?.userId === me?.userId

  return (
    <div>
      <PageHeader
        title="User management"
        description="Farm administrators can create, edit, lock and unlock accounts."
        actions={
          <Button
            onClick={() => {
              setFormError('')
              setCreateOpen(true)
            }}
          >
            Create user
          </Button>
        }
      />

      <ErrorBox message={error} />

      <Card className="mb-4">
        <form className="flex flex-wrap items-end gap-3" onSubmit={onSearch}>
          <div className="min-w-56 flex-1">
            <Input
              label="Search"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Name or email"
            />
          </div>

          <Select
            label="Role"
            value={roleFilter}
            onChange={(e) => {
              setRoleFilter(e.target.value)
              setPage(1)
            }}
          >
            <option value="">All</option>
            <option value="ADMIN">ADMIN</option>
            <option value="OPERATOR">OPERATOR</option>
          </Select>

          <Select
            label="Status"
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value)
              setPage(1)
            }}
          >
            <option value="">All</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="LOCKED">LOCKED</option>
          </Select>

          <Button type="submit">Search</Button>
        </form>
      </Card>

      <Card>
        {users.length === 0 ? (
          <Empty text="No users found." />
        ) : (
          <Table headers={['Name', 'Email', 'Role', 'Status', 'Phone', 'Actions']}>
            {users.map((u) => {
              const isSelf = u.userId === me?.userId
              const locked = u.status?.toUpperCase() === 'LOCKED'

              return (
                <tr key={u.userId}>
                  <td className="px-3 py-2">{u.fullName}</td>
                  <td className="px-3 py-2">{u.email}</td>
                  <td className="px-3 py-2">{u.role}</td>
                  <td className="px-3 py-2">
                    <Badge color={locked ? 'red' : 'green'}>{u.status}</Badge>
                  </td>
                  <td className="px-3 py-2">{u.phone || '—'}</td>
                  <td className="px-3 py-2">
                    <div className="flex gap-2">
                      <Button variant="outline" onClick={() => openEdit(u)}>
                        Edit
                      </Button>
                      {!isSelf && (
                        <Button
                          variant={locked ? 'ghost' : 'danger'}
                          disabled={busyId === u.userId}
                          onClick={() => setLockTarget(u)}
                        >
                          {locked ? 'Unlock' : 'Lock'}
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              )
            })}
          </Table>
        )}

        <div className="mt-3 flex items-center justify-between text-sm text-muted">
          <span>
            Page {page} / {totalPages} · {total} users
          </span>
          <div className="flex gap-2">
            <Button variant="outline" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <Button
              variant="outline"
              disabled={page >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      </Card>

      {/* Popup xác nhận khóa / mở khóa */}
      <ConfirmDialog
        open={lockTarget !== null}
        icon={lockTargetLocked ? 'unlock' : 'lock'}
        tone={lockTargetLocked ? 'primary' : 'danger'}
        title={lockTargetLocked ? 'Mở khóa tài khoản?' : 'Khóa tài khoản?'}
        message={
          <>
            {lockTargetLocked
              ? 'Người dùng sẽ có thể đăng nhập trở lại với tài khoản '
              : 'Người dùng sẽ không thể đăng nhập bằng tài khoản '}
            <span className="font-semibold text-ink">{lockTarget?.email}</span>.
          </>
        }
        confirmText={lockTargetLocked ? 'Mở khóa' : 'Khóa'}
        cancelText="Hủy"
        loading={busyId !== null && busyId === lockTarget?.userId}
        onConfirm={confirmToggleLock}
        onCancel={() => setLockTarget(null)}
      />

      {/* Modal tạo user */}
      <Modal open={createOpen} title="Create user" onClose={closeCreate}>
        <form className="space-y-3" onSubmit={onCreate}>
          <ErrorBox message={formError} />
          <Input
            label="Full name"
            value={createForm.fullName}
            onChange={(e) => setCreateForm((f) => ({ ...f, fullName: e.target.value }))}
            required
          />
          <Input
            label="Email"
            type="email"
            value={createForm.email}
            onChange={(e) => setCreateForm((f) => ({ ...f, email: e.target.value }))}
            required
          />
          <Input
            label="Password"
            type="password"
            value={createForm.password}
            onChange={(e) => setCreateForm((f) => ({ ...f, password: e.target.value }))}
            required
          />
          <Input
            label="Phone"
            value={createForm.phone}
            onChange={(e) => setCreateForm((f) => ({ ...f, phone: e.target.value }))}
            placeholder="Optional"
          />
          <Select
            label="Role"
            value={createForm.role}
            onChange={(e) => setCreateForm((f) => ({ ...f, role: e.target.value as UserRole }))}
          >
            <option value="OPERATOR">UAV and Device Operator</option>
            <option value="ADMIN">Farm Administrator</option>
          </Select>
          <div className="flex justify-end gap-2 pt-2">
            <Button onClick={closeCreate} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Creating...' : 'Save'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal sửa user */}
      <Modal open={editing !== null} title="Edit user" onClose={closeEdit}>
        <form className="space-y-3" onSubmit={onEdit}>
          <ErrorBox message={formError} />
          <Input label="Email" value={editing?.email ?? ''} disabled />
          <Input
            label="Full name"
            value={editForm.fullName}
            onChange={(e) => setEditForm((f) => ({ ...f, fullName: e.target.value }))}
            required
          />
          <Input
            label="Phone"
            value={editForm.phone}
            onChange={(e) => setEditForm((f) => ({ ...f, phone: e.target.value }))}
            placeholder="Optional"
          />
          <Select
            label="Role"
            value={editForm.role}
            disabled={editingSelf}
            onChange={(e) => setEditForm((f) => ({ ...f, role: e.target.value as UserRole }))}
          >
            <option value="OPERATOR">UAV and Device Operator</option>
            <option value="ADMIN">Farm Administrator</option>
          </Select>
          {editingSelf && (
            <p className="text-xs text-muted">Bạn không thể tự đổi vai trò của chính mình.</p>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button onClick={closeEdit} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Saving...' : 'Save'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}