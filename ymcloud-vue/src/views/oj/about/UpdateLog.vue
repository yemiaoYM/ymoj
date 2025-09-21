<template>
  <div class="container">
    <n-flex vertical size="large">
      <n-card title="更新日志" size="large">
        <template #header-extra>
          <n-tag type="success">YMOJ</n-tag>
        </template>
        <span style="color:#888;font-size:16px;">记录平台每次重要更新</span>
      </n-card>
      <n-timeline>
        <n-timeline-item
          v-for="log in logs"
          :key="log.version"
          :type="log.type"
          :time="log.date"
        >
          <n-card :title="log.title" size="medium">
            <n-flex align="center" size="small" style="margin-bottom:8px;">
              <n-tag type="primary">{{ log.version }}</n-tag>
            </n-flex>
            <n-space vertical size="small">
              <div v-for="content in log.content" :key="content">
                <template v-if="content.startsWith('修复')">
                  <span style="color:#d03050;">🐛 {{ content }}</span>
                </template>
                <template v-else="content.startsWith('新增')">
                  <span style="color:#18a058;">✨ {{ content }}</span>
                </template>
              </div>
            </n-space>
            <n-space v-if="log.contributors && log.contributors.length" align="center" size="small" style="margin-top:12px;">
              <span style="color:#888;font-size:13px;">贡献者:</span>
              <n-avatar-group :options="log.contributors as any" :size="40" :max="3">
                <template #avatar="{ option }">
                  <n-tooltip>
                    <template #trigger>
                      <n-avatar :src="option.src" />
                    </template>
                    {{ (option as any).name }}
                  </n-tooltip>
                </template>
              </n-avatar-group>
            </n-space>
          </n-card>
        </n-timeline-item>
      </n-timeline>
    </n-flex>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { NCard, 
  NTimeline,
  NTimelineItem,
  NTag, 
  NFlex, 
  NSpace, 
  NAvatarGroup,
  NTooltip,
  NAvatar
 } from 'naive-ui';



//更新日志类型
interface UpdateLog {
  version: string;
  date: string;
  title: string;
  content: string[];
  //新增(success)、修复(error)、优化(warning)
  type: 'success' | 'error' | 'warning';
  contributors: {
    name: string;
    src: string;
  }[];
}

const logs = ref<UpdateLog[]>([
  {
    version: 'v1.2.0',
    date: '2024-06-10',
    title: '全新题目详情页UI',
    content: [
      '新增 题目详情页重构，采用Naive UI美化，提升用户体验',
      '新增 测试用例支持折叠查看，复制按钮更友好',
      '新增 优化响应式布局，适配更多屏幕',
      '修复 代码提交后状态更新不及时的问题',
      '修复 部分浏览器兼容性问题'
    ],
    type: 'success',
    contributors: [
      {
        src: 'http://47.93.124.26:5173/api/image/avatar/9dcfdcb5-c9fe-4b01-a6e3-89421fca3891.jpg',
        name: '夜喵'
      },
      {
        src: 'http://47.93.124.26:5173/api/image/avatar/9dcfdcb5-c9fe-4b01-a6e3-89421fca3891.jpg',
        name: '夜喵'
      }
    ]
  },
  {
    version: 'v1.1.5',
    date: '2024-06-05',
    title: '性能优化与Bug修复',
    content: [
      '优化 优化页面加载速度，减少首屏加载时间',
      '修复 用户提交历史记录分页问题',
      '修复 夜间模式下部分UI显示异常'
    ],
    type: 'warning',
    contributors: []
  },
  {
    version: 'v1.1.0',
    date: '2024-05-28',
    title: '支持多语言代码编辑',
    content: [
      '新增 集成CodeMirror，支持C/C++/Java/Python代码高亮',
      '新增 新增自测输入与运行结果展示',
      '新增 支持代码片段保存与分享功能',
      '修复 部分语言编译错误提示不准确的问题'
    ],
    type: 'success',
    contributors: [
      {
        src: 'http://47.93.124.26:5173/api/image/avatar/9dcfdcb5-c9fe-4b01-a6e3-89421fca3891.jpg',
        name: '夜喵'
      }
    ]
  },
  {
    version: 'v1.0.5',
    date: '2024-05-15',
    title: '用户体验改进',
    content: [
      '新增 优化移动端适配，提升手机访问体验',
      '新增 新增题目难度标签，帮助用户选择合适题目',
      '修复 账号设置页面保存失败的问题'
    ],
    type: 'success',
    contributors: [
      {
        src: 'http://47.93.124.26:5173/api/image/avatar/9dcfdcb5-c9fe-4b01-a6e3-89421fca3891.jpg',
        name: '夜喵'
      }
    ]
  },
  {
    version: 'v1.0.0',
    date: '2024-05-01',
    title: '平台上线',
    content: [
      '新增 OJ平台正式上线',
      '新增 支持题库浏览、题目提交、状态查询等基础功能',
      '新增 实现用户注册、登录、个人信息管理',
      '新增 支持排行榜和竞赛系统'
    ],
    type: 'success',
    contributors: [
      {
        src: 'http://47.93.124.26:5173/api/image/avatar/9dcfdcb5-c9fe-4b01-a6e3-89421fca3891.jpg',
        name: '夜喵'
      }
    ]
  }
]);
</script>

<style scoped>
.container {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px 8px 40px 8px;
}
</style>