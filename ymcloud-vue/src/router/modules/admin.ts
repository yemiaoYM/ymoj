import Layout from '@/components/admin/AdminLayout.vue';

//admin模块
const adminRoutes = [
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('@/views/admin/AdminLogin.vue'),
    meta: { title: 'Admin Login' },
  },
  {
    path: '/admin',
    name: 'AdminContainer',
    redirect: '/admin/index',
    component: Layout,
    children: [
      //首页
      {
        path: 'index',
        name: 'Dashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: 'Admin Home', requireLogin: true },
      }
    ]
  },
  //缓冲加载页面
  {
    path: '/admin/:pathMatch(.*)*',
    component: Layout,
    meta: { hidden: true },
  }
]

//动态路由，基于用户权限动态去加载
export const dynamicRoutes = [
  //编辑题目路由
  {
    path: '/admin/problem',
    component: Layout,
    permissions: ['problem:problem:edit'],
    children: [
      {
        path: 'edit/:id(\\d+)',
        name: 'AdminProblemEdit',
        component: () => import('@/views/admin/problem/Problem.vue'),
        meta: { title: 'Admin Problem Edit', requireLogin: true },
      }
    ]
  },
]


export default adminRoutes;