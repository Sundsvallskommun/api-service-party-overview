# 6. REST-API

Del av [arkitekturen för party-overview](README.md).

Alla sökvägar validerar med `@ValidMunicipalityId`, `@ValidNamespace` och `@ValidUuid`. Listor returneras som
`Page<T>` med `@ParameterObject Pageable`, och sidstorleken är högst 100. Fel returneras som Problem. `@ValidUuid`
sätts inte på id-fält som är READ_ONLY.

`X-Sent-By` krävs för alla anrop som ändrar något: noteringar (`POST`, `PATCH`, `DELETE`), kundradering, radering av den
tekniska loggen, kontaktpersonsrollerna och `POST /search/reindex`. Läsningar kräver den inte, inte heller uppslaget och
sökningen, som är `POST` bara för att hålla numret och frågan borta från URL:en. Ett ändrande anrop utan headern ger
400.

## 6.1 Endpoints

|                                      Metod och sökväg                                      |                                                                                                Beskrivning                                                                                                |                                          Svar                                          |
|--------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| `GET /{municipalityId}/{namespace}/customers/{partyId}`                                    | Kunden med de kontaktuppgifter och kontaktpersoner som syns för namespacet                                                                                                                                | 200, 404                                                                               |
| `POST /{municipalityId}/{namespace}/customers/lookup`                                      | Slår upp en kund på personnummer eller organisationsnummer. Kropp: `legalId` och `partyType`. Numret mappas till partyId via Party ([§7.1]) och kastas sedan. Svaret är detsamma som för `GET` på kunden. | 200, 404                                                                               |
| `POST /{municipalityId}/{namespace}/customers/search`                                      | Fritextsökning bland kunderna. Kropp: `query` och `partyType` ([§6.2]).                                                                                                                                   | 200, 400, 503, 504                                                                     |
| `POST /search/reindex`                                                                     | Bygger om sökindexet från databasen, asynkront ([§6.2])                                                                                                                                                   | 202, 409, 503                                                                          |
| `GET /{municipalityId}/{namespace}/customers/{partyId}/engagements`                        | Engagemang. Filter `objectType` och `engagementNamespace`. Sorteras förvalt på `sourceModified,desc`.                                                                                                     | 200, 404                                                                               |
| `GET /{municipalityId}/{namespace}/customers/{partyId}/engagements/{engagementId}`         | Ett engagemang                                                                                                                                                                                            | 200, 404                                                                               |
| `GET /{municipalityId}/{namespace}/customers/{partyId}/engagements/{engagementId}/history` | Historik, förvalt `occurredAt,desc`                                                                                                                                                                       | 200, 404                                                                               |
| `GET /{municipalityId}/{namespace}/customers/{partyId}/notes`                              | Namespacets noteringar, förvalt `created,desc`                                                                                                                                                            | 200, 404                                                                               |
| `POST /{municipalityId}/{namespace}/customers/{partyId}/notes`                             | Skapar en notering och vid behov kunden ([§7.2]). `subject` högst 255 tecken och `body` högst 10 000.                                                                                                     | 201 med Location, 400, 404 om parten är okänd, 503 om Party inte kan avgöra partytypen |
| `GET /{municipalityId}/{namespace}/customers/{partyId}/notes/{noteId}`                     | En notering                                                                                                                                                                                               | 200, 404                                                                               |
| `PATCH /{municipalityId}/{namespace}/customers/{partyId}/notes/{noteId}`                   | Ändrar `subject` eller `body`                                                                                                                                                                             | 200, 404                                                                               |
| `DELETE /{municipalityId}/{namespace}/customers/{partyId}/notes/{noteId}`                  | Raderar noteringen och dess historik                                                                                                                                                                      | 204, 404                                                                               |
| `GET /{municipalityId}/{namespace}/customers/{partyId}/notes/{noteId}/history`             | Noteringens historik                                                                                                                                                                                      | 200, 404                                                                               |
| `DELETE /{municipalityId}/customers/{partyId}`                                             | Raderar kunden med allt den äger och loggar `CUSTOMER_DELETED`                                                                                                                                            | 204, 404                                                                               |
| `GET /{municipalityId}/customers/{partyId}/technical-log`                                  | Den tekniska loggen, förvalt `created,desc`. Fungerar även efter radering.                                                                                                                                | 200                                                                                    |
| `DELETE /{municipalityId}/technical-log?partyId=&createdBefore=`                           | Raderar rader i den tekniska loggen. Minst ett av filtren krävs, och båda kan kombineras ([§8.2]).                                                                                                        | 204, 400                                                                               |
| `GET /{municipalityId}/contact-person-roles`                                               | Kontaktpersonsroller per källa                                                                                                                                                                            | 200                                                                                    |
| `PUT /{municipalityId}/contact-person-roles/{source}`                                      | Ersätter källans roller (kropp: lista med roller). En okänd källa ger 400.                                                                                                                                | 204, 400                                                                               |

