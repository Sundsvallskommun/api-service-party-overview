# 12. Drift och konfiguration

Del av [arkitekturen för party-overview](README.md).

```yaml
dept44:
  rabbitmq:
    enabled: true
  logback:
    pii-masking:
      enabled: true              # alla kategorier, även uuid, enligt dept44:s PII-policy (§2)
integration:
  sources:                       # källa → kö
    SUPPORT_MANAGEMENT: api-fabriken.party-overview.support-management
    PARTY_ASSETS: api-fabriken.party-overview.party-assets
    CONTRACT: api-fabriken.party-overview.contract
    CASE_DATA: api-fabriken.party-overview.case-data
  registry-changes:
    queue: …                     # ändringskön för företag och personer (§5.5), som inte finns ännu
  dead-letter-suffix: .dlq       # DLQ = kö + suffix, används av health-indikatorn
  citizen:
    url: …
  legalentity:
    url: …
logbook:
  excluded:
    paths: "**/guid"             # LegalEntitys uppslag med organisationsnumret i URL:en (§7.1)
technical-log:
  retention: P5Y                 # gallring (§8.2), bestäms med arkivfunktionen och dataskyddsombudet
protection:
  batch-size: 100
search:
  timeout: PT5S
  relay:
    batch-size: 500
    max-run-time: PT4M           # under relayns shedlock-lock-at-most-for (§6.2)
  reindex:
    lock-at-most-for: PT5M       # förlängs av KeepAliveLockProvider medan ombyggnaden pågår
scheduler:
  protection-refresh:
    cron: "0 0 2 * * *"
    shedlock-lock-at-most-for: PT3H
    maximum-execution-time: PT3H # förvalet i @Dept44Scheduled är PT2M
  technical-log-retention:
    cron: "0 0 3 * * *"
    shedlock-lock-at-most-for: PT1H
    maximum-execution-time: PT1H
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
            version: opensearch:3.6  # låst, så att starten inte beror på klustret (§6.2)
            version_check:
              enabled: false
            connection_timeout: 2000
            read_timeout: 10000
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
- Vid första driftsättningen i varje miljö sätts kontaktpersonsrollerna för `SUPPORT_MANAGEMENT`, `CONTRACT` och
  `CASE_DATA` med `PUT /{municipalityId}/contact-person-roles/{source}` ([beslut 33]), **innan** lyssnarna slås på. Ett
  meddelande som tillämpas utan roller gör varje kontaktperson med id till kund.
- För contract och Casedata är rollen `CONTACT_PERSON`. I SupportManagement är rollerna metadata per namespace, och
  `CONTACT_PERSON` finns inte i SM:s testdata. Vilka roller som betyder kontaktperson i SM tas fram ur produktionens
  metadata innan rollerna sätts ([§13.2]).
- dept44:s PII-maskning är påslagen med alla kategorier, även `uuid`. Den maskar personnummer, telefonnummer, e-post och
  UUID:n i loggradernas meddelanden, payload-loggen inräknad, men inte i stacktraces. Därför får inga undantag ha
  personuppgifter i sina meddelanden ([§7.1]). Meddelande-id och request-id är också UUID:n och maskeras i
  loggraderna, men de finns omaskerade i den tekniska loggen.

## 12.1 Larm för dead letter-köer

- `DeadLetterHealthIndicator` gör en passiv deklaration (`queueDeclarePassive`) av varje källas och ändringsköns dead
  letter-kö och
  läser antalet meddelanden.
- Indikatorn finns bara när RabbitMQ är påslaget (`@ConditionalOnBooleanProperty("dept44.rabbitmq.enabled")`, [§9]).
  Med `dept44.rabbitmq.enabled=false` finns ingen `ConnectionFactory`, och tjänsten ska ändå starta (C1).
- Indikatorn blir `DOWN`, med köernas namn och antal i detaljerna, när någon kö inte är tom. Spring Boot Admin
  notifierar då till Slack, som för andra statusändringar.
- En dead letter-kö som saknas ger också `DOWN`, eftersom det är ett fel i topologin.
- Indikatorn ingår inte i grupperna `liveness` och `readiness`. Tjänsten fortsätter alltså att ta emot trafik.
- Att AMQP-användaren får göra en passiv deklaration av dead letter-köerna verifieras i T1.
- Hur dead letters hanteras står i README: läs dem och åtgärda orsaken. Flytta sedan tillbaka meddelandena till
  källkön, t.ex. med RabbitMQ Management ("Move messages") eller en shovel. Meddelanden som inte ska behandlas raderas.

[§9]: 09-kodstruktur.md
[§13.2]: 13-risker-och-oppna-fragor.md#132-förutsättningar-före-driftsättning
[beslut 33]: 00-beslutslogg.md#beslut-33-kontaktpersonsrollerna-sätts-via-rest-vid-driftsättning
[§7.1]: 07-integrationer.md#71-uppslag-av-partyid

