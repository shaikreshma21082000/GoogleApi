package com.google_api.email.mapper;

import com.google_api.email.dto.GmailDto;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GmailMapper {

    public GmailDto messageToDto(Message fullMessage) {

        Map<String, String> headers = extractHeaders(fullMessage);

        String subject = headers.getOrDefault("Subject", "(no subject)");
        String from = headers.getOrDefault("From", "(unknown)");
        List<String> recipients = new ArrayList<>();
        if (headers.containsKey("To")) {
            recipients.addAll(Arrays.asList(headers.get("To").split(",")));
        }

        OffsetDateTime receivedAt = null;
        if (fullMessage.getInternalDate() != null) {
            receivedAt = OffsetDateTime.ofInstant(
                    new Date(fullMessage.getInternalDate()).toInstant(),
                    ZoneId.of("Asia/Kolkata")
            );
        }

        String snippet = fullMessage.getSnippet();

        return new GmailDto(
                fullMessage.getId(),
                subject,
                from,
                recipients,
                receivedAt,
                snippet
        );
    }

    private Map<String, String> extractHeaders(Message message) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) {
            return Collections.emptyMap();
        }
        return message.getPayload()
                .getHeaders()
                .stream()
                .collect(Collectors.toMap(
                        MessagePartHeader::getName,
                        MessagePartHeader::getValue,
                        (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new
                ));
    }
}
