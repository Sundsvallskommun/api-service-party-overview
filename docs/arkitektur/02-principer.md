# 2. Principer och regler

Del av [arkitekturen för party-overview](README.md).

1. **Envelope-data, inte detaljer.** party-overview lagrar det som behövs för att lista och känna igen ett objekt: id i
   källsystemet, referens, typ, titel, status, datum, namespace och parter. Allt annat hämtas från källan.
2. **Inga personnummer eller organisationsnummer.** De lagras inte i någon tabell, skrivs inte till någon logg och
   hamnar inte i något undantagsmeddelande. Även partyId räknas som personuppgift i applikationsloggar, enligt
   `dept44/docs/pii-logging-policy.md`. partyId står i sökvägar, både i API:t och mot Citizen och LegalEntity, och
   därför är dept44:s PII-maskning påslagen med alla kategorier ([§12]). Uppslaget av organisationsnummer mot
   LegalEntity har numret i URL:en och undantas därför från payload-loggen ([§7.1]).
3. **Källan är master.** Meddelanden innehåller alltid hela tillståndet, och party-overview gör upsert. Det enda
   party-overview själv äger är noteringarna och konfigurationen av kontaktpersonsroller.
4. **Allt som hör till en kund raderas med kunden.** Det gäller både via JPA och via `DELETE FROM customer` direkt i
   databasen, eftersom varje FK under `customer` har `on delete cascade`. Undantagen saknar FK: den tekniska loggen,
   som raderas för sig ([§8.2]), sökindexets kö, som töms av relayn ([§6.2]), versionsregistret, vars rader för
   `CUSTOMER` raderas med kunden ([§3.6]) och vars övriga rader inte innehåller några kunduppgifter ([§4.3]), och
   personens kontaktrader hos företag, som tjänsten raderar via `contact.party_id` ([§5.3]). Vid en radering direkt i
   databasen raderas de för sig, och README beskriver hur.
5. **Spårbarhet.** Tabellerna har `created`, utom kollektionstabellen `contact_channel` och `search_index_queue`, som
   har `queued`. De tabeller som uppdateras har även `modified`. Noteringar har `created_by`/`modified_by` och
   kontaktpersonsroller `created_by`, och för andra ändringar via REST står anroparen i den tekniska loggens
   `performed_by`.
   Varje ändring av en kund eller dess engagemang, vare sig den kom via
   meddelande, REST eller den nattliga kontrollen, syns i den tekniska loggen med id:t på meddelandet eller anropet
   bakom den ([beslut 47]). Noteringar har en egen historik.
6. **Skyddad identitet går före allt annat.** För en kund med skyddade personuppgifter lagras inga namn, adresser
   eller egna kontaktuppgifter, och kunden går bara att hitta på referenser som ärendenummer. Skyddet visas på kunden i
   varje svar ([§5.4]).
7. **Ingen över-engineering.** Det finns en lyssnarklass för alla köer (med en container per kö), en tabell för alla
   engagemangstyper och inga generiska ramverk. Kod, kommentarer och javadoc beskriver vad koden gör, inte hur den kom
   till.

[§3.6]: 03-meddelanden.md#36-kundradering
[§4.3]: 04-datamodell.md#43-tabellerna
[§6.2]: 06-rest-api.md#62-kundsökning-opensearch
[§8.2]: 08-teknisk-logg.md#82-radering-av-den-tekniska-loggen
[§12]: 12-drift.md
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[beslut 47]: 00-beslutslogg.md#beslut-47-meddelandets-id-finns-bara-i-den-tekniska-loggen
[§7.1]: 07-integrationer.md#71-uppslag-av-partyid
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter

