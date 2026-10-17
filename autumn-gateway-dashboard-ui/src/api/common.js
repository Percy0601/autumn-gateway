import service from '@/utils/request';
export const useDataApi = async (url) => {
    const { data } = await service.get(url);
    return data;
};
//# sourceMappingURL=common.js.map