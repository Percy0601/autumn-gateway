package xyz.wewin.autumn.gateway.dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicOperLog;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicOperLogRepository;

/**
 * 操作日志落库。
 *
 * <p>使用 {@link Propagation#REQUIRES_NEW} 独立事务：即便业务操作回滚或抛异常，
 * 审计日志也能独立提交；同时写失败（如 DB 抖动）也不会拖累主流程（由调用方 catch）。
 */
@Component
public class OperLogRecorder {

    @Autowired
    private BasicOperLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(BasicOperLog log) {
        repository.save(log);
    }
}
