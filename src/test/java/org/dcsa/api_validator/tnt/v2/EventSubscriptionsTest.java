package org.dcsa.api_validator.tnt.v2;

import org.dcsa.api_validator.conf.Configuration;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import spark.Request;
import spark.Spark;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;

public class EventSubscriptionsTest {
    //Don't reuse request objects to reduce risk of other unrelated events affecting the tests
    private Request req;
    private Request reqTransportEvent;
    private CountDownLatch lock = new CountDownLatch(1); //Initialize countdown at 1, when count is 0 lock is released
    private CountDownLatch lock2 = new CountDownLatch(1); //Initialize countdown at 1, when count is 0 lock is released

    @BeforeMethod
    void setup() {
        cleanUp();
        Spark.port(Configuration.getCallbackListenPort());
        Spark.post("/webhook/receive", (req, res) -> {
            if(req.body()==null) return "Ignoring null callback received"; //Not sure why this sometimes happens. May be a problem in the API, for now we ignore it to avoid tests failing sporadically
            this.req = req;
            lock.countDown(); //Release lock
            return "Callback received!";
        });
        Spark.post("/webhook/receive-transport-events", (req, res) -> {
            if(req.body()==null) return "Ignoring null callback received"; //Not sure why this sometimes happens. May be a problem in the API, for now we ignore it to avoid tests failing sporadically
            this.reqTransportEvent = req;
            lock2.countDown(); //Release lock
            return "Callback received!";
        });
        Spark.awaitInitialization();


    }

    private void cleanUp() {
        this.req = null;
        this.reqTransportEvent = null;
        lock = new CountDownLatch(1); //Initialize countdown at 1, when count is 0 lock is released
        lock2 = new CountDownLatch(1); //Initialize countdown at 1, when count is 0 lock is released
    }

    @AfterMethod
    void shutdown() {
        Spark.stop();
        Spark.awaitStop();
    }

    @Test
    public void testCallbacks() throws InterruptedException, IOException, JSONException {
        Map<?, ?> subscription = given().
                auth().
                oauth2(Configuration.accessToken).
                contentType("application/json").
                body("{\n" +
                        "  \"callbackUrl\": \""+Configuration.CALLBACK_URI+"/webhook/receive\",\n" +
                        "  \"eventType\": [\n" +
                        "  ],\n" +
                        "  \"bookingReference\": \"\",\n" +
                        "  \"transportDocumentID\": \"\",\n" +
                        "  \"transportDocumentType\": \"\",\n" +
                        "  \"equipmentReference\": \"\",\n" +
                        "  \"scheduleID\": \"\",\n" +
                        "  \"transportCallID\": \"\"\n" +
                        "}").
                post(Configuration.ROOT_URI+"/event-subscriptions").
                then().
                assertThat().
                statusCode(201).
                extract().body().as(Map.class);

        given().
                auth().
                oauth2(Configuration.accessToken).
                contentType("application/json").
                body("{\n" +
                        "    \"eventType\": \"SHIPMENT\",\n" +
                        "    \"eventDateTime\": \"2019-11-12T07:41:00+08:30\",\n" +
                        "    \"eventClassifierCode\": \"ACT\",\n" +
                        "    \"eventTypeCode\": \"RECE\",\n" +
                        "    \"shipmentInformationTypeCode\": \"SRM\",\n" +
                        "    \"shipmentID\": \"123e4567-e89b-12d3-a456-426614174000\"\n" +
                        "  }").
                post(Configuration.ROOT_URI + "/events").
                then().
                assertThat().
                statusCode(201);

        UUID subscriptionID = assertValueIsSubscriptionID(subscription.get("subscriptionID"));

        lock.await(20000, TimeUnit.MILLISECONDS); //Released immediately if lock countdown is 0

        // Delete the subscription again to ensure we clean up after ourselves before moving on with the test
        given().
                auth().
                oauth2(Configuration.accessToken).
                delete(Configuration.ROOT_URI + "/event-subscriptions/" + subscriptionID).
                then().
                assertThat().
                statusCode(204);


        Assert.assertNotNull(req, "The callback request should not be null");
        Assert.assertNotNull(req.body(), "The callback request body should not be null");
        String jsonBody = req.body();

        System.out.println("The testCallbacks() test received the body: " + jsonBody);
        //Validate that the callback body is a Shipment Event
        JSONObject jsonSubject = new JSONObject(jsonBody);

        try (InputStream inputStream = getClass().getResourceAsStream("/tnt/v2/ShipmentEventSchema.json")) {
            JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
            Schema schema = SchemaLoader.load(rawSchema);
            schema.validate(jsonSubject); // throws a ValidationException if this object is invalid
        }

    }

    @Test
    public void testCallbackFilter() throws InterruptedException, JSONException {
        Map<?, ?> subscription = given().
                auth().
                oauth2(Configuration.accessToken).
                contentType("application/json").
                body("{\n" +
                        "  \"callbackUrl\": \""+Configuration.CALLBACK_URI+"/receive-transport-events\",\n" +
                        "  \"eventType\": [\"TRANSPORT\"]," +
                        "  \"bookingReference\": \"\",\n" +
                        "  \"transportDocumentID\": \"\",\n" +
                        "  \"transportDocumentType\": \"\",\n" +
                        "  \"equipmentReference\": \"\",\n" +
                        "  \"scheduleID\": \"\",\n" +
                        "  \"transportCallID\": \"\"\n" +
                        "}").
                post(Configuration.ROOT_URI+"/event-subscriptions").
                then().
                assertThat().
                statusCode(201).
                extract().body().as(Map.class);

        given().
                auth().
                oauth2(Configuration.accessToken).
                contentType("application/json").
                body("{\n" +
                        "    \"eventType\": \"SHIPMENT\",\n" +
                        "    \"eventDateTime\": \"2019-11-12T07:41:00+08:30\",\n" +
                        "    \"eventClassifierCode\": \"ACT\",\n" +
                        "    \"eventTypeCode\": \"RECE\",\n" +
                        "    \"shipmentInformationTypeCode\": \"SRM\",\n" +
                        "    \"shipmentID\": \"123e4567-e89b-12d3-a456-426614174000\"\n" +
                        "  }").
                post(Configuration.ROOT_URI + "/events").
                then().
                assertThat().
                statusCode(201).
                extract().body().as(Map.class);

        lock2.await(3000, TimeUnit.MILLISECONDS); //Released immediately if lock countdown is 0

        // Delete the subscription again to ensure we clean up after ourselves before moving on with the test
        UUID subscriptionID = assertValueIsSubscriptionID(subscription.get("subscriptionID"));

        given().
                auth().
                oauth2(Configuration.accessToken).
                delete(Configuration.ROOT_URI + "/event-subscriptions/" + subscriptionID).
                then().
                assertThat().
                statusCode(204);

        Assert.assertNull(reqTransportEvent, "The callback request should be null"); //The body should be null, since only transport events must be sent to this endpoint

    }

    private static UUID assertValueIsSubscriptionID(Object v) {
        Assert.assertNotNull(v, "Resulting subscription had no subscriptionID");
        Assert.assertTrue(v instanceof String,
                "The SubscriptionID must be an UUID/String, was: " + v.getClass().getSimpleName());

        UUID uuid = null;
        try {
            uuid = UUID.fromString((String) v);
        } catch (IllegalArgumentException e) {
            Assert.fail("The SubscriptionID must be an UUID/String, but " + v + " could not be parsed as one", e);
        }
        return uuid;
    }

}
