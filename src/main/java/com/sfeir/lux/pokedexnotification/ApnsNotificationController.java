package com.sfeir.lux.pokedexnotification;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.DeliveryPriority;
import com.eatthepath.pushy.apns.PushType;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import com.google.gson.JsonObject;
import com.sfeir.lux.pokedexnotification.config.TokenCacheHolder;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.http.*;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static com.eatthepath.pushy.apns.util.SimpleApnsPushNotification.DEFAULT_EXPIRATION_PERIOD;

@RestController
@RequestMapping("/apns")
public class ApnsNotificationController {

    @Value("${apns.device}")
    private String device;

    @Value("${apns.topic}")
    private String topic;

    private final String teamId;
    private final String keyId;

    private final WebClient webClient;
    private final CacheManager tokenCacheManager;
    private final ApnsClient apnsClient;

    public ApnsNotificationController(WebClient webClient, CacheManager tokenCacheManager, @Value("${apns.keyId}") String keyId, @Value("${apns.teamId}") String teamId) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        this.webClient = webClient;
        this.tokenCacheManager = tokenCacheManager;
        this.keyId = keyId;
        this.teamId = teamId;
        apnsClient = new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
                .setSigningKey(ApnsSigningKey.loadFromPkcs8File(ResourceUtils.getFile(String.format("classpath:AuthKey_%s_Pushy.p8", keyId)),
                        teamId, keyId))
                .build();
    }

    @GetMapping("/notify/pushy")
    public Mono<ResponseEntity<String>> sendNotificationWithPushy(@RequestParam(required = false) String pokemon) {
        final ApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
        payloadBuilder
                .setAlertTitle("A wild Pokemon Appear !")
                .setAlertBody("Click to see who it is")
                .setBadgeNumber(1);

        if (pokemon != null) {
            payloadBuilder.addCustomProperty("pokemon", pokemon);
        } else {
            payloadBuilder.addCustomProperty("pokemon", new Random().nextInt(1025) + 1);
        }

        final String payload = payloadBuilder.build();
        final String token = TokenUtil.sanitizeTokenString(device);

        var pushNotification = new SimpleApnsPushNotification(token, topic, payload, Instant.now().plus(DEFAULT_EXPIRATION_PERIOD), DeliveryPriority.CONSERVE_POWER, PushType.ALERT);
        var sendNotificationFuture = apnsClient.sendNotification(pushNotification);
        return Mono.fromFuture(sendNotificationFuture.thenApply(r -> r.getApnsUniqueId().map(UUID::toString).orElse("No apns-unique-id found")).thenApply(ResponseEntity::ok));
    }

    @GetMapping("/notify")
    public Mono<ResponseEntity<String>> sendNotification(@RequestParam(required = false) String pokemon) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        // need to use webclient because Apple endpoint is in HTTP/2
        return webClient.post()
                .uri("https://api.sandbox.push.apple.com:443/3/device/" + device)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(getApsSend(pokemon).toString())
                .header("apns-id", UUID.randomUUID().toString())
                .header("apns-push-type", "alert")
                .header("apns-expiration", "0")
                .header("apns-topic", topic)
                .header("apns-priority", "5")
                .header("Authorization", "Bearer " + getJwt(device))
                .exchangeToMono(response -> Mono.just(ResponseEntity.ok(response.headers().header("apns-unique-id").stream().findFirst().orElse("No apns-unique-id found"))));
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

    private String getJwt(String device) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        // Need to used cache because Apple want the authentication token to be updated no more thane once every 20 minutes
        var cache = this.tokenCacheManager.getCache("apns-token-cache");
        TokenCacheHolder exchangedToken;
        if (cache != null && (exchangedToken = cache.get(device, TokenCacheHolder.class)) != null && exchangedToken.expirationDate().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            return exchangedToken.token();
        }
        // create a key from .p8 file
        File file = ResourceUtils.getFile(String.format("classpath:AuthKey_%s.p8", keyId));
        byte[] p8der = FileUtils.readFileToByteArray(file);
        PKCS8EncodedKeySpec priPKCS8 = new PKCS8EncodedKeySpec(new org.apache.commons.codec.binary.Base64().decode(p8der));
        PrivateKey appleKey = KeyFactory.getInstance("EC").generatePrivate(priPKCS8);
        // end create a key from .p8 file
        Map<String, String> jwtHeader = new HashMap<>();
        jwtHeader.put("alg", "ES256");
        jwtHeader.put("kid", keyId);

        JsonObject jwtPayload = new JsonObject();
        jwtPayload.addProperty("iss", teamId);
        jwtPayload.addProperty("iat", LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC));
        // begin signWithES256
        JwtBuilder jwtBuilder = Jwts.builder()
                .header().add(jwtHeader).and()
                .content(jwtPayload.toString())
                .signWith(appleKey);
        var result = jwtBuilder.compact();
        if (cache != null) {
            cache.put(device, new TokenCacheHolder(result, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30)));
        }
        return result;
    }
}
