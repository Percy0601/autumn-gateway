package xyz.wewin.autumn.gateway.thrift.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thrift 泛化路由配置（{@code autumn.thrift.*}）。
 *
 * <h3>默认关闭</h3>
 * {@link #enabled} 默认 {@code false}：starter 被打进部署包但没有显式打开时，
 * {@code ThriftGeneric} 路由会直接返回 503，一个字节都不会发往后端。
 * 这样"引入了依赖"和"改变了既有行为"是两件事。
 *
 * <h3>P0 的端点来源是静态配置</h3>
 * 服务地址来自 {@link Service#getHost()} / {@link Service#getPort()}。
 * 从注册中心解析实例（复用 HTTP 侧的泳道选择器）排在 P1，见
 * {@code docs/网关Thrift泛化路由规划.md}。
 */
@ConfigurationProperties("autumn.thrift")
public class ThriftGenericProperties {

    /** 总开关；false 时过滤器短路返回 503 */
    private boolean enabled = false;

    /** TCP 连接超时（毫秒） */
    private int connectTimeoutMillis = 2000;

    /** 读超时（毫秒） */
    private int socketTimeoutMillis = 5000;

    /** 并发闸门：同时在飞的 thrift 调用上限，超出直接 503 快速失败 */
    private int maxConcurrentCalls = 256;

    /** 全局默认：是否 framed（与 TServer 的 transportFactory 保持一致） */
    private boolean framed = true;

    /** 全局默认：是否 multiplexed（后端用了 TMultiplexedProcessor 时打开） */
    private boolean multiplexed = false;

    /**
     * 是否把后端错误信息原文透给调用方。
     * 默认 false：只回 errorType，避免把后端异常栈/实现细节漏给外部。
     */
    private boolean exposeErrorDetail = false;

    /**
     * HTTP 头 → attachment key 的映射。默认把泳道头翻成 thrift 侧的隐式参数名，
     * 与 {@code autumn-cloud-extension} 的 {@code X-Traffic} 和
     * {@code autumn-protocol-extension} 的 {@code traffic} 参数对齐。
     */
    private Map<String, String> attachmentHeaderMapping = new LinkedHashMap<>(Map.of("X-Traffic", "traffic"));

    /** 需要暴露给调用方的 thrift 服务；P0 为静态配置 */
    private List<Service> services = new ArrayList<>();

    /**
     * 入参/出参映射规则。路由上写 {@code ThriftGeneric=<service>,<method>,<mapping>} 引用它。
     *
     * <p>不配映射时保持最朴素的行为：请求体 JSON 整体作为参数（对象按参数名，数组按声明顺序）。
     * 配了映射就可以把 header / queryString / path 变量 / 常量拼进同一个参数对象，
     * 这正是"HTTP endpoint 转 thrift 参数"可配置的部分。</p>
     */
    private List<Mapping> mappings = new ArrayList<>();

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getConnectTimeoutMillis() {
        return this.connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getSocketTimeoutMillis() {
        return this.socketTimeoutMillis;
    }

    public void setSocketTimeoutMillis(int socketTimeoutMillis) {
        this.socketTimeoutMillis = socketTimeoutMillis;
    }

    public int getMaxConcurrentCalls() {
        return this.maxConcurrentCalls;
    }

    public void setMaxConcurrentCalls(int maxConcurrentCalls) {
        this.maxConcurrentCalls = maxConcurrentCalls;
    }

    public boolean isFramed() {
        return this.framed;
    }

    public void setFramed(boolean framed) {
        this.framed = framed;
    }

    public boolean isMultiplexed() {
        return this.multiplexed;
    }

    public void setMultiplexed(boolean multiplexed) {
        this.multiplexed = multiplexed;
    }

    public boolean isExposeErrorDetail() {
        return this.exposeErrorDetail;
    }

    public void setExposeErrorDetail(boolean exposeErrorDetail) {
        this.exposeErrorDetail = exposeErrorDetail;
    }

    public Map<String, String> getAttachmentHeaderMapping() {
        return this.attachmentHeaderMapping;
    }

    public void setAttachmentHeaderMapping(Map<String, String> attachmentHeaderMapping) {
        this.attachmentHeaderMapping = attachmentHeaderMapping;
    }

    public List<Service> getServices() {
        return this.services;
    }

    public void setServices(List<Service> services) {
        this.services = services;
    }

    public List<Mapping> getMappings() {
        return this.mappings;
    }

    public void setMappings(List<Mapping> mappings) {
        this.mappings = mappings;
    }

    /** 参数取值来源 */
    public enum ArgumentSource {
        /** 请求体 JSON 里的同名字段 */
        BODY,
        /** queryString 参数 */
        QUERY,
        /** 请求头 */
        HEADER,
        /** 路径变量（{@code Path=/x/{name}} 里的 name） */
        PATH,
        /** 配置里的常量 */
        FIXED
    }

    /** 响应形态 */
    public enum ResponseMode {
        /** 默认：{@code {success, costMillis, data|errorType/message}} */
        ENVELOPE,
        /** 只返回 data 本身（业务更愿意看到这种干净的结构） */
        DATA
    }

    /**
     * 一套入参/出参映射规则。
     *
     * <p>{@link #includeBody} 为 true（默认）时，请求体先作为参数"基座"，
     * 再由 {@link #arguments} 逐条覆盖/补充；为 false 时只看映射规则，请求体不参与。</p>
     */
    public static class Mapping {

        /** 规则名，路由上用 {@code ThriftGeneric=<service>,<method>,<name>} 引用 */
        private String name;

        /** 是否把请求体作为参数基座 */
        private boolean includeBody = true;

        private List<Argument> arguments = new ArrayList<>();

        private Response response = new Response();

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isIncludeBody() {
            return this.includeBody;
        }

        public void setIncludeBody(boolean includeBody) {
            this.includeBody = includeBody;
        }

        public List<Argument> getArguments() {
            return this.arguments;
        }

        public void setArguments(List<Argument> arguments) {
            this.arguments = arguments;
        }

        public Response getResponse() {
            return this.response;
        }

        public void setResponse(Response response) {
            this.response = response;
        }
    }

    /**
     * 一条参数规则：把 {@link #source}+{@link #key} 取到的值，写到 thrift 的 {@link #arg} 参数上。
     */
    public static class Argument {

        /** thrift 侧参数名（IDL 里的字段名） */
        private String arg;

        private ArgumentSource source = ArgumentSource.BODY;

        /** BODY=字段名；QUERY=参数名；HEADER=头名；PATH=路径变量名；FIXED 忽略 */
        private String key;

        /** FIXED 时的常量值 */
        private String value;

        /** 缺值时是否报错（默认 false：缺就跳过，让 thrift 侧按自己的 required 规则处理） */
        private boolean required = false;

        /** 取不到值时使用的默认值 */
        private String defaultValue;

        public String getArg() {
            return this.arg;
        }

        public void setArg(String arg) {
            this.arg = arg;
        }

        public ArgumentSource getSource() {
            return this.source;
        }

        public void setSource(ArgumentSource source) {
            this.source = source;
        }

        public String getKey() {
            return this.key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return this.value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public boolean isRequired() {
            return this.required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getDefaultValue() {
            return this.defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }
    }

    /** 出参规则 */
    public static class Response {

        private ResponseMode mode = ResponseMode.ENVELOPE;

        /** 成功时覆盖 HTTP 状态码；空表示按默认映射（200） */
        private Integer successStatus;

        public ResponseMode getMode() {
            return this.mode;
        }

        public void setMode(ResponseMode mode) {
            this.mode = mode;
        }

        public Integer getSuccessStatus() {
            return this.successStatus;
        }

        public void setSuccessStatus(Integer successStatus) {
            this.successStatus = successStatus;
        }
    }

    /**
     * 一个 thrift 服务：名字 + 地址 + IDL 来源。
     *
     * <p>IDL 二选一：{@link #idl} 内联文本，或 {@link #idlLocation}
     * （{@code classpath:idl/demo.thrift} / {@code file:/tmp/demo.thrift}）。
     * 用内联方式时注意 properties 里的换行要写成 {@code \n}。</p>
     */
    public static class Service {

        /** 服务名；留空时取 IDL 里唯一的 service 名 */
        private String name;

        private String host = "127.0.0.1";

        private int port;

        private String idl;

        private String idlLocation;

        /** 单服务覆盖全局的 framed / multiplexed */
        private Boolean framed;

        private Boolean multiplexed;

        /** multiplexed 模式下发给后端的服务名前缀，默认用 {@link #name} */
        private String multiplexedName;

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHost() {
            return this.host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return this.port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getIdl() {
            return this.idl;
        }

        public void setIdl(String idl) {
            this.idl = idl;
        }

        public String getIdlLocation() {
            return this.idlLocation;
        }

        public void setIdlLocation(String idlLocation) {
            this.idlLocation = idlLocation;
        }

        public Boolean getFramed() {
            return this.framed;
        }

        public void setFramed(Boolean framed) {
            this.framed = framed;
        }

        public Boolean getMultiplexed() {
            return this.multiplexed;
        }

        public void setMultiplexed(Boolean multiplexed) {
            this.multiplexed = multiplexed;
        }

        public String getMultiplexedName() {
            return this.multiplexedName;
        }

        public void setMultiplexedName(String multiplexedName) {
            this.multiplexedName = multiplexedName;
        }
    }
}
