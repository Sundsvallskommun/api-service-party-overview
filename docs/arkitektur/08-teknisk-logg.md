# 8. Teknisk logg och radering

Del av [arkitekturen för party-overview](README.md).

## 8.1 Utfall i den tekniska loggen

Ett meddelande kan ge flera rader per kund, t.ex. `ENGAGEMENT_REMOVED` följt av `CUSTOMER_DELETED`.

|        Utfall        |                                                                          När                                                                           |
|----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `CUSTOMER_CREATED`   | Kunden skapades, via meddelande eller notering                                                                                                         |
| `CUSTOMER_UPDATED`   | Ett `CUSTOMER UPDATE` tillämpades                                                                                                                      |
| `CUSTOMER_DELETED`   | Kunden raderades ([§3.6])                                                                                                                              |
| `ENGAGEMENT_CREATED` | Kunden fick ett nytt engagemang                                                                                                                        |
| `ENGAGEMENT_UPDATED` | Ett befintligt engagemang uppdaterades. Det gäller även en rättning med samma version.                                                                 |
| `ENGAGEMENT_REMOVED` | Objektet raderades, eller kunden är inte längre part i det                                                                                             |
| `IGNORED_STALE`      | Meddelandet var äldre än det lagrade och ändrade ingenting                                                                                             |
| `PROTECTION_CHANGED` | Kundens effektiva skyddsnivå ändrades ([§5.4]). Den nya nivån står i raden.                                                                            |
| `CONTACT_REMOVED`    | En kontaktpersons rader hos kunden raderades, eftersom personen raderades eller fick skydd ([§5.3]). Raden har kundens partyId, inte kontaktpersonens. |
| `PROFILE_UPDATED`    | Företagsprofilen skrevs av ändringskön eller den nattliga kontrollen ([§5.5])                                                                          |

Vid `UPDATE` skrivs `IGNORED_STALE` för meddelandets kundparter. En `DELETE` för `ERRAND`, `ASSET` och `CONTRACT` saknar
parter, så där skrivs det för de kunder som har engagemang i objektet. Ett `CUSTOMER DELETE` har kunden som enda part.

Meddelanden som dead-letteras syns inte här ([§3.7]). Ett meddelande där ingen part kunde knytas till ett partyId ger
ingen rad, eftersom loggen förs per kund.

## 8.2 Radering av den tekniska loggen

Den tekniska loggen gallras efter en lagringstid ([beslut 60]). Den raderas inte med kunden ([beslut 11]).

- `TechnicalLogRetentionScheduler` (`@Dept44Scheduled` med ShedLock) körs varje natt och raderar rader vars `created`
  är äldre än `technical-log.retention` (förval `P5Y`) via `idx_technical_log_created`.
- Rader kan också raderas med `DELETE /{municipalityId}/technical-log`, filtrerad på `partyId`, `createdBefore` eller
  båda. Det används t.ex. för en enskild persons begäran enligt GDPR.
- Båda raderar i batchar (`LIMIT`) med en transaktion per batch, så att låsen hålls korta.

Inget annat gallras automatiskt:

- Historik och kontakter följer sina engagemang och sin kund. Undantaget är en kontaktpersons rader, som också raderas
  när personen raderas, också automatiskt, eller får skydd ([§5.3]).
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

