import { SessionStorage, Storage } from '@/utils/storage';
import CacheKey from '@/utils/cache/key';
import { themeConfig } from '@/store/theme/config';
// 缓存
class Cache {
    getToken = () => {
        return Storage.getItem(CacheKey.TokenKey) || '';
    };
    setToken = (value) => {
        Storage.setItem(CacheKey.TokenKey, value);
    };
    getTenantId = () => {
        return Storage.getItem(CacheKey.TenantId) || '';
    };
    setTenantId = (value) => {
        Storage.setItem(CacheKey.TenantId, value);
    };
    getRefreshToken = () => {
        return Storage.getItem(CacheKey.RefreshTokenKey) || '';
    };
    setRefreshToken = (value) => {
        Storage.setItem(CacheKey.RefreshTokenKey, value);
    };
    getSidebarOpened = () => {
        return Storage.getItem(CacheKey.SidebarOpenedKey) || false;
    };
    setSidebarOpened = (value) => {
        Storage.setItem(CacheKey.SidebarOpenedKey, value);
    };
    getLanguage = () => {
        return Storage.getItem(CacheKey.LangKey) || 'zh-CN';
    };
    setLanguage = (value) => {
        Storage.setItem(CacheKey.LangKey, value);
    };
    getComponentSize = () => {
        return Storage.getItem(CacheKey.ComponentSizeKey) || 'default';
    };
    setComponentSize = (value) => {
        Storage.setItem(CacheKey.ComponentSizeKey, value);
    };
    getTheme = () => {
        return SessionStorage.getItem(CacheKey.ThemeKey) || themeConfig;
    };
    setTheme = (value) => {
        SessionStorage.setItem(CacheKey.ThemeKey, value);
    };
    removeTheme = () => {
        SessionStorage.removeItem(CacheKey.ThemeKey);
    };
    getRedirect = () => {
        return Storage.getItem(CacheKey.Redirect);
    };
    setRedirect = (value) => {
        Storage.setItem(CacheKey.Redirect, value);
    };
}
export default new Cache();
//# sourceMappingURL=index.js.map