package com.google_api.email.dto;

public record ReplyEmailDto(String originalMessageId, String subject, String bodyText) { }