# 7. Integrationer

Del av [arkitekturen för party-overview](README.md).

## 7.1 Party (api-service-party)

|                       Användning                        |                                                                Endpoint                                                                |
|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| Personnummer → partyId (meddelanden och uppslag)        | `POST /{municipalityId}/PRIVATE/partyIds` (batch, inget personnummer i URL:en)                                                         |
| Organisationsnummer → partyId (meddelanden och uppslag) | `POST /{municipalityId}/ENTERPRISE/partyIds` (batch, inget nummer i URL:en). Endpointen byggs i P1 ([beslut 32]).                      |
| Partytyp för ett partyId                                | `GET /{municipalityId}/PRIVATE/{partyId}/legalId`, och vid 404 samma anrop med `ENTERPRISE`. Svaret används bara för att avgöra typen. |

- Feign med `dismiss404` → `Optional`, `@CircuitBreaker` och OAuth2.
- Förebilden är `api-service-party-assets`: `integration/party/PartyClient.java`, `PartyConfiguration.java` och
  `PartyTypeProvider.java`.
- `PartyIntegration` lägger aldrig legalId i loggrader eller undantag, eftersom Party:s 404-detail innehåller det. Den
  ska inte kopieras från case-status, som gör just det.
- Varken personnummer eller organisationsnummer skickas i en URL. För en enskild firma är organisationsnumret ägarens
  personnummer, och en URL hamnar både i dept44:s payload-logg och i gatewayens och Party:s loggar. Kropparna loggas bara
  med de fält som står i allowlisten.
- Svaret från `PRIVATE/partyIds` är en map med personnumren som nycklar. Allowlisten maskar bara värden, så nycklarna
  skulle loggas i klartext. dept44:s PII-maskning, som är påslagen ([§12]), maskar dem. P1 svarar i stället med en
  lista av objekt, så att numren är värden.
- legalId valideras mot dept44:s format innan det skickas. Ett ogiltigt värde skickas inte, och parten hoppas över
  ([§3.7]). Party ska därför aldrig svara 400 på en batch från party-overview.
- 429 och 408 görs om till ett avbrott i `PartyIntegration`, eftersom dept44-startern annars dead-letterar en 4xx
  direkt ([§3.7]).

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

1. `PartyIntegration` avgör partytypen. Om Party svarar 404 för båda typerna blir svaret 404, och ingen notering skapas.
   Party frågar själv Citizen för `PRIVATE`, så när Citizen inte svarar svarar Party 5xx. Det blir 503, och ingen
   notering skapas. Ett tillfälligt fel blir aldrig 404.
2. `PRIVATE`: Citizen ger skyddsstatus, och om personen är oskyddad även namn (`givenname` plus `lastname`) och
   folkbokföringsadress. Har Citizen ingen post, eller svarar Citizen inte på party-overviews eget anrop, skapas kunden
   med `UNKNOWN`, utan namn och adress ([§5.4]).
3. `ENTERPRISE`: LegalEntity ger namnet (`name`) och adressen (`postAddress`). `organizationNumber` i svaret används inte.
   Svaret från Party:s typuppslag är organisationsnumret. Är det ett personnummer, alltså en enskild firma, får kunden
   ägarens skydd från folkbokföringen som i [§5.4], och är ägaren skyddad sparas varken namn eller adress.
4. Anropen görs utanför transaktionen. Kunden skapas sedan med IODKU och låses ([§3.6]). Om ett meddelande hann skapa
   kunden under tiden behålls dess uppgifter, men skyddsnivån räknas om. Om kunden fanns men hann raderas under tiden
   skapas den med `UNKNOWN`, eftersom Citizen aldrig tillfrågades.

Fel från Citizen hanteras olika beroende på var anropet görs:

- *I meddelandeflödet och när en notering skapar kunden:* om Citizen inte svarar, svarar 5xx, 429 eller 408, eller har
  en öppen circuit breaker, skapas nya privatkunder med `UNKNOWN` ([§5.4]). Felet kastas inte vidare, så meddelandet
  görs inte om och källans kö står inte still ([§3.7]). Felet loggas med antalet berörda kunder, men utan partyId.
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
[§3.6]: 03-meddelanden.md#36-kundradering
[§3.7]: 03-meddelanden.md#37-felhantering
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§12]: 12-drift.md
[§12.1]: 12-drift.md#121-larm-för-dead-letter-köer
[beslut 6]: 00-beslutslogg.md#beslut-6-en-notering-på-en-okänd-kund-skapar-kunden
[beslut 32]: 00-beslutslogg.md#beslut-32-organisationsnummer-skickas-i-kroppen-mot-party

