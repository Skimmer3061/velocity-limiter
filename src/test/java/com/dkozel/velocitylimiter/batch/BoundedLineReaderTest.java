package com.dkozel.velocitylimiter.batch;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedLineReaderTest {

    @Test
    void splitsLinesLikeReadLine() throws IOException {
        // \n, \r\n and a lone \r all end a line; a last line without a terminator still counts
        assertThat(readAll("a\nb\r\nc\rd\n\ne", 10)).containsExactly(
                new BoundedLineReader.Line("a", false),
                new BoundedLineReader.Line("b", false),
                new BoundedLineReader.Line("c", false),
                new BoundedLineReader.Line("d", false),
                new BoundedLineReader.Line("", false),
                new BoundedLineReader.Line("e", false));
    }

    @Test
    void returnsNothingForEmptyInput() throws IOException {
        assertThat(readAll("", 10)).isEmpty();
    }

    @Test
    void keepsALineOfExactlyTheLimit() throws IOException {
        assertThat(readAll("12345\n", 5)).containsExactly(new BoundedLineReader.Line("12345", false));
    }

    @Test
    void marksALongerLineKeepsOnlyItsStartAndReadsTheNextLine() throws IOException {
        assertThat(readAll("123456789\r\nnext\n", 5)).containsExactly(
                new BoundedLineReader.Line("12345", true),
                new BoundedLineReader.Line("next", false));
    }

    private static List<BoundedLineReader.Line> readAll(String text, int maxLength) throws IOException {
        BoundedLineReader reader = new BoundedLineReader(new BufferedReader(new StringReader(text)), maxLength);
        List<BoundedLineReader.Line> lines = new ArrayList<>();
        BoundedLineReader.Line line;
        while ((line = reader.next()) != null) {
            lines.add(line);
        }
        return lines;
    }
}
