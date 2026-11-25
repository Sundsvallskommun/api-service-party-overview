# 14. Uppgifter att bygga

Del av [arkitekturen för party-overview](README.md).

Varje uppgift går att slå ihop för sig. Acceptanskriterierna kan klistras in i en Jira-subtask. **C** är
party-overview, **T** är infrastrukturen (RabbitMQ och OpenSearch) och **SM**, **PA**, **CT** och **CD** är
publicerarna. Formatet (**F1**) ägs av beställaren och definieras senare. Ändringskön för företag och personer
(**F2**) ägs av sin förvaltare, och det som återstår att ta reda på står i [§13.1].

**Gäller alla uppgifter (Definition of Done):**

- För uppgifter med kod: `mvn clean verify` är grönt och JaCoCo-gränsen uppfylld.
- `openapi.yaml` är regenererad och WireMock-stubbarna uppdaterade när API:t ändras.
- Javadoc och kommentarer beskriver vad koden gör.
- Inga TODO:s.
- README är uppdaterad när konfiguration eller drift påverkas.

| Steg | Id  |                       Uppgift                        |            Beror på            |
|------|-----|------------------------------------------------------|--------------------------------|
| 1    | C1  | Projektgrund                                         | —                              |
| 2    | C2  | Datamodell, Flyway och schemaverifiering             | C1                             |
| 3    | C3  | Integrationer mot Citizen och LegalEntity            | C1, behörighet i Metakatalogen |
| 4    | C4  | Kontaktpersonsroller via REST                        | C2                             |
| 5    | C5  | Meddelandekärnan                                     | C2, C3, C4, F1                 |
| 6    | C6  | Kundmeddelanden (`CUSTOMER`)                         | C5                             |
| 7    | C7  | Läs-API för kund, kontakter, engagemang och historik | C2, C3                         |
| 8    | C8  | Noteringar                                           | C3, C5                         |
| 9    | C9  | Kundradering via REST och API för teknisk logg       | C5                             |
| 10   | C10 | Skyddad identitet: nattlig kontroll                  | C3, C5                         |
| 11   | T2  | Egen OpenSearch-container per miljö                  | —                              |
| 12   | C12 | Sökindex: Hibernate Search, kö och relay             | C2, T2                         |
| 13   | C13 | Sök-endpoint                                         | C5, C12                        |
| 14   | T1  | Köer och exchanges i rabbitmq-topology               | —                              |
| 15   | C11 | RabbitMQ-lyssnare och `MessageIT`                    | C5, C6, T1                     |
| 16   | SM1 | SupportManagement: outbox, relay och publicering     | F1                             |
| 17   | SM2 | SupportManagement: grundladdning                     | SM1                            |
| 18   | CD1 | Casedata: outbox, relay och publicering              | F1                             |
| 19   | CD2 | Casedata: grundladdning                              | CD1                            |
| 20   | PA1 | party-assets: outbox, relay och publicering          | F1                             |
| 21   | PA2 | party-assets: grundladdning via outbox               | PA1                            |
| 22   | CT1 | contract: tidsstämplar                               | —                              |
| 23   | CT2 | contract: outbox, relay och publicering              | CT1, F1                        |
| 24   | CT3 | contract: grundladdning                              | CT2                            |
| 25   | T3  | Ändringskön i rabbitmq-topology                      | F2                             |
| 26   | C14 | Företagsprofilen från ändringskön                    | C3, C5, C11, T3, F2            |

## C1 — Projektgrund

**Bygg:**

- Ta bort POC-koden (`integration/source`, `EventService`, `PartyOverviewService`, POC-entiteter och tester,
  `PartyOverviewIT`).
- I `pom.xml`: uppgradera `dept44-service-parent` till 8.2.0, byt `spring-boot-starter-amqp` mot
  `dept44-starter-rabbitmq`, och lägg till `dept44-starter-feign` och `dept44-starter-scheduler`.
  `dept44-common-validators` finns redan.
- Lägg in `application.yml` och profilerna enligt [§12], och `SourceProperties` (`integration.sources`, `@Validated`),
  som C4 och C11 använder.
- `SentBy`, som hämtar anroparen ur dept44:s `Identifier` och ger 400 enligt [§6] när `X-Sent-By` saknas, inte går att
  tolka eller har ett värde över 255 tecken. C4, C8, C9 och C12 använder den.
- README skrivs om efter detta dokument.

**Acceptans:**

- Tjänsten startar med `dept44.rabbitmq.enabled=false` utan någon anslutning till RabbitMQ.
- Tjänsten startar inte om `integration.sources` är tom eller om en källa saknar kö.
- `management.health.rabbit.enabled` är borttagen ur `application.yml`.
- dept44:s PII-maskning är påslagen med alla kategorier.
- Ingen POC-kod finns kvar, och `openapi.yaml` är regenererad utan POC:ens endpoint.
- `SentBy` ger värdet för `joe01doe; type=adAccount` och ger 400 för en saknad header, för `joe01doe` utan `type=` och
  för ett värde på 256 tecken. Enhetstest.

## C2 — Datamodell, Flyway och schemaverifiering

**Bygg:**

- En ny `V1_0__create_tables.sql` med alla tabeller i [§4.3], inklusive `shedlock`.
- Entiteter, `AbstractHistoryEntity`, adressens `@Embeddable`, enums och `RolesConverter`.
- Repositories med grundfrågorna: IODKU och låsning av `source_object` och `customer`, och markeringen i
  `search_index_queue` ([§6.2]). Frågorna för ett visst flöde läggs till i uppgiften som bygger flödet.
- Valet av modell för `contact_channel` ([§4.2]). Blir den en entitet uppdateras tabellen i [§4.3].
- `schema.sql`, `truncate.sql`, `CascadeIT` och `SchemaParityIT`.

**Acceptans:**

- Namn, kolumner, index och FK stämmer exakt med [§4.3]. Varje FK under `customer` har `on delete cascade`, både i
  Flyway och i `schema.sql`.
- `SchemaVerificationTest` och `SchemaParityIT` är gröna. Om ett index tas bort i bara den ena källan blir
  `SchemaParityIT` röd. Det ska provas manuellt en gång.
- `CascadeIT`: `DELETE FROM customer WHERE id = ?` lämnar noll rader i `customer_address`, `engagement`,
  `engagement_history`, `contact`, `contact_channel`, `note`, `note_history`, `asserted_protection`,
  `enterprise_profile`, `enterprise_sni_code` och `enterprise_proceeding` för kunden. Raderna i `technical_log`,
  `source_object` och `search_index_queue` finns kvar, och likaså kundens kontaktrader hos andra kunder
  (`contact.party_id`).
