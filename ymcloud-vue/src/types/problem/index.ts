import type { PageQuery } from '@/types';
import type { ProblemJudgeMode } from '@/constants/problem';
import type { Tag } from '@/types/problem/tag';

/**
 * 公开题目详情
 */
export type ProblemVO = {
  /**
   * 比赛正式提交开始时间
   * 普通练习为 null
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  submissionStartTime: string | null;
  /**
   * 比赛正式提交结束时间，不含结束时刻
   * 普通练习为 null
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  submissionEndTime: string | null;
  /**
   * 服务端当前时间，用于比赛页面校准时段
   * 普通练习为 null
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  serverTime: string | null;
  /**
   * 题目展示id 例如YMOJ-1000（本题库），P1005（洛谷）等等
   */
  problemId: string;
  /**
   * 题目标题
   */
  title: string;
  /**
   * 题目描述内容（题干）
   */
  content: string;
  /**
   * 输入描述
   */
  input: string;
  /**
   * 输出描述
   */
  output: string;
  /**
   * 题目来源说明，最多255字符，可为空
   * 不参与判题和题号生成
   */
  source: string | null;
  /**
   * 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师
   */
  difficulty: number;
  /**
   * 判题方式：default=普通、spj=特殊、interactive=交互
   * 当前支持 default 和基于 testlib 的 C++17 SPJ，interactive 暂不开放
   */
  judgeMode: ProblemJudgeMode;
  /**
   * 时间限制（ms）
   */
  timeLimit: number;
  /**
   * 内存限制（KiB）
   */
  memoryLimit: number;
  /**
   * 出题人用户名
   */
  authorUsername: string;
  /**
   * 题面样例
   */
  problemCaseList: ProblemCaseVO[] | null;
  /**
   * 编程语言选项
   */
  languageList: LanguageVO[];
  /**
   * 用户该题目的通过状态
   */
  accepted: boolean;
};

/**
 * 公开题目列表项
 */
export type ProblemTableVO = {
  /**
   * 题目唯一标识
   */
  problemId: string;
  /**
   * 题目标题
   */
  title: string;
  /**
   * 题目来源说明，最多255字符，可为空
   * 不参与判题和题号生成
   */
  source: string | null;
  /**
   * 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师
   */
  difficulty: number;
  /**
   * 标签
   */
  tagList: Tag[] | null;
  /**
   * 通过提交数
   */
  acceptCount: number;
  /**
   * 总提交数
   */
  submitCount: number;
};

/**
 * 题面样例
 */
export type ProblemCaseVO = {
  /**
   * 输入内容
   */
  input: string;
  /**
   * 输出内容
   */
  output: string;
  /**
   * 样例解释或者提示
   */
  note: string;
};

/**
 * 后台题目列表项，不包含 SPJ 源码
 */
