import axios, { AxiosError, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { ElMessage } from 'element-plus';

declare module 'axios' {
  interface AxiosRequestConfig {
    skipErrorMessage?: boolean;
  }
}

const service = axios.create({
  baseURL: '/api',
  // 普通接口使用适中的等待时间，编译和文件接口按业务单独覆盖
  timeout: 30000,
  transitional: {
    clarifyTimeoutError: true,
  },
});

/**
 * 解析下载接口返回的 JSON 错误，附件内容保持原样
 */
async function decodeResponse(response: AxiosResponse) {
  if (response.data instanceof Blob
    && !response.headers['content-disposition']
    && String(response.headers['content-type']).includes('application/json')) {
    try {
      response.data = JSON.parse(await response.data.text());
    } catch (error) {
      throw AxiosError.from(error, AxiosError.ERR_BAD_RESPONSE, response.config, response.request, response);
    }
  }
  return response;
}

/**
 * 在安装路由前绑定当前用户会话，请求层不反向依赖 Store
 * @param session 用户 Store 提供的令牌与身份清理能力
 */
export function setupRequestInterceptors(session: { token: string; clearSession: (expired?: boolean) => void }) {
  /**
   * 旧请求的认证失败不能清除新登录的身份
   */
  function invalidateSession(config?: InternalAxiosRequestConfig) {
    if (session.token && config?.headers.Authorization === session.token) {
      session.clearSession(true);
    }
  }

  service.interceptors.request.use(config => {
    if (session.token) {
      config.headers.Authorization = session.token;
    }
    return config;
  }, undefined, { synchronous: true });

  // 先解析响应，解析失败同样进入下方统一错误处理
  service.interceptors.response.use(decodeResponse, async (error: unknown) => {
    if (axios.isAxiosError(error) && error.response) {
      await decodeResponse(error.response);
    }
    return Promise.reject(error);
  });

  service.interceptors.response.use(
    response => {
      if (response.data instanceof Blob || response.status === 204) {
        return response;
      }
      const result = response.data;
      if (result.code === 200) {
        return response;
      }
      if (result.code === 401) {
        invalidateSession(response.config);
      }
      if (!response.config.skipErrorMessage) {
        if (result.code === 400 || result.code === 409) {
          ElMessage.warning(result.msg);
        } else {
          ElMessage.error(result.msg);
        }
      }
      return Promise.reject(response);
    },
    (error: unknown) => {
      if (axios.isCancel(error) || !axios.isAxiosError(error)) {
        return Promise.reject(error);
      }
      if (error.response?.status === 401) {
        invalidateSession(error.config);
      }
      let message = error.message;
      if (error.code === AxiosError.ERR_NETWORK) {
        message = '后端接口连接异常';
      } else if (error.code === AxiosError.ETIMEDOUT) {
        message = '系统接口请求超时';
      } else if (error.code === AxiosError.ECONNABORTED) {
        message = '请求已中断';
      } else if (error.code === AxiosError.ERR_BAD_RESPONSE && error.response?.status === 200) {
        message = '服务响应格式错误';
      } else if (error.response) {
        message = error.response.data?.msg || `系统接口 ${error.response.status} 异常`;
      }
      if (!error.config?.skipErrorMessage) {
        ElMessage.error(message);
      }
      return Promise.reject(error);
    },
  );
}

/**
 * 发起请求并保留完整 HTTP 响应
 * @param options Axios 请求配置
 * @returns 指定响应体类型的完整响应
 */
function request<T>(options: AxiosRequestConfig): Promise<AxiosResponse<T>> {
  return service<T>(options);
}

export default request;