- `created` och `modified` sätts av `@PrePersist`/`@PreUpdate`, trunkeras till millisekunder och testas per entitet.
- Repository-tester: IODKU för `source_object` och `customer` är idempotent. Samtidighetstestet med två trådar som
  skapar samma kund ger en rad och inget undantag.

## C3 — Integrationer mot Citizen och LegalEntity

**Bygg:**

- Kodgenereringen för `citizen.yaml` och `legalentity.yaml` med openapi-generator ([§9]).
- `CitizenClient`/`CitizenIntegration` med:
  - `getPartyIds` (batch, `POST /{municipalityId}/guid/batch`) för personnummer → partyId ([§7.1])
  - `getCitizen` och `getCitizens` (batch), som båda returnerar skyddsnivå och, för oskyddade personer, namn och
    folkbokföringsadress ([§5.4], [§7.2])
- `LegalEntityClient`/`LegalEntityIntegration` med:
  - `getPartyId` (`GET /{municipalityId}/{organizationNumber}/guid`) för organisationsnummer → partyId ([§7.1])
  - `getLegalEntity` (`GET /{municipalityId}/{legalEntityId}`), som returnerar namn, postadress, besöksadress och
    organisationsnummer, och företagsprofilen, där `EnterpriseProfileMapper` räknar fram SNI-koderna ([§5.5], [§7.2])
- Partytypen för ett partyId avgörs med `getCitizen` och `getLegalEntity` ([§7.2]). En enskild firma känns igen på att
  organisationsnumret är ett personnummer ([§5.4]). Numret sparas aldrig.
- legalId valideras mot formatet i [§3.1], per partytyp och med kontrollsiffran, innan det skickas ([§7.1]).
- Ett eget undantag utan URL för IO-fel ([§7.1]).
- `logbook.excluded.paths` för LegalEntitys uppslag med numret i URL:en ([§12]).
- Konfiguration med OAuth2 och circuit breaker.
- WireMock-stubbar för IT.

**Acceptans:**

- 404 på ett enskilt anrop ger `Optional.empty`, och 5xx ger `ServerProblem` som inte sväljs. 400 från LegalEntitys
  uppslag ger också `Optional.empty`. Ett element utan `success` i Citizens batchsvar ger ingen mappning, och ett
  legalId med fel format eller fel kontrollsiffra skickas inte.
- Ett `ENTERPRISE`-nummer slås upp mot LegalEntity och aldrig mot Citizen, också en enskild firmas nummer med 10 eller
  12 siffror. Ett `PRIVATE`-nummer slås upp mot Citizen.
- Ett tiosiffrigt personnummer får århundradet enligt [§5.4]. Det testas med gränsfallen: dagens datum ger innevarande
  sekel, och morgondagens datum ger förra seklet.
- En timeout mot LegalEntitys uppslag ger ett undantag vars meddelande och stacktrace saknar organisationsnumret, och
  startern klassar det som ett avbrott (`TransientFailures.isOutage`). Testet stubbar en fördröjning som är längre än
  read timeout.
- 429 och 408 görs om till ett avbrott när partyId slås upp. När skyddsstatus hämtas ger de samma resultat som när
  Citizen inte svarar ([§3.7]).
- Inga legalId i några loggrader eller undantagsmeddelanden. Testet stubbar ett fel vars detail innehåller ett legalId
  och kontrollerar att det inte syns i undantaget.
- LegalEntitys uppslag med organisationsnumret i URL:en finns inte i loggern `se.sundsvall.dept44.payload`, och inget
  `PRIVATE`-personnummer skickas i en URL. Det verifieras med ett test. En enskild firmas nummer står i LegalEntitys URL
  ([beslut 32]).
- Partytypen: en person som Citizen känner ger `PRIVATE`, ett företag som bara LegalEntity känner ger `ENTERPRISE`,
  ett partyId som ingen av dem känner ger `Optional.empty`, och 5xx från någon av dem ger ett fel och aldrig
  `Optional.empty`.
- Utan behörighet i Metakatalogen svarar Citizen 401 och inte ett filtrerat svar. Det verifieras i testmiljön, eftersom
  [§5.4] bygger på det.
- I testmiljön verifieras också:
  - att `guid/batch` ger `personId` även för personer med skyddade personuppgifter
  - att Citizen svarar 5xx på hela `guid/batch`-anropet, och inte per element, när felet är tillfälligt ([§7.1])
  - vilken version av LegalEntity som har `…/{organizationNumber}/guid`
  - i vilket format LegalEntity ger en enskild firmas organisationsnummer, och vilket format `…/guid` tar emot för det
  - vilka värden Citizen har i `addressType`, och att folkbokföringsadressen har `POPULATION_REGISTRATION_ADDRESS`

  Resultaten dokumenteras i README.

- Citizen anropas med `ShowClassified=true` i alla anrop som ger personuppgifter. Flaggorna tolkas enligt tabellen i
  [§5.4], med ett testfall per rad. För en skyddad person returnerar integrationen varken namn eller adress.

- Citizens folkbokföringsadress blir en adress av typen `POPULATION_REGISTRATION_ADDRESS`, och andra adresser i svaret
  tas inte med. LegalEntitys `postAddress` och `address` blir `POSTAL_ADDRESS` och `VISITING_ADDRESS` enligt [§7.2].
  En adress där alla fält är tomma tas inte med.

- Företagsprofilen mappas enligt [§5.5]. Testet använder svaret för SCA Forest Products (organisationsnummer
  5563793586) som stub och kontrollerar:

  - SNI-koderna 70100, 72190, 86212 och 91012, i den ordningen, utan de tomma elementen
  - storleksklassen `8` "200–499 anställda", juridisk form `49` och sätets kommun `2281`
  - besöksadressen "SKEPPARPLATSEN 1" och en postadress med bara postnummer och ort
  - att tomma strängar blir null
- `officers` och `fullSignatureDescription` finns inte i integrationens modell, och ingenting ur LegalEntitys svar
  loggas. Ett organisationsnummer som är ett personnummer ger ingen `organizationNumber` i profilen.
- Tolkningen av `classified` och `protectedNR` ([beslut 41]) verifieras i testmiljön mot en sekretessmarkerad testperson
  och en testperson med skyddad folkbokföring. Resultatet dokumenteras i README.
