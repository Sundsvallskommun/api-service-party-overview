# 6. REST-API

Del av [arkitekturen för party-overview](README.md).

Alla sökvägar validerar med `@ValidMunicipalityId` och `@ValidUuid`. Ingen sökväg har namespace ([§5.1]):

- Vid läsning är `namespace` en valfri query-parameter som kan anges flera gånger, t.ex. `?namespace=A&namespace=B`.
  Utan den returneras allt. Med den returneras de angivna namespacena och det som saknar namespace. Det gäller
  kunden, uppslaget, sökningen, engagemangen, noteringarna och kontaktpersonerna.
- När en notering eller kontaktperson skapas är `namespace` ett valfritt fält i kroppen. Saknas det blir den global.
  Namespace går inte att ändra efteråt.
- Fältet i kroppen valideras med `@ValidNamespace(nullable = true)` och varje värde i filtret med `@ValidNamespace`. Ett
  ogiltigt värde ger 400.

Listor returneras som `Page<T>` med `@ParameterObject Pageable`, och sidstorleken är högst 100. Varje lista sorteras
till sist på `id`, så att sidorna är stabila när sorteringsnyckeln är lika. Fel returneras som Problem. `@ValidUuid`
sätts inte på id-fält som är READ_ONLY.

`X-Sent-By` krävs för alla anrop som ändrar något: noteringar och kontaktpersoner (`POST`, `PATCH`, `DELETE`),
kundradering, radering av den tekniska loggen, kontaktpersonsrollerna och `POST /search/reindex`. Läsningar kräver den
inte, inte heller uppslaget och sökningen, som är `POST` bara för att hålla numret och frågan borta från URL:en.

Headern läses med dept44:s `Identifier`, och formatet är `<värde>; type=<typ>`, t.ex. `joe01doe; type=adAccount`. Ett
ändrande anrop ger 400 om headern saknas, om den inte går att tolka, t.ex. för att `type=` saknas, eller om värdet är
längre än 255 tecken. Det tolkade värdet, `Identifier.getValue()`, sparas som `created_by` och `modified_by` på
noteringar och kontaktpersonsroller och som `performed_by` i historiken och den tekniska loggen ([§4.3]). `parse` tar
bort alla blanksteg, så `Anna Berg; type=custom` sparas som `AnnaBerg`, och en okänd typ, också en med fel skiftläge,
blir `CUSTOM`.

## 6.1 Endpoints

