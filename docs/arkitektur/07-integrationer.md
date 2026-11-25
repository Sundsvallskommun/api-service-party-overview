# 7. Integrationer

Del av [arkitekturen för party-overview](README.md).

## 7.1 Uppslag av partyId

partyId är Citizens `personId` för en person och LegalEntitys id för ett företag. party-overview slår upp dem direkt mot
Citizen och LegalEntity, utan api-service-party ([beslut 56]).

|                                                      Användning                                                      |                                                              Endpoint                                                               |
|----------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| Personnummer → partyId: `PRIVATE`-parter i meddelanden och uppslag, kontaktpersoner och ägaren till en enskild firma | Citizen `POST /{municipalityId}/guid/batch`, med numren i kroppen                                                                   |
| Organisationsnummer → partyId: `ENTERPRISE`-parter i meddelanden och uppslag, även en enskild firmas nummer          | LegalEntity `GET /{municipalityId}/{organizationNumber}/guid`                                                                       |
| Partytyp när en notering skapar kunden                                                                               | De anrop som ändå görs: Citizen `GET /{municipalityId}/{personId}` och LegalEntity `GET /{municipalityId}/{legalEntityId}` ([§7.2]) |

- Feign med `dismiss404` → `Optional`, `@CircuitBreaker` och OAuth2, i `CitizenIntegration` och
  `LegalEntityIntegration`.
- Citizens batchsvar har ett element per nummer, med `personNumber` och `personId` som värden och `success` och
  `errorMessage`. Ett element utan `success` räknas som att numret inte finns ([§3.3]). Svarar Citizen 5xx på hela
  anropet görs meddelandet om ([§3.7]).
- Ett `ENTERPRISE`-nummer slås alltid upp mot LegalEntity, också en enskild firmas, och aldrig mot Citizen. Annars
  skulle firmans engagemang hamna på ägarens privatkund. LegalEntity anropas med numret i det format som C3 verifierar,
  och ett tolvsiffrigt nummer kortas då till tio siffror. 404 och 400 från LegalEntity räknas som att numret inte finns.
- Organisationsnumret står i URL:en mot LegalEntity, eftersom LegalEntity saknar ett uppslag med numret i kroppen och
  inte är vår tjänst ([beslut 32]). För en enskild firma är det ägarens personnummer. Anropet undantas från dept44:s
  payload-logg med `logbook.excluded.paths` ([§12]), och PII-maskningen är påslagen. Numret syns ändå i gatewayens och
  LegalEntitys loggar, vilket är en accepterad risk ([§13.3]).
- Integrationerna lägger aldrig legalId eller partyId i loggrader eller undantag, inte heller när ett fels detail
  innehåller numret.
- Ett IO-fel, t.ex. en timeout, blir i Feign ett `RetryableException`, vars meddelande innehåller URL:en med
  organisationsnumret eller partyId:t. PII-maskningen gäller bara loggradens meddelande och inte stacktraces ([§12]).
  Integrationerna kastar därför ett eget undantag utan URL, med IO-felet som orsak, så att startern fortfarande klassar
  det som ett avbrott ([§3.7]). dept44:s `ProblemErrorDecoder` lägger inte URL:en i sina undantag.
- legalId valideras mot formatet i [§3.1], med kontrollsiffran, innan det skickas. Ett ogiltigt värde skickas inte,
  och parten hoppas över ([§3.7]).
- 429 och 408 görs om till ett avbrott, eftersom dept44-startern annars dead-letterar en 4xx direkt ([§3.7]).
- C3 verifierar att `guid/batch` ger `personId` även för personer med skyddade personuppgifter, att Citizen svarar 5xx
  på hela anropet och inte per element när felet är tillfälligt, vilken version av LegalEntity som har
  `…/{organizationNumber}/guid`, och vilket format den tar emot för en enskild firmas nummer.

## 7.2 Citizen och LegalEntity

Citizen används på fyra ställen. Klienten genereras från den version som är i produktion när C3 byggs. I dag finns
specifikationen för v3 i `api-service-messaging/src/main/resources/integrations/citizen-v3.yml`. Alla anrop som ger
personuppgifter görs med `ShowClassified=true`, vilket kräver behörighet i Metakatalogen ([§5.4]), och svaret tolkas
enligt [§5.4].

|                                    Användning                                     |              Endpoint               |
|-----------------------------------------------------------------------------------|-------------------------------------|
| Personnummer → partyId ([§7.1])                                                   | `POST /{municipalityId}/guid/batch` |
| Skyddsstatus för nya kunder och kontaktpersoner i ett meddelande ([§3.3], steg 5) | `POST /{municipalityId}/batch`      |
| Nattlig kontroll av skyddsstatus ([§5.4])                                         | `POST /{municipalityId}/batch`      |
| Partytyp, namn, adress och skyddsstatus när en notering skapar kunden             | `GET /{municipalityId}/{personId}`  |

LegalEntity v2 ger namn, adresser, organisationsnummer och företagsprofilen med
`GET /{municipalityId}/{legalEntityId}`. Det anropet görs när en notering skapar en kund och när ett meddelande skapar
en företagskund, så att en enskild firma känns igen ([§5.4]) och profilen sparas ([§5.5]). Det görs också när
ändringskön säger att ett företag har ändrats, och när den nattliga kontrollen hämtar en profil som saknas.
Organisationsnummer slås upp med `GET /{municipalityId}/{organizationNumber}/guid` ([§7.1]).

Svaret innehåller personnummer och namn på företagets funktionärer (`officers`, `fullSignatureDescription`). De mappas
aldrig, och ingenting ur svaret loggas. Tomma strängar och värden med bara blanksteg blir null.

