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
  `profile_modified = now()` ([§7.2]). Detsamma gäller när den nattliga kontrollen fyller i namn och adress för en
  privatkund ([§5.4]).
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
  matchade, kontaktkanaler och kontaktpersonens partyId när det är känt.

Alla rader får engagemangets namespace.

Vid läsning slås identiska kontakter ihop: samma typ, namn, roll och samma uppsättning kanaler. Det som ändrats senast
kommer först.

En ändring av kontaktpersonsrollerna gäller från nästa meddelande om varje objekt. Befintliga rader räknas om vid nästa
ändring i källan eller vid en grundladdning.

För en skyddad kund sparas ingen rad med `contact_type = CUSTOMER` ([§5.4]).

**Kontaktpersoner med skyddade personuppgifter** ([beslut 52]). En skyddad person ska inte heller synas som
kontaktperson, eftersom kopplingen till ett företag, t.ex. en arbetsgivare, är skyddsvärd.

- En kontaktpersons legalId mappas till partyId på samma sätt som en kunds ([§3.3]).
- En kontaktperson med partyId kontrolleras: finns personen som kund används kundens skyddsnivå, annars frågas Citizen
  i samma batch som för nya privatkunder. En kontaktperson med `ENTERPRISE` som inte finns som kund kontrolleras som en
  ny företagskund ([§5.4]): en enskild firma får ägarens nivå, och ett annat företag har `NONE`. Är nivån något annat
  än `NONE`, `UNKNOWN` inräknat, sparas inte kontaktpersonen alls. Nivån avgörs efter kundlåsen ([§3.3]).
- Kontaktpersonens partyId sparas på raden (`contact.party_id`). Personens kontaktrader hos alla företag i kommunen
  raderas när personen raderas via REST eller ett `CUSTOMER DELETE` ([§3.6]), och när en kund skapas med, eller får,
  en skyddsnivå som inte är `NONE`. Vid en automatisk radering ligger de kvar.
- Raderna hittas på `contact.party_id`, och kommunen avgörs via `contact.customer_id`. Varje berört företag loggas som
  `CONTACT_REMOVED` och markeras för omindexering ([§8.1]).
- Den nattliga kontrollen gör samma radering för varje kund i batchen vars nivå inte är `NONE` ([§5.4]). En kontaktrad
  som ett meddelande skrev samtidigt som personen skapades som skyddad kund, innan det fanns en kundrad att låsa, ligger
  då kvar högst ett dygn.
- En kontaktperson som saknar både partyId och legalId kan inte kontrolleras och sparas som förut. Det står som risk i
  [§13.3].

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

|                               Källa                                |              Kolumn              |                              Uppdateras                              |
|--------------------------------------------------------------------|----------------------------------|----------------------------------------------------------------------|
| Folkbokföringen via Citizen, för `PRIVATE` och för enskilda firmor | `customer.registry_protection`   | när kunden skapas, och varje natt                                    |
| `CUSTOMER`-meddelanden, en rad per källa                           | `asserted_protection.protection` | när ett `CUSTOMER UPDATE` tillämpas. Den högsta källans nivå gäller. |
| Engagemangsmeddelanden                                             | `engagement.party_protection`    | när objektet uppdateras. Försvinner med engagemanget.                |

Ordningen är `NONE` < `UNKNOWN` < `SECRECY_MARKING` < `PROTECTED_REGISTRATION`. Nivån räknas om i samma transaktion
varje gång någon av källorna ändras. En ändrad nivå loggas som `PROTECTION_CHANGED` i den tekniska loggen. `UNKNOWN`
kommer bara från folkbokföringen. En källas `SECRECY_MARKING` eller `PROTECTED_REGISTRATION` går före den, eftersom den
säger mer, men att en källa inte anger något skydd ändrar inte `UNKNOWN`.

**Enskilda firmor** ([beslut 54]). För en enskild firma är organisationsnumret ägarens personnummer, och firmans namn
och adress kan vara ägarens. När en `ENTERPRISE`-kund skapas tar party-overview reda på organisationsnumret: från
meddelandet, eller från `organizationNumber` i LegalEntitys svar på `GET /{municipalityId}/{legalEntityId}`.

- Numret är ett personnummer om det har 12 siffror, eller om det har 10 siffror och tredje siffran är 0 eller 1.
- Ett tiosiffrigt nummer får det århundrade som gör personen yngre än 100 år, enligt Skatteverkets regel för
  skiljetecknet i ett personnummer.
- Ägarens partyId slås upp via Citizen (`POST /{municipalityId}/guid/batch`) och sparas som `registry_party_id`.
  Kunden får sedan folkbokföringens skydd precis som en privatkund, också i den nattliga kontrollen.
- Numret sparas aldrig. För en privatkund är `registry_party_id` kundens eget partyId. För andra företag är den null,
  och likaså för ett företag där det inte gick att avgöra om det är en enskild firma eller vem som äger den.

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
| Citizen har ingen post (204), eller personen saknas i batchsvaret |              | `UNKNOWN`                |

Ingen post tolkas som okänd status och inte som `NONE`. Utan `ShowClassified=true`, eller med en behörighet som
filtrerar i stället för att ge 401, saknas just de skyddade personerna i svaret, och då ska de inte räknas som
oskyddade. C3 verifierar att en saknad behörighet ger 401.