|                                 Metod och sökväg                                 |                                                                                                                                                                      Beskrivning                                                                                                                                                                      |                                                           Svar                                                            |
|----------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `GET /{municipalityId}/customers/{partyId}?namespace=`                           | Kunden, med kontaktuppgifter och kontaktpersoner enligt filtret ([§5.1])                                                                                                                                                                                                                                                                              | 200, 400, 404                                                                                                             |
| `POST /{municipalityId}/customers/lookup?namespace=`                             | Slår upp en kund på personnummer eller organisationsnummer. Kropp: `legalId` och `partyType`. Numret mappas till partyId via Citizen eller LegalEntity ([§7.1]) och kastas sedan. Svaret är detsamma som för `GET` på kunden.                                                                                                                         | 200, 400, 404, 503                                                                                                        |
| `POST /{municipalityId}/customers/search?namespace=`                             | Fritextsökning bland kunderna, i kontaktuppgifter och noteringar enligt filtret. Kropp: `query` och `partyType` ([§6.2]).                                                                                                                                                                                                                             | 200, 400, 503, 504                                                                                                        |
| `POST /search/reindex`                                                           | Bygger om sökindexet från databasen, asynkront ([§6.2])                                                                                                                                                                                                                                                                                               | 202, 400, 409, 503                                                                                                        |
| `GET /{municipalityId}/customers/{partyId}/engagements?namespace=&objectType=`   | Engagemang. Filter `namespace` och `objectType`. Sorteras förvalt på `sourceModified,desc`.                                                                                                                                                                                                                                                           | 200, 400, 404                                                                                                             |
| `GET /{municipalityId}/customers/{partyId}/workplaces`                           | Företagets arbetsställen, med huvudarbetsstället först och sedan efter namn ([§5.5]). En kund utan profil ger en tom sida.                                                                                                                                                                                                                            | 200, 400, 404                                                                                                             |
| `GET /{municipalityId}/customers/{partyId}/engagements/{engagementId}`           | Ett engagemang                                                                                                                                                                                                                                                                                                                                        | 200, 400, 404                                                                                                             |
| `GET /{municipalityId}/customers/{partyId}/engagements/{engagementId}/history`   | Historik, förvalt `occurredAt,desc`                                                                                                                                                                                                                                                                                                                   | 200, 400, 404                                                                                                             |
| `GET /{municipalityId}/customers/{partyId}/notes?namespace=`                     | Kundens noteringar enligt filtret, förvalt `created,desc`                                                                                                                                                                                                                                                                                             | 200, 400, 404                                                                                                             |
| `POST /{municipalityId}/customers/{partyId}/notes`                               | Skapar en notering och vid behov kunden ([§7.2]). Kropp: `namespace` (valfritt, saknas det blir noteringen global), `subject` högst 255 tecken och `body` högst 10 000. `createdBy` sätts från `X-Sent-By`.                                                                                                                                           | 201 med Location, 400, 404 om parten är okänd, 503 om partytypen inte går att avgöra                                      |
| `GET /{municipalityId}/customers/{partyId}/notes/{noteId}`                       | En notering, med versionen som `ETag`                                                                                                                                                                                                                                                                                                                 | 200, 400, 404                                                                                                             |
| `PATCH /{municipalityId}/customers/{partyId}/notes/{noteId}`                     | Ändrar `subject` eller `body`. Namespace går inte att ändra. `modifiedBy` sätts från `X-Sent-By`. Kräver `If-Match` ([beslut 61]).                                                                                                                                                                                                                    | 200, 400, 404, 412, 428                                                                                                   |
| `DELETE /{municipalityId}/customers/{partyId}/notes/{noteId}`                    | Raderar noteringen och dess historik. Har kunden sedan inget annat kvar raderas kunden och personens kontaktrader hos andra kunder ([§3.6]). Kräver `If-Match`.                                                                                                                                                                                       | 204, 400, 404, 412, 428                                                                                                   |
| `GET /{municipalityId}/customers/{partyId}/notes/{noteId}/history`               | Noteringens historik                                                                                                                                                                                                                                                                                                                                  | 200, 400, 404                                                                                                             |
| `GET /{municipalityId}/customers/{partyId}/contact-persons?namespace=`           | Kundens kontaktpersoner som har lagts till via REST, enligt filtret ([§5.3]). Sorteras förvalt på `name,asc`.                                                                                                                                                                                                                                         | 200, 400, 404                                                                                                             |
| `POST /{municipalityId}/customers/{partyId}/contact-persons`                     | Lägger till en kontaktperson, och vid behov kunden ([§5.3], [§7.2]). Kropp: `namespace` (valfritt, saknas det blir kontaktpersonen global), `name` (krav, högst 255 tecken), `role` (högst 64), `contactChannels[]` (högst 10), `address`, och `partyId` eller `legalId`, med `partyType` när något av dem anges. `createdBy` sätts från `X-Sent-By`. | 201 med Location, 400, 404 om kundens part är okänd, 503 om partytypen eller kontaktpersonens partyId inte går att avgöra |
| `GET /{municipalityId}/customers/{partyId}/contact-persons/{contactPersonId}`    | En kontaktperson som har lagts till via REST, med versionen som `ETag`                                                                                                                                                                                                                                                                                | 200, 400, 404                                                                                                             |
| `PATCH /{municipalityId}/customers/{partyId}/contact-persons/{contactPersonId}`  | Ändrar `name`, `role`, `contactChannels[]` eller `address`. Namespace och vem kontaktpersonen är går inte att ändra. `modifiedBy` sätts från `X-Sent-By`. Kräver `If-Match`.                                                                                                                                                                          | 200, 400, 404, 412, 428                                                                                                   |
| `DELETE /{municipalityId}/customers/{partyId}/contact-persons/{contactPersonId}` | Raderar kontaktpersonen. Har kunden sedan inget annat kvar raderas den ([§3.6]). Kräver `If-Match`.                                                                                                                                                                                                                                                   | 204, 400, 404, 412, 428                                                                                                   |
| `DELETE /{municipalityId}/customers/{partyId}`                                   | Raderar kunden med allt den äger och loggar `CUSTOMER_DELETED`. Raderar också personens kontaktrader hos andra kunder, även de som har lagts till via REST och även när personen bara är kontaktperson, och loggar `CONTACT_REMOVED` för de kunderna ([§5.3]).                                                                                        | 204, 400, 404 när varken kund eller kontaktrader finns                                                                    |
| `GET /{municipalityId}/customers/{partyId}/technical-log`                        | Den tekniska loggen, förvalt `created,desc`. Fungerar även efter radering.                                                                                                                                                                                                                                                                            | 200, 400                                                                                                                  |
| `DELETE /{municipalityId}/technical-log?partyId=&createdBefore=`                 | Raderar rader i den tekniska loggen. Minst ett av filtren krävs, och båda kan kombineras ([§8.2]).                                                                                                                                                                                                                                                    | 204, 400                                                                                                                  |
| `GET /{municipalityId}/contact-person-roles`                                     | Kontaktpersonsroller per källa                                                                                                                                                                                                                                                                                                                        | 200, 400                                                                                                                  |
| `PUT /{municipalityId}/contact-person-roles/{source}`                            | Ersätter källans roller (kropp: lista med roller). En okänd källa ger 400.                                                                                                                                                                                                                                                                            | 204, 400                                                                                                                  |

