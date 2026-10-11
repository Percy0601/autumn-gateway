package xyz.wewin.autumn.gateway.dashboard.controller.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicOperLog;
import xyz.wewin.autumn.gateway.dashboard.service.basic.BasicOperLogService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志（basic_oper_log），只读查询。
 *
 * <p>日志写入不在此处——由网关 / 拦截器统一落库，这里只负责浏览检索。
 */
@RestController
@RequestMapping("/api/system/basic/oper-log")
public class BasicOperLogController {

    @Autowired
    private BasicOperLogService service;

    /**
     * 分页列表：
     * ?current=1&pageSize=10&appCode=&title=&businessType=&status=&startTime=yyyy-MM-dd&endTime=yyyy-MM-dd
     */
    @GetMapping
    public Result<List<BasicOperLog>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDateTime endTime) {
        Page<BasicOperLog> page =
                service.list(appCode, title, businessType, status, startTime, endTime, current, pageSize);
        return Result.success(page.getContent(), page.getTotalElements());
    }
}
