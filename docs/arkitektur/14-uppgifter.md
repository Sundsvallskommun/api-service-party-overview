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

| Steg | Id  |                       Uppgift                        |              Beror på              |
|------|-----|------------------------------------------------------|------------------------------------|
| 1    | C1  | Projektgrund                                         | —                                  |
| 2    | C2  | Datamodell, Flyway och schemaverifiering             | C1                                 |
| 3    | C3  | Integrationer mot Citizen och LegalEntity            | C1, C2, behörighet i Metakatalogen |
| 4    | C4  | Kontaktpersonsroller via REST                        | C2                                 |
| 5    | C5  | Meddelandekärnan                                     | C2, C3, C4, F1                     |
| 6    | C6  | Kundmeddelanden (`CUSTOMER`)                         | C5                                 |
| 7    | C7  | Läs-API för kund, kontakter, engagemang och historik | C2, C3                             |
| 8    | C8  | Noteringar                                           | C3, C5                             |
| 9    | C9  | Kundradering via REST och API för teknisk logg       | C5                                 |
| 10   | C10 | Skyddad identitet: nattlig kontroll                  | C3, C5, C9                         |
| 11   | T2  | Egen OpenSearch-container per miljö                  | —                                  |
| 12   | C12 | Sökindex: Hibernate Search, kö och relay             | C2, C3, C5, T2                     |
| 13   | C13 | Sök-endpoint                                         | C5, C7, C12                        |
| 14   | T1  | Köer och exchanges i driftkonfigurationen            | —                                  |
| 15   | C11 | RabbitMQ-lyssnare och `MessageIT`                    | C5, C6, T1                         |
| 16   | SM1 | SupportManagement: outbox, relay och publicering     | F1                                 |
| 17   | SM2 | SupportManagement: grundladdning                     | SM1                                |
| 18   | CD1 | Casedata: outbox, relay och publicering              | F1                                 |
| 19   | CD2 | Casedata: grundladdning                              | CD1                                |
| 20   | PA1 | party-assets: outbox, relay och publicering          | F1                                 |
| 21   | PA2 | party-assets: grundladdning via outbox               | PA1                                |
| 22   | CT1 | contract: tidsstämplar                               | —                                  |
| 23   | CT2 | contract: outbox, relay och publicering              | CT1, F1                            |
| 24   | CT3 | contract: grundladdning                              | CT2                                |
| 25   | T3  | Ändringskön i driftkonfigurationen                   | F2                                 |
| 26   | C14 | Företagsprofilen från ändringskön                    | C3, C5, C11, T3, F2                |
| 27   | C15 | Skyddskontroll från ändringskön                      | C10, C14, F2                       |
| 28   | C16 | Kontaktpersoner via REST                             | C7, C8, C9, C10, C13               |
| 29   | T4  | Påminnelser i Spring Boot Admin                      | —                                  |

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
  tolka eller har ett värde över 255 tecken. C4, C8, C9, C12 och C16 använder den.
- README skrivs om efter detta dokument.

**Acceptans:**

- Tjänsten startar med `dept44.rabbitmq.enabled=false` utan någon anslutning till RabbitMQ.
- Tjänsten startar inte om `integration.sources` är tom eller om en källa saknar kö.
- `management.health.rabbit.enabled` är borttagen ur `application.yml`.
- dept44:s PII-maskning är påslagen med alla kategorier.
- `spring.task.scheduling.pool.size` är minst antalet schemalagda jobb ([§12]). Ett test med två schemalagda metoder
  visar att den ena körs medan den andra pågår.
- Ingen POC-kod finns kvar, och `openapi.yaml` är regenererad utan POC:ens endpoint.
- `SentBy` ger värdet för `joe01doe; type=adAccount` och ger 400 för en saknad header, för `joe01doe` utan `type=` och
  för ett värde på 256 tecken. `Anna Berg; type=custom` ger `AnnaBerg` med typen `CUSTOM`, och `type=adaccount` ger
  typen `CUSTOM` ([§6]). Enhetstest.

## C2 — Datamodell, Flyway och schemaverifiering

**Bygg:**

- En ny `V1_0__create_tables.sql` med alla tabeller i [§4.3], inklusive `shedlock`.
- Entiteter, `AbstractHistoryEntity`, adressens `@Embeddable`, enums och `RolesConverter`.
- Repositories med grundfrågorna: IODKU och låsning av `source_object` och `customer`, och markeringen i
  `search_index_queue` ([§6.2]). Frågorna för ett visst flöde läggs till i uppgiften som bygger flödet.
- Valet av modell för `contact_channel` ([§4.2]), med primärnyckeln `(contact_id, type, value)` i båda fallen.
- `DeadlockRetry`, som gör om en hel transaktion högst tre gånger vid `PessimisticLockingFailureException`, utanför
  transaktionen ([§3.6]). C4, C8, C9, C10 och C16 använder den.
- `schema.sql`, `truncate.sql`, `CascadeIT` och `SchemaParityIT`.

**Acceptans:**

- Namn, kolumner, index och FK stämmer exakt med [§4.3]. Varje FK under `customer` har `on delete cascade`, både i
  Flyway och i `schema.sql`.
- `SchemaVerificationTest` och `SchemaParityIT` är gröna. Om ett index tas bort i bara den ena källan blir
  `SchemaParityIT` röd. Det provas manuellt en gång.
- `CascadeIT`: `DELETE FROM customer WHERE id = ?` lämnar noll rader i `customer_address`, `engagement`,
  `engagement_history`, `contact`, `contact_channel`, `manual_contact_person`, `manual_contact_person_channel`,
  `note`, `note_history`, `asserted_protection`, `enterprise_profile`, `enterprise_workplace`,
  `enterprise_workplace_address`, `enterprise_workplace_sni_code` och `enterprise_proceeding` för kunden. Raderna i
  `technical_log`, `source_object`, `search_index_queue` och `registry_person` finns kvar, och likaså kundens
  kontaktrader hos andra kunder (`party_id`).
- `created` och `modified` sätts av `@PrePersist`/`@PreUpdate`, trunkeras till millisekunder och testas per entitet.
- Repository-tester: IODKU för `source_object` och `customer` är idempotent. Samtidighetstestet med två trådar som
  skapar samma kund ger en rad och inget undantag.
- `DeadlockRetry` gör om en transaktion som faller på deadlock och kastar felet efter tre misslyckade försök. Det testas
  med en `CannotAcquireLockException` i första försöket.
- Adressens `@Embeddable` har nullbara kolumner, och `address_type` är `not null` i `customer_address` och
  `enterprise_workplace_address` via `@AttributeOverride`. `text`-kolumnerna har `columnDefinition = "text"` ([§4.2]).

## C3 — Integrationer mot Citizen och LegalEntity

**Bygg:**

- Kodgenereringen för `citizen.yaml` och `legalentity.yaml` med openapi-generator ([§9]).
- `CitizenClient`/`CitizenIntegration` med:
  - `getPartyIds` (batch, `POST /{municipalityId}/guid/batch`) för personnummer → partyId ([§7.1])
  - `getCitizen` och `getCitizens` (batch), som båda returnerar skyddsnivå, Citizens värden för `classified` och
    `protectedNR`, namn och folkbokföringsadress ([§5.4], [§7.2])
- `LegalEntityClient`/`LegalEntityIntegration` med:
  - `getPartyId` (`GET /{municipalityId}/{organizationNumber}/guid`) för organisationsnummer → partyId ([§7.1])
  - `getLegalEntity` (`GET /{municipalityId}/{legalEntityId}`), som returnerar namn, postadress, besöksadress och
    organisationsnummer, och företagsprofilen med arbetsställen och förfaranden, mappad av `EnterpriseProfileMapper`
    ([§5.5], [§7.2])
