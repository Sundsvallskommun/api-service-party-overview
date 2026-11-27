# 8. Teknisk logg och radering

Del av [arkitekturen för party-overview](README.md).

## 8.1 Utfall i den tekniska loggen

Ett meddelande kan ge flera rader per kund, t.ex. `ENGAGEMENT_REMOVED` följt av `CUSTOMER_DELETED`.

|          Utfall           |                                                                                                          När                                                                                                          |
|---------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `CUSTOMER_CREATED`        | Kunden skapades, via meddelande, notering eller kontaktperson från REST                                                                                                                                               |
| `CUSTOMER_UPDATED`        | Ett `CUSTOMER UPDATE` tillämpades                                                                                                                                                                                     |
| `CUSTOMER_DELETED`        | Kunden raderades ([§3.6])                                                                                                                                                                                             |
| `ENGAGEMENT_CREATED`      | Kunden fick ett nytt engagemang                                                                                                                                                                                       |
| `ENGAGEMENT_UPDATED`      | Ett befintligt engagemang uppdaterades. Det gäller även en rättning med samma version.                                                                                                                                |
| `ENGAGEMENT_REMOVED`      | Objektet raderades, eller kunden är inte längre part i det                                                                                                                                                            |
| `IGNORED_STALE`           | Meddelandet var äldre än det lagrade och ändrade ingenting                                                                                                                                                            |
| `PROTECTION_CHANGED`      | Kundens effektiva skyddsnivå ändrades ([§5.4]). Den nya nivån står i raden.                                                                                                                                           |
| `CONTACT_REMOVED`         | En kontaktpersons rader hos kunden raderades, eftersom parten raderades ([§5.3]). Raden har kundens partyId, inte kontaktpersonens, och kontaktradens källa i `source`. För en kontaktperson från REST saknas källan. |
| `PROFILE_UPDATED`         | Företagsprofilen skrevs av ändringskön eller den nattliga kontrollen ([§5.5])                                                                                                                                         |
| `PROFILE_NOT_FOUND`       | LegalEntity känner inte företaget som ändringskön angav, och profilen lämnades orörd ([§5.5])                                                                                                                         |
| `NAME_ADDRESS_FILLED`     | Den nattliga kontrollen fyllde i namn och folkbokföringsadress från Citizen för en privatkund som saknade dem ([§5.4])                                                                                                |
| `CONTACT_PERSON_CREATED`  | En kontaktperson lades till via REST ([§5.3]). Raden har kundens partyId och `performed_by`.                                                                                                                          |
| `CONTACT_PERSON_UPDATED`  | En kontaktperson från REST ändrades                                                                                                                                                                                   |
| `CONTACT_PERSON_DELETED`  | En kontaktperson från REST raderades                                                                                                                                                                                  |
| `CONTACT_SETTING_UPDATED` | Kundens kontaktinställning från ContactSettings skapades eller ändrades ([§5.6])                                                                                                                                      |
| `CONTACT_SETTING_DELETED` | Kundens kontaktinställning från ContactSettings togs bort ([§5.6])                                                                                                                                                    |
| `LABEL_ADDED`             | Kunden fick en etikett via REST ([§5.7]). Raden har etikettens id i `object_id` och anroparen i `performed_by`.                                                                                                       |
| `LABEL_REMOVED`           | En etikett togs bort från kunden, eller raderades för alla kunder ([§5.7]). Raden har etikettens id i `object_id` och anroparen i `performed_by`.                                                                     |

Ändringar i `registry_person` loggas inte, eftersom raden inte hör till någon kund. En kunds egen nivå loggas som
`PROTECTION_CHANGED`.

Vid `UPDATE` skrivs `IGNORED_STALE` för meddelandets kundparter. En `DELETE` för `ERRAND`, `ASSET` och `CONTRACT` saknar
parter, så där skrivs det för de kunder som har engagemang i objektet. Ett `CUSTOMER DELETE` har kunden som enda part.
Ett inaktuellt `CONTACT_SETTING`-meddelande ger `IGNORED_STALE` för kunden, om personen är kund ([§5.6]).

Rader från ändringskön har kanalen `MESSAGE` och meddelandets id, men ingen källa, objekttyp, operation eller version.
Rader från den nattliga kontrollen har kanalen `SCHEDULER` och inget av dem ([§5.5]).

Meddelanden som dead-letteras syns inte här ([§3.7]). Ett meddelande där ingen part kunde knytas till ett partyId ger
ingen rad, eftersom loggen förs per kund.

## 8.2 Radering av den tekniska loggen

Den tekniska loggen gallras efter en lagringstid ([beslut 60]). Den raderas inte med kunden ([beslut 11]).

- `TechnicalLogRetentionScheduler` (`@Dept44Scheduled` med ShedLock) körs varje natt och raderar rader vars `created`
  är äldre än `technical-log.retention` via `idx_technical_log_created`. Lagringstiden binds som `java.time.Period`
  (förval `P5Y`), och gränsen räknas som `OffsetDateTime.now(clock).minus(retention)`, eftersom ett `Period` med år
  inte går att dra från ett `Instant`.
- Rader kan också raderas med `DELETE /{municipalityId}/technical-log`, filtrerad på `partyId`, `createdBefore` eller
  båda. `createdBefore` är en tidpunkt med zon enligt ISO 8601. Det används t.ex. för en enskild persons begäran enligt
  GDPR.
- Båda raderar i batchar om `technical-log.delete-batch-size` (förval 1 000) med `LIMIT` och en transaktion per batch,
  så att låsen hålls korta.

Inget annat gallras efter en lagringstid:

- Historik och kontakter följer sina engagemang och sin kund. Undantaget är en kontaktpersons rader, som också raderas
  när parten raderas, även automatiskt ([§5.3]).
- En kund som inte har något kvar raderas av den nattliga kontrollen ([§3.6], [§5.4]).
- En persons rad i `registry_person` raderas av den nattliga kontrollen när ingen kontaktrad pekar på den ([§5.4]).
- En persons rad i `contact_setting` följer ContactSettings och raderas när inställningen tas bort där ([§5.6]).
- Tombstones i `source_object` sparas för alltid ([beslut 12]). Rader för `CUSTOMER`, som innehåller partyId, blir
  aldrig tombstones och raderas med kunden ([§3.6]).

[§3.6]: 03-meddelanden.md#36-kundradering
[§3.7]: 03-meddelanden.md#37-felhantering
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[beslut 11]: 00-beslutslogg.md#beslut-11-den-tekniska-loggen-har-ingen-fk-till-kunden
[beslut 12]: 00-beslutslogg.md#beslut-12-versionsvakt-i-ett-register-per-källobjekt
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§5.5]: 05-synlighet-och-kunddata.md#55-företagsuppgifter
[beslut 60]: 00-beslutslogg.md#beslut-60-den-tekniska-loggen-gallras-efter-en-lagringstid
[§5.6]: 05-synlighet-och-kunddata.md#56-kontaktinställningar
[§5.7]: 05-synlighet-och-kunddata.md#57-etiketter

