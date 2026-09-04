import { defineStore } from 'pinia'
import http from '../utils/request'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    currentUser: null,
  }),
  getters: {
    isLogin: (state) => !!state.token,
    displayName: (state) =>
      state.currentUser?.name || '未登录',
  },
  actions: {
    setToken(token) {
      this.token = token
      if (token) localStorage.setItem('token', token)
      else localStorage.removeItem('token')
    },
    /** 获取当前用户信息（未登录时返回 null） */
    async fetchUserInfo() {
      try {
        const body = await http.get('/api/currentUser')
        if (body && body.success !== false && body.data) {
          this.currentUser = body.data
          return body.data
        }
        this.currentUser = null
        return null
      } catch (e) {
        // 401 场景由 axios 拦截器统一移除 token 并跳转登录页，
        // 这里仅清空本地用户信息，避免网络抖动导致误登出
        this.currentUser = null
        return null
      }
    },
    async logout() {
      try {
        await http.post('/api/login/outLogin')
      } catch {
        /* 忽略退出接口异常 */
      }
      this.setToken('')
      this.currentUser = null
    },
  },
})
