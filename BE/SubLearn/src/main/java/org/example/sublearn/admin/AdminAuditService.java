package org.example.sublearn.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.example.sublearn.domain.AdminAuditAction;
import org.example.sublearn.domain.AdminAuditLog;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.repository.AdminAuditLogRepository;
import org.example.sublearn.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminAuditService {

    private static final List<String> SENSITIVE_KEY_FRAGMENTS = List.of(
            "password", "token", "secret", "apikey", "api_key", "cookie"
    );

    private final AdminAuditLogRepository auditLogRepository;
    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper;

    public AdminAuditService(AdminAuditLogRepository auditLogRepository,
                             AppUserRepository appUserRepository,
                             ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.appUserRepository = appUserRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(Long adminUserId, AdminAuditAction action, String targetType, String targetId,
                       String summary, Map<String, Object> details, String ipAddress) {
        AppUser admin = adminUserId != null ? appUserRepository.getReferenceById(adminUserId) : null;
        String serialized = serializeDetails(details);
        auditLogRepository.save(new AdminAuditLog(admin, action, targetType, targetId, summary, serialized, ipAddress));
    }

    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    private String serializeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        Map<String, Object> sanitized = sanitizeMap(details);
        if (sanitized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (JacksonException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeMap(Map<?, ?> raw) {
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            if (isSensitiveKey(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                clean.put(key, sanitizeMap(nested));
            } else {
                clean.put(key, value);
            }
        }
        return clean;
    }

    private static boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        for (String fragment : SENSITIVE_KEY_FRAGMENTS) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
