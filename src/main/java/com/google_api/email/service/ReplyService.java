package com.google_api.email.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.google_api.email.dto.ReplyEmailDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.google.api.services.gmail.model.Message;
import java.io.IOException;
import java.util.List;

import com.google.api.client.util.Base64;

import static com.google_api.shared.Constant.AGENT_EMOJI;

@Service
@RequiredArgsConstructor
public class ReplyService {

    private final Gmail gmailService;

    @Value("${gmail.user.email}")
    private String userEmail;

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ReplyService.class);

    public String replyToEmail(ReplyEmailDto replyEmailDto) {
        try {
            String labelId = getOrCreateLabelId("AI-AGENT-REPLIED");
            Message originalMessage = gmailService.users().messages()
                    .get(userEmail, replyEmailDto.originalMessageId())
                    .setFormat("full")
                    .execute();

            String threadId = originalMessage.getThreadId();
            StringBuilder rawEmailBuilder = getStringBuilder(replyEmailDto, originalMessage);
            String encodedEmail = Base64.encodeBase64URLSafeString(rawEmailBuilder.toString().getBytes("UTF-8"));

            Message message = new Message();
            message.setRaw(encodedEmail);
            message.setThreadId(threadId);

            Message sentMessage = gmailService.users().messages().send(userEmail, message).execute();

            ModifyMessageRequest originalMods = new ModifyMessageRequest()
                    .setRemoveLabelIds(List.of("UNREAD", "INBOX"));
            gmailService.users().messages().modify(userEmail, replyEmailDto.originalMessageId(), originalMods).execute();

            ModifyMessageRequest labelMods = new ModifyMessageRequest()
                    .setAddLabelIds(List.of(labelId, "UNREAD"));
            gmailService.users().messages().modify(userEmail, sentMessage.getId(), labelMods).execute();
            logger.info(AGENT_EMOJI +"AGENT_REPLY - Successfully replied to mail with label: {}", labelId);
            return "SUCCESS";
        } catch (IOException e) {
            logger.error(AGENT_EMOJI + "AGENT_REPLY - Error removing label and moving to inbox: {}", e.getMessage());
            return "failure: " + e.getMessage();
        }
    }

    private StringBuilder getStringBuilder(ReplyEmailDto replyEmailDto, Message originalMessage) {
        String originalSubject = "";
        String originalFrom = "";
        String messageId = "";

        for (var header : originalMessage.getPayload().getHeaders()) {
            switch (header.getName()) {
                case "Subject" -> originalSubject = header.getValue();
                case "From" -> originalFrom = header.getValue();
                case "Message-ID" -> messageId = header.getValue();
            }
        }

        String replySubject = originalSubject.startsWith("Re:") ? originalSubject : "Re: " + originalSubject;

        StringBuilder rawEmailBuilder = new StringBuilder();
        rawEmailBuilder.append("From: ").append(userEmail).append("\r\n");
        rawEmailBuilder.append("To: ").append(originalFrom).append("\r\n");
        rawEmailBuilder.append("Subject: ").append(replySubject).append("\r\n");
        if (!messageId.isEmpty()) {
            rawEmailBuilder.append("In-Reply-To: ").append(messageId).append("\r\n");
            rawEmailBuilder.append("References: ").append(messageId).append("\r\n");
        }
        rawEmailBuilder.append("Content-Type: text/plain; charset=\"UTF-8\"\r\n");
        rawEmailBuilder.append("\r\n");
        rawEmailBuilder.append(replyEmailDto.bodyText()).append("\r\n");
        return rawEmailBuilder;
    }

    private String getOrCreateLabelId(String labelName) throws IOException {
        List<com.google.api.services.gmail.model.Label> labels = gmailService.users().labels().list(userEmail).execute().getLabels();

        for (com.google.api.services.gmail.model.Label label : labels) {
            if (label.getName().equalsIgnoreCase(labelName)) {
                return label.getId();
            }
        }

        com.google.api.services.gmail.model.Label newLabel = new com.google.api.services.gmail.model.Label()
                .setName(labelName)
                .setLabelListVisibility("labelShow")
                .setMessageListVisibility("show");

        com.google.api.services.gmail.model.Label createdLabel =
                gmailService.users().labels().create(userEmail, newLabel).execute();

        return createdLabel.getId();
    }

}