När en notering skapar en kund ([beslut 6]) sker följande:

1. Citizen `GET /{municipalityId}/{personId}` anropas med partyId. Svarar Citizen med en person är kunden `PRIVATE`.
   Svaret ger skyddsstatus och, om personen är oskyddad, namn (`givenname` plus `lastname`) och folkbokföringsadressen,
   alltså adressen med `addressType = POPULATION_REGISTRATION_ADDRESS`. Andra adresser i svaret används inte.
2. Har Citizen ingen post anropas LegalEntity `GET /{municipalityId}/{legalEntityId}`. Svarar LegalEntity är kunden
   `ENTERPRISE`, med namnet (`name`) och två adresser ([§5.2]):
   - `postAddress` som `POSTAL_ADDRESS`: `coAdress` blir `careOf`, `address1` och `address2` blir `street` med
     kommatecken emellan, och `postalCode`, `city` och `country` följer med.
   - `address` som `VISITING_ADDRESS`: `addressArea` och `adressNumber` blir `street` med mellanslag emellan, och
     `postalCode` och `city` följer med.

   Delar som saknas utelämnas ur `street`. LegalEntity kan ha hela gatuadressen i `addressArea`, t.ex.
   "SKEPPARPLATSEN 1", och en postadress med bara postnummer och ort, som för ett företagspostnummer. Arbetsställenas
   adresser i `workplaces[]` mappas på samma sätt ([§5.5]).

   Är `organizationNumber` i svaret ett personnummer, alltså en enskild firma, får kunden ägarens skydd från
   folkbokföringen som i [§5.4], och är ägaren skyddad sparas varken namn, adresser eller profil. Annars sparas
   profilen ([§5.5]).

3. Känner varken Citizen eller LegalEntity partyId:t blir svaret 404, och ingen notering skapas. Svarar Citizen eller
   LegalEntity inte, eller med 5xx, blir svaret 503, och ingen notering skapas. Ett tillfälligt fel blir aldrig 404.

4. Anropen görs utanför transaktionen. Kunden skapas sedan med IODKU och låses ([§3.6]). Om ett meddelande hann skapa
   kunden under tiden behålls dess uppgifter, men skyddsnivån räknas om. Om kunden fanns men hann raderas under tiden
   skapas den med `UNKNOWN`, eftersom Citizen aldrig tillfrågades om skyddet. Ett företag får då inget
   `registry_party_id`, och den nattliga kontrollen gör uppslaget ([§5.4]).

5. Skapas kunden med en skyddsnivå som inte är `NONE` raderas personens kontaktrader hos företag ([§5.3]).

Fel från Citizen hanteras olika beroende på var anropet görs:

- *När skyddsstatus, eller en ny företagskunds organisationsnummer och ägare, hämtas i meddelandeflödet:* om Citizen
  eller LegalEntity inte svarar, svarar 5xx, 429 eller 408, eller har en öppen circuit breaker, skapas nya kunder med
  `UNKNOWN`, och kontaktpersoner utan nivå sparas inte ([§5.3], [§5.4]). Felet kastas inte vidare, så meddelandet görs
  inte om och källans kö står inte still ([§3.7]). Felet loggas på WARN med källan och antalet berörda kunder och
  kontaktpersoner, men utan partyId, så att det går att se vilka källor som behöver en ny grundladdning ([§13.3]).
- *När partyId slås upp:* ett fel görs om som andra avbrott ([§3.7]), eftersom en part utan partyId inte kan
  tillämpas.
- *När en notering skapar kunden:* 503, enligt steg 3 ovan.
- *I den nattliga kontrollen:* en batch som misslyckas loggas, och dess kunder tas med nästa natt.
- *401:* betyder att behörigheten i Metakatalogen saknas. Det är ett konfigurationsfel. I meddelandeflödet blir det en
  `ClientProblem`, och meddelandet dead-letteras så att larmet går ([§12.1]). README beskriver åtgärden: registrera
  behörigheten och flytta sedan tillbaka meddelandena.

Förebilder:

- `pw-parking-permit` `integration/citizen/CitizenClient.java`
- `api-service-caremanagement` `citizen/service/CitizenService.hasProtectedIdentity`, som läser flaggorna med
  `ShowClassified=true`
- `api-service-postportalservice` `integration/legalentity/LegalEntityClient.java`
- `api-service-party` `integration/citizen/CitizenClient.java` (`guid/batch`) och
  `integration/legalentity/LegalEntityClient.java` (`…/{organizationNumber}/guid`), för uppslagen av partyId

[§3.3]: 03-meddelanden.md#33-flödet-genom-tjänsten
[§3.6]: 03-meddelanden.md#36-kundradering
[§3.7]: 03-meddelanden.md#37-felhantering
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§7.2]: #72-citizen-och-legalentity
[§12]: 12-drift.md
[§12.1]: 12-drift.md#121-larm-för-dead-letter-köer
[§13.3]: 13-risker-och-oppna-fragor.md#133-risker
[beslut 6]: 00-beslutslogg.md#beslut-6-en-notering-på-en-okänd-kund-skapar-kunden
[beslut 32]: 00-beslutslogg.md#beslut-32-organisationsnummer-slås-upp-direkt-mot-legalentity
[beslut 56]: 00-beslutslogg.md#beslut-56-party-overview-anropar-inte-api-service-party
[§7.1]: #71-uppslag-av-partyid
[§3.1]: 03-meddelanden.md#31-krav-på-det-gemensamma-formatet
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§5.2]: 05-synlighet-och-kunddata.md#52-namn-och-adress
[§5.5]: 05-synlighet-och-kunddata.md#55-företagsuppgifter

