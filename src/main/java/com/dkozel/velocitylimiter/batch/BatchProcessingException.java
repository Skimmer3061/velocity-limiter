package com.dkozel.velocitylimiter.batch;

class BatchProcessingException extends BatchStoppedException {

    BatchProcessingException(long lineNumber, BatchSummary summary, RuntimeException cause) {
        super("Processing stopped at line " + lineNumber, summary, cause);
    }
}