- `CompanySniCodes`, en ren funktion som räknar fram företagets SNI-koder ur arbetsställenas sparade rader ([§5.5]).
  C7 och C12 använder den.
- Partytypen för ett partyId avgörs med `getCitizen` och `getLegalEntity` ([§7.2]). En enskild firma känns igen på att
  organisationsnumret är ett personnummer ([§5.4]). Numret sparas aldrig.
- legalId valideras mot formatet i [§3.1], per partytyp och med kontrollsiffran, innan det skickas ([§7.1]).
- Ett eget undantag utan URL för IO-fel ([§7.1]).
- `logbook.excluded.paths` för LegalEntitys uppslag med numret i URL:en ([§12]).
- Konfiguration med OAuth2 och circuit breaker, där `ClientProblem` inte räknas som fel ([§12]). Instansnamnen är
  klienternas `CLIENT_ID`.
- WireMock-stubbar för IT.

**Acceptans:**

- En serie 400 från LegalEntity eller Citizen öppnar inte circuit breakern, men en serie 5xx eller timeouts gör det
  ([§12]).
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
- Utan behörighet i Metakatalogen svarar Citizen 401 och inte ett filtrerat svar. Det verifieras i testmiljön ([§5.4]).
- I testmiljön verifieras också:
  - att `guid/batch` ger `personId` även för personer med skyddade personuppgifter
  - att Citizen svarar 5xx på hela `guid/batch`-anropet, och inte per element, när felet är tillfälligt ([§7.1])
  - vilken version av LegalEntity som har `…/{organizationNumber}/guid`
  - i vilket format LegalEntity ger en enskild firmas organisationsnummer, och vilket format `…/guid` tar emot för det
  - vilka värden Citizen har i `addressType`, och att folkbokföringsadressen har `POPULATION_REGISTRATION_ADDRESS`

  Resultaten dokumenteras i README.

- Citizen anropas med `ShowClassified=true` i alla anrop som ger personuppgifter. Flaggorna tolkas enligt tabellen i
  [§5.4], med ett testfall per rad. Integrationen returnerar också Citizens värden för `classified` och `protectedNR`,
  trimmade och med ett tomt värde som null, och namn och adress även för en skyddad person.

- Citizens folkbokföringsadress blir en adress av typen `POPULATION_REGISTRATION_ADDRESS`, och andra adresser i svaret
  tas inte med. LegalEntitys `postAddress` och `address` blir `POSTAL_ADDRESS` och `VISITING_ADDRESS` enligt [§7.2].
  En adress där alla fält är tomma tas inte med.

- Företagsprofilen mappas enligt [§5.5]. Testet använder svaret för SCA Forest Products (organisationsnummer
  5563793586) som stub och kontrollerar:

  - tio arbetsställen, där huvudarbetsstället har CFAR-nummer 31107162, och arbetsställenas SNI-koder utan de tomma
    elementen
  - storleksklassen `8` "200–499 anställda", juridisk form `49` och kommunen för företagets säte `2281`
  - besöksadressen "SKEPPARPLATSEN 1" och en postadress med bara postnummer och ort
  - att tomma strängar blir null
- `CompanySniCodes` ger 70100, 72190, 86212 och 91012, i den ordningen, för arbetsställena i SCA-stubben. Enhetstester
  täcker ett företag utan huvudarbetsställe, ett med två, och ett arbetsställe utan storleksklass eller med en som inte
  är ett tal, som räknas som minst.
- `officers` och `fullSignatureDescription` finns inte i integrationens modell, och ingenting ur LegalEntitys svar
  loggas. Profilen har inget organisationsnummer.
- Tolkningen av `classified` och `protectedNR` ([beslut 41]) verifieras i testmiljön mot en sekretessmarkerad testperson
  och en testperson med skyddad folkbokföring. Resultatet dokumenteras i README.
- 401 från Citizen ger en `ClientProblem` vars detail säger att behörigheten i Metakatalogen saknas.
- Ingenting ur Citizens svar loggas.

## C4 — Kontaktpersonsroller via REST

**Bygg:**

- `ContactPersonRoleResource`, `ContactPersonRoleService` och repository, enligt [§6].

**Acceptans:**

- `PUT` ersätter källans roller som en helhet. En tom lista tar bort alla.
- Ett `PUT` som faller på deadlock görs om med `DeadlockRetry` från C2.
- En källa som saknas i `integration.sources` ger 400. En tom eller blank roll ger 400.
- `created_by` sätts från `X-Sent-By`, och ett anrop utan den headern ger 400.
- Resource-test, failure-test och IT (happy case).

## C5 — Meddelandekärnan

**Bygg:**

- Kodgenereringen för AsyncAPI-dokumentet `party-overview-messages.yaml` med ZenWave SDK ([§9]).
- `ObjectChange` och mappningen från den genererade meddelandemodellen.
- `TechnicalLogService`, som markerar kunden i `search_index_queue` för varje rad utom `IGNORED_STALE` ([§6.2]).
- `MessageService` utan transaktion och `MessageWriter` med `@Transactional`, enligt [§3.3]–[§3.6] för `ERRAND`, `ASSET`
  och `CONTRACT`.
- `EnterpriseProfileService`, som skriver företagsprofilen under kundens lås ([§5.5]).
- Klassningen av parter och kontaktuppgifterna ([§5.3]), "nyast vinner" ([§5.2]) och skrivningen av den tekniska loggen
  ([§8.1]).
- Ännu ingen lyssnare.

**Acceptans:**

- Versionsmatrisen i [§3.4] testas tabelldrivet, och varje cell har ett eget fall.
- Om samma meddelande tillämpas två gånger blir det ingen dubblerad historik, och den andra loggraden blir
  `ENGAGEMENT_UPDATED`.
- Ett inaktuellt `UPDATE` skriver sina aktiviteter på engagemangsraderna för de kundparter som har en rad i objektet,
  och ändrar inget annat ([§3.5]). Testfall: v=8 tillämpas och sedan v=7, och historiken har båda meddelandenas
  aktiviteter.
- En aktivitet vars id redan finns på raden skrivs inte igen, men samma id på en annan rad skrivs ([§4.3]).
- En part som försvinner ur objektet ger `ENGAGEMENT_REMOVED`. Var det kundens sista engagemang och har kunden inga
  noteringar ger det dessutom `CUSTOMER_DELETED`. En kund med en notering finns kvar.
- En kontaktpersonsroll ger en `CONTACT_PERSON`-kontakt på varje `ENTERPRISE`-kund och aldrig en egen kund. Ett objekt
  utan företagskund ger ingen kontakt.
- En kontaktperson får partens första postadress, annars den första besöksadressen och annars den första
  fakturaadressen ([§5.3]). En part utan adress ger en kontaktrad utan adress, och kundens egen kontaktrad
  (`contact_type = CUSTOMER`) har aldrig någon adress.
- En kontaktpersons legalId mappas till partyId, och partyId sparas på kontaktraden. En kontaktperson sparas oavsett
  skydd ([§5.3]). En person får sig själv som `registry_party_id`. En kontaktperson med `ENTERPRISE` kontrolleras som
  en företagskund och frågas inte i Citizens batch. En enskild firma får ägaren som `registry_party_id`, och ett annat
  företag får `fallback_protection = NONE` utan `registry_party_id`. Partens `protection` sparas som
  `party_protection`.
- En kontaktperson som saknar både partyId och legalId, eller där det inte gick att avgöra om den är en enskild firma,
  får `fallback_protection = UNVERIFIED` utan `registry_party_id` ([§5.3]).
