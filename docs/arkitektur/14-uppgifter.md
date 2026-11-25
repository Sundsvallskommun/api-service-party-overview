# 14. Uppgifter att bygga

Del av [arkitekturen för party-overview](README.md).

Varje uppgift går att slå ihop för sig. Acceptanskriterierna kan klistras in i en Jira-subtask. **C** är
party-overview, **T** är topologin och **SM**, **PA**, **CT** och **CD** är publicerarna. Formatet (**F1**) ägs av
beställaren och definieras senare.

**Gäller alla uppgifter (Definition of Done):**

- `mvn clean verify` är grönt och JaCoCo-gränsen uppfylld.
- `openapi.yaml` är regenererad och WireMock-stubbarna uppdaterade när API:t ändras.
- Javadoc och kommentarer beskriver vad koden gör.
- Inga TODO:s.
- README är uppdaterad när konfiguration eller drift påverkas.

| Steg | Id  |                             Uppgift                              |                          Beror på                           |
|------|-----|------------------------------------------------------------------|-------------------------------------------------------------|
| 1    | C1  | Projektgrund: skrota POC:en, beroenden, konfiguration            | —                                                           |
| 2    | C2  | Datamodell, Flyway och schemaverifiering                         | C1                                                          |
| 3    | C3  | Integrationer mot Party, Citizen och LegalEntity                 | C1                                                          |
| 4    | C4  | Kontaktpersonsroller via REST                                    | C2                                                          |
| 5    | C5  | Meddelandekärnan, inklusive skyddad identitet                    | C2, C3, C4, F1                                              |
| 6    | C6  | Kundmeddelanden (`CUSTOMER`)                                     | C5                                                          |
| 7    | C7  | Läs-API och uppslag för kund, kontakter, engagemang och historik | C2, C3                                                      |
| 8    | C8  | Noteringar                                                       | C2, C3                                                      |
| 9    | C9  | Kundradering via REST och API för teknisk logg                   | C2                                                          |
| 10   | C10 | Skyddad identitet: nattlig kontroll                              | C3, C5                                                      |
| 11   | T2  | Egen OpenSearch-container per miljö                              | —                                                           |
| 12   | C12 | Sökindex: Hibernate Search, kö och relay                         | C2, T2                                                      |
| 13   | C13 | Sök-endpoint                                                     | C12                                                         |
| 14   | T1  | Köer och exchanges i rabbitmq-topology                           | F1                                                          |
| 15   | C11 | RabbitMQ-lyssnare, larm för dead letters och `MessageIT`         | C5, C6, T1, release av startern, behörighet i Metakatalogen |
| 16   | SM1 | SM: outbox, relay och publicering                                | F1, release av startern                                     |
| 17   | SM2 | SM: grundladdning                                                | SM1                                                         |
| 18   | CD1 | Casedata: outbox, relay och publicering                          | F1, release av startern                                     |
| 19   | CD2 | Casedata: grundladdning                                          | CD1                                                         |
| 20   | PA1 | party-assets: outbox, relay och publicering                      | F1, release av startern                                     |
| 21   | PA2 | party-assets: grundladdning via outbox                           | PA1                                                         |
| 22   | CT1 | contract: tidsstämplar och vem                                   | —                                                           |
| 23   | CT2 | contract: outbox, relay och publicering                          | CT1, F1, release av startern                                |
| 24   | CT3 | contract: grundladdning                                          | CT2                                                         |

## C1 — Projektgrund

**Bygg:**

- Ta bort POC-koden (`integration/source`, `EventService`, `PartyOverviewService`, POC-entiteter och tester, `PartyOverviewIT`).
- I `pom.xml`: byt `spring-boot-starter-amqp` mot `dept44-starter-rabbitmq` (SNAPSHOT tills den är släppt), och lägg
  till `dept44-starter-feign`, `dept44-starter-scheduler` och `dept44-common-validators`.
