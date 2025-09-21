//oj模块
const ojRoutes = [
  {
    path: '/',
    name: 'OJ',
    component: () => import('@/components/oj/Layout.vue'),
    meta: { title: 'OJ' },
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/oj/home/HomeDetail.vue'),
        meta: { title: 'Home' },
      },
      // 用户主页
      {
        name: 'UserHome',
        path: 'user-home',
        component: () => import('@/views/oj/userHome/UserHome.vue'),
        meta: { title: 'User Home' }
      },
      // 用户设置
      {
        name: 'Setting',
        path: 'setting',
        component: () => import('@/views/oj/userHome/setting.vue'),
        meta: { title: 'Setting', requireLogin: true },
      },
      // 题目列表
      {
        path: 'problem',
        name: 'ProblemList',
        component: () => import('@/views/oj/problem/ProblemList.vue'),
        meta: { title: 'Problem List' }
      },
      // 题目详情
      {
        path: 'problem/:problemID',
        name: 'ProblemDetail',
        component: () => import('@/views/oj/problem/Problem.vue'),
        meta: { title: 'Problem Detail', requireLogin: true },
      },
      // 比赛列表
      {
        path: 'contest',
        name: 'ContestList',
        component: () => import('@/views/oj/contest/contestList.vue'),
        meta: { title: 'Contest List' },
      },
      // 比赛详情
      {
        path: 'contest/:contestID',
        name: 'ContestDetail',
        component: () => import('@/views/oj/contest/contestDetail.vue'),
        meta: { title: 'Contest Detail', requireLogin: true },
        children:[
          {
            path: 'problem/:displayID',
            name: 'ContestProblemDetail',
            component: () => import('@/views/oj/problem/Problem.vue'),
            meta: { title: 'Contest Problem Detail', requireLogin: true }
          },
          {
            path: 'problem',
            name: 'ContestProblemList',
            component: () => import('@/views/oj/contest/child/contestProblemList.vue'),
            meta: { title: 'Contest Problem List', requireLogin: true }
          },
          {
            path: 'submission',
            name: 'ContestSubmissionList',
            component: () => import('@/views/oj/status/StatusList.vue'),
            meta: { title: 'Contest Submission List', requireLogin: true }
          },
          {
            path: 'rank',
            name: 'ContestRank',
            component: () => import('@/views/oj/contest/child/contestRank.vue'),
            meta: { title: 'Contest Rank', requireLogin: true }
          }
        ]
      },
      // 状态列表
      {
        path: 'status',
        name: 'StatusList',
        component: () => import('@/views/oj/status/StatusList.vue'),
        meta: { title: 'Status List' }
      },
      // 提交详情
      {
        path: 'submission-detail/:submitID',
        name: 'SubmissionDetail',
        component: () => import('@/views/oj/status/StatusDetail.vue'),
        meta: { title: 'Submission Detail', requireLogin: true }
      },
      // 关于
      {
        path: 'about',
        name: 'About',
        component: () => import('@/views/oj/about/About.vue'),
        meta: { title: 'About' }
      },
    ]
  },
  //登录
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/oj/login/index.vue'),
    meta: { title: 'Login' }
  },
]
export default ojRoutes;
