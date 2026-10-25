import path from 'node:path'
import { defineConfig } from 'vitest/config'

// Reglas del panel que no dependen del navegador: corren en Node, sin DOM
export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  test: {
    include: ['src/**/*.test.ts'],
    environment: 'node',
  },
})
