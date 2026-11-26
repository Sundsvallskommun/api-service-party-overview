# 5. Synlighet, kunddata och kontaktpersoner

Del av [arkitekturen för party-overview](README.md).

## 5.1 Synlighet per namespace

En kund tillhör inget namespace. Den kan ha ärenden, assets, kontrakt, kontaktpersoner och noteringar från många
verksamheter. Namespace finns på det som hör till en verksamhet: engagemang, kontaktuppgifter, kontaktpersoner och
noteringar ([beslut 7]). Det är alltid valfritt, och något som saknar namespace är globalt.

Ingen sökväg har namespace. Vid läsning är `namespace` ett valfritt filter som kan anges flera gånger ([§6]):

|                        Data                        |               Utan filter                |              Med filtret `namespace`               |
|----------------------------------------------------|------------------------------------------|----------------------------------------------------|
| Kund (namn, adresser, typ, skyddsnivå)             | Allt, också för en skyddad kund ([§5.4]) | Samma som utan filter                              |
| Företagsprofil ([§5.5])                            | Allt                                     | Samma som utan filter                              |
| Engagemang och deras historik                      | Alla i kommunen                          | De angivna namespacena och de som saknar namespace |
| Kontaktuppgifter och kontaktpersoner från källorna | Alla                                     | De angivna namespacena och de globala              |
| Kontaktpersoner som lagts till via REST ([§5.3])   | Alla                                     | De angivna namespacena och de globala              |
| Noteringar och deras historik                      | Alla                                     | De angivna namespacena och de globala              |
| Teknisk logg                                       | Allt. Filtret finns inte.                | —                                                  |

När en notering eller en kontaktperson skapas är namespace ett valfritt fält i kroppen, och det går inte att ändra
efteråt. Namespace är ett filter och ingen behörighet. Vem som får läsa och ändra styrs av åtkomsten till API:t
([§13.3]).

Frågan för kontakter med filtret, `customer_id = ? and (namespace in (…) or namespace is null)`, går via
`idx_contact_customer_id_namespace`.

## 5.2 Namn och adress

En kund kan ha flera adresser, var och en med en typ ([beslut 57]):

|                Typ                |        Adress        |                 Kommer från                 |
|-----------------------------------|----------------------|---------------------------------------------|
| `POSTAL_ADDRESS`                  | postadress           | meddelanden, och LegalEntitys `postAddress` |
| `VISITING_ADDRESS`                | besöksadress         | meddelanden, och LegalEntitys `address`     |
| `INVOICE_ADDRESS`                 | fakturaadress        | meddelanden                                 |
| `POPULATION_REGISTRATION_ADDRESS` | folkbokföringsadress | Citizen, för en privatkund                  |

- Namn och adresser sätts från kundparten i varje tillämpat meddelande som har namn eller adresser. Det gäller bara om
  `customer.name_address_modified` är null eller ≤ meddelandets `sourceModified`. Då sätts `name_address_modified` till
  `sourceModified`.
- Uppgifterna ersätts som helhet: kundens adresser raderas och meddelandets skrivs. Ett meddelande som har namn men
  saknar adresser tömmer adresserna.
- Adressfälten trimmas, och tomma fält blir null. Identiska adresser, med samma typ och samma fält, sparas en gång. En
  adress där alla fält utom typen är tomma sparas inte. Adresserna returneras i typernas ordning i tabellen ovan och
  sedan sorterade på gatuadress och `id`.
- En kund som skapas av en notering får sina uppgifter från Citizen eller LegalEntity, med
  `name_address_modified = now()` ([§7.2]). Detsamma gäller när den nattliga kontrollen fyller i namn och
  folkbokföringsadress för en privatkund som aldrig har fått några ([§5.4]).
- Namn och adresser sparas på samma sätt för en skyddad kund ([§5.4]).

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

Kontaktpersonens adress väljs efter att adresserna har trimmats och tomma adresser tagits bort, som i [§5.2]. Det är
partens första adress av typen `POSTAL_ADDRESS`, annars den första av typen `VISITING_ADDRESS` och annars den första
av typen `INVOICE_ADDRESS` ([beslut 57]). Raden med `contact_type = CUSTOMER` har ingen adress, eftersom kundens
adresser ligger på kunden ([§5.2]).

