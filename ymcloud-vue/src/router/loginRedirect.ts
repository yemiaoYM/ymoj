import type { RouteLocationNormalizedLoaded, RouteLocationRaw, Router } from 'vue-router';

/**
 * 解析站内登录返回地址，保留目标 query、hash 及登录页附带参数
 */
export function resolveLoginRedirect(
  router: Router,
  route: Pick<RouteLocationNormalizedLoaded, 'path' | 'query'>,
): RouteLocationRaw {
  const { redirect, ...query } = route.query;
  const homePath = route.path.startsWith('/admin/') ? '/admin' : '/';
  if (typeof redirect !== 'string' || !redirect.startsWith('/') || /^\/\/|[\\\s]/.test(redirect)) {
    return { path: homePath, query };
  }
  const target = router.resolve(redirect);
  const path = target.path === '/login' || target.path === '/admin/login' ? homePath : target.path;
  return { path, query: { ...target.query, ...query }, hash: target.hash };
}
