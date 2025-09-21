class Storage {
    /**
     * 将对象保存到 localStorage 中
     * @param {string} key 键
     * @param {T} value 值，泛型 T
     */
    static set<T>(key: string, value: T): void {
        localStorage.setItem(key, JSON.stringify(value));
    }

    /**
     * 从 localStorage 中获取对象
     * @param {string} key 键
     * @return {T | null} 返回对象，泛型 T 或 null
     */
    static get<T>(key: string): T | null {
        const item = localStorage.getItem(key);
        return item ? JSON.parse(item) as T : null;
    }

    /**
     * 从 localStorage 中移除指定的 key
     * @param {string} key 键
     */
    static remove(key: string): void {
        localStorage.removeItem(key);
    }

    /**
     * 清空 localStorage
     */
    static clear(): void {
        localStorage.clear();
    }
}

export default Storage;
