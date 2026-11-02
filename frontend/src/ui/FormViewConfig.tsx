import { useState } from 'react'
import { Button, Checkbox, Input, Radio, Select, Switch } from 'antd'
import {
  AppstoreOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  SafetyOutlined,
  SearchOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import type { DataViewDef, DataViewField, DataViewHeight, DataViewRange, DataViewVisibility } from '../types'

export type ViewDraft = Omit<DataViewDef, 'id' | 'module' | 'builtin' | 'createdById' | 'createdByName'>

const TABS = [
  { key: 'basic', label: '基础信息', icon: <InfoCircleOutlined /> },
  { key: 'display', label: '数据展示', icon: <AppstoreOutlined /> },
  { key: 'scope', label: '数据权限', icon: <SafetyOutlined /> },
  { key: 'button', label: '按钮配置', icon: <PlusOutlined /> },
  { key: 'action', label: '操作权限', icon: <SettingOutlined /> },
  { key: 'query', label: '查询条件', icon: <SearchOutlined /> },
] as const

export type ConfigTab = (typeof TABS)[number]['key']

function flag(extra: Record<string, unknown> | undefined, key: string, fallback = true) {
  return extra?.[key] === undefined ? fallback : extra[key] === true
}

function text(extra: Record<string, unknown> | undefined, key: string, fallback: string) {
  const value = extra?.[key]
  return typeof value === 'string' && value ? value : fallback
}

function list(extra: Record<string, unknown> | undefined, key: string): string[] {
  const value = extra?.[key]
  return Array.isArray(value) ? value.map(String) : []
}

export function visibilityOf(draft: ViewDraft): DataViewVisibility {
  const mode = text(draft.extra, 'visibilityMode', 'ALL')
  const scope = text(draft.extra, 'memberScope', 'ALL_MEMBERS')
  if (mode === 'ALL' || scope === 'ALL_MEMBERS' || scope === 'MEMBER') return 'ALL'
  if (scope === 'ADMIN') return 'ADMIN'
  return 'SELF'
}

export function FormViewConfig({
  draft,
  fields,
  section,
  saving,
  isEdit,
  onSection,
  onChange,
  onPreview,
  onSave,
  onClose,
}: {
  draft: ViewDraft
  fields: DataViewField[]
  section: ConfigTab
  saving?: boolean
  isEdit?: boolean
  onSection: (key: ConfigTab) => void
  onChange: (partial: Partial<ViewDraft>) => void
  onPreview: () => void
  onSave: () => void
  onClose: () => void
}) {
  const extra = draft.extra || {}
  const patchExtra = (partial: Record<string, unknown>) => onChange({ extra: { ...extra, ...partial } })
  const buttons = list(extra, 'listMainButtons')
  const moreButtons = list(extra, 'listMoreButtons')

  return (
    <aside className="qf-view-config">
      <div className="qf-view-config-head">
        <strong>视图配置</strong>
        <button type="button" className="qf-view-config-close" onClick={onClose} aria-label="关闭">
          ×
        </button>
      </div>
      <div className="qf-view-config-main">
        <nav className="qf-view-config-nav">
          {TABS.map((item) => (
            <button
              key={item.key}
              type="button"
              className={section === item.key ? 'is-active' : ''}
              onClick={() => onSection(item.key)}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
        <div className="qf-view-config-form">
          {section === 'basic' ? (
            <>
              <label>视图标题</label>
              <Input value={draft.name} onChange={(e) => onChange({ name: e.target.value })} />
              <div className="qf-view-switch">
                <span>备注信息</span>
                <Switch checked={flag(extra, 'remarkEnabled', false)} onChange={(remarkEnabled) => patchExtra({ remarkEnabled })} />
              </div>
              {flag(extra, 'remarkEnabled', false) ? (
                <Input.TextArea rows={3} value={draft.remark} onChange={(e) => onChange({ remark: e.target.value })} />
              ) : null}
              <div className="qf-view-switch">
                <span>固定至顶部导航</span>
                <Switch checked={draft.pinTop} onChange={(pinTop) => onChange({ pinTop })} />
              </div>
              <label>可见范围</label>
              <Select
                value={text(extra, 'visibilityMode', 'ALL')}
                onChange={(visibilityMode) => patchExtra({ visibilityMode })}
                options={[
                  { value: 'ALL', label: '全部可见' },
                  { value: 'PART', label: '部分可见' },
                ]}
              />
              {text(extra, 'visibilityMode', 'ALL') === 'PART' ? (
                <>
                  <label>工作区成员配置</label>
                  <Radio.Group
                    className="qf-view-radios"
                    value={text(extra, 'memberScope', 'ALL_MEMBERS')}
                    onChange={(e) => patchExtra({ memberScope: e.target.value })}
                  >
                    <Radio value="ADMIN">管理员可见</Radio>
                    <Radio value="MEMBER">成员可见</Radio>
                    <Radio value="ALL_MEMBERS">全部成员可见</Radio>
                    <Radio value="EXTERNAL">外部用户可见</Radio>
                    <Radio value="INVITE">仅限邀请</Radio>
                  </Radio.Group>
                </>
              ) : null}
            </>
          ) : null}
          {section === 'display' ? (
            <>
              <div className="qf-view-switch">
                <span>关联收集表视图</span>
                <Switch checked={flag(extra, 'relateCollect', false)} onChange={(relateCollect) => patchExtra({ relateCollect })} />
              </div>
              <label>自定义排序</label>
              <div className="qf-view-sort">
                <Select
                  allowClear
                  placeholder="请选择排序字段"
                  value={draft.sortField}
                  onChange={(sortField) => onChange({ sortField })}
                  options={fields.map((item) => ({ value: item.key, label: item.title }))}
                />
                <Select
                  value={draft.sortDir}
                  onChange={(sortDir: 'ASC' | 'DESC') => onChange({ sortDir })}
                  options={[
                    { value: 'DESC', label: '降序' },
                    { value: 'ASC', label: '升序' },
                  ]}
                />
              </div>
              <label>默认行高</label>
              <Radio.Group
                value={draft.rowHeight}
                onChange={(e) => onChange({ rowHeight: e.target.value as DataViewHeight })}
              >
                <Radio value="COMPACT">紧凑</Radio>
                <Radio value="MEDIUM">适中</Radio>
                <Radio value="TALL">较高</Radio>
              </Radio.Group>
              <label>视图显示字段</label>
              <div className="qf-view-fields">
                {fields.map((item) => (
                  <label key={item.key} className="qf-view-field">
                    <Checkbox
                      checked={draft.fields.includes(item.key)}
                      onChange={(e) => {
                        const next = e.target.checked
                          ? [...draft.fields, item.key]
                          : draft.fields.filter((key) => key !== item.key)
                        onChange({ fields: next })
                      }}
                    />
                    <span>{item.title}</span>
                  </label>
                ))}
              </div>
            </>
          ) : null}
          {section === 'scope' ? (
            <>
              <label>生效的权限</label>
              <Select
                value={text(extra, 'applyPermission', 'FOLLOW')}
                onChange={(applyPermission) => patchExtra({ applyPermission })}
                options={[
                  { value: 'FOLLOW', label: '跟随角色数据权限' },
                  { value: 'CUSTOM', label: '自定义权限' },
                ]}
              />
              <p className="qf-view-hint">自定义权限仅叠加当前视图，不会放宽角色已有的数据范围。</p>
              <label>数据范围</label>
              <Select
                value={draft.dataRange}
                onChange={(dataRange: DataViewRange) => onChange({ dataRange })}
                options={[
                  { value: 'ALL', label: '全部数据' },
                  { value: 'MINE', label: '我的数据' },
                  { value: 'RELATED', label: '关于自己的数据' },
                  { value: 'STARTED', label: '我发起的' },
                  { value: 'DONE', label: '已办数据' },
                ]}
              />
              <div className="qf-view-field-perm-head">
                <label>字段权限</label>
                <span>
                  <button type="button" onClick={() => onChange({ fields: fields.map((item) => item.key) })}>
                    可见
                  </button>
                  <button type="button" onClick={() => onChange({ fields: [] })}>
                    隐藏
                  </button>
                </span>
              </div>
              {fields.map((item) => (
                <div key={item.key} className="qf-view-field-perm">
                  <span>{item.title}</span>
                  <button
                    type="button"
                    className={draft.fields.includes(item.key) ? 'is-on' : ''}
                    onClick={() => {
                      const next = draft.fields.includes(item.key)
                        ? draft.fields.filter((key) => key !== item.key)
                        : [...draft.fields, item.key]
                      onChange({ fields: next })
                    }}
                  >
                    {draft.fields.includes(item.key) ? '可见' : '隐藏'}
                  </button>
                </div>
              ))}
            </>
          ) : null}
          {section === 'button' ? (
            <>
              <label>视图按钮</label>
              <Select
                value={text(extra, 'viewButton', '查看')}
                onChange={(viewButton) => patchExtra({ viewButton })}
                options={[{ value: '查看', label: '查看按钮' }]}
              />
              <label>详情按钮</label>
              <Select
                value={text(extra, 'detailButton', '查看')}
                onChange={(detailButton) => patchExtra({ detailButton })}
                options={[{ value: '查看', label: '查看按钮' }]}
              />
              <label>列表按钮</label>
              <p className="qf-view-sub">主按钮</p>
              <ButtonList values={buttons} onChange={(listMainButtons) => patchExtra({ listMainButtons })} />
              <p className="qf-view-sub">更多按钮</p>
              <ButtonList values={moreButtons} onChange={(listMoreButtons) => patchExtra({ listMoreButtons })} />
            </>
          ) : null}
          {section === 'action' ? (
            <>
              <label>数据详情操作权限</label>
              {[
                ['allowDetail', '可查看详情'],
                ['allowFlowChart', '可查看流程图'],
                ['allowRobot', '可发起 Q-Robot、Webhook'],
                ['allowUrge', '可催办'],
                ['allowComment', '可评论'],
                ['allowPrint', '可打印'],
              ].map(([key, label]) => (
                <Checkbox
                  key={key}
                  checked={flag(extra, key, key === 'allowDetail')}
                  onChange={(e) => patchExtra({ [key]: e.target.checked })}
                >
                  {label}
                </Checkbox>
              ))}
            </>
          ) : null}
          {section === 'query' ? (
            <>
              <div className="qf-view-switch">
                <span>搜索</span>
                <Switch checked={draft.searchEnabled} onChange={(searchEnabled) => onChange({ searchEnabled })} />
              </div>
              <div className="qf-view-switch">
                <span>查询</span>
                <Switch checked={draft.queryEnabled} onChange={(queryEnabled) => onChange({ queryEnabled })} />
              </div>
            </>
          ) : null}
        </div>
      </div>
      <div className="qf-view-config-footer">
        <Button onClick={onPreview}>预览</Button>
        <Button className="qf-view-ok" type="primary" loading={saving} onClick={onSave}>
          {isEdit ? '保存' : '完成创建'}
        </Button>
      </div>
    </aside>
  )
}

function ButtonList({ values, onChange }: { values: string[]; onChange: (next: string[]) => void }) {
  const [name, setName] = useState('')
  const add = () => {
    if (!name.trim()) return
    onChange([...values, name.trim()])
    setName('')
  }
  return (
    <div className="qf-view-buttons">
      {values.map((item, index) => (
        <span key={`${item}-${index}`} className="qf-view-chip-btn">
          {item}
          <button type="button" onClick={() => onChange(values.filter((_, i) => i !== index))} aria-label="移除">
            ×
          </button>
        </span>
      ))}
      <Input
        size="small"
        placeholder="添加按钮"
        value={name}
        onChange={(e) => setName(e.target.value)}
        onPressEnter={add}
        suffix={<PlusOutlined onClick={add} />}
      />
    </div>
  )
}
