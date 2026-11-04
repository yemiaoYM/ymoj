<template>
  <div class="markdown-editor" v-loading="uploading" element-loading-text="正在上传正文图片" style="width: 100%;height: 100%;">
    <MdEditor
      :theme="theme.resolvedTheme"
      ref="editorRef"
      v-model="modelValue" 
      :toolbars="toolbars"
      :sanitize="sanitizeMarkdown"
      :disabled="disabled || uploading"
      previewTheme="cyanosis"
      show-toolbar-name
      no-upload-img
      @on-upload-img="onUploadImg"
      @on-drop="onDropImage"
    >
      <template #defToolbars>
        <MarkdownToolbarMenu
          title="文字"
          :icon="toolbarMenus.text.icon"
          :items="toolbarMenus.text.items"
          :disabled="disabled || uploading"
          @command="$event === 'mark' ? onMark() : editorRef.execCommand($event)"
        />
        <MarkdownToolbarMenu
          title="对齐"
          :icon="toolbarMenus.align.icon"
          :items="toolbarMenus.align.items"
          :disabled="disabled || uploading"
          @command="onAlign"
        />
        <MarkdownToolbarMenu
          title="列表"
          :icon="toolbarMenus.list.icon"
          :items="toolbarMenus.list.items"
          :disabled="disabled || uploading"
          @command="editorRef.execCommand($event)"
        />
        <MarkdownToolbarMenu
          title="代码"
          :icon="toolbarMenus.code.icon"
          :items="toolbarMenus.code.items"
          :disabled="disabled || uploading"
          @command="editorRef.execCommand($event)"
        />
        <DropdownToolbar
          title="图片"
          :visible="imageMenuVisible"
          :disabled="disabled || uploading"
          @on-change="imageMenuVisible = $event"
        >
          <x-flex inline align="center" :wrap="false" :size="2">
            <ImageIcon class="md-editor-icon" />
            <ChevronDown :size="12" aria-hidden="true" />
          </x-flex>
          <span class="md-editor-toolbar-item-name">图片</span>
          <template #overlay>
            <ul class="md-editor-menu" role="menu" @click="imageMenuVisible = false">
              <li
                class="md-editor-menu-item md-editor-menu-item-image"
                role="menuitem"
                tabindex="0"
                @click="editorRef.execCommand('image')"
                @keydown.enter="editorRef.execCommand('image'); imageMenuVisible = false"
              >添加链接</li>
              <li class="md-editor-menu-item md-editor-menu-item-image" role="none">
                <ImageCropUpload
                  ref="cropUploadRef"
                  :disabled="disabled || uploading"
                  :aspect-ratio="null"
                  :output-width="1920"
                  @cropped="onPreviewImage"
                >
                  <span role="menuitem">上传图片</span>
                </ImageCropUpload>
              </li>
            </ul>
          </template>
        </DropdownToolbar>
        <MarkdownToolbarMenu
          title="视图"
          :icon="toolbarMenus.view.icon"
          :items="toolbarMenus.view.items"
          :disabled="disabled || uploading"
          @command="onView"
        />
      </template>
    </MdEditor>
  </div>
</template>

<script>
import { useThemeStore } from '@/stores/theme';
import { ref,defineComponent,watch,onBeforeUnmount,nextTick } from 'vue';
import { DropdownToolbar, MdEditor } from 'md-editor-v3';
import {
  AlignCenter, AlignLeft, AlignRight, Bold, ChevronDown, Code, CodeXml,
  Columns2, Eye, Highlighter, Image as ImageIcon, Italic, List, ListChecks,
  ListOrdered, Pencil, Strikethrough, Subscript, Superscript, Underline
} from '@lucide/vue';
import ImageCropUpload from '@/components/common/ImageCropUpload.vue';
import MarkdownToolbarMenu from '@/components/common/MarkdownToolbarMenu.vue';
import { createMarkdownSanitizer } from '@/utils/markdown';
import { uploadMarkdownImage } from '@/api/markdown';
import { ElMessage } from 'element-plus';
import 'md-editor-v3/lib/style.css';

