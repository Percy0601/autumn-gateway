package xyz.wewin.autumn.gateway.examples.oidc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微信用户信息 DTO
 * 对应微信 API: https://api.weixin.qq.com/sns/userinfo 返回
 *
 * @author: baoxin.zhao
 * @date: 2026/7/14
 */
public class WeChatUserInfo {

    /**
     * 微信用户 openid（唯一标识）
     */
    @JsonProperty("openid")
    private String openid;

    /**
     * 微信昵称
     */
    @JsonProperty("nickname")
    private String nickname;

    /**
     * 性别 1=男 2=女 0=未知
     */
    @JsonProperty("sex")
    private Integer sex;

    /**
     * 用户头像 URL
     */
    @JsonProperty("headimgurl")
    private String headImgUrl;

    /**
     * 用户统一标识（同一微信开放平台账号下唯一）
     */
    @JsonProperty("unionid")
    private String unionId;

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Integer getSex() {
        return sex;
    }

    public void setSex(Integer sex) {
        this.sex = sex;
    }

    public String getHeadImgUrl() {
        return headImgUrl;
    }

    public void setHeadImgUrl(String headImgUrl) {
        this.headImgUrl = headImgUrl;
    }

    public String getUnionId() {
        return unionId;
    }

    public void setUnionId(String unionId) {
        this.unionId = unionId;
    }

    @Override
    public String toString() {
        return "WeChatUserInfo{" +
                "openid='" + openid + '\'' +
                ", nickname='" + nickname + '\'' +
                ", sex=" + sex +
                ", unionId='" + unionId + '\'' +
                '}';
    }
}
