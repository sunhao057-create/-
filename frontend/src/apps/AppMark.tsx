const TONES = ['blue', 'cyan', 'green', 'orange', 'purple', 'indigo', 'rose', 'sky'] as const

function appTone(seed: string) {
  let hash = 0
  for (let i = 0; i < seed.length; i += 1) hash = (hash * 31 + seed.charCodeAt(i)) >>> 0
  return TONES[hash % TONES.length]
}

export function AppMark({ label, seed, size = 'lg' }: { label: string; seed: string; size?: 'sm' | 'lg' }) {
  const text = label.trim().slice(0, 1) || '应'
  return <span className={`qf-app-mark is-${size} is-${appTone(seed)}`}>{text}</span>
}
