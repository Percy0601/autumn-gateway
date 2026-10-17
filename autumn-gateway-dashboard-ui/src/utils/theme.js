// 处理主题样式
export const handleThemeStyle = (theme) => {
    document.documentElement.style.setProperty('--el-color-primary', theme.primaryColor);
    for (let i = 1; i <= 9; i++) {
        document.documentElement.style.setProperty(`--el-color-primary-light-${i}`, `${getLightColor(theme.primaryColor, i / 10)}`);
    }
    for (let i = 1; i <= 9; i++) {
        document.documentElement.style.setProperty(`--el-color-primary-dark-${i}`, `${getDarkColor(theme.primaryColor, i / 10)}`);
    }
};
// hex颜色转rgb颜色
const hexToRgb = (str) => {
    str = str.replace('#', '');
    let hexs = str.match(/../g);
    for (let i = 0; i < 3; i++) {
        hexs[i] = parseInt(hexs[i], 16);
    }
    return hexs;
};
// rgb颜色转Hex颜色
const rgbToHex = (r, g, b) => {
    let hexs = [r.toString(16), g.toString(16), b.toString(16)];
    for (let i = 0; i < 3; i++) {
        if (hexs[i].length == 1) {
            hexs[i] = `0${hexs[i]}`;
        }
    }
    return `#${hexs.join('')}`;
};
// 变浅颜色值
const getLightColor = (color, level) => {
    let rgb = hexToRgb(color);
    for (let i = 0; i < 3; i++) {
        rgb[i] = Math.floor((255 - rgb[i]) * level + rgb[i]);
    }
    return rgbToHex(rgb[0], rgb[1], rgb[2]);
};
// 变深颜色值
const getDarkColor = (color, level) => {
    let rgb = hexToRgb(color);
    for (let i = 0; i < 3; i++) {
        rgb[i] = Math.floor(rgb[i] * (1 - level));
    }
    return rgbToHex(rgb[0], rgb[1], rgb[2]);
};
//# sourceMappingURL=theme.js.map