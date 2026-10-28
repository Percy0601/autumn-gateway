import service from '@/utils/request'

// 注意：后端网关接口统一挂在 /api/system/gateway/** 下（与 basic 的 /api/system/basic/** 同级）。
// 开发环境由 vite 的 /api 代理转发到 dashboard 服务（见 vite.config.ts）。
const BASE = '/api/system/gateway'

// 网关路由
export const gatewayRouteApi = {
  list: () => service.get(`${BASE}/route`),
  create: (data) => service.post(`${BASE}/route`, data),
  update: (id, data) => service.put(`${BASE}/route/${id}`, data),
  remove: (id) => service.delete(`${BASE}/route/${id}`),
  predicates: (id) => service.get(`${BASE}/route/${id}/predicates`),
  addPredicate: (id, data) => service.post(`${BASE}/route/${id}/predicates`, data),
  updatePredicate: (pid, data) => service.put(`${BASE}/route/predicates/${pid}`, data),
  removePredicate: (pid) => service.delete(`${BASE}/route/predicates/${pid}`),
  filters: (id) => service.get(`${BASE}/route/${id}/filters`),
  addFilter: (id, data) => service.post(`${BASE}/route/${id}/filters`, data),
  updateFilter: (fid, data) => service.put(`${BASE}/route/filters/${fid}`, data),
  removeFilter: (fid) => service.delete(`${BASE}/route/filters/${fid}`),
}

// 全局配置项
export const gatewayConfigApi = {
  list: (profile = 'dev') => service.get(`${BASE}/config`, { params: { profile } }),
  create: (data) => service.post(`${BASE}/config`, data),
  update: (id, data) => service.put(`${BASE}/config/${id}`, data),
  remove: (id) => service.delete(`${BASE}/config/${id}`),
}

// 全局默认过滤器
export const gatewayGlobalFilterApi = {
  list: () => service.get(`${BASE}/global-filter`),
  create: (data) => service.post(`${BASE}/global-filter`, data),
  update: (id, data) => service.put(`${BASE}/global-filter/${id}`, data),
  remove: (id) => service.delete(`${BASE}/global-filter/${id}`),
}

// 配置中心连接
export const gatewayCenterApi = {
  list: () => service.get(`${BASE}/center`),
  create: (data) => service.post(`${BASE}/center`, data),
  update: (id, data) => service.put(`${BASE}/center/${id}`, data),
  remove: (id) => service.delete(`${BASE}/center/${id}`),
  services: (id) => service.get(`${BASE}/center/${id}/services`),
}

// 过滤器/断言模板（v1）
export const gatewayTemplateApi = {
  filterList: () => service.get(`${BASE}/template/filter`),
  predicateList: () => service.get(`${BASE}/template/predicate`),
}

// 模板系统 v2（模板化可视化配置）：值类型目录 / 模板定义 / 结构化实例 / 校验导出
export const gatewayTemplateV2Api = {
  // 值类型目录
  valueTypes: (status) => service.get(`${BASE}/template-v2/value-types`, { params: { status } }),
  createValueType: (data) => service.post(`${BASE}/template-v2/value-types`, data),
  updateValueType: (id, data) => service.put(`${BASE}/template-v2/value-types/${id}`, data),
  removeValueType: (id) => service.delete(`${BASE}/template-v2/value-types/${id}`),
  saveTypeFields: (id, fields) => service.put(`${BASE}/template-v2/value-types/${id}/fields`, fields),
  // 模板定义
  templates: (kind) => service.get(`${BASE}/template-v2/templates`, { params: { kind } }),
  templateTree: (id) => service.get(`${BASE}/template-v2/templates/${id}/tree`),
  createTemplate: (data) => service.post(`${BASE}/template-v2/templates`, data),
  updateTemplate: (id, data) => service.put(`${BASE}/template-v2/templates/${id}`, data),
  removeTemplate: (id) => service.delete(`${BASE}/template-v2/templates/${id}`),
  // STRUCT 实例
  structs: (profile) => service.get(`${BASE}/template-v2/structs`, { params: { profile } }),
  createStruct: (data) => service.post(`${BASE}/template-v2/structs`, data),
  updateStruct: (id, data) => service.put(`${BASE}/template-v2/structs/${id}`, data),
  removeStruct: (id) => service.delete(`${BASE}/template-v2/structs/${id}`),
  structPreview: (id) => service.get(`${BASE}/template-v2/structs/${id}/preview`),
  // 校验与导出预览（FILTER/PREDICATE → args 扁平键；STRUCT → properties 行）
  validate: (data) => service.post(`${BASE}/template-v2/validate`, data),
}
