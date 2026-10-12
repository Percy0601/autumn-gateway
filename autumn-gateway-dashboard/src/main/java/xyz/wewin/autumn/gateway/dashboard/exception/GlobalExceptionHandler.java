package xyz.wewin.autumn.gateway.dashboard.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.wewin.autumn.gateway.dashboard.common.Result;

/**
 * 全局异常处理。
 *
 * <p>要点：
 * <ol>
 *   <li>兜底的 {@code Exception} 分支不能一律返回 500。Spring 自身的「接口不存在(404)」、
 *       「方法不支持(405)」等信号都实现了 {@link ErrorResponse}，若被统一吞成
 *       500「服务器内部错误」，前端就完全无法定位问题。这里按它自带的状态码返回。</li>
 *   <li>数据约束冲突（唯一键重复、NOT NULL 列收到 NULL）转成 400 并给出可读原因。</li>
 *   <li>真正的 500 打印完整堆栈，并把原因带回前端，便于排查。</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务校验失败：直接把原因带给前端 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Result.error(400, e.getMessage()));
    }

    /** 唯一键冲突、NOT NULL 列收到 NULL 等 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据约束冲突: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Result.error(400, describe(e)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        // 接口不存在 / 方法不支持 / 参数解析失败 等都实现了 ErrorResponse
        if (e instanceof ErrorResponse er) {
            int status = er.getStatusCode().value();
            String msg = status == 404 ? "接口不存在" : String.valueOf(e.getMessage());
            return ResponseEntity.status(status).body(Result.error(status, msg));
        }
        log.error("服务器内部错误", e);
        return ResponseEntity.internalServerError()
                .body(Result.error(500, "服务器内部错误: " + e.getMessage()));
    }

    /** 把数据库原始报错翻译成可读的原因 */
    private String describe(DataIntegrityViolationException e) {
        Throwable root = e.getRootCause() != null ? e.getRootCause() : e;
        String msg = String.valueOf(root.getMessage());
        if (msg.contains("Duplicate entry")) {
            return "数据已存在（唯一约束冲突）: " + msg;
        }
        if (msg.contains("cannot be null")) {
            return "必填字段为空: " + msg;
        }
        return "数据校验未通过: " + msg;
    }
}
