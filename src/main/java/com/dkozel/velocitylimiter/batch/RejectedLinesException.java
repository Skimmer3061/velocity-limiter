package com.dkozel.velocitylimiter.batch;

class RejectedLinesException extends RuntimeException {

    RejectedLinesException(long rejected) {
        super(rejected + " line(s) rejected, see the WARN log lines");
    }
}