Vid läsning slås identiska kontakter från källorna ihop: samma typ, namn, roll, adress, namespace och samma
uppsättning kanaler. Det som ändrats senast kommer först. En ihopslagen kontaktperson får den högsta skyddsnivån bland
raderna, med den radens värden från Citizen. Kontaktpersoner som har lagts till via REST slås aldrig ihop med andra.

En ändring av kontaktpersonsrollerna gäller från nästa meddelande om varje objekt. Befintliga rader räknas om vid nästa
ändring i källan eller vid en grundladdning.

**Kontaktpersoners skyddsnivå** ([beslut 52]). En kontaktperson sparas oavsett skydd och har en egen skyddsnivå, med
samma värden som kundens ([§5.4]). Nivån visar att uppgifterna inte får lämnas ut utan prövning.

- En kontaktpersons legalId mappas till partyId på samma sätt som en kunds ([§3.3]), och partyId:t sparas på raden
  (`contact.party_id`).
- `contact.registry_party_id` är den person vars skydd hämtas från folkbokföringen: kontaktpersonen själv för en
  person, och ägaren för en enskild firma. En kontaktperson med `ENTERPRISE` som inte finns som kund kontrolleras som en
  ny företagskund ([§5.4]).
- En kontaktperson utan `registry_party_id` får en fast nivå i `fallback_protection`: `NONE` för ett företag som inte
  är en enskild firma, och `UNVERIFIED` när skyddet inte går att kontrollera. Det gäller när kontaktpersonen saknar både
  partyId och legalId, eller när det inte gick att avgöra om ett företag är en enskild firma. `UNVERIFIED` ersätts
  aldrig av den nattliga kontrollen, bara när källan skriver om raden ([beslut 48]).
- Partens `protection` i meddelandet sparas som `party_protection`.
- Folkbokföringens status lagras en gång per person, i `registry_person`, med nivån, Citizens värden och `checked`.
  Varje `registry_party_id` på en kontaktrad har en rad där. Raden skapas med `UNKNOWN` när Citizen inte svarar eller
  saknar post. Ett svar skrivs bara om dess anrop startade senare än det sparade, som för kunden ([§5.4]).
- Kontaktraderna ersätts som helhet och uppdateras aldrig. Skyddsnivån räknas fram när raden läses, som den högsta av
  `party_protection` och:
  - kundens `protection_level`, om `party_id` är kund i radens kommun. Citizens värden tas då från kundraden.
  - annars personens rad i `registry_person`, om raden har `registry_party_id`. Saknas personens rad räknas nivån som
    `UNKNOWN`.
  - annars `fallback_protection`
- `registry_person` hålls aktuell av meddelandeflödet, den nattliga kontrollen och ändringskön ([§5.4], [§5.5]). En ny
  nivå syns därför direkt på personens alla kontaktrader, i alla kommuner, utan att någon kontaktrad skrivs om.
- Partens kontaktrader hos andra kunder i kommunen raderas när parten raderas, på vilket av sätten i [§3.6] som
  helst. Partens kontaktpersoner från REST raderas bara när parten raderas med `CUSTOMER DELETE` eller via REST, inte
  vid en automatisk radering. Raderna hittas på `party_id`, och varje berörd kund loggas som `CONTACT_REMOVED` och
  markeras för omindexering ([§8.1]).

**Kontaktpersoner som läggs till via REST** ([beslut 65]). De kan läggas till på privatkunder och företag ([§6.1]).

- Kontaktpersonen sparas i `manual_contact_person`. Namespace är valfritt och anges i kroppen, och en kontaktperson
  utan namespace är global. Namespace går inte att ändra efteråt.
- Kundens kontaktpersoner från REST hämtas med en egen endpoint, och i kundvyn tillsammans med kontaktpersonerna från
  källorna. Båda följer filtret i [§5.1].
