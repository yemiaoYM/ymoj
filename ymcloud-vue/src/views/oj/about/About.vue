<template>
  <div class="container">
    <el-row :gutter="20">
      <el-col :md="12" :sm="24">
        <el-card class="compiler">
          <template #header>
            <span style="font-size: 20px;color: var(--ym-link);">编译器 & 例题</span>
          </template>
          <div class="content">
            <ul>
              <li v-for="lang in languages" :key="lang.name">
                {{ lang.name }} ( {{ lang.version }} )
                <p style="color: var(--ym-link);font-size:16px;margin-top: 10px;">
                  编译器
                </p>
                <pre>{{ lang.compileCmd }}</pre>
                <p style="color: var(--ym-link);font-size:16px">
                  A+B 例题
                </p>
                <Highlight style="margin: 10px 0px;" v-if="lang.templateCode" :code="lang.templateCode"
                  :language="lang.name">
                </Highlight>
              </li>
            </ul>
          </div>
        </el-card>
      </el-col>
      <el-col :md="12" :sm="24">
        <el-card>
          <template #header>
            <span style="font-size: 20px;color: var(--ym-link);">结果说明</span>
          </template>
          <ul class="result">
            <li v-for="item in results" :key="item.status">
              <el-tag effect="dark" size="large" :type="getStatusType(item.status).tag">{{ item.status }}</el-tag>
              <span>{{ item.description }}</span>
            </li>
          </ul>
        </el-card>
        <el-card style="margin-top: 20px;">
          <template #header>
            <span style="font-size: 20px;color: var(--ym-link);">帮助</span>
          </template>
          <ul class="help-list">
            <li><strong>输入与输出</strong>从标准输入读取数据，将答案写入标准输出。不要输出“请输入”等提示或调试信息。</li>
            <li><strong>多组数据</strong>按题面区分组数 T、读到文件结束（EOF）和结束标记；每组数据处理前重置相应变量。</li>
            <li><strong>空格与换行</strong>按要求保留输出格式。读取含空格的字符串时使用整行读取，混用逐项读取和整行读取时留意残留换行。</li>
            <li><strong>整数范围</strong>C / C++ 注意整数溢出，必要时使用 long long；乘法要在运算前转换类型，不能只在赋值时转换。</li>
            <li><strong>浮点精度</strong>按题面要求设置小数位数。浮点运算存在舍入误差，不直接用相等比较判断近似结果。</li>
            <li><strong>编译与入口</strong>提交时选择对应语言。C / C++ 使用 int main；Java 主类名为 Main，不添加 package 声明。编译命令见“编译器 & 例题”。</li>
            <li><strong>样例与边界</strong>样例通过不代表全部通过。检查最小和最大规模、重复值、负数以及题目允许的空数据。</li>
            <li><strong>超时与运行异常</strong>超时先检查算法复杂度、死循环和输入效率；运行异常检查数组越界、除零与递归深度，并查看错误信息。</li>
            <li><strong>内存限制</strong>注意大数组、容器和递归栈的占用。大数组放在局部栈上可能导致运行异常。</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
//import
import { ref, defineAsyncComponent, onMounted } from 'vue';
import type { LanguageTableVO } from '@/types/problem';
import { getLanguageTableList } from '@/api/problem';
import { getStatusType } from '@/utils/utils';

const Highlight = defineAsyncComponent(() => import('@/components/common/CodeHighLight.vue'));

const results = [
  { status: 'Pending', description: '提交已进入队列，等待评测。' },
  { status: 'Compiling', description: '正在编译代码。' },
  { status: 'Judging', description: '正在运行测试数据。' },
  { status: 'Accepted', description: '通过全部测试。' },
  { status: 'Partial Accepted', description: '部分测试通过，得分以评测结果为准。' },
  { status: 'Wrong Answer', description: '输出与预期结果不符。' },
  { status: 'Presentation Error', description: '输出格式不符合要求。' },
  { status: 'Compile Error', description: '编译未通过，请查看编译错误信息。' },
  { status: 'Runtime Error', description: '程序运行异常，请查看错误信息。' },
  { status: 'Time Limit Exceeded', description: '运行时间超过题目限制。' },
  { status: 'Memory Limit Exceeded', description: '内存使用超过题目限制。' },
  { status: 'Submitted Failed', description: '提交未成功，请稍后重试。' },
  { status: 'System Error', description: '评测服务异常，可联系管理员检查。' },
  { status: 'Cancelled', description: '本次评测已取消。' }
];

const languages = ref<LanguageTableVO[]>([] as LanguageTableVO[]);

onMounted(() => {
  initLanguages();
});

const initLanguages = () => {
  getLanguageTableList().then((res) => {
    languages.value = res.data.data;
  }).catch((e) => {
    console.log(e);
  })
}

</script>

<style scoped>
.compiler {
  margin-bottom: 20px;
}

.compiler .content {
  font-size: 16px;
  margin: 0 50px 20px 50px;
}

.compiler .content pre {
  padding: 5px 10px;
  white-space: pre-wrap;
  margin-top: 15px;
  margin-bottom: 15px;
  background: var(--ym-table-header);
  border: 1px dashed var(--ym-code-border);
  color: var(--ym-content-text);
  font-family:
    Consolas,
    "SFMono-Regular",
    Menlo,
    "Liberation Mono",
    monospace;
}

ul {
  list-style: disc;
  padding-inline-start: 0px;
}

li {
  line-height: 2;
}

li .title {
  font-weight: 600;
  font-size: 1rem;
}

.container {
  width: 100%;
  padding: 16px;
}

.about-title {
  margin: 4px 0 20px;
  padding-left: 12px;
  border-left: 3px solid var(--ym-link);
  color: var(--el-text-color-primary);
  font-size: 22px;
  font-weight: 600;
}

.result li {
  display: grid;
  grid-template-columns: 185px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  font-size: 14px;
  line-height: 1.7;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.result li:first-child {
  padding-top: 0;
}

.result li:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.result .el-tag {
  justify-self: start;
}

.help-list {
  padding-left: 18px;
  font-size: 14px;
}

.result :deep(.el-tag__content) {
  font-weight: 600;
}

.help-list strong {
  margin-right: 10px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.help-list li + li {
  margin-top: 10px;
}

@media (max-width: 600px) {
  .compiler .content {
    margin: 0 12px 12px;
  }
  .result li {
    grid-template-columns: 1fr;
    gap: 6px;
  }
}

@media screen and (min-width: 1024px) {
  .container {
    padding: 10px 25px;
  }
}
</style>