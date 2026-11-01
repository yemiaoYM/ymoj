import request from "./request";
import type { AxiosResponse } from 'axios';
import type { AnnouncementPageQuery, AnnouncementPageResult } from '@/type/announcement';
import type { CloneContestRequest, ContestProblemPageQuery } from '@/type/contest';
import type { TagDTO, TagClassificationDTO, TagClassification, TagsWithClassification, TagResult } from '@/type/tag';
import type {
  LoginDTO,
  RegisterDTO,
  UserDTO,
  SystemSettingDTO,
  SystemSettingVO,
  SystemSettingResult,
} from "@/type/types";

/**
 * 获取公开的系统配置
 */
export function getPublicSystemSetting(): Promise<AxiosResponse<SystemSettingResult<SystemSettingVO>>> {
  return request({
    url: "/public/system-setting",
    method: "GET",
  })
}

/**
 * 获取后台系统配置
 */
export function getAdminSystemSetting(): Promise<AxiosResponse<SystemSettingResult<SystemSettingVO>>> {
  return request({
    url: "/admin/system-setting",
    method: "GET",
  })
}

/**
 * 保存后台系统配置
 * @param data 系统配置
 */
export function updateSystemSetting(data: SystemSettingDTO): Promise<AxiosResponse<SystemSettingResult<null>>> {
  return request({
    url: "/admin/system-setting",
    method: "PUT",
    data
  })
}


//================= OJ =====================

/**
 * 获取比赛列表
 * @param keyword 关键字
 * @param pageNo 页码
 * @param pageSize 每页条数
 * @returns 比赛列表
 */
export function getContestTableList(keyword: string | null, pageNo: number, pageSize: number) {
  let params = {
    keyword,
    pageNo,
    pageSize
  }
  return request({
    url: "/public/contests",
    method: "GET",
    params
  })
}
/**
 * 获取标签列表
 * @returns 标签列表
 */
export function getTagsList() {
  return request({
    url: "/public/tags",
    method: "GET",
  })
}
/**
 * 获取题目列表
 * @param params 题目列表信息
 * @returns 题目列表
 */
export function getProblemTableList(params: ProblemListQuery) {
  return request({
    url: "/public/problems",
    method: "GET",
    params: {
      ...params,
      tagIds: params.tagIds.length ? params.tagIds.join(',') : undefined,
    }
  })
}

export type ProblemListQuery = {
  pageNo: number;
  pageSize: number;
  keyword: string;
  difficulty: number;
  tagIds: number[];
};
/**
 * 获取题目详情
 * @param id 题目ID
 * @returns 题目详情
 */
export function getProblemDetail(id: string) {
  return request({
    url: `/problem/${id}`,
    method: "GET",
  })
}
/**
 * 获取验证码
 * @returns 验证码
 */
export function getCaptcha() {
  return request({
    url: "/captchaImage",
    method: "GET",
  })
}
/**
 * 登录
 * @param data 登录信息
 * @returns token
 */
export function login(data: LoginDTO) {
  return request({
    url: "/login",
    method: "POST",
    data
  })
}
/**
 * 注册
 * @param data 注册信息
 */
export function register(data: RegisterDTO) {
  return request({
    url: "/register",
    method: "POST",
    data
  })
}
/**
 * 忘记密码-发送验证码
 * @param data 忘记密码信息
 */
export function forgetPasswordSendCode(data: any) {
  return request({
    url: "/forget-code",
    method: "POST",
    data
  })
}
/**
 * 忘记密码-重置密码
 * @param data 忘记密码信息
 */
export function forgetPasswordResetPassword(data: any) {
  return request({
    url: "/forget-password",
    method: "PUT",
    data
  })
}
/**
 * 获取当前用户基本信息
 * @returns 当前用户基本信息
 */
export function getUserProfile() {
  return request({
    url: "/user/profile",
    method: "GET",
  })
}
/**
 * 登出
 */
export function logout() {
  return request({
    url: "/logout",
    method: "GET",
  })
}
/**
 * 获取提交列表
 * @returns 提交列表
 * @param params
 */
