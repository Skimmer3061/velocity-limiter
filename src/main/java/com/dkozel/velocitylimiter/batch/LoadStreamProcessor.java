package com.dkozel.velocitylimiter.batch;

import com.dkozel.velocitylimiter.api.JsonFieldNames;
import com.dkozel.velocitylimiter.api.LoadRequest;
import com.dkozel.velocitylimiter.api.LoadResponse;
import com.dkozel.velocitylimiter.domain.LoadDecision;
import com.dkozel.velocitylimiter.domain.LoadOutcome;
import com.dkozel.velocitylimiter.service.LoadService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LoadStreamProcessor {

    private static final Logger log = LoggerFactory.getLogger(LoadStreamProcessor.class);
    private static final int LOGGED_LINE_MAX_LENGTH = 1000;

    private final LoadService loadService;
    private final JsonMapper jsonMapper;
    private final Validator validator;
    private final long maxRejectedLines;
    private final int maxLineLength;

    LoadStreamProcessor(LoadService loadService, JsonMapper jsonMapper, Validator validator, BatchProperties batchProperties) {
        this.loadService = loadService;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
        this.maxRejectedLines = batchProperties.maxRejectedLines();
        this.maxLineLength = batchProperties.maxLineLength();
    }

    public BatchSummary process(BufferedReader input, Writer output) throws IOException {
        long lines = 0;
        long accepted = 0;
        long declined = 0;
        long duplicates = 0;
        long rejected = 0;
        BoundedLineReader lineReader = new BoundedLineReader(input, maxLineLength);
        BoundedLineReader.Line next;
        while ((next = lineReader.next()) != null) {
            lines++;
            String line = next.text();
            if (lines == 1 && line.startsWith("\uFEFF")) {
                // a UTF-8 byte order mark that some Windows tools write; not part of the data
                line = line.substring(1);
            }
            if (!next.tooLong() && line.isBlank()) {
                continue;
            }
            Optional<LoadRequest> request = next.tooLong()
                    ? reject(lines, "longer than " + maxLineLength + " characters (batch.max-line-length)", line)
                    : read(line, lines);
            if (request.isEmpty()) {
                rejected++;
                if (rejected > maxRejectedLines) {
                    throw new RejectedLinesException("over " + maxRejectedLines
                            + " lines rejected (batch.max-rejected-lines), processing stopped at line " + lines,
                            new BatchSummary(lines, accepted, declined, duplicates, rejected));
                }
                continue;
            }
            LoadOutcome outcome;
            try {
                outcome = loadService.process(request.get().toCommand());
            } catch (RuntimeException failure) {
                throw new BatchProcessingException(lines, new BatchSummary(lines, accepted, declined, duplicates, rejected), failure);
            }
            if (outcome instanceof LoadOutcome.Conflicting) {
                log.warn("Line {}: load id already used for this customer with a different amount or time, ignored", lines);
            }
            // the task: a repeated id gets no answer, whether it is a retry or carries other data
            if (!(outcome instanceof LoadOutcome.Decided(LoadDecision decision))) {
                duplicates++;
                continue;
            }
            if (decision.accepted()) {
                accepted++;
            } else {
                declined++;
            }
            output.write(jsonMapper.writeValueAsString(LoadResponse.from(decision)));
            output.write('\n');
        }
        output.flush();
        return new BatchSummary(lines, accepted, declined, duplicates, rejected);
    }

    private Optional<LoadRequest> read(String line, long lineNumber) {
        // a byte that wasn't UTF-8 was decoded as U+FFFD: the line is damaged and must not be decided
        if (line.indexOf('\uFFFD') >= 0) {
            return reject(lineNumber, "not valid UTF-8", line);
        }
        String reason;
        try {
            LoadRequest request = jsonMapper.readValue(line, LoadRequest.class);
            if (request == null) {
                reason = "not a load";
            } else {
                Set<ConstraintViolation<LoadRequest>> violations = validator.validate(request);
                if (violations.isEmpty()) {
                    return Optional.of(request);
                }
                reason = violations.stream()
                        .map(v -> JsonFieldNames.of(LoadRequest.class, v.getPropertyPath().toString()) + " " + v.getMessage())
                        .sorted()
                        .collect(Collectors.joining("; "));
            }
        } catch (JacksonException unreadable) {
            reason = reasonFor(unreadable);
        }
        return reject(lineNumber, reason, line);
    }

    private static Optional<LoadRequest> reject(long lineNumber, String reason, String line) {
        log.warn("Line {} rejected: {}; line: {}", lineNumber, reason, truncate(line));
        return Optional.empty();
    }

    private static String reasonFor(JacksonException unreadable) {
        if (unreadable instanceof DatabindException mapping && !mapping.getPath().isEmpty()) {
            String field = mapping.getPath().stream()
                    .map(JacksonException.Reference::getPropertyName)
                    .collect(Collectors.joining("."));
            return unreadable instanceof UnrecognizedPropertyException
                    ? "unknown field " + field
                    : field + " has a value of the wrong type or format";
        }
        return "not valid JSON";
    }

    private static String truncate(String line) {
        return line.length() <= LOGGED_LINE_MAX_LENGTH ? line : line.substring(0, LOGGED_LINE_MAX_LENGTH) + "…";
    }
}
