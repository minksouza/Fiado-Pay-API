# FiadoPay Simulator (Spring Boot + H2)

Gateway de pagamento **FiadoPay** para a AVI/POOA.
Substitui PSPs reais com um backend em memória (H2).

## Rodar
```bash
./mvnw spring-boot:run
# ou
mvn spring-boot:run
```

H2 console: http://localhost:8080/h2  
Swagger UI: http://localhost:8080/swagger-ui.html

## Fluxo

1) **Cadastrar merchant** OKAY
```bash
curl -X POST http://localhost:8080/fiadopay/admin/merchants   -H "Content-Type: application/json"   -d '{"name":"MinhaLoja ADS","webhookUrl":"http://localhost:8081/webhooks/payments"}'

RESULTADO
{"id":1,"name":"MinhaLoja ADS","clientId":"9c67fa63-bda7-42ef-b9a5-14cc05b1383a","clientSecret":"3dde7070d36c451285e6ea3cbd5520fb","webhookUrl":"http://localhost:8081/webhooks/payments","status":"ACTIVE"}

```

2) **Obter token**
```bash
curl -X POST http://localhost:8080/fiadopay/auth/token   -H "Content-Type: application/json"   -d '{"client_id":"<clientId>","client_secret":"<clientSecret>"}'

curl -X POST http://localhost:8080/fiadopay/auth/token \
  -H "Content-Type: application/json" \
  -d '{"client_id":"9c67fa63-bda7-42ef-b9a5-14cc05b1383a","client_secret":"3dde7070d36c451285e6ea3cbd5520fb"}'

RESULTADO
{"access_token":"FAKE-1","token_type":"Bearer","expires_in":3600}

```

3) **Criar pagamento** OKAY
```bash
curl -X POST http://localhost:8080/fiadopay/gateway/payments   -H "Authorization: Bearer FAKE-<merchantId>"   -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000"   -H "Content-Type: application/json"   -d '{"method":"CARD","currency":"BRL","amount":250.50,"installments":12,"metadataOrderId":"ORD-123"}'

RESULTADO
{"id":"pay_9723d1df-8de7-4443-869d-b9ee36d10c86","status":"PENDING","method":"CARD","amount":250.50,"installments":12,"interestRate":0.0,"total":250.50}

```

4) **Consultar pagamento** OKAY
```bash
curl http://localhost:8080/fiadopay/gateway/payments/<paymentId>

RESULTADO
{"id":"pay_9723d1df-8de7-4443-869d-b9ee36d10c86","status":"APPROVED","method":"CARD","amount":250.50,"installments":12,"interestRate":1.0,"total":280.56}

```
