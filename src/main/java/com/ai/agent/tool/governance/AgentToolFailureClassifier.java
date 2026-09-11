package com.ai.agent.tool.governance;

import com.ai.agent.capability.AgentDelegationGuard;
import com.ai.agent.runtime.AgentRunTerminatedException;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.HttpRetryException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 以默认不可重试策略分类工具异常。
 *
 * @author data-agent
 */
@Component
public class AgentToolFailureClassifier {

    private static final Pattern HTTP_STATUS = Pattern.compile("(?<!\\d)([45]\\d{2})(?!\\d)");

    public AgentToolFailure classify(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        Throwable runTermination = findCause(cause, AgentRunTerminatedException.class);
        if (runTermination != null) {
            return new AgentToolFailure(AgentToolExecutionStatus.RUN_TERMINATED, false, "Agent Run 已终止");
        }
        AgentDelegationGuard.DelegationRejectedException delegationRejection =
                (AgentDelegationGuard.DelegationRejectedException) findCause(
                        cause, AgentDelegationGuard.DelegationRejectedException.class);
        if (delegationRejection != null) {
            return new AgentToolFailure(
                    AgentToolExecutionStatus.POLICY_DENIED,
                    false,
                    delegationRejection.safeMessage());
        }
        if (findCause(cause, TimeoutException.class) != null
                || findCause(cause, SocketTimeoutException.class) != null
                || findCause(cause, HttpTimeoutException.class) != null) {
            return new AgentToolFailure(AgentToolExecutionStatus.TIMEOUT, true, "工具调用超时");
        }
        if (findCause(cause, ConnectException.class) != null || findCause(cause, SocketException.class) != null) {
            return new AgentToolFailure(AgentToolExecutionStatus.EXECUTION_FAILED, true, "工具连接暂时不可用");
        }
        if (findCause(cause, IllegalArgumentException.class) != null) {
            return new AgentToolFailure(AgentToolExecutionStatus.INVALID_ARGUMENTS, false, "工具参数不符合业务约束");
        }
        if (cause instanceof HttpRetryException retryException) {
            int status = retryException.responseCode();
            return httpFailure(status);
        }
        Integer status = extractHttpStatus(cause);
        if (status != null) {
            return httpFailure(status);
        }
        return new AgentToolFailure(AgentToolExecutionStatus.EXECUTION_FAILED, false, "工具执行失败");
    }

    private AgentToolFailure httpFailure(int status) {
        boolean retriable = status == 429 || status >= 500;
        String message = retriable ? "工具服务暂时不可用" : "工具服务拒绝了请求";
        return new AgentToolFailure(AgentToolExecutionStatus.EXECUTION_FAILED, retriable, message);
    }

    private Integer extractHttpStatus(Throwable throwable) {
        String text = ((throwable == null ? "" : throwable.getClass().getSimpleName()) + " "
                + (throwable == null || throwable.getMessage() == null ? "" : throwable.getMessage()))
                .toLowerCase(Locale.ROOT);
        Matcher matcher = HTTP_STATUS.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable == null ? new IllegalStateException("未知工具异常") : throwable;
        while ((current instanceof ExecutionException || current.getClass().getSimpleName().equals("ToolExecutionException"))
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private Throwable findCause(Throwable throwable, Class<? extends Throwable> expectedType) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }
}
