package xyz.wewin.autumn.gateway.dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import xyz.wewin.autumn.gateway.dashboard.entity.AuditLog;
import xyz.wewin.autumn.gateway.dashboard.repo.AuditLogRepository;

import java.time.LocalDateTime;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    public Page<AuditLog> list(int page, int size, Long appId, Long userId, String action,
                               Integer status, LocalDateTime startTime, LocalDateTime endTime) {
        return auditLogRepository.findWithPage(appId, userId, action, status, startTime, endTime,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "created_at")));
    }
}