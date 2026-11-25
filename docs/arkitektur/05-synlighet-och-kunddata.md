# 5. Synlighet, kunddata och kontaktpersoner

Del av [arkitekturen för party-overview](README.md).

## 5.1 Synlighet per namespace

|                 Data                 |            Vad en verksamhet (`{namespace}` i sökvägen) ser             |
|--------------------------------------|-------------------------------------------------------------------------|
| Kund (namn, adress, typ, skyddsnivå) | Allt. För en skyddad kund finns varken namn eller adress ([§5.4]).      |
| Engagemang och deras historik        | Alla i kommunen. Valfritt filter: `engagementNamespace`.                |
| Kontaktuppgifter och kontaktpersoner | `namespace = {namespace}` eller `namespace is null`                     |
| Noteringar och deras historik        | `namespace = {namespace}`                                               |
| Teknisk logg                         | Ingen namespace-filtrering. Exponeras utan namespace i sökvägen ([§6]). |

Frågan för kontakter, `customer_id = ? and (namespace = ? or namespace is null)`, går via
`idx_contact_customer_id_namespace` (`ref_or_null`).

## 5.2 Namn och adress

- Namn och adress sätts från kundparten i varje tillämpat meddelande som har namn eller adress. Det gäller bara om
  `customer.profile_modified` är null eller ≤ meddelandets `sourceModified`. När det sker sätts `profile_modified` till
  `sourceModified`.
- Uppgifterna ersätts som helhet. Ett meddelande som har namn men saknar adress tömmer alltså adressen.
- En oskyddad kund som skapas av en notering får sina uppgifter från Citizen eller LegalEntity, med
  `profile_modified = now()` ([§7.2]).
- För en skyddad kund sparas inga namn- eller adressuppgifter alls ([§5.4]).

## 5.3 Kunder, kontaktpersoner och kontaktuppgifter

Varje part i ett meddelande sorteras så här:

|               Parten har                | Har en roll i `contact_person_role` för (kommun, källa) |                                                              Resultat                                                              |
|-----------------------------------------|---------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| partyId eller legalId som går att mappa | nej                                                     | **Kund**, med engagemang                                                                                                           |
| vad som helst                           | ja                                                      | **Kontaktperson** på objektets alla `ENTERPRISE`-kunder. Blir aldrig själv kund. Har objektet ingen företagskund ignoreras parten. |
| varken partyId eller legalId            | nej                                                     | Ignoreras                                                                                                                          |

För varje kunds engagemangsrad ersätts kontaktuppgifterna (`contact` med `engagement_id` = raden) med:

- en rad med `contact_type = CUSTOMER` med kundpartens egna `contactChannels`, om den har några, och
- om kunden är `ENTERPRISE`: en rad med `contact_type = CONTACT_PERSON` för varje kontaktperson, med namn, den roll som
  matchade och kontaktkanaler.

Alla rader får engagemangets namespace.

Vid läsning slås identiska kontakter ihop: samma typ, namn, roll och samma uppsättning kanaler. Det som ändrats senast
kommer först.

En ändring av kontaktpersonsrollerna gäller från nästa meddelande om varje objekt. Befintliga rader räknas om vid nästa
ändring i källan eller vid en grundladdning.

För en skyddad kund sparas ingen rad med `contact_type = CUSTOMER` ([§5.4]). Kontaktpersoner på företag påverkas inte,
eftersom skyddet gäller kunden och inte kontaktpersonen.

## 5.4 Skyddad identitet