- En notering, kontaktperson eller ett engagemang som inte tillhör kunden i sökvägen ger 404. De hämtas med sitt id
  oavsett namespace. Bara kontaktpersoner som har lagts till via REST har ett id.
- Ett `legalId` för en kontaktperson tas bara i kroppen, mappas till partyId och sparas aldrig ([§7.1]). Finns numret
  inte blir svaret 400, och Problem-detail innehåller aldrig numret.
- `PATCH` och `DELETE` på en notering eller en kontaktperson kräver `If-Match` med dess `ETag` ([beslut 61]):
  - `ETag` är versionen som en stark ETag inom citattecken, t.ex. `"3"`. `GET`, `POST` och `PATCH` returnerar den, och
    `PATCH` returnerar versionen efter ändringen, så servicen flushar innan svaret byggs.
  - `If-Match` kan ha flera ETags, och det räcker att en stämmer. Värdet jämförs också när det kommer som svag ETag
    (`W/"3"`), eftersom en proxy kan göra om en stark ETag till svag.
  - Headern deklareras som valfri i controllern. Saknas den, eller är den `*`, blir svaret 428. Stämmer ingen ETag med
    den sparade versionen, eller går värdet inte att tolka, blir svaret 412.
  - Kunden låses först och noteringen eller kontaktpersonen läses sedan ([§3.6]), så av två samtidiga ändringar med
    samma version lyckas bara den ena. Ett `OptimisticLockingFailureException` ger ändå 412.
  - OpenAPI-specen beskriver `If-Match` och `ETag`.
- `PATCH` ändrar bara de fält som finns i kroppen och inte är null.
- `DELETE` på kund, den tekniska loggen och kontaktpersonsrollerna gäller hela kommunen. De finns i samma interna API
  som resten ([beslut 30]).
- Tillåtna sorteringsfält är `sourceModified` och `sourceCreated` för engagemang, `occurredAt` för historik, `created`
  och `modified` för noteringar, `name`, `created` och `modified` för kontaktpersoner och `created` för den tekniska
  loggen. Ett annat fält ger 400. Arbetsställena har en fast ordning: huvudarbetsstället först, sedan efter namn, där
  ett arbetsställe utan namn kommer sist, och sist efter `id`.
- Uppslaget på personnummer tar numret i kroppen och aldrig i URL:en. Om numret inte finns, eller om kunden inte
  finns i party-overview, blir svaret 404. Svarar Citizen eller LegalEntity inte, eller med 5xx, blir svaret 503.
  Problem-detail innehåller aldrig numret.
