import { useEffect, useMemo, useState } from 'react'
import {
  App,
  Avatar,
  Button,
  Dropdown,
  Form,
  Input,
  Modal,
  Select,
  Tree,
  TreeSelect,
} from 'antd'
import { MoreOutlined, PlusOutlined, SearchOutlined, UserOutlined } from '@ant-design/icons'
import { useLocation } from 'react-router-dom'
import { orgApi, roleApi, userApi } from '../../api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { can } from '../../auth/permissions'
import { buildDeptTree, deptPath, descendantIds } from '../../org/deptTree'
import { PermissionTree } from '../../role/PermissionTree'
import { allAllowedPermissions, emptyPermissions, mergePermissions } from '../../role/modules'
import type { FormPermission, OrgDepartment, RoleItem, User } from '../../types'
import { EntityDrawer } from '../../ui/CreateDrawer'
import { ListExportButton } from '../../export/ListExportButton'

type SideTab = 'org' | 'roles'
type Bucket = 'all' | 'unassigned' | 'pending' | 'disabled'

function genPassword() {
  return `Qm${Math.random().toString(36).slice(2, 10)}A1`
}

function dashPath(depts: OrgDepartment[], id?: number | null) {
  return deptPath(depts, id).replace(/ \//g, '-')
}

export function ContactsDirectory() {
  const { message, modal } = App.useApp()
  const { user: me, refresh } = useAuth()
  const location = useLocation()
  const canUser = can(me, 'USER', 'view')
  const canUserCreate = can(me, 'USER', 'create')
  const canUserEdit = can(me, 'USER', 'edit')
  const canUserDelete = can(me, 'USER', 'delete')
  const canUserExport = can(me, 'USER', 'importExport')
  const canOrg = can(me, 'ORG', 'view')
  const canOrgCreate = can(me, 'ORG', 'create')
  const canOrgEdit = can(me, 'ORG', 'edit')
  const canOrgDelete = can(me, 'ORG', 'delete')
  const canRole = can(me, 'ROLE', 'view')
  const canRoleCreate = can(me, 'ROLE', 'create')
  const canRoleEdit = can(me, 'ROLE', 'edit')
  const canRoleDelete = can(me, 'ROLE', 'delete')

  const [topTab, setTopTab] = useState<'members' | 'external'>('members')
  const [side, setSide] = useState<SideTab>(location.pathname.includes('/roles') ? 'roles' : 'org')
  const [bucket, setBucket] = useState<Bucket>('all')
  const [deptId, setDeptId] = useState<number | null>(null)
  const [roleCode, setRoleCode] = useState<string | null>(null)
  const [keyword, setKeyword] = useState('')
  const [users, setUsers] = useState<User[]>([])
  const [depts, setDepts] = useState<OrgDepartment[]>([])
  const [roles, setRoles] = useState<RoleItem[]>([])
  const [createOpen, setCreateOpen] = useState(false)
  const [pickOpen, setPickOpen] = useState(false)
  const [editUser, setEditUser] = useState<User | null>(null)
  const [deptOpen, setDeptOpen] = useState(false)
  const [deptParentId, setDeptParentId] = useState<number | null>(null)
  const [editingDept, setEditingDept] = useState<OrgDepartment | null>(null)
  const [roleOpen, setRoleOpen] = useState(false)
  const [editingRole, setEditingRole] = useState<RoleItem | null>(null)
  const [perms, setPerms] = useState<FormPermission[]>(emptyPermissions())
  const [createdSecret, setCreatedSecret] = useState<string | null>(null)
  const [deptForm] = Form.useForm<{ name: string; leaderId?: number }>()
  const [roleForm] = Form.useForm<{ name: string; description?: string }>()
  const [createForm] = Form.useForm()
  const [pickForm] = Form.useForm<{ userIds: number[] }>()
  const deptTree = useMemo(() => buildDeptTree(depts), [depts])
  const roleOptions = roles.filter((item) => item.code !== 'PUBLIC_GUEST').map((item) => ({ value: item.code, label: item.name }))

  const reload = async () => {
    if (canUser) {
      try {
        setUsers((await userApi.list({ page: 1, size: 500 })).items)
      } catch (e) {
        message.error(e instanceof ApiError ? e.message : '加载成员失败')
      }
    }
    if (canOrg) {
      try {
        setDepts(await orgApi.departments())
      } catch {
        setDepts([])
      }
    }
    if (canRole) {
      try {
        setRoles(await roleApi.list())
      } catch {
        setRoles([])
      }
    }
  }

  useEffect(() => {
    void reload()
  }, [])

  const members = users.filter((item) => item.role !== 'PUBLIC_GUEST' && item.accountType !== 'EXTERNAL')
  const counts = {
    all: members.filter((item) => item.status === 'ENABLED').length,
    unassigned: members.filter((item) => !item.departmentId && item.status === 'ENABLED').length,
    pending: members.filter((item) => item.mustChangePassword && item.status === 'ENABLED').length,
    disabled: members.filter((item) => item.status === 'DISABLED').length,
  }
  const selectedDept = depts.find((item) => item.id === deptId) || null
  const selectedRole = roles.find((item) => item.code === roleCode) || null

  const visibleUsers = useMemo(() => {
    const source = topTab === 'external' ? users.filter((item) => item.accountType === 'EXTERNAL') : members
    const q = keyword.trim().toLowerCase()
    return source.filter((item) => {
      if (q && !`${item.displayName}${item.username}${item.departmentName || ''}`.toLowerCase().includes(q)) return false
      if (side === 'roles' && roleCode) return item.role === roleCode
      if (bucket === 'unassigned') return !item.departmentId && item.status === 'ENABLED'
      if (bucket === 'pending') return item.mustChangePassword && item.status === 'ENABLED'
      if (bucket === 'disabled') return item.status === 'DISABLED'
      if (deptId) return descendantIds(depts, deptId).includes(item.departmentId || 0)
      return item.status === 'ENABLED'
    })
  }, [users, members, topTab, keyword, side, roleCode, bucket, deptId, depts])

  const openCreate = () => {
    createForm.resetFields()
    createForm.setFieldsValue({
      departmentId: deptId || undefined,
      role: roleCode || 'USER',
      accountType: topTab === 'external' ? 'EXTERNAL' : 'INTERNAL',
    })
    setCreateOpen(true)
  }

  const openPick = () => {
    pickForm.resetFields()
    setPickOpen(true)
  }

  const openDept = (parentId: number | null, dept?: OrgDepartment | null) => {
    setDeptParentId(parentId)
    setEditingDept(dept || null)
    deptForm.setFieldsValue({
      name: dept?.name,
      leaderId: members.find((item) => item.departmentId === dept?.id && item.deptLeader)?.id,
    })
    setDeptOpen(true)
  }

  const openRole = (role?: RoleItem) => {
    setEditingRole(role || null)
    roleForm.setFieldsValue({ name: role?.name, description: role?.description ?? undefined })
    setPerms(role?.code === 'ADMIN' ? allAllowedPermissions() : mergePermissions(role?.permissions))
    setRoleOpen(true)
  }

  const setLeader = async (user: User, leader: boolean) => {
    if (!user.departmentId || !canOrgEdit) return
    try {
      await orgApi.assignMember(user.departmentId, { userIds: [user.id], deptLeader: leader })
      await reload()
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '设置失败')
    }
  }

  const pickable = members.filter((item) => {
    if (side === 'roles' && roleCode) return item.role !== roleCode
    if (deptId) return item.departmentId !== deptId
    return true
  })

  const treeData = useMemo(() => {
    const walk = (nodes: ReturnType<typeof buildDeptTree>): typeof nodes =>
      nodes.map((node) => {
        const dept = depts.find((item) => item.id === node.key)
        return {
          ...node,
          title: (
            <span className="qf-contacts-dept">
              <span>{node.title}</span>
              {canOrgCreate || canOrgEdit || canOrgDelete ? (
                <Dropdown
                  trigger={['click']}
                  menu={{
                    items: [
                      canOrgCreate ? { key: 'child', label: '添加子部门', onClick: () => openDept(node.key, null) } : null,
                      canOrgEdit ? { key: 'add', label: '添加成员', onClick: () => { setDeptId(node.key); setBucket('all'); openPick() } } : null,
                      canOrgEdit ? { key: 'rename', label: '重命名', onClick: () => dept && openDept(dept.parentId ?? null, dept) } : null,
                      canOrgDelete
                        ? {
                            key: 'del',
                            label: '删除',
                            danger: true,
                            onClick: () => {
                              modal.confirm({
                                title: '删除部门',
                                content: `确认删除「${node.title}」？`,
                                okButtonProps: { danger: true },
                                onOk: async () => {
                                  await orgApi.removeDepartment(node.key)
                                  if (deptId === node.key) setDeptId(null)
                                  message.success('已删除')
                                  await reload()
                                },
                              })
                            },
                          }
                        : null,
                    ].filter(Boolean) as { key: string; label: string }[],
                  }}
                >
                  <MoreOutlined className="qf-contacts-more" onClick={(e) => e.stopPropagation()} />
                </Dropdown>
              ) : null}
            </span>
          ),
          children: node.children ? walk(node.children) : undefined,
        }
      })
    const q = keyword.trim().toLowerCase()
    const filter = (nodes: ReturnType<typeof buildDeptTree>): typeof nodes =>
      nodes
        .map((node) => ({ ...node, children: node.children ? filter(node.children) : undefined }))
        .filter((node) => !q || String(node.title).toLowerCase().includes(q) || (node.children && node.children.length))
    return q ? filter(walk(deptTree)) : walk(deptTree)
  }, [deptTree, depts, canOrgCreate, canOrgEdit, canOrgDelete, keyword, deptId])

  return (
    <div className="qf-contacts">
      <div className="qf-contacts-top">
        <div className="qf-contacts-tabs">
          <button type="button" className={topTab === 'members' ? 'is-active' : ''} onClick={() => setTopTab('members')}>成员</button>
          <button type="button" className={topTab === 'external' ? 'is-active' : ''} onClick={() => setTopTab('external')}>外部用户</button>
        </div>
      </div>
      <div className="qf-contacts-body">
        <aside className="qf-contacts-side">
          <Input allowClear prefix={<SearchOutlined />} placeholder="搜索成员、部门或角色" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
          <div className="qf-contacts-side-tabs">
            {canOrg || canUser ? (
              <button type="button" className={side === 'org' ? 'is-active' : ''} onClick={() => { setSide('org'); setRoleCode(null) }}>企业架构</button>
            ) : null}
            {canRole ? (
              <button type="button" className={side === 'roles' ? 'is-active' : ''} onClick={() => { setSide('roles'); setDeptId(null); setBucket('all') }}>角色</button>
            ) : null}
          </div>
          {side === 'org' ? (
            <>
              {([
                ['all', '全部成员', counts.all],
                ['unassigned', '未分配成员', counts.unassigned],
                ['pending', '未接受邀请成员', counts.pending],
                ['disabled', '已禁用成员', counts.disabled],
              ] as const).map(([key, label, count]) => (
                <button key={key} type="button" className={`qf-contacts-bucket${bucket === key && !deptId ? ' is-active' : ''}`} onClick={() => { setBucket(key); setDeptId(null) }}>
                  <span>{label}</span><em>{count}</em>
                </button>
              ))}
              {canOrgCreate ? (
                <button type="button" className="qf-contacts-add-dept" onClick={() => openDept(null, null)}>
                  <PlusOutlined /> 添加部门
                </button>
              ) : null}
              <Tree blockNode defaultExpandAll selectedKeys={deptId ? [deptId] : []} treeData={treeData} onSelect={(keys) => { setDeptId(keys.length ? Number(keys[0]) : null); setBucket('all') }} />
            </>
          ) : (
            <>
              {canRoleCreate ? (
                <button type="button" className="qf-contacts-add-dept" onClick={() => openRole()}>
                  <PlusOutlined /> 添加角色
                </button>
              ) : null}
              {roles.filter((item) => item.code !== 'PUBLIC_GUEST' && (!keyword || item.name.includes(keyword))).map((role) => (
                <div key={role.id} className={`qf-contacts-role${roleCode === role.code ? ' is-active' : ''}`}>
                  <button type="button" className="qf-contacts-bucket" onClick={() => setRoleCode(role.code)}>
                    <span>{role.name}</span>
                    <em>{members.filter((item) => item.role === role.code).length}</em>
                  </button>
                  <Dropdown
                    trigger={['click']}
                    menu={{
                      items: [
                        { key: 'edit', label: '编辑权限', onClick: () => openRole(role) },
                        canRoleDelete && !role.builtin
                          ? {
                              key: 'del',
                              label: '删除',
                              danger: true,
                              onClick: () => {
                                modal.confirm({
                                  title: '删除角色',
                                  content: `确认删除「${role.name}」？`,
                                  okButtonProps: { danger: true },
                                  onOk: async () => {
                                    await roleApi.remove(role.id)
                                    if (roleCode === role.code) setRoleCode(null)
                                    message.success('已删除')
                                    await reload()
                                  },
                                })
                              },
                            }
                          : null,
                      ].filter(Boolean) as { key: string; label: string }[],
                    }}
                  >
                    <MoreOutlined className="qf-contacts-more" />
                  </Dropdown>
                </div>
              ))}
            </>
          )}
        </aside>
        <main className="qf-contacts-main">
          <div className="qf-contacts-toolbar">
            <strong>
              {selectedDept ? selectedDept.name : selectedRole ? selectedRole.name : { all: '全部成员', unassigned: '未分配成员', pending: '未接受邀请成员', disabled: '已禁用成员' }[bucket]}
              <em> {visibleUsers.length}</em>
            </strong>
            <div className="qf-contacts-actions">
              {canUserExport ? <ListExportButton module="USER" fallbackName="成员.xlsx" idleLabel="导出" /> : null}
              {selectedDept && canOrgCreate ? (
                <Button onClick={() => openDept(selectedDept.id, null)}>添加子部门</Button>
              ) : null}
              {selectedDept && canOrgEdit ? (
                <Button className="qf-view-ok" type="primary" onClick={openPick}>添加成员</Button>
              ) : selectedRole && canUserEdit ? (
                <Button className="qf-view-ok" type="primary" onClick={openPick}>添加成员</Button>
              ) : canUserCreate ? (
                <Dropdown
                  menu={{
                    items: [
                      { key: 'create', label: '创建新成员', onClick: openCreate },
                      canOrgEdit ? { key: 'pick', label: '从通讯录选择', onClick: openPick } : null,
                    ].filter(Boolean) as { key: string; label: string }[],
                  }}
                >
                  <Button className="qf-view-ok" type="primary">添加成员</Button>
                </Dropdown>
              ) : null}
            </div>
          </div>
          <table className="qf-contacts-table">
            <thead>
              <tr>
                <th>成员</th>
                <th style={{ width: 120 }}>职位</th>
                <th>部门</th>
              </tr>
            </thead>
            <tbody>
              {visibleUsers.map((item) => (
                <tr key={item.id}>
                  <td onClick={() => canUser && setEditUser(item)}>
                    <div className="qf-contacts-person">
                      <Avatar src={item.avatarUrl || undefined} icon={<UserOutlined />} />
                      <span>
                        <strong>{item.displayName}</strong>
                        <em>{item.username}</em>
                      </span>
                    </div>
                  </td>
                  <td>
                    <Select
                      size="small"
                      value={item.deptLeader ? 'leader' : 'member'}
                      disabled={!canOrgEdit || !item.departmentId}
                      style={{ width: 88 }}
                      options={[
                        { value: 'member', label: '成员' },
                        { value: 'leader', label: '主管' },
                      ]}
                      onChange={(value) => void setLeader(item, value === 'leader')}
                    />
                  </td>
                  <td onClick={() => canUser && setEditUser(item)}>{dashPath(depts, item.departmentId) || item.departmentName || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {visibleUsers.length === 0 ? <div className="qf-contacts-empty">暂无成员</div> : null}
        </main>
      </div>

      <EntityDrawer title="创建新成员" open={createOpen} onClose={() => setCreateOpen(false)} footer={null}>
        <Form
          form={createForm}
          layout="vertical"
          onFinish={async (values) => {
            try {
              const password = genPassword()
              await userApi.create({
                username: values.username,
                displayName: values.displayName,
                role: values.role,
                password,
                departmentId: canOrgEdit ? values.departmentId : undefined,
                deptLeader: canOrgEdit ? values.deptLeader : undefined,
                accountType: values.accountType || 'INTERNAL',
              })
              setCreateOpen(false)
              setCreatedSecret(`${values.displayName} / ${values.username}  初始密码 ${password}`)
              await reload()
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '创建失败')
            }
          }}
        >
          <Form.Item name="displayName" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}><Input /></Form.Item>
          <Form.Item name="username" label="手机号 / 账号" rules={[{ required: true, message: '请输入账号' }]}><Input placeholder="用于登录，不能为空" /></Form.Item>
          <Form.Item name="role" label="角色" initialValue="USER" rules={[{ required: true }]}><Select options={roleOptions} /></Form.Item>
          <Form.Item name="accountType" label="账号类型" initialValue="INTERNAL" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'INTERNAL', label: '内部账号（小程序打开手机版网页，权限跟角色走）' },
                { value: 'EXTERNAL', label: '外部账号（客户小程序和网页都能登录，权限跟角色走）' },
              ]}
            />
          </Form.Item>
          {canOrgEdit ? (
            <Form.Item name="departmentId" label="所属部门">
              <TreeSelect allowClear treeDefaultExpandAll treeLine placeholder="未分配" treeData={deptTree} />
            </Form.Item>
          ) : null}
          <Button type="primary" htmlType="submit" block>添加</Button>
        </Form>
      </EntityDrawer>

      <Modal
        title={selectedRole ? `为「${selectedRole.name}」添加成员` : selectedDept ? `为「${selectedDept.name}」添加成员` : '从通讯录选择'}
        open={pickOpen}
        onCancel={() => setPickOpen(false)}
        onOk={() => pickForm.submit()}
        okText="添加"
      >
        <Form
          form={pickForm}
          onFinish={async (values) => {
            try {
              if (selectedDept) {
                await orgApi.assignMember(selectedDept.id, { userIds: values.userIds })
              } else if (selectedRole) {
                await Promise.all(values.userIds.map((id: number) => {
                  const user = users.find((item) => item.id === id)
                  return userApi.update(id, { displayName: user?.displayName || '', role: selectedRole.code, departmentId: user?.departmentId, deptLeader: user?.deptLeader })
                }))
              }
              message.success('已添加')
              setPickOpen(false)
              await reload()
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '添加失败')
            }
          }}
        >
          <Form.Item name="userIds" rules={[{ required: true, type: 'array', min: 1, message: '请选择成员' }]}>
            <Select mode="multiple" showSearch optionFilterProp="label" placeholder="选择成员" options={pickable.map((item) => ({ value: item.id, label: `${item.displayName}（${item.username}）` }))} />
          </Form.Item>
        </Form>
      </Modal>

      <EntityDrawer title="成员信息" open={!!editUser} onClose={() => setEditUser(null)} footer={null}>
        {editUser ? (
          <Form
            layout="vertical"
            disabled={!canUserEdit}
            initialValues={editUser}
            onFinish={async (values) => {
              try {
                await userApi.update(editUser.id, values)
                message.success('已保存')
                setEditUser(null)
                await reload()
              } catch (e) {
                message.error(e instanceof ApiError ? e.message : '保存失败')
              }
            }}
          >
            <Form.Item label="账号"><Input value={editUser.username} disabled /></Form.Item>
            <Form.Item name="displayName" label="姓名" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="role" label="角色"><Select options={roleOptions} /></Form.Item>
            <Form.Item name="accountType" label="账号类型" initialValue="INTERNAL">
              <Select
                options={[
                  { value: 'INTERNAL', label: '内部账号（小程序打开手机版网页）' },
                  { value: 'EXTERNAL', label: '外部账号（客户小程序）' },
                ]}
              />
            </Form.Item>
            {canOrgEdit ? (
              <Form.Item name="departmentId" label="所属部门">
                <TreeSelect allowClear treeDefaultExpandAll treeLine placeholder="未分配" treeData={deptTree} />
              </Form.Item>
            ) : (
              <Form.Item label="所属部门"><Input disabled value={dashPath(depts, editUser.departmentId) || '未分配'} /></Form.Item>
            )}
            {canUserEdit ? <Button type="primary" htmlType="submit" block>保存</Button> : null}
            <div className="qf-contacts-edit-actions">
              {canUserEdit && editUser.id !== me?.id ? (
                <Button onClick={async () => {
                  await userApi.updateStatus(editUser.id, editUser.status === 'ENABLED' ? 'DISABLED' : 'ENABLED')
                  message.success(editUser.status === 'ENABLED' ? '已禁用成员' : '已启用成员')
                  setEditUser(null)
                  await reload()
                }}>
                  {editUser.status === 'ENABLED' ? '禁用成员' : '启用成员'}
                </Button>
              ) : null}
              {canOrgEdit && editUser.departmentId ? (
                <Button onClick={async () => {
                  await orgApi.removeMember(editUser.departmentId!, editUser.id)
                  message.success('已移出部门')
                  setEditUser(null)
                  await reload()
                }}>
                  移出部门
                </Button>
              ) : null}
              {canUserDelete && editUser.id !== me?.id ? (
                <Button danger onClick={() => {
                  modal.confirm({
                    title: '移出工作区',
                    content: `确认将「${editUser.displayName}」移出通讯录？`,
                    okButtonProps: { danger: true },
                    onOk: async () => {
                      await userApi.remove(editUser.id)
                      message.success('已移出工作区')
                      setEditUser(null)
                      await reload()
                    },
                  })
                }}>
                  移出工作区
                </Button>
              ) : null}
            </div>
          </Form>
        ) : null}
      </EntityDrawer>

      <Modal
        title={editingDept ? '重命名' : deptParentId ? '添加子部门' : '添加部门'}
        open={deptOpen}
        onCancel={() => setDeptOpen(false)}
        onOk={() => deptForm.submit()}
        okText="确定"
      >
        <Form
          form={deptForm}
          layout="vertical"
          onFinish={async (values) => {
            try {
              if (editingDept) {
                await orgApi.updateDepartment(editingDept.id, { name: values.name, parentId: editingDept.parentId })
              } else {
                const created = await orgApi.createDepartment({ name: values.name, parentId: deptParentId })
                if (values.leaderId) {
                  await orgApi.assignMember(created.id, { userIds: [values.leaderId], deptLeader: true })
                }
              }
              message.success('已保存')
              setDeptOpen(false)
              await reload()
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '保存失败')
            }
          }}
        >
          <Form.Item name="name" label="部门名称" rules={[{ required: true, message: '请填写部门名称' }]}><Input /></Form.Item>
          {editingDept ? null : (
            <Form.Item name="leaderId" label="部门主管">
              <Select allowClear showSearch optionFilterProp="label" placeholder="从通讯录选择" options={members.map((item) => ({ value: item.id, label: item.displayName }))} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <EntityDrawer title={editingRole ? '编辑角色' : '添加角色'} open={roleOpen} onClose={() => setRoleOpen(false)} footer={null}>
        <Form
          form={roleForm}
          layout="vertical"
          onFinish={async (values) => {
            try {
              const payload = { ...values, permissions: perms }
              if (editingRole) await roleApi.update(editingRole.id, payload)
              else await roleApi.create(payload)
              message.success('已保存')
              setRoleOpen(false)
              await reload()
              await refresh()
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '保存失败')
            }
          }}
        >
          <Form.Item name="name" label="角色名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="description" label="说明"><Input.TextArea rows={2} /></Form.Item>
          <Form.Item label="权限控制">
            <PermissionTree
              value={perms}
              disabled={editingRole?.code === 'ADMIN' || (editingRole ? !canRoleEdit : !canRoleCreate)}
              onChange={setPerms}
            />
          </Form.Item>
          {editingRole?.code === 'ADMIN' ? null : <Button type="primary" htmlType="submit" block>确定</Button>}
        </Form>
      </EntityDrawer>

      <Modal title="已创建成员" open={!!createdSecret} onOk={() => setCreatedSecret(null)} onCancel={() => setCreatedSecret(null)} cancelButtonProps={{ style: { display: 'none' } }}>
        <p>成员已加入通讯录，处于「未接受邀请」。请把账号和初始密码发给对方，首次登录后需改密。</p>
        <p>{createdSecret}</p>
      </Modal>
    </div>
  )
}