- Varje `registry_party_id` på en kontaktrad har en rad i `registry_person` efter commit. Citizen anropas bara för de
  personer som saknar rad eller har `UNKNOWN`. Svarar Citizen inte, eller saknar post, skapas raden med `UNKNOWN`.
- Två meddelanden som hämtar Citizen för samma person och skriver i omvänd ordning ger svaret från det anrop som
  startade senast. Det äldre svaret ändrar varken nivå eller Citizens värden i `registry_person` ([§5.4]).
- Ingen kontaktrad uppdateras när en kunds eller en persons nivå ändras. Kontaktraderna ersätts bara som helhet av
  meddelandena ([§5.3]).
- En automatisk radering av kunden raderar partens kontaktrader från källorna hos andra kunder i kommunen, men inte
  partens kontaktpersoner från REST. Varje kund som förlorar rader loggas som `CONTACT_REMOVED` och markeras för
  omindexering ([§3.6]). När objektet sedan ändras i källan och fortfarande anger personen som kontaktperson kommer
  raden tillbaka.
- En ny `ENTERPRISE`-kund vars organisationsnummer är ett personnummer, med 10 eller 12 siffror, får ägarens partyId
  som `registry_party_id` och ägarens skydd från Citizen. Firman och ägaren blir två olika kunder. Ett vanligt
  organisationsnummer ger `registry_party_id = null` ([§5.4]).
- Svarar LegalEntity eller Citizen inte när en ny företagskunds organisationsnummer eller ägare hämtas, får kunden
  `UNKNOWN` utan `registry_party_id`, och meddelandet tillämpas utan omförsök ([§3.7]).
- En ny företagskund får sin profil från LegalEntity, också när organisationsnumret finns i meddelandet. Svarar
  LegalEntity inte skapas kunden utan profil, och organisationsnumret från meddelandet avgör ändå om det är en enskild
  firma. En skyddad företagskund får sin profil, och en kund som blir skyddad behåller sin ([§5.5]).
- En profil vars `fetched` är äldre än den sparade skriver inte över den. Det testas med två hämtningar som skrivs i
  omvänd ordning.
- En profil med 2 000 arbetsställen, vart och ett med två adresser och tre SNI-koder, skrivs och läses tillbaka
  komplett, och testet visar att raderna skrivs i JDBC-batchar ([§13.3]).
- En part med legalId mappas och tillämpas. Finns legalId inte hos Citizen eller LegalEntity, eller har det fel format,
  hoppas parten över medan övriga parter tillämpas, och engagemang raderas ändå för kunder som inte längre är parter
  ([beslut 16]). legalId finns inte i någon entitet.
- En kund som förekommer två gånger blir en rad, och rollerna slås ihop.
- Ett nyare `sourceModified` ersätter namn och adresser som helhet, men ett äldre gör det inte. Ett nyare meddelande
  med färre adresser tar bort de övriga, och ett med namn men utan adresser tömmer adresserna ([§5.2]).
- Identiska adresser i ett meddelande sparas en gång, och en adress där alla fält utom typen är tomma sparas inte.
- En part med en tom postadress följd av en besöksadress ger en kontaktperson med besöksadressen ([§5.3]).
- En kund som blir skyddad i samma meddelande som den har adresser får nivån och behåller namn och adresser från
  meddelandet efter commit. Barnraderna ersätts utan att kunden kopplas loss ([§4.2]).
- En ny privatkund får sin skyddsstatus och Citizens värden för `classified` och `protectedNR` från Citizen innan den
  skapas. Om Citizen inte svarar, svarar 5xx, 429 eller 408, har en öppen circuit breaker eller saknar en post för
  personen, skapas kunden med `UNKNOWN` och med namn, adresser och egna kontaktuppgifter från meddelandet. Meddelandet
  tillämpas då utan omförsök. 401 från Citizen kastas som `ClientProblem` ([§5.4]).
- `UNKNOWN` från folkbokföringen ger den effektiva nivån `UNKNOWN`, men en källas `SECRECY_MARKING` eller
  `PROTECTED_REGISTRATION` går före.
- README och javadoc för `ProtectionLevel` beskriver alla fem värden, också `UNVERIFIED`, som bara gäller
  kontaktpersoner. För `UNKNOWN` står när värdet uppstår, att kunden hanteras som skyddad och att den nattliga
  kontrollen ersätter det. Det står att en nivå som inte är `NONE` betyder att uppgifterna inte får lämnas ut utan
  prövning ([§5.4]).
- En part med `protection` ger `engagement.party_protection`, och kunden räknas som skyddad. Namn, adresser och egna
  kontaktuppgifter sparas, och befintliga står kvar. Skyddet upphör när varken något engagemang eller folkbokföringen
  längre anger det.
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
- Versionsvakten fungerar som för andra objekt, nycklad på partyId.
- `DELETE` raderar kunden även när källan aldrig har skickat `CUSTOMER UPDATE` för den, och när kunden har raderats och
  skapats på nytt av ett engagemang ([§3.4]). Ett `DELETE` utan part ger valideringsfel. Kundens rader i
  `source_object` raderas med den.
- `DELETE` sparar aldrig någon tombstone. Efter ett `DELETE`, också ett som levereras igen, finns ingen rad i
  `source_object` för `CUSTOMER` med partyId:t, även när kunden saknades.
- `DELETE` raderar personens kontaktrader hos andra kunder, också kontaktpersonerna från REST, och loggar
  `CONTACT_REMOVED` för de kunderna ([§5.3]).
- En kund som har fått ett `CUSTOMER UPDATE` raderas inte automatiskt när dess sista engagemang försvinner, och dess
  kontaktuppgifter och skydd från källan finns kvar ([§3.6]).
- Partens `protection` sparas som källans rad i `asserted_protection`, och skyddsnivån räknas om. En skyddad kund får
  namn, adresser och kontaktuppgifter från meddelandet som andra kunder. `UPDATE` loggas som `CUSTOMER_UPDATED`.
- Ett `UPDATE` utan `protection` från källa B tar bort B:s rad men lämnar källa A:s skydd orört, och kunden förblir
  skyddad ([§4.3]).
- Kundens kontaktrader från meddelandet får meddelandets namespace, eller blir globala om det saknas.
- Parten i ett `CUSTOMER`-meddelande är alltid kunden och klassas inte på roller. Den behöver partyId eller ett legalId
  som går att mappa, annars ignoreras meddelandet.

## C7 — Läs-API för kund, kontakter, engagemang och historik

**Bygg:**

- `CustomerResource` (GET och lookup) och `EngagementResource` med service och API-modeller, enligt [§5.1] och [§6.1].
- Endpointen för ett företags arbetsställen ([§6.1]).

**Acceptans:**

- Ingen sökväg har namespace ([§6]). Utan filter visas kundens alla kontakter. Med `namespace=A` visas A:s och de
  globala, men aldrig B:s, och med `namespace=A&namespace=B` visas A:s, B:s och de globala. Ett ogiltigt namespace
  i filtret ger 400. Dubbletter slås ihop, men två kontaktpersoner som bara skiljer sig i adress eller namespace visas
  båda. En ihopslagen kontaktperson får den högsta nivån bland raderna, med den radens värden från Citizen.
- Kunden returneras med alla sina adresser, i ordningen i [§5.2], och varje kontaktperson med sin adress, sin
  `protectionLevel` och Citizens värden ([§5.3]).