- Kontaktpersonen har namn, roll, kontaktkanaler och en adress. Den kan anges med partyId eller med legalId, och då med
  `partyType`. legalId mappas till partyId som i [§3.3] och sparas aldrig. `registry_party_id` och
  `fallback_protection` sätts som för en kontaktrad från ett meddelande, och skyddsnivån räknas fram på samma sätt.
- Citizen anropas när personen saknas i `registry_person` eller har `UNKNOWN`. Svarar Citizen inte får personen
  `UNKNOWN`, som i meddelandeflödet. Går legalId inte att mappa blir svaret 400. Kan numret eller partytypen inte
  avgöras för att Citizen eller LegalEntity inte svarar blir det 503.
- Finns kunden inte skapas den, som när en notering skapar en kund ([§7.2]).
- Kontaktpersonen ändras och raderas via REST med optimistisk låsning, som en notering ([beslut 61]). Varje ändring
  loggas i den tekniska loggen med `performed_by` ([§8.1]).
- En kund som har sådana kontaktpersoner raderas inte automatiskt ([§3.6]).
- Kontaktpersonen indexeras i `contacts_ns_<namespace>`, och dess telefonnummer i `phones_ns_<namespace>`. En
  kontaktperson utan namespace indexeras i `contacts_global` och `phones_global` ([§6.2]).

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

- En person kan ha ett skyddsbehov utan att vara markerad i folkbokföringen. Därför räknas också källsystemens uppgift
  om skydd.

party-overview sparar uppgifterna också för skyddade personer, och skyddsnivån är ett attribut som visar att de inte
får lämnas ut utan prövning ([beslut 27]). Det gäller kunder och kontaktpersoner ([§5.3]).

**Nivåer och källor.** Kundens effektiva nivå, `customer.protection_level`, är den högsta av:

|                               Källa                                |              Kolumn              |                              Uppdateras                              |
|--------------------------------------------------------------------|----------------------------------|----------------------------------------------------------------------|
| Folkbokföringen via Citizen, för `PRIVATE` och för enskilda firmor | `customer.registry_protection`   | när kunden skapas, och varje natt                                    |
| `CUSTOMER`-meddelanden, en rad per källa                           | `asserted_protection.protection` | när ett `CUSTOMER UPDATE` tillämpas. Den högsta källans nivå gäller. |
| Engagemangsmeddelanden                                             | `engagement.party_protection`    | när objektet uppdateras. Försvinner med engagemanget.                |

Ordningen är `NONE` < `UNKNOWN` < `SECRECY_MARKING` < `PROTECTED_REGISTRATION`. Nivån räknas om i samma transaktion
varje gång någon av källorna ändras. En ändrad nivå loggas som `PROTECTION_CHANGED` i den tekniska loggen. `UNKNOWN`
kommer bara från folkbokföringen. En källas `SECRECY_MARKING` eller `PROTECTED_REGISTRATION` går före den, eftersom den
säger mer. Att en källa inte anger något skydd ändrar inte `UNKNOWN`.

En kontaktperson kan också ha nivån `UNVERIFIED`, som betyder att skyddet inte går att kontrollera ([§5.3]). En kund
har aldrig den. Ordningen för kontaktpersoner är `NONE` < `UNVERIFIED` < `UNKNOWN` < `SECRECY_MARKING` <
`PROTECTED_REGISTRATION`.

**Enskilda firmor** ([beslut 54]). För en enskild firma är organisationsnumret ägarens personnummer. När en
`ENTERPRISE`-kund skapas tar party-overview reda på organisationsnumret: från meddelandet, eller från
`organizationNumber` i LegalEntitys svar på `GET /{municipalityId}/{legalEntityId}`.

- Numret är ett personnummer om det har 12 siffror, eller om det har 10 siffror och tredje siffran är 0 eller 1.
- Ett tiosiffrigt nummer får det århundrade som gör personen yngre än 100 år, enligt Skatteverkets regel för
  skiljetecknet i ett personnummer.
- Ägarens partyId slås upp via Citizen (`POST /{municipalityId}/guid/batch`) och sparas som `registry_party_id`.
  Kunden får sedan folkbokföringens skydd som en privatkund, också i den nattliga kontrollen.
