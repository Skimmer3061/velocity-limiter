package com.dkozel.velocitylimiter.batch;

import com.dkozel.velocitylimiter.api.LoadRequest;
import com.dkozel.velocitylimiter.api.LoadResponse;
import com.dkozel.velocitylimiter.domain.LoadDecision;
import com.dkozel.velocitylimiter.service.LoadService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Optional;

@Component
public class LoadStreamProcessor {

    private final LoadService loadService;
    private final JsonMapper jsonMapper;

    LoadStreamProcessor(LoadService loadService, JsonMapper jsonMapper) {
        this.loadService = loadService;
        this.jsonMapper = jsonMapper;
    }

    public void process(BufferedReader input, Writer output) throws IOException {
        String line;
        while ((line = input.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            LoadRequest request = jsonMapper.readValue(line, LoadRequest.class);
            Optional<LoadDecision> decision = loadService.process(request.toCommand());
            if (decision.isPresent()) {
                output.write(jsonMapper.writeValueAsString(LoadResponse.from(decision.get())));
                output.write('\n');
            }
        }
        output.flush();
    }
}
