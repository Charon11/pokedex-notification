package com.sfeir.lux.pokedexnotification;

import com.google.gson.JsonObject;
import com.windowsazure.messaging.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/azure")
public class AzureNotificationController {

    private final List<String> azureFcmTokens;

    @Value("${azure.apns.template}")
    private String azureApnsTemplate;

    @Value("${azure.fcm.template}")
    private String azureFcmTemplate;

    private final NotificationHubClient hub;

    public AzureNotificationController(@Value("${azure.connection-string}") String connectionString, @Value("${azure.hub-path}") String hubPath, @Qualifier("azureFcmTokens") List<String> azureFcmTokens) {
        this.hub = new NotificationHub(connectionString, hubPath);
        this.azureFcmTokens = azureFcmTokens;
    }

    @GetMapping("/registrations")
    public ResponseEntity<List<Registration>> getRegistrations() throws NotificationHubsException {
        var res = this.hub.getRegistrations().getRegistrations();
        return ResponseEntity.ok(res);
    }


    @GetMapping("/registrations/update")
    public ResponseEntity<List<Registration>> updateRegistrations(@RequestParam String topic) throws NotificationHubsException {
        var res = this.hub.getRegistrations().getRegistrations();
        res.forEach(reg -> {
            try {
                reg.setTags(Set.of(topic, "pokemon"));
                hub.updateRegistration(reg);
            } catch (NotificationHubsException e) {
                throw new RuntimeException(e);
            }
        });
        return ResponseEntity.ok(this.hub.getRegistrations().getRegistrations());
    }

    @GetMapping("/registrations/template")
    public ResponseEntity<Registration> updateRegistrationsToTemplate(@RequestParam String id) throws NotificationHubsException {
        var res = this.hub.getRegistration(id);
        // @ registration, a tag starting with a $ is add, but we can't update a tag starting with a $
        var tags = res.getTags().stream().filter(s-> !s.startsWith("$")).collect(Collectors.toSet());
        switch (res) {
            case AppleRegistration appleRegistration:
                var appleTemplateRegistration = new AppleTemplateRegistration(
                        id,
                        appleRegistration.getDeviceToken(),
                        azureApnsTemplate
                );
                appleTemplateRegistration.setTags(tags);
                hub.upsertRegistration(appleTemplateRegistration);
                break;
            case FcmV1Registration fcmV1Registration:
                var fcmV1TemplateRegistration = new FcmV1TemplateRegistration(
                        id,
                        fcmV1Registration.getFcmRegistrationId(),
                        azureFcmTemplate
                );
                fcmV1TemplateRegistration.setTags(tags);
                hub.upsertRegistration(fcmV1TemplateRegistration);
                break;
            default:
                break;
        }
        return ResponseEntity.ok(this.hub.getRegistration(id));
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
        List<String> result = new ArrayList<>(List.of());
        result.add(hub.sendNotification(Notification.createFcmV1Notification(fcmSend.toString()), Set.of("pokemon")).getTrackingId());
        result.add(hub.sendNotification(Notification.createAppleNotification(apsSend.toString()), Set.of("pokemon")).getTrackingId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/notify/wild-pokemon/template")
    public ResponseEntity<String> sendWildPokemonTemplateNotification(@RequestParam(required = false) String pokemon) throws NotificationHubsException {
        var n = Notification.createTemplateNotification(getTemplateProperties(pokemon));
        var r = hub.sendNotification(n, Set.of("pokemon")).getTrackingId();
        return ResponseEntity.ok(r);
    }

    private static Map<String, String> getTemplateProperties(String pokemon) {
        return Map.of(
                "title", "A wild Pokemon Appear !",
                "body", "Click to see who it is",
                "badge", "1",
                "pokemon", pokemon != null ? pokemon : String.valueOf(new Random().nextInt(1025) + 1)
        );
    }


    private static JsonObject getFCMSend(String pokemon) {
        JsonObject notification = new JsonObject();
        JsonObject message = new JsonObject();
        JsonObject data = new JsonObject();
        JsonObject send = new JsonObject();
        if (pokemon != null)
            data.addProperty("pokemon", pokemon);
        else
            data.addProperty("pokemon", String.valueOf(new Random().nextInt(1025) + 1));
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
            send.addProperty("pokemon", String.valueOf(new Random().nextInt(1025) + 1));
        alert.addProperty("title", "A wild Pokemon Appear !");
        alert.addProperty("body", "Click to see who it is");
        aps.addProperty("badge", String.valueOf(1));
        aps.add("alert", alert);
        send.add("aps", aps);
        return send;
    }


}
