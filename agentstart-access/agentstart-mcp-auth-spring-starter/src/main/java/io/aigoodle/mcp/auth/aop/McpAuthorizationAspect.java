package io.aigoodle.mcp.auth.aop;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.mcp.auth.annotation.McpAuthorize;
import io.aigoodle.mcp.auth.exception.McpAccessDeniedException;
import io.aigoodle.mcp.auth.exception.McpAuthenticationException;
import io.aigoodle.mcp.auth.spi.McpCredentialExtractor;
import io.aigoodle.mcp.auth.spi.McpTokenAuthenticator;
import io.aigoodle.mcp.auth.support.McpCredential;
import io.aigoodle.mcp.auth.support.McpCredentialContext;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;

@Aspect
public final class McpAuthorizationAspect {

    private final McpCredentialExtractor credentialExtractor;
    private final McpTokenAuthenticator tokenAuthenticator;

    public McpAuthorizationAspect(
            McpCredentialExtractor credentialExtractor, McpTokenAuthenticator tokenAuthenticator) {
        this.credentialExtractor = credentialExtractor;
        this.tokenAuthenticator = tokenAuthenticator;
    }

    @Around("@annotation(io.aigoodle.mcp.auth.annotation.McpAuthorize) || "
            + "@within(io.aigoodle.mcp.auth.annotation.McpAuthorize)")
    public Object authorize(ProceedingJoinPoint joinPoint) throws Throwable {
        McpAuthorize rule = rule(joinPoint);
        McpCredential credential = credentialExtractor
                .extract(joinPoint.getArgs())
                .orElseThrow(() -> new McpAuthenticationException("Missing MCP Authorization credential"));
        CurrentUser caller = tokenAuthenticator.authenticate(credential.token());
        if (caller == null) {
            throw new McpAuthenticationException("McpTokenAuthenticator returned no caller");
        }
        verify(rule, caller);
        try (UserContextHolder.ContextScope ignored = UserContextHolder.openScope(caller);
                McpCredentialContext.Scope credentialScope = McpCredentialContext.open(credential)) {
            return joinPoint.proceed();
        }
    }

    private McpAuthorize rule(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        McpAuthorize methodRule = AnnotatedElementUtils.findMergedAnnotation(method, McpAuthorize.class);
        if (methodRule != null) return methodRule;
        McpAuthorize classRule =
                AnnotatedElementUtils.findMergedAnnotation(joinPoint.getTarget().getClass(), McpAuthorize.class);
        if (classRule == null) throw new IllegalStateException("MCP authorization rule not found");
        return classRule;
    }

    private void verify(McpAuthorize rule, CurrentUser caller) {
        verifyValues("role", rule.roles(), caller.getRoles(), rule.roleMatch());
        verifyValues("scope", rule.scopes(), caller.getScopes(), rule.scopeMatch());
    }

    private void verifyValues(String kind, String[] required, Set<String> actual, McpAuthorize.Match match) {
        if (required.length == 0) return;
        Set<String> available = actual == null ? Set.of() : actual;
        boolean granted = match == McpAuthorize.Match.ALL
                ? Arrays.stream(required).allMatch(available::contains)
                : Arrays.stream(required).anyMatch(available::contains);
        if (!granted) {
            throw new McpAccessDeniedException("MCP caller lacks required " + kind + " permission");
        }
    }
}
