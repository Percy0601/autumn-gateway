package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 操作日志记录，对应表 basic_oper_log
 */
@Table("basic_oper_log")
public class BasicOperLog {
    @Id
    private Long id;
    @Column("app_code")
    private String appCode; // 系统标识
    @Column("business_type")
    private String businessType; // 业务类型
    private String title; // 请求标题
    @Column("request_id")
    private String requestId; // 请求ID
    @Column("request_url")
    private String requestUrl; // 请求URL
    @Column("request_method")
    private String requestMethod; // 请求方法
    @Column("request_data")
    private String requestData; // 请求数据
    @Column("class_method")
    private String classMethod; // 执行方法
    @Column("consume_time")
    private Integer consumeTime; // 执行时间（毫秒）
    @Column("response_data")
    private String responseData; // 响应结果
    private Integer status; // 操作状态：0-正常 1-异常
    @Column("error_msg")
    private String errorMsg; // 操作错误消息
    @Column("oper_id")
    private Long operId; // 操作人
    @Column("oper_ip")
    private String operIp; // 操作人IP
    @Column("op_time")
    private LocalDateTime opTime; // 操作时间

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }

    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }

    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }

    public String getRequestData() { return requestData; }
    public void setRequestData(String requestData) { this.requestData = requestData; }

    public String getClassMethod() { return classMethod; }
    public void setClassMethod(String classMethod) { this.classMethod = classMethod; }

    public Integer getConsumeTime() { return consumeTime; }
    public void setConsumeTime(Integer consumeTime) { this.consumeTime = consumeTime; }

    public String getResponseData() { return responseData; }
    public void setResponseData(String responseData) { this.responseData = responseData; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public Long getOperId() { return operId; }
    public void setOperId(Long operId) { this.operId = operId; }

    public String getOperIp() { return operIp; }
    public void setOperIp(String operIp) { this.operIp = operIp; }

    public LocalDateTime getOpTime() { return opTime; }
    public void setOpTime(LocalDateTime opTime) { this.opTime = opTime; }
}
