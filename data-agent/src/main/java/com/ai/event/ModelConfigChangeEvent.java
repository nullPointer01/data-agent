package com.ai.event;

import org.springframework.context.ApplicationEvent;

public class ModelConfigChangeEvent extends ApplicationEvent {

    private final String modelId;
    private final ChangeType changeType;

    public enum ChangeType {
        UPDATED, DELETED, TOGGLED
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
