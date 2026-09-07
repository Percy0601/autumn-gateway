package xyz.wewin.autumn.gateway.examples.oidc.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.examples.oidc.config.WeChatOAuth2User;
import xyz.wewin.autumn.gateway.examples.oidc.util.JwtUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    /**
     * JWT 表单登录（测试用）
     */
    @PostMapping("/login")
    public Map<String, String> login(@RequestBody LoginRequest request) {
        if (!"admin".equals(request.username()) || !"123456".equals(request.password())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = JwtUtils.generateToken(
                request.username(),
                "123",
                List.of("ADMIN", "USER")
        );

        return Map.of("token", token, "type", "Bearer");
    }

    /**
     * 获取当前登录用户信息
     * 支持 OAuth2 Login（微信/GitHub/Google）和 JWT Resource Server 两种模式
     */
    @GetMapping("/user")
    public Map<String, Object> getUserInfo(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            return Map.of("error", "未登录或非 OAuth2 用户");
        }

        Map<String, Object> userInfo = new HashMap<>();

        // 判断是否为微信用户
        if (oauth2User instanceof WeChatOAuth2User wechatUser) {
            userInfo.put("provider", "wechat");
            userInfo.put("openid", wechatUser.getOpenid());
            userInfo.put("nickname", wechatUser.getNickname());
            userInfo.put("headimgurl", wechatUser.getHeadImgUrl());
            userInfo.put("unionid", wechatUser.getUnionid());
        } else {
            // GitHub / Google 等标准 OIDC Provider
            userInfo.put("provider", "standard");
            userInfo.put("name", getAttr(oauth2User, "name", "login"));
            userInfo.put("email", oauth2User.getAttribute("email"));
            userInfo.put("avatar_url", oauth2User.getAttribute("avatar_url"));
            userInfo.put("bio", oauth2User.getAttribute("bio"));
        }

        userInfo.put("authorities", oauth2User.getAuthorities().toString());
        userInfo.put("allAttributes", oauth2User.getAttributes());

        return userInfo;
    }

    /**
     * OAuth2 登录成功回调页面
     */
    @GetMapping("/oauth2-success")
    public Map<String, Object> oauth2Success(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            return Map.of("message", "OAuth2 登录成功，但未获取到用户信息");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message", "OAuth2 登录成功！");

        if (oauth2User instanceof WeChatOAuth2User wechatUser) {
            result.put("provider", "wechat");
            result.put("name", wechatUser.getNickname() != null ? wechatUser.getNickname() : "");
            result.put("openid", wechatUser.getOpenid() != null ? wechatUser.getOpenid() : "");
            result.put("headimgurl", wechatUser.getHeadImgUrl() != null ? wechatUser.getHeadImgUrl() : "");
        } else {
            result.put("provider", "standard");
            result.put("name", getAttrStr(oauth2User, "name", "login"));
            result.put("email", oauth2User.getAttribute("email"));
        }

        result.put("allAttributes", oauth2User.getAttributes());

        return result;
    }

    private Object getAttr(OAuth2User user, String... keys) {
        for (String key : keys) {
            Object val = user.getAttribute(key);
            if (val != null) return val;
        }
        return null;
    }

    private String getAttrStr(OAuth2User user, String... keys) {
        Object val = getAttr(user, keys);
        return val != null ? val.toString() : null;
    }
}

record LoginRequest(String username, String password) {}
