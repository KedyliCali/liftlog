import '@testing-library/jest-dom/vitest'
import { configure } from '@testing-library/react'

// findBy*/waitFor give up after 1 s by default; with every test file running in parallel on a busy machine
// (or a CI runner) whole-page tests can need longer, so a slow run waits instead of failing at random
configure({ asyncUtilTimeout: 5000 })
