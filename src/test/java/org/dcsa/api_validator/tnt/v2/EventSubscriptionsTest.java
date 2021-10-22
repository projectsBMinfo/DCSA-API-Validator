package org.dcsa.api_validator.tnt.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.response.Response;
import org.dcsa.api_validator.conf.Configuration;
import org.hamcrest.Matchers;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.dcsa.api_validator.TestUtil.loadFileAsString;
import static org.hamcrest.Matchers.*;

/*
Test for /event-subscriptions
 */
public class EventSubscriptionsTest {
  public static final String VALID_EVENT_SUBSCRIPTION =
      loadFileAsString("tnt/v2/EventSubscription/ValidEventSubscriptionSample.json");

  final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testToCreateEventSubscriptionWithInvalidEventType() {
    final String INVALID_EVENT_SUBSCRIPTION =
        loadFileAsString("tnt/v2/EventSubscription/InvalidEventTypeEventSubscriptionSample.json");

    given()
        .auth()
        .oauth2(Configuration.accessToken)
        .contentType("application/json")
        .body(INVALID_EVENT_SUBSCRIPTION)
        .post(Configuration.ROOT_URI + "/event-subscriptions")
        .then()
        .assertThat()
        .statusCode(400)
        .body("message", Matchers.containsString("must be any of [SHIPMENT, TRANSPORT, EQUIPMENT]"));
  }

  @Test
  public void testToCreateEventSubscriptionWithInvalidVesselIMONumber() {
    final String INVALID_EVENT_SUBSCRIPTION =
        loadFileAsString(
            "tnt/v2/EventSubscription/InvalidVesselIMONumberEventSubscriptionSample.json");

    given()
        .auth()
        .oauth2(Configuration.accessToken)
        .contentType("application/json")
        .body(INVALID_EVENT_SUBSCRIPTION)
        .post(Configuration.ROOT_URI + "/event-subscriptions")
        .then()
        .assertThat()
        .statusCode(400)
        .body("message", Matchers.containsString("must be a valid Vessel IMO Number"));
  }

  @Test(enabled = false)
  public void testToCreateEventSubscription() {
    Response response =
        given()
            .auth()
            .oauth2(Configuration.accessToken)
            .contentType("application/json")
            .body(VALID_EVENT_SUBSCRIPTION)
            .post(Configuration.ROOT_URI + "/event-subscriptions");

    Assert.assertEquals(response.getStatusCode(), 201);
    Assert.assertNotNull(response.jsonPath().get("subscriptionID"));
  }

  @Test
  public void testToGetEventSubscriptions() {

    given()
        .auth()
        .oauth2(Configuration.accessToken)
        .contentType("application/json")
        .get(Configuration.ROOT_URI + "/event-subscriptions")
        .then()
        .assertThat()
        .statusCode(200);
  }

  @Test
  public void testToGetSpecificEventSubscription() {
    Response response =
        given()
            .auth()
            .oauth2(Configuration.accessToken)
            .contentType("application/json")
            .body(VALID_EVENT_SUBSCRIPTION)
            .post(Configuration.ROOT_URI + "/event-subscriptions");

    String createdEventSubscriptionID = response.jsonPath().get("subscriptionID");

    given()
        .auth()
        .oauth2(Configuration.accessToken)
        .contentType("application/json")
        .get(Configuration.ROOT_URI + "/event-subscriptions/" + createdEventSubscriptionID)
        .then()
        .assertThat()
        .statusCode(200)
        .body("subscriptionID", Matchers.equalTo(createdEventSubscriptionID));
  }

  @Test
  public void testToDeleteSpecificEventSubscription() {
    Response response =
        given()
            .auth()
            .oauth2(Configuration.accessToken)
            .contentType("application/json")
            .body(VALID_EVENT_SUBSCRIPTION)
            .post(Configuration.ROOT_URI + "/event-subscriptions");

    String createdEventSubscriptionID = response.jsonPath().get("subscriptionID");

    given()
        .auth()
        .oauth2(Configuration.accessToken)
        .contentType("application/json")
        .delete(Configuration.ROOT_URI + "/event-subscriptions/" + createdEventSubscriptionID)
        .then()
        .assertThat()
        .statusCode(204);
  }

