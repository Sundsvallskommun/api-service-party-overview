# 5. Synlighet, kunddata och kontaktpersoner

Del av [arkitekturen för party-overview](README.md).

## 5.1 Synlighet per namespace

|                  Data                  |            Vad en verksamhet (`{namespace}` i sökvägen) ser             |
|----------------------------------------|-------------------------------------------------------------------------|
| Kund (namn, adresser, typ, skyddsnivå) | Allt. För en skyddad kund finns varken namn eller adresser ([§5.4]).    |
| Engagemang och deras historik          | Alla i kommunen. Valfritt filter: `engagementNamespace`.                |
| Företagsprofil ([§5.5])                | Allt. Saknas för en skyddad kund.                                       |
| Kontaktuppgifter och kontaktpersoner   | `namespace = {namespace}` eller `namespace is null`                     |
| Noteringar och deras historik          | `namespace = {namespace}`                                               |
| Teknisk logg                           | Ingen namespace-filtrering. Exponeras utan namespace i sökvägen ([§6]). |

Frågan för kontakter, `customer_id = ? and (namespace = ? or namespace is null)`, går via
`idx_contact_customer_id_namespace` (`ref_or_null`).

## 5.2 Namn och adress

En kund kan ha flera adresser, var och en med en typ ([beslut 57]):

|                Typ                |        Adress        |                 Kommer från                 |
|-----------------------------------|----------------------|---------------------------------------------|
| `POSTAL_ADDRESS`                  | postadress           | meddelanden, och LegalEntitys `postAddress` |
| `VISITING_ADDRESS`                | besöksadress         | meddelanden, och LegalEntitys `address`     |
| `INVOICE_ADDRESS`                 | fakturaadress        | meddelanden                                 |
| `POPULATION_REGISTRATION_ADDRESS` | folkbokföringsadress | Citizen, för en privatkund                  |

- Namn och adresser sätts från kundparten i varje tillämpat meddelande som har namn eller adresser. Det gäller bara om
  `customer.profile_modified` är null eller ≤ meddelandets `sourceModified`. När det sker sätts `profile_modified` till
  `sourceModified`.
- Uppgifterna ersätts som helhet: kundens adresser raderas och meddelandets skrivs. Kunden har alltså namnet och
  adresserna från det senaste meddelandet, och ett meddelande som har namn men saknar adresser tömmer adresserna.
- Identiska adresser, med samma typ och samma fält, sparas en gång. En adress där alla fält utom typen är tomma sparas
  inte. Adresserna returneras i typernas ordning i tabellen ovan och sedan sorterade på gatuadress.
- En oskyddad kund som skapas av en notering får sina uppgifter från Citizen eller LegalEntity, med
  `profile_modified = now()` ([§7.2]). Detsamma gäller när den nattliga kontrollen fyller i namn och
  folkbokföringsadress för en privatkund ([§5.4]).
- För en skyddad kund sparas inga namn eller adresser alls ([§5.4]).

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
  matchade, kontaktkanaler, en adress och kontaktpersonens partyId när det är känt.

Alla rader får engagemangets namespace.

Kontaktpersonens adress är partens första adress av typen `POSTAL_ADDRESS`, annars den första av typen
`VISITING_ADDRESS` och annars den första av typen `INVOICE_ADDRESS` ([beslut 57]). Raden med
`contact_type = CUSTOMER` har ingen adress, eftersom kundens adresser ligger på kunden ([§5.2]).

Vid läsning slås identiska kontakter ihop: samma typ, namn, roll, adress och samma uppsättning kanaler. Det som ändrats
senast kommer först.

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
  raderas när personen raderas, på vilket av sätten i [§3.6] som helst, och när en kund skapas med, eller får,
  en skyddsnivå som inte är `NONE`.
- Raderna hittas på `contact.party_id`, och kommunen avgörs via `contact.customer_id`. Varje berört företag loggas som
  `CONTACT_REMOVED` och markeras för omindexering ([§8.1]).
