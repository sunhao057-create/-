import { Input, Modal } from 'antd'

export function askQfSubmit(opts?: {
  nextNodeName?: string | null
  assignees?: string[]
}): Promise<string | null> {
  return new Promise((resolve) => {
    let comment = ''
    const next = opts?.nextNodeName || '下一办理人'
    const people = opts?.assignees?.filter(Boolean).join('、')
    Modal.confirm({
      title: '提交数据',
      icon: null,
      centered: true,
      className: 'qf-submit-confirm',
      okText: '提交',
      cancelText: '取消',
      content: (
        <div>
          <div style={{ marginBottom: 8 }}>
            提交后进入：<b>{next}</b>
            {people ? `（${people}）` : ''}
          </div>
          <Input.TextArea
            rows={3}
            placeholder="审批意见（选填）"
            onChange={(event) => {
              comment = event.target.value
            }}
          />
        </div>
      ),
      onOk: () => resolve(comment),
      onCancel: () => resolve(null),
    })
  })
}
