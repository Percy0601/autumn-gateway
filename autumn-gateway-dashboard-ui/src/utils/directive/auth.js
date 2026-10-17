import { useUserStore } from '@/store/modules/user';
export function auth(app) {
    // 权限验证
    app.directive('auth', {
        mounted(el, binding) {
            const userStore = useUserStore();
            if (!userStore.authorityList.some((v) => v === binding.value)) {
                el.parentNode.removeChild(el);
            }
        }
    });
}
//# sourceMappingURL=auth.js.map