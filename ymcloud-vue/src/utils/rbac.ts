import type { AuthVO, AuthTreeNode } from '@/type/rbac';

/**
 * 构建权限树，目录、页面和操作使用同一份权限数据
 */
export function buildAuthTree(auths: AuthVO[], canGrant?: (auth: AuthVO) => boolean): AuthTreeNode[] {
  const nodes = new Map<number, AuthTreeNode>();
  auths.forEach(auth => {
    nodes.set(auth.id, {
      ...auth,
      children: [],
      disabled: canGrant ? !canGrant(auth) : false,
    });
  });
  const roots: AuthTreeNode[] = [];
  nodes.forEach(node => {
    const parent = nodes.get(node.parentId);
    if (parent) {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  });
  return roots;
}
