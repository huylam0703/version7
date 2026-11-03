import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // Dev: gọi /api sẽ được chuyển sang backend ASP.NET
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:5255',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
