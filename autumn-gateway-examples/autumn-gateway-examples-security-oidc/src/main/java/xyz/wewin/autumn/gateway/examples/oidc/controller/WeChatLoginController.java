package xyz.wewin.autumn.gateway.examples.oidc.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import xyz.wewin.autumn.gateway.examples.oidc.dto.WeChatUserInfo;
import xyz.wewin.autumn.gateway.examples.oidc.service.WeChatOAuthService;
import xyz.wewin.autumn.gateway.examples.oidc.util.JwtUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 微信登录控制器
 * 
 * 流程：
 * 1. GET /auth/wechat/url → 返回微信授权页 URL
 * 2. 微信回调 GET /auth/wechat/callback?code=xxx&state=yyy
 * 3. Gateway 用 code 换 access_token → 获取用户信息 → 签发 JWT
 *
 * @author: baoxin.zhao
 * @date: 2026/7/14
 */
@RestController
@RequestMapping("/auth/wechat")
public class WeChatLoginController {

    private static final Logger log = LoggerFactory.getLogger(WeChatLoginController.class);

    private final WeChatOAuthService weChatOAuthService;

    public WeChatLoginController(WeChatOAuthService weChatOAuthService) {
        this.weChatOAuthService = weChatOAuthService;
    }

    /**
     * 获取微信授权页 URL（前端调用）
     * 
     * @return { "authorizeUrl": "https://open.weixin.qq.com/..." }
     */
    @GetMapping("/url")
    public Mono<ResponseEntity<Map<String, String>>> getWeChatAuthorizeUrl() {
        String state = UUID.randomUUID().toString().replace("-", "");
        String authorizeUrl = weChatOAuthService.buildAuthorizeUrl(state);
        return Mono.just(ResponseEntity.ok(Map.of("authorizeUrl", authorizeUrl)));
    }

    /**
     * 微信授权回调
     * 
     * 微信会重定向到此接口，带上 code 和 state 参数
     * 例如: /auth/wechat/callback?code=021xyz&state=abc123
     *
     * @param code  授权码
     * @param state 防 CSRF 校验
     * @return JWT token 信息
     */
    @GetMapping("/callback")
    public Mono<ResponseEntity<Object>> weChatCallback(
            @RequestParam("code") String code,
            @RequestParam("state") String state) {

        log.info("微信回调: code={}, state={}", code, state);

        return weChatOAuthService.getAccessToken(code)
                .<ResponseEntity<Object>>flatMap(tokenResp -> {
                    // 检查微信返回错误
                    if (tokenResp.containsKey("errcode")) {
                        log.error("微信获取 token 失败: {}", tokenResp);
                        return Mono.just(ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("error", "微信授权失败: " + tokenResp.get("errmsg"))));
                    }

                    String accessToken = (String) tokenResp.get("access_token");
                    String openId = (String) tokenResp.get("openid");

                    // 获取微信用户信息
                    return weChatOAuthService.getUserInfo(accessToken, openId)
                            .<ResponseEntity<Object>>map(userInfo -> {
                                // 生成 JWT（复用现有 JwtUtils）
                                String jwt = generateJwtFromWeChatUser(userInfo);

                                Map<String, Object> result = Map.of(
                                        "token", jwt,
                                        "type", "Bearer",
                                        "userInfo", Map.of(
                                                "openId", userInfo.getOpenid(),
                                                "nickname", userInfo.getNickname() != null ? userInfo.getNickname() : "",
                                                "avatar", userInfo.getHeadImgUrl() != null ? userInfo.getHeadImgUrl() : "",
                                                "unionId", userInfo.getUnionId() != null ? userInfo.getUnionId() : ""
                                        )
                                );
                                return ResponseEntity.ok((Object) result);
                            });
                })
                .onErrorResume(e -> {
                    log.error("微信登录流程异常", e);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "登录失败: " + e.getMessage())));
                });
    }

    /**
     * 根据微信用户信息生成 JWT
     * 将微信的 openid 作为 userId，nickname 作为 username
     */
    private String generateJwtFromWeChatUser(WeChatUserInfo userInfo) {
        String userId = userInfo.getOpenid();
        String username = userInfo.getNickname() != null ? userInfo.getNickname() : "wx_" + userId.substring(0, 8);

        // TODO: 实际项目中应该查数据库确定用户角色
        // 这里默认给 USER 角色
        List<String> roles = List.of("USER");

        return JwtUtils.generateToken(username, userId, roles);
    }
}
