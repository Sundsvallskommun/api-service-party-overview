# 12. Drift och konfiguration

Del av [arkitekturen för party-overview](README.md).

```yaml
dept44:
  rabbitmq:
    enabled: true
integration:
  sources:                       # källa → kö
    SUPPORT_MANAGEMENT: api-fabriken.party-overview.support-management
    PARTY_ASSETS: api-fabriken.party-overview.party-assets
    CONTRACT: api-fabriken.party-overview.contract
    CASE_DATA: api-fabriken.party-overview.case-data
  dead-letter-suffix: .dlq       # DLQ = kö + suffix, används av health-indikatorn
  party:
    url: …
  citizen:
    url: …
  legalentity:
    url: …
protection:
  refresh-age: PT20H # kortare än ett dygn, så att varje natt når förra nattens kunder (§5.4)
  batch-size: 100
search:
  timeout: PT5S
  relay:
    batch-size: 500
  reindex:
    lock-at-most-for: PT2H
scheduler:
  protection-refresh:
    cron: "0 0 2 * * *"
    shedlock-lock-at-most-for: PT3H
    maximum-execution-time: PT3H # förvalet i @Dept44Scheduled är PT2M
  search-index-relay:
    cron: "*/10 * * * * *"
    shedlock-lock-at-most-for: PT5M
    maximum-execution-time: PT5M
spring:
  data:
    web:
      pageable:
        max-page-size: 100
  jpa:
    properties:
      hibernate:
        search:
          enabled: false         # true per miljö när OpenSearch finns
          backend:
            uris: …
          indexing:
            listeners:
              enabled: false
          schema_management:
            strategy: none
```

- RabbitMQ:s health-indikator ingår i `/actuator/health`, där Spring Boot Admin larmar när brokern inte svarar, men inte
  i `readiness`. Readiness styr bara REST-trafiken, och läs-API:t fungerar utan brokern.
- Varje integration behöver OAuth2-klient, URL och sealed secrets per miljö.
- En ny källa kräver en rad under `integration.sources`, kön och dead letter-kön i rabbitmq-topology och
  kontaktpersonsroller via REST.
- Vid första driftsättningen i varje miljö sätts `CONTACT_PERSON` som kontaktpersonsroll för `SUPPORT_MANAGEMENT`,
  `CONTRACT` och `CASE_DATA` med `PUT /{municipalityId}/contact-person-roles/{source}` ([beslut 33]).

## 12.1 Larm för dead letter-köer

- `DeadLetterHealthIndicator` gör en passiv deklaration (`queueDeclarePassive`) av varje källas dead letter-kö och
  läser antalet meddelanden.
- Indikatorn finns bara när RabbitMQ är påslaget (`@ConditionalOnBean(ConnectionFactory.class)`). Med
  `dept44.rabbitmq.enabled=false` finns ingen `ConnectionFactory`, och tjänsten ska ändå starta (C1).
- Indikatorn blir `DOWN`, med köernas namn och antal i detaljerna, när någon kö inte är tom. Spring Boot Admin
  notifierar då till Slack, som för andra statusändringar.
- Indikatorn ingår inte i grupperna `liveness` och `readiness`. Tjänsten fortsätter alltså att ta emot trafik.
- Att AMQP-användaren får göra en passiv deklaration av dead letter-köerna verifieras i T1.
- Hur dead letters hanteras står i README: läs dem och åtgärda orsaken. Flytta sedan tillbaka meddelandena till
  källkön, t.ex. med RabbitMQ Management ("Move messages") eller en shovel. Meddelanden som inte ska behandlas raderas.

[beslut 33]: 00-beslutslogg.md#beslut-33-kontaktpersonsrollerna-sätts-via-rest-vid-driftsättning

