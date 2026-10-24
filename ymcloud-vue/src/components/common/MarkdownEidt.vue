<template>
  <div style="width: 100%;height: 100%;">
    <MdEditor 
      v-model="modelValue" 
      :toolbars="toolbars"
      :sanitize="DOMPurify.sanitize"
      previewTheme="cyanosis"
      noUploadImg
      show-toolbar-name
    />
  </div>
</template>

<script>
import { ref,defineComponent,watch } from 'vue';
import { MdEditor } from 'md-editor-v3';
import DOMPurify from 'dompurify';
import { ElMessage } from 'element-plus';
import 'md-editor-v3/lib/style.css';

export default defineComponent({
  name: 'MarkdownEidt',
  components: {
    MdEditor
  },
  props: {
    content: {
      type: String,
      default: "写点什么呢？"
    }
  },
  emits: ["update:content"],
  setup(props, { emit }) {
    //配置
    const toolbars = [
      'bold',
      'underline',
      'italic',
      '-',
      'title',
      'strikeThrough',
      'sub',
      'sup',
      'quote',
      'unorderedList',
      'orderedList',
      'task',
      '-',
      'codeRow',
      'code',
      'link',
      'image',
      'table',
      'katex',
      '-',
      'revoke',
      'next',
      '=',
      'pageFullscreen',
      'fullscreen',
      'preview',
      'previewOnly',
    ];
    
    const modelValue = ref(props.content);

    //监听编辑器内部变化->通知父组件更新
    watch(modelValue, (val, previous) => {
      if (val === props.content) {
        return;
      }
      // 在受控值边界限长，避免编辑器读取粘贴/删除事件的空 data。
      if (val.length > 65535) {
        modelValue.value = previous;
        ElMessage.warning('内容不能超过65535个字符');
        return;
      }
      emit("update:content", val);
    });

    //监听props变化->更新内部状态
    watch(() => props.content, (newContent) => {
      if (newContent !== modelValue.value) {
        modelValue.value = newContent;
      }
    });

    return {
      DOMPurify,
      modelValue,
      toolbars
    }
  }
})
</script>