- Numret sparas aldrig, varken för en enskild firma eller för andra företag. Kunden identifieras med partyId:t. För en
  privatkund är `registry_party_id` kundens eget partyId. För andra företag är den null, liksom för ett företag där det
  inte gick att avgöra om det är en enskild firma eller vem som äger den.

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
filtrerar i stället för att ge 401, saknas just de skyddade personerna i svaret. De ska då inte räknas som oskyddade. C3
verifierar att en saknad behörighet ger 401.

Citizens dokumentation säger inte uttryckligen vilken flagga som betyder vad. Tolkningen bygger på namngivningen, och
den verifieras mot testpersoner i C3. En felaktig tolkning skulle bara ändra vilken nivå som visas, eftersom
hanteringen nedan är densamma för båda nivåerna.

Värdena i `classified` och `protectedNR` sparas också som Citizen gav dem, i `registry_classified` och
`registry_protected_nr`, och för kontaktpersoner i `registry_person` ([§5.3], [beslut 41]). Värdena trimmas, och ett
tomt värde blir null. Ett värde som är längre än kolumnen kortas, och det loggas på WARN med värdets längd. Båda är
null när Citizen saknar post för personen. När Citizen inte svarar lämnas de orörda.

I övrigt hålls svaret från Citizen bara i minnet. Namn och folkbokföringsadress ur svaret används bara när en
notering eller en kontaktperson från REST skapar en privatkund, och när den nattliga kontrollen fyller i dem ([§5.2]).
Ingenting ur svaret loggas.

**Okänd skyddsstatus (`UNKNOWN`).** En kund får `registry_protection = UNKNOWN` när
folkbokföringens status inte går att få ([beslut 48]):

- Citizen svarar inte, svarar 5xx, 429 eller 408, eller har en öppen circuit breaker när ett meddelande skapar en
  privatkund eller en enskild firma. Meddelandet tillämpas ändå ([beslut 48]).
- Citizen har ingen post för personen eller ägaren (se tabellen ovan).
- Det går inte att avgöra om en ny företagskund är en enskild firma, eller vem som äger den: LegalEntity eller Citizen
  svarar inte, LegalEntity känner inte partyId:t, eller ägarens nummer finns inte i Citizen. Kunden har då inget
  `registry_party_id`.
- Kunden fanns när statusen kontrollerades, men hann raderas innan meddelandet eller noteringen skrevs. Ett företag har
  då inget `registry_party_id`.

När en notering eller en kontaktperson från REST skapar kunden blir svaret i stället 503 om Citizen eller LegalEntity
inte svarar ([§7.2]).

Så länge statusen är okänd gäller följande:

- Uppgifterna sparas och indexeras som för andra nivåer.
- Den nattliga kontrollen tar med kunden varje natt, utom ett företag under uppehållet efter 404 från LegalEntity
  ([beslut 64]). Saknar kunden `registry_party_id` görs uppslaget av organisationsnumret
  och ägaren om först. Är kunden inte en enskild firma blir `registry_protection` null. När Citizen svarar ersätts
  `UNKNOWN` med folkbokföringens nivå.
- Frontend visar `UNKNOWN` som okänd skyddsstatus och hanterar kunden som skyddad.

401 från Citizen ger inte `UNKNOWN`. Den betyder att behörigheten i Metakatalogen saknas, vilket är ett
konfigurationsfel. Meddelandet dead-letteras så att larmet går ([§7.2]).

**Dokumentation.** README, javadoc för enumen `ProtectionLevel` och OpenAPI-specens beskrivning av `protectionLevel`
beskriver alla fem värden. För `UNKNOWN` står när värdet uppstår, att kunden ska hanteras som skyddad och att den
nattliga kontrollen ersätter det. För `UNVERIFIED` står att det bara gäller kontaktpersoner, att skyddet inte går att
kontrollera och att värdet inte ersätts förrän källan skriver om kontaktpersonen. Beskrivningen säger att en nivå som
inte är `NONE` betyder att uppgifterna inte får lämnas ut utan prövning, och att konsumenten ansvarar för prövningen.
`classified` och `protectedNR` beskrivs som Citizens värden.

