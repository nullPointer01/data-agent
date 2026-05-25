package com.ai.event;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when a model configuration changes.
 *
 * @author data-agent
 */
public class ModelConfigChangeEvent extends ApplicationEvent {

    private final String modelId;
    private final ChangeType changeType;

    public enum ChangeType {
        /** Model configuration was updated. */
        UPDATED,

        /** Model configuration was deleted. */
        DELETED,

        /** Model configuration enabled state changed. */
        TOGGLED
    }

    public ModelConfigChangeEvent(Object source, String modelId, ChangeType changeType) {
        super(source);
        this.modelId = modelId;
        this.changeType = changeType;
    }

    public String getModelId() {
        return modelId;
    }

    public ChangeType getChangeType() {
        return changeType;
    }
}
