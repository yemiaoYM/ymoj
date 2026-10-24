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
            @change="updateLanguage"
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
        <x-flex>
          <el-tag effect="dark" size="large">主题</el-tag>
          <el-select
            id="theme-select"
            v-model="selectedTheme"
            placeholder="选择当前主题"
            @change="updateTheme"
            class="theme-select"
            style="width: 150px;"
          >
            <el-option
              v-for="item in themes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </x-flex>
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
        style="font-size: 16px;"
      />
    </div>

    <!-- 评测结果 -->
    <div class="editor-judge" v-show="showFullScreen">
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
                  size="small"
                  maxlength="1000"
                  v-model="userInput"
                  :autosize="{ minRows: 1, maxRows: 10}"
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
                  size="small"
                  maxlength="1000"
                  v-model="expectedOutput"
                  :autosize="{ minRows: 1, maxRows: 10}"
                />
              </x-flex>
            </x-flex>
          </el-scrollbar>
        </el-tab-pane>
        <el-tab-pane label="运行结果" name="result" style="height: 100%;">
          <el-scrollbar height="100%">
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
                <x-flex>
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
                <x-flex>
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
                <x-flex>
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
                              background-color: #f5f5f5;
                              border: 1px solid #ccc;
                              border-radius: 5px;
                              padding: 5px 10px;"
                  >
                    <pre style="font-family: Consolas;">{{ judgeResult.errorMessage }}</pre>
                  </span>
                </x-flex>
              </x-flex>
            </div>
            <el-empty v-else class="judge-empty" description="保存并提交之后，这里将会显示运行结果" :image-size="40" />
          </el-scrollbar>
        </el-tab-pane>
        <el-tab-pane name="test-play" disabled>
          <template #label>
            <el-button
              type="success"
              plain
              @click="handSelfTestJudge"
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
          @click="handFullScreen"
          style="height: 40px;"
          :icon="Bug"
        >
          在线自测
        </el-button>
        <el-button
          type="primary"
          effect="dark"
          @click="handSubmitJudge"
          style="height: 40px;"
          :loading="judgeLoading"
          :disabled="isSubmitDisabled"
        >
          <el-icon style="margin-right: 10px;"><Edit/></el-icon>提交测评
        </el-button>
      </x-flex>
    </x-flex>
  </div>
</template>

<script lang="ts">
//import
import { onMounted, onUnmounted, ref, type PropType } from 'vue';
import { ElMessage,ElMessageBox } from 'element-plus';
import { TimeOutline,HardwareChipSharp,Bug } from '@vicons/ionicons5';
import type { ProblemCaseVO,LanguageVO,EditorCache,JudgeResult } from '@/type/types';
import { getStatusType,formatSubmissionTime,formatSubmissionMemory } from '@/utils/utils';
import { useUserStore } from '@/stores/user';
import Storage from '@/utils/storage';
import { submitJudge,testJudge,getJudgeStatus } from '@/api/api';

import Codemirror from 'codemirror-editor-vue3';
// 引入css文件
import 'codemirror/lib/codemirror.css';

// 引入主题 可以从 codemirror/theme/ 下引入多个
import 'codemirror/theme/idea.css';
import "codemirror/theme/monokai.css";
import "codemirror/theme/solarized.css";

// 引入语言模式 可以从 codemirror/mode/ 下引入多个
import 'codemirror/mode/clike/clike.js';    //c,c++,java
import 'codemirror/mode/python/python.js';  //python

// 代码提示功能 具体语言可以从 codemirror/addon/hint/ 下引入多个
import 'codemirror/addon/hint/show-hint.css';
import 'codemirror/addon/hint/show-hint';

// foldGutter
import "codemirror/addon/fold/foldgutter.css";
import "codemirror/addon/fold/foldgutter.js";