- En kontaktpersons nivå räknas fram enligt [§5.3], tabelldrivet med ett fall per gren: kontaktpersonen är kund i
  kommunen, är kund bara i en annan kommun, har en rad i `registry_person`, har bara `fallback_protection`, och har
  `party_protection` som är högre respektive lägre än övriga. En enskild firma som är kontaktperson och vars ägare är
  kund i kommunen, men inte firman, får ägarens nivå från `registry_person`.
- När `registry_person` ändras visar nästa läsning den nya nivån på alla personens kontaktrader i alla kommuner, utan
  att någon kontaktrad har skrivits om.
- En företagskund returneras med `enterpriseProfile` och SNI-koderna i ordning, också när den är skyddad. En
  privatkund och ett företag utan profil returneras utan den ([§6.1]).
- Företagets SNI-koder räknas fram ur arbetsställenas enligt [§5.5]. Med stubben för SCA Forest Products från C3 blir
  de 70100, 72190, 86212 och 91012, i den ordningen.
- Arbetsställena returneras sidvis, med huvudarbetsstället först och sedan efter namn, var och en med sina adresser och
  SNI-koder. Ett arbetsställe utan namn kommer sist. En privatkund och ett företag utan profil ger en tom sida, och en
  okänd kund ger 404.
- En skyddad kund, också en med `UNKNOWN`, returneras med `protectionLevel`, `classified`, `protectedNR`, namn,
  adresser och kontaktkanaler.
- `protectionLevel` i OpenAPI-specen beskriver alla fem värden, också `UNVERIFIED` för kontaktpersoner. Där står att
  `UNKNOWN` betyder okänd skyddsstatus som ska hanteras som skyddad, och att en nivå som inte är `NONE` betyder att
  uppgifterna inte får lämnas ut utan prövning. `classified` och `protectedNR` beskrivs som Citizens värden ([§5.4]).
- Uppslaget tar `legalId` och `partyType` i kroppen och ger samma svar som `GET` på kunden. Finns inte numret eller
  kunden blir svaret 404. Svarar Citizen eller LegalEntity inte, eller med 5xx, blir svaret 503. Numret finns aldrig
  i svar, loggrader eller Problem-detail. En enskild firma slås upp med `ENTERPRISE` och ägarens personnummer, med 10
  eller 12 siffror, och ger firman och inte ägaren ([§3.1]).
- Historiken för ett engagemang sorteras förvalt på `occurredAt,desc` och pagineras ([§6.1]).
- Engagemang från alla namespaces visas utan filter. `namespace`, som kan anges flera gånger, och `objectType`
  filtrerar. Med ett namespace-filter kommer engagemang utan namespace också med ([§5.1]). Förvald sortering är
  `sourceModified,desc`, och paginering med `size > 100` begränsas till 100.
- Ett engagemang som tillhör en annan kund ger 404.
- Ett sorteringsfält som inte är tillåtet ger 400, och sidorna är stabila när sorteringsnyckeln är lika ([§6.1]).
- Testerna för profilen och arbetsställena lägger in sina rader direkt i databasen, med värdena från SCA-stubben i C3.
- Frågorna använder indexen i [§4.3]. Det verifieras med `EXPLAIN` i ett repository-test för engagemangslistan och
  kontaktfrågan.

## C8 — Noteringar

**Bygg:**

- `NoteResource`, `NoteService` och `note_history`.
- Att en notering skapar kunden enligt [§7.2].
- Varje skrivning av en notering låser kunden först och körs med READ COMMITTED ([§3.6]), med `DeadlockRetry` från C2.

**Acceptans:**

- En notering skapas med eller utan `namespace` i kroppen, och utan blir den global. Listan ger kundens alla noteringar
  utan filter, och med `namespace=A` A:s och de globala, men aldrig B:s ([§5.1]). En notering hämtas, ändras och
  raderas med sitt id oavsett namespace, och en notering som inte tillhör kunden i sökvägen ger 404. `PATCH` ändrar
  aldrig namespace.
- `POST` sätter `created_by` till värdet i `X-Sent-By`, och `PATCH` sätter `modified_by` men ändrar aldrig
  `created_by`. Båda returneras som `createdBy` och `modifiedBy` ([§4.3]).
- `CREATED` och `UPDATED` skrivs till historiken med `performed_by` och `performed_by_type` från `X-Sent-By`.
- `GET`, `POST` och `PATCH` returnerar noteringens version som en stark `ETag`, t.ex. `"3"`, och `PATCH` returnerar
  den nya versionen ([§6.1]).
- `PATCH` och `DELETE` utan `If-Match`, eller med `If-Match: *`, ger 428. Med en annan version än den sparade, eller
  ett värde som inte går att tolka, ger de 412, och ingenting ändras ([beslut 61]). `W/"3"` godtas som `"3"`, och en
  lista med flera ETags godtas om en av dem stämmer.
- Samtidighetstest: två `PATCH` med samma version ger en lyckad ändring och en 412, aldrig 500.
- `PATCH` med `subject: null` lämnar ämnet orört.
- `POST`, `PATCH` och `DELETE` utan `X-Sent-By`, med en header utan `type=` och med ett värde över 255 tecken ger 400,
  och ingenting sparas ([§6]).
- En notering på en okänd kund skapar kunden:
  - en `PRIVATE`-kund när Citizen känner partyId:t, med skyddsstatus, Citizens värden för `classified` och
    `protectedNR`, namn och folkbokföringsadress från Citizen, också när personen är skyddad
  - en `ENTERPRISE`-kund när bara LegalEntity känner det, med namn, postadress, besöksadress och företagsprofil från
    LegalEntity, och för en enskild firma med ägarens skydd och värden från Citizen, också när ägaren är skyddad

  Kunden loggas som `CUSTOMER_CREATED` med kanalen `REST`.

- En part som varken Citizen eller LegalEntity känner ger 404, och ingen kund skapas. Svarar Citizen eller LegalEntity
  inte, eller med 5xx, blir svaret 503, och varken notering eller kund skapas. Ett tillfälligt fel ger aldrig 404.

- `subject` över 255 tecken och `body` över 10 000 tecken ger 400.

- Att skapa, ändra eller radera en notering markerar kunden i `search_index_queue`.

- Om ett meddelande skapar kunden samtidigt blir det en kund och inget fel. Om ett meddelande raderar kunden medan en
  notering skapas blir det inget FK-fel, eftersom kunden är låst.

- `PATCH` och `DELETE` på en notering vars kund har raderats ger 404 och skapar inte kunden ([§3.6]).

- När den sista noteringen raderas och kunden saknar engagemang och `CUSTOMER`-data raderas kunden, och
  `CUSTOMER_DELETED` loggas med kanalen `REST` ([§3.6]). Personens kontaktrader från källorna hos andra kunder raderas
  också, men inte personens kontaktpersoner från REST.

- En skrivning som faller på deadlock görs om med `DeadlockRetry` från C2 ([§3.6]).

## C9 — Kundradering via REST och API för teknisk logg

**Bygg:**

- `DELETE /{municipalityId}/customers/{partyId}`.
- `TechnicalLogResource`: `GET` per kund och `DELETE` med filtren `partyId` och `createdBefore` ([§8.2]).
- `TechnicalLogRetentionScheduler`, som gallrar loggen efter `technical-log.retention` ([§8.2], [§12]).

**Acceptans:**

- `DELETE` på kund raderar kunden med allt den äger, inklusive dess rader i `source_object` för `CUSTOMER`, och skriver
  `CUSTOMER_DELETED` med kanalen `REST` och `performed_by`.
- `DELETE` på kund raderar också personens kontaktrader hos andra kunder i kommunen, i `contact` och i
  `manual_contact_person` (`party_id`), även när personen bara är kontaktperson. De kunderna loggas som
  `CONTACT_REMOVED` och markeras för omindexering. Kontaktrader i en annan kommun lämnas orörda. Svaret blir 404 bara
  när varken kund eller kontaktrader i någon av tabellerna finns.