  @Test
  public void testToVerifyNotAllowingUpdateOfSecretInEventSubscription()
      throws JsonProcessingException {
    Response response =
        given()
            .auth()
            .oauth2(Configuration.accessToken)
            .contentType("application/json")
            .body(VALID_EVENT_SUBSCRIPTION)
            .post(Configuration.ROOT_URI + "/event-subscriptions");

    String createdEventSubscriptionID = response.jsonPath().get("subscriptionID");

    JsonNode node = objectMapper.valueToTree(response.jsonPath().get());
    ((ObjectNode) node)
        .put(
            "secret",
            "OG1wOWFaRW1HTTF1Y2M4OUN0RlAtaU9JMjM5N25vMWtWd25rS2Vkc2ktZms0c01zaTJQOElZRVNQN29MYUkzcg==");

    given()
        .auth()
        .oauth2(Configuration.accessToken)
        .contentType("application/json")
        .body(objectMapper.writeValueAsString(node))
        .put(Configuration.ROOT_URI + "/event-subscriptions/" + createdEventSubscriptionID)
        .then()
        .assertThat()
        .statusCode(400)
        .body("message", Matchers.containsString("Please omit the \"secret\" attribute."));
  }

  @Test
  public void testToVerifyFailureIfSubscriptionIDInPathAndBodyDoNotMatchEventSubscription()
      throws JsonProcessingException {
    Response response =
        given()
            .auth()
            .oauth2(Configuration.accessToken)
            .contentType("application/json")
            .body(VALID_EVENT_SUBSCRIPTION)
            .post(Configuration.ROOT_URI + "/event-subscriptions");

    String createdEventSubscriptionID = response.jsonPath().get("subscriptionID");

    JsonNode node = objectMapper.valueToTree(response.jsonPath().get());
    ((ObjectNode) node).put("subscriptionID", "190b766e-3d8a-43b2-962d-4df0b3284098");

    given()
        .auth()
        .oauth2(Configuration.accessToken)
        .contentType("application/json")
        .body(objectMapper.writeValueAsString(node))
        .put(Configuration.ROOT_URI + "/event-subscriptions/" + createdEventSubscriptionID)
        .then()
        .assertThat()
        .statusCode(400)
        .body("errors", hasItem(hasEntry(equalTo("message"), equalTo("Id in url does not match id in body"))));
  }

  @Test
  public void testToUpdateEventSubscription() throws JsonProcessingException {

    UUID uuid = UUID.randomUUID();

    Response response =
        given()
            .auth()
            .oauth2(Configuration.accessToken)
            .contentType("application/json")
            .body(VALID_EVENT_SUBSCRIPTION)
            .post(Configuration.ROOT_URI + "/event-subscriptions");

    String createdEventSubscriptionID = response.jsonPath().get("subscriptionID");

    JsonNode node = objectMapper.valueToTree(response.jsonPath().get());
    ((ObjectNode) node)
        .put("callbackUrl", "http://127.0.0.1:9092/v2/notification-endpoints/receive/" + uuid);

    given()
        .auth()
        .oauth2(Configuration.accessToken)
        .contentType("application/json")
        .body(objectMapper.writeValueAsString(node))
        .put(Configuration.ROOT_URI + "/event-subscriptions/" + createdEventSubscriptionID)
        .then()
        .assertThat()
        .statusCode(200)
        .body("callbackUrl", Matchers.containsString(uuid.toString()));
  }

  @Test
  public void testToUpdateSecretEventSubscription() throws JsonProcessingException {

    UUID uuid = UUID.randomUUID();

    Response response =
        given()
            .auth()
            .oauth2(Configuration.accessToken)
            .contentType("application/json")
            .body(VALID_EVENT_SUBSCRIPTION)
            .post(Configuration.ROOT_URI + "/event-subscriptions");

    String createdEventSubscriptionID = response.jsonPath().get("subscriptionID");

    given()
        .auth()
        .oauth2(Configuration.accessToken)
        .contentType("application/json")
        .body(
            "{\"secret\": \"MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDM2NTc4NjIzODk3NDY5MDgyNzM0OTg3MTIzNzg2NA==\"}")
        .put(
            Configuration.ROOT_URI
                + "/event-subscriptions/"
                + createdEventSubscriptionID
                + "/secret")
        .then()
        .assertThat()
        .statusCode(204);
  }
}
