package com.sfeir.lux.pokedexnotification;

import com.google.gson.JsonObject;
import com.windowsazure.messaging.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Random;
import java.util.Set;

@RestController
@RequestMapping("/azure")
public class AzureNotificationController {

    private final List<String> azureFcmTokens;


    private final NotificationHubClient hub;

    public AzureNotificationController(@Value("${azure.connection-string}") String connectionString, @Value("${azure.hub-path}") String hubPath, @Qualifier("azureFcmTokens") List<String> azureFcmTokens) {
        this.hub = new NotificationHub(connectionString, hubPath);
        this.azureFcmTokens = azureFcmTokens;
    }

    @GetMapping("/registrations")
    public ResponseEntity<List<Registration>> getRegistrations() throws NotificationHubsException {
        return ResponseEntity.ok(this.hub.getRegistrations().getRegistrations());
    }

    @GetMapping("/notify")
    public ResponseEntity<String> sendNotification(@RequestParam(required = false) String pokemon) throws NotificationHubsException {
        JsonObject send = getFCMSend(pokemon);
        Notification n = Notification.createFcmV1Notification(send.toString());
        NotificationOutcome outcome = hub.sendDirectNotification(n, azureFcmTokens);
        return ResponseEntity.ok(outcome.getTrackingId());
    }

    @GetMapping("/apple/notify")
    public ResponseEntity<String> sendNotificationToApple(@RequestParam(required = false) String pokemon) throws NotificationHubsException {
        JsonObject send = getApsSend(pokemon);
        Notification n = Notification.createAppleNotification(send.toString());
        NotificationOutcome outcome = hub.sendNotification(n, Set.of("pokemon"));
        return ResponseEntity.ok(outcome.getTrackingId());
    }

    @GetMapping("/notify/{topic}")
    public ResponseEntity<String> sendNotificationToTopic(@PathVariable String topic) throws NotificationHubsException {
        JsonObject send = getFCMSend(null);
        Notification n = Notification.createFcmV1Notification(send.toString());
        NotificationOutcome outcome = hub.sendNotification(n, Set.of(topic));
        return ResponseEntity.ok(outcome.getTrackingId());
    }


    @GetMapping("/registrations/delete/{id}")
    public ResponseEntity<String> deleteRegistration(@PathVariable String id) throws NotificationHubsException {
        hub.deleteRegistration(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/notify/wild-pokemon")
    public ResponseEntity<List<String>> sendWildPokemonNotification(@RequestParam(required = false) String pokemon) throws NotificationHubsException {
        JsonObject fcmSend = getFCMSend(pokemon);
        JsonObject apsSend = getApsSend(pokemon);
        List<String> result = new java.util.ArrayList<>(List.of());
        result.add(hub.sendNotification(Notification.createFcmV1Notification(fcmSend.toString()), Set.of("pokemon")).getTrackingId());
        result.add(hub.sendNotification(Notification.createAppleNotification(apsSend.toString()), Set.of("pokemon")).getTrackingId());
        return ResponseEntity.ok(result);
    }


    private static JsonObject getFCMSend(String pokemon) {
        JsonObject notification = new JsonObject();
        JsonObject message = new JsonObject();
        JsonObject data = new JsonObject();
        JsonObject send = new JsonObject();
        if (pokemon != null)
            data.addProperty("pokemon", pokemon);
        else
            data.addProperty("pokemon", new Random().nextInt(1025) + 1);
        notification.addProperty("title", "A wild Pokemon Appear !");
        notification.addProperty("body", "Click to see who it is");
        message.add("notification", notification);
        message.add("data", data);
        send.add("message", message);
        return send;
    }

    private static JsonObject getApsSend(String pokemon) {
        JsonObject alert = new JsonObject();
        JsonObject aps = new JsonObject();
        JsonObject send = new JsonObject();
        if (pokemon != null)
            send.addProperty("pokemon", pokemon);
        else
            send.addProperty("pokemon", new Random().nextInt(1025) + 1);
        alert.addProperty("title", "A wild Pokemon Appear !");
        alert.addProperty("body", "Click to see who it is");
        aps.addProperty("badge", 1);
        aps.add("alert", alert);
        send.add("aps", aps);
        return send;
    }


}
