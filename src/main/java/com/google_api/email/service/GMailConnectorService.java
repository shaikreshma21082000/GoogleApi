package com.google_api.email.service;

import com.google_api.email.dto.GmailDto;
import com.google_api.email.mapper.GmailMapper;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static com.google_api.shared.Constant.AGENT_EMOJI;

@Service
@RequiredArgsConstructor
public class GMailConnectorService {

    private final Gmail gmailService;
    private final GmailMapper gmailMapper;

    @Value("${gmail.user.email}")
    private String userEmail;

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(GMailConnectorService.class);


    public List<GmailDto> readAndLabelUnreadEmailsFromInbox(long timestampSeconds) {
        List<GmailDto> gmailDtos = new ArrayList<>();
        try {
            String query = "is:unread after:" + timestampSeconds;

            ListMessagesResponse response = gmailService.users().messages().list(userEmail).setQ(query).setLabelIds(Arrays.asList("INBOX")).execute();
            List<Message> messages = response.getMessages();


            if (messages != null && !messages.isEmpty()) {
                logger.info(AGENT_EMOJI +"AGENT_FETCH Found " + messages.size() + " unread messages received after " + timestampSeconds + ".");
                for (Message message : messages) {
                    Message fullMessage = gmailService.users().messages().get(userEmail, message.getId()).execute();
                    gmailDtos.add(gmailMapper.messageToDto(fullMessage));
                }
            } else {
                logger.info(AGENT_EMOJI +"AGENT_FETCH - No unread messages found after the specified time.");
            }

        } catch (IOException e) {
            logger.error(AGENT_EMOJI +"AGENT_FETCH - Error reading emails: {}", e.getMessage());
            return Collections.emptyList();
        }
        return gmailDtos;
    }

    public String moveMessageToSpamWithLabelAndUnread(String messageId) {
        try {
            String labelName = "AI-AGENT-MOVED";
            String labelId = getOrCreateLabelId(labelName);

            ModifyMessageRequest spamMods = new ModifyMessageRequest()
                    .setAddLabelIds(List.of("SPAM", "UNREAD"))
                    .setRemoveLabelIds(List.of("INBOX"));
            gmailService.users().messages().modify(userEmail, messageId, spamMods).execute();

            ModifyMessageRequest labelMods = new ModifyMessageRequest()
                    .setAddLabelIds(List.of(labelId));
            gmailService.users().messages().modify(userEmail, messageId, labelMods).execute();
            logger.info(AGENT_EMOJI +"AGENT_MOVE - Successfully moved mail to spam with label: {}", labelName);
            return "success";
        } catch (IOException e) {
            logger.error(AGENT_EMOJI +"AGENT_MOVE - Error moving message to spam: {}", e.getMessage());
            return "failure";
        }
    }

    private String getOrCreateLabelId(String labelName) {
        try {
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
        } catch (IOException e) {
            logger.error("Error moving message to spam: {}", e.getMessage());
            return "failure";
        }
    }

    public String removeLabelAndMoveToInbox(String labelName, String folderName) {
        try {
            List<com.google.api.services.gmail.model.Label> labels =
                    gmailService.users().labels().list(userEmail).execute().getLabels();

            String labelId = null;
            String folderId = null;

            for (com.google.api.services.gmail.model.Label label : labels) {
                if (label.getName().equalsIgnoreCase(labelName) || label.getId().equalsIgnoreCase(labelName)) {
                    labelId = label.getId();
                }
                if (label.getName().equalsIgnoreCase(folderName) || label.getId().equalsIgnoreCase(folderName)) {
                    folderId = label.getId();
                }
            }

            if (labelId == null) {
                return "Label not found: " + labelName;
            }
            if (folderId == null) {
                return "Folder not found: " + folderName;
            }

            ListMessagesResponse response = gmailService.users().messages().list(userEmail).setLabelIds(Collections.singletonList(folderId)).execute();

            List<Message> messages = response.getMessages();
            if (messages == null || messages.isEmpty()) {
                return "No messages found in folder: " + folderName;
            }

            for (Message message : messages) {
                ModifyMessageRequest mods = new ModifyMessageRequest()
                        .setRemoveLabelIds(Arrays.asList(labelId, folderId))
                        .setAddLabelIds(Arrays.asList("INBOX", "UNREAD"));

                gmailService.users().messages()
                        .modify(userEmail, message.getId(), mods)
                        .execute();
            }

            return "success (" + messages.size() + " messages updated)";
        } catch (IOException e) {
            logger.error("Error removing label and moving to inbox: {}", e.getMessage());
            return "failure: " + e.getMessage();
        }
    }


}