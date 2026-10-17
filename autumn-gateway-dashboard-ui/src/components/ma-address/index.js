import pca from './pca.json';
const pcaData = Object.entries(pca).map(([province, cities]) => ({
    label: province,
    value: province,
    children: Object.entries(cities).map(([city, areas]) => ({
        label: city,
        value: city,
        children: areas.map((area) => ({
            label: area,
            value: area
        }))
    }))
}));
export { pcaData };
//# sourceMappingURL=index.js.map