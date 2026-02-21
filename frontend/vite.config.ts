import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/files': 'http://localhost:8080'
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            // 将重型依赖单独拆分成 vendor chunk，优化加载性能与浏览器缓存
            if (id.includes('echarts')) {
              return 'vendor-echarts';
            }
            if (id.includes('element-plus')) {
              return 'vendor-element-plus';
            }
            if (id.includes('@wangeditor') || id.includes('dompurify')) {
              return 'vendor-editor';
            }
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) {
              return 'vendor-vue-core';
            }
            // 兜底：其他第三方依赖归入通用的 vendor
            return 'vendor-others';
          }
        }
      }
    },
    // 将静态资源拆分大小限制从 500kb 提高至 1000kb 避免警告，因为图表库本身较大
    chunkSizeWarningLimit: 1000
  }
})
