/**
 * 路径匹配器
 * @param {string} pattern
 * @param {string} path
 * @returns {Boolean}
 */
export function isPathMatch(pattern: string, path: string) {
    const regexPattern = pattern
        .replace(/\//g, "\\/")
        .replace(/\*\*/g, ".*")
        .replace(/\*/g, "[^\\/]*");
    const regex = new RegExp(`^${regexPattern}$`);
    return regex.test(path);
}
