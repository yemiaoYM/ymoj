/**
 * types 管理项目类型字段
 */
import type { ProblemJudgeMode } from "@/constants/problem";

/**
 * 系统配置
 */
export type SystemSettingVO = {
  /** 是否允许新用户注册 */
  registerEnabled: boolean;
  /** 首页备案栏开关；关闭时保留备案号 */
  filingEnabled: boolean;
  /** ICP 备案号；未配置时为空串 */
  icpFilingNumber: string;
  /** 公安备案号；含一段数字编号，未配置时为空串 */
  policeFilingNumber: string;
};

export type SystemSettingDTO = SystemSettingVO;

export type SystemSettingResult<T> = {
  code: number;
  msg: string;
  data: T;
};

/**
 * 用户实体
 */
export type User = {
  /** 用户ID */
  id: number;
  /** 登录用户名（唯一） */
  username: string;
  /** 密码 */
  password: string | null;
  /** 昵称 */
  nickname: string | null;
  /** 性别：0=保密，1=男，2=女 */
  sex: string;
  /** 邮箱 */
  email: string;
  /** 头像 URL */
  avatar: string | null;
  /** 学校 */
  school: string | null;
  /** 个性签名 */
  signature: string | null;
  /** 博客链接 */
  blog: string | null;
  /** 最后登录 IP */
  loginIp: string | null;
  /** 最后登录时间 */
  loginDate: string | null;
  /** 账号状态：1=正常，0=停用 */
  status: string;
  /** 创建时间 */
  createdTime: string;
  /** 更新时间 */
  updatedTime: string;
};
/**
 * 用户DTO
 */
export type UserDTO = {
  /** 用户ID */
  id: number | null;
  /** 登录用户名（唯一） */
  username: string;
  /** 密码 */
  password: string | null;
  /** 昵称 */
  nickname: string | null;
  /** 性别：0=保密，1=男，2=女 */
  sex: string;
  /** 邮箱 */
  email: string;
  /** 头像 URL */
  avatar: string | null;
  /** 学校 */
  school: string | null;
  /** 个性签名 */
  signature: string | null;
  /** 博客链接 */
  blog: string | null;
  /** 账号状态：1=正常，0=停用 */
  status: string;
};
/**
 * 用户基本信息
 */
export type UserProfile = {
  /** 用户ID */
  id: number;
  username: string;
  nickname: string;
  sex: string;
  email: string;
  phoneNumber: string;
  avatar: string;
  school: string;
  signature: string;
  blog: string;
  loginDateTime: string;
  ipaddr: string;
  loginLocation: string;
  browser: string;
  os: string;
  roles:string[];
  permissions:string[];
}
/**
 * 提交表单
 */
export type SubmissionTableVO = {
  /**
   * 当前用户是否可重评该提交
   */
  canRejudge: boolean;
  /** 当前执行标识，历史提交为 null */
  executionId: string | null;
  /** 提交 ID，来自 submission 表 */
  submissionId: number;
  /** 题目标题 */
  problemTitle: string;
  /** 题目展示 ID，例如 ABC100-A */
  displayId: string;
  /** 用户名 */
  username: string;
  /** 编程语言名称 */
  language: string;
  /** 代码长度（单位：字符） */
  codeLength: number;
  /** 判题状态*/
  status: string;
  /** 得分，ACM 模式为 null，OI 模式使用 */
  score: number | null;
  /** 执行耗时（毫秒） */
  time: number | null;
  /** 内存使用（KB） */
  memory: number | null;
  /** 判题机器名 */
  judgeServer: string;
  /** 提交时间（ISO 字符串或 Date 对象） */
  createdTime: string; // or `Date` if你会转换处理
}
/**
 * 用户信息
 */
export type UserInfoVO = {
  /** 用户名 */
  username: string;
  /** 昵称 */
  nickname: string;
  /** 性别 */
  sex: string;
  /** 电子邮箱 */
  email: string;
  /** 头像 */
  avatar: string|null;
  /** 学校 */
  school: string;
  /** 个性签名 */
  signature: string;
  /** 最近上线时间 */
  loginDate: string;
  /** 博客 */
  blog: string;
  /** 已解决题目的题号 */
  problemId: string[];
  /** 已解决题目个数 */
  ac: number;
  /** 总提交个数 */
  total: number;
}
/**
 * 登录DTO
 */
