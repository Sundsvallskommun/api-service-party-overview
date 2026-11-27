# 9. Kodstruktur och konventioner

Del av [arkitekturen för party-overview](README.md).

```
se.sundsvall.partyoverview
├─ api/                         CustomerResource, CustomerSearchResource, EngagementResource,
│  │                            NoteResource, ContactPersonResource, TechnicalLogResource, PartyRoleResource,
│  │                            SearchIndexResource, WorkplaceResource, LabelResource, CustomerLabelResource
│  └─ model/                    Customer, EnterpriseProfile, Workplace, Engagement, HistoryEntry, Note, Label, …
├─ service/                     CustomerService, EngagementService, NoteService, ContactPersonService,
│  │                            TechnicalLogService, PartyRoleService, LabelService (katalogen och kundens etiketter)
│  ├─ message/                  MessageService (ingen tx), MessageWriter (@Transactional)
│  ├─ protection/               ProtectionService (räknar om kundens skyddsnivå och skriver personer i registry_person)
│  ├─ enterprise/               EnterpriseProfileService (skriver profilen), EnterpriseProfileMapper (SNI-koderna),
│  │                            SniClassificationService (skriver och läser SNI-trädet)
│  ├─ search/                   CustomerSearchService, SearchIndexMarker, SearchIndexRelay,
│  │                            SearchReindexService, SearchAvailability, SearchSchemaInitializer
│  ├─ mapper/
│  └─ model/                    ObjectChange, PartyChange, Activity
├─ integration/
│  ├─ db/                       repositories
│  │  ├─ model/                 entiteter, AbstractHistoryEntity, enums, RolesConverter
│  │  └─ search/                CustomerDocumentBinder, SearchAnalysisConfigurer, OpenSearchHealthIndicator
│  ├─ rabbitmq/                 MessageListener, RegistryChangeListener, SourceProperties (källa → kö),
│  │                            DeadLetterHealthIndicator
│  ├─ citizen/                  CitizenClient, CitizenIntegration, configuration/
│  └─ legalentity/              LegalEntityClient, LegalEntityIntegration, configuration/
└─ scheduler/                   ProtectionRefreshScheduler, SearchIndexRelayScheduler,
                                TechnicalLogRetentionScheduler
```

- **Beroenden:** `dept44-starter-jpa`, `dept44-starter-feign`, `dept44-starter-rabbitmq`, `dept44-starter-scheduler`,
  `dept44-common-validators`, `hibernate-search-mapper-orm` och `hibernate-search-backend-elasticsearch`. För test:
  `dept44-starter-test`, `dept44-starter-jpa-test`, Testcontainers RabbitMQ och `opensearch-testcontainers`.
- **Genererade modeller:**
  - `citizen.yaml` och `legalentity.yaml` genereras med openapi-generator till
    `generated.se.sundsvall.*`, som i POC:ens `pom.xml`.
  - `party-overview-messages.yaml` är ett AsyncAPI-dokument. openapi-generator kan inte läsa AsyncAPI, så modellerna
    genereras med ZenWave SDK (`zenwave-sdk-maven-plugin` med `asyncapi-jsonschema2pojo`, 2.8.1), som på grenen
    `publish-events` i det här repot.
- **Entiteter:** ingen Lombok, utan `create()`/`withX()` och `equals`/`hashCode`/`toString` som i POC:en.
- **Lyssnaren:** en klass, `MessageListener`, men en direct-container per kö ([beslut 17], [beslut 68]). Endpointerna
  registreras med `RabbitListenerConfigurer`, en per post i `integration.sources`, så att de ligger i Springs
  listener-register och får dept44-starterns loggning och request-id. Ingen `@ConditionalOnProperty` på lyssnaren,
  eftersom startern inte startar lyssnare när RabbitMQ är avstängt. Ingen `@EnableRabbit`, och ingen egen
  `MessageConverter`. Ändringskön har ett annat format och en egen klass, `RegistryChangeListener`, med en egen
  container ([§5.5]). Den finns bara när `integration.registry-changes.queue` har ett värde, så att tjänsten startar
  innan kön finns.
- **`DeadLetterHealthIndicator`:** villkorad med `@ConditionalOnBooleanProperty("dept44.rabbitmq.enabled")`, samma
  tolkning som startern. `@ConditionalOnBean(ConnectionFactory.class)` fungerar inte på en komponent som tjänsten själv
  skannar in, eftersom villkoret utvärderas innan auto-konfigurationens `ConnectionFactory` finns. Då skulle
  indikatorn aldrig skapas.
- **Kommentarer och javadoc** beskriver vad koden gör, inte hur den kom till. Kontrakt som anroparen behöver känna till,
  som transaktionsgränser och felvägar, står med.
- **Inga TODO:s** och ingen bortkommenterad kod.

[§5.5]: 05-synlighet-och-kunddata.md#55-företagsuppgifter
[beslut 17]: 00-beslutslogg.md#beslut-17-en-lyssnare-och-en-mappning-för-alla-källors-köer
[beslut 68]: 00-beslutslogg.md#beslut-68-lyssnarna-använder-direct-containern

