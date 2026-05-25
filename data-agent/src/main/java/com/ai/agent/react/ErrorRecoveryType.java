package com.ai.agent.react;

/**
 * Tool recovery category used for ReAct self-correction.
 *
 * @author data-agent
 */
public enum ErrorRecoveryType {

    /**
     * Tool result does not need a special recovery instruction.
     */
    NONE,

    /**
     * The model selected a tool that does not exist.
     */
    UNKNOWN_TOOL,

    /**
     * Required tool arguments are missing or malformed.
     */
    ARGUMENT_ERROR,

    /**
     * A data or knowledge lookup returned no available data.
     */
    NO_DATA,

    /**
     * SQL execution failed and the model should inspect schema or simplify query.
     */
    SQL_ERROR,

    /**
     * The provider or downstream dependency timed out.
     */
    TIMEOUT,

    /**
     * Generic tool execution failure.
     */
    TOOL_FAILURE
}
