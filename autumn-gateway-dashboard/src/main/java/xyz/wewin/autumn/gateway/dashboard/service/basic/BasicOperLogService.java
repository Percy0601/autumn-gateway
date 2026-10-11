package xyz.wewin.autumn.gateway.dashboard.service.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicOperLog;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicOperLogRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志服务，基于 basic_oper_log。
 *
 * <p>本服务只提供查询：日志的写入不在此处，由网关 / 拦截器统一落库。
 */
@Service
@Transactional(readOnly = true)
public class BasicOperLogService {

    /** 时间区间哨兵：避免 NULL 参数绑定 */
    private static final LocalDateTime MIN_TIME = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
    private static final LocalDateTime MAX_TIME = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    @Autowired
    private BasicOperLogRepository repository;

    public Page<BasicOperLog> list(String appCode,
                                   String title,
                                   String businessType,
                                   Integer status,
                                   LocalDateTime startTime,
                                   LocalDateTime endTime,
                                   int current,
                                   int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), size);
        long offset = (long) Math.max(current - 1, 0) * size;

        String ac = appCode == null ? "" : appCode.trim();
        String ti = title == null ? "" : title.trim();
        String bt = businessType == null ? "" : businessType.trim();
        Integer st = status == null ? -1 : status;
        LocalDateTime from = startTime == null ? MIN_TIME : startTime;
        LocalDateTime to = endTime == null ? MAX_TIME : endTime;

        List<BasicOperLog> content =
                repository.findWithPage(ac, ti, bt, st, from, to, offset, (long) size);
        long total = repository.countWithFilter(ac, ti, bt, st, from, to);
        return new PageImpl<>(content, pageable, total);
    }
}
