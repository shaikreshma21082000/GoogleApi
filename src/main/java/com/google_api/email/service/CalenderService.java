package com.google_api.email.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Events;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventAttendee;
import com.google_api.email.dto.CalendarEventDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import static com.google_api.shared.Constant.AGENT_EMOJI;

@Service
@RequiredArgsConstructor
public class CalenderService {

    private final Calendar calendarService;

    @Value("${gmail.user.email}")
    private String userEmail;

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(GMailConnectorService.class);

    public String addEvent(CalendarEventDto calendarEventDto) {
        try {
            Event event = new Event();
            event.setSummary(calendarEventDto.getSummary());
            event.setDescription(calendarEventDto.getDescription());
            event.setLocation(calendarEventDto.getLocation());

            if (calendarEventDto.getAttendees() != null && !calendarEventDto.getAttendees().isEmpty()) {
                List<EventAttendee> attendees = calendarEventDto.getAttendees().stream()
                        .map(email -> new EventAttendee().setEmail(email))
                        .collect(Collectors.toList());
                event.setAttendees(attendees);
            }
            String startStr = calendarEventDto.getStartDateTime();
            String endStr = calendarEventDto.getEndDateTime();
            Long durationMinutes = calendarEventDto.getMeetingDurationMinutes();

            if ((startStr == null || startStr.isEmpty())) {
                throw new IllegalArgumentException("startDateTime is required");
            }

            com.google.api.client.util.DateTime startDt = toDateTime(startStr);
            com.google.api.client.util.DateTime endDt;

            if (endStr != null && !endStr.isEmpty()) {
                endDt = toDateTime(endStr);
            } else if (durationMinutes != null) {
                long endMillis = startDt.getValue() + (durationMinutes * 60L * 1000L);
                endDt = new com.google.api.client.util.DateTime(endMillis);
            } else {
                throw new IllegalArgumentException("Either endDateTime or meetingDurationMinutes must be provided");
            }

            EventDateTime start = new EventDateTime();
            EventDateTime end = new EventDateTime();
            start.setDateTime(startDt);
            end.setDateTime(endDt);
            event.setStart(start);
            event.setEnd(end);

            String meetingLink = calendarEventDto.getMeetingLink();
            if (meetingLink != null && !meetingLink.isEmpty()) {
                String existingDesc = event.getDescription();
                String joinText = "Join meeting: " + meetingLink;
                if (existingDesc == null || existingDesc.isEmpty()) {
                    event.setDescription(joinText);
                } else if (!existingDesc.contains(meetingLink)) {
                    event.setDescription(existingDesc + "\n\n" + joinText);
                }
                event.setLocation(meetingLink);
            }
            Event created = calendarService.events().insert(userEmail, event).execute();
            logger.info(AGENT_EMOJI +"AGENT_ADD_EVENT - Created calendar event id={} htmlLink={}", created.getId(), created.getHtmlLink());
            return created.getId();
        } catch (Exception e) {
            logger.error(AGENT_EMOJI +"AGENT_ADD_EVENT -Failed to create calendar event: {}", e.getMessage(), e);
            return null;
        }
    }


    private static com.google.api.client.util.DateTime toDateTime(String input) {
        if (input == null) return null;
        try {
            return new com.google.api.client.util.DateTime(input);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid date/time format: " + input, e);
        }
    }

    public List<CalendarEventDto> getAllEventDetails() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime oneWeekLater = now.plusWeeks(1);
            Date startDate = Date.from(now.atZone(ZoneId.systemDefault()).toInstant());
            Date endDate = Date.from(oneWeekLater.atZone(ZoneId.systemDefault()).toInstant());
            DateTime timeMin = new DateTime(startDate);
            DateTime timeMax = new DateTime(endDate);

            Events events = calendarService.events().list(userEmail)
                    .setTimeMin(timeMin)
                    .setTimeMax(timeMax)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();

            List<CalendarEventDto> eventDtos = new ArrayList<>();
            for (Event event : events.getItems()) {
                String startStr = null;
                String endStr = null;
                Long meetingDurationMinutes = null;
                if (event.getStart() != null && event.getEnd() != null) {
                    com.google.api.client.util.DateTime startDt = event.getStart().getDateTime();
                    com.google.api.client.util.DateTime endDt = event.getEnd().getDateTime();
                    if (startDt != null && endDt != null) {
                        startStr = startDt.toStringRfc3339();
                        endStr = endDt.toStringRfc3339();
                        meetingDurationMinutes = (endDt.getValue() - startDt.getValue()) / (1000 * 60);
                    } else if (event.getStart().getDate() != null && event.getEnd().getDate() != null) {
                        startStr = event.getStart().getDate().toStringRfc3339();
                        endStr = event.getEnd().getDate().toStringRfc3339();
                        meetingDurationMinutes = 24L * 60;
                    }
                }

                CalendarEventDto dto = CalendarEventDto.builder()
                        .eventId(event.getId())
                        .summary(event.getSummary())
                        .description(event.getDescription())
                        .location(event.getLocation())
                        .meetingLink(event.getHangoutLink())
                        .organizerEmail(event.getOrganizer() != null ? event.getOrganizer().getEmail() : null)
                        .attendees(event.getAttendees() != null ? event.getAttendees().stream().map(com.google.api.services.calendar.model.EventAttendee::getEmail).toList() : null)
                        .startDateTime(startStr != null ? startStr : (event.getStart() != null ? (event.getStart().getDateTime() != null ? event.getStart().getDateTime().toString() : event.getStart().getDate() != null ? event.getStart().getDate().toString() : null) : null))
                        .endDateTime(endStr != null ? endStr : (event.getEnd() != null ? (event.getEnd().getDateTime() != null ? event.getEnd().getDateTime().toString() : event.getEnd().getDate() != null ? event.getEnd().getDate().toString() : null) : null))
                        .meetingDurationMinutes(meetingDurationMinutes)
                        .build();
                eventDtos.add(dto);
            }
            return eventDtos;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch calendar events", e);
        }
    }

}
