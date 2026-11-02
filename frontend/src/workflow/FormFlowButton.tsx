import { useState } from 'react'
import { App, Button } from 'antd'
import { useNavigate } from 'react-router-dom'
import { workflowApi } from '../api'
import { useAuth } from '../auth/AuthContext'
import { can } from '../auth/permissions'
import { formListPath } from './WorkflowBar'

/** 打开这张表已绑定的流程。需本表「流程」权限，或流程引擎编辑权。 */
export function FormFlowButton({ module }: { module: string }) {
  const { user } = useAuth()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  if (!module || (!can(user, module, 'flow') && !can(user, 'WORKFLOW', 'edit'))) return null
  return (
    <Button
      loading={loading}
      onClick={async () => {
        setLoading(true)
        try {
          const wf = await workflowApi.forForm(module)
          navigate(`/workflows/${wf.id}`, { state: { fromForm: module, fromPath: formListPath(module) } })
        } catch (err) {
          message.error(err instanceof Error ? err.message : '打不开这张表的流程')
        } finally {
          setLoading(false)
        }
      }}
    >
      流程设置
    </Button>
  )
}