**Hantering när kunden är skyddad** (`protection_level ≠ NONE`, [beslut 27]):

1. Namn, adresser, kontaktuppgifter, kontaktpersoner och företagsprofil sparas och uppdateras som för en oskyddad
   kund. Ingenting töms eller raderas när ett skydd tillkommer eller upphör, bara nivån ändras.
2. Kunden indexeras i OpenSearch med alla uppgifter. Sökträffen har kundens `protectionLevel`, `classified` och
   `protectedNR`, och `hasProtectedContacts`, som visar om någon av kundens synliga kontaktpersoner har en nivå som
   inte är `NONE` ([§6.2]).
3. API:et returnerar uppgifterna med `protectionLevel`, `classified` och `protectedNR`, på kunden och på varje
   kontaktperson ([§5.3]). Frontend ska visa skyddet tydligt i varje vy och lista som rör kunden eller kontaktpersonen,
   skilja nivåerna åt, också `UNKNOWN`, och se till att uppgifterna inte lämnas ut utan prövning.
4. Läsningar av skyddade kunder loggas inte särskilt ([beslut 35]).

**Ordningen mellan svar.** Skyddet hämtas utanför transaktionen och skrivs under kundens lås, av meddelandeflödet, den
nattliga kontrollen och ändringskön ([§5.5]). Ett äldre svar kan därför komma fram efter ett nyare. `registry_checked`
är tidpunkten då anropet till Citizen startade, för det svar som gäller. Ett svar skrivs bara om dess anrop startade
senare. Annars lämnas nivån och flaggorna orörda. När Citizen inte svarar ändras inte `registry_checked`, och för en ny
kund är den då null.

**Nattlig kontroll.** `ProtectionRefreshScheduler` (`@Dept44Scheduled` med ShedLock) körs en gång per natt.

- Den hämtar två slags kunder ([beslut 28]):
  - kunder som har `registry_party_id` eller `registry_protection = UNKNOWN`, alltså privatkunder, enskilda firmor
    och företag med okänd status, och vars `registry_checked` saknas eller är äldre än körningens start
  - företagskunder som saknar företagsprofil, vars profil hämtas från LegalEntity ([§5.5])
- Kunderna gås igenom med keyset på `id` i batchar om `protection.batch-size` (förval 100), så att en batch som
  misslyckas inte väljs igen i samma körning. Nästan alla kunder tas med varje natt, så urvalet går i primärnyckelns
  ordning och har inget eget index.
- För en kund utan `registry_party_id` görs först uppslaget av organisationsnumret och ägaren, som när kunden skapas.
  Sedan hämtas skyddet med `POST /{municipalityId}/batch?ShowClassified=true`.
- Svarar LegalEntity 404 sätts `legal_entity_not_found` till tidpunkten då anropet startade, och ett svar med
  företaget sätter den till null. LegalEntity anropas sedan inte för kunden förrän `protection.legal-entity-retry`
  (förval `P7D`) har gått ([beslut 64]). Ett företag med `UNKNOWN` utan `registry_party_id`, och ett företag utan
  profil, tas inte med under tiden. En enskild firma med `registry_party_id` kontrolleras ändå mot Citizen, men
  profilen hämtas inte. 5xx, en timeout eller en öppen circuit breaker ändrar inte `legal_entity_not_found`.
- Varje batch skrivs i en egen transaktion med READ COMMITTED ([§3.3]). Batchens kunder låses med `FOR UPDATE` i
  partyId-ordning, som i meddelandeflödet. En kund som har hunnit raderas hoppas över och skapas inte på nytt. Nivån och
  flaggorna skrivs, och nivån räknas om. De kunder vars nivå ändrades markeras för omindexering ([§6.2]). En batch som
  faller på deadlock görs om ([§3.6]).
- En privatkund vars `name_address_modified` är null får namn och folkbokföringsadress från svaret, med
  `name_address_modified = now()` ([§5.2]). Det gäller t.ex. en kund som bara har skapats av meddelanden utan namn,
  eller som skapades med `UNKNOWN` när en notering skapade den ([§7.2]). En enskild firma får aldrig ägarens namn eller
  adress. Ifyllnaden loggas som `NAME_ADDRESS_FILLED`, och kunden markeras för omindexering ([§8.1]).
