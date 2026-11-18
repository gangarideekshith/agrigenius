package com.agrigenius.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.integration.ml.MlServiceClient;
import com.agrigenius.reminder.application.ReminderDueService;
import com.agrigenius.support.PostgresTestcontainer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reminders and notifications end to end against PostgreSQL: CRUD, state changes, ownership,
 * validation, list views, the due-reminder scanner and duplicate prevention.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReminderIntegrationTest implements PostgresTestcontainer {

    private static final String PASSWORD = "Kh3t-Safe-pass";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired ReminderDueService dueService;
    @MockitoBean MlServiceClient mlServiceClient;

    private String token;
    private UUID userId;

    @BeforeEach
    void signIn() throws Exception {
        String[] farmer = registerAndLogin();
        token = farmer[0];
        userId = UUID.fromString(farmer[1]);
    }

    // ---- CRUD -------------------------------------------------------------------------------

    @Test
    void createGetUpdateAndDeleteOwnReminder() throws Exception {
        LocalDateTime when = LocalDateTime.now(IST).plusDays(2).withHour(6).withMinute(30).truncatedTo(ChronoUnit.MINUTES);
        Map<String, Object> body = reminder("Irrigate paddy field", "IRRIGATION", when);
        body.put("description", "  Open the canal gate  ");
        body.put("cropName", " Rice ");
        body.put("priority", "HIGH");
        body.put("userId", UUID.randomUUID().toString()); // must be ignored

        JsonNode created = read(perform(post("/api/reminders"), body)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.title").value("Irrigate paddy field"))
                .andExpect(jsonPath("$.description").value("Open the canal gate"))
                .andExpect(jsonPath("$.cropName").value("Rice"))
                .andExpect(jsonPath("$.category").value("IRRIGATION"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.scheduledDate").value(when.toLocalDate().toString()))
                .andExpect(jsonPath("$.scheduledTime").value("06:30:00"))
                .andExpect(jsonPath("$.overdue").value(false)));
        String id = created.get("id").asText();
        assertThat(jdbc.queryForObject("select user_id from reminders where id = ?", UUID.class, UUID.fromString(id)))
                .isEqualTo(userId);

        mockMvc.perform(get("/api/reminders/" + id).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        Map<String, Object> update = reminder("Irrigate paddy (east)", "IRRIGATION", when.plusHours(1));
        perform(put("/api/reminders/" + id), update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Irrigate paddy (east)"))
                .andExpect(jsonPath("$.scheduledTime").value("07:30:00"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.description").isEmpty());

        mockMvc.perform(delete("/api/reminders/" + id).header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/reminders/" + id).header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void completeReopenAndCancelFollowTheStateRules() throws Exception {
        String id = createReminder("Spray neem oil", "PESTICIDE", LocalDateTime.now(IST).plusDays(1));

        postAction(id, "complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        postAction(id, "complete").andExpect(status().isOk()); // idempotent
        perform(put("/api/reminders/" + id), reminder("Edit after done", "PESTICIDE", LocalDateTime.now(IST).plusDays(3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMINDER_NOT_EDITABLE"));
        postAction(id, "cancel").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMINDER_INVALID_STATE"));

        postAction(id, "reopen").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.completedAt").isEmpty());
        postAction(id, "cancel").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        postAction(id, "complete").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMINDER_INVALID_STATE"));
        postAction(id, "reopen").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ---- Security ---------------------------------------------------------------------------

    @Test
    void anotherFarmersReminderIsInvisibleAndUntouchable() throws Exception {
        String id = createReminder("Weed the cotton rows", "WEEDING", LocalDateTime.now(IST).plusDays(1));
        String otherToken = "Bearer " + registerAndLogin()[0];

        mockMvc.perform(get("/api/reminders/" + id).header("Authorization", otherToken)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/reminders/" + id).header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reminder("Hijack", "GENERAL", LocalDateTime.now(IST).plusDays(1)))))
                .andExpect(status().isNotFound());
        for (String action : new String[] {"complete", "cancel", "reopen"}) {
            mockMvc.perform(post("/api/reminders/" + id + "/" + action).header("Authorization", otherToken))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(delete("/api/reminders/" + id).header("Authorization", otherToken)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reminders").header("Authorization", otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        assertThat(jdbc.queryForObject("select title from reminders where id = ?", String.class, UUID.fromString(id)))
                .isEqualTo("Weed the cotton rows");
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/reminders")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/reminders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- Validation -------------------------------------------------------------------------

    @Test
    void invalidRequestsAreRejectedWithProblemDetails() throws Exception {
        perform(post("/api/reminders"), Map.of("title", " ", "category", "IRRIGATION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3)); // title, scheduledDate, scheduledTime

        Map<String, Object> badCategory = reminder("Water", "DANCING", LocalDateTime.now(IST).plusDays(1));
        perform(post("/api/reminders"), badCategory).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/reminders").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        perform(post("/api/reminders"), reminder("Too late", "GENERAL", LocalDateTime.now(IST).minusHours(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REMINDER_IN_PAST"));

        perform(post("/api/reminders"), reminder("Far", "GENERAL", LocalDateTime.now(IST).plusYears(5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REMINDER_TOO_FAR_AHEAD"));

        Map<String, Object> badZone = reminder("Zone", "GENERAL", LocalDateTime.now(IST).plusDays(1));
        badZone.put("timeZone", "Mars/Olympus");
        perform(post("/api/reminders"), badZone).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_ZONE"));

        Map<String, Object> longTitle = reminder("x".repeat(121), "GENERAL", LocalDateTime.now(IST).plusDays(1));
        perform(post("/api/reminders"), longTitle).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/reminders").param("status", "DONE").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reminders").param("from", "2030-02-01").param("to", "2030-01-01")
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        mockMvc.perform(get("/api/reminders/not-a-uuid").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from reminders where user_id = ?", Long.class, userId)).isZero();
    }

    @Test
    void editingKeepsAnOverdueTimeButRejectsMovingItIntoThePast() throws Exception {
        String id = createReminder("Inspect leaves", "CROP_INSPECTION", LocalDateTime.now(IST).plusDays(1));
        LocalDateTime past = LocalDateTime.now(IST).minusDays(1).withSecond(0).withNano(0);
        moveToPast(id, past);

        perform(put("/api/reminders/" + id), reminder("Inspect leaves carefully", "CROP_INSPECTION", past))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(true));
        perform(put("/api/reminders/" + id), reminder("Inspect leaves", "CROP_INSPECTION", past.minusHours(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REMINDER_IN_PAST"));
    }

    // ---- Views ------------------------------------------------------------------------------

    @Test
    void listFiltersAndViewsReturnTheRightReminders() throws Exception {
        LocalDateTime now = LocalDateTime.now(IST);
        String overdue = createReminder("Apply urea", "FERTILIZER", now.plusDays(1));
        moveToPast(overdue, now.minusDays(2));
        String soon = createReminder("Soil sample to lab", "SOIL_TESTING", now.plusDays(3));
        String later = createReminder("Harvest maize", "HARVESTING", now.plusDays(20));
        String done = createReminder("Sow groundnut", "SOWING", now.plusDays(4));
        postAction(done, "complete").andExpect(status().isOk());

        mockMvc.perform(get("/api/reminders").param("status", "PENDING").header("Authorization", bearer()))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].id").value(overdue)); // earliest first
        mockMvc.perform(get("/api/reminders").param("category", "HARVESTING").header("Authorization", bearer()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(later));
        mockMvc.perform(get("/api/reminders").param("q", "SOIL").header("Authorization", bearer()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(soon));
        mockMvc.perform(get("/api/reminders").param("q", "%").header("Authorization", bearer()))
                .andExpect(jsonPath("$.totalItems").value(0)); // LIKE wildcards are escaped
        LocalDate soonDate = now.plusDays(3).toLocalDate();
        mockMvc.perform(get("/api/reminders").param("from", soonDate.toString()).param("to", soonDate.toString())
                        .header("Authorization", bearer()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(soon));

        mockMvc.perform(get("/api/reminders/overdue").header("Authorization", bearer()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(overdue))
                .andExpect(jsonPath("$[0].overdue").value(true));
        mockMvc.perform(get("/api/reminders/upcoming").header("Authorization", bearer()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(soon));
        mockMvc.perform(get("/api/reminders/upcoming").param("days", "30").header("Authorization", bearer()))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/reminders/summary").header("Authorization", bearer()))
                .andExpect(jsonPath("$.pending").value(3))
                .andExpect(jsonPath("$.overdue").value(1))
                .andExpect(jsonPath("$.upcoming").value(1))
                .andExpect(jsonPath("$.upcomingDays").value(7))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.cancelled").value(0));
    }

    @Test
    void todayListsTodaysRemindersInTheRequestedZone() throws Exception {
        LocalDateTime endOfToday = LocalDate.now(IST).atTime(23, 59);
        if (!endOfToday.isAfter(LocalDateTime.now(IST).plusMinutes(1))) {
            return; // the test ran in the last minute of the day; nothing left of "today" to schedule
        }
        String today = createReminder("Check drip lines", "IRRIGATION", endOfToday);
        createReminder("Tomorrow's task", "GENERAL", endOfToday.plusDays(1));

        mockMvc.perform(get("/api/reminders/today").param("timeZone", "Asia/Kolkata").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(today));
        mockMvc.perform(get("/api/reminders/today").param("timeZone", "Nowhere/Land").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_ZONE"));
    }

    // ---- Due detection and notifications ----------------------------------------------------

    @Test
    void dueRemindersCreateExactlyOneNotification() throws Exception {
        LocalDateTime now = LocalDateTime.now(IST);
        String due = createReminder("Open irrigation valve", "IRRIGATION", now.plusDays(1));
        moveToPast(due, now.minusMinutes(5));
        String future = createReminder("Future task", "GENERAL", now.plusDays(1));
        String completed = createReminder("Done already", "GENERAL", now.plusDays(1));
        postAction(completed, "complete").andExpect(status().isOk());
        moveToPast(completed, now.minusMinutes(5));
        String cancelled = createReminder("Called off", "GENERAL", now.plusDays(1));
        postAction(cancelled, "cancel").andExpect(status().isOk());
        moveToPast(cancelled, now.minusMinutes(5));

        drainDue();
        drainDue(); // a second scan (or a restart) must not notify again

        assertThat(notificationsFor(due)).isEqualTo(1);
        assertThat(notificationsFor(future)).isZero();
        assertThat(notificationsFor(completed)).isZero();
        assertThat(notificationsFor(cancelled)).isZero();

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer()))
                .andExpect(jsonPath("$.count").value(1));
        JsonNode page = read(mockMvc.perform(get("/api/notifications").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("REMINDER_DUE"))
                .andExpect(jsonPath("$.items[0].title").value("Open irrigation valve"))
                .andExpect(jsonPath("$.items[0].reminderId").value(due))
                .andExpect(jsonPath("$.items[0].read").value(false)));
        String notificationId = page.get("items").get(0).get("id").asText();

        // Another farmer cannot read or mark it.
        String otherToken = "Bearer " + registerAndLogin()[0];
        mockMvc.perform(post("/api/notifications/" + notificationId + "/read").header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/notifications").header("Authorization", otherToken))
                .andExpect(jsonPath("$.totalItems").value(0));

        mockMvc.perform(post("/api/notifications/" + notificationId + "/read").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(get("/api/notifications").param("unreadOnly", "true").header("Authorization", bearer()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void reschedulingNotifiesAgainButReopeningDoesNot() throws Exception {
        LocalDateTime now = LocalDateTime.now(IST);
        String id = createReminder("Second fertilizer dose", "FERTILIZER", now.plusDays(1));
        moveToPast(id, now.minusMinutes(10));
        drainDue();
        assertThat(notificationsFor(id)).isEqualTo(1);

        // Complete then reopen: same time was already notified, so no duplicate.
        postAction(id, "complete").andExpect(status().isOk());
        postAction(id, "reopen").andExpect(status().isOk()).andExpect(jsonPath("$.overdue").value(true));
        drainDue();
        assertThat(notificationsFor(id)).isEqualTo(1);

        // Rescheduling to a new time clears the notified marker; once due again it notifies once more.
        perform(put("/api/reminders/" + id), reminder("Second fertilizer dose", "FERTILIZER", now.plusDays(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifiedAt").isEmpty());
        moveToPast(id, now.minusMinutes(1));
        drainDue();
        assertThat(notificationsFor(id)).isEqualTo(2);

        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer()))
                .andExpect(jsonPath("$.count").value(0));

        mockMvc.perform(delete("/api/reminders/" + id).header("Authorization", bearer())).andExpect(status().isNoContent());
        assertThat(notificationsFor(id)).isZero(); // notifications go with their reminder
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private void drainDue() {
        while (dueService.processBatch().claimed() > 0) {
            // keep going until nothing is due
        }
    }

    /** Simulates the passage of time: puts the reminder's time in the past, as if it fell due. */
    private void moveToPast(String id, LocalDateTime localPast) {
        jdbc.update("update reminders set scheduled_at = ?, notified_at = null where id = ?",
                java.sql.Timestamp.from(localPast.atZone(IST).toInstant()), UUID.fromString(id));
    }

    private int notificationsFor(String reminderId) {
        return jdbc.queryForObject("select count(*) from notifications where reminder_id = ?", Integer.class,
                UUID.fromString(reminderId));
    }

    private String createReminder(String title, String category, LocalDateTime when) throws Exception {
        return read(perform(post("/api/reminders"), reminder(title, category, when)).andExpect(status().isCreated()))
                .get("id").asText();
    }

    private ResultActions postAction(String id, String action) throws Exception {
        return mockMvc.perform(post("/api/reminders/" + id + "/" + action).header("Authorization", bearer()));
    }

    private static Map<String, Object> reminder(String title, String category, LocalDateTime when) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("category", category);
        body.put("scheduledDate", when.toLocalDate().toString());
        body.put("scheduledTime", when.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString());
        body.put("timeZone", "Asia/Kolkata");
        return body;
    }

    private ResultActions perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String bearer() {
        return "Bearer " + token;
    }

    /** Returns {accessToken, userId}. */
    private String[] registerAndLogin() throws Exception {
        String mobile = "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"fullName\":\"Ravi Kumar\",\"mobileNumber\":\"" + mobile + "\",\"password\":\"" + PASSWORD
                                + "\",\"preferredLanguage\":\"te\"}"))
                .andExpect(status().isCreated());
        JsonNode login = json.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobileNumber\":\"" + mobile + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new String[] {login.get("accessToken").asText(), login.get("user").get("id").asText()};
    }
}
