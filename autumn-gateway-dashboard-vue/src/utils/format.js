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
