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
  -d '{"client_id":"0a77f78a-a905-48ef-842f-e5cc50a7276e","client_secret":"1673eba729a74ce0b916e1fae83cd16c"}'

RESULTADO
{"access_token":"FAKE-1","token_type":"Bearer","expires_in":3600}

```

3) **Criar pagamento** OKAY
```bash
curl -X POST http://localhost:8080/fiadopay/gateway/payments   -H "Authorization: Bearer FAKE-2"   -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000"   -H "Content-Type: application/json"   -d '{"method":"CARD","currency":"BRL","amount":250.50,"installments":12,"metadataOrderId":"ORD-123"}'

RESULTADO
{"id":"pay_9723d1df-8de7-4443-869d-b9ee36d10c86","status":"PENDING","method":"CARD","amount":250.50,"installments":12,"interestRate":0.0,"total":250.50}

```

4) **Consultar pagamento** OKAY
```bash
<<<<<<< HEAD
curl http://localhost:8080/fiadopay/gateway/payments/<paymentId>
```
------------------------------------------------------------------------------------------
# Fiado-Pay-API - Refatoração 

A **Fiado-Pay-API** é o resultado da refatoração profunda da API original (`https://github.com/pooadoc/fiadopay.git`).

O objetivo central desta refatoração foi transformar a estrutura inicial, que apresentava alto acoplamento e baixa manutenibilidade, em uma API **robusta, escalável e com código limpo**, baseada em princípios de arquitetura moderna e aplicação de padrões de projeto.

---

### Contexto Escolhido

* **Domínio Principal:** Gestão de Dívidas e Transações (Criação, Pagamento, Consultas, Lançamento de Juros).
* **Versão Refatorada:** Adoção de **Arquitetura Limpa (Clean Architecture)** para isolar o domínio, garantir a testabilidade e facilitar a manutenção, focando em **desacoplamento e coesão**.

### Decisões de Design

As principais decisões de design visaram desacoplar a aplicação e preparar o sistema para o crescimento:

* **Arquitetura Orientada ao Domínio (DDD):** Modelagem rigorosa das **Entidades** e **Value Objects** de negócio (e.g., Cliente, Dívida), garantindo que as regras de negócio residam na camada de Domínio.
* **Separação de Camadas (Ports and Adapters):** O código é dividido em Camadas (Domínio, Aplicação, Infraestrutura) onde as interfaces (Portas) são implementadas por classes de infraestrutura (Adaptadores), isolando o **Domínio** da tecnologia.
* **Injeção de Dependência (IoC/DI):** Utilização de um contêiner para gerenciar o ciclo de vida dos componentes, promovendo o acoplamento fraco.

### Anotações Criadas e seus Metadados

Foram criadas anotações customizadas para aplicar lógica de forma declarativa e não invasiva (Programação Orientada a Aspectos - AOP).

| Anotação Customizada | Uso/Contexto | Metadados Principais (Exemplo) |
| :--- | :--- | :--- |
| **`@<Nome da Anotação 1>`** | `<Ex: Validação de limites de crédito.>` | `limiteValor` (double), `diasBloqueio` (int) |
| **`@Auditoria`** | Usada em métodos de serviço para registrar o autor e o *timestamp* de uma alteração. | `tipoOperacao` (enum: CREATE, UPDATE) |

### Mecanismo de Reflexão

O uso da **Reflexão** é a fundação do *bootstrapping* e do processamento das anotações customizadas.

* **Inversão de Controle (IoC):** A Reflexão é usada para escanear pacotes em busca de classes anotadas (e.g., `@Service`) e instanciar as dependências automaticamente, construindo o **grafo de objetos**.
* **Processamento AOP:** É utilizada para ler os **Metadados** das anotações customizadas em tempo de execução, permitindo que a lógica de aspectos (como auditoria ou validação) seja aplicada antes ou depois do método original via *Proxies*.

### Threads

A gestão de **Threads** é crucial para a responsividade e escalabilidade da API:

* **Pool de Threads HTTP:** O servidor web (e.g., Tomcat/Kestrel) gerencia um pool de threads para atender múltiplas requisições simultaneamente.
* **Tarefas Assíncronas:** Utilização de um **ExecutorService** (ou análogos de framework) para delegar tarefas que não exigem resposta imediata.
    * **Exemplo:** O envio de e-mails de notificação ou a geração de relatórios complexos são despachados para uma thread separada, liberando a thread principal da requisição mais rapidamente.

### Padrões Aplicados

Diversos **Padrões de Projeto (Design Patterns)** foram aplicados na refatoração:

| Padrão | Tipo | Camada | Objetivo |
| :--- | :--- | :--- | :--- |
| **Repository** | Estrutural | Infraestrutura | Abstrair a lógica de persistência de dados do restante da aplicação. |
| **Strategy** | Comportamental | Domínio/Aplicação | Definir uma família de algoritmos (e.g., diferentes métodos de cálculo de juros) e torná-los intercambiáveis. |
| **Factory Method** | Criacional | Infraestrutura | Criar instâncias de objetos complexos (e.g., *parsers* de pagamento) sem expor a lógica de criação. |

### Limites Conhecidos

Embora a refatoração tenha elevado a qualidade do código, alguns limites conhecidos permanecem:

* **Escalabilidade de Persistência:** O banco de dados atual pode se tornar um gargalo sob extrema carga. Seria necessário implementar *sharding* ou migrar para soluções distribuídas para lidar com milhões de transações.
* **Segurança Avançada:** A implementação de recursos de segurança mais avançados, como **Rate Limiting** e **Circuit Breaker**, está pendente em *endpoints* críticos.

=======
curl http://localhost:8080/fiadopay/gateway/payments/pay_c38a2c12-bbe0-4df8-9981-626ca613975e
RESULTADO
{"id":"pay_9723d1df-8de7-4443-869d-b9ee36d10c86","status":"APPROVED","method":"CARD","amount":250.50,"installments":12,"interestRate":1.0,"total":280.56}

```
>>>>>>> e3a832e4b3faa84f1162d8fd7d48caeb01edc691