export function getSubmissionTableList(params: any) {
  return request({
    url: "/public/submissions",
    method: "GET",
    params
  });
}
/**
 * 获取用户信息
 * @param username 用户名
 * @returns 用户信息
 */
export function getUserInfoByUsername(username: string) {
  return request({
    url: "/public/user-info",
    method: "GET",
    params: { username }
  })
}
/**
 * 获取用户日历热力图
 * @param username 用户名
 * @returns 用户日历热力图
 */
export function getUserCalendarHeatmap(username: string) {
  return request({
    url: "/public/user-calendar-heatmap",
    method: "GET",
    params: {
      username
    }
  })
}
/**
 * 更新用户资料
 * @param data 用户资料
 */
export function updateUserProfile(data: any) {
  return request({
    url: "/user/update-profile",
    method: "PUT",
    data
  })
}
/**
 * 更新用户密码
 * @param data 用户密码
 */
export function updateUserPassword(data: any) {
  return request({
    url: "/user/update-password",
    method: "PUT",
    data
  })
}
/**
 * 更新用户邮箱
 * @param data 用户邮箱
 */
export function updateUserEmail(data: any) {
  return request({
    url: "/user/update-email",
    method: "PUT",
    data
  })
}
/**
 * 获取提交详情
 * @param id 提交ID
 * @returns 提交详情
 */
export function getSubmissionDetail(id: number) {
  return request({
    url: `/submission/${id}`,
    method: "GET",
  })
}
/**
 * 更新分享代码状态
 * @param id 提交ID
 */
export function updateShareCode(id: number) {
  return request({
    url: `/submission/${id}/share`,
    method: "PUT",
  })
}
/**
 * 提交题目评测
 * @param data 提交评测信息
 * @returns 评测结果
 */
export function submitJudge(data: any) {
  return request({
    url: "/judge/submit",
    method: "POST",
    data
  })
}
/**
 * 测试评测
 * @param data 测试评测信息
 * @returns 评测结果
 */
export function testJudge(data: any) {
  return request({
    url: "/judge/test",
    method: "POST",
    data
  })
}
/**
 * 定时轮询获取评测状态
 * @param params 评测状态信息
 * @returns 评测状态
 */
export function getJudgeStatus(params: any) {
  return request({
    url: "/judge/status",
    method: "GET",
    params
  })
}
/**
 * 获取题目提交列表
 * @param params 题目提交列表信息
 * @returns 题目提交列表
 */
export function getProblemSubmissionList(params: any) {
  return request({
    url: "/problem/submissions",
    method: "GET",
    params
  })
}
/**
 * 获取用户AC排名
 * @returns 用户AC排名
 */
export function getUserAcRank() {
  return request({
    url: "/public/user-ac-rank",
    method: "GET"
  })
}
/**
 * 获取随机题目
 * @returns 随机题目的唯一标识
 */
export function getRandomProblem() {
  return request({
    url: "/problem/random",
    method: "GET",
  })
}
/**
 * 获取比赛详情
 * @param id 比赛ID
 * @returns 比赛详情
 */
export function getContestInfo(id: number) {
  return request({
    url: `/contest/${id}`,
    method: "GET"
  })
}
/**
 * 注册比赛
 * @param data 比赛ID和密码
 */
export function registerContest(data: any) {
  return request({
    url: "/contest/register",
    method: "POST",
    data
  })
}
/**
 * 获取比赛题目列表
 * @param id 比赛ID
 * @returns 比赛题目列表
 */
export function getContestProblemList(id: number) {
  return request({
    url: `/contest/${id}/problems`,
    method: "GET",
  })
}
/**
 * 获取比赛题目详情
 * @param contestId 比赛ID
 * @param displayId 题目ID
 * @returns 比赛题目详情
 */
export function getContestProblemInfo(contestId: number, displayId: string) {
  return request({
    url: `/contest/${contestId}/problem/${displayId}`,
    method: "GET",
  })
}
/**
 * 获取比赛题目提交记录
 * @param params 比赛ID和题目ID
 * @returns 比赛题目提交记录
 */
