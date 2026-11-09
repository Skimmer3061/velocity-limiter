package com.dkozel.velocitylimiter.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

@Component
@Profile("batch")
class LoadFileRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadFileRunner.class);

    private final LoadStreamProcessor processor;

    LoadFileRunner(LoadStreamProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path inputPath = pathOption(args, "input");
        Path outputPath = args.containsOption("output") ? pathOption(args, "output") : null;
        if (outputPath != null && Files.exists(outputPath) && Files.isSameFile(inputPath, outputPath)) {
            throw new IllegalArgumentException("--output must not be the same file as --input");
        }
        long started = System.nanoTime();
        BatchSummary summary;
        try (BufferedReader input = Files.newBufferedReader(inputPath)) {
            if (outputPath != null) {
                // answers go to a sibling temp file, renamed over the output only when the run completes
                Path temporary = outputPath.resolveSibling(outputPath.getFileName() + ".tmp");
                try {
                    try (Writer output = Files.newBufferedWriter(temporary)) {
                        summary = processor.process(input, output);
                    }
                    Files.move(temporary, outputPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } else {
                Writer stdout = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
                try {
                    summary = processor.process(input, stdout);
                } finally {
                    stdout.flush(); // flush, don't close: System.out isn't ours to close
                }
            }
        }
        log.info("Batch finished: input={}, lines={}, accepted={}, declined={}, duplicates={}, rejected={}, durationMs={}",
                inputPath, summary.lines(), summary.accepted(), summary.declined(), summary.duplicates(),
                summary.rejected(), Duration.ofNanos(System.nanoTime() - started).toMillis());
        if (summary.rejected() > 0) {
            throw new RejectedLinesException(summary.rejected());
        }
    }

    private static Path pathOption(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.size() != 1 || values.getFirst().isBlank()) {
            throw new IllegalArgumentException("--" + name + " needs exactly one file path");
        }
        return Path.of(values.getFirst());
    }
}