- `DELETE` på kund låser kundens rader i `source_object` för `CUSTOMER` före kunden ([§3.6]). Samtidighetstest: ett
  `CUSTOMER UPDATE` för samma kund tillämpas samtidigt, och båda lyckas.
- En radering som faller på deadlock görs om med `DeadlockRetry` från C2.
- Båda `DELETE`-anropen kräver `X-Sent-By`, och ett anrop utan den ger 400.
- Den tekniska loggen kan läsas efter raderingen, sorteras förvalt på `created,desc` och pagineras.
- `DELETE` på den tekniska loggen utan något filter ger 400. Med `partyId`, med `createdBefore` och med båda raderas
  exakt de rader som matchar.
- Raderingen görs i batchar med `LIMIT`, med en transaktion per batch. Det testas med fler rader än en batch.
- Gallringen raderar rader vars `created` är äldre än lagringstiden och lämnar yngre rader orörda ([§8.2]).
  `technical-log.retention` binds som `Period`, förvalet är `P5Y`, och ett annat värde i konfigurationen gäller.
  Gränsen räknas från en injicerad `Clock`, och testet flyttar klockan över gränsen.
- Gallringen använder `idx_technical_log_created`, vilket verifieras med `EXPLAIN`, och ShedLock förhindrar att två
  körningar pågår samtidigt.
- `createdBefore` tar en tidpunkt med zon enligt ISO 8601, och ett annat format ger 400. Raderingen sker i batchar om
  `technical-log.delete-batch-size`, och testet sätter den lägre än antalet rader.

## C10 — Skyddad identitet: nattlig kontroll

**Bygg:**

- `ProtectionRefreshScheduler` enligt [§5.4]: urval på `registry_party_id`, `registry_protection`,
  `registry_checked` och `legal_entity_not_found`, keyset på `id`, uppslaget av ägaren för företag utan
  `registry_party_id`, uppehållet efter 404 från LegalEntity, Citizen batch, en transaktion per batch, omräkning av
  skyddsnivån, `PROTECTION_CHANGED`, ifyllnaden av namn och folkbokföringsadress och markering av ändrade kunder för
  omindexering.
- Passet över `registry_person`, med keyset på `id`, och rensningen av rader som ingen kontaktrad pekar på ([§5.4]).
- Rensningen av kunder som inte har något kvar, först i körningen, med samma låsning som raderingen via REST i C9
  ([§3.6], [§5.4], [beslut 69]).

**Acceptans:**

- En kund utan engagemang, noteringar, kontaktpersoner från REST och `CUSTOMER`-data raderas och loggas som
  `CUSTOMER_DELETED` med kanalen `SCHEDULER`. Personens kontaktrader från källorna hos andra kunder raderas och loggas
  som `CONTACT_REMOVED`, och personens kontaktpersoner från REST står kvar ([§3.6]). En kund som har något av de fyra
  står kvar, med ett testfall per slag.
- Samtidighetstest: ett meddelande ger en tom kund ett engagemang medan rensningen kör. Antingen står kunden kvar med
  engagemanget, eller så raderas kunden först och skapas på nytt av meddelandet. Engagemanget finns i båda fallen, och
  inget FK-fel uppstår.
- En kund som rensningen raderar tas inte med i kontrollen mot Citizen samma natt.
- `EXPLAIN` i ett repository-test visar att rensningens urval använder indexen som börjar med `customer_id` i
  `engagement`, `note` och `manual_contact_person`, och nyckeln i `source_object` ([§4.3]).
- Alla kunder med `registry_party_id` eller `registry_protection = UNKNOWN` vars `registry_checked` saknas eller är
  äldre än körningens start tas med, även en kund som skapades kl. 10 dagen före. Undantaget är ett företag med
  `UNKNOWN` utan `registry_party_id` under uppehållet efter 404 från LegalEntity. Ett företag med känd status som inte
  är en enskild firma, och som har en profil, tas aldrig med ([§5.4]).
- Två kontroller av samma kund som skrivs i omvänd ordning ger nivån från det anrop som startade senast. Det äldre
  svaret ändrar varken nivå eller Citizens värden ([§5.4]).
- Urvalet går i primärnyckelns ordning. `EXPLAIN` i ett repository-test visar en range-läsning på `PRIMARY` utan
  filesort.
- En batch som misslyckas väljs inte igen i samma körning.
- En kund med `UNKNOWN` får folkbokföringens nivå och Citizens värden när Citizen svarar. Svarar Citizen inte förblir
  kunden `UNKNOWN` och tas med nästa natt.
- En privatkund vars `name_address_modified` är null får namn och folkbokföringsadress från Citizens svar och
  `name_address_modified = now()`, också när den är skyddad. En privatkund som redan har namn eller adresser från ett
  meddelande eller en notering ändras inte, och en enskild firma får aldrig ägarens namn eller adress ([§5.4]).
  Ifyllnaden loggas som `NAME_ADDRESS_FILLED` med kanalen `SCHEDULER`, och kunden markeras för omindexering, också
  när nivån inte ändrades.
- Ett företag med `UNKNOWN` utan `registry_party_id` slås upp i LegalEntity igen. En enskild firma får ägaren som
  `registry_party_id` och ägarens nivå. Ett annat företag får `registry_protection = null` och sin profil från samma
  svar, och tas inte med igen.
- En företagskund utan profil tas med och får sin profil, loggad som `PROFILE_UPDATED`, också när den är skyddad
  ([§5.5]).
- Svarar LegalEntity 404 får kunden `legal_entity_not_found`. Ett företag med `UNKNOWN` utan `registry_party_id`, och
  ett företag utan profil, tas då inte med förrän `protection.legal-entity-retry` har gått. Testet har en kund vars
  `legal_entity_not_found` ligger inom intervallet och en vars värde ligger utanför. En enskild firma med
  `registry_party_id` inom intervallet kontrolleras mot Citizen men får ingen profil. 5xx från LegalEntity ändrar inte
  `legal_entity_not_found`, och ett svar med företaget sätter den till null ([§5.4]).
- När en kund blir skyddad står namn, adresser, egna kontaktuppgifter och profil kvar, och kunden markeras för
  omindexering. Ingen kontaktrad skrivs om ([§5.3]).
- Alla rader i `registry_person` vars `checked` saknas eller är äldre än körningens start tas med och får
  folkbokföringens nivå och Citizens värden, med ordningsvakten. Citizen anropas en gång per kommun i batchen, med
  radernas `municipality_id`. Svarar Citizen inte lämnas raderna orörda.
- En rad i `registry_person` som ingen rad i `contact` eller `manual_contact_person` pekar på raderas. En rad som någon
  av dem pekar på står kvar. En kontaktrad vars `registry_party_id` saknar rad får en rad med `UNKNOWN`, och läses
  fram till dess som `UNKNOWN`. `EXPLAIN` i ett repository-test visar att kontrollerna använder
  `idx_contact_registry_party_id` och `idx_manual_contact_person_registry_party_id`.
- Citizens värden som är längre än kolumnen kortas och loggas på WARN med längden, utan att batchen faller ([§5.4]).
- En batch som faller på deadlock görs om med `DeadlockRetry` från C2.
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
- En skyddad kunds dokument, också en kund med `UNKNOWN`, innehåller samma fält som en oskyddad kunds, och
  `protectionLevel` ([§6.2]).
- Kontakter, med kontaktpersonernas namn, adresser och kanaler, hamnar i `contacts_ns_<namespace>` respektive
  `contacts_global`.
