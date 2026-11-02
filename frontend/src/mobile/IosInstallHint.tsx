function isIos() {
  const ua = navigator.userAgent
  return /iPad|iPhone|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
}

function isStandalone() {
  const nav = navigator as Navigator & { standalone?: boolean }
  return window.matchMedia('(display-mode: standalone)').matches || nav.standalone === true
}

/** iPhone 无 Mac、无开发者签名时，用 Safari「添加到主屏幕」安装同一套 /m 页面。 */
export function IosInstallHint() {
  if (!isIos() || isStandalone()) return null
  const inWeChat = /MicroMessenger/i.test(navigator.userAgent)
  return (
    <p className="mini-ios-install">
      {inWeChat
        ? '请点右上角，用 Safari 打开，再选「添加到主屏幕」。'
        : '安装到 iPhone：点底部分享按钮，选择「添加到主屏幕」。'}
    </p>
  )
}
