package xyz.wewin.autumn.gateway.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.AuditLog;
import xyz.wewin.autumn.gateway.dashboard.service.AuditLogService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/system/audit-log")
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    @GetMapping
    public Result<List<AuditLog>> list(@RequestParam(defaultValue = "1") int current,
                                       @RequestParam(defaultValue = "20") int pageSize,
                                       @RequestParam(required = false) Long appId,
                                       @RequestParam(required = false) Long userId,
                                       @RequestParam(required = false) String action,
                                       @RequestParam(required = false) Integer status,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Page<AuditLog> page = auditLogService.list(current, pageSize, appId, userId, action, status, startTime, endTime);
        return Result.success(page.getContent(), page.getTotalElements());
    }
}
