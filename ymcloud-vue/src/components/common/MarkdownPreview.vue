<template>
  <div style="width: 100%;height: 100%;">
    <MdPreview 
      :model-value="content"
      :sanitize="sanitizeMarkdown"
      previewTheme="cyanosis"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { MdPreview } from 'md-editor-v3';
import { sanitizeMarkdown } from '@/utils/markdown';
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
    return { sanitizeMarkdown };
  }
})
</script>