- En notering eller ett engagemang som inte tillhör kunden i sökvägen, eller en notering i ett annat namespace, ger 404.
- `DELETE` på kund, den tekniska loggen och kontaktpersonsrollerna ligger utanför namespace-sökvägen, eftersom de gäller
  hela kommunen. De finns i samma interna API som resten ([beslut 30]).
- Uppslaget på personnummer tar numret i kroppen och aldrig i URL:en. Om Party inte känner numret, eller om kunden inte
  finns i party-overview, blir svaret 404. Problem-detail innehåller aldrig numret.
- Responsmodeller: `Customer` (`partyId`, `partyType`, `protectionLevel`, `name`, `address`, `contactChannels[]`,
  `contactPersons[]`), `CustomerSearchHit`, `Engagement`, `HistoryEntry`, `Note`, `TechnicalLogEntry` och
  `ContactPersonRoles`. `openapi.yaml` regenereras och verifieras av `OpenApiSpecificationIT`.

## 6.2 Kundsökning (OpenSearch)

Lösningen följer SupportManagement, men med två skillnader: indexeringen går via en kö ([beslut 25]), och frågespråket är
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
- Varje miljö har en egen OpenSearch-container, som SupportManagement, tills ett riktigt kluster finns ([beslut 38], T2).
- Indexet heter `customer` och innehåller alla kommuner, med `municipalityId` som filter.

**Indexmodell**

`@Indexed` sitter på `CustomerEntity`, och dokumentet byggs av en `CustomerDocumentBinder` (TypeBinder). Bindaren läser
kundens kontakter, engagemang och noteringar genom sessionen, deduplicerade och grupperade per namespace. Det sker med
projektioner, utan att hela entiteter laddas, så `customer` behöver ingen kollektion ([§4.2]). Grupperingen är en ren
funktion som går att enhetstesta.

|                     Fält                     |                                                          Mappning                                                          |                 Skyddad kund                 |
|----------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| `municipalityId`, `partyType`                | keyword, filter                                                                                                            | med                                          |
| `protectionLevel`                            | keyword, projicerbar                                                                                                       | med                                          |
| `name`                                       | text (`text_sv`: standard, lowercase och light_swedish), plus `name_sort` som keyword                                      | saknas, eftersom namnet inte lagras ([§5.4]) |
| `careOf`, `street`, `postalCode`, `city`     | text (`text_sv`)                                                                                                           | utelämnas                                    |
| `contacts_global`, `contacts_ns_<namespace>` | text via fieldTemplate: kontaktpersoners namn och kanalvärden, högst 500 värden per fält                                   | utelämnas                                    |
| `phones_global`, `phones_ns_<namespace>`     | keyword med normalizern `phone` (`+46` och `0046` blir `0`, och allt utom siffror tas bort), bara kanaler av typen `PHONE` | utelämnas                                    |
| `references`                                 | keyword med `lowercase_normalizer`: engagemangens `reference` och `objectId`, alla                                         | **med**                                      |
| `engagements`                                | text (`text_sv`): engagemangens titel och kategori, de 1 000 senast ändrade                                                | utelämnas                                    |
| `notes_ns_<namespace>`                       | text (`text_sv`) via fieldTemplate: noteringarnas ämne och text, de 200 senaste per namespace                              | utelämnas                                    |

- Namespace i fältnamnet skrivs med gemener. Det är säkert, eftersom `@ValidNamespace` bara tillåter `[\w-]{2,32}`.
- Engagemang är sökbara för alla verksamheter, precis som de syns för alla ([§5.1]). Noteringar är bara sökbara inom det
  egna namespacet, och kontaktuppgifter inom det egna namespacet och de globala.
- En skyddad kund går bara att hitta på en referens, t.ex. ett ärendenummer ([beslut 39]). Fritext i titlar och
  noteringar kan innehålla namn och indexeras därför inte för skyddade kunder.
- Personnummer indexeras aldrig.

**Indexering**

1. *Markering:* kunden markeras i samma transaktion som ändringen med
   `insert into search_index_queue … on duplicate key update version = version + 1, attempts = 0`. Det sker:

   - varje gång en rad skrivs till den tekniska loggen för kunden, utom `IGNORED_STALE`. Markeringen görs i
     `TechnicalLogService`, så den täcker meddelanden, REST och den nattliga skyddskontrollen utan att varje flöde
     behöver komma ihåg den.
   - när en notering skapas, ändras eller raderas (C8).

   Kundraden är redan låst ([§3.3], [§3.6]), så ingen ny låsordning tillkommer. Primärnyckeln gör att det högst finns en rad per kund,
   även när OpenSearch ligger nere.

