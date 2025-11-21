package edu.ucsal.fiadopay.payment;


import org.springframework.stereotype.Component;
import edu.ucsal.fiadopay.annotations.PaymentMethod;
import edu.ucsal.fiadopay.domain.Payment;


@PaymentMethod(type = "CARD")
@Component
public class CardPaymentProcessor implements PaymentProcessor {
@Override
public boolean process(Payment payment) {
// Simulate basic card rule: decline if amount ends with .13 (funny business), otherwise approve
return payment.getAmount().remainder(new java.math.BigDecimal("1")).compareTo(java.math.BigDecimal.ZERO) != 0
|| !payment.getAmount().toPlainString().endsWith(".13");
}
}

PAYMENT SERVICE

package edu.ucsal.fiadopay.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import edu.ucsal.fiadopay.annotations.PaymentMethod;
import edu.ucsal.fiadopay.controller.PaymentRequest;
import edu.ucsal.fiadopay.controller.PaymentResponse;
import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.payment.PaymentProcessor;
import edu.ucsal.fiadopay.repo.MerchantRepository;
import edu.ucsal.fiadopay.repo.PaymentRepository;
import edu.ucsal.fiadopay.webhook.WebhookDispatcher;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final MerchantRepository merchants;
    private final PaymentRepository payments;
    private final ApplicationContext ctx;
    private final ExecutorService fiadopayExecutor;
    private final AntiFraudService antifraud;
    private final WebhookDispatcher webhookDispatcher;

    public PaymentResponse createPayment(String authHeader, String idemKey, PaymentRequest req) {

        Long merchantId = validateAuthAndGetMerchantId(authHeader);

        // Idempotência
        if (idemKey != null) {
            var existing = payments.findByIdempotencyKeyAndMerchantId(idemKey, merchantId);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        Payment p = Payment.builder()
                .id("pay_" + UUID.randomUUID())
                .merchantId(merchantId)
                .method(req.method().toUpperCase())
                .amount(req.amount())
                .currency(req.currency())
                .installments(req.installments() == null ? 1 : req.installments())
                .monthlyInterest(0.0)
                .totalWithInterest(req.amount())
                .status(Payment.Status.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .idempotencyKey(idemKey)
                .metadataOrderId(req.metadataOrderId())
                .build();

        payments.save(p);

        // processamento async
        fiadopayExecutor.submit(() -> processPayment(p));

        return toResponse(p);
    }

    public PaymentResponse getPayment(String id) {
        Payment p = payments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return toResponse(p);
    }

    public Map<String, Object> refund(String auth, String paymentId) {
        Long merchantId = validateAuthAndGetMerchantId(auth);

        var p = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!p.getMerchantId().equals(merchantId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        p.setStatus(Payment.Status.REFUNDED);
        p.setUpdatedAt(Instant.now());
        payments.save(p);

        
        Merchant m = merchants.findById(p.getMerchantId()).orElse(null);
        if (m != null && m.getWebhookUrl() != null) {
            webhookDispatcher.dispatchAsync(
                    m.getWebhookUrl(),
                    "{\"event\":\"payment.refunded\",\"id\":\"" + p.getId() + "\"}",
                    p.getId(),
                    "payment.refunded"
            );
        }

        return Map.of("status", "ok", "id", p.getId());
    }

    // ---------------------------------------------------------------------------------------
    // PROCESSAMENTO ASSÍNCRONO
    // ---------------------------------------------------------------------------------------
    private void processPayment(Payment p) {
        try {
            // 1) Anti-fraude
            var af = antifraud.inspect(p);
            if (af.isPresent()) {
                p.setStatus(Payment.Status.DECLINED);
                p.setUpdatedAt(Instant.now());
                payments.save(p);
                notifyMerchant(p);
                return;
            }

            // 2) Processor via @PaymentMethod
            Map<String, PaymentProcessor> procs = ctx.getBeansOfType(PaymentProcessor.class);
            PaymentProcessor chosen = null;

            for (PaymentProcessor pp : procs.values()) {
                PaymentMethod a = pp.getClass().getAnnotation(PaymentMethod.class);
                if (a != null && a.type().equalsIgnoreCase(p.getMethod())) {
                    chosen = pp;
                    break;
                }
            }

            boolean approved = chosen == null || chosen.process(p);

            // 3) Juros
            double monthlyInterest = 0.0;
            if (p.getInstallments() > 1) {
                monthlyInterest = 1.0;
                BigDecimal total = p.getAmount();
                total = total.add(total.multiply(BigDecimal.valueOf((monthlyInterest / 100.0) * p.getInstallments())));
                p.setTotalWithInterest(total);
            }
            p.setMonthlyInterest(monthlyInterest);
            p.setStatus(approved ? Payment.Status.APPROVED : Payment.Status.DECLINED);
            p.setUpdatedAt(Instant.now());
            payments.save(p); 

        } catch (Exception ex) {
            p.setStatus(Payment.Status.EXPIRED);
            p.setUpdatedAt(Instant.now());
            payments.save(p);
        }
    }

    // ---------------------------------------------------------------------------------------
    // WEBHOOK
    // ---------------------------------------------------------------------------------------
    private void notifyMerchant(Payment p) {
        Merchant m = merchants.findById(p.getMerchantId()).orElse(null);
        if (m != null && m.getWebhookUrl() != null) {
            String payload = "{\"event\":\"payment.updated\",\"id\":\"" + p.getId()
                    + "\",\"status\":\"" + p.getStatus() + "\"}";
            webhookDispatcher.dispatchAsync(m.getWebhookUrl(), payload, p.getId(), "payment.updated");
        }
    }

    // ---------------------------------------------------------------------------------------
    // AUTH
    // ---------------------------------------------------------------------------------------
    private Long validateAuthAndGetMerchantId(String authHeader) {
        if (authHeader == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.replace("Bearer", "").trim();

        if (!token.startsWith("FAKE-")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            return Long.valueOf(token.substring(5));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    // ---------------------------------------------------------------------------------------
    // MAPPER Payment -> PaymentResponse
    // ---------------------------------------------------------------------------------------
    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getStatus().name(),
                p.getMethod(),
                p.getAmount(),
                p.getInstallments(),
                p.getMonthlyInterest(),
                p.getTotalWithInterest()
        );
    }
}