- Responsmodeller:
  - `Customer`: `partyId`, `partyType`, `protectionLevel`, `classified`, `protectedNR`, `name`, `addresses[]`,
    `contactChannels[]` och `contactPersons[]`, och för ett företag `enterpriseProfile`. `classified` och
    `protectedNR` är Citizens värden ([§5.4]).
  - `Address`: `type`, `careOf`, `street`, `postalCode`, `city` och `country` ([§5.2])
  - `ContactPerson`: `id`, `version`, `namespace`, `name`, `role`, `contactChannels[]`, `address`, `protectionLevel`,
    `classified` och `protectedNR` ([§5.3]). `namespace` saknas för en global kontaktperson. `id` och `version` finns
    bara för en kontaktperson som har lagts till via REST. Den har också `created`, `createdBy`, `modified` och
    `modifiedBy`.
  - `Note`: `id`, `subject`, `body`, `version`, `created`, `createdBy`, `modified` och `modifiedBy`
  - `Workplace`: `cfarNumber`, `name`, `mainWorkplace`, `addresses[]`, `phoneNumber`, `employeeSize` (`code` och
    `name`) och `sniCodes[]` ([§5.5])
  - `EnterpriseProfile`: `legalForm`, `companyForm`, `registeredOfficeMunicipality`, `registered`, `deregistered`,
    `deregistrationReason`, `proceedings[]`, `employeeSize`, `revenueSize`, `revenueYear`, `workplaceCount`,
    `businessDescription`, `email`, `phoneNumber`, `sniCodes[]` och `fetched` ([§5.5]). Koder med namn, som
    `legalForm` och `employeeSize`, är objekt med `code` och `name`. `sniCodes[]` är företagets framräknade koder i
    ordning, och `proceedings[]` har `code`, `name` och `fromDate`. Profilen saknas för en privatkund och för ett
    företag som ännu inte har någon profil.
  - `CustomerSearchHit` ([§6.2]), `Engagement`, `HistoryEntry`, `TechnicalLogEntry` och `ContactPersonRoles`

  `openapi.yaml` regenereras och verifieras av `OpenApiSpecificationIT`.

## 6.2 Kundsökning (OpenSearch)

Lösningen följer SupportManagement med två skillnader: indexeringen går via en kö ([beslut 25]), och frågespråket är
begränsat ([beslut 25b]). Förebilder i SM finns i `integration/db/search/` och `service/search/` på `origin/main`.

**Beroenden och konfiguration**

- `hibernate-search-mapper-orm` och `hibernate-search-backend-elasticsearch` (8.4.1.Final, som SM) mot OpenSearch.
  Testberoendet är `opensearch-testcontainers`. dept44 har inget eget stöd för OpenSearch.
- Konfigurationen ligger under `spring.jpa.properties.hibernate.search` som i SM, med två undantag:
  - `indexing.listeners.enabled: false`
  - `schema_management.strategy: none`
- Som i SM är versionen låst (`backend.version: opensearch:3.6`, `backend.version_check.enabled: false`). Annars frågar
  Hibernate Search klustret vid start, och då startar inte tjänsten, varken REST eller lyssnarna, när sökningen är
  påslagen och OpenSearch inte svarar.
- Klientens tidsgränser sätts uttryckligen ([§12]), så att en OpenSearch som hänger inte håller relayn kvar.
- Värdar, protokoll och inloggning sätts per miljö via sealed secrets. Sökningen är avstängd som standard.
- Varje miljö har en egen OpenSearch-container, som SupportManagement, tills ett riktigt kluster finns ([beslut 38],
  T2).
- Indexet heter `customer` och innehåller alla kommuner, med `municipalityId` som filter.

**Indexmodell**

`@Indexed` sitter på `CustomerEntity`, och en `CustomerDocumentBinder` (TypeBinder) bygger dokumentet. Bindaren läser
kundens adresser, kontakter, engagemang, noteringar och företagsprofil genom sessionen, deduplicerade och grupperade per
namespace. Det sker med projektioner, utan att hela entiteter laddas, så `customer` behöver ingen kollektion ([§4.2]).
Grupperingen är en ren funktion som går att enhetstesta.

