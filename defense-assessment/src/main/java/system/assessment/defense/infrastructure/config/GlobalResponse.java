package system.assessment.defense.infrastructure.config;

import cn.hutool.json.JSONUtil;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Set;

/**
 * @Author taoHouChao
 * @Date 12:24 2025/6/7
 */
@RestControllerAdvice
public class GlobalResponse implements ResponseBodyAdvice<Object> {

    // Binary-stream endpoints write directly to the response output and must not
    // be wrapped as {code,message,data} JSON (otherwise the committed stream throws).
    private static final Set<String> excludePath = Set.of(
            "/v3/api-docs",
            "/gemini/generate-voice",
            "/file/preview",
            "/backend/student/export-template",
            "/backend/student/export",
            "/backend/study-record/export-record"
    );

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String requestURI = requestAttributes.getRequest().getRequestURI();
        return !excludePath.contains(requestURI);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof BaseResult<?>) {
            return body;
        }
        if (body instanceof String) {
            return JSONUtil.toJsonStr(BaseResult.success(body));
        }
        return BaseResult.success(body);
    }
}