Citizens dokumentation säger inte uttryckligen vilken flagga som betyder vad. Tolkningen bygger på namngivningen, och
den verifieras mot testpersoner i C3. En felaktig tolkning skulle bara ändra vilken nivå som visas, eftersom
hanteringen nedan är densamma för båda nivåerna.

Svaret från Citizen hålls bara i minnet. Namn och adress ur svaret används bara om personen är oskyddad, och ingenting
ur svaret loggas.

**Okänd skyddsstatus (`UNKNOWN`).** En kund får `registry_protection = UNKNOWN` och `registry_checked = null` när
folkbokföringens status inte går att få ([beslut 48]):

- Citizen svarar inte, svarar 5xx, 429 eller 408, eller har en öppen circuit breaker när ett meddelande skapar en
  privatkund eller en enskild firma. Meddelandet tillämpas ändå, så att källans kö inte står still medan Citizen är
  nere.
- Citizen har ingen post för personen eller ägaren (se tabellen ovan).
- Det går inte att avgöra om en ny företagskund är en enskild firma, eller vem som äger den: LegalEntity eller Citizen
  svarar inte, LegalEntity känner inte partyId:t, eller ägarens nummer finns inte i Citizen. Kunden har då inget
  `registry_party_id`.
- Kunden fanns när statusen kontrollerades, men hann raderas innan meddelandet eller noteringen skrevs. Ett företag har
  då inget `registry_party_id`.

När en notering skapar kunden blir svaret i stället 503 om Citizen eller LegalEntity inte svarar ([§7.2]).

Så länge statusen är okänd gäller följande:

- `UNKNOWN` räknas som skyddat. Inga namn, adresser eller egna kontaktuppgifter sparas, och kunden indexeras bara med
  engagemangens referenser.
- Den nattliga kontrollen tar alltid med kunden. Saknar kunden `registry_party_id` görs uppslaget av organisationsnumret
  och ägaren om först. Är kunden inte en enskild firma blir `registry_protection` null. När Citizen svarar ersätts
  `UNKNOWN` med folkbokföringens nivå. Är kunden en oskyddad privatkund fylls namn och adress i från svaret, och en
  enskild firma får dem med nästa tillämpade meddelande. Kontaktuppgifterna kommer tillbaka med nästa tillämpade
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
4. Engagemang, historik och noteringar finns kvar. Envelope-data och noteringar ska inte innehålla skyddsvärda
   uppgifter. Det ansvaret ligger hos källsystemen och verksamheterna ([§13]).

**När skyddet upphör** fylls namn och adress i igen. För privatkunder sker det från Citizens svar i den nattliga
kontrollen, och för en enskild firma med nästa tillämpade meddelande från källan. Kontaktuppgifter kommer tillbaka med
nästa tillämpade meddelande från källan, eller vid en grundladdning.

**Nattlig kontroll.** `ProtectionRefreshScheduler` (`@Dept44Scheduled` med ShedLock) körs en gång per natt.

- Den hämtar alla kunder som har `registry_party_id` eller `registry_protection = UNKNOWN`, alltså privatkunder,
  enskilda firmor och företag med okänd status, och vars `registry_checked` saknas eller är äldre än körningens start
  ([beslut 28]).
- Kunderna gås igenom med keyset på `id` i batchar om `protection.batch-size` (förval 100), så att en batch som
  misslyckas inte väljs igen i samma körning. Nästan alla kunder tas med varje natt, så urvalet går i primärnyckelns
  ordning och har inget eget index.
- För en kund utan `registry_party_id` görs först uppslaget av organisationsnumret och ägaren, som när kunden skapas.
  Sedan hämtas skyddet med `POST /{municipalityId}/batch?ShowClassified=true`.
- Varje batch skrivs i en egen transaktion med READ COMMITTED ([§3.3]). Batchens kunder låses i partyId-ordning, som i
  meddelandeflödet, och nivån räknas om. De ändrade kunderna markeras för omindexering ([§6.2]), och kontaktrader
  raderas enligt [§5.3]. En batch som faller på deadlock görs om ([§3.6]).

[§3.3]: 03-meddelanden.md#33-flödet-genom-tjänsten
[§5.4]: #54-skyddad-identitet
[§6]: 06-rest-api.md
[§7.2]: 07-integrationer.md#72-citizen-och-legalentity
[§13]: 13-risker-och-oppna-fragor.md
[beslut 48]: 00-beslutslogg.md#beslut-48-okänd-skyddsstatus-när-citizen-inte-svarar
[§13.2]: 13-risker-och-oppna-fragor.md#132-förutsättningar-före-driftsättning
[beslut 41]: 00-beslutslogg.md#beslut-41-tolkningen-av-citizens-skyddsflaggor
[§6.2]: 06-rest-api.md#62-kundsökning-opensearch
[beslut 28]: 00-beslutslogg.md#beslut-28-skyddsstatus-från-citizen-vid-skapandet-och-varje-natt
[beslut 52]: 00-beslutslogg.md#beslut-52-kontaktpersoner-kontrolleras-mot-skyddad-identitet
[beslut 54]: 00-beslutslogg.md#beslut-54-enskilda-firmor-kontrolleras-mot-folkbokföringen
[§13.3]: 13-risker-och-oppna-fragor.md#133-risker
[§3.6]: 03-meddelanden.md#36-kundradering
[§8.1]: 08-teknisk-logg.md#81-utfall-i-den-tekniska-loggen
[§5.3]: #53-kunder-kontaktpersoner-och-kontaktuppgifter