- Lägg in kodgeneringen för `party-overview-messages.yaml`, `party.yaml`, `citizen.yaml` och `legalentity.yaml`.
- Lägg in `application.yml` och profilerna enligt [§12].
- README skrivs om efter detta dokument.

**Acceptans:**

- Tjänsten startar med `dept44.rabbitmq.enabled=false` utan någon anslutning till RabbitMQ.
- `management.health.rabbit.enabled` är borttagen ur `application.yml`.
- Ingen POC-kod finns kvar, och `openapi.yaml` är regenererad utan POC:ens endpoint.

## C2 — Datamodell, Flyway och schemaverifiering

**Bygg:**

- En ny `V1_0__create_tables.sql` med alla tabeller i [§4.3], inklusive `shedlock`.
- Entiteter, `AbstractHistoryEntity`, enums och `RolesConverter`.
- Repositories med de frågor [§3], [§5] och [§8] behöver, inklusive markeringen i `search_index_queue` (IODKU, [§6.2]).
- `schema.sql`, `truncate.sql`, `CascadeIT` och `SchemaParityIT`.

**Acceptans:**

- Namn, kolumner, index och FK stämmer exakt med [§4.3]. Varje FK under `customer` har `on delete cascade`, både i
  Flyway och i `schema.sql`.
- `SchemaVerificationTest` och `SchemaParityIT` är gröna. Om ett index tas bort i bara den ena källan blir
  `SchemaParityIT` röd. Det ska provas manuellt en gång.
- `CascadeIT`: `DELETE FROM customer WHERE id = ?` lämnar noll rader i `engagement`, `engagement_history`, `contact`,
  `contact_channel`, `note` och `note_history` för kunden. Raderna i `technical_log` och `source_object` finns kvar.
- `created` och `modified` sätts av `@PrePersist`/`@PreUpdate`, trunkeras till millisekunder och testas per entitet.
- Repository-tester: IODKU för `source_object` och `customer` är idempotent. Samtidighetstestet med två trådar som
  skapar samma kund ger en rad och inget undantag.

## C3 — Integrationer mot Party, Citizen och LegalEntity

**Bygg:**

- `PartyClient`/`PartyIntegration` med `getPartyIds(PRIVATE, batch)`, `getPartyId(ENTERPRISE)` och `getPartyType`.
- `CitizenClient`/`CitizenIntegration` med `getCitizen` och `getCitizens` (batch), som båda returnerar skyddsnivå och,
  för oskyddade personer, namn och adress ([§5.4]).
- `LegalEntityClient`/`LegalEntityIntegration`.
- Konfiguration med OAuth2 och circuit breaker.
- WireMock-stubbar för IT.

**Acceptans:**

- 404 ger `Optional.empty`, och 5xx ger `ServerProblem` som inte sväljs.
- Inga legalId i några loggrader eller undantagsmeddelanden. Testet stubbar ett 404 vars detail innehåller legalId och
  kontrollerar att det inte syns i undantaget.
- Citizen anropas alltid med `ShowClassified=true`. Flaggorna tolkas enligt tabellen i [§5.4], med ett testfall per rad.
  För en skyddad person returnerar integrationen varken namn eller adress.
- Tolkningen av `classified` och `protectedNR` ([beslut 41]) verifieras i testmiljön mot en sekretessmarkerad testperson
  och en testperson med skyddad folkbokföring. Resultatet dokumenteras i README.
- 401 från Citizen ger en `ClientProblem` vars detail säger att behörigheten i Metakatalogen saknas.
- Ingenting ur Citizens svar loggas.
- Partytypen avgörs med `PRIVATE` först och sedan `ENTERPRISE`. Om ingen av dem hittas blir det `Optional.empty`.
- Anropet med organisationsnummer i URL:en finns inte i loggern `se.sundsvall.dept44.payload`. Det verifieras med ett
  test ([§7.1]).

## C4 — Kontaktpersonsroller via REST

**Bygg:**

- `ContactPersonRoleResource`, `ContactPersonRoleService` och repository, enligt [§6].

