import axios from 'axios';
import { ElMessage } from 'element-plus';
// API 前缀推导：独立部署(base './')或挂在网关子路径下都能正确拼接。
// 也可用 VITE_API_BASE 显式覆盖。
const resolveApiBase = () => {
    const env = import.meta.env || {};
    if (env.VITE_API_BASE)
        return env.VITE_API_BASE;
    if (typeof document === 'undefined')
        return '';
    const base = new URL(env.BASE_URL || './', document.baseURI);
    return base.pathname.replace(/\/+$/, '');
};
const baseURL = resolveApiBase();
const http = axios.create({
    baseURL,
    timeout: 15000
});
// 请求拦截：附加 JWT
http.interceptors.request.use((config) => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers = { ...config.headers, Authorization: `Bearer ${token}` };
    }
    return config;
});
// 响应拦截：统一处理后端 Result 结构 + 401
http.interceptors.response.use((response) => {
    const body = response.data;
    // 兼容两种返回：
    //  业务接口: { code:200, message, data, total }
    //  login/currentUser: { status } / { success, data }
    if (body && typeof body === 'object' && 'code' in body && body.code !== 200) {
        ElMessage.error(body.message || '请求失败');
        return Promise.reject(new Error(body.message || '请求失败'));
    }
    return body;
}, (error) => {
    if (error.response && error.response.status === 401) {
        localStorage.removeItem('token');
        if (typeof location !== 'undefined' && !location.hash.includes('/login')) {
            location.hash = '#/login';
        }
        ElMessage.warning('登录已过期，请重新登录');
    }
    else {
        const msg = error.response?.data?.message ||
            error.response?.data?.errorMessage ||
            error.message ||
            '网络错误，请稍后重试';
        ElMessage.error(msg);
    }
    return Promise.reject(error);
});
export const service = http;
export default http;
//# sourceMappingURL=request.js.map