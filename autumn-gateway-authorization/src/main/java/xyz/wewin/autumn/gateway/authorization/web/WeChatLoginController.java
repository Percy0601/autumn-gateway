package xyz.wewin.autumn.gateway.authorization.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import xyz.wewin.autumn.gateway.authorization.config.WeChatProperties;
import xyz.wewin.autumn.gateway.authorization.security.AutumnUserDetails;
import xyz.wewin.autumn.gateway.authorization.security.JdbcUserDetailsService;
import xyz.wewin.autumn.gateway.authorization.security.WeChatAccountService;
import xyz.wewin.autumn.gateway.authorization.security.WeChatService;

import java.io.IOException;

/**
 * 微信登录入口与回调。
 *
 * <p>流程：{@code /login/wechat} 生成 state 并写入 session，再 302 到微信授权页；
 * 用户在微信侧确认后，微信携带 {@code code + state} 跳回 {@code /login/wechat/callback}；
 * 校验 state 后换取用户资料、映射/新建本地账号，并以编程方式完成登录（写入 SecurityContext 与会话），
 * 最后重定向回最初被拦截的 {@code /oauth2/authorize} 请求，使 OIDC 授权码流程继续。</p>
 */
@Controller
public class WeChatLoginController {

    private static final String STATE_KEY = "AUTUMN_WECHAT_STATE";

    private final WeChatProperties props;
    private final WeChatService wechatService;
    private final WeChatAccountService accountService;
    private final JdbcUserDetailsService userDetailsService;

    public WeChatLoginController(WeChatProperties props,
                                 WeChatService wechatService,
                                 WeChatAccountService accountService,
                                 JdbcUserDetailsService userDetailsService) {
        this.props = props;
        this.wechatService = wechatService;
        this.accountService = accountService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * 发起微信登录：生成 state 存 session，再重定向到微信授权页。
     */
    @GetMapping("/login/wechat")
    public String start(HttpServletRequest request) {
        if (!props.isEnabled()) {
            return "redirect:/login?error=wechat_disabled";
        }
        String state = WeChatService.randomState();
        request.getSession(true).setAttribute(STATE_KEY, state);
        return "redirect:" + wechatService.buildAuthorizeUrl(state);
    }

    /**
     * 微信回调：校验 state → 换资料 → 映射账号 → 编程式登录 → 回到原授权请求。
     */
    @GetMapping("/login/wechat/callback")
    public void callback(@RequestParam("code") String code,
                         @RequestParam(value = "state", required = false) String state,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        if (!props.isEnabled()) {
            response.sendRedirect("/login?error=wechat_disabled");
            return;
        }
        HttpSession session = request.getSession(false);
        String savedState = session != null ? (String) session.getAttribute(STATE_KEY) : null;
        if (session != null) {
            session.removeAttribute(STATE_KEY);
        }
        if (savedState == null || state == null || !savedState.equals(state)) {
            response.sendRedirect("/login?error=wechat_state");
            return;
        }
        try {
            WeChatService.WeChatUserInfo info = wechatService.exchange(code);
            WeChatAccountService.LocalUser local =
                    accountService.resolveLocalUser(info, props.getAppId(), props.isAutoCreateUser());
            accountService.touchLogin(local.id());

            AutumnUserDetails details = userDetailsService.loadUserById(local.id());
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    details, null, details.getAuthorities());

            // 显式写入会话，确保后续 /oauth2/authorize 请求认得已登录身份
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            new HttpSessionSecurityContextRepository().saveContext(securityContext, request, response);
            SecurityContextHolder.clearContext();

            SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
            String target = savedRequest != null ? savedRequest.getRedirectUrl() : "/";
            new HttpSessionRequestCache().removeRequest(request, response);
            response.sendRedirect(target);
        } catch (Exception e) {
            response.sendRedirect("/login?error=wechat");
        }
    }
}