- Den nattliga kontrollen gör samma radering för varje kund i batchen vars nivå inte är `NONE` ([§5.4]). En kontaktrad
  som ett meddelande skrev samtidigt som personen skapades som skyddad kund, innan det fanns en kundrad att låsa, ligger
  då kvar högst ett dygn.
- En kontaktperson som saknar både partyId och legalId kan inte kontrolleras och sparas med namn, kanaler och adress.
  Det står som risk i [§13.3].

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
- Numret sparas aldrig, varken för en enskild firma eller för andra företag. Kunden identifieras med partyId:t. För en
  privatkund är `registry_party_id` kundens eget partyId. För andra företag är den null, och likaså för ett företag
  där det inte gick att avgöra om det är en enskild firma eller vem som äger den.

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
  `UNKNOWN` med folkbokföringens nivå. Är kunden en oskyddad privatkund fylls namn och folkbokföringsadress i från
  svaret, och en enskild firma får dem med nästa tillämpade meddelande. Kontaktuppgifterna kommer tillbaka med nästa
  tillämpade meddelande från källan.
- Frontend visar `UNKNOWN` som okänd skyddsstatus och hanterar kunden som skyddad.

401 från Citizen ger inte `UNKNOWN`. Den betyder att behörigheten i Metakatalogen saknas, vilket är ett
konfigurationsfel, och meddelandet dead-letteras så att larmet går ([§7.2]).

**Dokumentation.** README, javadoc för enumen `ProtectionLevel` och OpenAPI-specens beskrivning av `protectionLevel`
beskriver alla fyra värden. För `UNKNOWN` står när värdet uppstår, att kunden ska hanteras som skyddad och att den
nattliga kontrollen ersätter det.

**Hantering när kunden är skyddad** (`protection_level ≠ NONE`):

1. `name` och `profile_modified` töms, och kundens adresser, rader med `contact_type = CUSTOMER` och företagsprofil
   raderas ([§5.5]). Så länge skyddet gäller sparas inga sådana uppgifter, varken från meddelanden, från Citizen eller
   från LegalEntity.
2. Kunden indexeras i OpenSearch bara med engagemangens referenser, utan namn, adresser, kontaktuppgifter, titlar och
   noteringar. Den går därför bara att hitta på en referens, som ett ärendenummer, på partyId eller med uppslag på
   personnummer ([§6]).
3. API:et returnerar `protectionLevel` på kunden, och namn, adresser och kontaktkanaler är tomma. Frontend ska visa
   skyddet tydligt i varje vy som rör kunden och skilja nivåerna åt, också `UNKNOWN`.
4. Engagemang, historik och noteringar finns kvar. Envelope-data och noteringar ska inte innehålla skyddsvärda
   uppgifter. Det ansvaret ligger hos källsystemen och verksamheterna ([§13]).

**När skyddet upphör** fylls namn och adresser i igen. För privatkunder sker det från Citizens svar i den nattliga
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
- Kontrollen tar också med oskyddade företagskunder som saknar företagsprofil, och hämtar profilen ([§5.5]).
- Ändringskön startar samma kontroll direkt för en person som har ändrats ([§5.5]).

## 5.5 Företagsuppgifter

En företagskund har en profil med uppgifter från LegalEntity, som i sin tur hämtar dem från Bolagsverket och SCB:s
företagsregister ([beslut 58]). Profilen syns för alla namespaces, som kundens namn ([§5.1]).

