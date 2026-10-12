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
        path: '/system/role',
        name: 'system-role',
        component: () => import('../views/system/role/index.vue'),
        meta: { title: '角色管理' },
      },
      {
        path: '/system/menu',
        name: 'system-menu',
        component: () => import('../views/system/menu/index.vue'),
        meta: { title: '菜单管理' },
      },
      {
        path: '/system/permission',
        name: 'system-permission',
        component: () => import('../views/system/permission/index.vue'),
        meta: { title: '权限管理' },
      },
      {
        path: '/system/resource',
        name: 'system-resource',
        component: () => import('../views/system/resource/index.vue'),
        meta: { title: '资源管理' },
      },
      {
        path: '/system/group',
        name: 'system-group',
        component: () => import('../views/system/group/index.vue'),
        meta: { title: '用户组管理' },
      },
      {
        path: '/system/group-role',
        name: 'system-group-role',
        component: () => import('../views/system/group-role/index.vue'),
        meta: { title: '用户组-角色' },
      },
      {
        path: '/system/user-group',
        name: 'system-user-group',
        component: () => import('../views/system/user-group/index.vue'),
        meta: { title: '用户-用户组' },
      },
      {
        path: '/system/user-role',
        name: 'system-user-role',
        component: () => import('../views/system/user-role/index.vue'),
        meta: { title: '用户-角色' },
      },
      {
        path: '/system/group-group',
        name: 'system-group-group',
        component: () => import('../views/system/group-group/index.vue'),
        meta: { title: '用户组-用户组' },
      },
      {
        path: '/system/role-menu',
        name: 'system-role-menu',
        component: () => import('../views/system/role-menu/index.vue'),
        meta: { title: '角色-菜单' },
      },
      {
        path: '/system/role-resource',
        name: 'system-role-resource',
        component: () => import('../views/system/role-resource/index.vue'),
        meta: { title: '角色-资源' },
      },
      {
        path: '/system/oper-log',
        name: 'system-oper-log',
        component: () => import('../views/system/operLog/index.vue'),
        meta: { title: '日志管理' },
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