export type LoginDTO = {
    username: string,
    password: string,
    uuid: string,
    code: string
}
/**
 * 注册DTO
 */
export type RegisterDTO = {
    username: string,
    password: string,
    rePassword: string,
    email: string,
    code: string,
    uuid: string
}
/**
 * 单个评测样例结果
 */
export type JudgeCaseVO = {
  /** 样例在评测中的顺序编号（从0或1开始） */
  caseIndex: number;
  /** 评测结果 */
  status: string;
  /** 运行耗时(ms) */
  time: number | null;
  /** 运行内存(KB) */
  memory: number | null;
  /** 该样例得分（OI模式下有效） */
  score: number | null;
};
/**
 * 提交记录信息
 */
export type SubmissionVO = {
  /** 提交ID */
  submissionId: number;
  /** 题目标题 */
  problemTitle: string;
  /** 题目展示ID，例如 ABC100-A */
  displayId: string;
  /** 用户名 */
  username: string;
  /** 编程语言名称 */
  language: string;
  /** 用户提交代码（个人或者允许分享查看） */
  code: string;
  /** 代码长度，单位：字符 */
  codeLength: number;
  /** 评测状态 */
  status: string;
  /** 得分，ACM 模式为 null，OI 模式使用 */
  score: number | null;
  /** 执行耗时(ms) */
  time: number | null;
  /** 内存使用(KB) */
  memory: number | null;
  /** 编译错误/运行时错误信息 */
  errorMessage: string | null;
  /** 是否公开代码 */
  share: boolean;
  /** 执行判题的机器标识 */
  judgeServer: string;
  /** 提交时间 */
  createdTime: string; // ISO 格式字符串，例如 "2025-07-31T08:00:00"
  /** 各个评测点结果 */
  judgeCases: JudgeCaseVO[];
};
/**
 * 题目详情视图对象
 */
export type ProblemVO = {
  /**
   * 普通练习无时段限制
   * 比赛由服务端返回开始、结束及校准时间
   */
  submissionStartTime: string | null;
  submissionEndTime: string | null;
  serverTime: string | null;
  /** 展示 ID，例如 YMOJ-1000、P1005 等 */
  problemId: string
  /** 题目标题 */
  title: string
  /** 题目内容（题干） */
  content: string
  /** 输入描述 */
  input: string
  /** 输出描述 */
  output: string
  /** 题目来源，例如 洛谷、ICPC 区域赛 */
  source: string | null
  /** 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师 */
  difficulty: number
  /**
   * 判题方式，当前开放 default 和 spj
   */
  judgeMode: ProblemJudgeMode
  /** 时间限制（单位：ms） */
  timeLimit: number
  /** 内存限制（单位：KB） */
  memoryLimit: number
  /** 出题人用户名 */
  authorUsername: string
  /** 样例列表 */
  problemCaseList: ProblemCaseVO[]
  /** 支持的语言列表 */
  languageList: LanguageVO[]
  /** 是否通过 */
  accepted:boolean
}
/**
 * 题目样例视图对象
 */
export type ProblemCaseVO = {
  /** 输入内容 */
  input: string
  /** 输出内容 */
  output: string
  /** 样例解释或提示（仅示例用例提供） */
  note: string
}
/**
 * 编程语言视图对象
 */
export type LanguageVO = {
  /** 语言名称，如 C++ 17 */
  name: string
  /** 语言版本信息，如 g++ 9.4 / Python 3.10 */
  version: string
  /** MIME 类型（用于代码高亮），如 text/x-c++src */
  contentType: string
  /** 默认代码模板（在编辑器中显示） */
  templateCode: string
  /** 嵌套代码模板（用于填空题） */
  codeTemplate: string
}
/**
 * 标签
 */
