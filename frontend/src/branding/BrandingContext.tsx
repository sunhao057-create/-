import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { brandingApi } from '../api'
import type { Branding } from '../types'

type BrandingContextValue = {
  branding: Branding
  reload: () => Promise<void>
}

const DEFAULT_BRANDING: Branding = { name: '项目管理', logoUrl: null }

const BrandingContext = createContext<BrandingContextValue | null>(null)

function applyDocumentBranding(branding: Branding) {
  document.title = branding.name || '项目管理'
  const href = branding.logoUrl
    ? `${branding.logoUrl}${branding.logoUrl.includes('?') ? '&' : '?'}v=${Date.now()}`
    : '/favicon.svg'
  const rels = ['icon', 'shortcut icon', 'apple-touch-icon']
  for (const rel of rels) {
    let link = document.querySelector<HTMLLinkElement>(`link[rel='${rel}']`)
    if (!link) {
      link = document.createElement('link')
      link.rel = rel
      document.head.appendChild(link)
    }
    if (branding.logoUrl) {
      link.removeAttribute('type')
    } else {
      link.type = 'image/svg+xml'
    }
    link.href = href
  }
}

export function BrandingProvider({ children }: { children: ReactNode }) {
  const [branding, setBranding] = useState<Branding>(DEFAULT_BRANDING)

  const reload = async () => {
    try {
      const data = await brandingApi.get()
      setBranding(data)
      applyDocumentBranding(data)
    } catch {
      setBranding(DEFAULT_BRANDING)
      applyDocumentBranding(DEFAULT_BRANDING)
    }
  }

  useEffect(() => {
    void reload()
  }, [])

  const value = useMemo(() => ({ branding, reload }), [branding])

  return <BrandingContext.Provider value={value}>{children}</BrandingContext.Provider>
}

export function useBranding() {
  const ctx = useContext(BrandingContext)
  if (!ctx) {
    throw new Error('useBranding 必须在 BrandingProvider 内使用')
  }
  return ctx
}