|                        Fält                        |                                                                                                                            Mappning                                                                                                                             |
|----------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `municipalityId`, `partyType`                      | keyword, filter                                                                                                                                                                                                                                                 |
| `protectionLevel`                                  | keyword, projicerbar                                                                                                                                                                                                                                            |
| `name`                                             | text (`text_sv`: standard, lowercase och light_swedish), plus `name_sort` som keyword                                                                                                                                                                           |
| `careOf`, `street`, `postalCode`, `city`           | text (`text_sv`), från alla kundens adresser                                                                                                                                                                                                                    |
| `contacts_global`, `contacts_ns_<namespace>`       | text via fieldTemplate: kontaktpersoners namn och adresser och kanalvärden, högst 500 värden per fält, de senast ändrade först. Kontaktpersoner från REST hamnar i sitt namespace, eller i `contacts_global` om de saknar namespace.                            |
| `business`                                         | text (`text_sv`): företagsprofilens SNI-namn och verksamhetsbeskrivning ([§5.5])                                                                                                                                                                                |
| `workplaces`                                       | text (`text_sv`): arbetsställenas namn och adresser, högst 500 värden, med huvudarbetsstället först och sedan de största ([§5.5])                                                                                                                               |
| `phones_global`, `phones_ns_<namespace>`           | keyword med normalizern `phone` (`+46` och `0046` blir `0`, och allt utom siffror tas bort), bara kanaler av typen `PHONE`, och i `phones_global` även företagets och arbetsställenas telefonnummer, högst 500 per fält. Ett värde utan siffror indexeras inte. |
| `references_global`, `references_ns_<namespace>`   | keyword med `lowercase_normalizer` via fieldTemplate: engagemangens `reference` och `objectId`, alla. Engagemang utan namespace hamnar i `references_global`.                                                                                                   |
| `engagements_global`, `engagements_ns_<namespace>` | text (`text_sv`) via fieldTemplate: engagemangens titel och kategori, de 1 000 senast ändrade per fält. Engagemang utan namespace hamnar i `engagements_global`.                                                                                                |
| `notes_global`, `notes_ns_<namespace>`             | text (`text_sv`) via fieldTemplate: noteringarnas ämne och text, de 200 senaste per fält. Noteringar utan namespace hamnar i `notes_global`.                                                                                                                    |

- Namespace i fältnamnet skrivs med gemener. Det är säkert, eftersom `@ValidNamespace` bara tillåter `[\w-]{2,32}`.
- Engagemang, kontaktuppgifter och noteringar söks i de globala fälten och i de namespaces som filtret anger, eller i
  alla namespaces när filtret saknas. Sökningen följer samma filter som listorna ([§5.1]).
- En skyddad kund indexeras som andra kunder ([beslut 39]). Skyddade kontaktpersoner indexeras också, och träffen
  visar dem med `hasProtectedContacts` (se *Svar* nedan).
- Personnummer indexeras aldrig.

**Indexering**

1. *Markering:* kunden markeras i samma transaktion som ändringen med
   `insert into search_index_queue … on duplicate key update version = version + 1, attempts = 0, priority =
   priority or ?`. Det sker:

   - varje gång en rad skrivs till den tekniska loggen för kunden, utom `IGNORED_STALE`. Markeringen görs i
     `TechnicalLogService`, så den täcker meddelanden, REST och den nattliga skyddskontrollen utan att varje flöde
     behöver komma ihåg den.
   - när en notering skapas, ändras eller raderas (C8).

   En markering från `CONTACT_REMOVED` eller `CUSTOMER_DELETED` sätter `priority`, så att uppgifter om en raderad
   person försvinner ur indexet före andra ändringar ([§8.1]).

   Markeringarna samlas per transaktion och skrivs före commit i `customer_id`-ordning. Två transaktioner tar därför
   aldrig köns radlås i olika ordning. Det gäller också företag som inte är låsta och vars kontaktrader raderas när en
   person raderas ([§5.3]). Övriga kunder är redan låsta ([§3.3], [§3.6]). Primärnyckeln gör att det högst finns en rad
   per kund, även när OpenSearch ligger nere.

2. *Relay:* `SearchIndexRelay` körs direkt efter varje commit som har markerat kunder, och var 10:e sekund som skyddsnät
   ([beslut 40]).

   - Körningen efter commit görs asynkront via `@TransactionalEventListener(AFTER_COMMIT)` och hoppas över om relayn
     redan kör. Den schemalagda körningen tar då hand om resten.
   - Båda körningarna tar samma ShedLock-lås.
   - En ändring blir sökbar inom någon sekund, eftersom OpenSearchs `refresh_interval` är 1 s.

   Varje körning hämtar högst `search.relay.batch-size` (förval 500) av raderna med `attempts < 5`: först de
   prioriterade och sedan övriga, de äldsta först i båda. Det görs med två frågor, `priority = 1` och sedan
   `priority = 0`, så att båda läser `idx_search_index_queue_priority_queued` i ordning utan filesort ([§4.3]).
   - I en read-only `TransactionTemplate` med `writeSync`-strategi får kunder som finns `addOrUpdate`, och kunder som
   saknas får `purge`.
   - Efter lyckad skrivning körs `delete … where customer_id = ? and version = ?`. En markering som kom in under tiden
   ligger kvar.
   - Om OpenSearch inte går att nå, eller svarar med timeout, 5xx, 429 eller ett `cluster_block`, avbryts körningen.
   `attempts` ändras inte, och nästa körning försöker igen. Ett avbrott är inget fel i dokumenten.
   - Om OpenSearch avvisar enskilda dokument, t.ex. vid ett mappningsfel, körs raderna en och en. En rad som avvisas
   ensam får `attempts + 1`.
   - Ett tillfälligt databasfel (`TransientDataAccessException`) räknas som ett avbrott. Andra fel, t.ex. ett undantag
   när dokumentet byggs, hanteras som ett avvisat dokument, så att en rad som alltid felar inte stoppar kön.
   - Körningen avslutas när den har hållit på i `search.relay.max-run-time` (förval `PT4M`), en bit under låsets
   `shedlock-lock-at-most-for`, så att två relayer aldrig skriver samtidigt.

