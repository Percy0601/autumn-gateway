import { ElMessageBox } from 'element-plus'

// 统一的二次确认弹窗参数：确定 / 取消 / warning 图标
const CONFIRM_OPTIONS = {
  confirmButtonText: '确定',
  cancelButtonText: '取消',
  type: 'warning',
} as const

/**
 * 弹出 MessageBox 确认框（删除 / 禁用等危险操作前使用）
 * @returns Promise，点「确定」resolve，点「取消」或关闭则 reject
 */
export function confirmBox(message: string, title = '提示') {
  return ElMessageBox.confirm(message, title, { ...CONFIRM_OPTIONS })
}

/**
 * 是否已确认：内部吞掉取消/关闭异常，返回布尔值，调用处直接判断即可
 * @example if (!(await isConfirmed('此操作将删除，是否继续？'))) return
 */
export async function isConfirmed(message: string, title = '提示') {
  try {
    await confirmBox(message, title)
    return true
  } catch {
    return false
  }
}
