import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { brandingApi } from '../api'
import {
  applyThemeVars,
  DEFAULT_THEME_ID,
  getPreset,
  toAntdTheme,
  type ThemeId,
  type ThemePreset,
} from './presets'
import type { ThemeConfig } from 'antd'

const STORAGE_KEY = 'pm-ui-theme'

type ThemeContextValue = {
  themeId: ThemeId
  preset: ThemePreset
  antdTheme: ThemeConfig
  setThemeId: (id: ThemeId) => Promise<void>
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

function readCachedId(): ThemeId {
  try {
    return getPreset(localStorage.getItem(STORAGE_KEY)).id
  } catch {
    return DEFAULT_THEME_ID
  }
}

function applyAndCache(id: string | null | undefined): ThemeId {
  const preset = getPreset(id)
  applyThemeVars(preset)
  try {
    localStorage.setItem(STORAGE_KEY, preset.id)
  } catch {
    /* ignore */
  }
  return preset.id
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [themeId, setThemeIdState] = useState<ThemeId>(() => applyAndCache(readCachedId()))

  useEffect(() => {
    let cancelled = false
    brandingApi
      .get()
      .then((data) => {
        if (cancelled) return
        setThemeIdState(applyAndCache(data.themeId))
      })
      .catch(() => {
        /* keep cached theme if the server is unreachable */
      })
    return () => {
      cancelled = true
    }
  }, [])

  const setThemeId = async (id: ThemeId) => {
    const previous = themeId
    setThemeIdState(applyAndCache(id))
    try {
      const saved = await brandingApi.updateTheme(getPreset(id).id)
      setThemeIdState(applyAndCache(saved.themeId))
    } catch {
      setThemeIdState(applyAndCache(previous))
      throw new Error('主题未能保存到服务器')
    }
  }

  const value = useMemo(
    () => {
      const preset = getPreset(themeId)
      return {
        themeId: preset.id,
        preset,
        antdTheme: toAntdTheme(preset),
        setThemeId,
      }
    },
    [themeId],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useAppTheme() {
  const ctx = useContext(ThemeContext)
  if (!ctx) {
    throw new Error('useAppTheme 必须在 ThemeProvider 内使用')
  }
  return ctx
}
