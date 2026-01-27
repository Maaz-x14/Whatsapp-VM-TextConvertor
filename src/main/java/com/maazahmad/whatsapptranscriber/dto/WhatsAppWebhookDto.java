package com.maazahmad.whatsapptranscriber.dto;

import lombok.Data;
import java.util.List;

@Data
public class WhatsAppWebhookDto {
    private List<Entry> entry;

    @Data
    public static class Entry {
        private List<Change> changes;
    }

    @Data
    public static class Change {
        private Value value;
    }

    @Data
    public static class Value {
        private List<Message> messages;
    }

    @Data
    public static class Message {
        private String type;
        private Audio audio;
        private String from;
    }

    @Data
    public static class Audio {
        private String id;
    }
}