**Acceptans:**

- `PUT` ersätter källans roller som en helhet. En tom lista tar bort alla.
- En okänd källa, alltså en som saknas i `integration.sources`, ger 400. En tom eller blank roll ger 400.
- `created_by` sätts från `X-Sent-By`, och ett anrop utan den headern ger 400.
- Resource-test, failure-test och IT (happy case).

## C5 — Meddelandekärnan

**Bygg:**

- `ObjectChange` och mappningen från den genererade meddelandemodellen.
- `MessageService` utan transaktion och `MessageWriter` med `@Transactional`, enligt [§3.3]–[§3.6] för `ERRAND`, `ASSET`
  och `CONTRACT`.
- Klassningen av parter och kontaktuppgifterna ([§5.3]), "nyast vinner" ([§5.2]) och skrivningen av den tekniska loggen
  ([§8.1]).
- Ännu ingen lyssnare.

**Acceptans:**

- Versionsmatrisen i [§3.4] testas tabelldrivet, och varje cell har ett eget fall.
- Om samma meddelande tillämpas två gånger blir det ingen dubblerad historik, och den andra loggraden blir
  `ENGAGEMENT_UPDATED`.
- En part som försvinner ur objektet ger `ENGAGEMENT_REMOVED`. Var det kundens sista engagemang och har kunden inga
  noteringar ger det dessutom `CUSTOMER_DELETED`. En kund med en notering finns kvar.
- En kontaktpersonsroll ger en `CONTACT_PERSON`-kontakt på varje `ENTERPRISE`-kund och aldrig en egen kund. Ett objekt
  utan företagskund ger ingen kontakt.
- En part med legalId mappas och tillämpas. Om Party svarar 404 hoppas parten över medan övriga parter tillämpas.
  legalId finns inte i någon entitet.
- En kund som förekommer två gånger blir en rad, och rollerna slås ihop.
- Ett nyare `sourceModified` ersätter namn och adress, men ett äldre gör det inte.
- En ny privatkund får sin skyddsstatus från Citizen innan den skapas. Om Citizen inte svarar, svarar 5xx eller har en
  öppen circuit breaker skapas kunden med `UNKNOWN`, utan namn, adress och egna kontaktuppgifter, och meddelandet
  tillämpas utan omförsök. 401 från Citizen dead-letteras ([§5.4]).
- `UNKNOWN` från folkbokföringen ger den effektiva nivån `UNKNOWN`, men en källas `SECRECY_MARKING` eller
  `PROTECTED_REGISTRATION` går före. En kund med `UNKNOWN` indexeras bara med engagemangens referenser.
- README och javadoc för `ProtectionLevel` beskriver alla fyra värden. För `UNKNOWN` står när värdet uppstår, att
  kunden hanteras som skyddad och att den nattliga kontrollen ersätter det ([§5.4]).
- En part med `protection` ger `engagement.party_protection`, och kunden räknas som skyddad. Namn, adress och egna
  kontaktuppgifter sparas inte, och befintliga töms. Skyddet upphör när inget engagemang längre anger det och
  folkbokföringen inte gör det heller.
- En ändrad skyddsnivå loggas som `PROTECTION_CHANGED`.
- Varje kund som får en rad i den tekniska loggen, utom `IGNORED_STALE`, markeras i `search_index_queue` i samma
  transaktion.
- Ett ogiltigt meddelande ger `ConstraintViolationException`, som dead-letteras. Ett test per obligatoriskt fält i [§3.1].
- Feign-anropen görs utanför transaktionen: `MessageService` har ingen `@Transactional`, och `MessageWriter` anropar
  inga integrationer.
- Samtidighetstest: ett objekt raderas medan ett meddelande från en annan källa ger samma kund ett nytt engagemang.
  Kunden och det nya engagemanget finns kvar, oavsett vilken transaktion som får låset först ([§3.3]).
- En `DELETE` där den första av två kunder raderas automatiskt markerar ändå `source_object` som raderat, och den andra
  kundens skyddsnivå räknas om ([§3.6]).
