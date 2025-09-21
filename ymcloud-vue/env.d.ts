/// <reference types="vite/client" />

declare module '@wangeditor/editor-for-vue'
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
declare module 'element-plus/dist/locale/zh-cn.mjs';