export function getContestProblemSubmissionList(params: any) {
  return request({
    url: "/contest/problem/submissions",
    method: "GET",
    params
  })
}
/**
 * 获取比赛提交记录
 * @param params 比赛ID
 * @returns 比赛提交记录
 */
export function getContestSubmissionList(params: any) {
  return request({
    url: "/contest/submissions",
    method: "GET",
    params
  })
}
/**
 * 获取比赛排名列表
 * @param params 请求参数
 * @returns 比赛排名列表
 */
export function getContestRankList(params: any) {
  return request({
    url: "/contest/ranks",
    method: "GET",
    params
  })
}
/**
 * 获取公开公告分页；错误由公告区域展示，避免重复弹窗。
 */
export function getAnnouncementTableList(params: AnnouncementPageQuery): Promise<AxiosResponse<AnnouncementPageResult>> {
  return request({
    url: '/public/announcements',
    method: 'GET',
    params,
    skipErrorMessage: true,
  });
}
/**
 * 获取语言列表
 * @returns 语言列表
 */
export function getLanguageTableList() {
  return request({
    url: "/public/languages",
    method: "GET",
  })
}

//================= Admin =====================

/**
 * 获取管理员首页统计数据
 * @returns 统计数据
 */
export function getAdminDashboardStats() {
  return request({
    url: "/admin/dashboard/stats",
    method: "GET",
  })
}
/**
 * 获取最近两周提交数据折线图
 * @returns 折线图数据
 */
export function getAdminSubmissionChart() {
  return request({
    url: "/admin/dashboard/submission-chart",
    method: "GET",
  })
}
/**
 * 获取菜单列表
 * @returns 菜单列表
 */
export function getMenus(){
  return request({
    url: "/admin/menu",
    method: "GET",
  })
}
/**
 * 获取服务器配置
 * @returns 服务器配置
 */
export function getServerConfig() {
  return request({
    url: "/monitor/server",
    method: "GET",
  })
}
/**
 * 获取在线用户列表
 * @returns 在线用户列表
 */
export function getUserOnlineList() {
  return request({ 
    url: "/monitor/online/list", 
    method: "GET" 
  })
}
/**
 * 强制下线
 * @param token 会话编号
 */
export function forceOffline(token: string) {
  return request({ 
    url: "/monitor/online/" + token, 
    method: "DELETE", 
  })
}
/**
 * 获取用户列表
 * @param params 请求参数
 * @returns 用户列表
 */
export function getUserTableList(params: any) {
  return request({
    url: "/admin/user/list",
    method: "GET",
    params
  })
}
/**
 * 获取用户信息
 * @param id 用户ID
 * @returns 用户信息
 */
export function getUserById(id: number) {
  return request({
    url: `/admin/user/${id}`,
    method: "GET",
  })
}
/**
 * 添加用户
 * @param data 用户信息
 */
export function addUser(data: UserDTO) {
  return request({ 
    url: "/admin/user", 
    method: "POST", 
    data 
  })
}
/**
 * 更新用户
 * @param data 用户信息
 */
export function updateUser(data: UserDTO) {
  return request({ 
    url: "/admin/user", 
    method: "PUT", 
    data 
  })
}
/**
 * 更新用户状态
 * @param id 用户ID
 * @param status 状态
 */
export function changeUserStatus(id: number, status: string) {
  const data = {
    id,
    status
  }
  return request({
    url: '/admin/user/status',
    method: "PUT",
    data: data
  })
}
/**
 * 删除用户
 * @param id 用户ID
 */
export function deleteUserByIds(id: number | number[]) {
  return request({
    url: `/admin/user/${id}`,
    method: "DELETE",
  })
}
/**
 * 重置用户密码
 * @param id 用户ID
 * @param password 新密码
 */
export function resetUserPassword(id: number, password: string) {
  const data = {
    id,
    password
  }
  return request({
    url: '/admin/user/reset-password',
    method: "PUT",
    data: data
  })
}

