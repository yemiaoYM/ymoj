/**
 * 公开与后台系统配置
 */
export type SystemSettingVO = {
  /**
   * 是否允许新用户注册
   */
  registerEnabled: boolean;
  /**
   * 是否显示首页备案栏
   * 尚未配置时为 false
   */
  filingEnabled: boolean;
  /**
   * ICP 备案号
   * 未配置时为空串
   */
  icpFilingNumber: string;
  /**
   * 公安备案号
   * 未配置时为空串
   */
  policeFilingNumber: string;
};

/**
 * 在线会话
 */
export type UserOnlineVO = {
  /**
   * token
   */
  token: string;
  /**
   * 用户名
   */
  username: string;
  /**
   * IP地址
   */
  ipaddr: string;
  /**
   * 登录地点
   */
  loginLocation: string;
  /**
   * 浏览器
   */
  browser: string;
  /**
   * 操作系统
   */
  os: string;
  /**
   * 登录时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  loginDateTime: string;
};

/**
 * 后台首页统计
 */
export type DashboardStatsVO = {
  /**
   * 总用户数
   */
  totalUsers: number;
  /**
   * 题目总数
   */
  totalProblems: number;
  /**
   * 比赛总数
   */
  totalContests: number;
};

/**
 * 近期提交趋势
 */
export type SubmissionChartVO = {
  /**
   * 日期列表（格式：MM-dd）
   */
  dates: string[];
  /**
   * 总提交数列表
   */
  totalSubmissions: number[];
  /**
   * 通过数列表
   */
  acceptedSubmissions: number[];
};

/**
 * 保存系统配置使用相同字段
 */
export type SystemSettingDTO = SystemSettingVO;

/**
 * CPU 使用情况
 */
export type Cpu = {
  cpuNum: number;
  total: number;
  sys: number;
  used: number;
  wait: number;
  free: number;
};

/**
 * 物理内存使用情况
 */
export type Mem = {
  total: number;
  used: number;
  free: number;
  usage: number;
};

/**
 * JVM 运行情况
 */
export type Jvm = {
  total: number;
  max: number;
  free: number;
  version: string;
  home: string;
  used: number;
  usage: number;
  name: string;
  startTime: string;
  runTime: string;
  inputArgs: string;
};

/**
 * 操作系统信息
 */
export type Sys = {
  computerName: string;
  computerIp: string;
  userDir: string;
  osName: string;
  osArch: string;
};

/**
 * 磁盘使用情况
 */
export type SysFile = {
  dirName: string;
  sysTypeName: string;
  typeName: string;
  total: string;
  free: string;
  used: string;
  usage: number;
};

/**
 * 服务器监控信息
 */
export type Server = {
  cpu: Cpu;
  mem: Mem;
  jvm: Jvm;
  sys: Sys;
  sysFiles: SysFile[];
};