export type Tag = {
  /** 标签ID */
  id: number;
  /** 标签名称 */
  name: string;
  /** 父标签ID */
  parentId: number|null;
  /** 标签颜色（如 #ff6600） */
  color: string;
  /** 创建时间（ISO 字符串） */
  createdTime: string;
  /** 更新时间（ISO 字符串） */
  updatedTime: string;
}
/**
 * 题目表单
 */
export type ProblemTableVO = {
  /** 表单展示序号：key+id */
  problemId: string;
  /** 题目描述内容（题干） */
  title: string;
  /** 题目来源 */
  source: string | null;
  /** 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师 */
  difficulty: number;
  /** 标签 */
  tagList: Tag[]|null;
  /** 通过提交数 */
  acceptCount: number;
  /** 总提交数 */
  submitCount: number;
}

/**
 * 管理员题目列表VO
 */
export type AdminProblemVO = {
  /** 题目ID */
  id: number;
  /** 展示ID，例如 GYM-103388K */
  problemId: string;
  /** 题目标题 */
  title: string;
  /** 题目描述内容（题干） */
  content?: string;
  /** 时间限制（ms） */
  timeLimit?: number;
  /** 内存限制（KB） */
  memoryLimit?: number;
  /** 输入描述 */
  input?: string;
  /** 输出描述 */
  output?: string;
  /** 题面样例 */
  examples?: string;
  /** 题目来源 */
  source?: string | null;
  /** 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师 */
  difficulty: number;
  /**
   * 判题方式，当前开放 default 和 spj
   */
  judgeMode?: ProblemJudgeMode;
  /**
   * testlib 校验器，仅管理详情返回
   */
  spjLanguage: string | null;
  spjCode: string | null;
  /** 权限：1=公开，2=私有，3=比赛专用 */
  visibility: number;
  /** 是否允许查看测试样例结果（0=否，1=是） */
  openCaseResult?: boolean;
  /** 出题人ID（user.id） */
  authorId?: number;
  /** 出题人用户名 */
  authorUsername: string;
  /** 创建时间 */
  createdTime: string;
  /** 更新时间 */
  updatedTime?: string;
}

/**
 * 公告实体
 */
export type Announcement = {
  /** 公告ID */
  id: number;
  /** 公告标题 */
  title: string;
  /** 公告内容（支持HTML/Markdown） */
  content: string;
  /** 公告权重（值越大越靠前） */
  priority: number;
  /** 是否置顶：true=置顶，false=不置顶 */
  pinned: boolean;
  /** 是否可见：true=可见，false=不可见 */
  status: boolean;
  /** 发布人ID */
  authorId: number;
  /** 发布人用户名 */
  authorUsername: string;
  /** 创建时间 */
  createdTime: string;
  /** 更新时间 */
  updatedTime: string;
}

/**
 * 公告DTO
 */
export type AnnouncementDTO = {
  /** 公告标题 */
  title: string;
  /** 公告内容（支持HTML/Markdown） */
  content: string;
  /** 公告权重（值越大越靠前） */
  priority: number;
  /** 是否置顶：true=置顶，false=不置顶 */
  pinned: boolean;
  /** 是否可见：true=可见，false=不可见 */
  status: boolean;
}

/**
 * 题目表单DTO
 */
export type ProblemDTO = {
  /**
   * SPJ 语言与源码，普通判题时由后端清空
   */
  spjLanguage: string;
  spjCode: string;
  /** 题目ID */
  id: number | null;
  /** 问题的自定义ID */
  problemId: string;
  /** 题目标题 */
  title: string;
  /** 题目描述内容（题干） */
  content: string;
  /** 时间限制（ms） */
  timeLimit: number;
  /** 内存限制（MB） */
  memoryLimit: number;
  /** 输入描述 */
  input: string;
  /** 输出描述 */
  output: string;
  /** 题面样例 */
  examples: string;
  /** 题目来源 */
  source: string | null;
  /** 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师 */
  difficulty: number;
  /**
   * 判题方式，当前开放 default 和 spj
   */
  judgeMode: ProblemJudgeMode;
  /** 可见性：1=公开，2=私有，3=比赛专用 */
  visibility: number;
  /** 是否允许查看测试样例结果 */
  openCaseResult: boolean;
  /** 题面测试用例 */
  problemCaseList: ProblemCaseVO[];
}
/**
 * 用户AC排名
 */