export type AdminProblemVO = {
  /**
   * 题目ID
   */
  id: number;
  /**
   * 问题的自定义ID，可作为唯一标识，例如YMOJ-1000（本题库），P1005（洛谷）等等
   */
  problemId: string;
  /**
   * 题目标题
   */
  title: string;
  /**
   * 题目描述内容（题干）
   */
  content: string;
  /**
   * 时间限制（ms）
   */
  timeLimit: number;
  /**
   * 内存限制（KiB）
   */
  memoryLimit: number;
  /**
   * 输入描述
   */
  input: string;
  /**
   * 输出描述
   */
  output: string;
  /**
   * 题面样例
   */
  examples: string | null;
  /**
   * 题目来源说明，最多255字符，可为空
   * 不参与判题和题号生成
   */
  source: string | null;
  /**
   * 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师
   */
  difficulty: number;
  /**
   * 判题方式：default=普通、spj=特殊，交互判题暂不开放
   */
  judgeMode: ProblemJudgeMode;
  /**
   * 可见性：1=公开，2=私有，3=比赛专用
   */
  visibility: number;
  /**
   * 是否允许查看测试样例结果
   */
  openCaseResult: boolean;
  /**
   * 出题人ID（user.id）
   */
  authorId: number;
  /**
   * 出题人用户名
   */
  authorUsername: string;
  /**
   * 创建时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  createdTime: string;
  /**
   * 更新时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  updatedTime: string;
};

/**
 * 后台题目详情
 */
export type AdminProblemDetailVO = {
  /**
   * 题目ID
   */
  id: number;
  /**
   * 问题的自定义ID，可作为唯一标识，例如YMOJ-1000（本题库），P1005（洛谷）等等
   */
  problemId: string;
  /**
   * 题目标题
   */
  title: string;
  /**
   * 题目描述内容（题干）
   */
  content: string;
  /**
   * 时间限制（ms）
   */
  timeLimit: number;
  /**
   * 内存限制（KiB）
   */
  memoryLimit: number;
  /**
   * 输入描述
   */
  input: string;
  /**
   * 输出描述
   */
  output: string;
  /**
   * 题目来源说明，最多255字符，可为空
   * 不参与判题和题号生成
   */
  source: string | null;
  /**
   * 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师
   */
  difficulty: number;
  /**
   * 判题方式：default=普通、spj=特殊，交互判题暂不开放
   */
  judgeMode: ProblemJudgeMode;
  /**
   * testlib 校验器语言
   */
  spjLanguage: string | null;
  /**
   * 仅管理详情可见的校验器源码
   */
  spjCode: string | null;
  /**
   * 可见性：1=公开，2=私有，3=比赛专用
   */
  visibility: number;
  /**
   * 是否允许查看测试样例结果
   */
  openCaseResult: boolean;
  /**
   * 题面样例
   */
  problemCaseList: ProblemCaseVO[] | null;
  /**
   * 标签列表
   */
  tagList: number[];
};

/**
 * 保存题目参数，不包含数据库 ID
 */
export type ProblemDTO = {
  /**
   * 新增时绑定的已上传正文图片 ID
   */
  fileIds?: string[];
  /**
   * 新增时自定义展示 ID：1～15 位英文字母、数字或下划线
   * 编辑不修改原 ID
   */
  problemId: string;
  /**
   * 题目标题
   */
  title: string;
  /**
   * 题目描述内容（题干）
   */
  content: string;
  /**
   * 输入描述
   */
  input: string;
  /**
   * 输出描述
   */
  output: string;
  /**
   * 题目来源说明，最多255字符，可为空
   * 不决定判题方式或展示 ID
   */
  source: string | null;
  /**
   * 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师
   */
  difficulty: number;
  /**
   * 判题方式：default=普通、spj=特殊，交互判题暂不开放
   */
  judgeMode: ProblemJudgeMode;
  spjLanguage: string | null;
  spjCode: string | null;
  /**
   * 题目可见性
   */
  visibility: number;
  /**
   * 是否允许查看测试样例结果
   */
  openCaseResult: boolean;
  /**
   * 时间限制（ms）
   */
  timeLimit: number;
  /**
   * 内存限制（MiB）
   */
  memoryLimit: number;
  /**
   * 题面样例
   */
  problemCaseList: ProblemCaseVO[];
  /**
   * 标签列表
   */
  tagList: number[];
};

/**
 * 远程题目抓取参数
 */
export type RemoteProblemRequest = {
  platform: string;
  problemId: string;
};

/**
 * 远程题目草稿
 */
export type RemoteProblemDraft = {
  /**
   * 远程题面导入时已保存的图片 ID
   */
  fileIds: string[];
  /**
   * 建议的展示题号，可在新增页修改
   */
  problemId: string;
  /**
   * 原题标题，保留原文
   */
  title: string;
  /**
   * Markdown 题干，包含背景和提示
   */
  content: string;
  /**
   * Markdown 输入说明
   */
  input: string;
  /**
   * Markdown 输出说明
   */
  output: string;
  /**
   * 包含平台、题号及原题链接的出处说明
   */
  source: string;
  /**
   * 原题时间限制，单位毫秒
   * 平台未公开时为 null
   */
  timeLimit: number | null;
  /**
   * 原题内存限制，单位 MiB
   * 平台未公开时为 null
   */
  memoryLimit: number | null;
  /**
   * 平台与本站题目模式的差异说明，无额外说明时为 null
   */
  notice: string | null;
  /**
   * 最多前三组公开样例
   */
  problemCaseList: ProblemCaseVO[];
};

/**
 * 测试数据文件清单
 */
export type ProblemCaseFileVO = {
  fileId: string;
  size: number;
  sha256: string;
};

export type ProblemCaseInfoVO = {
  name: string;
  input: ProblemCaseFileVO;
  output: ProblemCaseFileVO;
};

export type ProblemCaseListVO = {
  version: number | null;
  cases: ProblemCaseInfoVO[];
};

/**
 * 题目支持的编程语言
 */
export type LanguageVO = {
  /**
   * 语言名称，如 C++ 17
   */
  name: string;
  /**
   * 语言版本信息，如 g++ 9.4 / Python 3.10
   */
  version: string;
  /**
   * MIME类型，用于前端代码高亮，如 text/x-c++src
   */
  contentType: string;
  /**
   * 语言默认代码模板，显示在编辑器中
   */
  templateCode: string;
  /**
   * 题面中用于填空题的嵌套模板
   */
  codeTemplate: string;
};

/**
 * 编译器与示例代码
 */
export type LanguageTableVO = {
  /**
   * 语言名称，如 C++ 17
   */
  name: string;
  /**
   * 语言版本信息，如 g++ 9.4 / Python 3.10
   */
  version: string;
  /**
   * MIME类型，用于前端代码高亮，如 text/x-c++src
   */
  contentType: string;
  /**
   * 编译指令，编译型语言需要，如 g++ main.cpp -o main
   */
  compileCmd: string;
  /**
   * 语言默认代码模板，显示在编辑器中
   */
  templateCode: string;
};

/**
 * 公开题目查询条件
 */
export type ProblemListQuery = PageQuery & {
  /**
   * 题目/题号 支持模糊查询
   */
  keyword?: string | null;
  /**
   * 难度
   */
  difficulty?: number | null;
  /**
   * 必须同时包含的标签 ID，省略或空集合表示不筛选，重复 ID 自动合并
   */
  tagIds: number[];
};

/**
 * 后台题目查询条件
 */
export type AdminProblemPageQuery = PageQuery & {
  /**
   * 题目/题号 支持模糊查询
   */
  keyword?: string | null;
  /**
   * 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师
   * null 不筛选
   */
  difficulty?: number | null;
  /**
   * 可见性：1=公开，2=私有，3=比赛专用
   */
  visibility?: number | null;
};

/**
 * SPJ 编译检查参数
 */
export type SpjCompileDTO = {
  language: string;
  code: string;
};

/**
 * 题目页本地草稿，不属于后端响应
 */
export type EditorCache = {
  code: string;
  language: string;
};

/**
 * SPJ 编译结果，不包含沙箱文件 ID
 */
export type SpjCompileResult = {
  requestId: string;
  digest: string | null;
  success: boolean;
  message: string;
};
