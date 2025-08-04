import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SS12000Client implements AutoCloseable {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authToken;
    private final ObjectMapper objectMapper;

    public SS12000Client(String baseUrl, String authToken) {
        this(baseUrl, authToken, HttpClient.newHttpClient());
    }

    public SS12000Client(String baseUrl, String authToken, HttpClient httpClient) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Base URL is mandatory for SS12000Client.");
        }
        if (!baseUrl.startsWith("https://")) {
            System.out.println("Warning: Base URL does not use HTTPS. All communication should occur over HTTPS " +
                    "in production environments to ensure security.");
        }
        if (authToken == null || authToken.isBlank()) {
            System.out.println("Warning: Authentication token is missing. Calls may fail if the API requires authentication.");
        }

        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authToken = authToken;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    private <T> CompletableFuture<T> requestAsync(String method, String path, Map<String, Object> queryParams, Object jsonContent, Class<T> responseType) {
        try {
            URI uri = buildUri(path, queryParams);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Accept", "application/json");

            if (authToken != null && !authToken.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + authToken);
            }

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
            if (jsonContent != null) {
                String jsonBody = objectMapper.writeValueAsString(jsonContent);
                bodyPublisher = HttpRequest.BodyPublishers.ofString(jsonBody);
                requestBuilder.header("Content-Type", "application/json");
            }

            HttpRequest request = requestBuilder.method(method, bodyPublisher).build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() >= 400) {
                            String errorBody = response.body();
                            System.err.println("API Error Response: " + errorBody);
                            throw new RuntimeException("HTTP Request Failed with status code: " + response.statusCode());
                        }

                        if (response.statusCode() == 204) {
                            return null;
                        }

                        try {
                            // Jackson by default is case-sensitive, but we can configure it to be case-insensitive if needed.
                            // For simplicity, we assume the API returns standard JSON keys.
                            return objectMapper.readValue(response.body(), responseType);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to deserialize response", e);
                        }
                    });

        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<Void> requestNoContentAsync(String method, String path, Map<String, Object> queryParams, Object jsonContent) {
        return requestAsync(method, path, queryParams, jsonContent, Void.class)
                .thenAccept(v -> {});
    }

    private URI buildUri(String path, Map<String, Object> queryParams) {
        StringBuilder uriBuilder = new StringBuilder(baseUrl + path);
        if (queryParams != null && !queryParams.isEmpty()) {
            uriBuilder.append("?");
            queryParams.forEach((key, value) -> {
                if (value == null) return;
                try {
                    if (value instanceof List<?>) {
                        ((List<?>) value).forEach(item -> uriBuilder.append(key).append("=").append(URLEncoder.encode(item.toString(), StandardCharsets.UTF_8)).append("&"));
                    } else if (value instanceof Boolean) {
                        uriBuilder.append(key).append("=").append(value.toString().toLowerCase()).append("&");
                    } else {
                        uriBuilder.append(key).append("=").append(URLEncoder.encode(value.toString(), StandardCharsets.UTF_8)).append("&");
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            if (uriBuilder.charAt(uriBuilder.length() - 1) == '&') {
                uriBuilder.deleteCharAt(uriBuilder.length() - 1);
            }
        }
        return URI.create(uriBuilder.toString());
    }

    // --- Organisation Endpoints ---

    public CompletableFuture<JsonNode> getOrganisationsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/organisations", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupOrganisationsAsync(Object body, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/organisations/lookup", queryParams, body, JsonNode.class);
    }

    public CompletableFuture<JsonNode> getOrganisationByIdAsync(String orgId, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/organisations/" + orgId, queryParams, null, JsonNode.class);
    }

    // --- Person Endpoints ---
    public CompletableFuture<JsonNode> getPersonsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/persons", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupPersonsAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/persons/lookup", queryParams, body, JsonNode.class);
    }

    public CompletableFuture<JsonNode> getPersonByIdAsync(String personId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/persons/" + personId, queryParams, null, JsonNode.class);
    }

    // --- Placements Endpoints ---
    public CompletableFuture<JsonNode> getPlacementsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/placements", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupPlacementsAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/placements/lookup", queryParams, body, JsonNode.class);
    }

    public CompletableFuture<JsonNode> getPlacementByIdAsync(String placementId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/placements/" + placementId, queryParams, null, JsonNode.class);
    }

    // --- Duties Endpoints ---
    public CompletableFuture<JsonNode> getDutiesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/duties", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupDutiesAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/duties/lookup", queryParams, body, JsonNode.class);
    }

    public CompletableFuture<JsonNode> getDutyByIdAsync(String dutyId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/duties/" + dutyId, queryParams, null, JsonNode.class);
    }

    // --- Groups Endpoints ---
    public CompletableFuture<JsonNode> getGroupsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/groups", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupGroupsAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/groups/lookup", queryParams, body, JsonNode.class);
    }

    public CompletableFuture<JsonNode> getGroupByIdAsync(String groupId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/groups/" + groupId, queryParams, null, JsonNode.class);
    }

    // --- Programmes Endpoints ---
    public CompletableFuture<JsonNode> getProgrammesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/programmes", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupProgrammesAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/programmes/lookup", queryParams, body, JsonNode.class);
    }

    public CompletableFuture<JsonNode> getProgrammeByIdAsync(String programmeId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/programmes/" + programmeId, queryParams, null, JsonNode.class);
    }

    // --- StudyPlans Endpoints ---
    public CompletableFuture<JsonNode> getStudyPlansAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/studyplans", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupStudyPlansAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/studyplans/lookup", queryParams, body, JsonNode.class);
    }

    public CompletableFuture<JsonNode> getStudyPlanByIdAsync(String studyPlanId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/studyplans/" + studyPlanId, queryParams, null, JsonNode.class);
    }

    // --- Syllabuses Endpoints ---
    public CompletableFuture<JsonNode> getSyllabusesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/syllabuses", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupSyllabusesAsync(Object body, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/syllabuses/lookup", queryParams, body, JsonNode.class);
    }

    public CompletableFuture<JsonNode> getSyllabusByIdAsync(String syllabusId, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/syllabuses/" + syllabusId, queryParams, null, JsonNode.class);
    }
    
    // --- SchoolUnitOfferings Endpoints ---
    public CompletableFuture<JsonNode> getSchoolUnitOfferingsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/schoolUnitOfferings", queryParams, null, JsonNode.class);
    }
    
    public CompletableFuture<JsonNode> lookupSchoolUnitOfferingsAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/schoolUnitOfferings/lookup", queryParams, body, JsonNode.class);
    }
    
    public CompletableFuture<JsonNode> getSchoolUnitOfferingByIdAsync(String offeringId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/schoolUnitOfferings/" + offeringId, queryParams, null, JsonNode.class);
    }

    // --- Activities Endpoints ---
    public CompletableFuture<JsonNode> getActivitiesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/activities", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupActivitiesAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/activities/lookup", queryParams, body, JsonNode.class);
    }

    public CompletableFuture<JsonNode> getActivityByIdAsync(String activityId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/activities/" + activityId, queryParams, null, JsonNode.class);
    }
    
    // --- CalendarEvents Endpoints ---
    public CompletableFuture<JsonNode> getCalendarEventsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/calendarEvents", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupCalendarEventsAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/calendarEvents/lookup", queryParams, body, JsonNode.class);
    }
    
    public CompletableFuture<JsonNode> getCalendarEventByIdAsync(String eventId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/calendarEvents/" + eventId, queryParams, null, JsonNode.class);
    }

    // --- Attendances Endpoints ---
    public CompletableFuture<JsonNode> getAttendancesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/attendances", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupAttendancesAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/attendances/lookup", queryParams, body, JsonNode.class);
    }

    public CompletableFuture<JsonNode> getAttendanceByIdAsync(String attendanceId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/attendances/" + attendanceId, queryParams, null, JsonNode.class);
    }

    public CompletableFuture<Void> deleteAttendanceAsync(String attendanceId) {
        return requestNoContentAsync("DELETE", "/attendances/" + attendanceId, null, null);
    }

    // --- AttendanceEvents Endpoints ---
    public CompletableFuture<JsonNode> getAttendanceEventsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/attendanceEvents", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupAttendanceEventsAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/attendanceEvents/lookup", queryParams, body, JsonNode.class);
    }
    
    public CompletableFuture<JsonNode> getAttendanceEventByIdAsync(String eventId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/attendanceEvents/" + eventId, queryParams, null, JsonNode.class);
    }

    // --- AttendanceSchedules Endpoints ---
    public CompletableFuture<JsonNode> getAttendanceSchedulesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/attendanceSchedules", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupAttendanceSchedulesAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/attendanceSchedules/lookup", queryParams, body, JsonNode.class);
    }

    public CompletableFuture<JsonNode> getAttendanceScheduleByIdAsync(String scheduleId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/attendanceSchedules/" + scheduleId, queryParams, null, JsonNode.class);
    }

    // --- Subscriptions Endpoints ---
    public CompletableFuture<JsonNode> getSubscriptionsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/subscriptions", queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> lookupSubscriptionsAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/subscriptions/lookup", queryParams, body, JsonNode.class);
    }

    public CompletableFuture<JsonNode> getSubscriptionByIdAsync(String subscriptionId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/subscriptions/" + subscriptionId, queryParams, null, JsonNode.class);
    }

    public CompletableFuture<JsonNode> createSubscriptionAsync(Object body) {
        return requestAsync("POST", "/subscriptions", null, body, JsonNode.class);
    }
    
    public CompletableFuture<JsonNode> updateSubscriptionAsync(String subscriptionId, Object body) {
        return requestAsync("PATCH", "/subscriptions/" + subscriptionId, null, body, JsonNode.class);
    }
    
    public CompletableFuture<Void> deleteSubscriptionAsync(String subscriptionId) {
        return requestNoContentAsync("DELETE", "/subscriptions/" + subscriptionId, null, null);
    }
    
    @Override
    public void close() {
        // HttpClient from Java 11 does not require manual closing.
        // This method is for compatibility with AutoCloseable.
    }
}