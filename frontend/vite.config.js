import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  // 定義環境變量（可在前端使用 import.meta.env.VITE_API_BASE_URL）
  define: {
    'import.meta.env.VITE_API_BASE_URL': JSON.stringify(
      process.env.VITE_API_BASE_URL || 'http://localhost:8080'
    )
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      }
    }
  },
  build: {
    // 生產環境優化
    target: 'es2015',
    minify: 'esbuild', // 使用 esbuild（Vite 內建，無需額外依賴）
    // 移除 console.log 和 debugger
    esbuild: {
      drop: ['console', 'debugger']
    },
    rollupOptions: {
      output: {
        // 代碼分割
        manualChunks: {
          'react-vendor': ['react', 'react-dom']
        },
        // 資源命名
        chunkFileNames: 'js/[name]-[hash].js',
        entryFileNames: 'js/[name]-[hash].js',
        assetFileNames: '[ext]/[name]-[hash].[ext]'
      }
    },
    // 優化設置
    chunkSizeWarningLimit: 1000,
    sourcemap: false,  // 生產環境不生成sourcemap
    cssCodeSplit: true
  }
})
