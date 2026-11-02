import { CheckOutlined } from '@ant-design/icons'
import { App, Card, Radio, Typography } from 'antd'
import { THEME_PRESETS, type ThemeId } from '../theme/presets'
import { useAppTheme } from '../theme/ThemeContext'
import { useAuth } from '../auth/AuthContext'
import { can } from '../auth/permissions'

export function ThemePage() {
  const { message } = App.useApp()
  const { user } = useAuth()
  const { themeId, setThemeId } = useAppTheme()
  const canEdit = can(user, 'THEME', 'edit')

  return (
    <Card>
      <div className="theme-page-head">
        <h3>主题风格</h3>
        <p>勾选后立即作用于整站，并保存到服务器。换浏览器、换电脑也会使用同一套主题。</p>
      </div>
      <Radio.Group
        className="theme-grid"
        value={themeId}
        disabled={!canEdit}
        onChange={async (event) => {
          const next = event.target.value as ThemeId
          try {
            await setThemeId(next)
            message.success('主题已保存到服务器')
          } catch {
            message.error('主题未能保存，请稍后重试')
          }
        }}
      >
        {THEME_PRESETS.map((item) => {
          const selected = item.id === themeId
          return (
            <label key={item.id} className={`theme-card${selected ? ' is-selected' : ''}`}>
              <div
                className="theme-preview"
                style={{ background: item.vars['--app-bg-image'] !== 'none' ? item.vars['--app-bg-image'] : item.vars['--app-bg'] }}
              >
                <span className="theme-preview-sider" style={{ background: item.vars['--app-sider-bg'] }} />
                <span className="theme-preview-main">
                  <span style={{ background: item.vars['--app-header-bg'] }} />
                  <span style={{ background: item.primary }} />
                </span>
              </div>
              <div className="theme-card-body">
                <Radio value={item.id}>
                  <span className="theme-card-name">{item.name}</span>
                </Radio>
                {selected ? <CheckOutlined className="theme-card-check" /> : null}
              </div>
              <p>{item.desc}</p>
              <div className="theme-swatches">
                {item.swatches.map((color) => (
                  <i key={color} style={{ background: color }} />
                ))}
              </div>
              <Typography.Text type="secondary" className="theme-from">
                参考 {item.from}
              </Typography.Text>
            </label>
          )
        })}
      </Radio.Group>
    </Card>
  )
}
