# 9. Kodstruktur och konventioner

Del av [arkitekturen för party-overview](README.md).

```
se.sundsvall.partyoverview
├─ api/                         CustomerResource, CustomerSearchResource, EngagementResource,
│  │                            NoteResource, TechnicalLogResource, ContactPersonRoleResource,
│  │                            SearchIndexResource
│  └─ model/                    Customer, Engagement, HistoryEntry, Note, TechnicalLogEntry, …
├─ service/                     CustomerService, EngagementService, NoteService,
│  │                            TechnicalLogService, ContactPersonRoleService
│  ├─ message/                  MessageService (ingen tx), MessageWriter (@Transactional)
│  ├─ protection/               ProtectionService (räknar om skyddsnivån, tömmer skyddade uppgifter)
│  ├─ search/                   CustomerSearchService, SearchIndexMarker, SearchIndexRelay,
│  │                            SearchReindexService, SearchAvailability, SearchSchemaInitializer
│  ├─ mapper/
│  └─ model/                    ObjectChange, PartyChange, Activity
├─ integration/
│  ├─ db/                       repositories
│  │  ├─ model/                 entiteter, AbstractHistoryEntity, enums, RolesConverter
│  │  └─ search/                CustomerDocumentBinder, SearchAnalysisConfigurer, OpenSearchHealthIndicator
│  ├─ rabbitmq/                 MessageListener, SourceProperties (kö → källa), DeadLetterHealthIndicator
│  ├─ party/                    PartyClient, PartyIntegration, configuration/
│  ├─ citizen/                  CitizenClient, CitizenIntegration, configuration/
│  └─ legalentity/              LegalEntityClient, LegalEntityIntegration, configuration/
└─ scheduler/                   ProtectionRefreshScheduler, SearchIndexRelayScheduler
```

- **Beroenden:** `dept44-starter-jpa`, `dept44-starter-feign`, `dept44-starter-rabbitmq`, `dept44-starter-scheduler`,
  `dept44-common-validators`, `hibernate-search-mapper-orm` och `hibernate-search-backend-elasticsearch`. För test:
  `dept44-starter-test`, `dept44-starter-jpa-test`, Testcontainers RabbitMQ och `opensearch-testcontainers`.
- **Genererade modeller:** `integrations/party-overview-messages.yaml`, `party.yaml`, `citizen.yaml` och
  `legalentity.yaml`, genererade med openapi-generator till `generated.se.sundsvall.*`, som i POC:ens `pom.xml`.
- **Entiteter:** ingen Lombok, utan `create()`/`withX()` och `equals`/`hashCode`/`toString` som i POC:en.
- **Lyssnaren:** ingen `@ConditionalOnProperty`, eftersom startern inte startar lyssnare när RabbitMQ är avstängt. Ingen
  `@EnableRabbit`, och ingen `MessageConverter` av egen.
- **Kommentarer och javadoc** beskriver vad koden gör, inte hur den kom till. Kontrakt som anroparen behöver känna till,
  som transaktionsgränser och felvägar, står med.
- **Inga TODO:s** och ingen bortkommenterad kod.

