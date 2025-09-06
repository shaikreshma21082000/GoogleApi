package com.google_api.email.controller;

import com.google_api.email.dto.GmailDto;
import com.google_api.email.dto.ReplyEmailDto;
import com.google_api.email.service.GMailConnectorService;
import com.google_api.email.service.ReplyService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GmailController {

    private final GMailConnectorService mailService;
    private final ReplyService replyService;

    @GetMapping("/unread-emails")
    public List<GmailDto> readAndLabelEmails(@RequestParam LocalDateTime localDateTime) {
            long timestampSeconds = localDateTime
                    .atZone(ZoneId.of("Asia/Kolkata"))
                    .toInstant()
                    .getEpochSecond();

            return mailService.readAndLabelUnreadEmailsFromInbox(timestampSeconds);

    }

    @PutMapping("/move-mail/{messageId}")
    public String moveSpamMail(@PathVariable String messageId) {
        return mailService.moveMessageToSpamWithLabelAndUnread(messageId);
    }

    @DeleteMapping("/remove-label/{label}/{folderName}")
    public String removeLabel(@PathVariable String label,@PathVariable String folderName) {
        return mailService.removeLabelAndMoveToInbox(label, folderName);
    }

    @PostMapping("/reply")
    public String reply(@RequestBody ReplyEmailDto replyEmailDto) throws MessagingException, IOException {
        return replyService.replyToEmail(replyEmailDto);
    }


}
