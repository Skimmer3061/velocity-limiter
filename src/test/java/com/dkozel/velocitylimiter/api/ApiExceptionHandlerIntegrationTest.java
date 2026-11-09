package com.dkozel.velocitylimiter.api;

import com.dkozel.velocitylimiter.service.LoadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// the service is mocked so a lock timeout or a crash can be produced on demand
@WebMvcTest(LoadController.class)
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerIntegrationTest {

    private static final String VALID_LOAD = """
            {"id":"1","customer_id":"c","load_amount":"$1.00","time":"2000-01-01T00:00:00Z"}
            """;

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    LoadService loadService;

    // QueryTimeoutException is what a real H2 lock timeout becomes; any transient data access failure is a 503
    @ParameterizedTest
    @ValueSource(classes = {QueryTimeoutException.class, CannotAcquireLockException.class})
    void asksTheClientToRetryWhenTheCustomerIsBusy(Class<? extends TransientDataAccessException> timeout) throws Exception {
        when(loadService.process(any())).thenThrow(timeout.getConstructor(String.class).newInstance("lock timeout"));

        assertThat(postValidLoad()).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .hasHeader(HttpHeaders.RETRY_AFTER, "1")
                .bodyJson().isLenientlyEqualTo("""
                        {"status": 503, "detail": "Another load for this customer is in progress; retry shortly."}
                        """);
    }

    @Test
    void hidesTheCauseOfAnUnexpectedFailureAndLogsItOnce(CapturedOutput output) {
        when(loadService.process(any())).thenThrow(new IllegalStateException("internal detail"));

        MvcTestResult response = postValidLoad();

        assertThat(response).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().isLenientlyEqualTo("""
                        {"status": 500, "detail": "An unexpected error occurred."}
                        """);
        assertThat(response).bodyText().doesNotContain("internal detail").doesNotContain("IllegalStateException");
        // one ERROR with the stack trace, so the cause is findable once and not repeated per layer
        assertThat(output.getAll()).containsOnlyOnce("java.lang.IllegalStateException: internal detail");
    }

    private MvcTestResult postValidLoad() {
        return mvc.post().uri("/loads").contentType(MediaType.APPLICATION_JSON).content(VALID_LOAD).exchange();
    }
}