3. *Larm:* `OpenSearchHealthIndicator`, kopierad från SM, blir `DOWN` när OpenSearch inte svarar eller när någon rad i
   kön har `attempts ≥ 5`. Spring Boot Admin larmar då som i [§12.1]. Indikatorn ingår inte i `readiness`, och den
   visar antalet sådana rader i mätvärdet `partyoverview.search.failed`.

4. *Radering direkt i databasen:* når inte kön ([beslut 25a]). Träffen filtreras bort när den laddas från databasen, och
   dokumentet försvinner vid nästa reindexering. README beskriver att reindexering ska köras efter en sådan radering.

**Reindexering**

`POST /search/reindex` kör MassIndexer asynkront med `dropAndCreateSchemaOnStart` och svarar 202.

- Den tar samma ShedLock-lås som relayn, så relayn pausar och kommer ikapp efteråt, och ombyggnaden kan aldrig skriva
  över nyare data. Om låset redan är taget blir svaret 409. Låset tas med ShedLocks `KeepAliveLockProvider` och
  `search.reindex.lock-at-most-for` (förval `PT5M`), som förlängs så länge ombyggnaden pågår. Dör podden under
  ombyggnaden släpps låset inom fem minuter, men indexet är halvbyggt tills reindexeringen körs igen. README beskriver
  det.
- När ombyggnaden startar tas köns rader bort, eftersom MassIndexer läser allt från databasen. Det gäller även rader med
  `attempts ≥ 5`, så `OpenSearchHealthIndicator` blir `UP` igen. Rader som läggs till under ombyggnaden ligger kvar.
- Sökningen är tom tills ombyggnaden är klar, vilket tar några minuter.
- Reindexering behövs när sökningen slås på första gången, efter en ändrad mappning och efter en radering direkt i
  databasen. Efter en grundladdning kan den köras för att komma ikapp snabbare än relayn.
- `SearchSchemaInitializer` och `SearchAvailability` kopieras från SM. Om sökningen är avstängd eller indexet inte går
  att använda svarar endpointen 503.

**Sök-endpoint:** `POST /{municipalityId}/customers/search?namespace=&page=&size=&sort=`, med `query` och `partyType` i
kroppen ([beslut 46]).

*Parametrar:*

- `query`: `@NotBlank`, `@Size(max = 255)`.
- `partyType`: valfri.
- `namespace`: valfri, och kan anges flera gånger ([§6]).
- `size`: högst 100, som för andra listor. `page × size + size` får vara högst 10 000, annars blir svaret 400.
- `sort`: tom, vilket ger relevans och sedan `name_sort`, eller `name`. Andra värden ger 400.

*Frågan:*

- Filter på `municipalityId`, och på `partyType` om den anges.
- En `must` med en bool av should-klausuler, där minst en ska matcha. Should-klausuler direkt bredvid ett filter är
  valfria i OpenSearch, och då träffar varje sökning alla kunder i kommunen. SM gör likadant med
  `.filter(…).must(query)`.
  - en `simple_query_string` med AND, utan fuzzy, över namn, adress, `business`, `workplaces` och fälten för
    kontakter, engagemang och noteringar, både de globala och namespace-fälten. Alla fält står i samma klausul, så att
    AND gäller över dem ([beslut 25b]).
  - en matchning av telefonnummer och referenser över de globala fälten och namespace-fälten för telefon och
    referenser. Telefonfälten tas bara med när frågan innehåller en siffra, eftersom normalizern annars gör frågan
    till en tom sträng.