- En part utan id behöver ingen `partyType`. En part med id men utan `partyType`, och ett textfält över maxlängden,
  ger valideringsfel ([§3.1]).

## C6 — Kundmeddelanden (`CUSTOMER`)

**Bygg:**

- `CUSTOMER UPDATE` och `CUSTOMER DELETE` enligt [§3.5] och [§3.6].

**Acceptans:**

- `UPDATE` skapar eller uppdaterar kunden och ersätter bara den här källans kontaktuppgifter på kundnivå. Andra källors
  uppgifter och engagemangens kontakter lämnas orörda.
- `DELETE` raderar kunden med allt den äger och skriver `CUSTOMER_DELETED` med kanalen `MESSAGE` och objekttypen
  `CUSTOMER`.
- Versionsvakten fungerar likadant som för andra objekt, nycklad på partyId.
- `DELETE` raderar kunden även när källan aldrig har skickat `CUSTOMER UPDATE` för den, och när kunden har raderats och
  skapats på nytt av ett engagemang ([§3.4]). Ett `DELETE` utan part ger valideringsfel.

## C7 — Läs-API för kund, kontakter, engagemang och historik

**Bygg:**

- `CustomerResource` (GET och lookup) och `EngagementResource` med service och API-modeller, enligt [§5.1] och [§6.1].

**Acceptans:**

- Kontakter visas för det egna namespacet och de globala, aldrig för andra namespaces. Dubbletter slås ihop.
- En skyddad kund, också en med `UNKNOWN`, returneras med `protectionLevel`, men utan namn, adress och kontaktkanaler.
- `protectionLevel` i OpenAPI-specen beskriver alla fyra värden, och att `UNKNOWN` betyder okänd skyddsstatus som ska
  hanteras som skyddad ([§5.4]).
- Uppslaget tar `legalId` och `partyType` i kroppen och ger samma svar som `GET` på kunden. Om Party inte känner numret,
  eller om kunden saknas, blir det 404. Numret finns aldrig i svar, loggrader eller Problem-detail.
- Engagemang från alla namespaces visas. `engagementNamespace` och `objectType` filtrerar. Förvald sortering är
  `sourceModified,desc`, och paginering med `size > 100` begränsas till 100.
- Ett engagemang som tillhör en annan kund ger 404.
- Frågorna använder indexen i [§4.3]. Det verifieras med `EXPLAIN` i ett repository-test för engagemangslistan och
  kontaktfrågan.

## C8 — Noteringar

**Bygg:**

- `NoteResource`, `NoteService` och `note_history`.
- Att en notering skapar kunden enligt [§7.2].

**Acceptans:**

- CRUD inom namespacet. En notering i ett annat namespace ger 404 vid läsning, ändring och radering.
- `CREATED` och `UPDATED` skrivs till historiken med `performed_by` från `X-Sent-By`. Ett anrop utan `X-Sent-By` ger 400.
- En notering på en okänd kund skapar kunden:
  - en `PRIVATE`-kund med skyddsstatus från Citizen, och med namn och adress bara om personen är oskyddad
  - en `ENTERPRISE`-kund med namn och adress från LegalEntity
  - utan namn och adress när Citizen inte har någon post
  - med `UNKNOWN`, utan namn och adress, när Citizen inte svarar. Noteringen skapas ändå.

  Kunden loggas som `CUSTOMER_CREATED` med kanalen `REST`.

- Okänd part ger 404, och ingen kund skapas.

- Om ett meddelande skapar kunden samtidigt blir det en kund och inget fel.

- När den sista noteringen raderas och kunden saknar engagemang raderas kunden, och `CUSTOMER_DELETED` loggas med
  kanalen `REST` ([§3.6]).

## C9 — Kundradering via REST och API för teknisk logg

**Bygg:**

- `DELETE /{municipalityId}/customers/{partyId}`.
- `TechnicalLogResource`: `GET` per kund och `DELETE` med filtren `partyId` och `createdBefore` ([§8.2]).

