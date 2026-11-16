<template>
  <div class="editor-container">
    <!-- 编辑器配置 -->
    <div class="editor-config-header">
      <x-flex justify="space-between" style="width: 100%;margin-bottom: 10px;">
        <x-flex>
          <el-select
            v-model="selectedLanguage"
            placeholder="选择语言"
            style="width: 150px; margin-right: 10px;"
            @change="saveDraft"
          >
            <el-option
              v-for="item in languages"
              :key="item.name"
              :label="item.name"
              :value="item.name"
            />
          </el-select>  
          <el-button @click="refreshCode" aria-label="重置代码">
            <el-icon><Refresh /></el-icon>
          </el-button>
          <el-button @click="uploadCode" aria-label="上传代码">
            <el-icon><UploadFilled /></el-icon>
          </el-button>
        </x-flex>
        <el-popover trigger="click" placement="bottom-end" :width="280">
          <template #reference>
            <el-button :icon="Setting">设置</el-button>
          </template>
          <el-form label-position="top" class="editor-settings">
            <el-form-item label="主题">
              <el-select v-model="settings.theme">
                <el-option
                  v-for="item in themes"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="字体大小">
              <el-select v-model="settings.fontSize" @change="updateFontSize">
                <el-option v-for="size in [14, 16, 18, 20]" :key="size" :label="`${size}px`" :value="size" />
              </el-select>
            </el-form-item>
            <el-form-item label="Tab 长度">
              <el-select v-model="settings.tabSize">
                <el-option v-for="size in [2, 4, 8]" :key="size" :label="`${size} 个空格`" :value="size" />
              </el-select>
            </el-form-item>
            <x-flex align="center" justify="space-between" class="editor-completion">
              <span>代码自动补全</span>
              <el-switch v-model="settings.autoComplete" />
            </x-flex>
          </el-form>
        </el-popover>
      </x-flex>
    </div>

    <!-- 编辑器 -->
    <div class="editor-main">
      <Codemirror
        v-model:value="codeValue"
        :options="cmOptions"
        ref="editor"
        height="100%"
        width="100%"
        @ready="onReady"
        @change="onEditorCodeChange"
        :style="{ fontSize: `${settings.fontSize}px` }"
      />
    </div>

    <!-- 评测结果 -->
    <div class="editor-judge" v-show="showJudgePanel">
      <el-tabs type="border-card" v-model="activeTabName" style="height: 100%;">
        <el-tab-pane label="自测输入" name="test" style="height: 100%;">
          <el-scrollbar height="100%">
            <x-flex vertical style="height: 100%;padding: 10px;">
              <!-- 载入用例 -->
              <x-flex style="margin-bottom: 10px;">
                <el-tag
                  v-for="(item, index) in problemCaseList"
                  :key="index"
                  effect="dark"
                  type="primary"
                  disable-transitions
                  @click="userInput=item.input;expectedOutput=item.output;"
                  size="large"
                  style="margin-right: 10px; cursor: pointer;"
                >载入用例{{index+1}}</el-tag>
              </x-flex>
              <!-- 自测输入 -->
              <x-flex>
                <span style="flex: 1;text-align: center;">自测输入</span>
                <el-input
                  style="flex: 10;"
                  placeholder="请输入自测输入"
                  type="textarea"
                  resize="none"
                  class="self-test-input"
                  maxlength="1000"
                  v-model="userInput"
                  :autosize="{ minRows: 3, maxRows: 10}"
                />
              </x-flex>
              <!-- 预期输出 -->
              <x-flex>
                <span style="flex: 1;text-align: center;">预期输出</span>
                <el-input
                  style="flex: 10;"
                  placeholder="请输入预期输出"
                  type="textarea"
                  resize="none"
                  class="self-test-input"
                  maxlength="1000"
                  v-model="expectedOutput"
                  :autosize="{ minRows: 3, maxRows: 10}"
                />
              </x-flex>
            </x-flex>
          </el-scrollbar>
        </el-tab-pane>
        <el-tab-pane label="运行结果" name="result" style="height: 100%;">
          <el-scrollbar height="100%" view-class="judge-result-view">
            <div class="result-container" style="padding: 15px;" v-if="judgeResult && Object.keys(judgeResult).length>0">
              <el-alert
                  :title="judgeResult.status"
                  :type="getStatusType(judgeResult.status).alter"
                  show-icon
                  effect="dark"
                  :closable="false"
                  style="padding: 20px;"
              >
                  <x-flex style="padding: 10px 0px;">
                      <x-flex align="center">
                          <el-icon size="25"><component :is="TimeOutline" /></el-icon>
                          <span>运行时间 {{ formatSubmissionTime(judgeResult.time) }}</span>
                      </x-flex>
                      <x-flex align="center">
                          <el-icon size="25"><component :is="HardwareChipSharp" /></el-icon>
                          <span>运行内存 {{ formatSubmissionMemory(judgeResult.memory) }}</span>
                      </x-flex>
                  </x-flex>
              </el-alert>
              <x-flex vertical style="margin-top: 15px;">
                <x-flex v-if="isSelfTestResult">
                  <span style="flex: 1;text-align: center;">自测输入</span>
                  <el-input
                    style="flex: 10;"
                    type="textarea"
                    :readonly="true"
                    resize="none"
                    :autosize="{ minRows: 1, maxRows: 4}"
                    v-model="judgeResult.input"
                  />
                </x-flex>
                <x-flex v-if="isSelfTestResult">
                  <span style="flex: 1;text-align: center;">预期输出</span>
                  <el-input
                    style="flex: 10;"
                    type="textarea"
                    :readonly="true"
                    resize="none"
                    :autosize="{ minRows: 1, maxRows: 4}"
                    v-model="judgeResult.expectedOutput"
                  />
                </x-flex>
                <x-flex v-if="isSelfTestResult">
                  <span style="flex: 1;text-align: center;">实际输出</span>
                  <el-input
                    style="flex: 10;"
                    type="textarea"
                    :readonly="true"
                    resize="none"
                    :autosize="{ minRows: 1, maxRows: 4}"
                    v-model="judgeResult.output"
                  />
                </x-flex>
                <x-flex v-if="judgeResult.errorMessage">
                  <span style="flex: 1;text-align: center;">错误信息</span>
                  <span style="flex: 10;
                              background-color: var(--ym-editor-fill);
                              border: 1px solid var(--ym-editor-border);
                              border-radius: 5px;
                              padding: 5px 10px;"
                  >
                    <pre style="font-family: Consolas;">{{ judgeResult.errorMessage }}</pre>
                  </span>
                </x-flex>
              </x-flex>
            </div>
            <el-empty v-else class="judge-empty" description="暂无运行结果" :image-size="64" />
          </el-scrollbar>
        </el-tab-pane>
        <el-tab-pane name="test-play" disabled>
          <template #label>
            <el-button
              type="success"
              plain
              @click="submitCode(true)"
              :loading="judgeLoading"
              :disabled="isSubmitDisabled"
              style="padding: 0px 10px;"
            >
              <el-icon :size="18" style="margin-right: 3px;"><VideoPlay /></el-icon>
              运行自测
            </el-button>
          </template>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 状态栏 -->
    <x-flex justify="space-between" style="width: 100%;margin-top: 10px;">
      <x-flex :wrap="false" align="center">
        <!-- 已解决 -->
        <el-alert
          v-if="accepted && Object.keys(judgeResult).length == 0"
          title="你已经解决了该问题"
          type="success"
          effect="dark"
          show-icon
          :closable="false"
        >
        <template #icon>
          <el-icon :size="25"><SuccessFilled /></el-icon>
        </template>
        </el-alert>
        <el-tag v-else-if="judgeResult.status"
          size="large"
          effect="dark"
          disable-transitions
          :type="getStatusType(judgeResult.status).tag"
          style="padding: 20px;"
        >
          <x-flex justify="center" align="center">
            <template v-if="judgeResult.status == 'Pending' 
            || judgeResult.status == 'Compiling' 
            || judgeResult.status == 'Judging' 
            || judgeResult.status == 'Submitting'">
              <i class="judge-loading"></i>
            </template>
            <span style="font-size: 15px;font-family: Consolas;">
              {{ judgeResult.status }}
            </span>
          </x-flex>
        </el-tag>
      </x-flex>
      <x-flex>
        <el-button
          type="success"
          effect="dark"
          @click="toggleJudgePanel"
          style="height: 40px;"
          :icon="Bug"
        >
          在线自测
        </el-button>
        <el-button
          type="primary"
          effect="dark"
          @click="submitCode(false)"
          style="height: 40px;"
          :loading="judgeLoading"
          :disabled="isSubmitDisabled || !submissionAllowed"
        >
          <el-icon style="margin-right: 10px;"><Edit/></el-icon>{{ submissionAllowed ? '提交测评' : '比赛当前仅可查看' }}
        </el-button>
      </x-flex>
    </x-flex>
  </div>
