import { defineStore } from 'pinia';
import { login, getUserProfile,logout,register } from '@/api/api';
import type { UserProfile } from '@/type/types';
import { useRouter,useRoute } from 'vue-router';
import Storage from '@/utils/storage';
import { ref,computed } from 'vue';
import { hasAnyValue,hasEveryValue,isNull } from '@/utils/utils';

/**
 * 用户信息
 */
export const useUserStore = defineStore('userInfo', () => { 
    /**
     * setup
     * ref() 就是 state 属性
     * computed() 就是 getters
     * function() 就是 actions
     */
    const router=useRouter();
    const route=useRoute();

    const user=ref<UserProfile>({} as UserProfile);
    const token=ref<string>(Storage.get<string>('Authorization')||"");

    /**
     * 只要token存在，就认为用户已登录
     */
    const isLogin=computed<boolean>(()=>{
        return !isNull(token.value);
    });

    /**
     * 判断是否可以刷新当前用户信息
     */
    const refreshUser=computed<boolean>(()=>{
        return !isNull(token.value) && isNull(user.value);
    })

    /**
     * 超级管理员固定为ID为1的用户
     */
    const isSuperAdmin = computed(() => user.value.id === 1);

    /**
     * 判断用户是否拥有任意一个角色
     */
    const hasAnyRole=(roles:string[]):boolean=>{
        if(isNull(user.value)){
            return false;
        }
        return hasAnyValue(user.value.roles,roles);
    }

    /**
     * 判断用户是否拥有所有权限
     */
    const hasEveryPermission=(permissions:string[]):boolean=>{
        if(isNull(user.value)){
            return false;
        }
        if (isSuperAdmin.value && user.value.permissions?.includes('*:*:*')) {
            return true;
        }
        return hasEveryValue(user.value.permissions,permissions);
    }

    /**
     * 异步登录
     */
    const doLogin=async (username:string,password:string,code:string,uuid:string)=>{
        try {
            const res = await login({ username, password, code, uuid });
            token.value = res.data.data;
            Storage.set('Authorization', res.data.data);
            await getUserInfo();
            //判断是否处于admin目录
            const isAdmin = route.path.split('/')[1] === 'admin';
            const homePath = isAdmin ? '/admin' : '/';
            //获取重定向路径
            const redirectPath = route.query && route.query.redirect as string || homePath;

            const query = route.query
            const otherQueryParams = Object.keys(query).reduce((acc: any, cur: string) => {
                if (cur !== "redirect") {
                    acc[cur] = query[cur]
                }
                return acc
            }, {})
            router.push({ path: redirectPath, query: otherQueryParams });
        } catch (error) {
            throw error;
        }
    }

    /**
     * 异步注册
     */
    const doRegister=async (username:string,password:string,rePassword:string,email:string,code:string,uuid:string)=>{
        try {
            await register({username,password,rePassword,email,code,uuid});
        }catch (error) {
            throw error;
        }
    }

    /**
     * 异步获取当前用户信息
     */
    const getUserInfo = async (force = false) => {
        if (force || refreshUser.value) {
            try {
                const res = await getUserProfile();
                user.value = res.data.data;
            } catch (error) {
                throw error;
            }
        }
    }

    /**
     * 异步登出
     */
    const doLogout=async ()=>{
        try {
            await logout();
        } catch (error) {
            throw error;
        }finally{
            //即使登出失败，也要清除用户信息
            user.value={} as UserProfile;
            token.value="";
            Storage.remove('Authorization');
            //判断是否处于admin目录
            const isAdmin=route.path.split('/')[1]==='admin';
            if(isAdmin){
                router.push('/admin/login');
            }else{
                router.push('/login');
            }
        }
    }

    return {
        user,
        token,
        isLogin,
        refreshUser,
        isSuperAdmin,
        doLogin,
        getUserInfo,
        doLogout,
        doRegister,
        hasAnyRole,
        hasEveryPermission
    }
});
