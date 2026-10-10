<template>
  <span class="legal-links">
    <el-link
      href="#user-agreement"
      type="primary"
      :underline="false"
      @click.stop.prevent="openDocument('agreement')"
    >
      《用户协议》
    </el-link>
    <span>和</span>
    <el-link
      href="#privacy-policy"
      type="primary"
      :underline="false"
      @click.stop.prevent="openDocument('privacy')"
    >
      《隐私政策》
    </el-link>
  </span>

  <el-dialog
    v-model="visible"
    :title="document.title"
    width="min(760px, calc(100% - 32px))"
    top="6vh"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <article class="legal-document" tabindex="0" :aria-label="document.title">
      <p class="legal-date">更新日期：{{ legalUpdatedDate }}</p>
      <p>{{ document.introduction }}</p>
      <section
        v-for="section in document.sections"
        :key="section.title"
        :class="{ 'legal-important': section.important }"
      >
        <h3>{{ section.title }}</h3>
        <p v-for="paragraph in section.paragraphs" :key="paragraph">{{ paragraph }}</p>
      </section>
    </article>
    <template #footer>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { legalDocuments, legalUpdatedDate } from '@/constants/legalDocuments';
import type { LegalDocumentType } from '@/constants/legalDocuments';

const visible = ref(false);
const selectedType = ref<LegalDocumentType>('agreement');
const document = computed(() => legalDocuments[selectedType.value]);

const openDocument = (type: LegalDocumentType) => {
  selectedType.value = type;
  visible.value = true;
};
</script>

<style scoped>
.legal-links {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.legal-document {
  max-height: 64vh;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 12px;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.legal-document p {
  margin: 0 0 12px;
}

.legal-date {
  color: var(--el-text-color-secondary);
}

.legal-document h3 {
  margin: 24px 0 12px;
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
}

.legal-important {
  font-weight: 600;
}
</style>