</template>

<script lang="ts">
//import
import { computed, onUnmounted, nextTick, ref, watch, type PropType } from 'vue';
import { ElMessage,ElMessageBox } from 'element-plus';
import { TimeOutline,HardwareChipSharp,Bug } from '@vicons/ionicons5';
import type { ProblemCaseVO,LanguageVO,EditorCache,JudgeResult } from '@/type/types';
import { getStatusType,formatSubmissionTime,formatSubmissionMemory } from '@/utils/utils';
import { useUserStore } from '@/stores/user';
import { useThemeStore } from '@/stores/theme';
import Storage from '@/utils/storage';
import { submitJudge,testJudge,getJudgeStatus } from '@/api/api';
import Codemirror from 'codemirror-editor-vue3';
import type { Editor } from 'codemirror';
import { Setting } from '@element-plus/icons-vue';
//codemirror
//引入css文件
import 'codemirror/lib/codemirror.css';
//引入主题 可以从 codemirror/theme/ 下引入多个
import 'codemirror/theme/idea.css';
import "codemirror/theme/monokai.css";
import "codemirror/theme/solarized.css";
//引入语言模式 可以从 codemirror/mode/ 下引入多个
import 'codemirror/mode/clike/clike.js';    //c,c++,java
import 'codemirror/mode/python/python.js';  //python
//代码提示功能 具体语言可以从 codemirror/addon/hint/ 下引入多个
import 'codemirror/addon/hint/show-hint.css';
import 'codemirror/addon/hint/show-hint';
//foldGutter
import "codemirror/addon/fold/foldgutter.css";
import "codemirror/addon/fold/foldgutter.js";
import "codemirror/addon/edit/matchbrackets.js";
import "codemirror/addon/edit/closebrackets.js";
import "codemirror/addon/fold/brace-fold.js";
import "codemirror/addon/fold/indent-fold.js";