- 401 från Citizen ger en `ClientProblem` vars detail säger att behörigheten i Metakatalogen saknas.
- Ingenting ur Citizens svar loggas.

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

- Kodgenereringen för AsyncAPI-dokumentet `party-overview-messages.yaml` med ZenWave SDK ([§9]).
- `ObjectChange` och mappningen från den genererade meddelandemodellen.
- `TechnicalLogService`, som markerar kunden i `search_index_queue` för varje rad utom `IGNORED_STALE` ([§6.2]).
- `MessageService` utan transaktion och `MessageWriter` med `@Transactional`, enligt [§3.3]–[§3.6] för `ERRAND`, `ASSET`
  och `CONTRACT`.
- `EnterpriseProfileService`, som skriver och raderar företagsprofilen under kundens lås ([§5.5]).
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
- En kontaktperson får partens första postadress, annars den första besöksadressen och annars den första
  fakturaadressen ([§5.3]). En part utan adress ger en kontaktrad utan adress, och kundens egen kontaktrad
  (`contact_type = CUSTOMER`) har aldrig någon adress.
- En kontaktpersons legalId mappas till partyId, och partyId sparas på kontaktraden. En kontaktperson som är skyddad,
  som kund eller enligt Citizen, eller vars status är `UNKNOWN`, sparas inte ([§5.3]). En kontaktperson med
  `ENTERPRISE` kontrolleras som en företagskund och frågas inte i Citizens batch.
- En kontaktperson som finns som kund låses med de andra kunderna, och dess nivå läses efter låset. Samtidighetstest:
  kontaktpersonen blir skyddad i en annan transaktion medan ett meddelande om företaget tillämpas. Efter båda finns
  ingen kontaktrad för personen, oavsett vilken transaktion som får låset först ([§3.3]).
- När en kund skapas med, eller får, en skyddsnivå som inte är `NONE` raderas personens kontaktrader hos företag, och
  varje företag loggas som `CONTACT_REMOVED` och markeras för omindexering. En automatisk radering av kunden lämnar
  kontaktraderna kvar ([§5.3]).
- En ny `ENTERPRISE`-kund vars organisationsnummer är ett personnummer, med 10 eller 12 siffror, får ägarens partyId
  som `registry_party_id` och ägarens skydd från Citizen. Firman och ägaren blir två olika kunder. Ett vanligt
  organisationsnummer ger `registry_party_id = null` ([§5.4]).
- Svarar LegalEntity eller Citizen inte när en ny företagskunds organisationsnummer eller ägare hämtas, får kunden
  `UNKNOWN` utan `registry_party_id`, och meddelandet tillämpas utan omförsök ([§3.7]).
- En ny, oskyddad företagskund får sin profil från LegalEntity, också när organisationsnumret finns i meddelandet.
  Svarar LegalEntity inte skapas kunden utan profil, och organisationsnumret från meddelandet avgör ändå om det är en
  enskild firma. En skyddad företagskund får ingen profil, och en kund som blir skyddad förlorar sin ([§5.5]).
- En profil vars `fetched` är äldre än den sparade skriver inte över den. Det testas med två hämtningar som skrivs i
  omvänd ordning.
- En part med legalId mappas och tillämpas. Finns legalId inte hos Citizen eller LegalEntity, eller har det fel format,
  hoppas parten över medan övriga parter tillämpas, och engagemang raderas ändå för kunder som inte längre är parter
  ([beslut 16]). legalId finns inte i någon entitet.
- En kund som förekommer två gånger blir en rad, och rollerna slås ihop.
- Ett nyare `sourceModified` ersätter namn och adresser som helhet, men ett äldre gör det inte. Ett nyare meddelande
  med färre adresser tar bort de övriga, och ett med namn men utan adresser tömmer adresserna ([§5.2]).
- Identiska adresser i ett meddelande sparas en gång, och en adress där alla fält utom typen är tomma sparas inte.
- En ny privatkund får sin skyddsstatus från Citizen innan den skapas. Om Citizen inte svarar, svarar 5xx, 429 eller
  408, har en öppen circuit breaker eller saknar en post för personen, skapas kunden med `UNKNOWN`, utan namn, adresser
  och egna kontaktuppgifter, och meddelandet tillämpas utan omförsök. 401 från Citizen kastas som `ClientProblem`
  ([§5.4]).
- `UNKNOWN` från folkbokföringen ger den effektiva nivån `UNKNOWN`, men en källas `SECRECY_MARKING` eller
  `PROTECTED_REGISTRATION` går före.
- README och javadoc för `ProtectionLevel` beskriver alla fyra värden. För `UNKNOWN` står när värdet uppstår, att
  kunden hanteras som skyddad och att den nattliga kontrollen ersätter det ([§5.4]).
- En part med `protection` ger `engagement.party_protection`, och kunden räknas som skyddad. Namn, adresser och egna
  kontaktuppgifter sparas inte, och befintliga töms. Skyddet upphör när inget engagemang längre anger det och
  folkbokföringen inte gör det heller.
- En ändrad skyddsnivå loggas som `PROTECTION_CHANGED`.
- Varje kund som får en rad i den tekniska loggen, utom `IGNORED_STALE`, markeras i `search_index_queue` i samma
  transaktion, och markeringarna skrivs i `customer_id`-ordning ([§6.2]).
- Varje loggrad från ett meddelande har meddelandets `message-id` och request-id, och `PROTECTION_CHANGED` har den nya
  nivån ([§4.3]).
- Ett ogiltigt meddelande ger `ConstraintViolationException`, som startern dead-letterar (verifieras i C11). Ett test
  per obligatoriskt fält i [§3.1].
- Feign-anropen görs utanför transaktionen: `MessageService` har ingen `@Transactional`, och `MessageWriter` anropar
  inga integrationer.
- Samtidighetstest: ett objekt raderas medan ett meddelande från en annan källa ger samma kund ett nytt engagemang.
  Kunden och det nya engagemanget finns kvar, oavsett vilken transaktion som får låset först ([§3.3]).
- Samtidighetstest: kunden raderas i en annan transaktion, med samma bulk-delete som den automatiska raderingen, medan
  ett meddelande om ett objekt som kunden har lämnat tillämpas. Kunden skapas inte på nytt ([§3.3]).
- En `DELETE` där den första av två kunder raderas automatiskt markerar ändå `source_object` som raderat, och den andra
  kundens skyddsnivå räknas om ([§3.6]).
