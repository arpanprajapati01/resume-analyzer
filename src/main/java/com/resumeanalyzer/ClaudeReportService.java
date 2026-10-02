package com.resumeanalyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Sends resume + JD to Claude with the ATS-specialist system prompt. Needs env var ANTHROPIC_API_KEY. */
@Service
public class ClaudeReportService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public boolean enabled() {
        String k = System.getenv("ANTHROPIC_API_KEY");
        return k != null && !k.isBlank();
    }

    public String generate(String resumeText, String jd) throws Exception {
        String system;
        try (InputStream in = getClass().getResourceAsStream("/system-prompt.txt")) {
            system = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String user = "RESUME:\n" + resumeText + "\n\nTARGET JOB DESCRIPTION:\n" + (jd == null || jd.isBlank() ? "(not provided)" : jd);
        String body = mapper.writeValueAsString(Map.of(
            "model", "claude-sonnet-5-5",
            "max_tokens", 4000,
            "system", system,
            "messages", List.of(Map.of("role", "user", "content", user))));
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
            .header("content-type", "application/json")
            .header("x-api-key", System.getenv("ANTHROPIC_API_KEY"))
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(res.body());
        if (res.statusCode() != 200) throw new IllegalStateException("Claude API error: " + res.body());
        return root.path("content").get(0).path("text").asText();
    }
}
