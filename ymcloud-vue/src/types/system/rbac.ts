import type { PageQuery } from '@/types';

/**
 * 角色信息
 */
export type RoleVO = {
  /**
   * 主键
   */
  id: number;
  /**
   * 角色编码（如 ADMIN, USER）
   */
  code: string;
  /**
   * 角色名称（中文名，如 管理员，普通用户）
   */
  name: string;
  /**
   * 角色描述
   */
  description: string | null;
  /**
   * 角色状态：0=禁用，1=启用
   */
  status: number;
  /**
   * 是否为系统内置角色
   */
  builtIn: boolean;
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
 * 保存角色参数
 */
export type RoleDTO = {
  /**
   * 角色编码
   */
  code: string;
  /**
   * 角色名称
   */
  name: string;
  /**
   * 角色描述
   */
  description: string | null;
  /**
   * 状态：0停用，1启用
   */
  status: number;
};

/**
 * 角色查询条件
 */
export type RolePageQuery = PageQuery & {
  /**
   * 角色名称
   */
  name?: string | null;
  /**
   * 角色编码
   */
  code?: string | null;
  /**
   * 状态：0停用，1启用
   */
  status?: number | null;
};

/**
 * 菜单与操作权限
 */
export type AuthVO = {
  /**
   * 主键
   */
  id: number;
  /**
   * 权限编码（如 problem:add, user:delete）
   */
  code: string | null;
  /**
   * 权限名称（中文名，如 新增题目，删除用户）
   */
  name: string;
  /**
   * 父权限ID
   */
  parentId: number;
  /**
   * 路由路径
   */
  path: string | null;
  /**
   * 路由名称
   */
  routeName: string | null;
  /**
   * 前端组件路径（菜单权限时使用）
   */
  component: string | null;
  /**
   * 前端菜单图标
   */
  icon: string | null;
  /**
   * 权限类型：M=目录，C=页面，F=操作
   */
  type: 'M' | 'C' | 'F';
  /**
   * 是否为系统内置权限
   */
  builtIn: boolean;
  /**
   * 权限状态：0=禁用，1=启用
   */
  status: number;
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
 * 保存菜单与操作权限参数
 */
export type AuthDTO = {
  /**
   * 权限编码
   */
  code: string | null;
  /**
   * 权限名称
   */
  name: string;
  /**
   * 上级权限ID，0表示顶层
   */
  parentId: number;
  /**
   * 路由路径
   */
  path: string | null;
  /**
   * 路由名称
   */
  routeName: string | null;
  /**
   * 页面组件路径
   */
  component: string | null;
  /**
   * 菜单图标
   */
  icon: string | null;
  /**
   * 权限类型：M目录，C页面，F操作
   */
  type: 'M' | 'C' | 'F';
  /**
   * 状态：0停用，1启用
   */
  status: number;
};

/**
 * 后台菜单路由，空字段由后端 NON_EMPTY 序列化规则省略
 */
export type MenuRoute = {
  name?: string;
  path: string;
  component?: string;
  meta: {
    title: string;
    icon: string | null;
    requireLogin: boolean;
  };
  children?: MenuRoute[];
};

/**
 * 页面构建的权限树节点，children 与 disabled 不属于接口字段
 */
export type AuthTreeNode = AuthVO & { children: AuthTreeNode[]; disabled?: boolean };