/**
 * 获取管理员题目列表
 * @param params 查询参数
 * @returns 管理员题目列表
 */
export function getAdminProblemList(params: any) {
  return request({
    url: "/admin/problem/list",
    method: "GET",
    params
  })
}

/**
 * 根据ID获取题目详情（管理员）
 * @param id 题目ID
 * @returns 题目详情
 */
export function getAdminProblemById(id: number) {
  return request({
    url: `/admin/problem/${id}`,
    method: "GET",
  })
}

/**
 * 删除题目
 * @param ids 题目ID或ID数组
 */
export function deleteProblemByIds(ids: number | number[]) {
  return request({
    url: `/admin/problem/${ids}`,
    method: "DELETE",
  })
}

/**
 * 修改题目可见性
 * @param id 题目ID
 * @param visibility 手动可设置为公开或私有，比赛专属状态由引用关系维护
 */
export function updateProblemVisibility(id: number, visibility: number) {
  return request({
    url: `/admin/problem/${id}/visibility`,
    method: "PUT",
    params: { visibility }
  })
}

/**
 * 添加题目
 * @param problem 题目数据
 */
export function addProblem(problem: any) {
  return request({
    url: "/admin/problem",
    method: "POST",
    data: problem
  })
}

/**
 * 更新题目
 * @param problemId
 * @param problem 题目数据
 */
export function updateProblem(problemId: number, problem: any) {
  return request({
    url: `/admin/problem/${problemId}`,
    method: "PUT",
    data: problem
  })
}

/**
 * 获取题目测试用例列表
 * @param problemId 题目ID
 */
export function getProblemTestCases(problemId: number) {
  return request({
    url: `/admin/problem/${problemId}/testcases`,
    method: "GET"
  })
}
/**
 * 上传测试用例文件
 * @param problemId 题目ID
 * @param files 文件列表
 * @param onUploadProgress
 */
