# party-overview — arkitektur för kundtjänsten (CRM)

party-overview håller en lättviktig bild av kommunens kunder, privatpersoner och företag, och deras engagemang i
kommunen: ärenden, tillgångar (assets), kontrakt och noteringar. Tjänsten lagrar referenser och envelope-data. Den som
vill se detaljer om ett objekt anropar källsystemet. Data kommer in asynkront via RabbitMQ och läses ut via REST.

Dokumentationen beskriver lösningen i party-overview och de ändringar som publicerarna (SupportManagement, party-assets,
contract och Casedata) behöver göra. All utveckling ska utgå från den. [§14] delar upp arbetet i uppgifter med
acceptanskriterier som kan klistras in i Jira.

**Status:** fastställd design, version 1.5 (2026-11-25). Det gemensamma meddelandeformatet definieras senare. [§3.1]
listar de krav party-overview ställer på det.

## Innehåll

| §  |                                   Fil                                   |                                                Innehåll                                                 |
|----|-------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| 0  | [Beslutslogg](00-beslutslogg.md)                                        | Besluten, med bortvalda alternativ och skäl                                                             |
| 1  | [Översikt](01-oversikt.md)                                              | Flödet från publicerarna till kundvyn och delarnas ansvar                                               |
| 2  | [Principer och regler](02-principer.md)                                 | Regler som gäller hela tjänsten                                                                         |
| 3  | [Meddelanden](03-meddelanden.md)                                        | Krav på meddelandeformatet, topologi, flöde, versionsmatris, tillämpning, kundradering och felhantering |
| 4  | [Datamodell](04-datamodell.md)                                          | Tabeller, index och Flyway                                                                              |
| 5  | [Synlighet, kunddata och kontaktpersoner](05-synlighet-och-kunddata.md) | Synlighet per namespace, namn och adress, kontaktpersoner, skyddad identitet och företagsuppgifter      |
| 6  | [REST-API](06-rest-api.md)                                              | Endpoints och kundsökningen i OpenSearch                                                                |
| 7  | [Integrationer](07-integrationer.md)                                    | Uppslag av partyId, Citizen och LegalEntity                                                             |
| 8  | [Teknisk logg och radering](08-teknisk-logg.md)                         | Utfall i den tekniska loggen och radering av den                                                        |
| 9  | [Kodstruktur och konventioner](09-kodstruktur.md)                       | Paketstruktur och kodkonventioner                                                                       |
| 10 | [Teststrategi](10-teststrategi.md)                                      | Vad varje testnivå täcker                                                                               |
| 11 | [Publicerarna](11-publicerare.md)                                       | Gemensamma krav och ändringarna i SupportManagement, party-assets, contract och Casedata                |
| 12 | [Drift och konfiguration](12-drift.md)                                  | Konfiguration, driftsättning och larm för dead letter-köer                                              |
| 13 | [Risker och öppna frågor](13-risker-och-oppna-fragor.md)                | Öppna frågor, förutsättningar före driftsättning och risker                                             |
| 14 | [Uppgifter att bygga](14-uppgifter.md)                                  | Uppgifterna att bygga, med acceptanskriterier                                                           |

En hänvisning som §5.4 pekar på avsnitt 5.4, som ligger i filen som börjar med `05-`. En hänvisning som
"beslut 12" pekar på beslut 12 i beslutsloggen.

[§3.1]: 03-meddelanden.md#31-krav-på-det-gemensamma-formatet
[§14]: 14-uppgifter.md

