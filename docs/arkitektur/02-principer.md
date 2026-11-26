# 2. Principer och regler

Del av [arkitekturen för party-overview](README.md).

1. **Envelope-data, inte detaljer.** party-overview lagrar det som behövs för att lista och känna igen ett objekt: id i
   källsystemet, referens, typ, titel, status, datum, namespace och parter. Allt annat hämtas från källan.
2. **Inga personnummer eller organisationsnummer.** De lagras inte i någon tabell, skrivs inte till någon logg och
   hamnar inte i något undantagsmeddelande. Även partyId räknas som personuppgift i applikationsloggar, enligt
   `dept44/docs/pii-logging-policy.md`. partyId står i sökvägar, både i API:t och mot Citizen och LegalEntity, och
   därför är dept44:s PII-maskning påslagen med alla kategorier ([§12]). Uppslaget av organisationsnummer mot
   LegalEntity har numret i URL:en och undantas därför från payload-loggen ([§7.1]).
3. **Källan är master.** Meddelanden innehåller alltid hela tillståndet, och party-overview gör upsert. party-overview
   äger bara noteringarna, kontaktpersonerna som läggs till via REST och konfigurationen av partsroller.
4. **Allt som hör till en kund raderas med kunden.** Det gäller både via JPA och via `DELETE FROM customer` direkt i
   databasen, eftersom varje FK under `customer` har `on delete cascade`. Undantagen saknar FK. Den tekniska loggen
   raderas för sig ([§8.2]), och sökindexets kö töms av relayn ([§6.2]). Versionsregistrets rader för `CUSTOMER` raderas
   med kunden ([§3.6]), och dess övriga rader innehåller inga kunduppgifter ([§4.3]). Tjänsten raderar personens
   kontaktrader hos andra kunder via `party_id` ([§5.3]). Vid en radering direkt i databasen raderas undantagen för sig,
   och README beskriver hur. `registry_person` hör inte till någon kund och rensas av den nattliga kontrollen ([§5.4]).
   `contact_setting` hör inte heller till någon kund och följer ContactSettings ([§5.6]).
5. **Spårbarhet.** Tabellerna har `created`, utom kollektionstabellerna `contact_channel`,
   `manual_contact_person_channel` och `contact_setting_channel`, `search_index_queue`, som har `queued`, och profilens
   barntabeller, som ersätts med profilen. De tabeller som uppdateras har även `modified`.
   Noteringar har `created_by`/`modified_by` och partsroller `created_by`, och för andra ändringar via REST
   står anroparen i den tekniska loggens `performed_by`. Varje ändring av en kund eller dess engagemang, vare sig den
   kom via meddelande, REST, ändringskön eller ett schemalagt jobb, syns i den tekniska loggen med id:t på meddelandet
   eller anropet bakom den ([beslut 47]). Noteringar har en egen historik.
6. **Skyddad identitet syns alltid.** Uppgifterna om en kund eller kontaktperson med skyddade personuppgifter sparas,
   och skyddsnivån visas i varje svar och varje sökträff, så att konsumenten kan pröva ett utlämnande ([§5.4]).
7. **Ingen över-engineering.** Det finns en lyssnarklass för källornas köer (med en container per kö) och en för
   ändringskön, en tabell för alla engagemangstyper och inga generiska ramverk. Kod, kommentarer och javadoc beskriver
   vad koden gör, inte hur den kom till.

[§3.6]: 03-meddelanden.md#36-kundradering
[§4.3]: 04-datamodell.md#43-tabellerna
[§6.2]: 06-rest-api.md#62-kundsökning-opensearch
[§8.2]: 08-teknisk-logg.md#82-radering-av-den-tekniska-loggen
[§12]: 12-drift.md
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[beslut 47]: 00-beslutslogg.md#beslut-47-meddelandets-id-finns-bara-i-den-tekniska-loggen
[§7.1]: 07-integrationer.md#71-uppslag-av-partyid
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§5.6]: 05-synlighet-och-kunddata.md#56-kontaktinställningar

