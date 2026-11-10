package xyz.wewin.autumn.gateway.dashboard.entity.gateway;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 服务治理：已登记的 autumn service framework 服务实例，对应表 gw_service_instance。
 *
 * <p>框架的 ControlApi 没有「枚举所有实例」的远程方法（USER_GUIDE 把服务列表列为后续规划），
 * 所以需要在这里登记实例地址，再由 dashboard 逐个远程 invoke 控制面取观测/度量数据。</p>
 *
 * <p>控制面 token 不落库：同集群共用一个共享密钥，统一由配置
 * {@code autumn.governance.control-token} 注入，避免敏感信息进数据库和接口响应。</p>
 */
@Table("gw_service_instance")
public class GwServiceInstance {

    @Id
    private Long id;

    /** 展示名 / 注册中心的 application.name */
    @Column("service_name")
    private String serviceName;

    /** Thrift 服务地址 */
    private String host;

    /** Thrift 服务端口，默认 30880 */
    private Integer port;

    /** 独立控制面端口；>0 时优先于 port 连接（框架 provider.control-port） */
    @Column("control_port")
    private Integer controlPort;

    /** 1 启用 0 停用（停用的实例不参与 overview 轮询） */
    private Integer status;

    @Column("del_flag")
    private Integer delFlag;

    private String remark;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    /** 实际建立 Thrift 连接的端口 */
    public int resolvePort() {
        if (controlPort != null && controlPort > 0) {
            return controlPort;
        }
        return port == null || port <= 0 ? 30880 : port;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Integer getControlPort() {
        return controlPort;
    }

    public void setControlPort(Integer controlPort) {
        this.controlPort = controlPort;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
