//错误模块
const errorRoutes = [
  //404页面
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404' }
  }
]
export default errorRoutes;