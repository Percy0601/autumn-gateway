export default [
  {
    path: '/user',
    layout: false,
    routes: [
      {
        name: '登录',
        path: '/user/login',
        component: './user/login',
      },
    ],
  },
  {
    name: '系统管理',
    path: '/system',
    routes: [
      { name: '应用管理', path: '/system/app', component: './System/App' },
      { name: '角色管理', path: '/system/role', component: './System/Role' },
      {
        name: '角色详情',
        path: '/system/role/detail/:id',
        component: './System/Role/Detail',
        hideInMenu: true,
      },
      { name: '权限管理', path: '/system/permission', component: './System/Permission' },
      {
        name: '权限详情',
        path: '/system/permission/detail/:id',
        component: './System/Permission/Detail',
        hideInMenu: true,
      },
      { name: '用户管理', path: '/system/user', component: './System/User' },
      {
        name: '用户详情',
        path: '/system/user/detail/:id',
        component: './System/User/Detail',
        hideInMenu: true,
      },
      { name: '资源管理', path: '/system/resource', component: './System/Resource' },
      { name: '审计日志', path: '/system/audit-log', component: './System/AuditLog' },
      // ...其他页面
    ],
  },
  {
    path: '/welcome',
    name: '欢迎',
    icon: 'smile',
    component: './Welcome',
  },
  {
    path: '/admin',
    name: '管理页',
    icon: 'crown',
    access: 'canAdmin',
    routes: [
      {
        path: '/admin',
        redirect: '/admin/sub-page',
      },
      {
        path: '/admin/sub-page',
        name: '二级管理页',
        component: './Admin',
      },
    ],
  },
  {
    name: '查询表格',
    icon: 'table',
    path: '/list',
    component: './table-list',
  },
  {
    path: '/',
    redirect: '/welcome',
  },
  {
    component: './exception/404',
    layout: false,
    path: './*',
  },
];
