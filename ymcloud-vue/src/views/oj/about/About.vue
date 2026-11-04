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
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('Pending').tag">Pending</el-tag>
              ：您的解答正在排队等待评测中，请等待结果...
            </li>
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('Submitted Failed').tag">Submitted Failed</el-tag>
              ：您的提交由于编译错误等原因导致评测失败，请检查代码后重新提交。
            </li>
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('Compiling').tag">Compiling</el-tag>
              ：您的提交正在编译中，请等待结果...
            </li>
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('Judging').tag">Judging</el-tag>
              ：您的提交正在评测中，请等待结果...
            </li>
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('Compile Error').tag">Compile Error</el-tag> :
              您的提交由于编译错误等原因导致评测失败，请检查代码后重新提交。
            </li>
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('Presentation Error').tag">Presentation
                Error</el-tag> :
              您的提交由于输出格式错误等原因导致评测失败，请检查代码后重新提交。
            </li>
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('Partial Accepted').tag">Partial Accepted</el-tag>
              :
              您的提交部分通过评测，部分未通过评测。
            </li>
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('Accepted').tag">Accepted</el-tag> :
              您的提交通过评测，恭喜您！
            </li>
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('Wrong Answer').tag">Wrong Answer</el-tag> :
              您的提交由于输出错误等原因导致评测失败，请检查代码后重新提交。
            </li>
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('Runtime Error').tag">Runtime Error</el-tag> :
              您的提交由于运行时错误等原因导致评测失败，请检查代码后重新提交。
            </li>
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('Time Limit Exceeded').tag">Time Limit
                Exceeded</el-tag>
              :
              您的提交由于超时等原因导致评测失败，请检查代码后重新提交。
            </li>
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('Memory Limit Exceeded').tag">Memory Limit
                Exceeded</el-tag> :
              您的提交由于内存使用超限等原因导致评测失败，请检查代码后重新提交。
            </li>
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('System Error').tag">System Error</el-tag> :
              您的提交由于系统错误等原因导致评测失败，请检查代码后重新提交。
            </li>
            <li>
              <el-tag effect="dark" size="large" :type="getStatusType('Cancelled').tag">Cancelled</el-tag> :
              您的提交由于取消等原因导致评测失败，请检查代码后重新提交。
            </li>
          </ul>
        </el-card>
        <el-card style="margin-top: 20px;">
          <template #header>
            <span style="font-size: 20px;color: var(--ym-link);">帮助</span>
          </template>
          <ul class="result">
            <li>1. __int64不是ANSI标准定义，只能在VC使用，在 GNU C++ 中应写成 long long 类型， scanf和printf 请使用%lld作为格式</li>
            <li>2. main() 返回值必须定义为 int ，而不是 void</li>
            <li>3. i 在循环外失去定义 "for(int i=0...){...}"</li>
            <li>4. itoa 不是ansi标准函数（标准 C/C++ 中无此函数）</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
//import
import { ref, defineAsyncComponent, onMounted } from 'vue';
import type { LanguageTableVO } from '@/type/types';
import { getStatusType } from '@/utils/utils';
import { getLanguageTableList } from '@/api/api';
const Highlight = defineAsyncComponent(() => import('@/components/common/CodeHighLight.vue'));

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
  font-family: Consolas;
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

.result li {
  font-size: 13px;
  list-style-type: none;
  margin-top: 8px;
}

:deep(.result .el-tag__content) {
  font-weight: 600;
}

@media screen and (min-width: 1024px) {
  .container {
    padding: 10px 25px;
  }
}
</style>