- Arbetsställenas namn och adresser hamnar i `workplaces`, med högst 500 värden, och telefonnumren i `phones_global`.
- Företagsprofilens SNI-namn och verksamhetsbeskrivning hamnar i `business`. En kund utan profil har inget `business`.
- Telefonnummer i formaten `+46 70-123 45 67`, `0046701234567` och `070-1234567` ger samma värde. Ett telefonvärde
  utan siffror, t.ex. `-`, indexeras inte.
- Engagemangens titel och kategori hamnar i `engagements_ns_<namespace>` och referensen i `references_ns_<namespace>`,
  och för ett engagemang utan namespace i `engagements_global` och `references_global`. Noteringar hamnar i
  `notes_ns_<namespace>`, och noteringar utan namespace i `notes_global`.
- Relayn:
  - körs direkt efter en commit som har markerat kunder, och hoppar över körningen om den redan kör
  - indexerar kunder som finns och tar bort kunder som saknas
  - låter en markering som kom in under indexeringen ligga kvar
  - ökar `attempts` bara på en rad vars dokument avvisas ensamt
  - avbryter körningen utan att ändra `attempts` när OpenSearch inte går att nå eller svarar med timeout, 5xx eller 429
  - ökar `attempts` på en rad vars dokument inte går att bygga, och fortsätter med de andra raderna
  - avslutar körningen efter `search.relay.max-run-time`
- `OpenSearchHealthIndicator` blir `DOWN` när någon rad har `attempts ≥ 5` och ingår inte i `readiness`. Mätvärdet
  `partyoverview.search.failed` visar antalet sådana rader ([§12.1]).
- En markering från `CONTACT_REMOVED` eller `CUSTOMER_DELETED` sätter `priority`, och relayn tar de prioriterade raderna
  före äldre rader utan prioritet ([§6.2]). En senare markering utan prioritet tar inte bort den. `EXPLAIN` i ett
  repository-test visar att relayns två frågor läser `idx_search_index_queue_priority_queued` utan filesort.
- `POST /search/reindex` utan `X-Sent-By` ger 400.
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
  - kontaktuppgifter och kontaktpersoner, med kontaktpersonens adress
  - engagemangens referens, titel och kategori
  - noteringar
  - företagets bransch och verksamhetsbeskrivning, t.ex. "skogsbruk" för SCA Forest Products
  - arbetsställenas namn och adresser, t.ex. "Bollsta sågverk" för SCA Forest Products

  Utan filter träffar den kontaktuppgifter, engagemang och noteringar i alla namespaces, också i ett namespace som
  tillkom efter att indexet byggdes. Med `namespace=A` träffar den A:s och de globala, men aldrig B:s, inte heller
  titeln eller referensen på ett engagemang i B ([§6.2]).

- En skyddad kund hittas på ärendenummer, namn, adresser, kontaktuppgifter, titel och notering, som andra kunder.
  Träffen har kundens `protectionLevel`, `classified` och `protectedNR` från databasen, också när dokumentet är
  inaktuellt ([§6.2]).

- En sökning på namnet på en skyddad kontaktperson hos ett oskyddat företag ger företaget med
  `hasProtectedContacts = true`. En kontaktperson med `UNVERIFIED` ger inte `true`, men en med `UNKNOWN` gör det. En
  kontaktperson som filtret utesluter påverkar inte värdet, och en ändrad nivå i `registry_person` syns i nästa sökning
  utan omindexering. Nivåerna läses med en fråga per sida.

- Ett telefonnummer hittas oavsett format, också när det bara finns i en kontaktperson i ett namespace och sökningen
  saknar filter. En fråga utan siffror tar inte med telefonfälten, med och utan filter ([§6.2]).

- AND gäller över alla fält med och utan filter: "Anna Sundsvall" träffar en kund där Anna finns i en kontaktperson i
  namespace X och Sundsvall i kundens adress, både utan filter och med `namespace=X`.

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

- En OpenSearch-container för party-overview i test och produktion, uppsatt som SupportManagements, tills ett riktigt
  kluster finns ([beslut 38]).
- Inloggningen som sealed secrets.

**Acceptans:**

- party-overview når containern i varje miljö.
- Bara party-overview och drift har åtkomst.

## T1 — Köer och exchanges i driftkonfigurationen

**Bygg:**

- Publicerarnas exchanges i deras egen driftkonfiguration, och party-overviews köer i party-overviews, enligt [§3.2]:
  quorum-köer med `x-single-active-consumer: true` som argument, dead letter-köer, och en policy per kö med
  `dead-letter-exchange`, `dead-letter-routing-key`, `delivery-limit: 5`, `dead-letter-strategy: at-least-once` och
  `overflow: reject-publish`.
- AMQP-användare: publicerarna får write på sin exchange, och party-overview får read på sina köer. Ingen får
  configure.

**Acceptans:**

- Köerna skapas med `x-queue-type: quorum` och `x-single-active-consumer: true` och har inga andra argument.
- Varje kö har sin policy som effektiv policy, och ingen dead letter-kö har den. Det syns i
  `rabbitmqctl list_queues name policy effective_policy_definition` i testmiljön.
- En ändrad `delivery-limit` i policyn gäller för den befintliga kön utan att den skapas om. Det provas en gång i
  testmiljön.
- Dead letter-köerna har ingen TTL. Bara drift och utvecklare har åtkomst ([beslut 31]).
- party-overviews AMQP-användare kan göra en passiv deklaration av sina köer och dead letter-köer ([§12.1]).

## C11 — RabbitMQ-lyssnare och `MessageIT`

**Bygg:**

- `MessageListener` med en direct-container per kö i `integration.sources` ([beslut 68]), registrerade med
  `RabbitListenerConfigurer`, och källan från `CONSUMER_QUEUE` ([§9]).
- `DeadLetterHealthIndicator` enligt [§12.1].
- `MessageIT` enligt [§10], utom fallet med ändringskön, som C14 lägger till.

**Acceptans:**

- Ett meddelande per väg (alla objekttyper och operationer) går hela vägen från kön till databasen.
- Ett meddelande som ligger i omförsök på en kö hindrar inte att ett meddelande på en annan kö tillämpas.
- Ett ogiltigt meddelande hamnar i sin dead letter-kö, och likaså ett meddelande utan `message-id` ([§3.1]).
- Varje meddelande får starterns loggrad och request-id, även med containrar som registreras via
  `RabbitListenerConfigurer`.
- Det finns ingen `@ConditionalOnProperty` på lyssnaren och ingen `@EnableRabbit`.
- `DeadLetterHealthIndicator` finns i `/actuator/health` när RabbitMQ är påslaget. Den är `DOWN` med kö och antal i
  detaljerna när en dead letter-kö har meddelanden eller saknas, eller när en huvudkö saknas, och `UP` annars. Den
  ingår inte i `liveness` eller `readiness`.
- Saknas en kö när tjänsten startar, startar den ändå. REST-API:t svarar, liveness blir aldrig `BROKEN`, och
  `DeadLetterHealthIndicator` är `DOWN` med köns namn. När kön skapas börjar lyssnaren konsumera utan omstart. Samma
  sak gäller när AMQP-användaren saknas ([§12]).
- Med `dept44.rabbitmq.enabled=false` finns ingen `DeadLetterHealthIndicator`, och tjänsten startar.
- `DeadLetterHealthIndicator` uppdaterar `partyoverview.deadletter.messages` och `partyoverview.queue.missing` per kö
  varje gång den körs ([§12.1]).
- README beskriver hur dead letters hanteras, och att kontaktpersonsrollerna sätts innan lyssnarna slås på ([§12]).

