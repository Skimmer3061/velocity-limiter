package com.dkozel.velocitylimiter.batch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
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

        processor.process(new BufferedReader(new StringReader(input)), output);

        assertThat(output).hasToString("""
                {"id":"1","customer_id":"stream-customer","accepted":true}
                {"id":"2","customer_id":"stream-customer","accepted":true}
                """);
    }
}