**Acceptans:**

- `DELETE` på kund raderar kunden med allt den äger och skriver `CUSTOMER_DELETED` med kanalen `REST` och
  `performed_by`. En okänd kund ger 404.
- Den tekniska loggen kan läsas efter raderingen, sorteras förvalt på `created,desc` och pagineras.
- `DELETE` på den tekniska loggen utan något filter ger 400. Med `partyId`, med `createdBefore` och med båda raderas
  exakt de rader som matchar.
- Raderingen görs i batchar med `LIMIT`, med en transaktion per batch. Det testas med fler rader än en batch.

## C10 — Skyddad identitet: nattlig kontroll

**Bygg:**

- `ProtectionRefreshScheduler` enligt [§5.4]: urval på `registry_checked`, Citizen batch, en transaktion per batch,
  omräkning av skyddsnivån, `PROTECTION_CHANGED` och omindexering av ändrade kunder.

**Acceptans:**

- Bara `PRIVATE`-kunder vars `registry_checked` saknas eller är äldre än `refresh-age` tas med. Företagskunder tas
  aldrig med.
- En kund med `UNKNOWN` får folkbokföringens nivå när Citizen svarar, och namn och adress om personen är oskyddad.
  Svarar Citizen inte förblir kunden `UNKNOWN` och tas med nästa natt.
- När en kund blir skyddad töms namn, adress och egna kontaktuppgifter, och kunden indexeras om utan dem.
- När skyddet upphör fylls namn och adress i från Citizens svar.
- En batch där Citizen svarar 5xx lämnar kundernas `registry_checked` orört, och övriga batchar körs ändå.
- ShedLock förhindrar att två körningar pågår samtidigt.
- Ett test med fler kunder än `batch-size` visar att alla behandlas.
- En kund som kontrollerades förra nattens körning tas med i nästa.
- Batchens kunder låses i partyId-ordning, och transaktionen körs med READ COMMITTED.

## C12 — Sökindex: Hibernate Search, kö och relay

**Bygg:** enligt [§6.2].

- Beroenden och konfiguration.
- `SearchAnalysisConfigurer`, `CustomerDocumentBinder` och `@Indexed` på `CustomerEntity`.
- `SearchSchemaInitializer`, `SearchAvailability` och `OpenSearchHealthIndicator`.
- `SearchIndexRelay` med schemaläggning.
- `POST /search/reindex`.
- Testcontainern för OpenSearch.

**Acceptans:**

- **Spike först:** verifiera att en explicit indexeringsplan med `writeSync` kastar ett fel när OpenSearch avvisar
  dokumentet. Resultatet dokumenteras i README.
- Hibernate Searchs automatiska lyssnare är avstängda (`indexing.listeners.enabled: false`).
- En skyddad kunds dokument saknar namn, adress, kontakt- och telefonfält.
- Kontakter hamnar i `contacts_ns_<namespace>` respektive `contacts_global`.
- Telefonnummer i formaten `+46 70-123 45 67`, `0046701234567` och `070-1234567` ger samma värde.
- En skyddad kunds dokument innehåller bara `references` av de sökbara fälten.
- Engagemangens referens, titel och kategori indexeras. Noteringar hamnar i `notes_ns_<namespace>`.
- Relayn:
  - körs direkt efter en commit som har markerat kunder, och hoppar över körningen om den redan kör
  - indexerar kunder som finns och tar bort kunder som saknas
  - låter en markering som kom in under indexeringen ligga kvar
  - ökar `attempts` på en rad som misslyckas ensam
- Att skapa, ändra eller radera en notering markerar kunden.
- `OpenSearchHealthIndicator` blir `DOWN` när någon rad har `attempts ≥ 5` och ingår inte i `readiness`.
- Reindexeringen svarar 202, och 409 om låset redan är taget. Den pausar relayn medan den pågår.
- Sökningen är avstängd som standard. Tjänsten startar och fungerar utan OpenSearch.
- Reindexeringen tar bort köns rader som lades till före starten, även de med `attempts ≥ 5`, och
  `OpenSearchHealthIndicator` blir `UP` igen.

