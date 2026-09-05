package com.ai.agent.tool.governance;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明模型可调用工具不可缺失的服务端治理属性。
 *
 * @author data-agent
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentToolPolicy {

    AgentToolRiskLevel risk();

    boolean readOnly();

    boolean idempotent();

    boolean retryable();

    long timeoutMs();

    int maxAttempts();

    String requiredPermission();

    boolean approvalRequired() default false;

    String approvalPermission() default "";

    int maxResultLength();
}
