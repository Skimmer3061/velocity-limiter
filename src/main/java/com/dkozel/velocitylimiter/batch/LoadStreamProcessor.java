package com.dkozel.velocitylimiter.batch;

import com.dkozel.velocitylimiter.api.JsonFieldNames;
import com.dkozel.velocitylimiter.api.LoadRequest;
import com.dkozel.velocitylimiter.api.LoadResponse;
import com.dkozel.velocitylimiter.domain.LoadDecision;
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

    LoadStreamProcessor(LoadService loadService, JsonMapper jsonMapper, Validator validator, BatchProperties batchProperties) {
        this.loadService = loadService;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
        this.maxRejectedLines = batchProperties.maxRejectedLines();
    }

    public BatchSummary process(BufferedReader input, Writer output) throws IOException {
        long lines = 0, accepted = 0, declined = 0, duplicates = 0, rejected = 0;
        String line;
        while ((line = input.readLine()) != null) {
            lines++;
            if (line.isBlank()) {
                continue;
            }
            Optional<LoadRequest> request = read(line, lines);
            if (request.isEmpty()) {
                rejected++;
                if (rejected > maxRejectedLines) {
                    throw new RejectedLinesException("over " + maxRejectedLines
                            + " lines rejected (batch.max-rejected-lines), processing stopped at line " + lines);
                }
                continue;
            }
            Optional<LoadDecision> decision = loadService.process(request.get().toCommand());
            if (decision.isEmpty()) {
                duplicates++;
                continue;
            }
            if (decision.get().accepted()) {
                accepted++;
            } else {
                declined++;
            }
            output.write(jsonMapper.writeValueAsString(LoadResponse.from(decision.get())));
            output.write('\n');
        }
        output.flush();
        return new BatchSummary(lines, accepted, declined, duplicates, rejected);
    }

    private Optional<LoadRequest> read(String line, long lineNumber) {
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
        log.warn("Line {} rejected: {}; line: {}", lineNumber, reason, trim(line));
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

    private static String trim(String line) {
        return line.length() <= LOGGED_LINE_MAX_LENGTH ? line : line.substring(0, LOGGED_LINE_MAX_LENGTH) + "…";
    }
}