type EditorSettings = {
  theme: string;
  fontSize: number;
  tabSize: number;
  autoComplete: boolean;
};

export default {
  name: 'CodeMirrorEditor',
  components: {
      Codemirror
  },
  //props接收父组件传递的数据
  props: {
      code: {
          type: String,
          default: "#include<stdio.h>\nint main(){\n  printf(\"hello\");\n  return 0;\n}",
      },
      language: {
        type: String,
        default: "C"
      },
      languages: {
        type: Array as PropType<LanguageVO[]>,
        default: () => [{name:"C",contentType:"text/x-csrc"},
                        {name:"C++",contentType:"text/x-c++src"},
                        {name:"Java",contentType:"text/x-java"},
                        {name:"Python",contentType:"text/x-python"}]
      },
      problemId: {
        //problemId是题目的唯一标识，是展示displayId
        type: String,
        default: ""
      },
      isContest: {
        type: Boolean,
        default: false
      },
      submissionAllowed: {
        type: Boolean,
        default: true
      },
      contestId: {
        //contestId是比赛的唯一标识，是表id
        type: Number,
        default: 0
      },
      problemCaseList: {
        type: Array as PropType<ProblemCaseVO[]>,
        default: () => [
              {
                  "input": "1 2",
                  "output": "3",
                  "score": null,
                  "notes": "无"
              },
              {
                  "input": "100 200",
                  "output": "300",
                  "score": null,
                  "notes": "无"
              },
              {
                  "input": "-1 -2",
                  "output": "-3",
                  "score": null,
                  "notes": "无"
              }
          ],
      },
      accepted: {
        type: Boolean,
        default: false
      },
  },
  //声明事件
  emits: ['update:code'],
  setup(props,{emit}) {
      const themeStore = useThemeStore();
      const userStore = useUserStore();
      const settings = ref(Storage.get<EditorSettings>('ymoj-editor-settings') || {
          theme: 'system',
          fontSize: 16,
          tabSize: 2,
          autoComplete: true
      });
      const selectedLanguage = ref(props.language);
      const codeValue = ref(props.code);
      let editorInstance: Editor;
      const cmOptions = computed(() => ({
          mode: props.languages.find(item => item.name === selectedLanguage.value)!.contentType,
          theme: settings.value.theme === 'system'
              ? (themeStore.isDark ? 'monokai' : 'solarized')
              : settings.value.theme,
          lineNumbers: true,
          foldGutter: true,
          gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"],
          lineWrapping: true,
          tabSize: settings.value.tabSize,
          indentUnit: settings.value.tabSize,
          matchBrackets: true,
          autoCloseBrackets: true,
          hintOptions: { completeSingle: false }
      }));
      watch(settings, value => {
          Storage.set('ymoj-editor-settings', value);
      }, { deep: true });
      //自测输入
      const userInput=ref('');
      //预期输出
      const expectedOutput=ref('');
      //是否展示评测面板
      const showJudgePanel=ref<boolean>(false);
      //当前tab
      const activeTabName=ref("test");
      //区分自测与正式评测的结果展示
      const isSelfTestResult = ref(false);
      //运行结果
      const judgeResult=ref<JudgeResult>({} as JudgeResult);
      //评测加载
      const judgeLoading=ref<boolean>(false);
      //提交禁用
      const isSubmitDisabled=ref<boolean>(true);
      //评测定时器
      const refreshStatus=ref<number|null>(null);
      //轮询计数器
      const pollCount=ref<number>(0);
      //最大轮询次数 (2秒一次，最多轮询15次，即30秒)
      const MAX_POLL_COUNT=15;

      //themes列表
      const themes = [
          { label: "跟随外观", value: "system" },
          { label: "solarized", value: "solarized" },
          { label: "idea", value: "idea" },
          { label: "monokai", value: "monokai" },
      ]

      onUnmounted(()=>{
        //清除评测定时器，防止切换页面时，评测定时器还在运行
        clearTimeoutTask();
      })

      //按题目保存代码与语言，比赛题目使用独立缓存
      const saveDraft = () => {
          const key = props.isContest
              ? `Problem_${props.contestId}_${props.problemId}`
              : `Problem_${props.problemId}`;
          Storage.set<EditorCache>(key, {
              code: codeValue.value,
              language: selectedLanguage.value
          });
      };

      const onReady = (editor: Editor) => {
          editorInstance = editor;
          editor.on('inputRead', (cm, change) => {
              if (settings.value.autoComplete && /[a-zA-Z]/.test(change.text[0])) {
                  cm.showHint();
              }
          });
          isSubmitDisabled.value = false;
      };

      const onEditorCodeChange = (newCode: string) => {
          codeValue.value = newCode;
          emit('update:code', newCode);
          saveDraft();
      };

      //字号变化后重新计算光标和行高
      const updateFontSize = async () => {
          await nextTick();
          editorInstance.refresh();
      };

      const toggleJudgePanel = () => {
          showJudgePanel.value = !showJudgePanel.value;
      };

      const refreshCode=()=>{
          ElMessageBox.confirm(
              '确定要重置代码吗?',{
                  confirmButtonText: '确定',
                  cancelButtonText: '取消',
                  type: 'warning',
              }
          ).then(() => {
              codeValue.value="";
              ElMessage({
                  type: 'success',
                  message: '重置成功',
              })
          }).catch(() => {
              ElMessage({
                  type: 'info',
                  message: '重置取消',
              })
          })
      }

      const uploadCode=()=>{
          const fileInput = document.createElement('input');
          fileInput.type = 'file';
          fileInput.accept = '.txt,.c,.cpp,.java,.py'; // 限制文件类型
          fileInput.addEventListener('change', (event: Event) => {
              const target = event.target as HTMLInputElement;
              const file = target.files ? target.files[0] : null;
              if (file) {
                  const reader = new FileReader();
                  reader.onload = (e: ProgressEvent<FileReader>) => {
                      if (e.target && e.target.result) {
                          codeValue.value = e.target.result.toString();
                      }
                  };
                  reader.readAsText(file);
              }
          });
          fileInput.click();
      };

      //自测与正式提交共用入口校验和状态更新
      const submitCode = async (isTest: boolean) => {
          if (!isTest && !props.submissionAllowed) {
              ElMessage.warning("比赛当前不可提交，请在比赛时段内提交");
              return;
          }
          if (!userStore.isLogin) {
              ElMessage.error("请先登录");
              return;
          }
          if (codeValue.value.trim() === "") {
              ElMessage.error("代码不能为空");
              return;
          }
          if (codeValue.value.length > 65535) {
              ElMessage.error("代码长度不能超过65535");
              return;
          }
          isSelfTestResult.value = isTest;
          activeTabName.value = "result";
          judgeLoading.value = true;
          judgeResult.value = { status: "Submitting" } as JudgeResult;
          const code = {
              language: selectedLanguage.value,
              code: codeValue.value
          };
          try {
              const response = isTest
                  ? await testJudge({ ...code, input: userInput.value, expectedOutput: expectedOutput.value })
                  : await submitJudge({
                      ...code,
                      problemId: props.problemId,
                      isContest: props.isContest,
                      contestId: props.contestId
                  });
              ElMessage.success("提交成功");
              checkJudgeStatus({ submissionId: response.data.data, isTest });
          } catch {
              judgeResult.value = { status: "Submitted Failed" } as JudgeResult;
              judgeLoading.value = false;
          }
      };

      //获取评测结果
      const checkJudgeStatus=(params: { submissionId: number; isTest: boolean })=>{
        //先清理之前的定时器，防止多次轮询
        clearTimeoutTask();
        //重置轮询计数器
        pollCount.value=0;
        const checkStatus=()=>{
          //检查是否超过最大轮询次数
          if(pollCount.value >= MAX_POLL_COUNT){
            ElMessage.warning("评测超时，已停止轮询");
            judgeResult.value={ status:"System Error" } as JudgeResult;
            judgeLoading.value=false;
            clearTimeoutTask();
            return;
          }
          //获取评测结果
          getJudgeStatus(params).then((res) => {
            judgeResult.value=res.data.data;
            const status=judgeResult.value.status;
            if(status==='Pending' 
            || status === 'Compiling' 
            || status === 'Judging' 
            || status === 'Submitting'){
              pollCount.value++;
              refreshStatus.value=setTimeout(checkStatus,2000);
            }else{
              //status不为判题和排队中才表示此次判题结束
              judgeLoading.value=false;
              showJudgePanel.value=true;
              activeTabName.value="result";
              clearTimeoutTask();
            }
          }).catch(() => {
            judgeResult.value={ status:"No status" } as JudgeResult;
            judgeLoading.value=false;
            clearTimeoutTask();
          })
        }
        //立即开始轮询
        checkStatus();
      }

      //清理轮询任务
      const clearTimeoutTask=()=>{
        if(refreshStatus.value){
          clearTimeout(refreshStatus.value);
          refreshStatus.value=null;
        }
        pollCount.value=0;
      }

      return {
          settings,
          updateFontSize,
          Setting,
          codeValue,
          cmOptions,
          onReady,
          activeTabName,
          onEditorCodeChange,
          themes,
          selectedLanguage,
          saveDraft,
          refreshCode,
          uploadCode,
          submitCode,
          userInput,
          expectedOutput,
          showJudgePanel,
          toggleJudgePanel,
          judgeResult,
          isSelfTestResult,
          judgeLoading,
          isSubmitDisabled,
          getStatusType,
          formatSubmissionTime,
          formatSubmissionMemory,
          TimeOutline,
          HardwareChipSharp,
          Bug
      }
  },
};
</script>

<style scoped>
.editor-container {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 10px;
  box-sizing: border-box;
  border: 1px solid var(--ym-editor-border);
  background-color: var(--ym-surface);
}
.editor-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--ym-editor-panel);
  overflow: hidden;
}
.editor-settings {
  padding: 4px;
}
.editor-completion {
  padding-top: 12px;
  border-top: 1px solid var(--ym-editor-border);
}
.editor-judge {
  height: 36%;
  min-height: 0;
}
.self-test-input :deep(.el-textarea__inner) {
  padding: 8px 12px;
  font-family: Consolas, "PingFang SC", "Microsoft YaHei", monospace;
  font-size: 16px;
  line-height: 1.6;
}
:deep(.judge-result-view) {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}
.judge-empty {
  flex: 1;
  --el-empty-padding: 16px 0;
  --el-empty-description-margin-top: 8px;
}
.judge-loading{
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  vertical-align: middle;
  background-color: var(--ym-surface);
}
:deep(.result-container .el-alert__title) {
  font-size: 22px;
  font-weight: bold;
}
:deep(.result-container .el-alert__content){
    margin-left: 10px;
}
:deep(.el-textarea__inner[readonly]){
    background-color: var(--ym-readonly-fill);
}
</style>
