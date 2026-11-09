package com.dkozel.velocitylimiter.batch;

import java.io.BufferedReader;
import java.io.IOException;

final class BoundedLineReader {

    record Line(String text, boolean tooLong) {
    }

    private final BufferedReader reader;
    private final int maxLength;

    BoundedLineReader(BufferedReader reader, int maxLength) {
        this.reader = reader;
        this.maxLength = maxLength;
    }

    Line next() throws IOException {
        int c = reader.read();
        if (c == -1) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        boolean tooLong = false;
        while (c != -1 && c != '\n' && c != '\r') {
            if (text.length() < maxLength) {
                text.append((char) c);
            } else {
                tooLong = true;
            }
            c = reader.read();
        }
        if (c == '\r') {
            reader.mark(1);
            if (reader.read() != '\n') {
                reader.reset();
            }
        }
        return new Line(text.toString(), tooLong);
    }
}
