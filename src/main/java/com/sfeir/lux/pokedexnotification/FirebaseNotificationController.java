package com.sfeir.lux.pokedexnotification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;

@RestController
public class FirebaseNotificationController {

    private final List<String> fcmTokens;


    public FirebaseNotificationController(@Qualifier("fcmTokens") List<String> fcmTokens) throws IOException {
        File file = ResourceUtils.getFile("classpath:firebase-adminsdk.json");
        FileInputStream refreshToken = new FileInputStream(file);

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(refreshToken))
                .build();

        FirebaseApp.initializeApp(options);
        this.fcmTokens = fcmTokens;
    }

    @GetMapping("/notify")
    public ResponseEntity<String> sendNotification(@RequestParam String pokemon) throws FirebaseMessagingException {

        var builder = Message.builder()
                .setApnsConfig(ApnsConfig.builder().setAps(Aps.builder().setBadge(1).build()).build())
                .setNotification(
                        Notification.builder().
                                setTitle("Pokedex Notification")
                                .setBody("Notification from api")
                                .build()
                )
                .putData("test", "test")
                .setToken(fcmTokens.get(0));

        if (pokemon != null) builder.putData("pokemon", pokemon);
        Message message = builder.build();
        String response = FirebaseMessaging.getInstance().send(message);
        return ResponseEntity.ok(response);

    }

    @GetMapping("/notifications")
    public ResponseEntity<List<String>> sendNotifications(@RequestParam String pokemon) throws FirebaseMessagingException {

        var builder = MulticastMessage.builder()
                .setNotification(
                        Notification.builder().
                                setTitle("A wild pokemon appears !")
                                .setBody("Click to see who it is")
                                .build()
                )
                .addAllTokens(fcmTokens);
        if (pokemon != null) builder.putData("pokemon", pokemon);
        MulticastMessage message = builder.build();
        var response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
        response.getResponses().forEach(r -> System.out.println(r.getMessageId()));
        return ResponseEntity.ok(response.getResponses().stream().map(SendResponse::getMessageId).toList());

    }

    @GetMapping("/notify/{topic}")
    public ResponseEntity<String> sendNotificationToTopic(@PathVariable String topic) throws FirebaseMessagingException {
        Message message = Message.builder()
                .setNotification(
                        Notification.builder().
                                setTitle("Pokedex Notification")
                                .setBody(topic)
                                .build()
                )
                .setTopic(topic)
                .build();
        var response = FirebaseMessaging.getInstance().send(message);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/notify/wild-pokemon")
    public ResponseEntity<String> sendNotificationToPokemonTopic(@RequestParam(required = false) String pokemon) throws FirebaseMessagingException {
        var builder = Message.builder()
                .setApnsConfig(ApnsConfig.builder().setAps(Aps.builder().setBadge(1).build()).build())
                .setNotification(
                        Notification.builder()
                                .setTitle("A wild pokemon appears !")
                                .setBody("Click to see who it is")
                                .build()
                )
                .setTopic("pokemon");

        if (pokemon != null) builder.putData("pokemon", pokemon);
        else builder.putData("pokemon", String.valueOf(new Random().nextInt(1025) + 1));
        Message message = builder.build();
        var response = FirebaseMessaging.getInstance().send(message);
        return ResponseEntity.ok(response);
    }
}