export function uploadTestCaseFiles(
  problemId: number,
  files: FormData,
  onUploadProgress?: (event: { loaded: number; total?: number }) => void
) {
  return request({
    url: `/admin/problem/${problemId}/testcases/upload`,
    method: "POST",
    data: files,
    timeout: 5 * 60 * 1000,
    onUploadProgress,
    skipErrorMessage: true,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
/**
 * 下载测试用例文件
 * @param problemId 题目ID
 * @param fileName 文件名
 */
export function downloadTestCaseFile(problemId: number, fileName: string) {
  let params = {
    fileName
  }
  return request({
    url: `/admin/problem/${problemId}/testcases/download`,
    method: "GET",
    params,
    timeout: 5 * 60 * 1000,
    responseType: 'blob'
  })
}

/**
 * 删除测试用例文件（支持单个或批量）
 * @param problemId 题目ID
 * @param fileNames 文件名数组
 */
export function deleteTestCaseFiles(problemId: number, fileNames: string[]) {
  return request({
    url: `/admin/problem/${problemId}/testcases`,
    method: "DELETE",
    data: fileNames
  })
}

/**
 * 获取标签和分类列表（管理员）
 * @returns 标签和分类列表
 */
export function getAdminTagsAndClassifications(): Promise<AxiosResponse<TagResult<TagsWithClassification[]>>> {
  return request({
    url: "/admin/tag/list",
    method: "GET"
  })
}

/**
 * 添加标签（管理员）
 * @param tag 标签信息
 * @returns 操作结果
 */
export function addAdminTag(tag: TagDTO): Promise<AxiosResponse<TagResult<null>>> {
  return request({
    url: "/admin/tag",
    method: "POST",
    data: tag
  })
}

/**
 * 更新标签（管理员）
 * @param id 标签ID
 * @param tag 标签信息
 * @returns 操作结果
 */
export function updateAdminTag(id: number, tag: TagDTO): Promise<AxiosResponse<TagResult<null>>> {
  return request({
    url: `/admin/tag/${id}`,
    method: "PUT",
    data: tag
  })
}

/**
 * 删除标签（管理员）
 * @param id 标签ID
 * @returns 操作结果
 */
export function deleteAdminTag(id: number): Promise<AxiosResponse<TagResult<null>>> {
  return request({
    url: `/admin/tag/${id}`,
    method: "DELETE"
  })
}

/**
 * 获取标签分类列表（管理员）
 * @returns 标签分类列表
 */
export function getAdminTagClassifications(): Promise<AxiosResponse<TagResult<TagClassification[]>>> {
  return request({
    url: "/admin/tag/classification/list",
    method: "GET"
  })
}

/**
 * 添加标签分类（管理员）
 * @param classification 标签分类信息
 * @returns 操作结果
 */
export function addAdminTagClassification(classification: TagClassificationDTO): Promise<AxiosResponse<TagResult<null>>> {
  return request({
    url: "/admin/tag/classification",
    method: "POST",
    data: classification
  })
}

/**
 * 更新标签分类（管理员）
 * @param id 标签分类ID
 * @param classification 标签分类信息
 * @returns 操作结果
 */
export function updateAdminTagClassification(id: number, classification: TagClassificationDTO): Promise<AxiosResponse<TagResult<null>>> {
  return request({
    url: `/admin/tag/classification/${id}`,
    method: "PUT",
    data: classification
  })
}

/**
 * 删除标签分类（管理员）
 * @param id 标签分类ID
 * @returns 操作结果
 */
export function deleteAdminTagClassification(id: number): Promise<AxiosResponse<TagResult<null>>> {
  return request({
    url: `/admin/tag/classification/${id}`,
    method: "DELETE"
  })
}

/**
 * 获取管理员比赛列表
 * @param params 查询参数
 * @returns 管理员比赛列表
 */
export function getAdminContestList(params: any) {
  return request({
    url: "/admin/contest/list",
    method: "GET",
    params
  })
}

/**
 * 根据ID获取比赛详情（管理员）
 * @param id 比赛ID
 * @returns 比赛详情
 */
export function getAdminContestById(id: number) {
  return request({
    url: `/admin/contest/${id}`,
    method: "GET"
  })
}

/**
 * 获取公告列表（管理员）
 * @param params 查询参数
 * @returns 公告列表
 */
export function getAdminAnnouncementList(params: any) {
  return request({
    url: "/admin/announcement/list",
    method: "GET",
    params
  })
}

/**
 * 根据ID获取公告详情（管理员）
 * @param id 公告ID
 * @returns 公告详情
 */
export function getAdminAnnouncementById(id: number) {
  return request({
    url: `/admin/announcement/${id}`,
    method: "GET"
  })
}

/**
 * 新增公告（管理员）
 * @param announcement 公告信息
 * @returns 操作结果
 */
export function addAnnouncement(announcement: any) {
  return request({
    url: "/admin/announcement",
    method: "POST",
    data: announcement
  })
}

/**
 * 修改公告（管理员）
 * @param id 公告ID
 * @param announcement 公告信息
 * @returns 操作结果
 */
export function updateAnnouncement(id: number, announcement: any) {
  return request({
    url: `/admin/announcement/${id}`,
    method: "PUT",
    data: announcement
  })
}

/**
 * 删除公告（管理员）
 * @param ids 公告ID列表
 * @returns 操作结果
 */
export function deleteAnnouncementByIds(ids: number | number[]) {
  return request({
    url: `/admin/announcement/${ids}`,
    method: "DELETE"
  })
}

/**
 * 修改公告状态（管理员）
 * @param id 公告ID
 * @param status 状态
 * @returns 操作结果
 */
export function updateAnnouncementStatus(id: number, status: boolean) {
  return request({
    url: `/admin/announcement/${id}/status`,
    method: "PUT",
    params: { status }
  })
}

/**
 * 修改公告置顶状态（管理员）
 * @param id 公告ID
 * @param pinned 置顶状态
 * @returns 操作结果
 */
export function updateAnnouncementPinned(id: number, pinned: boolean) {
  return request({
    url: `/admin/announcement/${id}/pinned`,
    method: "PUT",
    params: { pinned }
  })
}

/**
 * 删除比赛（管理员）
 * @param ids 比赛ID
 * @returns 操作结果
 */
export function deleteContestByIds(ids: number | number[]) {
  return request({
    url: `/admin/contest/${ids}`,
    method: "DELETE",
  })
}

/**
 * 更新比赛可见性（管理员）
 * @param id 比赛ID
 * @param visibility 可见性
 * @param password 密码（密码比赛时必填）
 * @returns 操作结果
 */
export function updateContestVisibility(id: number, visibility: number, password?: string | null) {
  const params: any = { visibility };
  if (password !== undefined && password !== null) {
    params.password = password;
  }
  return request({
    url: `/admin/contest/${id}/visibility`,
    method: "PUT",
    params
  })
}


/**
 * 添加比赛（管理员）
 * @param contest 比赛信息
 * @returns 操作结果
 */
export function addAdminContest(contest: any) {
  return request({
    url: "/admin/contest",
    method: "POST",
    data: contest
  })
}

/**
 * 更新比赛（管理员）
 * @param id 比赛ID
 * @param contest 比赛信息
 * @returns 操作结果
 */
export function updateAdminContest(id: number, contest: any) {
  return request({
    url: `/admin/contest/${id}`,
    method: "PUT",
    data: contest
  })
}

/**
 * 克隆比赛（管理员）
 * @param id 比赛ID
 * @param data 新比赛标题、访问类型、时间和独立密码
 * @returns 操作结果
 */
export function cloneAdminContest(id: number, data: CloneContestRequest) {
  return request({
    url: `/admin/contest/${id}/clone`,
    method: "POST",
    data
  })
}

/**
 * 获取比赛题目列表（管理员）
 * @param contestId 比赛ID
 * @returns 比赛题目列表
 */
export function getAdminContestProblems(contestId: number) {
  return request({
    url: `/admin/contest/${contestId}/problems`,
    method: "GET"
  })
}

/**
 * 获取可添加到比赛的题目列表（管理员）
 * @param contestId 比赛ID
 * @param params 题目标题或题号、难度及分页条件
 * @returns 排除比赛已有题目的分页结果
 */
export function getAvailableProblemsForContest(contestId: number, params: ContestProblemPageQuery) {
  return request({
    url: `/admin/contest/${contestId}/available-problems`,
    method: "GET",
    params
  })
}

/**
 * 批量添加题目到比赛（管理员）
 * @param contestId 比赛ID
 * @param problemIds 原题ID或原题ID列表
 * @returns 操作结果
 */
export function addProblemsToContest(contestId: number, problemIds: number | number[]) {
  return request({
    url: `/admin/contest/${contestId}/problems/${problemIds}`,
    method: "POST"
  })
}

/**
 * 更新比赛题目的标题和气球颜色（管理员），展示ID由调整顺序功能管理
 * @param contestId 比赛ID
 * @param problemId 比赛题目关联ID
 * @param displayTitle 在比赛中显示的题目标题
 * @param color 气球颜色
 * @returns 操作结果
 */
export function updateContestProblem(
  contestId: number,
  problemId: number,
  displayTitle: string,
  color: string
) {
  const params = { displayTitle, color };
  return request({
    url: `/admin/contest/${contestId}/problems/${problemId}`,
    method: "PUT",
    params
  })
}

/**
 * 从比赛中移除题目（管理员）
 * @param contestId 比赛ID
 * @param problemIds 比赛题目关联ID或关联ID列表
 * @returns 操作结果
 */
export function removeContestProblems(contestId: number, problemIds: number | number[]) {
  return request({
    url: `/admin/contest/${contestId}/problems/${problemIds}`,
    method: "DELETE"
  })
}

/**
 * 保存比赛题目的完整顺序（管理员）
 * @param contestId 比赛ID
 * @param ids 按目标顺序排列的完整比赛题目关联ID列表
 * @returns 操作结果
 */
export function reorderContestProblems(contestId: number, ids: number[]) {
  return request({
    url: `/admin/contest/${contestId}/problem-order`,
    method: "PUT",
    data: ids
  })
}
