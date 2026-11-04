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
import { ElMessage } from 'element-plus';
import { ref } from 'vue';
import { useUserStore } from '@/stores/user';
import { updateUserPassword,updateUserEmail } from '@/api/api';

//data
const userStore=useUserStore();
const formPasswordRef = ref();
const formEmailRef = ref();
const formPasswordLoading=ref<boolean>(false);
const formEmailLoading=ref<boolean>(false);
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
const handChangePassword = () => {
    formPasswordLoading.value = true;
    formPasswordRef.value.validate((valid: boolean) => {
        if (valid) {
            updateUserPassword(formPassword.value).then(res=>{
                ElMessage.success(res.data.msg);
            }).catch(err=>{
                console.log(err);
            }).finally(()=>{
                formPasswordLoading.value=false;   
            })
        } else {
            formPasswordLoading.value = false;
            ElMessage.warning('请检查输入');
            return false;
        }
    });
};
//更新用户邮箱
const handChangeEmail = () => {
    formEmailLoading.value = true;
    formEmailRef.value.validate((valid: boolean) => {
        if (valid) {
            updateUserEmail({
                password: formEmail.value.password,
                newEmail: formEmail.value.newEmail
            }).then(res=>{
                ElMessage.success(res.data.msg);
            }).catch(err=>{
                console.log(err);
            }).finally(()=>{
                formEmailLoading.value=false;
            })
        } else {
            formEmailLoading.value = false;
            ElMessage.warning('请检查输入');
            return false;
        }
    });
}
//rule
const passwordCheck = [
  { required: true, trigger: 'blur', message: '密码不能为空' },
  { min: 5, max: 20, trigger: 'blur', message: '密码长度在5-20位之间' }
]
// 修改密码规则
const rulePassword = {
  password: passwordCheck,
  newPassword: [
    ...passwordCheck,
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value === formPassword.value.password) {
          return callback(new Error('新密码不能与旧密码相同'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  againPassword: [
    { required: true, trigger: 'blur', message: '确认密码不能为空' },
    {
      validator: (_rule: any, value: string, callback: any) => {
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
const ruleEmail = {
  password: passwordCheck,
  newEmail: [
    { required: true, trigger: 'blur', message: '新邮箱不能为空' },
    { type: 'email', trigger: 'blur', message: '请输入正确的邮箱地址' },
    {
      validator: (_rule: any, value: string, callback: any) => {
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
