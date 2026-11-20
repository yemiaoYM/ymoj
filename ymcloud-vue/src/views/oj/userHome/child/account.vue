<template>
  <div class="account-box">
    <!-- 更改密码区域 -->
    <el-row :gutter="20">
        <el-col :sm="24" :md="10" :lg="10">
            <div class="left">
                <div class="section-title">更改密码</div>
                <el-form
                    ref="formPasswordRef"
                    :model="formPassword"
                    label-position="top"
                    :rules="rulePassword"
                    :disabled="formPasswordLoading"
                >
                    <el-form-item label="当前密码" prop="password">
                        <el-input 
                            v-model="formPassword.password" 
                            type="password"
                            placeholder="请输入当前密码"
                            required />
                    </el-form-item>
                    <el-form-item label="新密码" prop="newPassword">
                        <el-input 
                            v-model="formPassword.newPassword" 
                            type="password" 
                            placeholder="请输入新密码"
                            required />
                    </el-form-item>
                    <el-form-item
                        label="确认新密码"
                        prop="rePassword"
                    >
                        <el-input 
                            v-model="formPassword.rePassword" 
                            type="password" 
                            placeholder="请再次输入新密码"
                            required />
                    </el-form-item>
                </el-form>

                <div class="change-button">
                    <el-button 
                        type="primary"
                        size="large"
                        :loading="formPasswordLoading"
                        @click="handChangePassword"
                        >更新密码</el-button>
                </div>
            </div>
        </el-col>
        <el-col :md="4" :lg="4">
            <div class="separator hidden-md-and-down"></div>
            <p></p>
        </el-col>
        <el-col :sm="24" :md="10" :lg="10">
            <!-- 更改邮箱区域 -->
            <div class="right">
                <div class="section-title">更改邮箱</div>
                <el-form
                    ref="formEmailRef"
                    :model="formEmail"
                    label-position="top"
                    :rules="ruleEmail"
                    :disabled="formEmailLoading"
                >
                    <el-form-item label="当前密码" prop="password">
                        <el-input v-model="formEmail.password" type="password" />
                    </el-form-item>
                    <el-form-item label="当前邮箱">
                        <el-input v-model="formEmail.oldEmail" disabled />
                    </el-form-item>
                    <el-form-item label="新邮箱" prop="newEmail">
                        <el-input v-model="formEmail.newEmail"/>
                    </el-form-item>
                </el-form>

                <div class="change-button">
                    <el-button 
                        type="primary"
                        size="large"
                        :loading="formEmailLoading"
                        @click="handChangeEmail"
                        >更新邮箱</el-button>
                </div>
            </div>
        </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
//import
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { ref } from 'vue';
import { useUserStore } from '@/stores/user';
import { updateUserPassword, updateUserEmail } from '@/api/user';
import { useAsyncChange } from '@/hooks/async/useAsyncChange';

//data
const userStore=useUserStore();
const formPasswordRef = ref<FormInstance>();
const formEmailRef = ref<FormInstance>();
const { pending: formPasswordLoading, beforeChange: changePassword } = useAsyncChange();
const { pending: formEmailLoading, beforeChange: changeEmail } = useAsyncChange();
const formPassword = ref({
    password: '',
    newPassword: '',
    rePassword: '',
});
const formEmail = ref({
    password: '',
    oldEmail: userStore.user?.email||'',
    newEmail: '',
});

//methods

//更新用户密码
const handChangePassword = async () => {
    const form = formPasswordRef.value;
    if (!form) return;
    await changePassword(async () => {
        await form.validate();
        const res = await updateUserPassword(formPassword.value);
        ElMessage.success(res.data.msg);
    });
};
//更新用户邮箱
const handChangeEmail = async () => {
    const form = formEmailRef.value;
    if (!form) return;
    await changeEmail(async () => {
        await form.validate();
        const res = await updateUserEmail({
            password: formEmail.value.password,
            newEmail: formEmail.value.newEmail
        });
        await userStore.getUserInfo(true);
        if (userStore.user) {
            formEmail.value.oldEmail = userStore.user.email;
        }
        ElMessage.success(res.data.msg);
    });
}
//rule
const passwordCheck = [
  { required: true, trigger: 'blur', message: '密码不能为空' },
  { min: 5, max: 20, trigger: 'blur', message: '密码长度在5-20位之间' }
]
// 修改密码规则
const rulePassword: FormRules<typeof formPassword.value> = {
  password: passwordCheck,
  newPassword: [
    ...passwordCheck,
    {
      validator: (_rule, value: string, callback) => {
        if (value === formPassword.value.password) {
          return callback(new Error('新密码不能与旧密码相同'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  rePassword: [
    { required: true, trigger: 'blur', message: '确认密码不能为空' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== formPassword.value.newPassword) {
          return callback(new Error('两次输入的密码不一致'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}
// 修改邮箱规则
const ruleEmail: FormRules<typeof formEmail.value> = {
  password: passwordCheck,
  newEmail: [
    { required: true, trigger: 'blur', message: '新邮箱不能为空' },
    { type: 'email', trigger: 'blur', message: '请输入正确的邮箱地址' },
    {
      validator: (_rule, value: string, callback) => {
        if (value === formEmail.value.oldEmail) {
          return callback(new Error('新邮箱不能与旧邮箱相同'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}
</script>

<style scoped>
.section-title {
    font-size: 22px;
    font-weight: 500;
    padding-top: 10px;
    padding-bottom: 20px;
    line-height: 30px;
    text-align: center;
}
:deep(.el-input__inner) {
  height: 32px;
}
:deep(.el-form-item__label) {
  font-size: 12px;
  line-height: 20px;
}
.separator {
  display: block;
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  border: 1px dashed var(--ym-upload-border);
}
.change-button {
  text-align: center;
}
</style>
