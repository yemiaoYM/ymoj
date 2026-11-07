/**
 * 后台返回的菜单路由
 */
export type MenuRoute = {
  name?: string;
  path: string;
  component?: string;
  meta: {
    title: string;
    icon?: string;
    requireLogin: boolean;
  };
  children?: MenuRoute[];
};
