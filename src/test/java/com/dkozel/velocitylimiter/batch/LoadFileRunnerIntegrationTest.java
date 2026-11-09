package com.dkozel.velocitylimiter.batch;

import com.dkozel.velocitylimiter.VelocityLimiterApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class LoadFileRunnerIntegrationTest {

    private static final String LOAD = """
            {"id":"1","customer_id":"runner-customer","load_amount":"$100.00","time":"2000-01-01T00:00:00Z"}
            """;
    private static final String ANSWER = """
            {"id":"1","customer_id":"runner-customer","accepted":true}
            """;

    @TempDir
    Path dir;

    @Test
    void writesAnswersToTheOutputFile() throws Exception {
        Path input = Files.writeString(dir.resolve("input.txt"), LOAD);
        Path output = dir.resolve("output.txt");

        runBatch("--input=" + input, "--output=" + output);

        assertThat(Files.readString(output)).isEqualTo(ANSWER);
    }

    @Test
    void writesOnlyAnswersToStdoutWhenNoOutputIsGiven(CapturedOutput captured) throws Exception {
        Path input = Files.writeString(dir.resolve("input.txt"), LOAD);

        runBatch("--input=" + input);

        // only the JSON lines: Boot sets up logging once per JVM, so if another test's context started first,
        // startup logs can share stdout here. "No logs on stdout" is checked by a real run.
        assertThat(captured.getOut().lines().filter(line -> line.startsWith("{")))
                .containsExactly(ANSWER.strip());
    }

    @Test
    void failsWithoutAnInputOption() {
        assertThatThrownBy(LoadFileRunnerIntegrationTest::runBatch)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--input needs exactly one file path");
    }

    @Test
    void rejectsAnInputOptionWithoutAPath() {
        assertThatThrownBy(() -> runBatch("--input"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--input needs exactly one file path");
    }

    @Test
    void refusesToOverwriteTheInputFile() throws Exception {
        Path input = Files.writeString(dir.resolve("input.txt"), LOAD);

        assertThatThrownBy(() -> runBatch("--input=" + input, "--output=" + input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--output must not be the same file as --input");
        assertThat(Files.readString(input)).isEqualTo(LOAD);
    }

    @Test
    void writesTheOtherAnswersThenFailsTheRunWhenALineWasRejected() throws Exception {
        Path input = Files.writeString(dir.resolve("input.txt"), "not json\n" + LOAD);
        Path output = dir.resolve("output.txt");

        // the run reports failure so a scheduler notices, but only after every valid line was answered
        assertThatThrownBy(() -> runBatch("--input=" + input, "--output=" + output))
                .isInstanceOf(RejectedLinesException.class)
                .hasMessage("1 line(s) rejected, see the WARN log lines");
        assertThat(Files.readString(output)).isEqualTo(ANSWER);
    }

    @Test
    void logsASummaryOfTheRun(CapturedOutput captured) throws Exception {
        Path input = Files.writeString(dir.resolve("input.txt"), LOAD + "\n" + LOAD);
        Path output = dir.resolve("output.txt");

        runBatch("--input=" + input, "--output=" + output);

        // key=value, so log tools can parse it; the blank line is read but not decided, the repeat is a duplicate
        assertThat(captured.getAll())
                .contains("Batch finished: input=" + input + ", lines=3, accepted=1, declined=0, duplicates=1, rejected=0, durationMs=");
    }

    // starts the real application the way `java -jar ... --spring.profiles.active=batch` does
    private static void runBatch(String... args) {
        new SpringApplicationBuilder(VelocityLimiterApplication.class)
                .profiles("batch")
                .run(args)
                .close();
    }
}
