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

    /**
     * GET /organisations
     * - Accepts a map of query parameters (filters, pagination, expandReferenceNames, sortkey, limit, pageToken).
     * - Returns a paged JSON structure with organisation objects.
     *
     * Query Parameters for `queryParams` Map:
     * - **parent** (array of UUID strings): Limits the selection to specified organization IDs.
     * - **schoolUnitCode** (array of strings): Limits the selection to school units with the specified School Unit Code (an identifier for the school unit according to the Swedish National Agency for Education).
     * - **organisationCode** (array of strings): Limits the selection to organization elements with the specified code.
     * - **municipalityCode** (string): Limits the selection to organization elements with the specified municipality code.
     * - **type** (array of OrganisationTypeEnum): Limits the selection to specified types.
     * - **schoolTypes** (array of SchoolTypesEnum): Limits the selection to organization elements with the specified school type.
     * - **startDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to organization elements with a `startDate` value before or on the specified date. Records with an unset `endDate` are always included.
     * - **startDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to organization elements with a `startDate` value on or after the specified date. Records with an unset `endDate` are always included.
     * - **endDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to organization elements with an `endDate` value before or on the specified date. Records with an unset `endDate` are always included.
     * - **endDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to organization elements with an `endDate` value on or after the specified date. Records with an unset `endDate` are always included.
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "ModifiedDesc", "DisplayNameAsc"): Specifies the sorting order for the results.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters, but can be combined with `limit`.
     */

    public CompletableFuture<JsonNode> getOrganisationsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/organisations", queryParams, null, JsonNode.class);
    }

    /**
     * POST /organisations/lookup
     * 
     * Lookup multiple organisations in a single request.
     * @param body JSON body containing { "ids": [...] } (required)
     * @param expandReferenceNames if true, return displayName for referenced objects
     * @return CompletableFuture with JSON result
     * @see #getOrganisationsAsync(Map)
     */
    public CompletableFuture<JsonNode> lookupOrganisationsAsync(Object body, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/organisations/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /organisations/{orgId]}
     * 
     * Get a single organisation by id.
     * @param orgId UUID of the organisation
     * @param expandReferenceNames include displayName for referenced objects
     * @return CompletableFuture with JSON organisation
     */
    public CompletableFuture<JsonNode> getOrganisationByIdAsync(String orgId, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/organisations/" + orgId, queryParams, null, JsonNode.class);
    }

    // --- Person Endpoints ---

    /**
     * GET /persons
     * - Query: various filters (nameContains, civicNo, identifier.*), relationship filters,
     * meta timestamps, expand (array), expandReferenceNames, sortkey, limit, pageToken.
     *
     * Query Parameters for `queryParams` Map:
     * - **nameContains** (array of strings): Limits the selection to persons whose names contain any of the parameter values. The search **must** be case-insensitive, and the value can appear anywhere in any of the three name fields. If multiple values are provided, all values must match at least one of the name fields. Example: ["Pa", "gens"] will match Palle Girgensohn.
     * - **civicNo** (string): Limits the selection to the person whose civicNo matches the parameter's value.
     * - **eduPersonPrincipalName** (string): Limits the selection to the person whose eduPersonPrincipalNames match the parameter's value.
     * - **identifier.value** (string): Limits the selection to persons who have a value in `externalIdentifiers.value` that matches the parameter's value. Can be combined with the `identifier.context` parameter to limit the match to a specific type of identifier.
     * - **identifier.context** (string): Limits the selection to persons who have a value in `externalIdentifiers.context` that matches the parameter's value. Usually combined with the `identifier.value` parameter.
     * - **relationship.entity.type** (string, enum: "enrolment", "duty", "placement.child", "placement.owner", "responsibleFor.enrolment", "responsibleFor.placement", "groupMembership"): Limits the selection to persons who have this type of relationship to other entities. This parameter controls which entity type other relationship parameters filter on. If no other parameters are specified, persons with a relationship of this type are returned.  * This can be combined with `relationship.startDate.onOrBefore` and `relationship.endDate.onOrAfter` to limit to active relationships.
     * - **relationship.organisation** (UUID string): Limits the selection of persons to those who have a relationship with the specified organization element (usually a school unit). To limit to a specific relationship type, use the `relationship.entity.type` parameter. 
     * - **relationship.startDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection of persons to those who have relationships with a `startDate` before or on the specified date. Records with an unset `startDate` are always included. To limit to a specific relationship type, use the `relationship.entity.type` parameter.
     * - **relationship.startDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection of persons to those who have relationships with a `startDate` after or on the specified date. Records with an unset `startDate` are always included. To limit to a specific relationship type, use the `relationship.entity.type` parameter.
     * - **relationship.endDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection of persons to those who have relationships with an `endDate` before or on the specified date. Records with an unset `endDate` are always included. To limit to a specific relationship type, use the `relationship.entity.type` parameter. 
     * - **relationship.endDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection of persons to those who have relationships with an `endDate` after or on the specified date. Records with an unset `endDate` are always included. To limit to a specific relationship type, use the `relationship.entity.type` parameter. 
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expand** (array of strings, enum: "duties", "responsibleFor", "placements", "ownedPlacements", "groupMemberships"): Specifies whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "DisplayNameAsc", "GivenNameDesc", "GivenNameAsc", "FamilyNameDesc", "FamilyNameAsc", "CivicNoAsc", "CivicNoDesc", "ModifiedDesc"): Specifies how the result should be sorted.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getPersonsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/persons", queryParams, null, JsonNode.class);
    }

    /**
     * POST /persons/lookup
     * 
     * - **expand** (array of strings, enum: "duties", "responsibleFor", "placements", "ownedPlacements", "groupMemberships"): Specifies whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.

     * Lookup persons using a list of identifiers (IDs or personal numbers).
     * @param body JSON body required (ids array)
     * @param expand list of embedded expansions to request (nullable)
     * @param expandReferenceNames include displayName for referenced objects
     * @return CompletableFuture<JsonNode>
     */
    public CompletableFuture<JsonNode> lookupPersonsAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/persons/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /persons/{personId}

     * - **expand** (array of strings, enum: "duties", "responsibleFor", "placements", "ownedPlacements", "groupMemberships"): Specifies whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Get person by id.
     * @param personId UUID
     * @param expand embedded objects to expand (nullable)
     * @param expandReferenceNames include displayName fields for references
     * @return CompletableFuture<JsonNode>
     */
    public CompletableFuture<JsonNode> getPersonByIdAsync(String personId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/persons/" + personId, queryParams, null, JsonNode.class);
    }

    // --- Placements Endpoints ---

    /**
     * GET /placements
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a paged JSON structure with placement objects.
     *
     * Query Parameters for `queryParams` Map:
     * - **organisation** (UUID string): Limits the selection to children who have a placement (`placedAt`) at the specified organizational element. This can be combined with `startDate.onOrBefore` and `endDate.onOrAfter` to limit to active placements.
     * - **group** (UUID string): Limits the selection to children who have a placement at the specified group. This can be combined with `startDate.onOrBefore` and `endDate.onOrAfter` to limit to active placements.
     * - **startDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to placements that have a `startDate` value before or on the specified date.
     * - **startDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to placements that have a `startDate` value on or after the specified date.
     * - **endDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to placements that have an `endDate` value before or on the specified date. Records with an unset `endDate` are always included.
     * - **endDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to placements that have an `endDate` value on or after the specified date. Records with an unset `endDate` are always included.
     * - **child** (UUID string): Limits the selection to a specific child. This can be combined with `startDate.onOrBefore` and `endDate.onOrAfter` to limit to active placements.
     * - **owner** (UUID string): Limits the selection to placements with this owner. This can be combined with `startDate.onOrAfter` and `endDate.onOrBefore` to limit to active placements.
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expand** (array of strings, enum: "child", "owners"): Describes whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "StartDateAsc", "StartDateDesc", "EndDateAsc", "EndDateDesc", "ModifiedDesc"): Specifies how the result should be sorted.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters, but can be combined with `limit`.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     */
    public CompletableFuture<JsonNode> getPlacementsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/placements", queryParams, null, JsonNode.class);
    }

    /**
     * POST /placements/lookup
     * 
     * - **expand** (array of strings, enum: "child", "owners"): Describes whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup multiple placements in one call.
     * @param body JSON with ids (required)
     * @param expand embedded fields to expand
     * @param expandReferenceNames include displayName for references
     */
    public CompletableFuture<JsonNode> lookupPlacementsAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/placements/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /placements/{placementId}
     * 
     * - **expand** (array of strings, enum: "child", "owners"): Describes whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Get placement by id.
     * @param placementId UUID
     * @param expand expansions
     * @param expandReferenceNames include display names
     */
    public CompletableFuture<JsonNode> getPlacementByIdAsync(String placementId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/placements/" + placementId, queryParams, null, JsonNode.class);
    }

    // --- Duties Endpoints ---

    /**
     * GET /duties
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a paged JSON structure with duty objects.
     *
     * Query Parameters for `queryParams` Map:
     * - **organisation** (UUID string): Limits the selection to duties linked to an organizational element or its sub-elements.
     * - **dutyRole** (DutyRole object): Limits the selection to duties matching the specified role.
     * - **person** (UUID string): Limits the selection to duties linked to a specific person ID.
     * - **startDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to duties with a `startDate` value before or on the specified date.
     * - **startDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to duties with a `startDate` value on or after the specified date.
     * - **endDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to duties with an `endDate` value before or on the specified date. Records with an unset `endDate` are always included.
     * - **endDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to duties with an `endDate` value on or after the specified date. Records with an unset `endDate` are always included.
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expand** (array of strings, enum: "person"): Describes whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "StartDateDesc", "StartDateAsc", "ModifiedDesc"): Specifies how the result should be sorted.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getDutiesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/duties", queryParams, null, JsonNode.class);
    }

    /**
     * POST /duties/lookup
     * 
     * - **expand** (array of strings, enum: "person"): Describes whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup many duties in a single request.
     * @param body JSON body with ids array
     * @param expand expansions
     * @param expandReferenceNames include displayName for referenced objects
     */
    public CompletableFuture<JsonNode> lookupDutiesAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/duties/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /duties/{dutyId}
     * 
     * - **expand** (array of strings, enum: "person"): Describes whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Get a duty by id.
     * @param dutyId UUID
     * @param expand expansions
     * @param expandReferenceNames include display names
     */
    public CompletableFuture<JsonNode> getDutyByIdAsync(String dutyId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/duties/" + dutyId, queryParams, null, JsonNode.class);
    }

    // --- Groups Endpoints ---

    /**
     * GET /groups
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a paged JSON structure with group objects.
     *
     * Query Parameters for `queryParams` Map:
     * - **groupType** (array of GroupTypesEnum): Limits the selection to groups of one or more specified types.
     * - **schoolTypes** (array of SchoolTypesEnum): Limits the selection of groups to those that have one of the specified school types.
     * - **organisation** (array of UUID strings): Limits the selection to groups directly linked to the specified organizational elements.
     * - **startDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to groups with a `startDate` value before or on the specified date.
     * - **startDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to groups with a `startDate` value on or after the specified date.
     * - **endDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to groups with an `endDate` value before or on the specified date. Records with an unset `endDate` are always included.
     * - **endDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to groups with an `endDate` value on or after the specified date. Records with an unset `endDate` are always included.
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expand** (array of strings, enum: "assignmentRoles"): Describes whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "ModifiedDesc", "DisplayNameAsc", "StartDateAsc", "StartDateDesc", "EndDateAsc", "EndDateDesc"): Specifies how the result should be sorted.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getGroupsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/groups", queryParams, null, JsonNode.class);
    }

    /**
     * POST /groups/lookup
     * 
     * - **expand** (array of strings, enum: "assignmentRoles"): Describes whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup many groups by ids.
     * @param body JSON with ids array
     * @param expand expansions
     * @param expandReferenceNames include display names
     */
    public CompletableFuture<JsonNode> lookupGroupsAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/groups/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /groups/{groupId}
     * 
     * - **expand** (array of strings, enum: "assignmentRoles"): Describes whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Get group by id.
     * @param groupId UUID
     * @param expand expansions
     * @param expandReferenceNames include display names
     */
    public CompletableFuture<JsonNode> getGroupByIdAsync(String groupId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/groups/" + groupId, queryParams, null, JsonNode.class);
    }

    // --- Programmes Endpoints ---

    /**
     * GET /programmes
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a paged JSON structure with programme objects.
     *
     * Query Parameters for `queryParams` Map:
     * - **schoolType** (array of SchoolTypesEnum): Limits the selection to programmes matching the school type.
     * - **code** (string): Limits the selection to programmes matching the programme code.
     * - **parentProgramme** (string): Limits the selection to programmes matching the specified parent programme.
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "NameAsc", "CodeAsc", "ModifiedDesc"): Specifies how the result should be sorted.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getProgrammesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/programmes", queryParams, null, JsonNode.class);
    }

    /**
     * POST /programmes/lookup
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup many programmes.
     * @param body JSON body with ids
     * @param expandReferenceNames include display names
     */
    public CompletableFuture<JsonNode> lookupProgrammesAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/programmes/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /programmes/{programmeId}
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Get programme by id.
     * @param programmeId UUID
     * @param expandReferenceNames include displayName for refs
     */
    public CompletableFuture<JsonNode> getProgrammeByIdAsync(String programmeId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/programmes/" + programmeId, queryParams, null, JsonNode.class);
    }

    // --- StudyPlans Endpoints ---

    /**
     * GET /studyplans
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a paged JSON structure with study plan objects.
     *
     * Query Parameters for `queryParams` Map:
     * - **student** (array of UUID strings): Limits the selection to specified students.
     * - **startDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to study plans with a `startDate` value before or on the specified date. Records with an unset `endDate` are always included.
     * - **startDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to study plans with a `startDate` value on or after the specified date. Records with an unset `endDate` are always included.
     * - **endDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to study plans with an `endDate` value before or on the specified date. Records with an unset `endDate` are always included.
     * - **endDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to study plans with an `endDate` value on or after the specified date. Records with an unset `endDate` are always included.
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "ModifiedDesc", "StartDateAsc", "StartDateDesc", "EndDateAsc", "EndDateDesc"): Specifies how the result should be sorted.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getStudyPlansAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/studyplans", queryParams, null, JsonNode.class);
    }
    /**
     * POST /studyplans/lookup
     * 
     * lookup is not part of the SS12000 standard for studyplans.
     * @param body
     * @return
    public CompletableFuture<JsonNode> lookupStudyPlansAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/studyplans/lookup", queryParams, body, JsonNode.class);
    }
     */

    /**
     * GET /studyplans/{studyplanId}
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup syllabuses by ids.
     * @param body JSON body with ids array
     * @param expandReferenceNames include displayName for references
     */
    public CompletableFuture<JsonNode> getStudyPlanByIdAsync(String studyPlanId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/studyplans/" + studyPlanId, queryParams, null, JsonNode.class);
    }

    // --- Syllabuses Endpoints ---

    /**
     * GET /syllabuses
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a paged JSON structure with syllabus objects.
     *
     * Query Parameters for `queryParams` Map:
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "ModifiedDesc", "SubjectNameAsc", "SubjectNameDesc", "SubjectCodeAsc", "SubjectCodeDesc", "CourseNameAsc", "CourseNameDesc", "CourseCodeAsc", "CourseCodeDesc", "SubjectDesignationAsc", "SubjectDesignationDesc"): Specifies how the result should be sorted.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getSyllabusesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/syllabuses", queryParams, null, JsonNode.class);
    }

    /**
     * POST /syllabuses/lookup
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup syllabuses by ids.
     * @param body JSON body with ids array
     * @param expandReferenceNames include displayName for references
     */
    public CompletableFuture<JsonNode> lookupSyllabusesAsync(Object body, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/syllabuses/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /syllabuses/{syllabusId}
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Get syllabus by id.
     * @param syllabusId UUID
     * @param expandReferenceNames include displayName for referenced objects
     */
    public CompletableFuture<JsonNode> getSyllabusByIdAsync(String syllabusId, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/syllabuses/" + syllabusId, queryParams, null, JsonNode.class);
    }
    
    // --- SchoolUnitOfferings Endpoints ---

    /**
     * GET /schoolUnitOfferings
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a paged JSON structure with school unit offering objects.
     *
     * Query Parameters for `queryParams` Map:
     * - **organisation** (UUID string): Limits the selection to a specific organizational element (`offeredAt`).
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "ModifiedDesc", "StartDateAsc", "StartDateDesc", "EndDateAsc", "EndDateDesc"): Specifies how the result should be sorted.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getSchoolUnitOfferingsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/schoolUnitOfferings", queryParams, null, JsonNode.class);
    }
    
    /**
     * POST /schoolUnitOfferings/lookup
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup many offerings.
     * @param body JSON with ids array
     * @param expand expansions
     * @param expandReferenceNames include displayName
     */
    public CompletableFuture<JsonNode> lookupSchoolUnitOfferingsAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/schoolUnitOfferings/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /schoolUnitOfferings/{offeringId}
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Get a single school unit offering by id.
     * @param offeringId UUID
     * @param expand expansions
     * @param expandReferenceNames include displayName
     */
    public CompletableFuture<JsonNode> getSchoolUnitOfferingByIdAsync(String offeringId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/schoolUnitOfferings/" + offeringId, queryParams, null, JsonNode.class);
    }

    // --- Activities Endpoints ---

    /**
     * GET /activities
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a paged JSON structure with activity objects.
     *
     * Query Parameters for `queryParams` Map:
     * - **member** (UUID string): Limits the selection to activities whose groups (`groups`) include this person. Time limitations (`startDate`, `endDate`) are not applied for this filter.
     * - **teacher** (UUID string): Limits the selection to activities whose teachers (`teachers`) include this ID in the `duty.id` attribute. Time limitations (`startDate`, `endDate`) are not applied for this filter.
     * - **organisation** (UUID string): Limits the selection to the specified organizational element and its sub-elements.
     * - **group** (UUID string): Limits the selection to the specified group.
     * - **startDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to activities with a `startDate` value before or on the specified date.
     * - **startDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to activities with a `startDate` value on or after the specified date.
     * - **endDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to activities with an `endDate` value before or on the specified date. Records with an unset `endDate` are always included.
     * - **endDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to activities with an `endDate` value on or after the specified date. Records with an unset `endDate` are always included.
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expand** (array of strings, enum: "groups", "teachers", "syllabus"): Describes whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "ModifiedDesc", "DisplayNameAsc"): Specifies how the result should be sorted.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getActivitiesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/activities", queryParams, null, JsonNode.class);
    }

    /**
     * POST /activities/lookup
     * 
     * - **expand** (array of strings, enum: "groups", "teachers", "syllabus"): Describes whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup activities in bulk.
     * @param body JSON with ids array
     * @param expand list of embedded expansions
     * @param expandReferenceNames include displayName for referenced objects
     */
    public CompletableFuture<JsonNode> lookupActivitiesAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/activities/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /activities/{activityId}
     * 
     * - **expand** (array of strings, enum: "groups", "teachers", "syllabus"): Describes whether expanded data should be retrieved.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Get activity by id.
     * @param activityId UUID
     * @param expand expansions
     * @param expandReferenceNames include display names
     */
    public CompletableFuture<JsonNode> getActivityByIdAsync(String activityId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/activities/" + activityId, queryParams, null, JsonNode.class);
    }
    
    // --- CalendarEvents Endpoints ---

    /**
     * GET /calendarEvents
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a paged JSON structure with calendar event objects.
     *
     * Query Parameters for `queryParams` Map:
     * - **startTime.onOrAfter** (date-time string, RFC 3339 format, e.g., "2016-10-15T09:00:00+02:00"): Retrieve calendar events from and including this time. **Required.**
     * - **startTime.onOrBefore** (date-time string, RFC 3339 format, e.g., "2016-10-15T09:00:00+02:00"): Retrieve calendar events up to and including this time. **Required.**
     * - **endTime.onOrBefore** (date-time string, RFC 3339 format, e.g., "2016-10-15T11:00:00+02:00"): Retrieve calendar events up to and including this time.
     * - **endTime.onOrAfter** (date-time string, RFC 3339 format, e.g., "2016-10-15T11:00:00+02:00"): Retrieve calendar events from and including this time.
     * - **activity** (UUID string): Limits the selection to the specified activity.
     * - **student** (UUID string): Limits the selection to calendar events whose activities' groups (`activity.group` => `group.groupMemberships.person.id`) or `studentExceptions.student.id` include this person. Time limitations (`startDate`, `endDate`) are not applied for this filter.
     * - **teacher** (UUID string): Limits the selection to calendar events whose activities' teachers (`activity.teachers.duty.id`) and `teacherExceptions.duty.id` include this duty (`duty.id`). Time limitations (`startDate`, `endDate`) are not applied for this filter.
     * - **organisation** (UUID string): Limits the selection to the specified organizational element and its sub-elements.
     * - **group** (UUID string): Limits the selection to the specified group, related through linked activities.
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expand** (array of strings, enum: "activity", "attendance"): Describes whether expanded data should be retrieved for the activity.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "ModifiedDesc", "StartTimeAsc", "StartTimeDesc"): Specifies how the result should be sorted.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getCalendarEventsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/calendarEvents", queryParams, null, JsonNode.class);
    }

    /**
     * POST /calendarEvents/lookup
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup calendar events in bulk.
     * @param body JSON with ids array (required)
     * @param expandReferenceNames include displayName for references
     */
    public CompletableFuture<JsonNode> lookupCalendarEventsAsync(Object body, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/calendarEvents/lookup", queryParams, body, JsonNode.class);
    }
    
    /**
     * GET /calendarEvents/{eventId}
     * 
     * - **expand** (array of strings, enum: "activity", "attendance"): Describes whether expanded data should be retrieved for the activity.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Get single calendar event by id.
     * @param eventId UUID
     * @param expand expansions
     * @param expandReferenceNames include displayName fields
     */
    public CompletableFuture<JsonNode> getCalendarEventByIdAsync(String eventId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/calendarEvents/" + eventId, queryParams, null, JsonNode.class);
    }

    // --- Attendances Endpoints ---

    /**
     * GET /attendances
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns registered attendance based on an activity or student.
     *
     * Query Parameters for `queryParams` Map:
     * - **student** (UUID string): Limits the selection to the specified person.
     * - **organisation** (UUID string): Limits the selection to the specified organizational element and its sub-elements.
     * - **calendarEvent** (UUID string): Limits the selection to the specified calendar event.
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getAttendancesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/attendances", queryParams, null, JsonNode.class);
    }

    /**
     * POST /attendances
     * 
     * Create (register) a new attendance.
     * @param body Attendance JSON (required)
     * @return CompletableFuture<JsonNode> representing created attendance or server response
     */
    public CompletableFuture<JsonNode> createAttendanceAsync(Object body) {
        return requestAsync("POST", "/attendances", null, body, JsonNode.class);
    }

    /**
     * POST /attendances/lookup
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup many attendances.
     * @param body JSON with ids array
     * @param expand expansions to include
     * @param expandReferenceNames include displayName fields
     */
    public CompletableFuture<JsonNode> lookupAttendancesAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/attendances/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /attendances/{attendanceId}
     * 
     * Get attendance by id.
     * @param attendanceId UUID
     * @param expand expansions
     * @param expandReferenceNames include display names
     */
    public CompletableFuture<JsonNode> getAttendanceByIdAsync(String attendanceId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/attendances/" + attendanceId, queryParams, null, JsonNode.class);
    }
    
    /**
     * DELETE /attendance/{attendanceId}
     * 
     * Delete an attendance by id.
     * @param attendanceId UUID
     * @return CompletableFuture<Void> completes on 204 No Content
     */
    public CompletableFuture<Void> deleteAttendanceAsync(String attendanceId) {
        return requestNoContentAsync("DELETE", "/attendances/" + attendanceId, null, null);
    }

    // --- AttendanceEvents Endpoints ---

    /**
     * GET /attendanceEvents
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns registered check-ins/check-outs.
     *
     * Query Parameters for `queryParams` Map:
     * - **group** (array of UUID strings): Limits the selection to the IDs of specified groups.
     * - **person** (UUID string): Limits the selection to the specified person.
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expand** (array of strings, enum: "person", "group", "registeredBy"): Describes whether expanded data should be retrieved for the activity.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getAttendanceEventsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/attendanceEvents", queryParams, null, JsonNode.class);
    }

    /**
     * POST /attendanceEvents
     * 
     * Create an attendance event (in/out check).
     * @param body AttendanceEvent JSON (required)
     * @return CompletableFuture<JsonNode>
     */
    public CompletableFuture<JsonNode> createAttendanceEventAsync(Object body) {
        return requestAsync("POST", "/attendanceEvents", null, body, JsonNode.class);
    }

    /**
     * POST /attendanceEvents/lookup
     *
     * - **expand** (array of strings, enum: "person", "group", "registeredBy"): Describes whether expanded data should be retrieved for the activity.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     *
     * Lookup many attendance events.
     * @param body JSON with ids array
     * @param expand expansions
     * @param expandReferenceNames include display names
     */
    public CompletableFuture<JsonNode> lookupAttendanceEventsAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/attendanceEvents/lookup", queryParams, body, JsonNode.class);
    }
    
    /**
     * GET /attendanceEvents/{eventId}
     * 
     * - **expand** (array of strings, enum: "person", "group", "registeredBy"): Describes whether expanded data should be retrieved for the activity.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Get a single attendance event.
     * @param eventId UUID
     * @param expand expansions
     * @param expandReferenceNames include display names
     */
    public CompletableFuture<JsonNode> getAttendanceEventByIdAsync(String eventId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/attendanceEvents/" + eventId, queryParams, null, JsonNode.class);
    }

    /**
     * DELETE /attendanceEvents/{eventId}
     * 
     * Delete attendance event by id.
     * @param eventId UUID
     */
    public CompletableFuture<Void> deleteAttendanceEventAsync(String eventId) {
        return requestNoContentAsync("DELETE", "/attendanceEvents/" + eventId, null, null);
    }

    // --- AttendanceSchedules Endpoints ---

    /**
     * GET /attendanceSchedule
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns registered attendance schedules.
     *
     * Query Parameters for `queryParams` Map:
     * - **placement** (UUID string): Limits the selection to schedules for the specified placement.
     * - **group** (UUID string): Limits the selection to schedules whose placements are linked to the specified group.
     * - **startDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to attendance schedules with a `startDate` value before or on the specified date.
     * - **startDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to attendance schedules with a `startDate` value on or after the specified date.
     * - **endDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to attendance schedules with an `endDate` value before or on the specified date. Records with an unset `endDate` are always included.
     * - **endDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection to attendance schedules with an `endDate` value on or after the specified date. Records with an unset `endDate` are always included.
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getAttendanceSchedulesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/attendanceSchedules", queryParams, null, JsonNode.class);
    }

    /**
     * POST /attendanceSchedules
     * 
     * Create a new attendance schedule.
     * @param body AttendanceSchedule JSON (required)
     */
    public CompletableFuture<JsonNode> createAttendanceScheduleAsync(Object body) {
        return requestAsync("POST", "/attendanceSchedules", null, body, JsonNode.class);
    }

    /**
     * POST /attendanceSchedules/lookup
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup many attendance schedules.
     * @param body JSON with ids array
     * @param expand expansions
     * @param expandReferenceNames include display names
     */
    public CompletableFuture<JsonNode> lookupAttendanceSchedulesAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/attendanceSchedules/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /attendanceSchedules/{scheduleId}
     * 
     * Get a single attendance schedule.
     * @param scheduleId UUID
     * @param expand expansions
     * @param expandReferenceNames include display names
     */
    public CompletableFuture<JsonNode> getAttendanceScheduleByIdAsync(String scheduleId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/attendanceSchedules/" + scheduleId, queryParams, null, JsonNode.class);
    }

    /**
     * DELETE /attendanceSchedules/{scheduleId}
     * 
     * Delete an attendance schedule (note singular path).
     * @param scheduleId UUID
     */
    public CompletableFuture<Void> deleteAttendanceScheduleAsync(String scheduleId) {
        return requestNoContentAsync("DELETE", "/attendanceSchedule/" + scheduleId, null, null);
    }

    // --- Grades Endpoints ---

    /**
     * GET /grades
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a list of grades.
     *
     * Query Parameters for `queryParams` Map:
     * - **organisation** (UUID string): Limits the selection to grades linked to the school unit.
     * - **student** (UUID string): Limits the selection to grades belonging to the student.
     * - **registeredBy** (UUID string): Limits the selection to grades registered by the person with this ID.
     * - **gradingTeacher** (UUID string): Limits the selection to grades issued by the responsible teacher with this ID.
     * - **registeredDate.onOrAfter** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection of grades to those registered within the interval starting on the specified date. Inclusive.
     * - **registeredDate.onOrBefore** (date string, RFC 3339 format, e.g., "2016-10-15"): Limits the selection of grades to those registered within the interval ending on the specified date. Inclusive.
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "registeredDateAsc", "registeredDateDesc", "ModifiedDesc"): Specifies how the result should be sorted.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getGradesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/grades", queryParams, null, JsonNode.class);
    }

    /**
     * POST /grades/lookup
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup many grades.
     * @param body JSON with ids array (required)
     * @param expandReferenceNames include display names
     */
    public CompletableFuture<JsonNode> lookupGradesAsync(Object body, bool expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/grades/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /grades/{gradeId}
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Get grade by id.
     * @param gradeId UUID
     * @param expandReferenceNames include display names (optional)
     */
    public CompletableFuture<JsonNode> getGradeByIdAsync(String gradeId) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/grades/" + gradeId, queryParams, JsonNode.class);
    }

    // --- Absences Endpoints ---

    /**
     * GET /absences
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a list of granted leave and reported absences.
     *
     * Query Parameters for `queryParams` Map:
     * - **organisation** (UUID string): Limits the selection to absences/leave linked to the organization.
     * - **student** (UUID string): Limits the selection to absences/leave linked to the student.
     * - **registeredBy** (UUID string): Limits the selection to absences/leave registered by the person with this ID.
     * - **type** (AbsenceEnum object): Limits the selection to absences/leave of the specified type.
     * - **startTime.onOrBefore** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only reported absences starting before or at this time. Inclusive.
     * - **startTime.onOrAfter** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only reported absences/leave starting after this time. Exclusive.
     * - **endTime.onOrBefore** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only reported absences ending before or at this time. Inclusive.
     * - **endTime.onOrAfter** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only reported absences/leave ending after this time. Exclusive.
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "ModifiedDesc", "StartTimeAsc", "StartTimeDesc"): Specifies how the result should be sorted.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
        public CompletableFuture<JsonNode> getAbsencesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/absences", queryParams, JsonNode.class);
    }

    /**
     * POST /absence
     * 
     * Create an absence (registered absence or granted leave).
     * @param body Absence JSON (required)
     */
    public CompletableFuture<JsonNode> createAbsenceAsync(Object body) {
        return requestAsync("POST", "/absences/", null, body, JsonNode.class);
    } 

    /**
     * POST /absence/lookup
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup many absences.
     * @param body JSON with ids array
     * @param expandReferenceNames include display names
     */
    public CompletableFuture<JsonNode> lookupAbsencesAsync(Object body, boolean expandReferenceNames) {
        Map<String, Object> queryparams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/absences/lookup", queryparams, body, JsonNode.class);
    }

    /**
     * GET /absence/{absenceId}
     * 
     * Get a single absence by id.
     * @param absenceId UUID
     */
    public CompletableFuture<JsonNode> getAbsenceByIdAsync(String absenceId) {
        return requestAsync("GET", "/absences/" + absenceId, null, JsonNode.class);
    }

    // --- AggreagatedAttendance Enpoint --

    /**
     * GET /aggregatedAttendance
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns summarized attendance information based on the specified time interval.
     *
     * Query Parameters for `queryParams` Map:
     * - **startDate** (date string, RFC 3339 format, e.g., "2016-10-15"): Retrieve aggregated attendance from and including this date. **Required.**
     * - **endDate** (date string, RFC 3339 format, e.g., "2016-10-15"): Retrieve aggregated attendance up to and including this date. **Required.**
     * - **organisation** (UUID string): Only include attendance from activities owned by the specified organizational element.
     * - **schoolType** (array of SchoolTypesEnum): Only retrieve attendance information from activities linked to the specified school type.
     * - **student** (array of UUID strings): Filter by student (person).
     * - **expand** (array of strings, enum: "activity", "student"): Describes whether and what expanded data is returned with the attendance information.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getAggregatedAttendanceAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/aggregatedAttendance", queryParams, JsonNode.class);
    }

    /**
     * Lookup and individual id not part of the SS12000 standard, for obvious reasons 
     */

    // --- Resources Endpoints ---

    /**
     * GET /resources
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a list of resources.
     *
     * Query Parameters for `queryParams` Map:
     * - **organisation** (UUID string): Limits the selection to a specific organizational element (owner).
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "ModifiedDesc", "DisplayNameAsc"): Specifies how the result should be sorted.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getResourcesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/resources", queryParams, null, JsonNode.class);
    }

    /**
     * POST /resources/lookup
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup resources in bulk.
     * @param body JSON with ids array
     * @param expandReferenceNames include displayName fields
     */
    public CompletableFuture<JsonNode> lookupResourcesAsync(Object body, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/resources/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /resources/{resourceId}
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Get resource by id.
     * @param resourceId UUID
     * @param expandReferenceNames include displayName
     */
    public CompletableFuture<JsonNode> getResourceByIdAsync(String resourceId, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/resources/" + resourceId, queryParams, null, JsonNode.class);
    }

    // --- Rooms Endpoints ---

    /**
     * GET /rooms
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a list of rooms.
     *
     * Query Parameters for `queryParams` Map:
     * - **organisation** (UUID string): Limits the selection to a specific organizational element (owner).
     * - **meta.created.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created on or before this timestamp. Inclusive.
     * - **meta.created.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records created after this timestamp. Exclusive.
     * - **meta.modified.before** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified on or before this timestamp. Inclusive.
     * - **meta.modified.after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Only records modified after this timestamp. Exclusive.
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * - **sortkey** (string, enum: "ModifiedDesc", "DisplayNameAsc"): Specifies how the result should be sorted.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getRoomsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/rooms", queryParams, null, JsonNode.class);
    }

    /**
     * POST /rooms/lookup
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Lookup rooms in bulk.
     * @param body JSON with ids array
     * @param expandReferenceNames include displayName
     */
    public CompletableFuture<JsonNode> lookupRoomsAsync(Object body, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/rooms/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /rooms/{roomId}
     * 
     * - **expandReferenceNames** (boolean): Returns `displayName` for all referenced objects.
     * 
     * Get a single room by id.
     * @param roomId UUID
     * @param expandReferenceNames include displayName
     */
    public CompletableFuture<JsonNode> getRoomByIdAsync(String roomId, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/rooms/" + roomId, queryParams, null, JsonNode.class);
    }

    // --- Subscriptions Endpoints ---

    /**
     * GET /subscriptions
     * 
     * Query Parameters for `queryParams` Map:
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
    public CompletableFuture<JsonNode> getSubscriptionsAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/subscriptions", queryParams, null, JsonNode.class);
    }

    /**
     * POST /subscriptions/lookup
     * 
     * Lookup subscriptions (if provider supports lookup).
     * @param body JSON body
     */
    public CompletableFuture<JsonNode> lookupSubscriptionsAsync(Object body, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("POST", "/subscriptions/lookup", queryParams, body, JsonNode.class);
    }

    /**
     * GET /subscriptions/{subscriptionId}
     * 
     * Get a subscription by id.
     * @param subscriptionId
     */
    public CompletableFuture<JsonNode> getSubscriptionByIdAsync(String subscriptionId, List<String> expand, boolean expandReferenceNames) {
        Map<String, Object> queryParams = new HashMap<>();
        if (expand != null) queryParams.put("expand", expand);
        if (expandReferenceNames) queryParams.put("expandReferenceNames", true);
        return requestAsync("GET", "/subscriptions/" + subscriptionId, queryParams, null, JsonNode.class);
    }

    /**
     * POST /subscriptions
     * 
     * Create a subscription.
     * @param body CreateSubscription JSON (required): { name, resourceTypes, target, ... }
     */
    public CompletableFuture<JsonNode> createSubscriptionAsync(Object body) {
        return requestAsync("POST", "/subscriptions", null, body, JsonNode.class);
    }
        
    /**
     * PATCH /subscriptions/{subscriptionId}
     * 
     * Update a subscription (PATCH) - typically used to extend expires.
     * @param subscriptionId UUID
     * @param body Partial subscription payload (PATCH semantics)
     */
    public CompletableFuture<JsonNode> updateSubscriptionAsync(String subscriptionId, Object body) {
        return requestAsync("PATCH", "/subscriptions/" + subscriptionId, null, body, JsonNode.class);
    }
        
    /**
     * DELETE /subscriptions/{subscriptionId}
     * 
     * Delete a subscription by id.
     * @param subscriptionId
     */
    public CompletableFuture<Void> deleteSubscriptionAsync(String subscriptionId) {
        return requestNoContentAsync("DELETE", "/subscriptions/" + subscriptionId, null, null);
    }
    
     // --- Log Endpoint ---

    /**
     * POST /log
     *   - Body: LogEntry JSON (required) - severityLevel, message, optional resourceType, etc.
     *   - Returns 201 on success.
     */
    public CompletableFuture<JsonNode> postLogAsync(Object body) {
        return requestAsync("POST", "/log/", null, body, JsonNode.class);
    }

     // --- Statistics Endpoint ---

    /**
     * POST /statistics
     *   - Body: StatisticsEntry JSON (required) - newCount, updatedCount, deletedCount, resourceType, ...
     *   - Returns 201 on success.
     */
     public CompletableFuture<JsonNode> postStatisticsAsync(Object body) {
        return requestAsync("POST", "/statistics/", null, body, JsonNode.class);
    }

     // --- DeletedEntities Endpoint ---

    /**
     * GET /deletedEntities
     * - Accepts a map of query parameters for filtering and pagination.
     * - Returns a list of entities deleted by the service.
     *
     * Query Parameters for `queryParams` Map:
     * - **after** (date-time string, RFC 3339 format, e.g., "2015-12-12T10:30:00+01:00"): Retrieve deletions that occurred after the specified time.
     * - **entities** (array of EndPointsEnum): A list of entity types whose deletions should be retrieved.
     * - **limit** (integer, minimum 1): The number of records to display in the result. If omitted, the server returns as many records as possible; see `pageToken`.
     * - **pageToken** (string): An opaque value provided by the server as a response to a previous query. Cannot be combined with other filters but can be combined with `limit`.
     */
     public CompletableFuture<JsonNode> getDeletedEntitiesAsync(Map<String, Object> queryParams) {
        return requestAsync("GET", "/deletedEntities", queryParams, null, JsonNode.class);
    }

    @Override
    public void close() {
        // HttpClient from Java 11 does not require manual closing.
        // This method is for compatibility with AutoCloseable.
    }
}