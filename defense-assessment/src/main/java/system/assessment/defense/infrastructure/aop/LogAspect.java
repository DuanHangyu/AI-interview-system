package system.assessment.defense.infrastructure.aop;

import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @Author taoHouChao
 * @Date 09:47 2025/5/18
 */
@Aspect
@Component
@Slf4j
public class LogAspect {

    private static final int MAX_LOG_LENGTH = 1000;

    private static final Pattern JSON_SECRET_PATTERN = Pattern.compile(
            "(?i)(\"(?:password|token|authorization|apiKey|api-key|secret|accessKeyId|accessKeySecret)\"\\s*:\\s*\")([^\"]*)(\")");

    private static final Pattern TO_STRING_SECRET_PATTERN = Pattern.compile(
            "(?i)((?:password|token|authorization|apiKey|api-key|secret|accessKeyId|accessKeySecret)=)([^,)}\\s]+)");

    @Around("execution(* system.assessment.defense.interfaces.rest..*.*(..))") // 拦截 controller 包下的所有方法
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();
        List<Object> params = Stream.of(args).filter(item -> item != null
                && !(item instanceof HttpServletRequest)
                && !(item instanceof HttpServletResponse)
                && !(item instanceof MultipartFile)).toList();
        log.info("Method {}.{}() called with args: {}", className, methodName, sanitizeForLog(JSONUtil.toJsonStr(params)));

        long startTime = System.currentTimeMillis();
        Object result = null; // 执行目标方法
        try {
            result = joinPoint.proceed();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            String response = "LoginController".equals(className) && "login".equals(methodName)
                    ? "\"***\""
                    : sanitizeForLog(JSONUtil.toJsonStr(result));
            log.info("Method {}.{}() response {} executed in {}ms", className, methodName, response, duration);
        }
        return result;
    }

    static String sanitizeForLog(String content) {
        if (content == null) {
            return null;
        }
        String sanitized = JSON_SECRET_PATTERN.matcher(content).replaceAll("$1***$3");
        sanitized = TO_STRING_SECRET_PATTERN.matcher(sanitized).replaceAll("$1***");
        if (sanitized.length() <= MAX_LOG_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_LOG_LENGTH) + "...(truncated,length=" + sanitized.length() + ")";
    }
}
