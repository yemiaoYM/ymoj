import request from '@/api/request';
import type { ApiResult, PageVO } from '@/types';
import type {
  ContestTableVO,
  ContestVO,
  RegisterContestDTO,
  ContestProblemTableVO,
  ContestProblemSubmissionPageQuery,
  ContestSubmissionPageQuery,
  ContestRankPageQuery,
  ContestRankTableVO,
  ContestPageQuery,
  AdminContest,
  AdminContestVO,
  ContestDTO,
  CloneContestRequest,
  AdminContestProblemVO,
  ContestProblemPageQuery,
} from '@/types/contest';
import type { ProblemVO, AdminProblemVO } from '@/types/problem';
import type { SubmissionTableVO } from '@/types/submission';

/**
 * 获取比赛列表
 * @param keyword 关键字
 * @param pageNo 页码
 * @param pageSize 每页条数
 * @returns 包含比赛列表的完整响应
 */
export function getContestTableList(keyword: string | null, pageNo: number, pageSize: number) {
  const params = {
    keyword,
    pageNo,
    pageSize
  };
  return request<ApiResult<PageVO<ContestTableVO>>>({
    url: '/public/contests',
    method: 'GET',
    params
  });
}

/**
 * 获取比赛详情
 * @param id 比赛ID
 * @returns 包含比赛详情的完整响应
 */
export function getContestInfo(id: number) {
  return request<ApiResult<ContestVO>>({
    url: `/contest/${id}`,
    method: 'GET'
  });
}

/**
 * 注册比赛
 * @param data 比赛ID和密码
 * @returns 操作结果，不包含业务数据
 */
export function registerContest(data: RegisterContestDTO) {
  return request<ApiResult<void>>({
    url: '/contest/register',
    method: 'POST',
    data
  });
}

/**
 * 获取比赛题目列表
 * @param id 比赛ID
 * @returns 包含比赛题目列表的完整响应
 */
export function getContestProblemList(id: number) {
  return request<ApiResult<ContestProblemTableVO[]>>({
    url: `/contest/${id}/problems`,
    method: 'GET',
  });
}

/**
 * 获取比赛题目详情
 * @param contestId 比赛ID
 * @param displayId 题目ID
 * @returns 包含比赛题目详情的完整响应
 */
export function getContestProblemInfo(contestId: number, displayId: string) {
  return request<ApiResult<ProblemVO>>({
    url: `/contest/${contestId}/problem/${displayId}`,
    method: 'GET',
  });
}

/**
 * 获取比赛题目提交记录
 * @param params 比赛ID和题目ID
 * @returns 包含比赛题目提交记录的完整响应
 */
export function getContestProblemSubmissionList(params: ContestProblemSubmissionPageQuery) {
  return request<ApiResult<PageVO<SubmissionTableVO>>>({
    url: '/contest/problem/submissions',
    method: 'GET',
    params
  });
}

/**
 * 获取比赛提交记录
 * @param params 比赛ID
 * @returns 包含比赛提交记录的完整响应
 */
export function getContestSubmissionList(params: ContestSubmissionPageQuery) {
  return request<ApiResult<PageVO<SubmissionTableVO>>>({
    url: '/contest/submissions',
    method: 'GET',
    params
  });
}

/**
 * 获取比赛排名列表
 * @param params 请求参数
 * @returns 包含比赛排名列表的完整响应
 */
export function getContestRankList(params: ContestRankPageQuery) {
  return request<ApiResult<ContestRankTableVO>>({
    url: '/contest/ranks',
    method: 'GET',
    params
  });
}

/**
 * 获取管理员比赛列表
 * @param params 查询参数
 * @returns 包含管理员比赛列表的完整响应
 */
export function getAdminContestList(params: ContestPageQuery) {
  return request<ApiResult<PageVO<AdminContest>>>({
    url: '/admin/contest/list',
    method: 'GET',
    params
  });
}

/**
 * 根据ID获取比赛详情（管理员）
 * @param id 比赛ID
 * @returns 包含比赛详情（管理员）的完整响应
 */
export function getAdminContestById(id: number) {
  return request<ApiResult<AdminContestVO>>({
    url: `/admin/contest/${id}`,
    method: 'GET'
  });
}

/**
 * 删除比赛（管理员）
 * @param ids 比赛ID
 * @returns 操作结果，不包含业务数据
 */
export function deleteContestByIds(ids: number | number[]) {
  return request<ApiResult<void>>({
    url: `/admin/contest/${ids}`,
    method: 'DELETE',
  });
}

/**
 * 更新比赛可见性（管理员）
 * @param id 比赛ID
 * @param visibility 可见性
 * @param password 密码（密码比赛时必填）
 * @returns 操作结果，不包含业务数据
 */
