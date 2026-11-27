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
    CONTACT_SETTINGS: api-fabriken.party-overview.contact-settings
  registry-changes:
    queue:                       # ändringskön (§5.5). Utan värde finns varken lyssnaren eller dess larm.
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
  delete-batch-size: 1000
protection:
  batch-size: 100
  legal-entity-retry: P7D        # uppehåll efter 404 från LegalEntity i den nattliga kontrollen (§5.4)
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
  task:
    scheduling:
      pool:
        size: 4                  # en tråd per schemalagt jobb, så att relayn inte väntar på den nattliga kontrollen
  rabbitmq:
    listener:
      type: direct               # försöker igen när en kö eller AMQP-användaren saknas, utan omstart (beslut 68)
      direct:
        missing-queues-fatal: false
        consumers-per-queue: 1
  datasource:
    hikari:
      maximum-pool-size: 20      # fem lyssnare, de schemalagda jobben och REST samtidigt (§3.7)
  data:
    web:
      pageable:
        max-page-size: 100
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50         # profilens arbetsställen skrivs i batchar
        order_inserts: true
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
resilience4j:
  circuitbreaker:
    instances:
      citizen:                   # klientens CLIENT_ID
        ignoreExceptions: se.sundsvall.dept44.exception.ClientProblem
      legalentity:
        ignoreExceptions: se.sundsvall.dept44.exception.ClientProblem
```

- RabbitMQ:s health-indikator ingår i `/actuator/health`, där Spring Boot Admin larmar när brokern inte svarar, men inte
  i `readiness` ([beslut 45]).
- Lyssnarna använder Spring AMQP:s direct-container ([beslut 68]). Saknas en kö eller AMQP-användaren försöker den igen
  tills de finns. Då markerar dept44-startern inte liveness som `BROKEN`, podden startas inte om, och REST-API:t
  fungerar. `missing-queues-fatal` sätts till `false` uttryckligen, så att ett annat förval i driftkonfigurationen inte
  gäller. En kö som saknas larmar i stället via `DeadLetterHealthIndicator` ([§12.1]).
- Med direct-containern körs varje lyssnare samtidigt, i en egen virtuell tråd som startern sätter upp. Fem köer ger fem
  lyssnare som kan hålla en databasanslutning var, så poolen är 20 i stället för dept44:s tio.
- Circuit breakern för Citizen och LegalEntity räknar inte `ClientProblem` (4xx) som fel. Annars skulle en serie 400
  från LegalEntity, som betyder att numret inte finns, öppna den, och varje uppslag skulle bli ett avbrott ([§7.1]).
  Instanserna heter som klienternas `CLIENT_ID`.
- Varje integration behöver OAuth2-klient, URL och sealed secrets per miljö.
- Köerna, dead letter-köerna och policyerna deklareras i tjänstens egen driftkonfiguration (spring-boot-chart), inte i
  ett gemensamt topologi-repo och inte av tjänsten själv ([§3.2]). Publicerarnas exchanges ligger i deras egen
  driftkonfiguration.
- En ny källa kräver en rad under `integration.sources`, kön och dead letter-kön i driftkonfigurationen och, om den
  publicerar objekt med parter, partsroller via REST. Kön ska finnas innan raden driftsätts. Annars försöker lyssnaren
  igen tills kön finns, och `DeadLetterHealthIndicator` larmar under tiden.
- Vid första driftsättningen i varje miljö sätts partsrollerna för `SUPPORT_MANAGEMENT`, `PARTY_ASSETS`, `CONTRACT` och
  `CASE_DATA` med `PUT /{municipalityId}/party-roles/{source}` ([beslut 33]), **innan** lyssnarna slås på.
  `CONTACT_SETTINGS` behöver inga partsroller ([§5.6]). Ett meddelande som tillämpas utan roller ger ingen kund, och
  varje part loggas på WARN.
- I SupportManagement är rollerna metadata per namespace, och `CONTACT_PERSON` finns inte i SM:s testdata. Vilka roller
  som är kundroller och kontaktroller i SM, och vilka som ska ignoreras, tas fram ur produktionens metadata tillsammans
  med verksamheterna innan rollerna sätts ([§13.2]).
- dept44:s PII-maskning är påslagen med alla kategorier, även `uuid`. Den maskar personnummer, telefonnummer, e-post och
  UUID:n i loggradernas meddelanden, payload-loggen inräknad, men inte i stacktraces. Därför får inga undantag ha
  personuppgifter i sina meddelanden ([§7.1]). Meddelande-id och request-id är också UUID:n och maskeras i
  loggraderna, men de finns omaskerade i den tekniska loggen.

## 12.1 Larm för dead letter-köer

- `DeadLetterHealthIndicator` gör en passiv deklaration (`queueDeclarePassive`) av varje källas kö och dead letter-kö,
  och av ändringsköns när `integration.registry-changes.queue` har ett värde. Den läser antalet meddelanden i dead
  letter-köerna.
- En huvudkö som saknas ger `DOWN`, med köns namn. Direct-containern försöker igen utan att larma själv, och dept44:s
  `rabbitListeners` visar en container som försöker igen som `UP`. Det är indikatorn som visar att en kö saknas.
- Indikatorn finns bara när RabbitMQ är påslaget (`@ConditionalOnBooleanProperty("dept44.rabbitmq.enabled")`, [§9]).
  Med `dept44.rabbitmq.enabled=false` finns ingen `ConnectionFactory`, och tjänsten ska ändå starta (C1).
- Indikatorn blir `DOWN`, med köernas namn och antal i detaljerna, när någon kö inte är tom. Spring Boot Admin
  notifierar då till Slack, som för andra statusändringar.
- En dead letter-kö som saknas ger också `DOWN`, eftersom det är ett fel i topologin.
- Indikatorn ingår inte i grupperna `liveness` och `readiness`, så tjänsten fortsätter att ta emot trafik.
- T1 och T3 verifierar att AMQP-användaren får göra en passiv deklaration av sina köer och dead letter-köer.
- Hur dead letters hanteras står i README: läs dem och åtgärda orsaken. Flytta sedan tillbaka meddelandena till
  källkön, t.ex. med RabbitMQ Management ("Move messages") eller en shovel. Meddelanden som inte ska behandlas raderas.

[§9]: 09-kodstruktur.md
[§13.2]: 13-risker-och-oppna-fragor.md#132-förutsättningar-före-driftsättning
[beslut 33]: 00-beslutslogg.md#beslut-33-partsrollerna-sätts-via-rest-vid-driftsättning
[beslut 45]: 00-beslutslogg.md#beslut-45-rabbitmq-ingår-inte-i-readiness
[§7.1]: 07-integrationer.md#71-uppslag-av-partyid
[§3.2]: 03-meddelanden.md#32-topologi
[§12.1]: #121-larm-för-dead-letter-köer
[beslut 68]: 00-beslutslogg.md#beslut-68-lyssnarna-använder-direct-containern
[§5.6]: 05-synlighet-och-kunddata.md#56-kontaktinställningar