- En part utan id behöver ingen `partyType`. En part med id men utan `partyType`, ett textfält över maxlängden, en
  adress utan typ och en part med fler än 10 adresser ger valideringsfel ([§3.1]).

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
  skapats på nytt av ett engagemang ([§3.4]). Ett `DELETE` utan part ger valideringsfel. Kundens rader i
  `source_object` raderas med den.
- `DELETE` sparar aldrig någon tombstone. Efter ett `DELETE`, också ett som levereras igen, finns ingen rad i
  `source_object` för `CUSTOMER` med partyId:t, även när kunden saknades.
- `DELETE` raderar personens kontaktrader hos företag och loggar `CONTACT_REMOVED` för företagen ([§5.3]).
- En kund som har fått ett `CUSTOMER UPDATE` raderas inte automatiskt när dess sista engagemang försvinner, och dess
  kontaktuppgifter och skydd från källan finns kvar ([§3.6]).
- Ett `UPDATE` med `protection` för en kund som var oskyddad raderar personens kontaktrader hos företag ([§5.3]).
- Partens `protection` sparas som källans rad i `asserted_protection`, skyddsnivån räknas om, och en skyddad kund får
  varken namn, adresser eller kontaktuppgifter. `UPDATE` loggas som `CUSTOMER_UPDATED`.
- Ett `UPDATE` utan `protection` från källa B tar bort B:s rad men lämnar källa A:s skydd orört, och kunden förblir
  skyddad ([§4.3]).
- Kundens kontaktrader från meddelandet får meddelandets namespace, eller blir globala om det saknas.
- Parten i ett `CUSTOMER`-meddelande är alltid kunden och klassas inte på roller. Den behöver partyId eller ett legalId
  som går att mappa, annars ignoreras meddelandet.

## C7 — Läs-API för kund, kontakter, engagemang och historik

**Bygg:**

- `CustomerResource` (GET och lookup) och `EngagementResource` med service och API-modeller, enligt [§5.1] och [§6.1].

**Acceptans:**

- Kontakter visas för det egna namespacet och de globala, aldrig för andra namespaces. Dubbletter slås ihop, men två
  kontaktpersoner som bara skiljer sig i adress visas båda.
- Kunden returneras med alla sina adresser, i ordningen i [§5.2], och varje kontaktperson med sin adress.
- En företagskund returneras med `enterpriseProfile` och SNI-koderna i ordning. En privatkund, en skyddad kund och ett
  företag utan profil returneras utan den ([§6.1]).
- En skyddad kund, också en med `UNKNOWN`, returneras med `protectionLevel`, men utan namn, adresser och kontaktkanaler.
- `protectionLevel` i OpenAPI-specen beskriver alla fyra värden, och att `UNKNOWN` betyder okänd skyddsstatus som ska
  hanteras som skyddad ([§5.4]).
- Uppslaget tar `legalId` och `partyType` i kroppen och ger samma svar som `GET` på kunden. Om numret inte finns, eller
  om kunden saknas, blir det 404. Numret finns aldrig i svar, loggrader eller Problem-detail. En enskild firma slås upp
  med `ENTERPRISE` och ägarens personnummer, med 10 eller 12 siffror, och ger firman och inte ägaren ([§3.1]).
- Historiken för ett engagemang sorteras förvalt på `occurredAt,desc` och pagineras ([§6.1]).
- Engagemang från alla namespaces visas. `engagementNamespace` och `objectType` filtrerar. Förvald sortering är
  `sourceModified,desc`, och paginering med `size > 100` begränsas till 100.
- Ett engagemang som tillhör en annan kund ger 404.
- Frågorna använder indexen i [§4.3]. Det verifieras med `EXPLAIN` i ett repository-test för engagemangslistan och
  kontaktfrågan.

## C8 — Noteringar

**Bygg:**

- `NoteResource`, `NoteService` och `note_history`.
- Att en notering skapar kunden enligt [§7.2].
- Varje skrivning av en notering låser kunden först och körs med READ COMMITTED ([§3.6]). Omförsök vid deadlock.

**Acceptans:**

- CRUD inom namespacet. En notering i ett annat namespace ger 404 vid läsning, ändring och radering.
- `POST` sätter `created_by` till värdet i `X-Sent-By`, och `PATCH` sätter `modified_by` men ändrar aldrig
  `created_by`. Båda returneras som `createdBy` och `modifiedBy` ([§4.3]).
- `CREATED` och `UPDATED` skrivs till historiken med `performed_by` och `performed_by_type` från `X-Sent-By`.
- `POST`, `PATCH` och `DELETE` utan `X-Sent-By`, med en header utan `type=` och med ett värde över 255 tecken ger 400,
  och ingenting sparas ([§6]).
- En notering på en okänd kund skapar kunden:
  - en `PRIVATE`-kund när Citizen känner partyId:t, med skyddsstatus från Citizen, och med namn och folkbokföringsadress
    bara om personen är oskyddad
  - en `ENTERPRISE`-kund när bara LegalEntity känner det, med namn, postadress, besöksadress och företagsprofil från
    LegalEntity, och för en enskild firma med ägarens skydd från Citizen, utan namn, adresser och profil om ägaren är
    skyddad

  Kunden loggas som `CUSTOMER_CREATED` med kanalen `REST`.

- En part som varken Citizen eller LegalEntity känner ger 404, och ingen kund skapas. Svarar Citizen eller LegalEntity
  inte, eller med 5xx, blir svaret 503, och varken notering eller kund skapas. Ett tillfälligt fel ger aldrig 404.

- `subject` över 255 tecken och `body` över 10 000 tecken ger 400.

- Att skapa, ändra eller radera en notering markerar kunden i `search_index_queue`.

- Om ett meddelande skapar kunden samtidigt blir det en kund och inget fel. Om ett meddelande raderar kunden medan en
  notering skapas blir det inget FK-fel, eftersom kunden är låst.

- `PATCH` och `DELETE` på en notering vars kund har raderats ger 404 och skapar inte kunden ([§3.6]).

- När den sista noteringen raderas och kunden saknar engagemang och `CUSTOMER`-data raderas kunden, och
  `CUSTOMER_DELETED` loggas med kanalen `REST` ([§3.6]).

- En notering som skapar en kund med en skyddsnivå som inte är `NONE` raderar personens kontaktrader hos företag
  ([§5.3]).

- En skrivning som faller på deadlock görs om, och efter tre misslyckade försök kastas felet. Det testas med en
  `CannotAcquireLockException` som stubbas i första försöket ([§3.6]).

## C9 — Kundradering via REST och API för teknisk logg

**Bygg:**

