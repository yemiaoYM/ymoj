<template>
  <div style="width: 100%;height: 100%;">
    <MdPreview
      :theme="theme.resolvedTheme"
      :model-value="content"
      :sanitize="sanitizeMarkdown"
      previewTheme="cyanosis"
    />
  </div>
</template>

<script lang="ts">
import { useThemeStore } from '@/stores/theme';
import { defineComponent } from 'vue';
import { MdPreview } from 'md-editor-v3';
import { useFileImages } from '@/hooks/useFileImages';
import 'md-editor-v3/lib/preview.css';

export default defineComponent({
  name: 'MarkdownPreview',
  components: {
    MdPreview
  },
  props: {
    contestId: Number,
    publicImages: Boolean,
    content: {
      type: String,
      default: "写点什么呢？"
    }
  },
  setup(props) {
    const { sanitizeMarkdown } = useFileImages({
      sources: () => props.content.match(/\/api\/files\/[1-9]\d*/g) || [],
      contestId: () => props.contestId,
      publicImages: () => props.publicImages,
    });
    return { sanitizeMarkdown, theme: useThemeStore() };
  }
})
</script>
