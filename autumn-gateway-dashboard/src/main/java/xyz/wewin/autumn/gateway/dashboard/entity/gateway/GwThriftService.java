package xyz.wewin.autumn.gateway.dashboard.entity.gateway;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Thrift 服务（协议转换目标），对应表 gw_thrift_service。
 *
 * <p>等价于 ShenYu 里的 metadata：描述"HTTP 请求要打到哪个 RPC 服务/方法"。
 * 差异是我们不需要接口 jar，靠 {@link #idlContent}/{@link #idlLocation} 里的 IDL 做泛化调用。</p>
 */
@Table("gw_thrift_service")
public class GwThriftService {

    @Id
    private Long id;

    /** 服务编码：必须等于 IDL 里的 service 名，导出为 autumn.thrift.services[i].name */
    @Column("service_code")
    private String serviceCode;

    /** 展示名（中文），仅页面展示用 */
    @Column("service_name")
    private String serviceName;

    private String host;

    private Integer port;

    /** 内联 IDL（与 idlLocation 二选一） */
    @Column("idl_content")
    private String idlContent;

    /** IDL 位置：classpath:/xx.thrift 或 file:/xx.thrift */
    @Column("idl_location")
    private String idlLocation;

    private Integer framed;

    private Integer multiplexed;

    @Column("multiplexed_name")
    private String multiplexedName;

    private Integer status;

    @Column("del_flag")
    private Integer delFlag;

    private String remark;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getIdlContent() { return idlContent; }
    public void setIdlContent(String idlContent) { this.idlContent = idlContent; }
    public String getIdlLocation() { return idlLocation; }
    public void setIdlLocation(String idlLocation) { this.idlLocation = idlLocation; }
    public Integer getFramed() { return framed; }
    public void setFramed(Integer framed) { this.framed = framed; }
    public Integer getMultiplexed() { return multiplexed; }
    public void setMultiplexed(Integer multiplexed) { this.multiplexed = multiplexed; }
    public String getMultiplexedName() { return multiplexedName; }
    public void setMultiplexedName(String multiplexedName) { this.multiplexedName = multiplexedName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
