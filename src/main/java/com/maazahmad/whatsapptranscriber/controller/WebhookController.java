package com.maazahmad.whatsapptranscriber.controller;

import com.maazahmad.whatsapptranscriber.dto.WhatsAppWebhookDto;
import com.maazahmad.whatsapptranscriber.service.GroqService;
import com.maazahmad.whatsapptranscriber.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WhatsAppService whatsAppService;
    private final GroqService groqService;

    @Value("${whatsapp.verifyToken}")
    private String verifyToken;

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyTokenParam,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && verifyToken.equals(verifyTokenParam)) {
            return ResponseEntity.ok(challenge);
        } else {
            return ResponseEntity.badRequest().body("Verification failed");
        }
    }

    @PostMapping
    public ResponseEntity<String> handleWebhook(@RequestBody WhatsAppWebhookDto payload) {
        // Check if there's a message
        if (payload.getEntry() != null && !payload.getEntry().isEmpty()) {
            var entry = payload.getEntry().get(0);
            if (entry.getChanges() != null && !entry.getChanges().isEmpty()) {
                var change = entry.getChanges().get(0);
                var value = change.getValue();
                if (value.getMessages() != null && !value.getMessages().isEmpty()) {
                    var message = value.getMessages().get(0);
                    if ("audio".equals(message.getType())) {
                        String mediaId = message.getAudio().getId();
                        String from = message.getFrom();

                        // Send processing message
                        whatsAppService.sendReply(from, "Processing your voice note...");

                        // Process asynchronously
                        processAudioAsync(mediaId, from);

                        return ResponseEntity.ok("EVENT_RECEIVED");
                    }
                }
            }
        }
        return ResponseEntity.ok("EVENT_RECEIVED");
    }

    @Async
    public void processAudioAsync(String mediaId, String from) {
        try {
            String mediaUrl = whatsAppService.getMediaUrl(mediaId);
            byte[] audioData = whatsAppService.downloadFile(mediaUrl);
            String transcribedText = groqService.transcribe(audioData);
            whatsAppService.sendReply(from, "Transcription: " + transcribedText);
        } catch (Exception e) {
            whatsAppService.sendReply(from, "Sorry, failed to process your voice note.");
        }
    }
}