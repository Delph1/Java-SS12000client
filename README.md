# **SS12000 Java Client Library**

This is a Node.js client package (or library if you will) that aims to simplify the integration with a server running a SS12000 compatible API. It is based on the OpenAPI 3 specification basically and it is really not that complicated to be honest. The package handles HTTP  calls and Bearer Token authentication, and is designed to provide a structured method for integrating with all of the defined endpoints of the standard. 

You can download your own personal copy of the SS12000 standard for free from here: [sis.se](https://www.sis.se/standarder/kpenstandard/forkopta-standarder/informationshantering-inom-utbildningssektorn/).

### **Important**

The SS12000 does not require the server to support all of the endpoints. You need to actually look at the server documentation to see which endpoints that are actually available with each service. Adding some sort of discovery service is beyond the scope of this small library in my humble opinion.

All dates are in the RFC 3339 format, we're not cavemen here. 

## **Content**

- [**SS12000 Java Client Library**](#ss12000-java-client-library)
    - [**Important**](#important)
  - [**Content**](#content)
  - [**Installation**](#installation)
    - [**Usage**](#usage)
    - [**Initiate the client**](#initiate-the-client)
    - [**Fetch Organisations**](#fetch-organisations)
    - [**Fetch Persons**](#fetch-persons)
    - [**Fetch ...**](#fetch-)
    - [**Webhooks for Subscriptions**](#webhooks-for-subscriptions)
  - [**API reference**](#api-reference)
  - [**Webhooks receiver (example)**](#webhooks-receiver-example)
  - [**Contribute**](#contribute)
  - [**License**](#license)

---

## **Installation**

1. Add this to your Maven project pom.xaml file.
```
<dependencies>  
    <dependency>  
        <groupId\>com.fasterxml.jackson.core</groupId>  
        <artifactId>jackson-databind</artifactId>  
        <version>2.16.1</version> <!-- Use the latest version -->  
    </dependency>  
</dependencies>
```

2. Copy the file SS12000Client.java to your project folder.  

### **Usage**

### **Initiate the client**

```
import com.fasterxml.jackson.databind.JsonNode;  
import java.util.concurrent.ExecutionException;

public class Main {  
    public static void main(String[] args) throws ExecutionException, InterruptedException {  
        final String baseUrl = "https://some.server.se/v2.0";  
        final String authToken = "YOUR_JWT_TOKEN_HERE";     

        try (SS12000Client client = new SS12000Client(baseUrl, authToken)) {  
            // Code goes here...  
            fetchOrganizationData(client);  
            fetchPersonData(client);  
            manageSubscriptions(client);  
        } catch (Exception e) {  
            System.err.println("An unexpected error occured: " + e.getMessage());  
            e.printStackTrace();  
        }  
    }  
    // ... (more methods below nedan)  
}
```

### **Fetch Organisations**

You can fetch a list of organisations or a specific organisation using its ID.  Parameters are sent as a Map<String, Object>.
```  
import java.util.HashMap;  
import java.util.Map;  
import com.fasterxml.jackson.databind.ObjectMapper;  
import com.fasterxml.jackson.databind.SerializationFeature;

public static void fetchOrganizationData(SS12000Client client) throws ExecutionException, InterruptedException {  
    try {  
        System.out.println("\nFetching organisations...");  
        Map<String, Object> queryParams = new HashMap<>();  
        queryParams.put("limit", 2);  
        JsonNode organizations = client.getOrganisationsAsync(queryParams).get();  
          
        ObjectMapper mapper = new ObjectMapper();  
        mapper.enable(SerializationFeature.INDENT_OUTPUT);  
        System.out.println("Fetched organisations:\n" + mapper.writeValueAsString(organizations));  
          
        if (organizations.has("data") && organizations.get("data").isArray() && organizations.get("data").size() > 0) {  
            String firstOrgId = organizations.get("data").get(0).get("id").asText();  
            System.out.println("\nFetching organisation with ID: " + firstOrgId + "...");  
            JsonNode orgById = client.getOrganisationByIdAsync(firstOrgId, true).get(); // expandReferenceNames = true  
            System.out.println("Fetched organisation with ID:\n" + mapper.writeValueAsString(orgById));  
        }  
    } catch (Exception e) {  
        System.err.println("An error occured when fetching organisation data: " + e.getMessage());  
    }  
}
```
### **Fetch Persons**
In the same manner you can fetch persons and expanded related data, e.g. duties.  
```
import java.util.List;

public static void fetchPersonData(SS12000Client client) throws ExecutionException, InterruptedException {  
    try {  
        System.out.println("\nFetching persons...");  
        Map<String, Object> queryParams = new HashMap<>();  
        queryParams.put("limit", 2);  
        queryParams.put("expand", List.of("duties"));  
        JsonNode persons = client.getPersonsAsync(queryParams).get();  
          
        ObjectMapper mapper = new ObjectMapper();  
        mapper.enable(SerializationFeature.INDENT_OUTPUT);  
        System.out.println("Fetched persons:\n" + mapper.writeValueAsString(persons));  
          
        if (persons.has("data") && persons.get("data").isArray() && persons.get("data").size() > 0) {  
            String firstPersonId = persons.get("data").get(0).get("id").asText();  
            System.out.println("\nFetching person with ID: " + firstPersonId + "...");  
            List<String> expands = List.of("duties", "responsibleFor");  
            JsonNode personById = client.getPersonByIdAsync(firstPersonId, expands, true).get();  
            System.out.println("Fetched person with ID:\n" + mapper.writeValueAsString(personById));  
        }  
    } catch (Exception e) {  
        System.err.println("An error occured when fetching person data: " + e.getMessage());  
    }  
}
```

### **Fetch ...**

Check the API reference below to see all available nodes. 

### **Webhooks for Subscriptions**

The client also contains methods to manage subscriptions (webhooks).
```
import com.fasterxml.jackson.databind.node.ObjectNode;

public static void manageSubscriptions(SS12000Client client) throws ExecutionException, InterruptedException {  
    try {  
        System.out.println("\nFetching subscriptions...");  
        JsonNode subscriptions = client.getSubscriptionsAsync(null).get();  
          
        ObjectMapper mapper = new ObjectMapper();  
        mapper.enable(SerializationFeature.INDENT_OUTPUT);  
        System.out.println("Fetched subscriptions:\n" + mapper.writeValueAsString(subscriptions));  
          
        // Example: Create a subscription  
        System.out.println("\nCreates a subscription...");  
        ObjectNode newSubscriptionData = mapper.createObjectNode();  
        newSubscriptionData.put("name", "My Java Test Subscription");  
        newSubscriptionData.put("target", "http://public-webhook-url.com/ss12000-webhook");  
        ObjectNode resourceType1 = mapper.createObjectNode();  
        resourceType1.put("resource", "Person");  
        ObjectNode resourceType2 = mapper.createObjectNode();  
        resourceType2.put("resource", "Activity");  
        newSubscriptionData.putArray("resourceTypes").add(resourceType1).add(resourceType2);  
          
        JsonNode newSubscription = client.createSubscriptionAsync(newSubscriptionData).get();  
        System.out.println("Created subscription:\n" + mapper.writeValueAsString(newSubscription));  
          
        // Example: Delete a subscription  
        if (subscriptions.has("data") && subscriptions.get("data").isArray() && subscriptions.get("data").size() > 0) {  
            String subToDeleteId = subscriptions.get("data").get(0).get("id").asText();  
            System.out.println("\nDelete subscription with ID: " + subToDeleteId + "...");  
            client.deleteSubscriptionAsync(subToDeleteId).get();  
            System.out.println("Subscription deleted successfully.");  
        }  
    } catch (Exception e) {  
        System.err.println("An error occured when managing subscriptions: " + e.getMessage());  
    }  
}
```

## **API reference**

SS12000Client is designed to expose methods for all SS12000 API endpoints. Here is a list of the primary endpoints that are defined in the OpenAPI specification:

* /organisations  
  * getOrganisationsAsync(Map\<String, Object\> queryParams)  
  * lookupOrganisationsAsync(Object body, boolean expandReferenceNames)  
  * getOrganisationByIdAsync(String orgId, boolean expandReferenceNames)  
* /persons  
  * getPersonsAsync(Map\<String, Object\> queryParams)  
  * lookupPersonsAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getPersonByIdAsync(String personId, List\<String\> expand, boolean expandReferenceNames)  
* /placements  
  * getPlacementsAsync(Map\<String, Object\> queryParams)  
  * lookupPlacementsAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getPlacementByIdAsync(String placementId, List\<String\> expand, boolean expandReferenceNames)  
* /duties  
  * getDutiesAsync(Map\<String, Object\> queryParams)  
  * lookupDutiesAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getDutyByIdAsync(String dutyId, List\<String\> expand, boolean expandReferenceNames)  
* /groups  
  * getGroupsAsync(Map\<String, Object\> queryParams)  
  * lookupGroupsAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getGroupByIdAsync(String groupId, List\<String\> expand, boolean expandReferenceNames)  
* /programmes  
  * getProgrammesAsync(Map\<String, Object\> queryParams)  
  * lookupProgrammesAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getProgrammeByIdAsync(String programmeId, List\<String\> expand, boolean expandReferenceNames)  
* /studyplans  
  * getStudyPlansAsync(Map\<String, Object\> queryParams)  
  * lookupStudyPlansAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getStudyPlanByIdAsync(String studyPlanId, List\<String\> expand, boolean expandReferenceNames)  
* /syllabuses  
  * getSyllabusesAsync(Map\<String, Object\> queryParams)  
  * lookupSyllabusesAsync(Object body, boolean expandReferenceNames)  
  * getSyllabusByIdAsync(String syllabusId, boolean expandReferenceNames)  
* /schoolUnitOfferings  
  * getSchoolUnitOfferingsAsync(Map\<String, Object\> queryParams)  
  * lookupSchoolUnitOfferingsAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getSchoolUnitOfferingByIdAsync(String offeringId, List\<String\> expand, boolean expandReferenceNames)  
* /activities  
  * getActivitiesAsync(Map\<String, Object\> queryParams)  
  * lookupActivitiesAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getActivityByIdAsync(String activityId, List\<String\> expand, boolean expandReferenceNames)  
* /calendarEvents  
  * getCalendarEventsAsync(Map\<String, Object\> queryParams)  
  * lookupCalendarEventsAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getCalendarEventByIdAsync(String eventId, List\<String\> expand, boolean expandReferenceNames)  
* /attendances  
  * getAttendancesAsync(Map\<String, Object\> queryParams)  
  * lookupAttendancesAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getAttendanceByIdAsync(String attendanceId, List\<String\> expand, boolean expandReferenceNames)  
  * deleteAttendanceAsync(String attendanceId)  
* /attendanceEvents  
  * getAttendanceEventsAsync(Map\<String, Object\> queryParams)  
  * lookupAttendanceEventsAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getAttendanceEventByIdAsync(String eventId, List\<String\> expand, boolean expandReferenceNames)  
* /attendanceSchedules  
  * getAttendanceSchedulesAsync(Map\<String, Object\> queryParams)  
  * lookupAttendanceSchedulesAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getAttendanceScheduleByIdAsync(String scheduleId, List\<String\> expand, boolean expandReferenceNames)  
* /grades  
  * getGradesAsync(Map\<String, Object\> queryParams)  
  * lookupGradesAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getGradeByIdAsync(String gradeId, List\<String\> expand, boolean expandReferenceNames)  
* /aggregatedAttendance  
  * getAggregatedAttendancesAsync(Map\<String, Object\> queryParams)  
  * lookupAggregatedAttendancesAsync(Object body, List\<String\> expand, boolean expandReferenceNames)  
  * getAggregatedAttendanceByIdAsync(String attendanceId, List\<String\> expand, boolean expandReferenceNames)  
* /resources  
  * getResourcesAsync(Map\<String, Object\> queryParams)  
  * lookupResourcesAsync(Object body, boolean expandReferenceNames)  
  * getResourceByIdAsync(String resourceId, boolean expandReferenceNames)  
* /rooms  
  * getRoomsAsync(Map\<String, Object\> queryParams)  
  * lookupRoomsAsync(Object body, boolean expandReferenceNames)  
  * getRoomByIdAsync(String roomId, boolean expandReferenceNames)  
* /subscriptions  
  * getSubscriptionsAsync(Map\<String, Object\> queryParams)  
  * createSubscriptionAsync(Object body)  
  * deleteSubscriptionAsync(String subscriptionId)  
  * getSubscriptionByIdAsync(String subscriptionId, List\<String\> expand, boolean expandReferenceNames)  
  * updateSubscriptionAsync(String subscriptionId, Object body)  
* /deletedEntities  
  * getDeletedEntitiesAsync(Map\<String, Object\> queryParams)  
* /log  
  * getLogAsync(Map\<String, Object\> queryParams)  
* /statistics  
  * getStatisticsAsync(Map\<String, Object\> queryParams)

Detaljerad information on available parameters are in the Javadoc comments in SS12000Client.java.  
The .yaml file can be downloaded from the SS12000 website at [sis.se](https://www.sis.se/standardutveckling/tksidor/tk400499/sistk450/ss-12000/).

## **Webhooks receiver (example)**

To receive a webhooks in a Java application you can use e.g. Spring Boot. This example shows a simple controller that can receive SS12000 notifications. This is just an example and not part of the client library. This is just an example and not production ready code.  
```
//`controllers/WebhookController.java  
package com.example.myss12000app.controllers;

import com.fasterxml.jackson.databind.JsonNode;  
import com.fasterxml.jackson.databind.ObjectMapper;  
import org.springframework.http.ResponseEntity;  
import org.springframework.web.bind.annotation.PostMapping;  
import org.springframework.web.bind.annotation.RequestBody;  
import org.springframework.web.bind.annotation.RequestHeader;  
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;  
import java.util.concurrent.CompletableFuture;

@RestController  
public class WebhookController {

    // Inject SS12000Client here if you want to do follow up API calls  
    private final SS12000Client ss12000Client;

    public WebhookController(SS12000Client ss12000Client) {  
        this.ss12000Client = ss12000Client;  
    }

    /**  
     * Webhook endpoint for SS12000 notifications.  
     */  
    @PostMapping("/webhook/ss12000-webhook")  
    public CompletableFuture\<ResponseEntity<String>> receiveSS12000Webhook(  
            @RequestHeader Map<String, String> headers,  
            @RequestBody JsonNode body  
    ) {  
        System.out.println("Received a webhook from SS12000!");  
        headers.forEach((key, value) -> System.out.println("Header: " + key + " = " + value));

        ObjectMapper mapper = new ObjectMapper();  
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try {  
            System.out.println("Body:\n" + mapper.writeValueAsString(body));

            // Here comes the logic for handling the webhook message  
              
            if (body.has("modifiedEntities") && body.get("modifiedEntities").isArray()) {  
                for (JsonNode resourceType : body.get("modifiedEntities")) {  
                    System.out.println("Changes for resource type: " + resourceType.asText());  
                    // E.g.: if (resourceType.asText().equals("Person")) { ss12000Client.getPersonsAsync(...); }  
                }  
            }

            if (body.has("deletedEntities") && body.get("deletedEntities").isArray()) {  
                System.out.println("There are deleted entitise to fetch from /deletedEntities.");  
                // Call client.getDeletedEntitiesAsync(...) to fetch the deleted IDs.  
            }  
              
            return CompletableFuture.completedFuture(ResponseEntity.ok("Webhook received!"));  
        } catch (Exception ex) {  
            System.err.println("Error when handling webhook: "\+ ex.getMessage());  
            return CompletableFuture.completedFuture(ResponseEntity.status(500).body("Internal server error: " + ex.getMessage()));  
        }  
    }  
}
```
To activate this webhook endpoint in your Spring Boot application, make sure you got:

1. **pom.xml:** Add to Spring Boot Web starter dependency.
```
<dependency>  
    <groupId>org.springframework.boot</groupId>  
    <artifactId>spring-boot-starter-web</artifactId>  
</dependency>
```
2. **Main class (Application.java):** Make sure it has the @SpringBootApplication annotation.

```
import org.springframework.boot.SpringApplication;  
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication  
public class MySS12000AppApplication {  
    public static void main(String\[\] args) {  
        SpringApplication.run(MySS12000AppApplication.class, args);  
    }  
}
```
3. **Run the application:** Run the application from your IDE or via mvn spring-boot:run. Your webhook endpoint will become available at http://localhost:<port>/webhook/ss12000-webhook. Remember that the SS12000 API needs to be able to reach your webhook, it needs to be publicly available (e.g. via a reverse proxy or a tunneling service like ngrok).

## **Contribute**

Contributions are welcome! If you want to add, improve, optimize or just change things just send in a pull request and I will have a look. Found a bug and don't know how to fix it? Create an issue!

## **License**

This project is licensed under the MIT License.