- `DELETE /{municipalityId}/customers/{partyId}`.
- `TechnicalLogResource`: `GET` per kund och `DELETE` med filtren `partyId` och `createdBefore` ([§8.2]).

**Acceptans:**

- `DELETE` på kund raderar kunden med allt den äger, inklusive dess rader i `source_object` för `CUSTOMER`, och skriver
  `CUSTOMER_DELETED` med kanalen `REST` och `performed_by`.
- `DELETE` på kund raderar också personens kontaktrader hos företag i kommunen (`contact.party_id`), även när personen
  bara är kontaktperson, loggar `CONTACT_REMOVED` för företagen och markerar dem för omindexering. Kontaktrader i en
  annan kommun lämnas orörda. 404 blir det bara när varken kund eller kontaktrader finns.
- `DELETE` på kund låser kundens rader i `source_object` för `CUSTOMER` före kunden ([§3.6]). Samtidighetstest: ett
  `CUSTOMER UPDATE` för samma kund tillämpas samtidigt, och båda blir klara utan att någon av dem misslyckas.
- En radering som faller på deadlock görs om, som i C8.
- Båda `DELETE`-anropen kräver `X-Sent-By`, och ett anrop utan den ger 400.
- Den tekniska loggen kan läsas efter raderingen, sorteras förvalt på `created,desc` och pagineras.
- `DELETE` på den tekniska loggen utan något filter ger 400. Med `partyId`, med `createdBefore` och med båda raderas
  exakt de rader som matchar.
- Raderingen görs i batchar med `LIMIT`, med en transaktion per batch. Det testas med fler rader än en batch.

## C10 — Skyddad identitet: nattlig kontroll

**Bygg:**

- `ProtectionRefreshScheduler` enligt [§5.4]: urval på `registry_party_id`, `registry_protection` och
  `registry_checked`, keyset på `id`, uppslaget av ägaren för företag utan `registry_party_id`, Citizen batch, en
  transaktion per batch, omräkning av skyddsnivån, `PROTECTION_CHANGED`, radering av kontaktrader och markering av
  ändrade kunder för omindexering.

**Acceptans:**

- Alla kunder med `registry_party_id` eller `registry_protection = UNKNOWN` vars `registry_checked` saknas eller är
  äldre än körningens start tas med, även en kund som skapades kl. 10 dagen före. Ett företag med känd status som inte
  är en enskild firma tas aldrig med.
- Urvalet går i primärnyckelns ordning. `EXPLAIN` i ett repository-test visar en range-läsning på `PRIMARY` utan
  filesort.
- En batch som misslyckas väljs inte igen i samma körning.
- En kund med `UNKNOWN` får folkbokföringens nivå när Citizen svarar. En privatkund som är oskyddad får namn och
  folkbokföringsadress från svaret, men en enskild firma får aldrig ägarens namn eller adress. Svarar Citizen inte
  förblir kunden `UNKNOWN` och tas med nästa natt.
- Ett företag med `UNKNOWN` utan `registry_party_id` slås upp i LegalEntity igen. En enskild firma får ägaren som
  `registry_party_id` och ägarens nivå, och ett annat företag får `registry_protection = null`, får sin profil från
  samma svar och tas inte med igen.
- En oskyddad företagskund utan profil tas med och får sin profil, loggad som `PROFILE_UPDATED`. En enskild firma vars
  ägare inte längre är skyddad får sin profil natten efter ([§5.5]).
- När en företagskund blir skyddad raderas profilen med SNI-koder och förfaranden i samma transaktion.
- När en kund blir skyddad töms namn, adresser och egna kontaktuppgifter, och kunden markeras för omindexering.
  Personens kontaktrader hos företag raderas, och företagen loggas som `CONTACT_REMOVED` och markeras för
  omindexering ([§5.3]).
- Varje kund i batchen vars nivå inte är `NONE` får sina kontaktrader hos företag raderade, även när nivån inte
  ändrades.
- När skyddet upphör för en privatkund fylls namn och folkbokföringsadress i från Citizens svar.
- En batch som faller på deadlock görs om, som i C8.
- En batch där Citizen svarar 5xx lämnar kundernas `registry_checked` orört, och övriga batchar körs ändå.
- ShedLock förhindrar att två körningar pågår samtidigt.
- Ett test med fler kunder än `batch-size` visar att alla behandlas.
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
- En skyddad kunds dokument, också en kund med `UNKNOWN`, innehåller bara `references` av de sökbara fälten. Namn,
  adresser, kontakt- och telefonfält saknas.
- Kontakter hamnar i `contacts_ns_<namespace>` respektive `contacts_global`.
- Företagsprofilens SNI-namn och verksamhetsbeskrivning hamnar i `business`. En skyddad kund och en kund utan profil
  har inget `business`.
- Telefonnummer i formaten `+46 70-123 45 67`, `0046701234567` och `070-1234567` ger samma värde.
- Engagemangens referens, titel och kategori indexeras. Noteringar hamnar i `notes_ns_<namespace>`.
- Relayn:
  - körs direkt efter en commit som har markerat kunder, och hoppar över körningen om den redan kör
  - indexerar kunder som finns och tar bort kunder som saknas
  - låter en markering som kom in under indexeringen ligga kvar
  - ökar `attempts` bara på en rad vars dokument avvisas ensamt
  - avbryter körningen utan att ändra `attempts` när OpenSearch inte går att nå eller svarar med timeout, 5xx eller 429
  - ökar `attempts` på en rad vars dokument inte går att bygga, och fortsätter med de andra raderna
  - avslutar körningen efter `search.relay.max-run-time`
- `OpenSearchHealthIndicator` blir `DOWN` när någon rad har `attempts ≥ 5` och ingår inte i `readiness`.
- Reindexeringen svarar 202, 409 om låset redan är taget och 503 om sökningen är avstängd. Den pausar relayn medan den
  pågår, och låset förlängs med `KeepAliveLockProvider`.
- Reindexeringen tar bort köns rader som lades till före starten, även de med `attempts ≥ 5`, och
  `OpenSearchHealthIndicator` blir `UP` igen.
- När OpenSearch-containern har startats om utan sitt index bygger `POST /search/reindex` upp det igen.
- Sökningen är avstängd som standard. Tjänsten startar och fungerar utan OpenSearch, både med sökningen avstängd och med
  den påslagen när OpenSearch inte går att nå ([§6.2]).

## C13 — Sök-endpoint

**Bygg:**

- `CustomerSearchResource` och `CustomerSearchService` enligt [§6.2].

**Acceptans:**

