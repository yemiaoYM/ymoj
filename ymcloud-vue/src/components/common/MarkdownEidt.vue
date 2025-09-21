<template>
  <div style="width: 100%;height: 100%;">
    <MdEditor 
      v-model="modelValue" 
      :toolbars="toolbars"
      previewTheme="cyanosis"
      noUploadImg
      :max-length="65535"
      show-toolbar-name
    />
  </div>
</template>

<script>
import { ref,defineComponent,watch } from 'vue';
import { MdEditor } from 'md-editor-v3';
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
    watch(modelValue, (val) => {
      emit("update:content", val);
    });

    return {
      modelValue,
      toolbars
    }
  }
})
</script>