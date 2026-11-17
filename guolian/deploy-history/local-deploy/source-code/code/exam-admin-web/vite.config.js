import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 6000,
    host: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8088',
        changeOrigin: true
      }
    }
  }
})