- Sökningen träffar på:

  - namn och var och en av kundens adresser
  - kontaktuppgifter och kontaktpersoner i eget namespace och globalt
  - engagemangens referens, titel och kategori
  - noteringar i eget namespace
  - företagets bransch och verksamhetsbeskrivning, t.ex. "skogsbruk" för SCA Forest Products

  Den träffar aldrig på kontaktuppgifter eller noteringar i andra namespaces.

- En skyddad kund hittas på ett ärendenummer, men inte på namn, adresser, kontaktuppgifter, titel eller notering. En
  skyddad kund vars dokument är inaktuellt släpps ur träfflistan.

- Ett telefonnummer hittas oavsett format.

- En kund som inte matchar frågan finns inte bland träffarna, även när filtret på kommun och partytyp matchar.

- Följande ger 400:

  - en `query` som är tom eller längre än 255 tecken
  - en sida bortom 10 000 träffar
  - ett okänt `sort`
- `size` över 100 begränsas till 100, som för andra listor.
- Timeout ger 504, och avstängd eller otillgänglig sökning ger 503.
- Frågan tas emot i kroppen och finns aldrig i URL:en eller i loggrader.
- `CustomerSearchIT` enligt [§10], där ett meddelande som tillämpas via `MessageService` blir sökbart via relayn.

## T2 — Egen OpenSearch-container per miljö

**Bygg:**

- En OpenSearch-container för party-overview i test och produktion, uppsatt på samma sätt som SupportManagements,
  tills ett riktigt kluster finns ([beslut 38]).
- Inloggningen som sealed secrets.

**Acceptans:**

- party-overview når containern i varje miljö.
- Bara party-overview och drift har åtkomst.

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

- `MessageListener` med en listener-container per kö i `integration.sources`, registrerade med
  `RabbitListenerConfigurer`, och källan från `CONSUMER_QUEUE` ([§9]).
- `DeadLetterHealthIndicator` enligt [§12.1].
- `MessageIT` enligt [§10].

**Acceptans:**

- Ett meddelande per väg (alla objekttyper och operationer) går hela vägen från kön till databasen.
- Ett meddelande som ligger i omförsök på en kö hindrar inte att ett meddelande på en annan kö tillämpas.
- Ett ogiltigt meddelande hamnar i sin dead letter-kö, och likaså ett meddelande utan `message-id` ([§3.1]).
- Varje meddelande får starterns loggrad och request-id, även med containrar som registreras via
  `RabbitListenerConfigurer`.
- Det finns ingen `@ConditionalOnProperty` på lyssnaren och ingen `@EnableRabbit`.
- `DeadLetterHealthIndicator` finns i `/actuator/health` när RabbitMQ är påslaget. Den är `DOWN` med kö och antal i
  detaljerna när en dead letter-kö har meddelanden eller saknas, och `UP` annars. Den ingår inte i `liveness` eller
  `readiness`.
- Med `dept44.rabbitmq.enabled=false` finns ingen `DeadLetterHealthIndicator`, och tjänsten startar.
- README beskriver hur dead letters hanteras, och att kontaktpersonsrollerna sätts innan lyssnarna slås på ([§12]).

## SM1 — SupportManagement: outbox, relay och publicering

**Bygg:** enligt [§11.1] och [§11.2].

**Acceptans:**

- En ändring i ett namespace utan opt-in ger ingen outbox-rad.
- Outbox-tabellen har kolumnerna och indexet i [§11.1].
- Ett API-anrop som ger flera events blir ett meddelande med alla aktiviteter.
- Soft delete publiceras som `DELETE`, och restore som `UPDATE` med högre version.
- En ändring som bara rör stakeholders ökar versionen.
- `parameters` finns aldrig i meddelandet.
- Kanaltyperna mappas enligt [§11.2], och kanaler med en annan typ skickas inte. Mappningen täcker de typvärden som
  finns i SM:s `contact_channel` i produktion. En kanal som förekommer flera gånger på en stakeholder skickas en gång.
- Bara `externalIdType` `PRIVATE` och `ENTERPRISE` skickas som partytyp. Stakeholders med `EMPLOYEE` eller
  `ADMINISTRATOR` skickas inte, och andra skickas utan id.
- En stakeholders adress skickas som `POSTAL_ADDRESS` ([§11.2]). En stakeholder vars adressfält alla är tomma skickas
  utan adress.
- Ett nytt ärende, som saknar `modified`, publiceras med `sourceModified = created`.
- Relayn låser objektraden som första sats, med READ COMMITTED, innan den läser raderna och tillståndet ([§11.1]). En
  relay som startar medan en ändring är ocommittad skickar inget förrän ändringen är committad. Sedan skickas ett
  meddelande med den nya versionen och båda ändringarnas aktiviteter. En ändring som committas medan relayn
  publicerar hamnar i nästa meddelande, med sin egen version.
- E-post, sms och webbmeddelanden till och från ett ärende ger ingen outbox-rad, så länge ärendet självt inte ändras
  ([beslut 55]).
- Ett namespace med `accessControl = true` kan inte slå på publiceringen. Slås åtkomstkontrollen på för ett namespace
  som publicerar, stängs publiceringen av ([beslut 51]).
- Utkast publiceras inte, och ett inskickat utkast publiceras som `UPDATE` ([beslut 26]). Hård radering och purge
  publiceras som `DELETE` med versionen från outbox-raden.
- En health-indikator blir `DOWN` när den äldsta väntande outbox-raden är för gammal ([§11.1]).
- SM exponerar sitt AsyncAPI-dokument enligt det gemensamma formatet.
- En ändring som rullas tillbaka publiceras inte. En publicering som misslyckas ligger kvar i outboxen och skickas vid
  nästa körning.

## SM2 — SupportManagement: grundladdning

**Bygg:**

- En `JobType` som går igenom ärendena per namespace med keyset och ShedLock, enligt [§11.2].

**Acceptans:**

- Alla ärenden i namespaces med opt-in publiceras utan aktiviteter.
- Körs jobbet med ett namespace utan opt-in som parameter publiceras namespacets ärenden som `DELETE` ([§11.1]).
- Två samtidiga starter ger en körning.
- Pågående ändringar publiceras i rätt ordning per ärende.

## CD1 — Casedata: outbox, relay och publicering

**Bygg:** enligt [§11.1] och [§11.5], inklusive uppgraderingen till dept44 8.2.0.

**Acceptans:**

