import { defineStore } from 'pinia';
import { generateRoutes, autumnMenu } from '@/router';
export const useRouterStore = defineStore('routerStore', {
    state: () => ({
        menuRoutes: [],
        searchMenu: [],
        routes: []
    }),
    actions: {
        // 静态菜单，不再从后端拉取菜单树
        async getMenuRoutes() {
            const routes = generateRoutes(autumnMenu);
            this.menuRoutes.push(...routes);
            return this.menuRoutes;
        },
        setSearchMenu(routers) {
            this.searchMenu = routers;
        },
        setRoutes(routers) {
            this.routes = routers;
        }
    }
});
//# sourceMappingURL=router.js.map