- Sedan gås `registry_person` igenom med keyset på `id`, i batchar om `protection.batch-size`. De rader vars `checked`
  saknas eller är äldre än körningens start tas med. Citizen anropas en gång per kommun i batchen, med radernas
  `municipality_id`. Svaren skrivs med ordningsvakten, i en transaktion per batch. Svarar Citizen inte lämnas raderna
  orörda.
- Sist raderas de rader i `registry_person` som ingen kontaktrad i `contact` eller `manual_contact_person` pekar på, i
  batchar om `protection.batch-size`. Därefter skapas en rad med `UNKNOWN` för varje `registry_party_id` på en
  kontaktrad som saknar rad. Det händer när ett meddelande skrev kontaktraden samtidigt som raden rensades. Nästa natt
  får raden folkbokföringens nivå.
- Ändringskön startar samma kontroll direkt för en person som har ändrats ([§5.5]).

## 5.5 Företagsuppgifter

En företagskund har en profil med uppgifter från LegalEntity, som hämtar dem från Bolagsverket och SCB:s
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
- Varje kod tas med en gång. Koderna från huvudarbetsstället (`mainWorkplace = true`) kommer först, i LegalEntitys
  ordning. Saknas huvudarbetsställe hoppas steget över, och finns flera tas de i LegalEntitys ordning.
- Sedan kommer övriga koder, ordnade efter storleksklassen hos det största arbetsställe som har koden, störst först, och
  därefter efter kod. Storleksklassen jämförs som tal, och en klass som saknas eller inte är ett tal räknas som minst.
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
- i den nattliga kontrollen, för en företagskund som saknar profil ([§5.4])

Profilen skrivs under kundens lås. Ändringskön och den nattliga kontrollen låser kunden med `FOR UPDATE`. En kund som
har hunnit raderas hoppas över och skapas inte på nytt. `fetched` är tidpunkten då anropet till LegalEntity startade,
och en profil skrivs bara om den är nyare än den sparade. Anropen görs utanför transaktionen, så två flöden kan skriva
i omvänd ordning mot den de hämtade i. Då vinner den senaste hämtningen.

`content_hash` är en SHA-256 av den mappade profilen med arbetsställen och förfaranden. Är den oförändrad flyttas bara
`fetched` fram, utan loggrad och utan omindexering. Annars ersätts profilen som helhet, och en profil som skrivs av
ändringskön eller den nattliga kontrollen loggas som `PROFILE_UPDATED` ([§8.1]).

**Skyddad identitet.** En enskild firmas profil är uppgifter om ägaren. Den sparas också när kunden är skyddad, och
kundens `protectionLevel` visar att den inte får lämnas ut utan prövning ([§5.4]).

**Ändringskön** ([beslut 59]). Kön finns inte ännu, och dess meddelandemodell är inte fastställd ([§13.1]). Den ska
innehålla ändringar av alla företag och personer, med lättviktig information: vad som har hänt, med vilken part och
lite metadata. party-overview kräver följande av ett meddelande:

|              Uppgift              |                                                  Krav                                                  |
|-----------------------------------|--------------------------------------------------------------------------------------------------------|
| Partens partyId                   | **krav**. Aldrig personnummer eller organisationsnummer, så att kön inte innehåller dem.               |
| Partens typ, företag eller person | **krav**                                                                                               |
| Vad som har hänt                  | **krav**. Används för att kasta händelser som inte berör party-overview, när händelsetyperna är kända. |
| Ett unikt id per meddelande       | **krav**, ett UUID, för spårbarhet. Det sparas i den tekniska loggen som `message_id` ([§4.3]).        |

Det mesta i kön berör inte party-overview. `RegistryChangeListener` slår därför först upp partyId:t i index: kunderna
med partyId:t (`idx_customer_party_id`), de enskilda firmor som personen äger (`idx_customer_registry_party_id`) och
personens rad i `registry_person` (`uq_registry_person_party_id`). Ett meddelande som inte berör något av dem kastas
utan anrop.
Ett meddelande med fel format dead-letteras direkt. Loggrader från ändringskön har kanalen `MESSAGE` och meddelandets
id, men ingen källa och ingen objekttyp.