export default defineComponent({
  name: 'MarkdownEidt',
  components: {
    MdEditor,
    DropdownToolbar,
    ImageIcon,
    ChevronDown,
    ImageCropUpload,
    MarkdownToolbarMenu
  },
  props: {
    content: {
      type: String,
      default: "写点什么呢？"
    },
    disabled: {
      type: Boolean,
      default: false
    }
  },
  emits: ["update:content"],
  setup(props, { emit }) {
    const theme = useThemeStore();
    //配置
    const toolbars = [
      0,
      'title',
      'quote',
      1,
      2,
      '-',
      3,
      'link',
      4,
      'table',
      'katex',
      '-',
      'revoke',
      'next',
      '=',
      'pageFullscreen',
      'fullscreen',
      5,
    ];

    const toolbarMenus = {
      text: {
        icon: Bold,
        items: [
          { label: '加粗', command: 'bold', icon: Bold },
          { label: '斜体', command: 'italic', icon: Italic },
          { label: '下划线', command: 'underline', icon: Underline },
          { label: '删除线', command: 'strikeThrough', icon: Strikethrough },
          { label: '标记', command: 'mark', icon: Highlighter },
          { label: '下标', command: 'sub', icon: Subscript },
          { label: '上标', command: 'sup', icon: Superscript },
        ],
      },
      align: {
        icon: AlignLeft,
        items: [
          { label: '居左', command: 'left', icon: AlignLeft },
          { label: '居中', command: 'center', icon: AlignCenter },
          { label: '居右', command: 'right', icon: AlignRight },
        ],
      },
      list: {
        icon: List,
        items: [
          { label: '无序列表', command: 'unorderedList', icon: List },
          { label: '有序列表', command: 'orderedList', icon: ListOrdered },
          { label: '任务列表', command: 'task', icon: ListChecks },
        ],
      },
      code: {
        icon: CodeXml,
        items: [
          { label: '行内代码', command: 'codeRow', icon: CodeXml },
          { label: '代码块', command: 'code', icon: Code },
        ],
      },
      view: {
        icon: Eye,
        items: [
          { label: '仅编辑', command: 'edit', icon: Pencil },
          { label: '分屏预览', command: 'split', icon: Columns2 },
          { label: '仅预览', command: 'preview', icon: Eye },
        ],
      },
    };
    
    const modelValue = ref(props.content);
    const editorRef = ref();
    const cropUploadRef = ref();
    const imageMenuVisible = ref(false);
    const uploading = ref(false);
    const localImages = new Map();
    const sanitizeMarkdown = createMarkdownSanitizer(url => localImages.has(url));

    const onMark = () => {
      editorRef.value.insert((selected) => ({
        targetValue: `<mark>${selected || '标记文字'}</mark>`,
        select: true,
        deviationStart: 6,
        deviationEnd: -7,
      }));
    };

    const onAlign = (align) => {
      editorRef.value.insert((selected) => ({
        targetValue: `\n\n<div style="text-align: ${align}">\n\n${selected || '请输入内容'}\n\n</div>\n\n`,
        select: false,
      }));
    };

    const onView = (mode) => {
      editorRef.value.togglePreview(mode !== 'edit');
      editorRef.value.togglePreviewOnly(mode === 'preview');
    };

    const onUploadImg = (files) => {
      if (files.length !== 1) {
        ElMessage.warning('请一次选择一张图片进行裁剪');
        return;
      }
      cropUploadRef.value.open(files[0]);
    };

    const onDropImage = (event) => {
      const files = event.dataTransfer.files;
      if (files.length > 0) {
        event.preventDefault();
        onUploadImg(Array.from(files));
      }
    };

    const onPreviewImage = (blob) => {
      const url = URL.createObjectURL(blob);
      localImages.set(url, { blob, uploadedUrl: '' });
      editorRef.value.insert(() => ({ targetValue: `![](${url})\n`, select: false }));
    };

    // 保存方先完成表单校验；失败向保存边界传播，已上传图片在重试或撤销后复用
    const uploadImages = async () => {
      if (uploading.value || !editorRef.value) {
        return false;
      }
      const urls = modelValue.value.match(/blob:(?:https?:\/\/|null\/)[^\s)"'<>]+/g) || [];
      if (urls.some(url => !localImages.has(url))) {
        ElMessage.warning('本地图片已失效，请删除后重新选择图片');
        return false;
      }
      uploading.value = true;
      try {
        for (const url of new Set(urls)) {
          const image = localImages.get(url);
          if (!image.uploadedUrl) {
            const response = await uploadMarkdownImage(image.blob);
            image.uploadedUrl = response.data.data;
          }
          if (!editorRef.value) {
            return false;
          }
          modelValue.value = modelValue.value.replaceAll(url, image.uploadedUrl);
          await nextTick();
        }
        return urls.some(url => modelValue.value.includes(url)) ? false : modelValue.value;
      } finally {
        uploading.value = false;
      }
    };

    const releaseImages = () => {
      for (const url of localImages.keys()) {
        URL.revokeObjectURL(url);
      }
      localImages.clear();
    };
    onBeforeUnmount(releaseImages);

    //监听编辑器内部变化->通知父组件更新
    watch(modelValue, (val, previous) => {
      if (val === props.content) {
        return;
      }
      // 在受控值边界限长，避免编辑器读取粘贴/删除事件的空 data
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
        if (newContent === '') {
          releaseImages();
        }
        modelValue.value = newContent;
      }
    });

    return {
      theme,
      sanitizeMarkdown,
      modelValue,
      toolbars,
      toolbarMenus,
      editorRef,
      cropUploadRef,
      imageMenuVisible,
      uploading,
      onMark,
      onAlign,
      onView,
      onUploadImg,
      onDropImage,
      onPreviewImage,
      uploadImages
    }
  }
})
</script>

<style scoped>
.markdown-editor {
  min-width: 0;
}

.markdown-editor :deep(.md-editor-toolbar-wrapper) {
  overflow: visible;
}

.markdown-editor :deep(.md-editor-toolbar) {
  flex-wrap: wrap;
  gap: 4px 8px;
}

.markdown-editor :deep(.md-editor-toolbar-left),
.markdown-editor :deep(.md-editor-toolbar-right) {
  max-width: 100%;
  flex-wrap: wrap;
  row-gap: 4px;
}

.markdown-editor :deep(.md-editor-toolbar-right) {
  margin-inline-start: auto;
}

.markdown-editor :deep(.md-editor-toolbar-item svg.md-editor-icon) {
  width: 16px;
  height: 16px;
  padding: 4px;
}

.markdown-editor :deep(.md-editor-footer) {
  height: auto;
  min-height: 24px;
  flex-wrap: wrap;
}

.markdown-editor :deep(.md-editor-footer-right) {
  margin-inline-start: auto;
}
</style>
