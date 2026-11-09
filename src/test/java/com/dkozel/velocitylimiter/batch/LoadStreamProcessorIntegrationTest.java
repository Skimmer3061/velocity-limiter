package com.dkozel.velocitylimiter.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class LoadStreamProcessorIntegrationTest {

    @Autowired
    LoadStreamProcessor processor;

    @Test
    void writesOneAnswerPerLoadInInputOrderAndSkipsBlankLines() throws IOException {
        String input = """
                {"id":"1","customer_id":"stream-customer","load_amount":"$100.00","time":"2000-01-01T00:00:00Z"}

                {"id":"2","customer_id":"stream-customer","load_amount":"$200.00","time":"2000-01-01T01:00:00Z"}
                """;
        StringWriter output = new StringWriter();

        BatchSummary summary = processor.process(new BufferedReader(new StringReader(input)), output);

        assertThat(output).hasToString("""
                {"id":"1","customer_id":"stream-customer","accepted":true}
                {"id":"2","customer_id":"stream-customer","accepted":true}
                """);
        assertThat(summary).isEqualTo(new BatchSummary(3, 2, 0, 0, 0)); // the blank line is read, not decided
    }

    @Test
    void countsATimeWithAnOffsetTowardItsUtcDay() throws IOException {
        // 23:30 at UTC-2 on 1 January is 01:30 UTC on 2 January, so it shares 2 January's $5,000 with the first load
        StringWriter output = new StringWriter();

        processor.process(new BufferedReader(new StringReader("""
                {"id":"1","customer_id":"offset-customer","load_amount":"$5000.00","time":"2000-01-02T00:30:00Z"}
                {"id":"2","customer_id":"offset-customer","load_amount":"$1.00","time":"2000-01-01T23:30:00-02:00"}
                """)), output);

        assertThat(output).hasToString("""
                {"id":"1","customer_id":"offset-customer","accepted":true}
                {"id":"2","customer_id":"offset-customer","accepted":false}
                """);
    }

    @Test
    void rejectsATimeWithoutAnOffset(CapturedOutput log) throws IOException {
        // without an offset the instant is unknown; guessing a zone would put the load on the wrong day
        BatchSummary summary = processor.process(new BufferedReader(new StringReader("""
                {"id":"1","customer_id":"no-offset-customer","load_amount":"$1.00","time":"2000-01-01T10:00:00"}
                """)), new StringWriter());

        assertThat(summary.rejected()).isEqualTo(1);
        assertThat(log.getAll()).contains("Line 1 rejected: time has a value of the wrong type or format");
    }
}
