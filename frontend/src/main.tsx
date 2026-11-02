import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import 'antd/dist/reset.css'
import './theme/theme.css'
import './index.css'
import App from './App.tsx'
import { markMpWebView } from './mobile/mpWebView.ts'

markMpWebView()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