## C13 — Sök-endpoint

**Bygg:**

- `CustomerSearchResource` och `CustomerSearchService` enligt [§6.2].

**Acceptans:**

- Sökningen träffar på:

  - namn och adress
  - kontaktuppgifter och kontaktpersoner i eget namespace och globalt
  - engagemangens referens, titel och kategori
  - noteringar i eget namespace

  Den träffar aldrig på kontaktuppgifter eller noteringar i andra namespaces.

- En skyddad kund hittas på ett ärendenummer, men inte på titel eller notering.

- Ett telefonnummer hittas oavsett format.

- En kund som inte matchar frågan finns inte bland träffarna, även när filtret på kommun och partytyp matchar.

- En skyddad kund hittas inte på namn, adress, kontaktuppgifter, titel eller notering. En skyddad kund vars dokument är
  inaktuellt släpps ur träfflistan.

- Följande ger 400:

  - en `query` som är tom eller längre än 255 tecken
  - en sida bortom 10 000 träffar
  - ett okänt `sort`
- `size` över 100 begränsas till 100, som för andra listor.
- Timeout ger 504, och avstängd eller otillgänglig sökning ger 503.
- Frågan tas emot i kroppen och finns aldrig i URL:en eller i loggrader.

## T2 — Egen OpenSearch-container per miljö

**Bygg:**

- En OpenSearch-container för party-overview i test och produktion, uppsatt på samma sätt som SupportManagements,
  tills ett riktigt kluster finns ([beslut 38]).
- Inloggningen som sealed secrets.

**Acceptans:**

- party-overview når containern i varje miljö.
- Bara party-overview och drift har åtkomst.
- Att containern startas om utan sitt index är hanterat: `POST /search/reindex` bygger upp det igen ([§6.2]).

## T1 — Köer och exchanges i rabbitmq-topology

**Bygg:**

- Publicerarnas exchanges och party-overviews köer enligt [§3.2], med DLQ, `x-delivery-limit: 5`,
  `x-dead-letter-strategy: at-least-once`, `x-overflow: reject-publish` och `x-single-active-consumer: true`.
- AMQP-användare: publicerarna får write på sin exchange, och party-overview får read på sina köer. Ingen får
  configure.

**Acceptans:**

- Köerna skapas med alla argument från början.
- Dead letter-köerna har ingen TTL. Bara drift och utvecklare har åtkomst ([beslut 31]).
- party-overviews AMQP-användare kan göra en passiv deklaration av sina dead letter-köer ([§12.1]).

## C11 — RabbitMQ-lyssnare och `MessageIT`

**Bygg:**

- `MessageListener` för alla köer i `integration.sources`, med källan från `CONSUMER_QUEUE`.
- `MessageIT` enligt [§10].

**Acceptans:**

- Ett meddelande per väg (alla objekttyper och operationer) går hela vägen från kön till databasen.
- Det finns ingen `@ConditionalOnProperty` och ingen `@EnableRabbit`.
- Tjänsten startar inte om `integration.sources` är tom eller om en källa saknar kö. `SourceProperties` är `@Validated`.
- `DeadLetterHealthIndicator` enligt [§12.1]: `DOWN` med kö och antal i detaljerna när en dead letter-kö har
  meddelanden, `UP` annars. Den ingår inte i `liveness` eller `readiness`.
- README beskriver hur dead letters hanteras.
- Med `dept44.rabbitmq.enabled=false` finns ingen `DeadLetterHealthIndicator`, och tjänsten startar.

## SM1 — SupportManagement: outbox, relay och publicering

**Bygg:** enligt [§11.1] och [§11.2].

**Acceptans:**

