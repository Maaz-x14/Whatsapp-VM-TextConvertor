package com.maazahmad.whatsapptranscriber.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GroqService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.url}")
    private String groqAudioUrl;

    @Value("${groq.api.key}")
    private String groqApiKey;

    // Hardcoded Chat URL for Llama-3 calls
    private static final String GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";


    // Step 1: Transcribe Audio (Whisper)
    @SneakyThrows
    public String transcribe(byte[] audioData) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(groqApiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // Wrap byte array in a Resource with a filename so Groq knows it's audio
        body.add("file", new ByteArrayResource(audioData) {
            @Override
            public String getFilename() {
                return "audio.ogg";
            }
        });
        body.add("model", "whisper-large-v3");
        body.add("response_format", "json");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(groqAudioUrl, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return jsonNode.get("text").asText();
        } else {
            throw new RuntimeException("Failed to transcribe audio: " + response.getStatusCode());
        }
    }

    /**
     * MASTER BRAIN: Analyzes Intent (Log vs Query vs Irrelevant) & Extracts Data
     */
    @SneakyThrows
    public String analyzeInput(String rawText) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        String today = LocalDate.now().toString();

        String systemPrompt = """
            You are an expert CFO AI. Analyze the user's input.
            
            Current Date Reference: %s
            
            STEP 1: DETERMINE INTENT
            1. "LOG_EXPENSE": User is reporting spending (e.g., "I spent 500 on lunch").
            2. "QUERY_SPENDING": User is asking for analytics (e.g., "How much did I spend on KFC?").
            3. "IRRELEVANT": Input is NOT related to finances (e.g., songs, greetings, random noise, poetry).
            
            STEP 2: EXTRACT DATA
            
            --- CASE A: LOG_EXPENSE ---
            Extract: item, amount, currency (default PKR), merchant, category, date (YYYY-MM-DD).
            
            --- CASE B: QUERY_SPENDING ---
            Extract filter parameters (use "ALL" if not specified):
            - category (string): e.g., "Food"
            - merchant (string): e.g., "KFC", "Uber"
            - item (string): e.g., "Chicken Wings"
            - start_date (string): YYYY-MM-DD
            - end_date (string): YYYY-MM-DD
            
            --- CASE C: IRRELEVANT ---
            No data needed.
            
            STEP 3: OUTPUT JSON ONLY
            
            Format for LOG_EXPENSE:
            {
              "intent": "LOG_EXPENSE",
              "data": { "item": "...", "amount": 0, "currency": "...", "merchant": "...", "category": "...", "date": "..." }
            }
            
            Format for QUERY_SPENDING:
            {
              "intent": "QUERY_SPENDING",
              "query": { "category": "...", "merchant": "...", "item": "...", "start_date": "...", "end_date": "..." }
            }
            
            Format for IRRELEVANT:
            {
              "intent": "IRRELEVANT",
              "message": "I can only help you with financial records."
            }
            """.formatted(today);

        Map<String, Object> body = Map.of(
                "model", "llama-3.3-70b-versatile",
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", rawText)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.1
        );

        String jsonBody = objectMapper.writeValueAsString(body);
        HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(GROQ_CHAT_URL, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        } else {
            throw new RuntimeException("Failed to analyze input");
        }
    }
}