import { useEffect, useState } from 'react'
import { App, Button, Card, Input, Space, Tag, Typography } from 'antd'
import { cursorApi, type CursorTask } from '../../api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'

const STATUS_COLOR: Record<string, string> = {
  RUNNING: 'processing',
  PUBLISHING: 'processing',
  PUBLISHED: 'green',
  FAILED: 'red',
}

export function CursorPage() {
  const { message } = App.useApp()
  const { user } = useAuth()
  const [prompt, setPrompt] = useState('')
  const [tasks, setTasks] = useState<CursorTask[]>([])
  const [sending, setSending] = useState(false)
  const [follow, setFollow] = useState<Record<number, string>>({})

  const load = async () => {
    setTasks(await cursorApi.tasks())
  }

  useEffect(() => {
    if (user?.role !== 'ADMIN') return
    void load().catch((e) => message.error(e instanceof ApiError ? e.message : '加载失败'))
  }, [user?.role, message])

  useEffect(() => {
    if (!tasks.some((item) => item.status === 'RUNNING' || item.status === 'PUBLISHING')) return
    const timer = window.setInterval(() => {
      void cursorApi.tasks().then(setTasks).catch(() => undefined)
    }, 8000)
    return () => window.clearInterval(timer)
  }, [tasks])

  if (user?.role !== 'ADMIN') {
    return <Card>只有系统管理员可以使用</Card>
  }

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <Card title="让孙浩改系统">
        <Input.TextArea
          rows={5}
          maxLength={4000}
          value={prompt}
          placeholder="写下要改的需求。孙浩改完后会自动发布到线上。"
          onChange={(event) => setPrompt(event.target.value)}
        />
        <Button
          type="primary"
          style={{ marginTop: 12 }}
          loading={sending}
          onClick={() => {
            const text = prompt.trim()
            if (!text) {
              message.warning('请填写需求')
              return
            }
            setSending(true)
            void cursorApi
              .create(text)
              .then((task) => {
                setPrompt('')
                setTasks((rows) => [task, ...rows.filter((item) => item.id !== task.id)])
                if (task.status === 'FAILED') message.error(task.errorMessage || '没有提交成功')
                else message.success('已交给孙浩')
              })
              .catch((e) => message.error(e instanceof ApiError ? e.message : '提交失败'))
              .finally(() => setSending(false))
          }}
        >
          提交给孙浩
        </Button>
      </Card>
      {tasks.map((task) => (
        <Card
          key={task.id}
          title={task.prompt.split('\n')[0]}
          extra={<Tag color={STATUS_COLOR[task.status] || 'default'}>{task.statusLabel}</Tag>}
        >
          <Typography.Paragraph style={{ whiteSpace: 'pre-wrap' }}>{task.resultText || task.errorMessage || '孙浩还在处理。'}</Typography.Paragraph>
          <Space wrap>
            {task.agentUrl ? (
              <a href={task.agentUrl} target="_blank" rel="noreferrer">
                查看进度
              </a>
            ) : null}
            {task.prUrl ? (
              <a href={task.prUrl} target="_blank" rel="noreferrer">
                查看改动
              </a>
            ) : null}
            {task.branch ? <span>分支 {task.branch}</span> : null}
          </Space>
          {task.status !== 'RUNNING' && task.agentId ? (
            <Space.Compact style={{ display: 'flex', marginTop: 12 }}>
              <Input
                value={follow[task.id] || ''}
                placeholder="补充说明"
                onChange={(event) => setFollow((prev) => ({ ...prev, [task.id]: event.target.value }))}
              />
              <Button
                onClick={() => {
                  const text = (follow[task.id] || '').trim()
                  if (!text) return
                  void cursorApi
                    .follow(task.id, text)
                    .then((next) => {
                      setFollow((prev) => ({ ...prev, [task.id]: '' }))
                      setTasks((rows) => rows.map((item) => (item.id === next.id ? next : item)))
                    })
                    .catch((e) => message.error(e instanceof ApiError ? e.message : '补充失败'))
                }}
              >
                继续改
              </Button>
            </Space.Compact>
          ) : null}
        </Card>
      ))}
    </div>
  )
}
