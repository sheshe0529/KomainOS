import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// En desarrollo Vite reenvía /api al backend y no hace falta CORS. KOMAINOS_BACKEND permite otro puerto
const backend = process.env.KOMAINOS_BACKEND ?? 'http://localhost:8081'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  build: {
    rolldownOptions: {
      output: {
        // Librerías aparte del código del panel: cambian poco y el navegador las conserva en caché entre versiones
        codeSplitting: {
          groups: [
            { name: 'react', test: /node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/ },
            {
              name: 'graficos',
              test: /node_modules[\\/](recharts|victory-vendor|d3-[a-z-]+|internmap|@reduxjs|react-redux|redux|redux-thunk|immer|reselect|es-toolkit|decimal\.js-light|eventemitter3|tiny-invariant)[\\/]/,
            },
          ],
        },
      },
    },
  },
  server: {
    // Puerto estricto: si está ocupado Vite falla en vez de moverse en silencio a otro
    port: 5180,
    strictPort: true,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
    },
  },
})
