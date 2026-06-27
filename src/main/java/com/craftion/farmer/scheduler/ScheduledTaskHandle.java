package com.craftion.farmer.scheduler;

public interface ScheduledTaskHandle {

    void cancel();

    boolean isCancelled();

    static ScheduledTaskHandle cancelled() {
        return CancelledScheduledTaskHandle.INSTANCE;
    }

    enum CancelledScheduledTaskHandle implements ScheduledTaskHandle {
        INSTANCE;

        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    }
}
