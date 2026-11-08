<template>
    <div class="user-info-box">
        <div class="section-title">头像设置</div>
        <div class="user-avatar">
            <Avatar
                :text="formProfile.username"
                :src="formProfile.avatar"
                :size="150" 
            />
        </div>
        <el-upload
            class="upload-demo"
            drag
            :action="uploadUrl" 
            :headers="headers"
            :disabled="loadingAvatar"
            method="post"
            :on-success="handleSuccess" 
            :on-error="handleError"    
            :before-upload="handleSelectFile"
            :show-file-list="false"
            >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
            将头像拖放到此处，或单击此处
            </div>
        </el-upload>
        <div class="section-title">资料设置</div>
        <el-form :model="formProfile" :disabled="loadingSaveBtn" label-position="top">
            <el-row :gutter="30" justify="space-around">
                <el-col :md="10" :xs="24">
                    <el-form-item label="昵称 (非用户名)">
                        <el-input v-model="formProfile.nickname" :maxlength="20" />
                    </el-form-item>
                    <el-form-item label="学校">
                        <el-input v-model="formProfile.school" :maxlength="20" />
                    </el-form-item>
                </el-col>
                
                <el-col :md="10" :xs="24">
                    <el-form-item label="博客 ( 比如CSDN ) ">
                        <el-input v-model="formProfile.blog"/>
                    </el-form-item>
                    <el-form-item label="性别">
                        <el-radio-group v-model="formProfile.sex">
                            <el-radio label="男" value="男" border>男</el-radio>
                            <el-radio label="女" value="女" border>女</el-radio>
                            <el-radio label="保密" value="保密" border>保密</el-radio>
                        </el-radio-group>
                    </el-form-item>
                </el-col>
            </el-row>

            <el-row>
                <el-col :span="24">
                    <label class="el-form-item__label" style="float: none;">个性签名</label>
                    <MarkdownEidt
                        ref="signatureEditorRef"
                        v-model:content="formProfile.signature"
                        :disabled="loadingSaveBtn"
                    />
                </el-col>
                <el-col :span="24" style="text-align: center;margin-top: 15px;">
                    <el-button
                        type="primary"
                        @click="handUpdateUserInfo"
                        :loading="loadingSaveBtn"
                        size="large"
                        >保存</el-button>
                </el-col>
            </el-row>
        </el-form>
    </div>
</template>

<script setup lang="ts">
//import
import { computed, onMounted, ref, defineAsyncComponent } from 'vue';
import { useUserStore } from '@/stores/user';
import { updateUserProfile } from '@/api/api';
import { useAsyncChange } from '@/hooks/useAsyncChange';
import { ElMessage, ElNotification } from 'element-plus';
const Avatar=defineAsyncComponent(() => import('@/components/common/Avatar.vue'));
const MarkdownEidt=defineAsyncComponent(() => import('@/components/common/markdown/MarkdownEidt.vue'));

//data
const userStore=useUserStore();
interface FormProfile {
    username: string,
    nickname: string,
    school: string,
    blog: string,
    sex: string,
    signature: string,
    avatar: string,
}
const formProfile=ref<FormProfile>({
    username: '',
    nickname: '',
    school: '',
    blog: '',
    sex: '保密',
    signature: '',
    avatar: '',
});
const { pending: loadingSaveBtn, beforeChange: saveProfile } = useAsyncChange();
const { pending: loadingAvatar, beforeChange: refreshAvatar } = useAsyncChange();
const signatureEditorRef = ref<InstanceType<typeof MarkdownEidt>>();
//后端接口地址
const uploadUrl = '/api/user/upload-avatar';  
const headers=computed(() => ({
    Authorization : userStore.token
}));


//methods
onMounted(()=>{
    const userInfo=userStore.user;
    if (userInfo) {
        Object.assign(formProfile.value, userInfo);
    }
})


const checkFileType=(file: File)=> {
  if (!/\.(gif|jpg|jpeg|png|bmp|webp|GIF|JPG|PNG|WEBP)$/.test(file.name)) {
    ElNotification({
      title: "文件类型不支持",
      message: file.name + "不是正确格式的文件",
      type: 'warning',
    });
    return false;
  }
  return true;
}
const checkFileSize=(file: File)=>{
  // max size is 2MB
  if (file.size > 2 * 1024 * 1024) {
    ElNotification({
      title: "文件大小超过限制2Mb",
      message: file.name + "大小超过2Mb",
      type: 'warning',
    });
    return false;
  }
  return true;
}
const handleSelectFile=(file: File)=>{
  return checkFileType(file) && checkFileSize(file);
}
//上传成功回调
const handleSuccess = async (response: { code: number; msg: string }) => {
    if (response.code !== 200) {
        ElMessage.error(response.msg);
        return;
    }
    await refreshAvatar(async () => {
        await userStore.getUserInfo(true);
        if (userStore.user) {
            formProfile.value.avatar = userStore.user.avatar;
            ElMessage.success(response.msg);
        }
    });
};
//上传失败回调
const handleError = () => {
  ElMessage.error("上传失败");
};
//更新用户信息
const handUpdateUserInfo=async ()=>{
    const editor = signatureEditorRef.value;
    if (!editor || loadingSaveBtn.value) return;
    await saveProfile(async () => {
        if (await editor.uploadImages() === false) return false;
        const res = await updateUserProfile({
            nickname: formProfile.value.nickname,
            school: formProfile.value.school,
            blog: formProfile.value.blog,
            sex: formProfile.value.sex,
            signature: formProfile.value.signature
        });
        await userStore.getUserInfo(true);
        ElMessage.success(res.data.msg);
    });
}
</script>

<style lang="css" scoped>
.user-info-box{
    .upload-demo{
        width: 320px;
        margin: 0 auto;
        .el-icon--upload{
            color: var(--ym-link);
        }
    }
    .user-avatar{
        text-align: center;
        margin-bottom: 10px;
    }
    .section-title {
        font-size: 22px;
        font-weight: 500;
        padding-top: 15px;
        padding-bottom: 20px;
        line-height: 30px;
        text-align: center;
    }
}
.md-editor{
    border-radius: 8px;
    box-shadow: 0 2px 8px var(--ym-header-shadow);
}
:deep(.el-form-item__label) {
    font-size: 12px;
    line-height: 20px;
}
</style>
