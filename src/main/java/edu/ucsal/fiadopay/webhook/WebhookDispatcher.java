package edu.ucsal.fiadopay.webhook;

import java.time.Instant;
import java.util.concurrent.ExecutorService;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import edu.ucsal.fiadopay.domain.WebhookDelivery;
import edu.ucsal.fiadopay.repo.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;


@Component
@RequiredArgsConstructor
public class WebhookDispatcher {
private final ExecutorService fiadopayExecutor;
private final RestTemplate rest = new RestTemplate();
private final WebhookDeliveryRepository deliveries;


public void dispatchAsync(String url, String payload, String eventId, String eventType) {
WebhookDelivery d = WebhookDelivery.builder()
.eventId(eventId)
.eventType(eventType)
.paymentId(eventId)
.targetUrl(url)
.payload(payload)
.attempts(0)
.delivered(false)
.lastAttemptAt(Instant.now())
.build();
deliveries.save(d);


fiadopayExecutor.submit(() -> {
try {
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
HttpEntity<String> ent = new HttpEntity<>(payload, headers);
ResponseEntity<String> resp = rest.postForEntity(url, ent, String.class);
d.setAttempts(d.getAttempts() + 1);
d.setLastAttemptAt(Instant.now());
if (resp.getStatusCode().is2xxSuccessful()) {
d.setDelivered(true);
}
} catch (Exception ex) {
d.setAttempts(d.getAttempts() + 1);
d.setLastAttemptAt(Instant.now());
} finally {
deliveries.save(d);
}
});
}
}
