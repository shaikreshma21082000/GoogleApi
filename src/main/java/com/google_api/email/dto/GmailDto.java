package com.google_api.email.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record GmailDto(
        String messageId,
        String subject,
        String from,
        List<String> recipients,
        OffsetDateTime receivedAt,
        String snippet
) {}
