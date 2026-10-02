import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

/**
 * В dev-режиме Vite проксирует запросы к Spring Boot, поэтому браузер видит один origin
 * и сессионная cookie с CSRF-токеном работают так же, как в production за nginx.
 */
const proxyTarget = process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080'

const proxy = {
  target: proxyTarget,
  changeOrigin: true,
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': proxy,
      '/auth': proxy,
      '/uploads': proxy,
      '/oauth2': proxy,
      '/login/oauth2': proxy,
      '/favicon.ico': proxy,
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
})
