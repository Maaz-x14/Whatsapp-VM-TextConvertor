package com.maazahmad.whatsapptranscriber.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${whatsapp.api.url}")
    private String whatsappApiUrl;

    @Value("${whatsapp.token}")
    private String whatsappToken;

    @Value("${whatsapp.phoneNumberId}")
    private String phoneNumberId;

    @SneakyThrows
    public String getMediaUrl(String mediaId) {
        String url = whatsappApiUrl + "/" + mediaId;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(whatsappToken);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return jsonNode.get("url").asText();
        } else {
            throw new RuntimeException("Failed to get media URL: " + response.getStatusCode());
        }
    }

    public byte[] downloadFile(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(whatsappToken);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, byte[].class);

        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        } else {
            throw new RuntimeException("Failed to download file: " + response.getStatusCode());
        }
    }

    @SneakyThrows
    public void sendReply(String to, String text) {
        String url = whatsappApiUrl + "/" + phoneNumberId + "/messages";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(whatsappToken);

        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", to);
        body.put("type", "text");
        Map<String, String> textBody = new HashMap<>();
        textBody.put("body", text);
        body.put("text", textBody);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to send reply: " + response.getStatusCode());
        }
    }
}