export type UserAcRankVO={
  /** 用户名 */
  username:string,
  /** 用户头像 */
  avatar:string|null,
  /** 用户昵称 */
  nickname:string|null,
  /** 通过题目数 */
  count:number
}
/**
 * 编辑器缓存
 */
export type EditorCache={
  code:string,
  language:string,
  theme:string
}
/**
 * 评测结果
 */
export type JudgeResult={
  status:string,
  time:number | null,
  memory:number | null,
  input:string | null,
  output:string | null,
  expectedOutput:string | null,
  errorMessage:string | null
}
/**
 * 比赛VO
 */
export type ContestVO={
    creatorUsername: string;
    title: string;
    description: string;
    sourceId: number;
    rated: number;
    mode: number;
    style: number;
    startTime: string;
    endTime: string;
    now: string;
    status: string;
    duration: number;
    visibility: number;
    canManage: boolean;
    canClone: boolean;
    count: number;
    access: boolean;
}
/**
 * 比赛列表VO
 */
export type ContestTableVO = {
  /** 比赛id */
  id: number;
  /** 比赛创建者用户名或主办方 */
  creatorUsername: string;
  /** 比赛Logo图片路径 */
  image: string|null;
  /** 比赛标题 */
  title: string;
  /** 比赛状态 */
  status: string;
  /** 来源比赛ID（0表示原创，其它为克隆赛ID） */
  sourceId: number;
  /** 是否计入Rating积分（0=否，1=是） */
  rated: number;
  /** 赛制模式：0=ACM，1=OI，2=IOI */
  mode: number;
  /** 比赛类型：0=其他，1=周赛，2=月赛，3=娱乐赛，4=挑战赛 */
  style: number;
  /**
   * 访问权限：0=公开，1=密码，2=私有（仅创建者及管理员）
   */
  visibility: number;
  /** 比赛开始时间 */
  startTime: string; // LocalDateTime 用 ISO 字符串
  /** 比赛结束时间 */
  endTime: string;
  /** 比赛时长（单位：分钟） */
  duration: number;
  /** 参与人数 */
  participantCount: number;
}
/**
 * 比赛题目列表VO
 */
export type ContestProblemTableVO={
  displayId:string;
  displayTitle:string;
  color:string|null;
  ac:number;
  total:number;
  accepted:boolean;
}
/**
 * 比赛题目VO
 */
export type ContestProblemVO={
  displayId: string;
  displayTitle: string;
  color: string|null;
  ac: number;
  total: number;
}
/**
 * 比赛用户提交VO
 */
export type ContestSubmissionVO={
  score: number;
  errorNum: number;
  actime: number;
  isAC: boolean;
  isFirstAC: boolean;
}
/**
 * 比赛用户排名VO
 */
export type ContestRankVO={
  rank: number;
  username: string;
  nickname: string;
  school: string|null;
  ac: number;
  total: number;
  totalScore: number;
  totalTime: number;
  submissionInfo: Record<string, ContestSubmissionVO>;
}
/**
 * 比赛排名VO
 */
export type ContestRankTableVO={
  mode: number;
  problemInfoData: ContestProblemVO[];
  userRankData: ContestRankVO[];
  total: number;
}
/**
 * 编程语言表VO
 */
export type LanguageTableVO={
  name:string;
  version:string;
  contentType:string;
  compileCmd:string;
  templateCode:string;
}
/**
 * 在线用户VO
 */
export type UserOnlineVO={
  token: string;
  username: string;
  ipaddr: string;
  loginLocation: string;
  browser: string;
  os: string;
  loginDateTime: string;
}

/**
 * 测试用例信息VO
 */
export type ProblemCaseInfoVO = {
  /** 样例编号 */
  id: number;
  /** 输入文件名 */
  input: string;
  /** 输出文件名 */
  output: string;
}