Ett meddelande om ett **företag** som är kund uppdaterar profilen:

- LegalEntity `GET /{municipalityId}/{legalEntityId}` anropas en gång, utanför transaktionen. Profilen skrivs sedan
  för kunden i varje kommun, i en transaktion per kund.
- LegalEntitys svar avgör profilen, inte meddelandet. Därför spelar meddelandenas ordning och dubbletter ingen roll, och
  kön behöver ingen single-active-consumer.
- 404 från LegalEntity lämnar profilen orörd och loggas som `PROFILE_NOT_FOUND` i den tekniska loggen ([§8.1]).
  WARN-raden i loggen har inget id.

Ett meddelande om en **person** startar kontrollen av skyddad identitet direkt, i stället för nästa natt ([§5.4]):

- Citizen `POST /{municipalityId}/batch?ShowClassified=true` anropas en gång för personen, utanför transaktionen.
- Personen som kund, och varje enskild firma som personen äger, kontrolleras sedan som i den nattliga kontrollen, i en
  transaktion per kund ([§5.4]). Nivån och flaggorna skrivs, och nivån räknas om.
- Personens rad i `registry_person` skrivs med ordningsvakten, och den nya nivån gäller då för personens alla
  kontaktrader ([§5.3]).

Meddelandet har ingen kommun, men både Citizen och LegalEntity tar en kommun i sökvägen. Anropet görs med kommunen för
den första träffen i id-ordning, eftersom svaret är detsamma för alla kommuner.

När LegalEntity eller Citizen inte svarar, eller svarar 5xx, 429 eller 408, görs meddelandet om. Till sist dead-letteras
det och larmet går ([§12.1]). Den nattliga kontrollen finns kvar som skyddsnät.

[§3.3]: 03-meddelanden.md#33-flödet-genom-tjänsten
[§5.4]: #54-skyddad-identitet
[§6]: 06-rest-api.md
[§7.2]: 07-integrationer.md#72-citizen-och-legalentity
[beslut 48]: 00-beslutslogg.md#beslut-48-okänd-skyddsstatus-när-citizen-inte-svarar
[beslut 27]: 00-beslutslogg.md#beslut-27-skyddad-identitet-med-tre-nivåer
[beslut 35]: 00-beslutslogg.md#beslut-35-ingen-särskild-loggning-av-läsningar-av-skyddade-kunder
[§13.2]: 13-risker-och-oppna-fragor.md#132-förutsättningar-före-driftsättning
[beslut 41]: 00-beslutslogg.md#beslut-41-tolkningen-av-citizens-skyddsflaggor
[§6.2]: 06-rest-api.md#62-kundsökning-opensearch
[beslut 28]: 00-beslutslogg.md#beslut-28-skyddsstatus-från-citizen-vid-skapandet-och-varje-natt
[beslut 52]: 00-beslutslogg.md#beslut-52-kontaktpersoner-kontrolleras-mot-skyddad-identitet
[beslut 54]: 00-beslutslogg.md#beslut-54-enskilda-firmor-kontrolleras-mot-folkbokföringen
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
[beslut 64]: 00-beslutslogg.md#beslut-64-den-nattliga-kontrollen-väntar-efter-404-från-legalentity
[§13.1]: 13-risker-och-oppna-fragor.md#131-öppna-frågor
[§12.1]: 12-drift.md#121-larm-för-dead-letter-köer
[§6.1]: 06-rest-api.md#61-endpoints
[beslut 61]: 00-beslutslogg.md#beslut-61-noteringar-ändras-med-optimistisk-låsning
[beslut 65]: 00-beslutslogg.md#beslut-65-verksamheter-lägger-till-egna-kontaktpersoner-via-rest
[§13.3]: 13-risker-och-oppna-fragor.md#133-risker
[beslut 7]: 00-beslutslogg.md#beslut-7-synlighet-för-engagemang-kontaktuppgifter-och-noteringar

