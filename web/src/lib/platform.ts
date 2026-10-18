/** True when running from the home screen rather than a browser tab (iOS standalone mode). */
export const isStandalone = () =>
  window.matchMedia('(display-mode: standalone)').matches || (navigator as Navigator & { standalone?: boolean }).standalone === true
