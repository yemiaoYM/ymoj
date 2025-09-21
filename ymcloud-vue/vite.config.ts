import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
//仅引入用到的组件与样式
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver, NaiveUiResolver } from 'unplugin-vue-components/resolvers'
//gzip静态资源压缩配置
import viteCompression from 'vite-plugin-compression';

export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [ElementPlusResolver(), NaiveUiResolver()],
    }),
    //gzip静态资源压缩配置
    viteCompression({
      verbose: true,
      disable: false,
      threshold: 10240,
      algorithm: 'gzip',
      ext: '.gz',
    })
  ],
  base: '/',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  //只在本地有效
  server: {
    host: true,
    open: false,
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:9091',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/files': {
        target: 'http://localhost:9091',
        changeOrigin: true,
        secure: false
      }
    }
  },
  build: {
    sourcemap: false,
    minify: 'terser',
    cssCodeSplit: true,
    chunkSizeWarningLimit: 1000,
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true
      }
    },
    rollupOptions: {
      input: 'index.html',
      output: {
        //静态资源打包做处理
        chunkFileNames: 'assets/js/[name]-[hash].js',
        entryFileNames: 'assets/js/[name]-[hash].js',
        assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',
        manualChunks(id) {
          if (id.includes('node_modules')) {
            return id.toString().split('node_modules/')[1].split('/')[0].toString();
          }
        }
      }
    },
  },
})
