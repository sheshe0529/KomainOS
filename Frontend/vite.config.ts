import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// En desarrollo el panel llama a /api en su mismo origen y Vite reenvía al
// backend: no hace falta CORS y la URL del backend no queda en el código.
// KOMAINOS_BACKEND permite apuntar a otro puerto (por defecto 8081).
const backend = process.env.KOMAINOS_BACKEND ?? 'http://localhost:8081'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    // Puerto propio y estricto: si esta ocupado, Vite falla en vez de moverse
    // en silencio a otro (5173 lo usa otro proyecto en esta maquina).
    port: 5180,
    strictPort: true,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
    },
  },
})
