import { ElMessage } from 'element-plus'

/** LocalDateTime "2026-09-04T10:00:00" -> "2026-09-04 10:00:00" */
export function fmtDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

/** 点击复制 */
export async function copyText(text) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('复制成功')
  } catch {
    ElMessage.error('复制失败')
  }
}

/** 状态枚举 */
export const STATUS_TEXT = { 1: '正常', 0: '禁用' }
export const STATUS_TAG = { 1: 'success', 0: 'danger' }

/** 权限类型 */
export const PERM_TYPES = ['MENU', 'API', 'BUTTON', 'DATA']
export const PERM_TYPE_MAP = {
  MENU: '菜单',
  API: 'API',
  BUTTON: '按钮',
  DATA: '数据',
}
export const MATCH_TYPES = ['exact', 'prefix', 'suffix']
export const HTTP_METHODS = ['ALL', 'GET', 'POST', 'PUT', 'DELETE', 'PATCH']

/** 应用编号枚举（basic_role / basic_menu 的 app_id 为 TINYINT 应用编码，非 basic_app.id） */
export const APP_ID_OPTIONS = [
  { value: 1, label: '1-运营后台' },
  { value: 2, label: '2-POP后台' },
  { value: 3, label: '3-B2B后台' },
  { value: 4, label: '4-互联网医院后台' },
  { value: 5, label: '5-数据中台' },
  { value: 6, label: '6-销售助手' },
  { value: 10, label: '10-数据洞察' },
  { value: 100, label: '100-商家后台' },
]
export const appIdLabel = (v) =>
  APP_ID_OPTIONS.find((o) => o.value === v)?.label || `应用#${v}`

/** 角色类型：1-系统角色 2-自定义角色 */
export const ROLE_TYPE_OPTIONS = [
  { value: 1, label: '系统角色' },
  { value: 2, label: '自定义角色' },
]
export const roleTypeLabel = (v) =>
  ROLE_TYPE_OPTIONS.find((o) => o.value === v)?.label || `类型#${v}`

/** 角色管理员标记：1-管理员 2-普通 */
export const MASTER_OPTIONS = [
  { value: 1, label: '管理员' },
  { value: 2, label: '普通' },
]
export const masterLabel = (v) =>
  MASTER_OPTIONS.find((o) => o.value === v)?.label || `标记#${v}`

/** 菜单类型：1-目录 2-菜单 3-按钮 */
export const MENU_TYPE_OPTIONS = [
  { value: 1, label: '目录' },
  { value: 2, label: '菜单' },
  { value: 3, label: '按钮' },
]
export const menuTypeLabel = (v) =>
  MENU_TYPE_OPTIONS.find((o) => o.value === v)?.label || `类型#${v}`

/** 菜单状态：1-启用 2-停用 */
export const MENU_STATUS_TAG = { 1: 'success', 2: 'info' }
export const menuStatusLabel = (v) => (v === 1 ? '启用' : '停用')

/** 操作日志状态：0-正常 1-异常 */
export const OPER_STATUS_TAG = { 0: 'success', 1: 'danger' }
export const operStatusLabel = (v) => (v === 1 ? '异常' : '正常')

export const PERM_TYPE_TAG = {
  MENU: 'purple',
  API: 'primary',
  BUTTON: 'warning',
  DATA: 'info',
}

/** 通用校验正则（与 React 版一致） */
export const REGEX = {
  appid: /^[a-z][a-z0-9_-]{2,31}$/,
  basePath: /^\/[a-zA-Z][\w/-]*$/,
  username: /^[a-zA-Z][a-zA-Z0-9_]{2,31}$/,
  permCode: /^[a-z][a-z0-9_:]{2,63}$/,
  email:
    /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/,
}
