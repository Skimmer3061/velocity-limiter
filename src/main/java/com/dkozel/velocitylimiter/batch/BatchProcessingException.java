package com.dkozel.velocitylimiter.batch;

class BatchProcessingException extends RuntimeException {

    BatchProcessingException(long lineNumber, RuntimeException cause) {
        super("Processing stopped at line " + lineNumber, cause);
    }
}