- En ändring i ett namespace utan opt-in ger ingen outbox-rad.
- Outbox-tabellen har kolumnerna och indexet i [§11.1].
- Varje publiceringspunkt i [§11.5] ger en outbox-rad. Schedulers, noteringar och beslut ger ingen.
- En PATCH av en enskild part ökar ärendets version och `updated`. Det verifieras med ett IT.
- Ett API-anrop blir ett meddelande med alla aktiviteter.
- En radering publiceras som `DELETE` med versionen från outbox-raden.
- `confidential = true` publiceras som `DELETE`. När flaggan tas bort publiceras `UPDATE` med högre version.
- Meddelandet innehåller aldrig `personId` som inte är ett UUID, `extraParameters`, `authorizedSignatory` eller
  handläggare. `organizationNumber` skickas bara som `legalId` när partyId saknas.
- `CELLPHONE` och `PHONE` skickas som kanaltypen `PHONE`, och `EMAIL` som `EMAIL`.
- En kanal som förekommer flera gånger på en part skickas en gång ([§11.1]).
- En parts alla adresser skickas med sin `addressCategory` som typ, och `street` och `houseNumber` blir `street`
  ([§11.5]). En adress utan kategori skickas som `POSTAL_ADDRESS`, och en part får högst 10 adresser.
- Titeln, `display_name` följt av `caseTitleAddition`, ryms inom 512 tecken ([§3.1]).
- `organizationNumber` skickas utan bindestreck och utan ett inledande `16` ([§3.1]). En enskild firmas nummer, med 10
  eller 12 siffror, skickas med `ENTERPRISE`. Ett värde som inte följer formatet, kontrollsiffran inräknad, skickas
  inte.
- Relayn låser objektraden som första sats, med READ COMMITTED, innan den läser raderna och tillståndet ([§11.1]). En
  relay som startar medan en ändring är ocommittad skickar inget förrän ändringen är committad. Sedan skickas ett
  meddelande med den nya versionen och båda ändringarnas aktiviteter.
- En health-indikator blir `DOWN` när den äldsta väntande outbox-raden är för gammal ([§11.1]).
- Casedata exponerar sitt AsyncAPI-dokument enligt det gemensamma formatet.
- `ProcessService.updateProcess` anropas i exakt samma fall som tidigare.
- En ändring som rullas tillbaka, t.ex. en misslyckad processtart, publiceras inte. En publicering som misslyckas ligger
  kvar i outboxen och skickas vid nästa körning.

## CD2 — Casedata: grundladdning

**Bygg:**

- En admin-endpoint och ett asynkront jobb med keyset och ShedLock, enligt [§11.5].

**Acceptans:**

- Endpointen returnerar utan att vänta på brokern.
- Alla ärenden i namespaces med opt-in publiceras utan aktiviteter.
- Körs jobbet med ett namespace utan opt-in som parameter publiceras namespacets ärenden som `DELETE` ([§11.1]).
- Två samtidiga starter ger en körning.
- Pågående ändringar publiceras i rätt ordning per ärende.

## PA1 — party-assets: outbox, relay och publicering

**Bygg:** enligt [§11.1] och [§11.3], med grenen `publish-asset-events` som grund.

**Acceptans:**

- Alla ändringspunkter i [§11.3] ger en outbox-rad, även PR3-importen och originalet som blir `REPLACED`.
- Outbox-tabellen har kolumnerna och indexet i [§11.1].
- En ändring ger en outbox-rad bara om assetens typ står på vitlistan före eller efter ändringen. En asset vars typ byts
  till en som inte står på listan publiceras som `DELETE`. Parkeringstillstånd (`PARKINGPERMIT`, `PARKING_PERMIT`)
  står inte på listan, och en ändring av dem ger ingen outbox-rad ([beslut 50]).
- Versionen är `AssetEntity.version`.
- `performedBy` sätts även vid delete.
- Händelsen skickas av servicemetoderna och `PR3Importer`, inte av `AssetEntityListener`. Ett IT visar att
  `createAsset`, `deleteAsset` och PR3-importen ger en outbox-rad.
- En ny asset publiceras med `sourceModified = created`.
- `PR3Importer` sparar varje asset och skickar händelsen i en `TransactionTemplate`, så att asseten och outbox-raden
  committas tillsammans ([§11.1]).
- En radering publiceras som `DELETE` med versionen från outbox-raden.
- Relayn låser objektraden som första sats, med READ COMMITTED, innan den läser raderna och tillståndet ([§11.1]). En
  relay som startar medan en ändring är ocommittad skickar inget förrän ändringen är committad. Sedan skickas ett
  meddelande med den nya versionen och båda ändringarnas aktiviteter.
- En health-indikator blir `DOWN` när den äldsta väntande outbox-raden är för gammal ([§11.1]).
- party-assets exponerar sitt AsyncAPI-dokument enligt det gemensamma formatet.
- En ändring som rullas tillbaka publiceras inte. En publicering som misslyckas ligger kvar i outboxen och skickas vid
  nästa körning.

## PA2 — party-assets: grundladdning via outbox

**Bygg:**

- republish skriver outbox-rader i batchar, enligt [§11.3].

**Acceptans:**

- republish returnerar utan att vänta på brokern. Jobbet körs asynkront med ShedLock, och två samtidiga starter ger en
  körning.
- Utan parameter publiceras alla assets av typerna på vitlistan utan aktiviteter. Körs jobbet för en typ som inte står
  på listan publiceras dess assets som `DELETE` ([§11.1]).

## CT1 — contract: tidsstämplar

**Bygg:**

- De nullbara kolumnerna `created` och `modified`, enligt [§11.4].

**Acceptans:**

- Migreringen är additiv och bakåtkompatibel vid rullande deploy.
- Befintliga rader behåller null.
- `created` och `modified` sätts när kontraktet skapas, och `modified` ändras vid varje skrivning.

## CT2 — contract: outbox, relay och publicering

**Bygg:** enligt [§11.1] och [§11.4].

**Acceptans:**

- Alla publiceringspunkter i [§11.4] ger en outbox-rad, även en vanlig periodförlängning.
- Billing-outboxen påverkas inte.
- Outbox-tabellen har kolumnerna och indexet i [§11.1].
- `organizationNumber` skickas bara som `legalId` när partyId saknas.
- `phoneNumber` och `emailAddress` skickas som kanaler av typen `PHONE` respektive `EMAIL`, och bara när de har ett
  värde.
- Adressen skickas med sin typ, där `BILLING_ADDRESS` blir `INVOICE_ADDRESS` och en adress utan typ blir
  `POSTAL_ADDRESS` ([§11.4]).
