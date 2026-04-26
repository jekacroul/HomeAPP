package com.example.demo.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.stream.Collectors;

@Aspect
@Component
public class MethodExecutionLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(MethodExecutionLoggingAspect.class);

    @Around("execution(* com.example.demo.controller..*(..)) || execution(* com.example.demo.service..*(..)) || execution(* com.example.demo.repository..*(..))")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        String args = Arrays.stream(joinPoint.getArgs())
            .map(this::safeToString)
            .collect(Collectors.joining(", "));

        long startedAt = System.currentTimeMillis();
        log.info("START {} args=[{}]", method, args);

        try {
            Object result = joinPoint.proceed();
            long executionMs = System.currentTimeMillis() - startedAt;
            log.info("DONE {} in {}ms result={}", method, executionMs, safeToString(result));
            return result;
        } catch (Throwable ex) {
            long executionMs = System.currentTimeMillis() - startedAt;
            log.error("FAIL {} in {}ms", method, executionMs, ex);
            throw ex;
        }
    }

    private String safeToString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof MultipartFile file) {
            return "MultipartFile(name=" + file.getOriginalFilename() + ", size=" + file.getSize() + ")";
        }

        String typeName = value.getClass().getName();
        if (typeName.startsWith("org.springframework.validation")
            || typeName.startsWith("org.springframework.ui")
            || typeName.startsWith("jakarta.servlet")
            || typeName.startsWith("org.apache.catalina")) {
            return value.getClass().getSimpleName();
        }

        String rendered = String.valueOf(value).replaceAll("\\s+", " ");
        if (rendered.length() > 250) {
            return rendered.substring(0, 250) + "...";
        }
        return rendered;
    }
}