- Ett namespace utan opt-in publicerar inget.
- Ett API-anrop som ger flera events blir ett meddelande med alla aktiviteter.
- Soft delete publiceras som `DELETE`, och restore som `UPDATE` med högre version.
- En ändring som bara rör stakeholders ökar versionen.
- `parameters` finns aldrig i meddelandet.
- Kanaltyperna mappas enligt [§11.2], och kanaler med en annan typ skickas inte. Mappningen täcker de typvärden som finns
  i SM:s `contact_channel` i produktion.
- Bara `externalIdType` `PRIVATE` och `ENTERPRISE` skickas som partytyp. Andra stakeholders skickas utan id.
- Ett nytt ärende, som saknar `modified`, publiceras med `sourceModified = created`.
- Relayn låser objektraden innan den läser raderna och tillståndet ([§11.1]). En ändring som committas medan relayn
  publicerar hamnar i nästa meddelande, med sin egen version.
- En ändring som rullas tillbaka publiceras inte. En publicering som misslyckas ligger kvar i outboxen och skickas vid
  nästa körning.

## SM2 — SupportManagement: grundladdning

**Bygg:**

- En `JobType` som går igenom ärendena per namespace med keyset och ShedLock, enligt [§11.2].

**Acceptans:**

- Alla ärenden i namespaces med opt-in publiceras utan aktiviteter.
- Två samtidiga starter ger en körning.
- Pågående ändringar publiceras i rätt ordning per ärende.

## CD1 — Casedata: outbox, relay och publicering

**Bygg:** enligt [§11.1] och [§11.5], inklusive uppgraderingen till den dept44-version där dept44-starter-rabbitmq är släppt.

**Acceptans:**

- Ett namespace utan opt-in publicerar inget.
- Varje publiceringspunkt i [§11.5] ger en outbox-rad. Schedulers, noteringar och beslut ger ingen.
- En PATCH av en enskild part ökar ärendets version och `updated`. Det verifieras med ett IT.
- Ett API-anrop blir ett meddelande med alla aktiviteter.
- En radering publiceras som `DELETE` med versionen från outbox-raden.
- `confidential = true` publiceras som `DELETE`. När flaggan tas bort publiceras `UPDATE` med högre version.
- Meddelandet innehåller aldrig `personId` som inte är ett UUID, `extraParameters`, `authorizedSignatory` eller
  handläggare. `organizationNumber` skickas bara som `legalId` när partyId saknas.
- `CELLPHONE` och `PHONE` skickas som kanaltypen `PHONE`, och `EMAIL` som `EMAIL`.
- Titeln, `display_name` följt av `caseTitleAddition`, ryms inom 512 tecken ([§3.1]).
- Relayn låser objektraden innan den läser raderna och tillståndet ([§11.1]).
- `ProcessService.updateProcess` anropas i exakt samma fall som tidigare.
- En ändring som rullas tillbaka, t.ex. en misslyckad processtart, publiceras inte. En publicering som misslyckas ligger
  kvar i outboxen och skickas vid nästa körning.

## CD2 — Casedata: grundladdning

**Bygg:**

- En admin-endpoint och ett asynkront jobb med keyset och ShedLock, enligt [§11.5].

**Acceptans:**

- Endpointen returnerar utan att vänta på brokern.
- Alla ärenden i namespaces med opt-in publiceras utan aktiviteter.
- Två samtidiga starter ger en körning.
- Pågående ändringar publiceras i rätt ordning per ärende.

## PA1 — party-assets: outbox, relay och publicering

**Bygg:** enligt [§11.1] och [§11.3], med grenen `publish-asset-events` som grund.

**Acceptans:**

- Alla ändringspunkter i [§11.3] ger en outbox-rad, även PR3-importen och originalet som blir `REPLACED`.
- Versionen är `AssetEntity.version`.
- `performedBy` sätts även vid delete.
- Händelsen skickas av servicemetoderna och `PR3Importer`, inte av `AssetEntityListener`. Ett IT visar att
  `createAsset`, `deleteAsset` och PR3-importen ger en outbox-rad.