2. *Relay:* `SearchIndexRelay` körs direkt efter varje commit som har markerat kunder, och dessutom var 10:e sekund som
   skyddsnät ([beslut 40]).

   - Körningen efter commit görs asynkront via `@TransactionalEventListener(AFTER_COMMIT)` och hoppas över om relayn
     redan kör. Den schemalagda körningen tar då hand om resten.
   - Båda körningarna tar samma ShedLock-lås.
   - En ändring blir sökbar inom någon sekund, eftersom OpenSearchs `refresh_interval` är 1 s.

   Varje körning hämtar högst `search.relay.batch-size` (förval 500) av de äldsta raderna med `attempts < 5`.
   - I en read-only `TransactionTemplate` med `writeSync`-strategi får kunder som finns `addOrUpdate`, och kunder som
   saknas får `purge`.
   - Efter lyckad skrivning körs `delete … where customer_id = ? and version = ?`. En markering som kom in under tiden
   ligger alltså kvar.
   - Om OpenSearch inte går att nå, eller svarar med timeout, 5xx, 429 eller ett `cluster_block`, avbryts körningen.
   `attempts` ändras inte, och nästa körning försöker igen. Ett avbrott är inget fel i dokumenten.
   - Om OpenSearch avvisar enskilda dokument, t.ex. vid ett mappningsfel, körs raderna en och en. En rad som avvisas
   ensam får `attempts + 1`.
   - Körningen avslutas när den har hållit på i `search.relay.max-run-time` (förval `PT4M`), en bit under låsets
   `shedlock-lock-at-most-for`, så att två relayer aldrig skriver samtidigt.

3. *Larm:* `OpenSearchHealthIndicator`, kopierad från SM, blir `DOWN` när OpenSearch inte svarar eller när någon rad i
   kön har `attempts ≥ 5`. Spring Boot Admin larmar då som i [§12.1]. Indikatorn ingår inte i `readiness`.

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

**Sök-endpoint:** `POST /{municipalityId}/{namespace}/customers/search?page=&size=&sort=`, med `query` och `partyType`
i kroppen.

Frågan ligger i kroppen, eftersom den kan innehålla namn, adresser och personnummer. En URL hamnar i dept44:s
payload-logg och i gatewayens loggar, medan en kropp bara loggas med de fält som står i allowlisten ([beslut 46]).

*Parametrar:*

- `query`: `@NotBlank`, `@Size(max = 255)`.
- `partyType`: valfri.
- `size`: högst 100, som för andra listor. `page × size + size` får vara högst 10 000, annars blir svaret 400.
- `sort`: tom, vilket ger relevans och sedan `name_sort`, eller `name`. Andra värden ger 400.

*Frågan:*

- Filter på `municipalityId`, och på `partyType` om den anges.
- En `must` med en bool av should-klausuler, där minst en ska matcha. Should-klausuler direkt bredvid ett filter är
  valfria i OpenSearch, och då träffar varje sökning alla kunder i kommunen. SM gör likadant med
  `.filter(…).must(query)`.
  - `simpleQueryString` med AND, utan fuzzy, över namn, adress, `contacts_global`, `contacts_ns_{namespace}`,
    `engagements` och `notes_ns_{namespace}`
  - `match` över `phones_global`, `phones_ns_{namespace}` och `references`
- Andra namespaces fält ingår aldrig i frågan.
- `failAfter(search.timeout)`.

*Träffarna:*

- Hibernate Search laddar träffarna från databasen.
- En träff där databasen säger att kunden är skyddad, men dokumentet inte gör det, släpps. Dokumentet kan då vara
  inaktuellt och innehålla adressen.
- En kund som har raderats ger ingen träff.

*Svar:* `Page<CustomerSearchHit>` med `partyId`, `partyType`, `protectionLevel`, `name` och `address`. För en skyddad
kund är `name` och `address` tomma.

*Fel:* timeout ger 504, och en OpenSearch som inte svarar ger 503. `simple_query_string` ger inga parsfel.

*Loggning:* frågesträngen loggas aldrig.

[§4.2]: 04-datamodell.md#42-gemensamt-för-alla-tabeller
[§5.1]: 05-synlighet-och-kunddata.md#51-synlighet-per-namespace
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§6.2]: #62-kundsökning-opensearch
[§7.1]: 07-integrationer.md#71-party-api-service-party
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