- `type` mappas till partytyp enligt [§11.4]. En stakeholder med `OTHER` eller utan typ skickas utan id.
- En stakeholder av typen `MUNICIPALITY`, och en som bara är `SIGNATORY` eller har en `POWER_OF_ATTORNEY_*`-roll,
  skickas inte. En stakeholder med en partsroll skickas med alla sina roller.
- `organizationNumber` skickas utan bindestreck och utan ett inledande `16` ([§3.1]). En enskild firmas nummer, med 10
  eller 12 siffror, skickas med `ENTERPRISE`. Ett värde som inte följer formatet, kontrollsiffran inräknad, skickas
  inte.
- Ett kontrakt utan `modified` publiceras med `sourceModified` = `1970-01-01T00:00:00Z` ([§11.4]).
- `Identifier` fångas när `X-Sent-By` finns och blir `performedBy`. Ett anrop utan headern fungerar som tidigare.
- En radering publiceras som `DELETE` med versionen från outbox-raden.
- Relayn låser objektraden som första sats, med READ COMMITTED, innan den läser raderna och tillståndet ([§11.1]). En
  relay som startar medan en ändring är ocommittad skickar inget förrän ändringen är committad. Sedan skickas ett
  meddelande med den nya versionen och båda ändringarnas aktiviteter.
- En health-indikator blir `DOWN` när den äldsta väntande outbox-raden är för gammal ([§11.1]).
- contract exponerar sitt AsyncAPI-dokument enligt det gemensamma formatet.
- En ändring som rullas tillbaka publiceras inte. En publicering som misslyckas ligger kvar i outboxen och skickas vid
  nästa körning.

## CT3 — contract: grundladdning

**Bygg:**

- En admin-endpoint som startar en asynkron, sidvis genomgång utan bilagor med ShedLock, och som skriver outbox-rader.

**Acceptans:**

- Endpointen returnerar utan att vänta på brokern, och två samtidiga starter ger en körning.
- Alla kontrakt i kommunen publiceras utan aktiviteter.
- Inga bilagor läses.

## T3 — Ändringskön i rabbitmq-topology

**Bygg:**

- party-overviews kö för ändringar av företag och personer, bunden till exchangen hos köns förvaltare ([§3.2], F2).
  Kön har DLQ, `x-delivery-limit: 5`, `x-dead-letter-strategy: at-least-once` och `x-overflow: reject-publish`, men
  ingen single-active-consumer.
- party-overview får read på kön.

**Acceptans:**

- Kön skapas med alla argument från början och tar emot förvaltarens meddelanden i testmiljön.
- Dead letter-kön har ingen TTL, och party-overviews AMQP-användare kan göra en passiv deklaration av den ([§12.1]).

## C14 — Företagsprofilen från ändringskön

**Bygg:**

- `RegistryChangeListener` med en egen listener-container för `integration.registry-changes.queue` ([§9], [§12]).
- Mappningen från köns format (F2) till partyId och partytyp.
- Ändringskön i `DeadLetterHealthIndicator`.

**Acceptans:**

- Ett meddelande om ett företag som inte är kund i någon kommun ger inget anrop till LegalEntity.
- Ett meddelande om ett företag som är kund i två kommuner ger ett anrop till LegalEntity och en profil per kommun,
  skriven i en transaktion per kund och loggad som `PROFILE_UPDATED` med kanalen `MESSAGE` ([§5.5]).
- Ett företag som är skyddat i en kommun får ingen profil där.
- Två meddelanden om samma företag i omvänd ordning, och samma meddelande två gånger, ger samma profil som det
  senaste anropet till LegalEntity.
- 404 från LegalEntity lämnar profilen orörd och loggar WARN med partyId:t. När LegalEntity inte svarar, eller svarar
  5xx, 429 eller 408, görs meddelandet om, och efter `x-delivery-limit` hamnar det i dead letter-kön. 429 och 408 görs
  om till ett avbrott, som när partyId slås upp ([§3.7]).
- Ett meddelande om en person ändrar ingenting och ger inget anrop.
- `DeadLetterHealthIndicator` blir `DOWN` när ändringsköns dead letter-kö inte är tom.
- `MessageIT` ([§10]) får ett fall med LegalEntity stubbad med svaret för SCA Forest Products: ett meddelande om
  företaget ger profilen enligt C3, och kunden markeras för omindexering.

[§3.1]: 03-meddelanden.md#31-krav-på-det-gemensamma-formatet
[§3.2]: 03-meddelanden.md#32-topologi
[§3.3]: 03-meddelanden.md#33-flödet-genom-tjänsten
[§3.4]: 03-meddelanden.md#34-versionsmatrisen
[§3.5]: 03-meddelanden.md#35-tillämpning
[§3.6]: 03-meddelanden.md#36-kundradering
[§4.3]: 04-datamodell.md#43-tabellerna
[§5.1]: 05-synlighet-och-kunddata.md#51-synlighet-per-namespace
[§5.2]: 05-synlighet-och-kunddata.md#52-namn-och-adress
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§6]: 06-rest-api.md
[§6.1]: 06-rest-api.md#61-endpoints
[§6.2]: 06-rest-api.md#62-kundsökning-opensearch
[§7.1]: 07-integrationer.md#71-uppslag-av-partyid
[§7.2]: 07-integrationer.md#72-citizen-och-legalentity
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
[§9]: 09-kodstruktur.md
[§3.7]: 03-meddelanden.md#37-felhantering
[§4.2]: 04-datamodell.md#42-gemensamt-för-alla-tabeller
[beslut 26]: 00-beslutslogg.md#beslut-26-supportmanagement-publicerar-inte-utkast
[§13.3]: 13-risker-och-oppna-fragor.md#133-risker
[beslut 50]: 00-beslutslogg.md#beslut-50-party-assets-publicerar-bara-asset-typer-på-en-vitlista
[beslut 51]: 00-beslutslogg.md#beslut-51-namespaces-med-åtkomstkontroll-i-sm-publiceras-inte
[beslut 55]: 00-beslutslogg.md#beslut-55-kommunikation-i-sm-publiceras-inte
[beslut 16]: 00-beslutslogg.md#beslut-16-personnummer-och-organisationsnummer-mappas-till-partyid
[beslut 32]: 00-beslutslogg.md#beslut-32-organisationsnummer-slås-upp-direkt-mot-legalentity
[§5.5]: 05-synlighet-och-kunddata.md#55-företagsuppgifter
[§13.1]: 13-risker-och-oppna-fragor.md#131-öppna-frågor