export function updateContestVisibility(id: number, visibility: number, password?: string | null) {
  const params: {
    visibility: number;
    password?: string;
  } = { visibility };
  if (password !== undefined && password !== null) {
    params.password = password;
  }
  return request<ApiResult<void>>({
    url: `/admin/contest/${id}/visibility`,
    method: 'PUT',
    params
  });
}

/**
 * 添加比赛（管理员）
 * @param contest 比赛信息
 * @returns 操作结果，不包含业务数据
 */
export function addAdminContest(contest: ContestDTO) {
  return request<ApiResult<void>>({
    url: '/admin/contest',
    method: 'POST',
    data: contest
  });
}

/**
 * 更新比赛（管理员）
 * @param id 比赛ID
 * @param contest 比赛信息
 * @returns 操作结果，不包含业务数据
 */
export function updateAdminContest(id: number, contest: ContestDTO) {
  return request<ApiResult<void>>({
    url: `/admin/contest/${id}`,
    method: 'PUT',
    data: contest
  });
}

/**
 * 克隆比赛（管理员）
 * @param id 比赛ID
 * @param data 新比赛标题、访问类型、时间和独立密码
 * @returns 操作结果，不包含业务数据
 */
export function cloneAdminContest(id: number, data: CloneContestRequest) {
  return request<ApiResult<void>>({
    url: `/admin/contest/${id}/clone`,
    method: 'POST',
    data
  });
}

/**
 * 获取比赛题目列表（管理员）
 * @param contestId 比赛ID
 * @returns 包含比赛题目列表（管理员）的完整响应
 */
export function getAdminContestProblems(contestId: number) {
  return request<ApiResult<AdminContestProblemVO[]>>({
    url: `/admin/contest/${contestId}/problems`,
    method: 'GET'
  });
}

/**
 * 获取可添加到比赛的题目列表（管理员）
 * @param contestId 比赛ID
 * @param params 题目标题或题号、难度及分页条件
 * @returns 包含可添加到比赛的题目列表（管理员）的完整响应
 */
export function getAvailableProblemsForContest(contestId: number, params: ContestProblemPageQuery) {
  return request<ApiResult<PageVO<AdminProblemVO>>>({
    url: `/admin/contest/${contestId}/available-problems`,
    method: 'GET',
    params
  });
}

/**
 * 批量添加题目到比赛（管理员）
 * @param contestId 比赛ID
 * @param problemIds 原题ID或原题ID列表
 * @returns 操作结果，不包含业务数据
 */
export function addProblemsToContest(contestId: number, problemIds: number | number[]) {
  return request<ApiResult<void>>({
    url: `/admin/contest/${contestId}/problems/${problemIds}`,
    method: 'POST'
  });
}

/**
 * 更新比赛题目的标题和气球颜色（管理员），展示ID由调整顺序功能管理
 * @param contestId 比赛ID
 * @param problemId 比赛题目关联ID
 * @param displayTitle 在比赛中显示的题目标题
 * @param color 气球颜色
 * @returns 操作结果，不包含业务数据
 */
export function updateContestProblem(
  contestId: number,
  problemId: number,
  displayTitle: string,
  color: string,
) {
  const params = { displayTitle, color };
  return request<ApiResult<void>>({
    url: `/admin/contest/${contestId}/problems/${problemId}`,
    method: 'PUT',
    params
  });
}

/**
 * 从比赛中移除题目（管理员）
 * @param contestId 比赛ID
 * @param problemIds 比赛题目关联ID或关联ID列表
 * @returns 操作结果，不包含业务数据
 */
export function removeContestProblems(contestId: number, problemIds: number | number[]) {
  return request<ApiResult<void>>({
    url: `/admin/contest/${contestId}/problems/${problemIds}`,
    method: 'DELETE'
  });
}

/**
 * 保存比赛题目的完整顺序（管理员）
 * @param contestId 比赛ID
 * @param ids 按目标顺序排列的完整比赛题目关联ID列表
 * @returns 操作结果，不包含业务数据
 */
export function reorderContestProblems(contestId: number, ids: number[]) {
  return request<ApiResult<void>>({
    url: `/admin/contest/${contestId}/problem-order`,
    method: 'PUT',
    data: ids
  });
}

/**
 * 上传比赛封面
 * @param file 上传文件
 * @returns 包含比赛封面公开地址的完整响应
 */
export function uploadContestImage(file: Blob) {
  const data = new FormData();
  data.append('file', file, file instanceof File ? file.name : 'contest.png');
  return request<ApiResult<string>>({
    url: '/admin/contest/image',
    method: 'POST',
    data,
    timeout: 120000,
  });
}
