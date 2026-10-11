package com.example.ragsystem.common;

import java.util.function.Supplier;

public final class RetryExecutor {

    private RetryExecutor() {
    }

    public static <T> T execute(String operation, int maxAttempts, long backoffMs, Supplier<T> action) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (NonRetryableException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt == maxAttempts) {
                    break;
                }
                try {
                    Thread.sleep(backoffMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RagException(operation + " interrupted", ie);
                }
            }
        }
        throw new RagException(operation + " failed after " + maxAttempts + " attempts", last);
    }
}
