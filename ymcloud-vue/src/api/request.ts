import axios from "axios";
import { ElMessage } from 'element-plus'
import Storage from "../utils/storage";
import router from "@/router";

//添加请求拦截器
const service = axios.create({
  baseURL: '/api',
  withCredentials: true,
  timeout: 5000
});

//添加请求拦截器
service.interceptors.request.use(
  config => {
    let token = Storage.get<string>("Authorization");
    if (token && config.headers) {
      config.headers['Authorization'] = token;
    }
    return config;
  },
  error => {
    console.log(error)
    return Promise.reject(error)
  }
)
//添加响应拦截器
service.interceptors.response.use(
  response=>{
    //如果是文件操作的返回，由后续进行处理
    if (response.config.responseType === 'blob') {
      return response;
    }
    const res = response.data;
    //操作成功
    if(res.code === 200){
      return Promise.resolve(response);
    }
    //请求成功，但业务异常
    if(res.code === 500){
      ElMessage.error(res.msg);
    }
    //未登录
    if(res.code === 401){
      ElMessage.error(res.msg);
      //清除缓存
      Storage.remove("Authorization");
    }
    //参数列表错误（缺少，格式不匹配）
    if(res.code === 400){
      ElMessage.warning(res.msg);
    }
    //访问受限，权限不足，授权过期
    if(res.code === 403){
      ElMessage.error(res.msg);
    }
    //未找到
    if(res.code === 404){
      ElMessage.error(res.msg);
    }
    return Promise.reject(response);
  },
  error => {
    if(error.response){
      let { message } = error;
      if (message == "Network Error") {
        message = "后端接口连接异常";
      }else if (message.includes("timeout")) {
        message = "系统接口请求超时";
      }else if (message.includes("Request failed with status code")) {
        message = "系统接口" + message.substr(message.length - 3) + "异常";
      }
      ElMessage.error(message);
      return Promise.reject(error);
    }
  }
);

function request(options: any) {
  options.method = options.method || "get";
  return service(options);
}

export default request;