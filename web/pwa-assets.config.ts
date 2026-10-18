import { defineConfig, minimal2023Preset } from '@vite-pwa/assets-generator/config'

// Generates the PNG app icons (Android, iOS home screen) from the SVG favicon
export default defineConfig({
  preset: {
    ...minimal2023Preset,
    // iOS fills transparent areas with black; the SVG already has its own dark background
    apple: { sizes: [180], padding: 0 },
    maskable: { sizes: [512], padding: 0.3, resizeOptions: { background: '#15171C' } },
  },
  images: ['public/favicon.svg'],
})
