/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

const { version } = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8')) as { version: string }

export default defineConfig({
  define: {
    __APP_VERSION__: JSON.stringify(version),
  },
  plugins: [
    react(),
    VitePWA({
      // A new deploy activates on the next launch without asking the user
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'apple-touch-icon-180x180.png'],
      manifest: {
        name: 'LiftLog',
        short_name: 'LiftLog',
        description: 'Log workouts and sets, and follow your strength progress.',
        lang: 'en',
        start_url: '/',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#15171c',
        theme_color: '#15171c',
        icons: [
          { src: 'pwa-64x64.png', sizes: '64x64', type: 'image/png' },
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: 'maskable-icon-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // Only the app shell is cached; API data always comes from the server so it is never stale
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        // The font packages ship every script; only Latin (with Turkish letters in latin-ext) is ever shown.
        // Other subsets still load from the server through unicode-range if a name ever needs them.
        globIgnores: ['**/*-vietnamese-*.woff2', '**/*-cyrillic*.woff2', '**/*-greek*.woff2'],
        navigateFallback: '/index.html',
      },
    }),
  ],
  build: {
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            {
              // React, the router and React Query change far less often than the app; in their own chunk a
              // new deploy only re-downloads the app code, not ~100 kB (gzip) of libraries
              name: 'react-vendor',
              test: /[\\/]node_modules[\\/](react|react-dom|scheduler|react-router|@tanstack[\\/][^\\/]+)[\\/]/,
            },
          ],
        },
      },
    },
  },
  server: {
    // Reachable from a phone on the same Wi-Fi for testing
    host: true,
    // Tests read the backend's food seed (src/lib/foodCatalogSeed.test.ts); the dev server never needs it
    ...(process.env.VITEST && { fs: { allow: ['.', '../backend/src/main/resources/db/migration'] } }),
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Above the 5 s that findBy* may wait (see src/test/setup.ts)
    testTimeout: 15000,
  },
})
