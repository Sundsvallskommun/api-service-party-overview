# 10. Teststrategi

Del av [arkitekturen för party-overview](README.md).

**Regel:** IT-tester täcker bara happy case, men för alla vägar. Felfall testas i JUnit- och resource-tester, och varje
felfall testas på ett ställe. JaCoCo-gränsen i dept44-service-parent gäller.

- **Entitetstester**: `@PrePersist`/`@PreUpdate`, `RolesConverter`, `equals`/`hashCode`/`toString`.
- **`SchemaVerificationTest`**: `schema.sql` stämmer med entiteterna, inklusive `on delete cascade`.
- **Repository-tester** (`@DataJpaTest`, junit-profil): Att IODKU är idempotent, `FOR UPDATE`, synlighetsfrågorna,
  sortering och paginering, raderingen av "kunder som inte längre är parter", radering av teknisk logg med `LIMIT`,
  urvalet till den nattliga skyddskontrollen och till rensningen av kunder utan innehåll, och den rekursiva frågan för
  SNI-kodernas sökvägar ([§5.5]). Etiketternas unika nyckel: `VIP` och `vip` krockar, också globalt, medan `Hög` och
  `Hog`, och samma namn i olika namespaces, inte gör det. Förslagen med `%` och `_` i texten ([§5.7]). Ett
  samtidighetstest där två trådar skapar samma kund (`NOT_SUPPORTED`, `TransactionTemplate` och latch) och resultatet
  blir en kund utan undantag, ett där två trådar skriver samma nya SNI-noder och resultatet blir en rad per nod utan
  undantag, och ett där två trådar skapar samma etikett och båda får samma id.
- **Servicetester** (Mockito): Skyddsnivån: tabelldrivet över alla kombinationer av de tre källorna, Citizens flaggor
  och `UNKNOWN` ([§5.4]), att namn, adresser, kontaktuppgifter och profil sparas och står kvar när kunden blir skyddad,
  att en kontaktpersons nivå räknas fram från källans `protection`, kunden, `registry_person` och den fasta nivån
  ([§5.3]), och `PROTECTION_CHANGED`. Uppslag på personnummer.
  `DeadLetterHealthIndicator` (tom kö, kö med meddelanden, kö som saknas). Hela versionsmatrisen, tabelldrivet med
  alla celler i [§3.4]. Klassningen av parter ([§5.3]), "nyast vinner" för namn och adresser, valet av
  kontaktpersonens adress, historik bara när raden är ny eller `v > s`, att sista engagemanget tar bort kunden men inte
  när den har noteringar eller kontaktpersoner från REST, att en automatisk radering inte tar personens kontaktpersoner
  från REST hos andra kunder, att den nattliga kontrollen raderar en kund som inte har något kvar ([§3.6]),
  legalId-mappning och 404, valideringsfel, notering och kontaktperson som skapar kund
  (PRIVATE, ENTERPRISE, Citizen tom, okänd part), synlighetsregler och 404-fall. Valideringen av SNI-kodernas
  sökvägar, tabelldrivet med ett fall per regel, och att bara nya eller ändrade noder skrivs ([§5.5]). Etiketterna:
  att bara nya etiketter skrivs och loggas, gränsen på 50, okänd etikett och etikett i en annan kommun, och att en kund
  med bara etiketter raderas automatiskt ([§5.7]).
- **Integrationstester (enhet)**: `CitizenIntegration`, `LegalEntityIntegration`: mappning,
  `Optional.empty` vid 404, att undantag inte sväljs och att legalId inte finns i några meddelanden. `MessageListener`:
  källan slås upp från kön.
- **Resource-tester** (`@ResourceTest`, WebTestClient): Happy path per endpoint. `*ResourceFailureTest`: alla
  valideringsfel och Problem-mappningar.
- **IT** (`AbstractAppTest`, WireMock): En klass per resurs (happy case). `MessageIT`: en `RabbitMQContainer` som
  deklarerar köerna som topologin gör, och ett meddelande per väg: `UPDATE` och `DELETE` för varje objekttyp,
  `CUSTOMER UPDATE`/`DELETE`, `CONTACT_SETTING UPDATE`/`DELETE` ([§5.6]), legalId stubbat i WireMock, en kontaktperson
  och ett meddelande i ändringskön ([§5.5]).
  Allt ligger i en klass, eftersom `@DynamicPropertySource` ger en egen Spring-context. Mönster: party-assets
  `origin/publish-asset-events` `AssetEventsIT`.
- **`CascadeIT`**: `DELETE FROM customer` med `JdbcTemplate` raderar `customer_address`, `engagement`,
  `engagement_history`, `contact`, `contact_channel`, `manual_contact_person`, `manual_contact_person_channel`,
  `note`, `note_history`, `asserted_protection`, `customer_label` och profilens tabeller (`enterprise_*`).
  `technical_log`, `source_object`, `search_index_queue`, `registry_person`, `contact_setting`, `sni_classification`,
  `label` och personens kontaktrader hos andra kunder (`party_id`) lämnas orörda. `DELETE FROM label` raderar
  etikettens rader i `customer_label` men ingen kund.
- **`SchemaParityIT`**: Jämför index (namn, kolumner, unikhet) och FK (namn, `DELETE_RULE`) i `information_schema`
  mellan den Flyway-migrerade IT-databasen och ett schema som skapats från `schema.sql`.
- **`OpenApiSpecificationIT`**: `openapi.yaml` stämmer med koden.
- **Sök (enhet)**: `CustomerDocumentBinder`: deduplicering, gruppering per namespace, att en skyddad kund
  indexeras med alla fält, telefonnormalisering och taket på 500 värden. `SearchIndexRelay`: kund som finns, kund som
  saknas, fel i batch, fel på enskild rad och `attempts`. `CustomerSearchService`: 503, gränsen för result window,
  sortering och att träffens `protectionLevel` kommer från databasen. Fasaden mot Hibernate Search mockas, men inte
  DSL:en.
- **Sök (IT)**: `CustomerSearchIT` med en OpenSearch-testcontainer, som startas av en `EnvironmentPostProcessor` som i
  SM. Happy case: sökning på namn, en av flera adresser och telefon i ett annat format, kontakt i ett namespace och
  globalt, med och utan namespace-filter, en etikett i ett namespace och globalt, ett meddelande som via relayn blir
  sökbart, och reindexering.

Topologins argument och policyer (single-active-consumer, delivery-limit och DLX) testas inte automatiskt. De granskas
i PR:en mot driftkonfigurationen.

[§3.4]: 03-meddelanden.md#34-versionsmatrisen
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§5.5]: 05-synlighet-och-kunddata.md#55-företagsuppgifter
[§3.6]: 03-meddelanden.md#36-kundradering
[§5.6]: 05-synlighet-och-kunddata.md#56-kontaktinställningar
[§5.7]: 05-synlighet-och-kunddata.md#57-etiketter

