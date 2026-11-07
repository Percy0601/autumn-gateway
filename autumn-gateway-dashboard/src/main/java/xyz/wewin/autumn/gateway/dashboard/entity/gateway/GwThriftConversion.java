package xyz.wewin.autumn.gateway.dashboard.entity.gateway;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Thrift 协议转换规则，对应表 gw_thrift_conversion。
 *
 * <p>一条规则 = 一份"HTTP endpoint 入参/出参 → thrift 调用"的说明书：
 * 请求体的哪些字段、query/header/path 变量、常量，分别落到 thrift 的哪个参数上；
 * 响应是原样信封还是只要 data。导出为 {@code autumn.thrift.mappings[i]}，
 * 路由过滤器上写 {@code ThriftGeneric=<service>,<method>,<conversionCode>} 引用它。</p>
 */
@Table("gw_thrift_conversion")
public class GwThriftConversion {

    @Id
    private Long id;

    /** 规则名 = 路由过滤器第三参 / autumn.thrift.mappings[i].name */
    @Column("conversion_code")
    private String conversionCode;

    @Column("conversion_name")
    private String conversionName;

    /** 关联 gw_thrift_service.service_code */
    @Column("service_code")
    private String serviceCode;

    /** thrift 方法名；空表示由路由或动态分发决定 */
    @Column("method_name")
    private String methodName;

    /** 建议的 HTTP 路径，仅展示/联调参考 */
    @Column("http_path")
    private String httpPath;

    /** 1=请求体作为参数基座，0=只看映射规则 */
    @Column("include_body")
    private Integer includeBody;

    /** 入参规则 JSON：[{arg,source,key,value,required,defaultValue}] */
    @Column("arguments_json")
    private String argumentsJson;

    /** ENVELOPE / DATA */
    @Column("response_mode")
    private String responseMode;

    /** 成功时覆盖 HTTP 状态码；0=默认 200 */
    @Column("success_status")
    private Integer successStatus;

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
    public String getConversionCode() { return conversionCode; }
    public void setConversionCode(String conversionCode) { this.conversionCode = conversionCode; }
    public String getConversionName() { return conversionName; }
    public void setConversionName(String conversionName) { this.conversionName = conversionName; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getHttpPath() { return httpPath; }
    public void setHttpPath(String httpPath) { this.httpPath = httpPath; }
    public Integer getIncludeBody() { return includeBody; }
    public void setIncludeBody(Integer includeBody) { this.includeBody = includeBody; }
    public String getArgumentsJson() { return argumentsJson; }
    public void setArgumentsJson(String argumentsJson) { this.argumentsJson = argumentsJson; }
    public String getResponseMode() { return responseMode; }
    public void setResponseMode(String responseMode) { this.responseMode = responseMode; }
    public Integer getSuccessStatus() { return successStatus; }
    public void setSuccessStatus(Integer successStatus) { this.successStatus = successStatus; }
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
