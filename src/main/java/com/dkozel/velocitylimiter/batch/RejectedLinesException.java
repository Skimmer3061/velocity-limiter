package com.dkozel.velocitylimiter.batch;

class RejectedLinesException extends BatchStoppedException {

    RejectedLinesException(BatchSummary summary) {
        super(summary.rejected() + " line(s) rejected, see the WARN log lines", summary, null);
    }

    RejectedLinesException(String message, BatchSummary summary) {
        super(message, summary, null);
    }
}
