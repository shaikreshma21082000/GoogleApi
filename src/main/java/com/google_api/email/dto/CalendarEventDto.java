package com.google_api.email.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventDto {
    private String eventId;
    private String summary;
    private String description;
    private String location;
    private String meetingLink;
    private String organizerEmail;
    private List<String> attendees;
    private String startDateTime;
    private String endDateTime;
    private Long meetingDurationMinutes;
}

