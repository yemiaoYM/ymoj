import request from '@/api/request';
import type {
  ProblemListQuery,
  ProblemTableVO,
  ProblemVO,
  AdminProblemPageQuery,
  AdminProblemVO,
  AdminProblemDetailVO,
  SpjCompileDTO,
  SpjCompileResult,
  ProblemDTO,
  ProblemCaseListVO,
  RemoteProblemRequest,
  RemoteProblemDraft,
  LanguageTableVO,
} from '@/types/problem';
import type { ApiResult, PageVO } from '@/types';

/**
 * 获取题目列表
 * @param params 题目列表信息
 * @returns 包含题目列表的完整响应
 */
export function getProblemTableList(params: ProblemListQuery) {
  return request<ApiResult<PageVO<ProblemTableVO>>>({
    url: '/public/problems',
    method: 'GET',
    params: {
      ...params,
      tagIds: params.tagIds.length ? params.tagIds.join(',') : undefined,
    }
  });
}

/**
 * 获取题目详情
 * @param id 题目ID
 * @returns 包含题目详情的完整响应
 */
export function getProblemDetail(id: string) {
  return request<ApiResult<ProblemVO>>({
    url: `/problem/${id}`,
    method: 'GET',
  });
}

/**
 * 获取随机题目
 * @returns 包含随机题目展示 ID 的完整响应
 */
export function getRandomProblem() {
  return request<ApiResult<string>>({
    url: '/problem/random',
    method: 'GET',
  });
}

/**
 * 获取管理员题目列表
 * @param params 查询参数
 * @returns 包含管理员题目列表的完整响应
 */
export function getAdminProblemList(params: AdminProblemPageQuery) {
  return request<ApiResult<PageVO<AdminProblemVO>>>({
    url: '/admin/problem/list',
    method: 'GET',
    params
  });
}

/**
 * 根据ID获取题目详情（管理员）
 * @param id 题目ID
 * @returns 包含题目详情（管理员）的完整响应
 */
export function getAdminProblemById(id: number) {
  return request<ApiResult<AdminProblemDetailVO>>({
    url: `/admin/problem/${id}`,
    method: 'GET',
  });
}

/**
 * 在 Worker 沙箱中检查 SPJ 编译，保留完整诊断供管理员修正
 * @param data 校验器语言和源码
 * @returns 包含 SPJ 编译结果和诊断信息的完整响应
 */
export function compileSpj(data: SpjCompileDTO) {
  return request<ApiResult<SpjCompileResult>>({
    url: '/admin/problem/spj/compile',
    method: 'POST',
    data,
    timeout: 50000,
  });
}

/**
 * 删除题目
 * @param ids 题目ID或ID数组
 * @returns 操作结果，不包含业务数据
 */
export function deleteProblemByIds(ids: number | number[]) {
  return request<ApiResult<void>>({
    url: `/admin/problem/${ids}`,
    method: 'DELETE',
  });
}

/**
 * 修改题目可见性
 * @param id 题目ID
 * @param visibility 手动可设置为公开或私有，比赛专属状态由引用关系维护
 * @returns 操作结果，不包含业务数据
 */
export function updateProblemVisibility(id: number, visibility: number) {
  return request<ApiResult<void>>({
    url: `/admin/problem/${id}/visibility`,
    method: 'PUT',
    params: { visibility }
  });
}

/**
 * 添加题目
 * @param problem 题目数据
 * @returns 包含新题目展示 ID 的完整响应
 */
export function addProblem(problem: ProblemDTO) {
  return request<ApiResult<string>>({
    url: '/admin/problem',
    method: 'POST',
    data: problem
  });
}

/**
 * 更新题目
 * @param problemId 题目数据库 ID
 * @param problem 题目数据
 * @returns 操作结果，不包含业务数据
 */
export function updateProblem(problemId: number, problem: ProblemDTO) {
  return request<ApiResult<void>>({
    url: `/admin/problem/${problemId}`,
    method: 'PUT',
    data: problem
  });
}

/**
 * 获取题目测试用例列表
 * @param problemId 题目ID
 * @returns 包含题目测试用例列表的完整响应
 */
export function getProblemTestCases(problemId: number) {
  return request<ApiResult<ProblemCaseListVO>>({
    url: `/admin/problem/${problemId}/testcases`,
    method: 'GET',
    skipErrorMessage: true
  });
}

/**
 * 上传测试用例文件
 * @param problemId 题目ID
 * @param files 文件列表
 * @param onUploadProgress 上传进度回调
 * @returns 发布后的清单版本
 */
export function uploadTestCaseFiles(problemId: number, files: FormData, onUploadProgress?: (event: {
  loaded: number;
  total?: number;
}) => void) {
  return request<ApiResult<number>>({
    url: `/admin/problem/${problemId}/testcases/upload`,
    method: 'POST',
    data: files,
    timeout: 5 * 60 * 1000,
    onUploadProgress,
    skipErrorMessage: true,
  });
}

/**
 * 下载测试用例文件
 * @param problemId 题目ID
 * @param fileId 文件主键
 * @param skipErrorMessage 是否由调用方展示错误
 * @returns 包含文件内容的完整响应
 */
export function downloadTestCaseFile(
  problemId: number,
  fileId: string,
  skipErrorMessage = false,
) {
  const params = {
    fileId
  };
  return request<Blob>({
    url: `/admin/problem/${problemId}/testcases/download`,
    method: 'GET',
    params,
    timeout: 5 * 60 * 1000,
    skipErrorMessage,
    responseType: 'blob'
  });
}

/**
 * 删除测试用例文件（支持单个或批量）
 * @param problemId 题目ID
 * @param version 编辑时的清单版本
 * @param names 测试点名称
 * @returns 发布后的清单版本
 */
export function deleteTestCaseFiles(problemId: number, version: number, names: string[]) {
  return request<ApiResult<number>>({
    url: `/admin/problem/${problemId}/testcases`,
    method: 'DELETE',
    params: { version },
    skipErrorMessage: true,
    data: names
  });
}

/**
 * 抓取远程题目草稿
 * @param data 请求业务数据
 * @returns 包含远程题目草稿的完整响应
 */
export function fetchRemoteProblem(data: RemoteProblemRequest) {
  return request<ApiResult<RemoteProblemDraft>>({
    url: '/admin/problem/remote',
    method: 'POST',
    data,
    timeout: 90000,
  });
}

/**
 * 获取语言列表
 * @returns 包含语言列表的完整响应
 */
export function getLanguageTableList() {
  return request<ApiResult<LanguageTableVO[]>>({
    url: '/public/languages',
    method: 'GET',
  });
}
