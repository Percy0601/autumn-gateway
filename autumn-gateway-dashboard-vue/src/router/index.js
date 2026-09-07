import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/login/index.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: () => import('../layouts/BasicLayout.vue'),
    redirect: '/system/app',
    children: [
      {
        path: '/system/app',
        name: 'system-app',
        component: () => import('../views/system/app/index.vue'),
        meta: { title: '应用管理' },
      },
      {
        path: '/system/user',
        name: 'system-user',
        component: () => import('../views/system/user/index.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: '/system/user/detail/:id',
        name: 'system-user-detail',
        component: () => import('../views/system/user/detail.vue'),
        meta: { title: '用户详情', activeMenu: '/system/user', hidden: true },
      },
      {
        path: '/system/role',
        name: 'system-role',
        component: () => import('../views/system/role/index.vue'),
        meta: { title: '角色管理' },
      },
      {
        path: '/system/role/detail/:id',
        name: 'system-role-detail',
        component: () => import('../views/system/role/detail.vue'),
        meta: { title: '角色详情', activeMenu: '/system/role', hidden: true },
      },
      {
        path: '/system/permission',
        name: 'system-permission',
        component: () => import('../views/system/permission/index.vue'),
        meta: { title: '权限管理' },
      },
      {
        path: '/system/permission/detail/:id',
        name: 'system-permission-detail',
        component: () =>
          import('../views/system/permission/detail.vue'),
        meta: {
          title: '权限详情',
          activeMenu: '/system/permission',
          hidden: true,
        },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/system/app',
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

// 登录守卫
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.meta.public) {
    // 已登录访问登录页则直接进入首页
    if (token && to.path === '/login') return '/system/app'
    return true
  }
  if (!token) {
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }
  return true
})

export default router
