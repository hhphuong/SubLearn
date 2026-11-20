package org.example.sublearn.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs every API request with its HTTP method, URL (including query string),
 * response status, execution duration, and whether it succeeded or failed.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api")) {
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String queryString = request.getQueryString() != null ? "?" + request.getQueryString() : "";
        String fullPath = uri + queryString;

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            boolean isSuccess = status >= 200 && status < 400;

            if (isSuccess) {
                log.info("[API THÀNH CÔNG] {} {} -> Status {} ({}ms)", method, fullPath, status, duration);
            } else {
                log.warn("[API THẤT BẠI] {} {} -> Status {} ({}ms)", method, fullPath, status, duration);
            }
        }
    }
}
