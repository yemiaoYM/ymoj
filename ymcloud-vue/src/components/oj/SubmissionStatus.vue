<template>
  <el-popover
    v-if="canEditStatus || canRejudge"
    v-model:visible="actionsOpen"
    trigger="click"
    placement="bottom"
    :width="260"
    :disabled="pending"
  >
    <template #reference>
      <el-tag
        class="status-action"
        effect="dark"
        size="large"
        round
        disable-transitions
        tabindex="0"
        role="button"
        :type="getStatusType(submission.status).tag"
        :aria-label="`管理提交 ${submission.submissionId} 的状态`"
      >{{ submission.status }}</el-tag>
    </template>
    <x-flex class="status-actions" :size="10">
      <el-button v-if="canEditStatus" type="warning" :icon="Edit" @click="onCommand('status')">修改状态</el-button>
      <el-button v-if="canRejudge" type="primary" :icon="Refresh" @click="onCommand('rejudge')">重新评测</el-button>
    </x-flex>
  </el-popover>
  <el-tag
    v-else
    effect="dark"
    size="large"
    round
    disable-transitions
    :type="getStatusType(submission.status).tag"
  >{{ submission.status }}</el-tag>

  <el-dialog
    v-model="editing"
    title="修改状态"
    width="min(380px, calc(100vw - 32px))"
    align-center
    append-to-body
    :show-close="!pending"
    :close-on-click-modal="!pending"
    :close-on-press-escape="!pending"
  >
    <el-form class="status-form" label-width="52px">
      <el-form-item label="状态">
        <el-select v-model="status" :disabled="pending" aria-label="最终状态">
          <el-option v-for="item in FINAL_JUDGE_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="status === JUDGE_STATUS.PARTIAL_ACCEPTED.value" label="分数">
        <el-input-number v-model="score" :min="1" :max="99" :disabled="pending" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="pending" @click="editing = false">取消</el-button>
      <el-button type="primary" :loading="pending" :disabled="status === JUDGE_STATUS.PARTIAL_ACCEPTED.value && !score" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Edit, Refresh } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import { useAsyncChange } from '@/hooks/useAsyncChange';
import { rejudgeSubmission, updateSubmissionStatus } from '@/api/submission';
import type { SubmissionTableVO } from '@/types/submission';
import { getStatusType } from '@/utils/utils';
import { JUDGE_STATUS, FINAL_JUDGE_STATUS_OPTIONS, type FinalJudgeStatusCode } from '@/constants/judge';

const props = defineProps<{ submission: SubmissionTableVO }>();
const emit = defineEmits<{ changed: [] }>();
const userStore = useUserStore();
const canEditStatus = computed(() => userStore.isSuperAdmin
  && userStore.hasPermission('system:submission:edit'));
const canRejudge = computed(() => props.submission.canRejudge
  && userStore.hasPermission('system:submission:rejudge'));
const { pending, beforeChange } = useAsyncChange();
const editing = ref(false);
const actionsOpen = ref(false);
const status = ref<FinalJudgeStatusCode>(JUDGE_STATUS.ACCEPTED.value);
const score = ref<number | undefined>(50);

async function onCommand(command: string) {
  actionsOpen.value = false;
  if (command === 'status') {
    status.value = FINAL_JUDGE_STATUS_OPTIONS.find(item => item.label === props.submission.status)?.value
      ?? JUDGE_STATUS.ACCEPTED.value;
    score.value = props.submission.score !== null && props.submission.score > 0 && props.submission.score < 100
      ? props.submission.score : 50;
    editing.value = true;
    return;
  }
  const submission = props.submission;
  const success = await beforeChange(async () => {
    await ElMessageBox.confirm(`重新评测提交 ${submission.submissionId}？`, '重新评测', {
      type: 'warning', confirmButtonText: '重新评测', cancelButtonText: '取消',
    });
    await rejudgeSubmission(submission.submissionId, submission.executionId);
  });
  if (success) {
    ElMessage.success('已加入评测队列');
    emit('changed');
  }
}

async function onSave() {
  const success = await beforeChange(async () => {
    await updateSubmissionStatus(props.submission.submissionId, {
      executionId: props.submission.executionId,
      status: status.value,
      score: status.value === JUDGE_STATUS.PARTIAL_ACCEPTED.value ? score.value! : null,
    });
  });
  if (success) {
    editing.value = false;
    ElMessage.success('状态已更新');
    emit('changed');
  }
}
</script>

<style scoped>
.status-action {
  cursor: pointer;
}
.status-action:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 3px;
}
.status-actions .el-button {
  flex: 1;
  margin: 0;
}
.status-form .el-form-item {
  margin-bottom: 12px;
}
.status-form .el-form-item:last-child {
  margin-bottom: 0;
}
.status-form .el-input-number {
  width: 100%;
}
</style>
