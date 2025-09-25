<template>
  <div style="width: 100%; height: 100%">
    <n-code
      :hljs="hljs"
      :code="code"
      :language="language"
      show-line-numbers
    ></n-code>
  </div>
</template>

<script lang="ts">
import { defineComponent } from "vue";
import hljs from "highlight.js/lib/core";
import cpp from "highlight.js/lib/languages/cpp";
import java from "highlight.js/lib/languages/java";
import python from "highlight.js/lib/languages/python";
import c from "highlight.js/lib/languages/c";

//注册语言
hljs.registerLanguage("c", c);
hljs.registerLanguage("cpp", cpp);
hljs.registerLanguage("java", java);
hljs.registerLanguage("python", python);

export default defineComponent({
  name: "CodeHighLight",
  props: {
    code: {
      type: String,
      required: true,
    },
    //默认自动识别语言
    language: {
      type: String,
      default: "C",
      required: false,
    },
  },
  setup(props) {
    return {
      hljs,
    };
  },
});
</script>

<style scoped>
:deep(.n-code) {
  border-radius: 4px;
  border: 1px solid #bbbec2;
  border-left: 3px solid #409eff;
}

:deep(.n-code .n-code__line-numbers) {
  font-size: 15px;
  border-top-left-radius: 4px;
  border-bottom-left-radius: 4px;
  padding: 0px 20px;
  background-color: #f1f1f1;
}
:deep(.n-code .__code__) {
  padding-left: 5px;
  width: 100%;
  font-size: 15px;
  background-color: #fafafa;
  border-top-right-radius: 4px;
  border-bottom-right-radius: 4px;
}
</style>
