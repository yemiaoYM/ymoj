<template>
  <div style="width: 100%;height: 100%;">
    <MdPreview 
      :model-value="content"
      :sanitize="DOMPurify.sanitize"
      previewTheme="cyanosis"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { MdPreview } from 'md-editor-v3';
import DOMPurify from 'dompurify';
import 'md-editor-v3/lib/preview.css';

export default defineComponent({
  name: 'MarkdownPreview',
  components: {
    MdPreview
  },
  props: {
    content: {
      type: String,
      default: "写点什么呢？"
    }
  },
  setup() {
    return { DOMPurify };
  }
})
</script>