## SM1 — SupportManagement: outbox, relay och publicering

**Bygg:** enligt [§11.1] och [§11.2].

**Acceptans:**

- `objectId` är `ErrandEntity.id`, och `reference` är `errandNumber` ([§11.2]).
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
- Relayn läser objektets väntande rader före tillståndet, utan lås, och håller ingen transaktion öppen medan brokern
  bekräftar ([§11.1]). En ändring som committas mellan läsningarna ger ett meddelande med den nya versionen, och
  ändringens egen rad publiceras i nästa meddelande med samma version och sina aktiviteter. Ett IT visar att en
  skrivning till objektet inte väntar medan relayn publicerar.
- Varje aktivitet har ett UUID som sparas på outbox-raden, och samma rad publicerad två gånger ger samma id:n ([§11.1]).
- E-post, sms och webbmeddelanden till och från ett ärende ger ingen outbox-rad, så länge ärendet självt inte ändras
  ([beslut 55]).
- Ett namespace med `accessControl = true` kan inte slå på publiceringen. Slås åtkomstkontrollen på för ett namespace
  som publicerar, stängs publiceringen av ([beslut 51]).
- Ett utkast ger ingen outbox-rad, och ett inskickat utkast publiceras som `UPDATE` ([beslut 26]). Hård radering och
  purge publiceras som `DELETE` med versionen från outbox-raden.
- En health-indikator blir `DOWN` när den äldsta väntande outbox-raden är för gammal ([§11.1]).
- SM exponerar sitt AsyncAPI-dokument enligt det gemensamma formatet.
- En ändring som rullas tillbaka publiceras inte. En publicering som misslyckas ligger kvar i outboxen och skickas vid
  nästa körning.

## SM2 — SupportManagement: grundladdning

**Bygg:**

- En `JobType` som går igenom ett namespace med keyset och SM:s jobbvakt, enligt [§11.2].

**Acceptans:**

- Alla ärenden i namespaces med opt-in publiceras utan aktiviteter.
- Körs jobbet med ett namespace utan opt-in som parameter publiceras namespacets ärenden som `DELETE` ([§11.1]).
- Jobbet startas per namespace, med namespacet i SM:s sökväg för jobb. En andra start för samma namespace medan
  jobbet är aktivt ger 409.
- Når jobbet ett utkast publiceras det som `DELETE` ([beslut 26]).
- Pågående ändringar publiceras i rätt ordning per ärende.

## CD1 — Casedata: outbox, relay och publicering

**Bygg:** enligt [§11.1] och [§11.5].

**Acceptans:**

- `objectId` är `ErrandEntity.id` som sträng, och `reference` är `errandNumber` ([§11.5]).
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
- Relayn läser objektets väntande rader före tillståndet, utan lås, och håller ingen transaktion öppen medan brokern
  bekräftar ([§11.1]). En ändring som committas mellan läsningarna ger ett meddelande med den nya versionen, och
  ändringens egen rad publiceras i nästa meddelande med samma version och sina aktiviteter. Ett IT visar att en
  skrivning till objektet inte väntar medan relayn publicerar.
- Varje aktivitet har ett UUID som sparas på outbox-raden, och samma rad publicerad två gånger ger samma id:n ([§11.1]).
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

- `objectId` är assetens `id`, och `reference` är `assetId` ([§11.3]).
- En asset med status `DRAFT` publiceras som `DELETE`, och när den aktiveras som `UPDATE` med högre version
  ([beslut 67]).
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
- Relayn läser objektets väntande rader före tillståndet, utan lås, och håller ingen transaktion öppen medan brokern
  bekräftar ([§11.1]). En ändring som committas mellan läsningarna ger ett meddelande med den nya versionen, och
  ändringens egen rad publiceras i nästa meddelande med samma version och sina aktiviteter. Ett IT visar att en
  skrivning till objektet inte väntar medan relayn publicerar.
- Varje aktivitet har ett UUID som sparas på outbox-raden, och samma rad publicerad två gånger ger samma id:n ([§11.1]).
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

- `objectId` är `contractId`, som contracts `GET` tar, och inte det interna `id` ([§11.4]).
- Ett kontrakt med status `DRAFT` publiceras som `DELETE`, och när det lämnar `DRAFT` som `UPDATE` med högre version
  ([beslut 67]).
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
- Relayn läser objektets väntande rader före tillståndet, utan lås, och håller ingen transaktion öppen medan brokern
  bekräftar ([§11.1]). En ändring som committas mellan läsningarna ger ett meddelande med den nya versionen, och
  ändringens egen rad publiceras i nästa meddelande med samma version och sina aktiviteter. Ett IT visar att en
  skrivning till objektet inte väntar medan relayn publicerar.
- Varje aktivitet har ett UUID som sparas på outbox-raden, och samma rad publicerad två gånger ger samma id:n ([§11.1]).
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

## T3 — Ändringskön i driftkonfigurationen

**Bygg:**

- party-overviews kö för ändringar av företag och personer, bunden till exchangen hos köns förvaltare ([§3.2], F2).
  Kön är en quorum-kö utan single-active-consumer, med DLQ och en egen policy som för källornas köer ([§3.2]).
- party-overview får read på kön.

**Acceptans:**

- Kön skapas med `x-queue-type: quorum` som enda argument, har sin policy som effektiv policy och tar emot
  förvaltarens meddelanden i testmiljön.
- Dead letter-kön har ingen TTL, och party-overviews AMQP-användare kan göra en passiv deklaration av den ([§12.1]).

## C14 — Företagsprofilen från ändringskön

**Bygg:**

- `RegistryChangeListener` med en egen listener-container för `integration.registry-changes.queue` ([§9], [§12]).
- Mappningen från köns format (F2) till partyId, partens typ och händelsetyp ([§5.5]).
- Ändringskön i `DeadLetterHealthIndicator`. Lyssnaren och kontrollen av dead letter-kön finns bara när
  `integration.registry-changes.queue` har ett värde ([§12.1]).

**Acceptans:**

- Ett meddelande om ett företag som inte är kund i någon kommun ger inget anrop till LegalEntity.
- Ett meddelande om ett företag som är kund i två kommuner ger ett anrop till LegalEntity och en profil per kommun,
  skriven i en transaktion per kund och loggad som `PROFILE_UPDATED` med kanalen `MESSAGE` ([§5.5]).
- Ett företag som är skyddat i en kommun får sin profil där, som i andra kommuner.
- Två meddelanden om samma företag i omvänd ordning, och samma meddelande två gånger, ger samma profil som det
  senaste anropet till LegalEntity.
- 404 från LegalEntity lämnar profilen orörd, skriver `PROFILE_NOT_FOUND` i den tekniska loggen och loggar WARN utan
  id ([§8.1]).
- När LegalEntity inte svarar, svarar 429 eller 408, eller har öppen circuit breaker, är det ett avbrott: meddelandet
  läggs tillbaka i kön och hamnar efter `delivery-limit` i dead letter-kön. 429 och 408 görs om till ett avbrott, som
  när partyId slås upp. 5xx görs om i lyssnaren och dead-letteras sedan direkt ([§3.7]).
- Ett meddelande om en person ger inget anrop till LegalEntity (C15 kontrollerar personen).
- Ett meddelande utan partyId eller partens typ, eller vars id inte är ett UUID, dead-letteras direkt ([§5.5]).
- En profil med samma `content_hash` som den sparade flyttar bara fram `fetched`, utan loggrad och utan omindexering.
- Anropet till LegalEntity görs med kommunen för den första träffen i id-ordning.
- Tjänsten startar, och `DeadLetterHealthIndicator` är `UP`, när `integration.registry-changes.queue` saknar värde.
- `DeadLetterHealthIndicator` blir `DOWN` när ändringsköns dead letter-kö inte är tom.
- `MessageIT` ([§10]) får ett fall med LegalEntity stubbad med svaret för SCA Forest Products: ett meddelande om
  företaget ger profilen enligt C3, och kunden markeras för omindexering.

