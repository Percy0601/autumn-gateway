import { defineStore } from 'pinia';
export const useTabsStore = defineStore('tabsStore', {
    state: () => ({
        visitedViews: [],
        cachedViews: []
    }),
    actions: {
        addView(view) {
            if (this.visitedViews.some(value => value.fullPath === view.fullPath)) {
                return;
            }
            this.visitedViews.push(Object.assign({}, view, {
                title: view.meta?.title || 'unknown'
            }));
        },
        addCachedView(view) {
            if (this.cachedViews.includes(view.name)) {
                return;
            }
            if (view.meta.cache) {
                this.cachedViews.push(view.name);
            }
        },
        delView(view) {
            for (const [i, v] of this.visitedViews.entries()) {
                if (v.fullPath === view.fullPath) {
                    this.visitedViews.splice(i, 1);
                    break;
                }
            }
            this.delCachedView(view).then();
        },
        delCachedView(view) {
            return new Promise(resolve => {
                const index = this.cachedViews.indexOf(view.name);
                if (index > -1) {
                    this.cachedViews.splice(index, 1);
                }
                resolve([...this.cachedViews]);
            });
        },
        delOthersViews(view) {
            this.visitedViews = this.visitedViews.filter(v => {
                return v.meta.affix || v.fullPath === view.fullPath;
            });
            const index = this.cachedViews.indexOf(view.name);
            if (index > -1) {
                this.cachedViews = this.cachedViews.slice(index, index + 1);
            }
            else {
                this.cachedViews = [];
            }
        },
        delAllViews() {
            this.visitedViews = this.visitedViews.filter(tab => tab.meta.affix);
            this.cachedViews = [];
        }
    }
});
//# sourceMappingURL=tabs.js.map