package com.dkozel.velocitylimiter.batch;

import com.dkozel.velocitylimiter.VelocityLimiterApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDataReplayEndToEndTest {

    @TempDir
    Path dir;

    @Test
    void replaysTheTaskInputToExactlyTheExpectedOutput() throws Exception {
        Path output = dir.resolve("output.txt");

        new SpringApplicationBuilder(VelocityLimiterApplication.class)
                .profiles("batch")
                .run("--input=" + resource("/input.txt"), "--output=" + output)
                .close();

        List<String> expected = Files.readAllLines(resource("/output.txt"));
        assertThat(expected).hasSize(999); // sanity check: the full expected file, not a truncated copy
        // line by line on purpose: the task's output.txt uses CRLF, we write LF
        assertThat(Files.readAllLines(output)).containsExactlyElementsOf(expected);
    }

    private static Path resource(String name) throws Exception {
        return Path.of(TaskDataReplayEndToEndTest.class.getResource(name).toURI());
    }
}