**Underlag.** Designen följer Skatteverkets
[vägledning för offentliga aktörers hantering av skyddade personuppgifter](https://skatteverket.se/offentligaaktorer/folkbokforing/vagledningforoffentligaaktorershanteringavskyddadepersonuppgifter.4.18e1b10334ebe8bc80002541.html)
och eSams promemoria
[Skyddade personuppgifter – myndigheters hantering (ES2023-12)](https://www.esamverka.se/download/18.2d4111b018c3dd35ea14c00/1701955298915/ES2023-12%20Skyddade%20personuppgifter%20-%20myndigheters%20hantering.pdf).
Det som styr designen:

- **Skyddade personuppgifter** är två nivåer som Skatteverket beslutar om:

  - *sekretessmarkering*: en varningssignal om att en skadeprövning krävs innan uppgifterna lämnas ut
  - *skyddad folkbokföring*: den högre nivån, där den verkliga adressen inte finns i folkbokföringen

  I Navet visas sekretessmarkering med en flagga och skyddad folkbokföring med två. Systemen ska kunna skilja nivåerna
  åt.

- *Fingerade personuppgifter* beslutas av Polismyndigheten. Personen har då en annan, fullständig identitet, och den
  syns inte som skydd. party-overview hanterar dem inte särskilt.

- Inte bara adressen är skyddsvärd. Vägledningen räknar upp bland annat namn, adress, e-post, telefonnummer,
  arbetsgivare och skola. Det gäller även uppgifter som fanns innan personen fick skydd.

- Skyddet ska vara tydligt utmärkt i varje vy. Behörigheten ska vara begränsad, och man ska inte hämta eller spara fler
  uppgifter än nödvändigt.

- Skyddet kan tillkomma, ändras och upphöra. Uppgifterna ska hållas uppdaterade, och man bör undvika egna markeringar
  utöver folkbokföringens flaggor.

- SPAR tar bort namn och adress för personer med skydd och behåller bara personnumret och en flagga.

- En person kan ha ett skyddsbehov utan att vara markerad i folkbokföringen. Därför räknas också källsystemens uppgift
  om skydd.

**Nivåer och källor.** Kundens effektiva nivå, `customer.protection_level`, är den högsta av:

|                      Källa                      |             Kolumn             |                      Uppdateras                       |
|-------------------------------------------------|--------------------------------|-------------------------------------------------------|
| Folkbokföringen via Citizen, bara för `PRIVATE` | `customer.registry_protection` | när kunden skapas, och varje natt                     |
| `CUSTOMER`-meddelanden                          | `customer.asserted_protection` | när ett `CUSTOMER UPDATE` tillämpas                   |
| Engagemangsmeddelanden                          | `engagement.party_protection`  | när objektet uppdateras. Försvinner med engagemanget. |

Ordningen är `NONE` < `UNKNOWN` < `SECRECY_MARKING` < `PROTECTED_REGISTRATION`. Nivån räknas om i samma transaktion
varje gång någon av källorna ändras. En ändrad nivå loggas som `PROTECTION_CHANGED` i den tekniska loggen. `UNKNOWN`
kommer bara från folkbokföringen, och en känd nivå från en källa går före den, eftersom den säger mer.

**Citizen.** Citizen svarar för personer både i och utanför kommunen. Personer som är sekretessmarkerade eller har
skyddad folkbokföring filtreras bort helt, om man inte frågar med `ShowClassified=true`.

- `ShowClassified=true` fungerar bara för en applikation i API-gatewayen (WSO2) som är behörig enligt en tabell i
  Metakatalogen. Annars svarar Citizen 401, oavsett vilken person man frågar efter.
- party-overviews applikation måste därför registreras i Metakatalogen innan meddelandeflödet och noteringarna slås
  på ([§13.2]).
- Flaggorna `classified` och `protectedNR` har värdet `"J"` när de är satta.

party-overview frågar alltid med `ShowClassified=true` och tolkar svaret så här ([beslut 41]):

|                           `protectedNR`                           | `classified` |  `registry_protection`   |
|-------------------------------------------------------------------|--------------|--------------------------|
| `J`                                                               | valfritt     | `PROTECTED_REGISTRATION` |
| inte `J`                                                          | `J`          | `SECRECY_MARKING`        |
| inte `J`                                                          | inte `J`     | `NONE`                   |
| Citizen har ingen post (204), eller personen saknas i batchsvaret |              | `NONE`                   |

Citizens dokumentation säger inte uttryckligen vilken flagga som betyder vad. Tolkningen bygger på namngivningen, och
den verifieras mot testpersoner i C3. En felaktig tolkning skulle bara ändra vilken nivå som visas, eftersom
hanteringen nedan är densamma för båda nivåerna.

Svaret från Citizen hålls bara i minnet. Namn och adress ur svaret används bara om personen är oskyddad, och ingenting
ur svaret loggas.

**Okänd skyddsstatus (`UNKNOWN`).** En privatkund skapas med `registry_protection = UNKNOWN` och
`registry_checked = null` när folkbokföringens status inte går att få ([beslut 48]):

- Citizen svarar inte, svarar 5xx eller har en öppen circuit breaker när kunden skapas, av ett meddelande eller av en
  notering ([§7.2]). Meddelandet eller noteringen tillämpas ändå, så att källans kö inte står still och noteringen inte
  misslyckas medan Citizen är nere.
- Kunden fanns när statusen kontrollerades, men hann raderas innan meddelandet skrevs.

Så länge statusen är okänd gäller följande:

- `UNKNOWN` räknas som skyddat. Inga namn, adresser eller egna kontaktuppgifter sparas, och kunden indexeras bara med
  engagemangens referenser.
- Den nattliga kontrollen tar alltid med kunden. När Citizen svarar ersätts `UNKNOWN` med folkbokföringens nivå, och är
  personen oskyddad fylls namn och adress i från svaret. Kontaktuppgifterna kommer tillbaka med nästa tillämpade
  meddelande från källan.
- Frontend visar `UNKNOWN` som okänd skyddsstatus och hanterar kunden som skyddad.

401 från Citizen ger inte `UNKNOWN`. Den betyder att behörigheten i Metakatalogen saknas, vilket är ett
konfigurationsfel, och meddelandet dead-letteras så att larmet går ([§7.2]).

**Dokumentation.** README, javadoc för enumen `ProtectionLevel` och OpenAPI-specens beskrivning av `protectionLevel`
beskriver alla fyra värden. För `UNKNOWN` står när värdet uppstår, att kunden ska hanteras som skyddad och att den
nattliga kontrollen ersätter det.

**Hantering när kunden är skyddad** (`protection_level ≠ NONE`):

1. `name`, adressfälten och `profile_modified` töms, och kundens rader med `contact_type = CUSTOMER` raderas. Så länge
   skyddet gäller sparas inga sådana uppgifter, varken från meddelanden, från Citizen eller från LegalEntity.
2. Kunden indexeras i OpenSearch bara med engagemangens referenser, utan namn, adress, kontaktuppgifter, titlar och
   noteringar. Den går därför bara att hitta på en referens, som ett ärendenummer, på partyId eller med uppslag på
   personnummer ([§6]).
3. API:et returnerar `protectionLevel` på kunden, och namn, adress och kontaktkanaler är tomma. Frontend ska visa
   skyddet tydligt i varje vy som rör kunden och skilja nivåerna åt, också `UNKNOWN`.
4. Engagemang, historik och noteringar finns kvar. Envelope-data och noteringar ska inte innehålla skyddsvärda uppgifter.
   Det ansvaret ligger hos källsystemen och verksamheterna ([§13]).

**När skyddet upphör** fylls namn och adress i igen. För privatkunder sker det från Citizens svar i den nattliga
kontrollen. Kontaktuppgifter kommer tillbaka med nästa tillämpade meddelande från källan, eller vid en grundladdning.

**Nattlig kontroll.** `ProtectionRefreshScheduler` (`@Dept44Scheduled` med ShedLock) körs en gång per natt. Den hämtar
privatkunder vars `registry_checked` saknas eller är äldre än `protection.refresh-age`. Förvalet är `PT20H`, så att
varje körning når alla som kontrollerades natten före. Med `P1D` skulle en kund som kontrollerades några sekunder efter
02:00 inte vara äldre än gränsen nästa natt, och då kontrolleras den bara varannan natt. Kunderna tas i batchar om
`protection.batch-size` (förval 100) via `POST /{municipalityId}/batch?ShowClassified=true`. Varje batch skrivs i en
egen transaktion med READ COMMITTED ([§3.3]). Batchens kunder låses i partyId-ordning, som i meddelandeflödet, och nivån
räknas om. Sedan indexeras de ändrade kunderna.

[§3.3]: 03-meddelanden.md#33-flödet-genom-tjänsten
[§5.4]: #54-skyddad-identitet
[§6]: 06-rest-api.md
[§7.2]: 07-integrationer.md#72-citizen-och-legalentity
[§13]: 13-risker-och-oppna-fragor.md
[beslut 48]: 00-beslutslogg.md#beslut-48-okänd-skyddsstatus-när-citizen-inte-svarar
[§13.2]: 13-risker-och-oppna-fragor.md#132-förutsättningar-före-driftsättning
[beslut 41]: 00-beslutslogg.md#beslut-41-tolkningen-av-citizens-skyddsflaggor