|                                                                     Uppgift                                                                     |                               Från LegalEntity                               |                                     Kolumn ([§4.3])                                     |
|-------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| Juridisk form enligt SCB, t.ex. `49` "Övriga aktiebolag"                                                                                        | `legalForm`                                                                  | `legal_form_code`, `legal_form`                                                         |
| Företagsform enligt Bolagsverket, t.ex. "Aktiebolag"                                                                                            | `form`                                                                       | `company_form`                                                                          |
| Kommunen där företaget har sitt säte, t.ex. `2281` "Sundsvall kommun". Kan skilja sig från kundens `municipality_id`.                           | `countyMunicipalityCode`, `municipality`                                     | `registered_office_municipality_code`, `registered_office_municipality`                 |
| Registrering och avregistrering, med orsak                                                                                                      | `registerDate`, `unRegisterDate`, `unRegisterReasonCode`, `unRegisterReason` | `registered`, `deregistered`, `deregistration_reason_code`, `deregistration_reason`     |
| Pågående likvidation, konkurs eller rekonstruktion                                                                                              | `liquidationOrRestructuring[]`                                               | `enterprise_proceeding`                                                                 |
| Antal anställda, som storleksklass                                                                                                              | `employeeSize`                                                               | `employee_size_class`, `employee_size`                                                  |
| Omsättning, som storleksklass, och året den gäller                                                                                              | `annualRevenue`, `revenueYear`                                               | `revenue_size_class`, `revenue_size`, `revenue_year`                                    |
| Antal arbetsställen                                                                                                                             | `totalWorkplaces`                                                            | `workplace_count`                                                                       |
| Verksamhetsbeskrivning                                                                                                                          | `businessDescription`                                                        | `business_description`                                                                  |
| Registrerad e-post och telefon                                                                                                                  | `eMail`, `phoneNumber`                                                       | `email`, `phone_number`                                                                 |
| Arbetsställen: CFAR-nummer, namn, om det är huvudarbetsstället, postadress och besöksadress, telefon, storleksklass för anställda och SNI-koder | `workplaces[]`                                                               | `enterprise_workplace`, `enterprise_workplace_address`, `enterprise_workplace_sni_code` |

Antalet anställda finns bara som storleksklass, t.ex. "200–499 anställda", eftersom SCB sekretesskyddar det exakta
antalet.

**SNI-koder.** SCB registrerar SNI-koder både för företaget och för vart och ett av dess arbetsställen, och företagets
koder räknas fram ur arbetsställenas. LegalEntity ger bara arbetsställenas koder. party-overview sparar dem per
arbetsställe och räknar fram företagets när profilen läses och indexeras:

- En kod med tomt `sniId` sparas inte. LegalEntity ger ett sådant element först för varje arbetsställe.
- Varje kod tas med en gång. Huvudarbetsställets koder (`mainWorkplace = true`) kommer först, i LegalEntitys ordning.
  Sedan kommer övriga koder, ordnade efter storleksklassen, som tal, hos det största arbetsställe som har koden och
  därefter
  efter kod.
- Koden på plats 1 är företagets huvudnäring, och dess namn är företagets bransch.

För SCA Forest Products, med tio arbetsställen, ger det 70100, 72190, 86212 och 91012, och branschen "Verksamheter som
utövas av huvudkontor".

Arbetsställenas adresser mappas som företagets ([§7.2]). Arbetsställena läses sidvis med en egen endpoint, med
huvudarbetsstället först ([§6.1]).

**Det som inte sparas:**

- `officers` och `fullSignatureDescription`, eftersom de innehåller namn och personnummer på styrelseledamöter,
  firmatecknare och revisorer
- aktiekapital, antal aktier, valuta och räkenskapsår
- kommun och län i adresserna, utöver orten
- `organizationNumber`, eftersom kunden identifieras med partyId:t ([§5.4])
- namn och adresser, som följer [§5.2]

Ingenting ur LegalEntitys svar loggas. Tomma strängar och värden med bara blanksteg blir null, övriga värden trimmas,
och ett värde som är längre än kolumnen kortas.

**När profilen skrivs:**

- när en företagskund skapas, av ett meddelande eller en notering, från samma anrop till LegalEntity som avgör om
  kunden är en enskild firma ([§3.3], [§7.2]). Anropet görs därför även när organisationsnumret finns i meddelandet.
  Svarar LegalEntity inte får kunden ingen profil, men ett organisationsnummer från meddelandet räcker ändå för att
  känna igen en enskild firma.
- när ändringskön säger att företaget har ändrats (se nedan)
- i den nattliga kontrollen, för en oskyddad företagskund som saknar profil ([§5.4])

Profilen ersätts som helhet under kundens lås, och bara om kunden är oskyddad efter låset. `fetched` är tidpunkten då
anropet till LegalEntity startade, och en profil skrivs bara om den är nyare än den sparade. Anropen görs utanför
transaktionen, så två flöden kan skriva i omvänd ordning mot den de hämtade i. Då vinner den senaste hämtningen. En
profil som skrivs av ändringskön eller den nattliga kontrollen loggas som `PROFILE_UPDATED` ([§8.1]).

