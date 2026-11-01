import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { permission } from '@/directives/permission';
import { config } from 'md-editor-v3';
import katex from 'katex';
import 'katex/dist/katex.min.css';
import { configureMarkdown } from '@/utils/markdown';

// 题面公式随应用提供，编辑器和预览器共用同一个本地实例。
config({
  editorExtensions: { katex: { instance: katex } },
  markdownItConfig: configureMarkdown,
});

const app = createApp(App);
app.use(createPinia());
app.use(router);
app.use(ElementPlus);
app.directive('permission', permission);
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}
app.mount('#app');