- En ny asset publiceras med `sourceModified = created`.
- Relayn låser objektraden innan den läser raderna och tillståndet ([§11.1]).

## PA2 — party-assets: grundladdning via outbox

**Bygg:**

- republish skriver outbox-rader i batchar, enligt [§11.3].

**Acceptans:**

- republish returnerar utan att vänta på brokern.
- Alla assets i kommunen publiceras utan aktiviteter.

## CT1 — contract: tidsstämplar och vem

**Bygg:**

- Kolumnerna `created` och `modified` och fångsten av `Identifier`, enligt [§11.4].

**Acceptans:**

- Migreringen är additiv och bakåtkompatibel vid rullande deploy.
- Befintliga rader får värden.
- `modified` sätts när kontraktet skapas och ändras vid varje skrivning.

## CT2 — contract: outbox, relay och publicering

**Bygg:** enligt [§11.1] och [§11.4].

**Acceptans:**

- Alla publiceringspunkter i [§11.4] ger en outbox-rad, även en vanlig periodförlängning.
- Billing-outboxen påverkas inte.
- `organizationNumber` skickas bara som `legalId` när partyId saknas.
- `phoneNumber` och `emailAddress` skickas som kanaler av typen `PHONE` respektive `EMAIL`, och bara när de har ett
  värde.
- `type` mappas till partytyp enligt [§11.4]. En stakeholder med `OTHER` eller utan typ skickas utan id.
- Relayn låser objektraden innan den läser raderna och tillståndet ([§11.1]).

## CT3 — contract: grundladdning

**Bygg:**

- En sidvis genomgång utan bilagor som skriver outbox-rader.

**Acceptans:**

- Alla kontrakt i kommunen publiceras utan aktiviteter.
- Inga bilagor läses.

[§3]: 03-meddelanden.md
[§3.1]: 03-meddelanden.md#31-krav-på-det-gemensamma-formatet
[§3.2]: 03-meddelanden.md#32-topologi
[§3.3]: 03-meddelanden.md#33-flödet-genom-tjänsten
[§3.4]: 03-meddelanden.md#34-versionsmatrisen
[§3.5]: 03-meddelanden.md#35-tillämpning
[§3.6]: 03-meddelanden.md#36-kundradering
[§4.3]: 04-datamodell.md#43-tabellerna
[§5]: 05-synlighet-och-kunddata.md
[§5.1]: 05-synlighet-och-kunddata.md#51-synlighet-per-namespace
[§5.2]: 05-synlighet-och-kunddata.md#52-namn-och-adress
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§6]: 06-rest-api.md
[§6.1]: 06-rest-api.md#61-endpoints
[§6.2]: 06-rest-api.md#62-kundsökning-opensearch
[§7.1]: 07-integrationer.md#71-party-api-service-party
[§7.2]: 07-integrationer.md#72-citizen-och-legalentity
[§8]: 08-teknisk-logg.md
[§8.1]: 08-teknisk-logg.md#81-utfall-i-den-tekniska-loggen
[§8.2]: 08-teknisk-logg.md#82-radering-av-den-tekniska-loggen
[§10]: 10-teststrategi.md
[§11.1]: 11-publicerare.md#111-gemensamt-för-alla-publicerare
[§11.2]: 11-publicerare.md#112-supportmanagement-errand
[§11.3]: 11-publicerare.md#113-party-assets-asset
[§11.4]: 11-publicerare.md#114-contract-contract
[§11.5]: 11-publicerare.md#115-casedata-errand-källan-case_data
[§12]: 12-drift.md
[§12.1]: 12-drift.md#121-larm-för-dead-letter-köer
[beslut 31]: 00-beslutslogg.md#beslut-31-dead-letter-köer-utan-ttl-med-larm
[beslut 38]: 00-beslutslogg.md#beslut-38-egen-opensearch-container-per-miljö
[beslut 41]: 00-beslutslogg.md#beslut-41-tolkningen-av-citizens-skyddsflaggor

