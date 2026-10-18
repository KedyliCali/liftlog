/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

interface ImportMetaEnv {
  /** Base URL of the LiftLog API, e.g. https://liftlog-api.onrender.com */
  readonly VITE_API_URL?: string
}

/** package.json version, injected at build time by vite.config.ts */
declare const __APP_VERSION__: string
