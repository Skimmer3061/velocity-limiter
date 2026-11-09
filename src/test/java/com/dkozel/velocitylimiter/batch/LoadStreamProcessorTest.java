package com.dkozel.velocitylimiter.batch;

import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.dkozel.velocitylimiter.domain.LoadDecision;
import com.dkozel.velocitylimiter.domain.LoadOutcome;
import com.dkozel.velocitylimiter.service.LoadService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class LoadStreamProcessorTest {

    @AutoClose
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    // as configured for the app (spring.jackson.deserialization.fail-on-unknown-properties)
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final LoadService service = mock(LoadService.class);

    private final LoadStreamProcessor processor =
            new LoadStreamProcessor(service, JSON, VALIDATOR, new BatchProperties(100L, 10_000));

    @Test
    void writesNothingForARepeatedIdAndContinues() throws IOException {
        // the task: a repeated id gets no answer, whether it is a retry or the same id with other data
        when(service.process(any()))
                .thenReturn(new LoadOutcome.Replayed(new LoadDecision("c", "1", true)))
                .thenReturn(new LoadOutcome.Conflicting(new LoadDecision("c", "1", true)))
                .thenReturn(new LoadOutcome.Decided(new LoadDecision("c", "2", true)));
        StringWriter output = new StringWriter();

        BatchSummary summary = processor.process(reader("""
                {"id":"1","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:00Z"}
                {"id":"1","customer_id":"c","load_amount":"$2.00","time":"2000-01-01T00:00:00Z"}
                {"id":"2","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:01Z"}
                """), output);

        assertThat(output).hasToString("""
                {"id":"2","customer_id":"c","accepted":true}
                """);
        assertThat(summary).isEqualTo(new BatchSummary(3, 1, 0, 2, 0));
        verify(service).process(new LoadCommand("c", "1", new BigDecimal("1.00"), Instant.parse("2000-01-01T00:00:00Z")));
        verify(service).process(new LoadCommand("c", "1", new BigDecimal("2.00"), Instant.parse("2000-01-01T00:00:00Z")));
        verify(service).process(new LoadCommand("c", "2", new BigDecimal("1.00"), Instant.parse("2000-01-01T00:00:01Z")));
        verifyNoMoreInteractions(service);
    }

    @Test
    void skipsEachBadLineWithAWarningAndDecidesTheRest(CapturedOutput log) throws IOException {
        when(service.process(any()))
                .thenReturn(new LoadOutcome.Decided(new LoadDecision("c", "1", true)))
                .thenReturn(new LoadOutcome.Decided(new LoadDecision("c", "5", false)));
        StringWriter output = new StringWriter();

        BatchSummary summary = processor.process(reader("""
                {"id":"1","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:00Z"}
                {"id":"2","customer_id":"c","load_amount":"$0.00","time":"2000-01-01T00:00:01Z"}
                not json
                {"id":"4","customer_id":"c","loadamount":"$1.00","time":"2000-01-01T00:00:03Z"}
                {"id":"5","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:04Z"}
                """), output);

        // a bad line gets no answer line; the lines around it are decided as usual
        assertThat(output).hasToString("""
                {"id":"1","customer_id":"c","accepted":true}
                {"id":"5","customer_id":"c","accepted":false}
                """);
        assertThat(summary).isEqualTo(new BatchSummary(5, 1, 1, 0, 3));
        verify(service).process(new LoadCommand("c", "1", new BigDecimal("1.00"), Instant.parse("2000-01-01T00:00:00Z")));
        verify(service).process(new LoadCommand("c", "5", new BigDecimal("1.00"), Instant.parse("2000-01-01T00:00:04Z")));
        verifyNoMoreInteractions(service);
        // the reason names the field as written in the file
        assertThat(log.getAll())
                .contains("Line 2 rejected: load_amount must be a positive dollar amount with exactly 2 decimals")
                .contains("Line 3 rejected: not valid JSON")
                .contains("Line 4 rejected: unknown field loadamount");
    }

    @Test
    void ignoresAByteOrderMarkAtTheStartOfTheFile() throws IOException {
        when(service.process(any())).thenReturn(new LoadOutcome.Decided(new LoadDecision("c", "1", true)));
        StringWriter output = new StringWriter();

        // some Windows tools start a UTF-8 file with U+FEFF; it is not part of the first load
        BatchSummary summary = processor.process(reader("\uFEFF" + """
                {"id":"1","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:00Z"}
                """), output);

        assertThat(summary.rejected()).isZero();
        assertThat(output).hasToString("""
                {"id":"1","customer_id":"c","accepted":true}
                """);
        verify(service).process(new LoadCommand("c", "1", new BigDecimal("1.00"), Instant.parse("2000-01-01T00:00:00Z")));
        verifyNoMoreInteractions(service);
    }

    @Test
    void countsAFirstLineWithOnlyAByteOrderMarkAsBlank() throws IOException {
        when(service.process(any())).thenReturn(new LoadOutcome.Decided(new LoadDecision("c", "1", true)));

        // the mark is removed before the blank check, so the line is not rejected as broken JSON
        BatchSummary summary = processor.process(reader("\uFEFF\n" + """
                {"id":"1","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:00Z"}
                """), new StringWriter());

        assertThat(summary).isEqualTo(new BatchSummary(2, 1, 0, 0, 0));
    }

    @Test
    void rejectsAByteOrderMarkInsideTheFile(CapturedOutput log) throws IOException {
        when(service.process(any())).thenReturn(new LoadOutcome.Decided(new LoadDecision("c", "1", true)));

        // only the very start of a file can carry a byte order mark; anywhere else the line is damaged
        BatchSummary summary = processor.process(reader("""
                {"id":"1","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:00Z"}
                """ + "\uFEFF" + """
                {"id":"2","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:01Z"}
                """), new StringWriter());

        assertThat(summary.rejected()).isEqualTo(1);
        assertThat(log.getAll()).contains("Line 2 rejected: not valid JSON");
        verify(service).process(new LoadCommand("c", "1", new BigDecimal("1.00"), Instant.parse("2000-01-01T00:00:00Z")));
        verifyNoMoreInteractions(service);
    }

    @Test
    void rejectsALineThatWasNotValidUtf8(CapturedOutput log) throws IOException {
        // the runner decodes bad bytes as U+FFFD; such a line is damaged, so it must not be decided
        BatchSummary summary = processor.process(reader("""
                {"id":"1","customer_id":"Jos\uFFFD","load_amount":"$1.00","time":"2000-01-01T00:00:00Z"}
                """), new StringWriter());

        assertThat(summary.rejected()).isEqualTo(1);
        assertThat(log.getAll()).contains("Line 1 rejected: not valid UTF-8");
        verifyNoInteractions(service);
    }

    @Test
    void explainsAValueOfTheWrongType(CapturedOutput log) throws IOException {
        processor.process(reader("""
                {"id":"1","customer_id":"c","load_amount":"$1.00","time":"yesterday"}
                """), new StringWriter());

        assertThat(log.getAll()).contains("Line 1 rejected: time has a value of the wrong type or format");
    }

    @Test
    void listsEveryViolationOfALine(CapturedOutput log) throws IOException {
        processor.process(reader("""
                {"id":"","customer_id":"c","load_amount":"$0.00","time":"2000-01-01T00:00:00Z"}
                """), new StringWriter());

        // sorted by field, so the same line always gives the same message
        assertThat(log.getAll()).contains("Line 1 rejected: id must not be blank; load_amount must be a positive");
    }

    @Test
    void rejectsALineThatIsValidJsonButNotALoad(CapturedOutput log) throws IOException {
        BatchSummary summary = processor.process(reader("null\n"), new StringWriter());

        assertThat(summary).isEqualTo(new BatchSummary(1, 0, 0, 0, 1));
        assertThat(log.getAll()).contains("Line 1 rejected: not a load");
        verifyNoInteractions(service);
    }

    @Test
    void logsOnlyTheStartOfALongBadLine(CapturedOutput log) throws IOException {
        processor.process(reader("x".repeat(1100) + "\n" + "y".repeat(1000) + "\n"), new StringWriter());

        assertThat(log.getAll()).contains("x".repeat(1000) + "…").doesNotContain("x".repeat(1001));
        // exactly at the limit: logged whole, no ellipsis
        assertThat(log.getAll()).contains("y".repeat(1000)).doesNotContain("y".repeat(1000) + "…");
    }

    @Test
    void rejectsALineOverTheLengthLimitAndDecidesTheNext(CapturedOutput log) throws IOException {
        when(service.process(any())).thenReturn(new LoadOutcome.Decided(new LoadDecision("c", "1", true)));
        LoadStreamProcessor shortLines = new LoadStreamProcessor(service, JSON, VALIDATOR, new BatchProperties(100L, 100));

        BatchSummary summary = shortLines.process(reader("x".repeat(101) + "\n" + """
                {"id":"1","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:00Z"}
                """), new StringWriter());

        assertThat(summary).isEqualTo(new BatchSummary(2, 1, 0, 0, 1));
        assertThat(log.getAll()).contains("Line 1 rejected: longer than 100 characters (batch.max-line-length)");
        verify(service).process(new LoadCommand("c", "1", new BigDecimal("1.00"), Instant.parse("2000-01-01T00:00:00Z")));
        verifyNoMoreInteractions(service);
    }

    @Test
    void allowsAsManyBadLinesAsTheLimit() throws IOException {
        LoadStreamProcessor limitOfTwo = new LoadStreamProcessor(service, JSON, VALIDATOR, new BatchProperties(2L, 10_000));

        BatchSummary summary = limitOfTwo.process(reader("""
                not json
                not json
                """), new StringWriter());

        assertThat(summary.rejected()).isEqualTo(2);
    }

    @Test
    void stopsAtTheFirstBadLineOverTheLimit() {
        when(service.process(any())).thenReturn(new LoadOutcome.Decided(new LoadDecision("c", "1", true)));
        LoadStreamProcessor limitOfTwo = new LoadStreamProcessor(service, JSON, VALIDATOR, new BatchProperties(2L, 10_000));

        // a wrong or corrupt file fails fast instead of logging a WARN for every line
        assertThatThrownBy(() -> limitOfTwo.process(reader("""
                {"id":"1","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:00Z"}
                not json
                not json
                not json
                {"id":"5","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:04Z"}
                """), new StringWriter()))
                .isInstanceOf(RejectedLinesException.class)
                .hasMessage("over 2 lines rejected (batch.max-rejected-lines), processing stopped at line 4")
                // the counts up to the stop, so the runner can still log a summary
                .isInstanceOfSatisfying(RejectedLinesException.class,
                        stopped -> assertThat(stopped.summary()).isEqualTo(new BatchSummary(4, 1, 0, 0, 3)));
        verify(service).process(new LoadCommand("c", "1", new BigDecimal("1.00"), Instant.parse("2000-01-01T00:00:00Z")));
        verifyNoMoreInteractions(service);
    }

    @Test
    void stopsWhenTheServiceFailsAndNamesTheLine() {
        DataAccessResourceFailureException databaseDown = new DataAccessResourceFailureException("database down");
        when(service.process(any()))
                .thenReturn(new LoadOutcome.Decided(new LoadDecision("c", "1", true)))
                .thenThrow(databaseDown);

        // a system failure is not a bad line: continuing could decide the next loads on a broken database
        assertThatThrownBy(() -> processor.process(reader("""
                {"id":"1","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:00Z"}
                {"id":"2","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:01Z"}
                {"id":"3","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:02Z"}
                """), new StringWriter()))
                .isInstanceOf(BatchProcessingException.class)
                .hasMessage("Processing stopped at line 2")
                .isInstanceOfSatisfying(BatchProcessingException.class,
                        stopped -> assertThat(stopped.summary()).isEqualTo(new BatchSummary(2, 1, 0, 0, 0)))
                .cause().isSameAs(databaseDown); // the original error, stack trace included, is kept
        verify(service).process(new LoadCommand("c", "1", new BigDecimal("1.00"), Instant.parse("2000-01-01T00:00:00Z")));
        verify(service).process(new LoadCommand("c", "2", new BigDecimal("1.00"), Instant.parse("2000-01-01T00:00:01Z")));
        verifyNoMoreInteractions(service);
    }

    private static BufferedReader reader(String text) {
        return new BufferedReader(new StringReader(text));
    }
}
