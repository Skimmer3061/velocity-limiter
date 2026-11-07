package com.dkozel.velocitylimiter.batch;

import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.dkozel.velocitylimiter.domain.LoadDecision;
import com.dkozel.velocitylimiter.service.LoadService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LoadStreamProcessorTest {

    @Test
    void writesNothingForADuplicateAndContinues() throws IOException {
        LoadService service = mock(LoadService.class);
        when(service.process(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new LoadDecision("c", "2", true)));
        var processor = new LoadStreamProcessor(service, JsonMapper.builder().build());
        StringWriter output = new StringWriter();

        processor.process(new BufferedReader(new StringReader("""
                {"id":"1","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:00Z"}
                {"id":"2","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:01Z"}
                """)), output);

        assertThat(output).hasToString("""
                {"id":"2","customer_id":"c","accepted":true}
                """);
        verify(service).process(new LoadCommand("c", "1", new BigDecimal("1.00"), Instant.parse("2000-01-01T00:00:00Z")));
        verify(service).process(new LoadCommand("c", "2", new BigDecimal("1.00"), Instant.parse("2000-01-01T00:00:01Z")));
        verifyNoMoreInteractions(service);
    }
}