import "codemirror/addon/edit/matchbrackets.js";
import "codemirror/addon/edit/closebrackets.js";
import "codemirror/addon/fold/brace-fold.js";
import "codemirror/addon/fold/indent-fold.js";

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
      theme: {
        type: String,
        default: "solarized"
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
  emits: ['update:codeValue'],
  setup(props,{emit}) {
      const cmOptions = ref({
          //语言及语法模式
          mode: 'text/x-csrc',
          theme: 'solarized',
          //显示行号
          line: true,
          lineNumbers: true,
          //代码折叠
          foldGutter: true,
          gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"],
          lineWrapping: true,
          tabSize: 2,
          matchBrackets: true, //括号匹配
          autoCloseBrackets: true,
          //代码提示功能
          hintOptions: {
              //避免由于提示列表只有一个提示信息时，自动填充
              completeSingle: false,
          },
      });
      //获取当前用户信息
      const userStore=useUserStore();
      //当前编辑器配置
      const selectedTheme=ref(props.theme);
      const selectedLanguage=ref(props.language);
      const codeValue = ref(props.code);
      //自测输入
      const userInput=ref('');
      //预期输出
      const expectedOutput=ref('');
      //是否全屏
      const showFullScreen=ref<boolean>(false);
      //当前tab
      const activeTabName=ref("test");
      //运行结果
      const judgeResult=ref<JudgeResult>({} as JudgeResult);
      //评测加载
      const judgeLoading=ref<boolean>(false);
      //提交禁用
      const isSubmitDisabled=ref<boolean>(true);
      //提交id
      const submissionId=ref<number|null>(null);
      //评测定时器
      const refreshStatus=ref<number|null>(null);
      //轮询计数器
      const pollCount=ref<number>(0);
      //最大轮询次数 (2秒一次，最多轮询15次，即30秒)
      const MAX_POLL_COUNT=15;

      //themes列表
      const themes = [
          { label: "solarized", value: "solarized" },
          { label: "idea", value: "idea" },
          { label: "monokai", value: "monokai" },
      ]

      //挂载组件
      onMounted(()=>{
        
      })

      onUnmounted(()=>{
        //清除评测定时器，防止切换页面时，评测定时器还在运行
        clearTimeoutTask();
      })

      const updateEditor=()=>{
        let param:EditorCache={
            code:codeValue.value,
            language:selectedLanguage.value,
            theme:selectedTheme.value,
        }
        //根据是否是比赛，设置缓存key
        let key=null;
        if(props.isContest&&props.contestId>0&&props.problemId){
          key="Problem_"+props.contestId+"_"+props.problemId;
        }else if(props.problemId){
          key="Problem_"+props.problemId;
        }
        //设置缓存
        if(key){
          Storage.set<EditorCache>(key,param);
        }
      }

      const onReady = (editor:any) => {
        editor.on('inputRead', function (cm: any, location: any) {
            if (/[a-zA-Z]/.test(location.text[0])) {
                cm.showHint();
            }
        });
        //根据父组件传入的参数，设置编辑器配置
        codeValue.value=props.code;
        const theme = themes.find(e => e.value === props.theme);
        if(theme){
          cmOptions.value.theme = theme.value;
          selectedTheme.value = theme.value;
        }
        const lang = props.languages.find(e => e.name === props.language);
        if(lang){
          cmOptions.value.mode = lang.contentType;
          selectedLanguage.value = lang.name;
        }
        isSubmitDisabled.value=false;
      };

      const onEditorCodeChange=(newCode:string)=>{
          emit('update:codeValue',newCode);
          updateEditor();
      }

      //更新主题
      const updateTheme=(value:string)=>{
          cmOptions.value.theme=value;
          updateEditor();
      }
      
      //更新语言
      const updateLanguage=(language:string)=>{
        const lang = props.languages.find(e => e.name === language);
        if(lang){
          cmOptions.value.mode = lang.contentType;
          updateEditor();
        }
      }

      //展开/折叠状态面板
      const handFullScreen=()=>{
        console.log("fullscreen");
        if(showFullScreen.value){
          showFullScreen.value=false;
        }else{
          showFullScreen.value=true;
        }
      }

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

      //自测运行
      const handSelfTestJudge=()=>{
        if(!userStore.isLogin){
          ElMessage.error("请先登录");
          return;
        }
        if(codeValue.value.trim() === ""){
          ElMessage.error("代码不能为空");
          return;
        }
        if(codeValue.value.length > 65535){
          ElMessage.error("代码长度不能超过65535");
          return;
        }
        judgeLoading.value=true;
        judgeResult.value={ status:"Submitting" } as JudgeResult;
        //测试评测
        let data = {
          language: selectedLanguage.value,
          code: codeValue.value,
          input: userInput.value,
          expectedOutput: expectedOutput.value
        }
        testJudge(data).then((res) => {
          submissionId.value=res.data.data;
          if(submissionId.value){
            ElMessage({
              type: 'success',
              message: '提交成功',
              plain: true
            })
            //开启定时获取评测结果
            checkJudgeStatus({submissionId:submissionId.value,isTest:true});
          }
        }).catch((err) => {
          judgeResult.value={ status:"Judging Failed" } as JudgeResult;
          judgeLoading.value=false;
          console.log(err);
        })
      }

      //正式提交
      const handSubmitJudge=()=>{
        if(!userStore.isLogin){
          ElMessage.error("请先登录");
          return;
        }
        if(codeValue.value.trim() === ""){
          ElMessage.error("代码不能为空");
          return;
        }
        if(codeValue.value.length > 65535){
          ElMessage.error("代码长度不能超过65535");
          return;
        }
        judgeLoading.value=true;
        judgeResult.value={ status:"Submitting" } as JudgeResult;
        //提交信息
        let data = {
          problemId: props.problemId,
          language: selectedLanguage.value,
          code: codeValue.value,
          isContest:props.isContest,
          contestId:props.contestId
        };
        //获取提交id
        submitJudge(data).then((res) => {
          submissionId.value=res.data.data;
          if(submissionId.value){
            ElMessage({
              type: 'success',
              message: '提交成功',
              plain: true
            })
            //开启定时获取评测结果
            checkJudgeStatus({submissionId:submissionId.value,isTest:false});
          }
        }).catch((err) => {
          judgeResult.value={ status:"Submitted Failed" } as JudgeResult;
          judgeLoading.value=false;
          console.log(err);
        })
      }

      //获取评测结果
      const checkJudgeStatus=(params:any)=>{
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
              showFullScreen.value=true;
              activeTabName.value="result";
              clearTimeoutTask();
            }
          }).catch((err) => {
            console.log(err);
            ElMessage.warning("获取评测结果中途失败，已停止轮询");
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
          codeValue,
          cmOptions,
          onReady,
          activeTabName,
          onEditorCodeChange,
          selectedTheme,
          themes,
          updateTheme,
          selectedLanguage,
          updateLanguage,
          refreshCode,
          uploadCode,
          handSelfTestJudge,
          handSubmitJudge,
          userInput,
          expectedOutput,
          showFullScreen,
          handFullScreen,
          judgeResult,
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
  border-radius: 8px;
  padding: 10px;
  box-sizing: border-box;
  border: 1px solid #ccc;
  background-color: #fff;
}
.editor-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  border-radius: 8px;
  overflow: hidden;
}
.editor-judge{
  height: 40%;
}
.judge-empty {
  --el-empty-padding: 0;
  --el-empty-description-margin-top: 8px;
}
.judge-loading{
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  vertical-align: middle;
  background-color: #fff;
}
:deep(.result-container .el-alert__title) {
  font-size: 22px;
  font-weight: bold;
}
:deep(.result-container .el-alert__content){
    margin-left: 10px;
}
:deep(.el-textarea__inner[readonly]){
    background-color: #f7f8f9;
}
</style>
