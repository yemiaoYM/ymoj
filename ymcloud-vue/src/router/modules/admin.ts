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
  //编辑题目路由
  {
    path: '/admin/problem',
    component: Layout,
    children: [
      {
        path: 'edit/:id',
        name: 'AdminProblemEdit',
        component: () => import('@/views/admin/problem/Problem.vue'),
        meta: { title: 'Admin Problem Edit', requireLogin: true },
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

export default adminRoutes;