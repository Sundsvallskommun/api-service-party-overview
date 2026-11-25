# 7. Integrationer

Del av [arkitekturen för party-overview](README.md).

## 7.1 Party (api-service-party)

|                       Användning                        |                                                                  Endpoint                                                                   |
|---------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| Personnummer → partyId (meddelanden och uppslag)        | `POST /{municipalityId}/PRIVATE/partyIds` (batch, inget personnummer i URL:en)                                                              |
| Organisationsnummer → partyId (meddelanden och uppslag) | `GET /{municipalityId}/ENTERPRISE/{legalId}/partyId`. Det finns ingen batch, så numret hamnar i URL:en, vilket är accepterat ([beslut 32]). |
| Partytyp för ett partyId                                | `GET /{municipalityId}/PRIVATE/{partyId}/legalId`, och vid 404 samma anrop med `ENTERPRISE`. Svaret används bara för att avgöra typen.      |

- Feign med `dismiss404` → `Optional`, `@CircuitBreaker` och OAuth2.
- Förebilden är `api-service-party-assets`: `integration/party/PartyClient.java`, `PartyConfiguration.java` och
  `PartyTypeProvider.java`.
- `PartyIntegration` lägger aldrig legalId i loggrader eller undantag, eftersom Party:s 404-detail innehåller det. Den
  ska inte kopieras från case-status, som gör just det.
- Anropet med organisationsnumret i URL:en undantas från dept44:s payload-loggning med `logbook.excluded.paths`. Logbook
  loggar annars hela URL:en för varje Feign-anrop. Kropparna är skyddade av allowlisten, men inte URL:en.

## 7.2 Citizen och LegalEntity

Citizen används på tre ställen. Klienten genereras från den version som är i produktion när C3 byggs. I dag finns
specifikationen för v3 i `api-service-messaging/src/main/resources/integrations/citizen-v3.yml`. Alla anrop görs med
`ShowClassified=true`, vilket kräver behörighet i Metakatalogen ([§5.4]), och svaret tolkas enligt [§5.4].

|                             Användning                              |              Endpoint              |
|---------------------------------------------------------------------|------------------------------------|
| Skyddsstatus för nya privatkunder i ett meddelande ([§3.3], steg 5) | `POST /{municipalityId}/batch`     |
| Nattlig kontroll av skyddsstatus ([§5.4])                           | `POST /{municipalityId}/batch`     |
| Namn, adress och skyddsstatus när en notering skapar en privatkund  | `GET /{municipalityId}/{personId}` |

LegalEntity v2 (`GET /{municipalityId}/{legalEntityId}`) används bara när en notering skapar en företagskund.

När en notering skapar en kund ([beslut 6]) sker följande:

1. `PartyIntegration` avgör partytypen. Om ingen typ hittas blir svaret 404 och ingen notering skapas.
2. `PRIVATE`: Citizen ger skyddsstatus, och om personen är oskyddad även namn (`givenname` plus `lastname`) och
   folkbokföringsadress. Har Citizen ingen post skapas kunden utan namn och adress.
3. `ENTERPRISE`: LegalEntity ger namnet (`name`) och adressen (`postAddress`). `organizationNumber` i svaret används inte.
4. Anropen görs utanför transaktionen. Kunden skapas sedan med IODKU. Om ett meddelande hann skapa kunden under tiden
   behålls dess uppgifter, men skyddsnivån räknas om.

Fel från Citizen hanteras olika beroende på var anropet görs:

- *I meddelandeflödet:* 5xx retryas, och en öppen circuit breaker räknas som avbrott ([§3.7]). Ingen ny privatkund skapas
  utan känd skyddsstatus.
- *I den nattliga kontrollen:* en batch som misslyckas loggas, och dess kunder tas med nästa natt.
- *401:* betyder att behörigheten i Metakatalogen saknas. Det är ett konfigurationsfel. I meddelandeflödet blir det en
  `ClientProblem`, och meddelandet dead-letteras så att larmet går ([§12.1]). README beskriver åtgärden: registrera
  behörigheten och flytta sedan tillbaka meddelandena.

Förebilder:

- `pw-parking-permit` `integration/citizen/CitizenClient.java`
- `api-service-caremanagement` `citizen/service/CitizenService.hasProtectedIdentity`, som läser flaggorna med
  `ShowClassified=true`
- `api-service-postportalservice` `integration/legalentity/LegalEntityClient.java`

[§3.3]: 03-meddelanden.md#33-flödet-genom-tjänsten
[§3.7]: 03-meddelanden.md#37-felhantering
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§12.1]: 12-drift.md#121-larm-för-dead-letter-köer
[beslut 6]: 00-beslutslogg.md#beslut-6-en-notering-på-en-okänd-kund-skapar-kunden
[beslut 32]: 00-beslutslogg.md#beslut-32-organisationsnummer-i-urlen-mot-party

