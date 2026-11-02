import { PlusOutlined } from '@ant-design/icons'
import { Button, Checkbox, Form, Input } from 'antd'
import { SubformTable } from './SubformTable'

export function ContactLineList({ editable }: { editable: boolean }) {
  return (
    <Form.List name="contacts">
      {(fields, { add, remove }) => (
        <>
          <SubformTable
            fields={fields}
            onRemove={editable ? (name) => remove(name) : undefined}
            columns={[
              {
                title: '姓名',
                width: 140,
                render: (field) => (
                  <Form.Item
                    {...field}
                    name={[field.name, 'name']}
                    rules={editable ? [{ required: true, message: '姓名' }] : undefined}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="姓名" disabled={!editable} />
                  </Form.Item>
                ),
              },
              {
                title: '职务',
                width: 140,
                narrow: true,
                render: (field) => (
                  <Form.Item {...field} name={[field.name, 'title']} style={{ marginBottom: 0 }}>
                    <Input placeholder="职务" disabled={!editable} />
                  </Form.Item>
                ),
              },
              {
                title: '电话',
                width: 140,
                narrow: true,
                render: (field) => (
                  <Form.Item {...field} name={[field.name, 'phone']} style={{ marginBottom: 0 }}>
                    <Input placeholder="电话" disabled={!editable} />
                  </Form.Item>
                ),
              },
              {
                title: '邮箱',
                width: 200,
                render: (field) => (
                  <Form.Item {...field} name={[field.name, 'email']} style={{ marginBottom: 0 }}>
                    <Input placeholder="邮箱" disabled={!editable} />
                  </Form.Item>
                ),
              },
              {
                title: '主联系人',
                width: 110,
                narrow: true,
                render: (field) => (
                  <Form.Item {...field} name={[field.name, 'primaryContact']} valuePropName="checked" style={{ marginBottom: 0 }}>
                    <Checkbox disabled={!editable}>主联系人</Checkbox>
                  </Form.Item>
                ),
              },
            ]}
          />
          {editable ? (
            <Button type="dashed" onClick={() => add({ primaryContact: fields.length === 0 })} block icon={<PlusOutlined />}>
              添加联系人
            </Button>
          ) : null}
        </>
      )}
    </Form.List>
  )
}

export function BankAccountLineList({ editable }: { editable: boolean }) {
  return (
    <Form.List name="bankAccounts">
      {(fields, { add, remove }) => (
        <>
          <SubformTable
            fields={fields}
            onRemove={editable ? (name) => remove(name) : undefined}
            columns={[
              {
                title: '开户名',
                width: 160,
                render: (field) => (
                  <Form.Item
                    {...field}
                    name={[field.name, 'accountName']}
                    rules={editable ? [{ required: true, message: '开户名' }] : undefined}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="开户名" disabled={!editable} />
                  </Form.Item>
                ),
              },
              {
                title: '开户银行',
                width: 160,
                narrow: true,
                render: (field) => (
                  <Form.Item
                    {...field}
                    name={[field.name, 'bankName']}
                    rules={editable ? [{ required: true, message: '开户银行' }] : undefined}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="开户银行" disabled={!editable} />
                  </Form.Item>
                ),
              },
              {
                title: '银行账号',
                width: 200,
                render: (field) => (
                  <Form.Item
                    {...field}
                    name={[field.name, 'accountNo']}
                    rules={editable ? [{ required: true, message: '账号' }] : undefined}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="银行账号" disabled={!editable} />
                  </Form.Item>
                ),
              },
              {
                title: '开户支行',
                width: 160,
                render: (field) => (
                  <Form.Item {...field} name={[field.name, 'branch']} style={{ marginBottom: 0 }}>
                    <Input placeholder="开户支行" disabled={!editable} />
                  </Form.Item>
                ),
              },
              {
                title: '默认账户',
                width: 110,
                narrow: true,
                render: (field) => (
                  <Form.Item {...field} name={[field.name, 'primaryAccount']} valuePropName="checked" style={{ marginBottom: 0 }}>
                    <Checkbox disabled={!editable}>默认账户</Checkbox>
                  </Form.Item>
                ),
              },
            ]}
          />
          {editable ? (
            <Button type="dashed" onClick={() => add({ primaryAccount: fields.length === 0 })} block icon={<PlusOutlined />}>
              添加银行账户
            </Button>
          ) : null}
        </>
      )}
    </Form.List>
  )
}
