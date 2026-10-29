<template>
  <el-switch
    v-bind="$attrs"
    :model-value="modelValue"
    :loading="pending"
    :before-change="() => beforeChange(props.beforeChange)"
    @update:model-value="(value: boolean | string | number) => emit('update:modelValue', value)"
  />
</template>

<script setup lang="ts">
import { useAsyncChange, type ChangeResult } from '@/composables/useAsyncChange';

defineOptions({ inheritAttrs: false });
const props = defineProps<{
  modelValue: boolean | string | number;
  beforeChange: () => ChangeResult | Promise<ChangeResult>;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean | string | number] }>();
const { pending, beforeChange } = useAsyncChange();
</script>
