# 2. Principer och regler

Del av [arkitekturen för party-overview](README.md).

1. **Envelope-data, inte detaljer.** party-overview lagrar det som behövs för att lista och känna igen ett objekt: id i
   källsystemet, referens, typ, titel, status, datum, namespace och parter. Allt annat hämtas från källan.
2. **Inga personnummer eller organisationsnummer.** De lagras inte i någon tabell, skrivs inte till någon logg och
   hamnar inte i något undantagsmeddelande. Även partyId räknas som personuppgift i applikationsloggar, enligt
   `dept44/docs/pii-logging-policy.md`.
3. **Källan är master.** Meddelanden innehåller alltid hela tillståndet, och party-overview gör upsert. Det enda
   party-overview själv äger är noteringarna och konfigurationen av kontaktpersonsroller.
4. **Allt som hör till en kund raderas med kunden.** Det gäller både via JPA och via `DELETE FROM customer` direkt i
   databasen, eftersom varje FK under `customer` har `on delete cascade`. Undantaget är den tekniska loggen och
   versionsregistret, som saknar FK och inte innehåller några kunduppgifter ([§4.3]).
5. **Spårbarhet.** Alla tabeller har `created`, och de tabeller som uppdateras har även `modified`. Rader som skrivs
   via REST har `created_by`/`modified_by`. Varje ändring av en kund eller dess engagemang, vare sig den kom via
   meddelande, REST eller den nattliga kontrollen, syns i den tekniska loggen med id:t på meddelandet eller anropet
   bakom den ([beslut 47]). Noteringar har en egen historik.
6. **Skyddad identitet går före allt annat.** För en kund med skyddade personuppgifter lagras inga namn, adresser
   eller egna kontaktuppgifter, och kunden går bara att hitta på referenser som ärendenummer. Skyddet visas på kunden i
   varje svar ([§5.4]).
7. **Ingen över-engineering.** Det finns en lyssnare för alla köer, en tabell för alla engagemangstyper och inga
   generiska ramverk. Kod, kommentarer och javadoc beskriver vad koden gör, inte hur den kom till.

[§4.3]: 04-datamodell.md#43-tabellerna
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[beslut 47]: 00-beslutslogg.md#beslut-47-meddelandets-id-finns-bara-i-den-tekniska-loggen