- Med filtret är namespace-fälten de angivna namespacenas, t.ex. `contacts_ns_a`, `engagements_ns_a`, `notes_ns_a`,
  `phones_ns_a` och `references_ns_a`, och klausulerna byggs med Hibernate Searchs DSL (`simpleQueryString` och
  `match`). Andra namespaces fält ingår aldrig.
- Utan filter ingår alla namespaces fält, med jokertecken i fältnamnet: `contacts_ns_*`, `engagements_ns_*`,
  `notes_ns_*`, `phones_ns_*` och `references_ns_*`. Fälten från en fieldTemplate går inte att räkna upp i förväg, så
  hela must-klausulen byggs då som ett native-predikat via `ElasticsearchExtension`: en `simple_query_string` med alla
  fält i samma klausul, och en `multi_match` för telefon och referenser, eftersom `match` inte tar jokertecken i
  fältnamn.
- `failAfter(search.timeout)`.

*Träffarna:*

- Hibernate Search laddar träffarna från databasen.
- En kund som har raderats ger ingen träff.

*Svar:* `Page<CustomerSearchHit>` med `partyId`, `partyType`, `protectionLevel`, `classified`, `protectedNR`, `name`,
`addresses[]` och `hasProtectedContacts`, med värdena från databasen ([§5.4]). `hasProtectedContacts` är sant när någon
av kundens kontaktpersoner som omfattas av filtret, eller alla utan filter, har nivån `UNKNOWN`, `SECRECY_MARKING` eller
`PROTECTED_REGISTRATION`, räknad som i [§5.3]. `UNVERIFIED` räknas inte ([beslut 48]). En träff på en skyddad
kontaktpersons namn visar därför att träffen rör en skyddad person, också när kunden själv är oskyddad. Nivåerna läses
med en fråga för hela sidan.

*Fel:* timeout ger 504, och en OpenSearch som inte svarar ger 503. `simple_query_string` ger inga parsfel.

*Loggning:* frågesträngen loggas aldrig.

[§4.2]: 04-datamodell.md#42-gemensamt-för-alla-tabeller
[§5.1]: 05-synlighet-och-kunddata.md#51-synlighet-per-namespace
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§6.2]: #62-kundsökning-opensearch
[§7.1]: 07-integrationer.md#71-uppslag-av-partyid
[§7.2]: 07-integrationer.md#72-citizen-och-legalentity
[§8.2]: 08-teknisk-logg.md#82-radering-av-den-tekniska-loggen
[§12.1]: 12-drift.md#121-larm-för-dead-letter-köer
[beslut 25]: 00-beslutslogg.md#beslut-25-fritextsökning-med-hibernate-search-och-en-indexeringskö
[beslut 25a]: 00-beslutslogg.md#beslut-25a-ingen-databastrigger-för-raderingar-i-sökindexet
[beslut 25b]: 00-beslutslogg.md#beslut-25b-begränsad-frågesyntax-och-kontaktfält-per-namespace
[beslut 30]: 00-beslutslogg.md#beslut-30-de-administrativa-endpoints-ligger-i-samma-api
[beslut 38]: 00-beslutslogg.md#beslut-38-egen-opensearch-container-per-miljö
[beslut 39]: 00-beslutslogg.md#beslut-39-vad-som-är-sökbart
[beslut 40]: 00-beslutslogg.md#beslut-40-sökindexets-relay-körs-efter-commit-och-var-10e-sekund
[beslut 46]: 00-beslutslogg.md#beslut-46-sökfrågan-skickas-i-kroppen
[§3.3]: 03-meddelanden.md#33-flödet-genom-tjänsten
[§3.6]: 03-meddelanden.md#36-kundradering
[§12]: 12-drift.md
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§5.2]: 05-synlighet-och-kunddata.md#52-namn-och-adress
[§4.3]: 04-datamodell.md#43-tabellerna
[§5.5]: 05-synlighet-och-kunddata.md#55-företagsuppgifter
[beslut 61]: 00-beslutslogg.md#beslut-61-noteringar-ändras-med-optimistisk-låsning
[§8.1]: 08-teknisk-logg.md#81-utfall-i-den-tekniska-loggen
[§6]: #6-rest-api
[beslut 48]: 00-beslutslogg.md#beslut-48-okänd-skyddsstatus-när-citizen-inte-svarar

