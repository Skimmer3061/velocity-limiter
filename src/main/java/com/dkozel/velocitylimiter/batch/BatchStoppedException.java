package com.dkozel.velocitylimiter.batch;

abstract class BatchStoppedException extends RuntimeException {

    private final BatchSummary summary;

    BatchStoppedException(String message, BatchSummary summary, Throwable cause) {
        super(message, cause);
        this.summary = summary;
    }

    BatchSummary summary() {
        return summary;
    }
}
