const KEY = 'qf-favorites'

function read(): string[] {
  try {
    const raw = localStorage.getItem(KEY)
    const parsed = raw ? (JSON.parse(raw) as unknown) : []
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch {
    return []
  }
}

function write(paths: string[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(paths))
  } catch {
    /* ignore */
  }
}

export function listFavorites() {
  return read()
}

export function isFavorite(path: string) {
  return read().includes(path)
}

export function toggleFavorite(path: string) {
  const current = read()
  const next = current.includes(path) ? current.filter((item) => item !== path) : [...current, path]
  write(next)
  return next
}