## C15 — Skyddskontroll från ändringskön

**Bygg:**

- Hanteringen av meddelanden om personer i `RegistryChangeListener` enligt [§5.5], med samma kontroll per kund som
  `ProtectionRefreshScheduler` ([§5.4]).

**Acceptans:**

- Ett meddelande om en person som varken är kund, äger en enskild firma som är kund eller finns i `registry_person`
  ger inget anrop till Citizen.
- En privatkund som har fått skyddad folkbokföring får `PROTECTION_CHANGED` med kanalen `MESSAGE`, den nya nivån och
  Citizens värden. Namn, adresser och egna kontaktuppgifter står kvar, och kunden markeras för omindexering.
- En enskild firma vars ägare har fått skydd får ägarens nivå och behåller sin företagsprofil.
- En person i `registry_person` som har fått skydd får den nya nivån och Citizens värden där, och nästa läsning visar
  nivån på personens kontaktrader i alla kommuner, också där firman som personen äger är kontaktperson ([§5.3]).
- Ett svar från Citizen som är äldre än det som redan har skrivits ändrar ingenting, varken på kunden eller i
  `registry_person` ([§5.4]).
- En person som är kund i två kommuner ger ett anrop till Citizen och en kontroll per kommun, i en transaktion per
  kund.
- När Citizen inte svarar, eller svarar 5xx, 429 eller 408, görs meddelandet om och hamnar till sist i dead letter-kön.
  401 dead-letteras direkt, som i [§3.7].

## C16 — Kontaktpersoner via REST

**Bygg:**

- `ContactPersonResource` och `ContactPersonService` med listan, `POST`, `GET`, `PATCH` och `DELETE` enligt [§6.1],
  och `manual_contact_person` enligt [§4.3] och [§5.3].
- Mappningen av kontaktpersonens partyId eller legalId till `registry_party_id`, och skrivningen i `registry_person`
  ([§5.3]).
- Kontaktpersonerna i kundens `contactPersons[]` enligt namespace-filtret, i sökindexet och i kontrollen av
  automatisk radering ([§3.6], [§6.2]).

**Acceptans:**

- En kontaktperson med namespace A finns i kundens `contactPersons[]` utan filter och med `namespace=A`, men inte med
  `namespace=B` och inte i en annan kommun. En kontaktperson utan namespace finns med i alla fall. Ingen av dem slås
  ihop med en kontaktperson från en källa.
- Listan utan filter ger kundens alla kontaktpersoner från REST. Med `namespace=A&namespace=B` ger den A:s, B:s och de
  globala, men inte C:s. Ett ogiltigt namespace i filtret ger 400, och en okänd kund ger 404. Varje kontaktperson har
  sitt `namespace`, eller inget för en global.
- `PATCH` med ett `namespace` i kroppen ändrar inte kontaktpersonens namespace.
- `POST` utan `name`, med `name` över 255 tecken, `role` över 64 tecken, fler än 10 kanaler, en kanal med okänd typ,
  ett ogiltigt `partyId` eller ett `legalId` med fel format ger 400. `partyId` eller `legalId` utan `partyType`, eller
  båda samtidigt, ger 400.
- Ett `legalId` mappas till partyId och finns aldrig i någon tabell, loggrad, svar eller Problem-detail. Finns numret
  inte blir svaret 400.
- En person får sig själv som `registry_party_id`, en enskild firma ägaren, ett annat företag
  `fallback_protection = NONE` och en kontaktperson utan id `UNVERIFIED`. Varje `registry_party_id` har en rad i
  `registry_person` efter commit. Citizen anropas bara när raden saknas eller har `UNKNOWN`, och svarar Citizen inte
  skapas raden med `UNKNOWN`.
- En kontaktperson som pekar på en skyddad person returneras med den skyddade nivån och Citizens värden, och kunden får
  `hasProtectedContacts = true` i en sökning utan filter och med kontaktpersonens namespace ([§6.2]).
- En kontaktperson på en okänd kund skapar kunden som en notering gör ([§7.2]). Svarar Citizen eller LegalEntity inte
  blir svaret 503, och ingenting sparas.
- `CONTACT_PERSON_CREATED`, `CONTACT_PERSON_UPDATED` och `CONTACT_PERSON_DELETED` loggas med kanalen `REST` och
  `performed_by`, och kunden markeras för omindexering ([§8.1]).
- `PATCH` och `DELETE` kräver `If-Match` och ger 428, 412 och 404 som för en notering ([§6.1]). `PATCH` ändrar bara
  fälten i kroppen som inte är null och ändrar aldrig vem kontaktpersonen är. `created_by` ändras aldrig, och
  `modified_by` sätts vid varje ändring.
- En kund som bara har kontaktpersoner från REST raderas inte när dess sista engagemang försvinner, och inte när dess
  sista notering raderas. När den sista kontaktpersonen från REST raderas och kunden inget annat har kvar raderas
  kunden ([§3.6]).
- `DELETE` på en kund och `CUSTOMER DELETE` raderar personens kontaktpersoner från REST hos andra kunder i kommunen, i
  alla namespaces, och loggar `CONTACT_REMOVED` för de kunderna ([§3.6]). En automatisk radering av personen som
  kund lämnar dem orörda.
- En kund som på det sättet förlorar sin sista kontaktperson från REST, och inget annat har kvar, står kvar tills den
  nattliga kontrollen raderar den med `CUSTOMER_DELETED` och kanalen `SCHEDULER` ([beslut 69]).
- `DELETE` på en kund som bara finns som kontaktperson från REST hos en annan kund raderar raden och ger 204.
- En kontaktperson med namespace hamnar i `contacts_ns_<namespace>`, med telefonnumren i `phones_ns_<namespace>`, och
  en utan namespace i `contacts_global` och `phones_global` ([§6.2]).
- Om ett meddelande raderar kunden medan en kontaktperson skapas blir det inget FK-fel, eftersom kunden är låst
  ([§3.6]).
- Varje skrivning av en kontaktperson låser kunden först och körs med READ COMMITTED ([§3.6]). En skrivning som faller
  på deadlock görs om med `DeadlockRetry` från C2.

## T4 — Påminnelser i Spring Boot Admin

**Bygg:**

- En `RemindingNotifier` runt Slack-notifieraren i spring-boot-admin, som påminner så länge en instans är `DOWN` eller
  `OFFLINE` ([beslut 72]).

**Acceptans:**

- En instans som är `DOWN` längre än påminnelseintervallet ger en ny notifiering i Slack, och en instans som har blivit
  `UP` ger inga fler.
- Intervallet är konfigurerbart och avstämt med dem som tar emot larmen. Påminnelserna gäller alla tjänster i Spring
  Boot Admin.

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
[beslut 61]: 00-beslutslogg.md#beslut-61-noteringar-ändras-med-optimistisk-låsning
[beslut 68]: 00-beslutslogg.md#beslut-68-lyssnarna-använder-direct-containern
[beslut 67]: 00-beslutslogg.md#beslut-67-utkast-publiceras-inte-i-party-assets-och-contract
[beslut 69]: 00-beslutslogg.md#beslut-69-den-nattliga-kontrollen-raderar-kunder-utan-innehåll
[beslut 72]: 00-beslutslogg.md#beslut-72-larmen-påminner-och-varje-signal-har-ett-mätvärde

