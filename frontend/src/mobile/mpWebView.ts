export function markMpWebView() {
  if (typeof window === 'undefined') return false
  const ua = navigator.userAgent || ''
  const env = (window as unknown as { __wxjs_environment?: string }).__wxjs_environment
  const query = new URLSearchParams(window.location.search)
  const hit =
    env === 'miniprogram' ||
    /miniProgram/i.test(ua) ||
    query.get('webview') === '1'
  if (hit) {
    document.documentElement.classList.add('is-mp-webview')
  }
  return hit
}
