package xyz.wewin.autumn.gateway.common;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

/**
 * Caffeine 本地缓存工具类
 * Caffeine 3.2.x SpringBoot4.x Java21
 */
public class CaffeineCacheUtil {
    private static final Logger log = LoggerFactory.getLogger(CaffeineCacheUtil.class);
    private CaffeineCacheUtil() {

    }

    /**
     * 基础缓存：无自动加载，手动put/get
     * expireAfterWrite：写入后过期
     * maximumSize：最大缓存条目数
     */
    private static final Cache<String, Object> MANUAL_CACHE = Caffeine.newBuilder()
            // 写入之后多久过期
            .expireAfterAccess(2, TimeUnit.HOURS)
            // 最大缓存数量，防止OOM
            .maximumSize(10_000)
            // 开启统计，监控命中率、淘汰数量
            .recordStats()
            .build();
    /**
     * LoadingCache：自动加载缓存，get时不存在调用load方法回源
     */
    private static final LoadingCache<String, String> LOADING_CACHE = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES) // 访问后N分钟过期
            .maximumSize(5000)
            .recordStats()
            .build(key -> {
                // 缓存未命中时，回源逻辑，例如查DB
                log.info("缓存未命中执行回源，key: {}", key);
                return "source_data_" + key;
            });

    // ========== 手动Cache操作API ==========

    /**
     * 设置缓存
     */
    public static void put(String key, Object value) {
        MANUAL_CACHE.put(key, value);
    }

    /**
     * 获取缓存，返回null不存在
     */
    public static <T> T get(String key, Class<T> clazz) {
        return (T) MANUAL_CACHE.getIfPresent(key);
    }

    /**
     * 获取缓存，返回null不存在
     */
    public static Object get(String key) {
        return MANUAL_CACHE.getIfPresent(key);
    }

    /**
     * 获取，如果不存在执行回源函数
     */
    public static Object get(String key, java.util.function.Function<String, Object> mappingFunction) {
        return MANUAL_CACHE.get(key, mappingFunction);
    }

    /**
     * 删除指定key
     */
    public static void remove(String key) {
        MANUAL_CACHE.invalidate(key);
    }

    /**
     * 清空全部缓存
     */
    public static void clearAll() {
        MANUAL_CACHE.invalidateAll();
    }

    /**
     * 获取缓存统计信息
     */
    public static String getStats() {
        return MANUAL_CACHE.stats().toString();
    }

    // ========= LoadingCache示例方法 =========
    public static String getWithLoad(String key) {
        return LOADING_CACHE.get(key);
    }

}
