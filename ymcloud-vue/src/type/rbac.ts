/**
 * 角色与菜单权限类型
 */
export type RoleVO = {
  id: number;
  code: string;
  name: string;
  description: string | null;
  status: number;
  builtIn: boolean;
  createdTime?: string;
};

export type RolePageQuery = {
  name: string;
  code: string;
  status: number | null;
  pageNo: number;
  pageSize: number;
};

export type RoleDTO = Pick<RoleVO, 'code' | 'name' | 'description' | 'status'>;

export type AuthVO = {
  id: number;
  code: string | null;
  name: string;
  parentId: number;
  path: string | null;
  routeName: string | null;
  component: string | null;
  icon: string | null;
  type: 'M' | 'C' | 'F';
  status: number;
  builtIn: boolean;
};

export type AuthDTO = Omit<AuthVO, 'id' | 'builtIn'>;
export type AuthTreeNode = AuthVO & { children: AuthTreeNode[]; disabled?: boolean };