**Skyddad identitet.** En företagskund med en nivå som inte är `NONE` har ingen profil, eftersom en enskild firmas
profil är uppgifter om ägaren. Profilen raderas i samma transaktion som kunden får en sådan nivå. När skyddet upphör
hämtar den nattliga kontrollen profilen igen.

**Ändringskön** ([beslut 59]). Kön finns inte ännu, och dess meddelandemodell är inte fastställd ([§13.1]). Den ska
innehålla ändringar av alla företag och personer, med lättviktig information: vad som har hänt, med vilken part och
lite metadata. party-overview kräver följande av ett meddelande:

|              Uppgift              |                                                  Krav                                                  |
|-----------------------------------|--------------------------------------------------------------------------------------------------------|
| Partens partyId                   | **krav**. Aldrig personnummer eller organisationsnummer, så att kön inte innehåller dem.               |
| Partens typ, företag eller person | **krav**                                                                                               |
| Vad som har hänt                  | **krav**. Används för att kasta händelser som inte berör party-overview, när händelsetyperna är kända. |
| Ett unikt id per meddelande       | **krav**, för spårbarhet. Det sparas i den tekniska loggen som `message_id` ([§4.3]).                  |

Det mesta i kön berör inte party-overview. `RegistryChangeListener` slår därför först upp partyId:t i index: kunderna
med partyId:t (`idx_customer_party_id`), de enskilda firmor som personen äger (`idx_customer_registry_party_id`) och
personens kontaktrader (`idx_contact_party_id`). Ett meddelande som inte berör något av dem kastas utan anrop.

Ett meddelande om ett **företag** som är kund uppdaterar profilen:

- LegalEntity `GET /{municipalityId}/{legalEntityId}` anropas en gång, utanför transaktionen. Profilen skrivs sedan
  för kunden i varje kommun, i en transaktion per kund.
- Det är LegalEntitys svar och inte meddelandet som avgör profilen. Därför spelar meddelandenas ordning och dubbletter
  ingen roll, och kön behöver ingen single-active-consumer.
- 404 från LegalEntity lämnar profilen orörd och loggas på WARN med partyId:t.

Ett meddelande om en **person** startar kontrollen av skyddad identitet direkt, i stället för nästa natt ([§5.4]):

- Citizen `POST /{municipalityId}/batch?ShowClassified=true` anropas en gång för personen, utanför transaktionen.
- Personen som kund, och varje enskild firma som personen äger, kontrolleras sedan som i den nattliga kontrollen, i en
  transaktion per kund. Nivån räknas om, och blir kunden skyddad töms dess uppgifter och personens kontaktrader raderas.
- Är personen kontaktperson utan att vara kund, och är nivån något annat än `NONE`, raderas personens kontaktrader
  ([§5.3]).

När LegalEntity eller Citizen inte svarar, eller svarar 5xx, 429 eller 408, görs meddelandet om, och till sist
dead-letteras det och larmet går ([§12.1]). Den nattliga kontrollen finns kvar som skyddsnät.

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
[beslut 57]: 00-beslutslogg.md#beslut-57-en-kund-kan-ha-flera-adresser-och-en-kontaktperson-har-en
[§5.2]: #52-namn-och-adress
[§5.5]: #55-företagsuppgifter
[§5.1]: #51-synlighet-per-namespace
[§4.3]: 04-datamodell.md#43-tabellerna
[beslut 58]: 00-beslutslogg.md#beslut-58-företagsuppgifter-lagras-i-en-egen-profil
[beslut 59]: 00-beslutslogg.md#beslut-59-ändringskön-uppdaterar-företagsprofilen-och-kontrollerar-personers-skydd
[§13.1]: 13-risker-och-oppna-fragor.md#131-öppna-frågor
[§12.1]: 12-drift.md#121-larm-för-dead-letter-köer
[§6.1]: 06-rest-api.md#61-endpoints

