package com.medbook.security;

import com.medbook.dto.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes the HTTP 401 response for requests that are not authenticated.
 *
 * <p>Two cases reach this point: a protected endpoint was called with no token at all, or
 * the presented Bearer token failed to validate (bad signature, expired, malformed). The
 * response body reuses the same {@link ErrorResponse} shape as the rest of the API instead
 * of Spring's default HTML/text error page.</p>
 *
 * <p>The message deliberately stays generic and never echoes the token, the header, or any
 * internal parsing detail.</p>
 */
@Component
public class MedbookAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public MedbookAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String message = authException instanceof OAuth2AuthenticationException
                ? "The access token is missing, invalid, or expired"
                : "Authentication is required to access this resource";

        writeJson(response, HttpStatus.UNAUTHORIZED, message, request);
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, String message,
                           HttpServletRequest request) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(status, message, request.getRequestURI()));
    }
}
