package com.dkozel.velocitylimiter.batch;

public record BatchSummary(long lines, long accepted, long declined, long duplicates, long rejected) {
}
