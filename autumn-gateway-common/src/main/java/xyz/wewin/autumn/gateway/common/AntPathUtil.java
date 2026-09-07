package xyz.wewin.autumn.gateway.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;

/**
 *
 * @author: baoxin.zhao
 * @date: 2026/9/5
 */

public class AntPathUtil {
    private static final Logger log = LoggerFactory.getLogger(AntPathUtil.class);
    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    private AntPathUtil() {

    }

    public static boolean match(String pattern, String path) {
        return pathMatcher.match(pattern, path);
    }
}
