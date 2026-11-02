import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button, Result } from 'antd'

type Props = { children: ReactNode }
type State = { error: Error | null }

export class PageErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(error, info.componentStack)
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <Result
        status="error"
        title="页面无法显示"
        subTitle={this.state.error.message || '渲染出错，请返回后重试。'}
        extra={
          <Button type="primary" onClick={() => this.setState({ error: null })}>
            重新加载本页
          </Button>
        }
      />
    )
  }
}
