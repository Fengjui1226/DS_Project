import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
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
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,  // 移除console.log
        drop_debugger: true
      }
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
