package com.medbook.security;

import com.medbook.dto.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes the HTTP 403 response for <em>authenticated</em> requests that lack the required
 * role — for example a token without any of the ADMIN/DOCTOR/PATIENT authorities calling an
 * appointment endpoint.
 *
 * <p>401 means "we do not know who you are"; 403 means "we know who you are, but your roles
 * do not allow this". The body reuses the standard {@link ErrorResponse} shape.</p>
 */
@Component
public class MedbookAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public MedbookAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource",
                request.getRequestURI()
        ));
    }
}
