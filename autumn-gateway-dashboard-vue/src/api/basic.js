// 基于 basic_* 表的新版后台 API 客户端
// 约定：所有接口前缀 /api/system/basic，与现有 /api/system/* 风格一致。
// 后端需提供对应 controller（basic_app / basic_role / basic_user / basic_menu /
// basic_resource / basic_oper_log / basic_group / basic_role_group / basic_user_group）。
import http from '../utils/request'

const base = '/api/system/basic'

// ---------------- 应用 basic_app ----------------
export const basicAppApi = {
  list: (params) => http.get(`${base}/app`, { params }),
  listAll: () => http.get(`${base}/app/list`),
  get: (id) => http.get(`${base}/app/${id}`),
  create: (data) => http.post(`${base}/app`, data),
  update: (id, data) => http.put(`${base}/app/${id}`, data),
  enable: (id) => http.put(`${base}/app/${id}/enable`),
  disable: (id) => http.put(`${base}/app/${id}/disable`),
}

// ---------------- 角色 basic_role ----------------
export const basicRoleApi = {
  list: (params) => http.get(`${base}/role`, { params }),
  listAll: () => http.get(`${base}/role/list`),
  get: (id) => http.get(`${base}/role/${id}`),
  create: (data) => http.post(`${base}/role`, data),
  update: (id, data) => http.put(`${base}/role/${id}`, data),
  enable: (id) => http.put(`${base}/role/${id}/enable`),
  disable: (id) => http.put(`${base}/role/${id}/disable`),
  // 关联用户 basic_user_role
  users: (id) => http.get(`${base}/role/${id}/users`),
  setUsers: (id, ids) => http.put(`${base}/role/${id}/users`, ids),
  // 授权菜单 basic_role_permission（type=1）
  menus: (id) => http.get(`${base}/role/${id}/menus`),
  setMenus: (id, ids) => http.put(`${base}/role/${id}/menus`, ids),
  // 授权资源 basic_role_permission（type=2）
  resources: (id) => http.get(`${base}/role/${id}/resources`),
  setResources: (id, ids) => http.put(`${base}/role/${id}/resources`, ids),
  // 关联用户组 basic_role_group
  groups: (id) => http.get(`${base}/role/${id}/groups`),
  setGroups: (id, ids) => http.put(`${base}/role/${id}/groups`, ids),
}

// ---------------- 用户 basic_user ----------------
export const basicUserApi = {
  list: (params) => http.get(`${base}/user`, { params }),
  get: (id) => http.get(`${base}/user/${id}`),
  create: (data) => http.post(`${base}/user`, data),
  update: (id, data) => http.put(`${base}/user/${id}`, data),
  remove: (id) => http.delete(`${base}/user/${id}`),
  // 关联用户组 basic_user_group
  groups: (id) => http.get(`${base}/user/${id}/groups`),
  setGroups: (id, ids) => http.put(`${base}/user/${id}/groups`, ids),
  // 关联角色 basic_user_role
  roles: (id) => http.get(`${base}/user/${id}/roles`),
  setRoles: (id, ids) => http.put(`${base}/user/${id}/roles`, ids),
  enable: (id) => http.put(`${base}/user/${id}/enable`),
  disable: (id) => http.put(`${base}/user/${id}/disable`),
}

// ---------------- 菜单 basic_menu ----------------
export const basicMenuApi = {
  list: (params) => http.get(`${base}/menu`, { params }),
  get: (id) => http.get(`${base}/menu/${id}`),
  create: (data) => http.post(`${base}/menu`, data),
  update: (id, data) => http.put(`${base}/menu/${id}`, data),
  enable: (id) => http.put(`${base}/menu/${id}/enable`),
  disable: (id) => http.put(`${base}/menu/${id}/disable`),
  listAll: () => http.get(`${base}/menu/list`),
  // 关联资源 basic_menu_resource
  resources: (id) => http.get(`${base}/menu/${id}/resources`),
  setResources: (id, ids) => http.put(`${base}/menu/${id}/resources`, ids),
  // 反查：哪些角色授权了这个菜单（只读）
  roles: (id) => http.get(`${base}/menu/${id}/roles`),
}

// ---------------- 资源 basic_resource ----------------
export const basicResourceApi = {
  list: (params) => http.get(`${base}/resource`, { params }),
  listAll: () => http.get(`${base}/resource/list`),
  get: (id) => http.get(`${base}/resource/${id}`),
  create: (data) => http.post(`${base}/resource`, data),
  update: (id, data) => http.put(`${base}/resource/${id}`, data),
  enable: (id) => http.put(`${base}/resource/${id}/enable`),
  disable: (id) => http.put(`${base}/resource/${id}/disable`),
  // 关联菜单 basic_menu_resource
  menus: (id) => http.get(`${base}/resource/${id}/menus`),
  setMenus: (id, ids) => http.put(`${base}/resource/${id}/menus`, ids),
  // 反查：哪些角色授权了这个资源（只读）
  roles: (id) => http.get(`${base}/resource/${id}/roles`),
}

// ---------------- 用户组 basic_group ----------------
export const basicGroupApi = {
  list: (params) => http.get(`${base}/group`, { params }),
  listAll: () => http.get(`${base}/group/list`),
  get: (id) => http.get(`${base}/group/${id}`),
  create: (data) => http.post(`${base}/group`, data),
  update: (id, data) => http.put(`${base}/group/${id}`, data),
  remove: (id) => http.delete(`${base}/group/${id}`),
  // 关联角色 basic_role_group
  roles: (id) => http.get(`${base}/group/${id}/roles`),
  setRoles: (id, ids) => http.put(`${base}/group/${id}/roles`, ids),
  // 关联用户 basic_user_group（组视角）
  users: (id) => http.get(`${base}/group/${id}/users`),
  setUsers: (id, ids) => http.put(`${base}/group/${id}/users`, ids),
  // 关联用户组 basic_group_group（组层级）
  subGroups: (id) => http.get(`${base}/group/${id}/sub-groups`),
  setSubGroups: (id, ids) => http.put(`${base}/group/${id}/sub-groups`, ids),
  parentGroups: (id) => http.get(`${base}/group/${id}/parent-groups`),
  setParentGroups: (id, ids) => http.put(`${base}/group/${id}/parent-groups`, ids),
  enable: (id) => http.put(`${base}/group/${id}/enable`),
  disable: (id) => http.put(`${base}/group/${id}/disable`),
}

// ---------------- 操作日志 basic_oper_log（只读） ----------------
export const basicOperLogApi = {
  list: (params) => http.get(`${base}/oper-log`, { params }),
}
