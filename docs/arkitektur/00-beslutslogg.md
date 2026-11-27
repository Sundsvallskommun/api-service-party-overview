# 0. Beslutslogg

Del av [arkitekturen för party-overview](README.md).

## Beslut 1: Namnet party-overview behålls

Tjänsten behåller namnet party-overview: repot `api-service-party-overview`, paketet `se.sundsvall.partyoverview` och
köerna `api-fabriken.party-overview.<källa>`.

**Valdes bort:** Byta namn till crm.

**Skäl:** Repot och namnet finns redan, så ett namnbyte vore bara kostnad.

## Beslut 2: POC-koden skrotas

POC-koden skrotas och `V1_0__create_tables.sql` skrivs om från grunden.

**Valdes bort:** En `V1_1` som tar bort POC-tabellerna.

**Skäl:** V1_0 har aldrig körts mot någon miljö.

## Beslut 3: Källsystemen är master, och payloaden lagras aldrig

Källsystemen är master för engagemang och kunddata. party-overview lagrar envelope-data men aldrig meddelandets payload.

**Valdes bort:** Att lagra hela payloaden, som POC:en gör.

**Skäl:** Tjänsten ska vara lättviktig, och en payload kan innehålla personuppgifter som inte behövs.

## Beslut 4: Kunddata kommer bara från meddelanden

Kunddata (namn, adresser, kontaktuppgifter och kontaktpersoner) kommer från meddelanden och går bara att läsa via REST.
Undantagen är namn och adress från Citizen och LegalEntity när en notering eller en kontaktperson från REST skapar
kunden ([beslut 6]), och när den nattliga kontrollen fyller i dem för en privatkund som aldrig har fått några
([§5.4]). Företagsprofilen och företagets funktionärer kommer från LegalEntity ([beslut 58], [beslut 70]), och
kontaktinställningarna från ContactSettings ([beslut 71]). Kontaktpersoner som läggs till via REST ägs av
party-overview ([beslut 65]).

**Valdes bort:** Att party-overview är master för kunddata.

**Skäl:** Källsystemen har redan datan, och det ska bara finnas en skrivväg.

## Beslut 5: Noteringar ägs av party-overview och kan höra till ett namespace

Noteringar ägs av party-overview och skapas, ändras och raderas via REST. En notering kan höra till ett namespace, som
anges i kroppen när den skapas. En notering utan namespace är global. Noteringarna läses med namespace som filter
([beslut 7]).

**Valdes bort:**

- Noteringar från källsystem, t.ex. api-service-notes. Deras noteringar är ärendedata, inte envelope-data.
- Att varje notering måste ha ett namespace.

**Skäl:** Noteringar är kundvyns egna anteckningar. En verksamhet ser sina egna genom att filtrera på sitt namespace,
och en notering som gäller hela kunden behöver inget namespace.

## Beslut 6: En notering på en okänd kund skapar kunden

En notering på en kund som inte finns skapar kunden. Citizen eller LegalEntity avgör partytypen och ger namn och adress
([§7.2]).

**Valdes bort:** Att svara 404 för okända kunder.

**Skäl:** Handläggaren ska kunna anteckna om en kund som inget källsystem har nämnt än.

## Beslut 7: Synlighet för engagemang, kontaktuppgifter och noteringar

En kund har inget namespace. Namespace finns på engagemang, kontaktuppgifter, kontaktpersoner och noteringar, och det är
alltid valfritt: något utan namespace är globalt. Ingen sökväg har namespace. Vid läsning är `namespace` ett valfritt
filter som kan anges flera gånger, och det ger de angivna namespacena plus det globala. Utan filter returneras allt. När
en notering eller kontaktperson skapas anges namespace i kroppen ([§5.1], [§6.1]).

**Valdes bort:**

- Namespace i sökvägen, som anroparens verksamhet och som en gräns för vad den ser.
- Att begränsa engagemang till eget namespace, eller att göra synligheten konfigurerbar.

**Skäl:** En kund tillhör ingen verksamhet. Den kan ha ärenden, assets och kontrakt från många namespaces, och alla
anropare hör inte till en verksamhet. Namespace hör till det som en verksamhet äger, och den som läser väljer själv
vilka namespaces den vill se. Vem som får läsa styrs av åtkomsten till API:t ([beslut 30]). Kundvyn ska ge helheten.
Engagemang kan synas för hela kommunen, eftersom det som är känsligt aldrig publiceras. SupportManagement publicerar
bara namespaces som har slagit på det ([beslut 19]) och aldrig namespaces med åtkomstkontroll ([beslut 51]). Casedata
publicerar inte sekretessmarkerade ärenden ([beslut 34]) och tills vidare bara `SBK_MEX` ([beslut 37]). party-assets
publicerar bara asset-typer på en vitlista ([beslut 50]).

## Beslut 8: En engagemangsrad per kund

Ett engagemang lagras som en rad per kund. Ett ärende med tre kunder blir tre rader.

**Valdes bort:** En delad rad med kopplingstabell.

**Skäl:** Bara med en rad per kund raderar `DELETE FROM customer` direkt i databasen allt som kunden äger, utan att
lämna föräldralösa rader med personuppgifter. Det som dupliceras är bara envelope-data. Personens kontaktrader hos andra
kunder ägs av de kunderna och raderas via `contact.party_id` ([beslut 52]).

## Beslut 9: En tabell för alla engagemangstyper

En gemensam tabell, `engagement`, med en typkolumn för ärenden, assets och kontrakt.

**Valdes bort:** En tabell per typ.

**Skäl:** Typerna har samma envelope, och kundvyns lista sorterad på "senast ändrad" blir en enda indexerad fråga i
stället för en UNION.

## Beslut 10: En historiktabell per förälder

Historiken ligger i en tabell per förälder, `engagement_history` och `note_history`, med FK och `on delete cascade`.

**Valdes bort:** En gemensam polymorf tabell.

**Skäl:** En polymorf tabell kan inte ha FK, och då försvinner kaskaden.

## Beslut 11: Den tekniska loggen har ingen FK till kunden

Den tekniska loggen har ingen FK till kunden. Den finns kvar efter en kundradering, tills den gallras efter sin
lagringstid ([beslut 60]) eller raderas manuellt via REST ([§8.2]).

**Valdes bort:** Mjukradering, eller att loggen kaskadraderas med kunden.

**Skäl:** Loggen ska kunna visa att kunden raderades, också efter raderingen.

## Beslut 12: Versionsvakt i ett register per källobjekt

Versionsvakten ligger i ett register per källobjekt, `source_object`, som saknar FK till kunderna. En DELETE lämnar en
tombstone, och en senare UPDATE med högre version väcker objektet till liv igen. Tombstones sparas för alltid, utom för
`CUSTOMER`, vars rader har kundens partyId och raderas med kunden ([§3.6]).

**Valdes bort:** En vakt på engagemangsraderna, ett register över message-id, eller gallring av tombstones.

**Skäl:** När objektet är raderat finns inga engagemangsrader kvar att jämföra mot, och en gammal UPDATE från en
grundladdning skulle då väcka det till liv. Övriga tombstones innehåller inga personuppgifter.

## Beslut 13: Radlås och single-active-consumer

Samtidighet hanteras med radlås på `source_object` och kunder (IODKU + `SELECT … FOR UPDATE`, kunderna i
partyId-ordning) med READ COMMITTED, plus `x-single-active-consumer` på varje källas kö.

**Valdes bort:** Att lita enbart på single-active-consumer, som POC:en gör.

**Skäl:** Flera köer och REST kan skriva till samma kund. Single-active-consumer behövs ändå för att objektets historik
ska komma i rätt ordning.

## Beslut 14: Tre sätt att radera en kund

En kund raderas av ett CUSTOMER DELETE-meddelande, av REST DELETE eller automatiskt när kunden saknar engagemang,
noteringar, kontaktpersoner från REST och data från `CUSTOMER`-meddelanden ([§3.6]). Den automatiska raderingen görs
direkt när kundens sista engagemang försvinner eller dess sista notering eller kontaktperson från REST raderas. När
kunden förlorar sin sista kontaktperson från REST för att personen raderas, görs den av den nattliga kontrollen
([beslut 69]).

**Valdes bort:** —

**Skäl:** Beställarens krav.

## Beslut 15: Partsrollerna avgör vem som är kund och kontaktperson

party-overview klassar varje part i ett meddelande efter dess roller i källan. Klassningen konfigureras per kommun,
källa och roll via REST: `CUSTOMER`, `CONTACT_PERSON` eller `IGNORED` ([§5.3]).

- En part med en kundroll blir kund, också när den har en kontaktroll.
- En part med en kontaktroll blir kontaktperson i objektet. Den visas på objektets engagemang hos varje kund, både
  privatkunder och företag, men aldrig på kunden, och blir aldrig själv kund.
- En part utan någon av dem ignoreras. En roll som saknas i konfigurationen loggas på WARN.

Kundens egna kontaktpersoner är ett företags funktionärer från LegalEntity och de som läggs till via REST ([beslut 65],
[beslut 70]).

**Valdes bort:**

- Att bara konfigurera kontaktrollerna och låta alla andra parter bli kunder.
- Att publiceraren klassar parterna.
- Att en kontaktperson i ett objekt också blir kontaktperson på kunden.
- Att kontaktpersoner bara gäller företagskunder.
- En lista i `application.yml`.

**Skäl:** Kunden är den som objektet gäller, och det är säkrare att ange vem det är än undantagen. En roll som saknas
ger då en uppgift som saknas och en WARN, inte en kund med uppgifter som sparas och blir sökbara. En kontaktperson i ett
ärende är kontakt för just det ärendet, oavsett om kunden är en person eller ett företag. I SupportManagement är
rollerna metadata per namespace utan uppgift om vad de betyder, så publiceraren kan inte klassa dem, och en ny roll ska
inte kräva en deploy. Med `IGNORED` ger en känd roll som inte ska med ingen WARN, så WARN visar bara roller som saknas.

## Beslut 16: Personnummer och organisationsnummer mappas till partyId

Publicerarna skickar partyId när de har det. Det som ändå kommer som personnummer eller organisationsnummer mappar
party-overview till partyId via Citizen och LegalEntity ([§7.1]). Sådana nummer lagras och loggas aldrig. En part vars
nummer inte finns, eller har fel format, hoppas över, och resten av meddelandet tillämpas, även raderingen av
engagemang för kunder som inte längre är parter ([§3.5]).

**Valdes bort:** Att bara party-overview mappar, eller att bara publicerarna gör det. Att hoppa över raderingen av
engagemang när en part inte gick att mappa.

**Skäl:** Personnummer ska färdas över köerna så sällan som möjligt, men kravtexten gäller fortfarande. Ett nummer som
inte finns blir inte bättre av omförsök. Ett nummer som aldrig går att mappa skulle annars för alltid hindra att
engagemang raderas för parter som har lämnat objektet. Tillfälliga fel görs om och når aldrig tillämpningen
([§3.7]).

## Beslut 17: En lyssnare och en mappning för alla källors köer

En lyssnarklass och en mappning hanterar alla källors köer, med en listener-container per kö ([§9]). Källan bestäms av
kön, inte av något i meddelandet. Ändringskön har en egen lyssnare ([§5.5]).

**Valdes bort:** Ett paket och en översättare per källa, som i POC:en, eller en enda container för alla köer.

**Skäl:** Det gemensamma formatet gör översättarna onödiga. Kön kan bara nås via källans egen exchange. Med en
container per kö syns varje kö för sig i dept44:s hälsokontroll `rabbitListeners`, och varje kö kan stoppas och startas
för sig.

## Beslut 18: Historiken kommer från källans aktiviteter

Historiken byggs bara av de aktiviteter meddelandet bär (vad som hände och vem som gjorde det). Ett meddelande från en
grundladdning bär inga aktiviteter.

**Valdes bort:** Att party-overview själv härleder CREATED och UPDATED.

**Skäl:** Bara källan vet vad som hände och vem som gjorde det.

## Beslut 19: SupportManagement publicerar bara namespaces med opt-in

SupportManagement publicerar bara ärenden från namespaces som har slagit på det i `namespace_config`.

**Valdes bort:** Att alltid publicera, eller att publicera utan titel.

**Skäl:** SM har åtkomstkontroll per namespace, och party-overview får inte kringgå den. Namespaces med åtkomstkontroll
per ärende publiceras inte alls ([beslut 51]).

## Beslut 20: Transaktionell outbox hos varje publicerare

Varje publicerare har en transaktionell outbox och en relay som körs direkt efter commit och schemalagt med ShedLock.
Grundladdningen går genom samma outbox.

**Valdes bort:** En gemensam outbox i dept44, eller att publicera efter commit utan outbox.

**Skäl:** Inga meddelanden får tappas, och mönstret finns redan i SM.

## Beslut 21: Namn och adress: nyast vinner

Namn och adresser följer regeln "nyast vinner", mätt på källans `sourceModified`. De ersätts som helhet, så kunden har
namnet och adresserna från det senaste meddelandet som hade något av dem ([§5.2]).

**Valdes bort:**

- Att den som skrev senast vinner.
- Att slå ihop adresserna per typ eller per källa.

**Skäl:** Resultatet blir deterministiskt oavsett i vilken ordning meddelandena kommer, även vid grundladdning. Slogs
adresserna ihop per typ eller källa skulle en adress som källan har tagit bort ligga kvar för alltid, eftersom inget
meddelande säger att den ska bort.

## Beslut 22: Kontaktuppgifter lagras per engagemang

Kontaktuppgifterna i ett objekt, kundens egna kanaler och objektets kontaktpersoner, lagras per engagemang och ersätts
som helhet varje gång ett meddelande appliceras. De visas bara på engagemanget och slås aldrig ihop till kunden, men de
är sökbara på kunden ([§5.3], [§6.2]). Kundens egna kontaktpersoner följer [beslut 15].

**Valdes bort:**

- Ett separat kontaktregister per kund.
- Att slå ihop kontaktuppgifterna från kundens objekt och visa dem på kunden.

**Skäl:** Uppgifterna följer källobjektet och försvinner med det. Det finns aldrig några inaktuella kontaktuppgifter att
städa bort. En kund kan ha många ärenden, och ett ärende många kontaktpersoner. Ihopslagna på kunden blir de en lista
som inte går att överblicka, och de gäller ärendet, inte kunden.

## Beslut 23: contract och party-assets publicerar utan namespace

contract och party-assets publicerar utan namespace. Deras engagemang och kontaktuppgifter blir därmed globala.

**Valdes bort:** Ett fast namespace per publicerare.

**Skäl:** Ingen av källorna har namespace, och beställaren valde förslaget.

## Beslut 24: Uppslag på personnummer eller organisationsnummer

party-overview kan slå upp en kund på personnummer eller organisationsnummer via `POST …/customers/lookup`, med numret i
kroppen. Numret mappas till partyId via Citizen eller LegalEntity och kastas sedan.

**Valdes bort:** Att bara ta emot partyId.

**Skäl:** Tredjepartstjänster som anropar party-overview har inte alltid partyId. Numret ska inte ligga i URL:en.

## Beslut 25: Fritextsökning med Hibernate Search och en indexeringskö

Kunder kan sökas med fritext via OpenSearch, med Hibernate Search som i SupportManagement ([§6.2]). Hibernate Search
sköter mappning, schema, fråge-DSL och MassIndexer, men dess automatiska lyssnare är avstängda. I stället markerar
tjänsten kunder för indexering i tabellen `search_index_queue`, i samma transaktion som ändringen. En relay skriver till
indexet.

**Valdes bort:**

- SM:s asynkrona lyssnare med nattlig reindexering, eller opensearch-java utan Hibernate Search.
- Att bygga om indexet bakom ett alias, så att sökningen fungerar under en reindexering.

**Skäl:** Lyssnarna ser varken IODKU, bulk-delete eller databasens kaskad. Asynkron indexering utan kö kan tappa
ändringar. Kön ger leveransgaranti och slår ihop ändringar per kund, vilket behövs vid grundladdning. En reindexering
sker sällan och tar några minuter, så det är godtagbart att sökningen är tom under tiden.

## Beslut 25a: Ingen databastrigger för raderingar i sökindexet

Ingen databastrigger fångar raderingar för sökindexet. Tjänsten markerar sina egna raderingar. En kund som raderas
direkt i databasen filtreras bort ur sökträffarna och försvinner ur indexet vid nästa reindexering.

**Valdes bort:** En `after delete`-trigger på `customer`.

**Skäl:** En trigger kräver särskilda databasrättigheter och syns inte i `schema.sql`. Att radera direkt i databasen är
en manuell undantagsåtgärd.

## Beslut 25b: Begränsad frågesyntax och kontaktfält per namespace

Sökfrågan är `simple_query_string` med AND, och klienten får inte använda full Lucene-syntax. Kontaktuppgifter,
engagemang och noteringar indexeras i ett fält per namespace, t.ex. `contacts_ns_<namespace>`, och i ett globalt fält,
inte som `nested`.

**Valdes bort:** SM:s fulla Lucene-syntax och `nested`-dokument.

**Skäl:** Full syntax kostade SM ett 40-tal rättelser. Med ett fält per namespace blir filtret en del av frågan, och
AND fungerar över namn och kontaktuppgifter.

## Beslut 26: SupportManagement publicerar inte utkast

SupportManagement publicerar inte utkast (`lifecycle = DRAFT`).

**Valdes bort:** Att publicera utkast.

**Skäl:** Ett utkast är inget engagemang än.

## Beslut 27: Skyddad identitet med tre nivåer

Skyddad identitet ([§5.4]) är ett attribut på kunden och på varje kontaktperson, med tre nivåer: `NONE`,
`SECRECY_MARKING` och `PROTECTED_REGISTRATION`. Kundens effektiva nivå är den högsta av folkbokföringens (Citizen),
CUSTOMER-meddelandenas (en per källa, [beslut 53]) och engagemangsmeddelandenas. När folkbokföringens status inte är
känd används värdet `UNKNOWN`, som ska hanteras som skyddat ([beslut 48]). En kontaktperson vars skydd inte går att
kontrollera, t.ex. för att den saknar id, har värdet `UNVERIFIED` ([§5.3]). Citizens värden för `classified` och
`protectedNR` sparas som de kom ([beslut 41]).

Namn, adresser, kontaktuppgifter, kontaktpersoner och företagsprofil sparas, indexeras och returneras av API:t oavsett
nivå. Nivån visar att uppgifterna inte får lämnas ut utan prövning, och konsumenten ansvarar för prövningen.

**Valdes bort:**

- Att inte spara namn, adresser och kontaktuppgifter för skyddade kunder, och att inte spara skyddade kontaktpersoner.
- Att spara uppgifterna men dölja dem i API:et.
- Att bara lita på publicerarnas flagga.

**Skäl:** Uppgifterna får sparas, och verksamheten behöver dem också för skyddade personer. En sekretessmarkering är en
varningssignal om att en prövning krävs innan uppgifterna lämnas ut, och skyddet ska vara tydligt utmärkt i varje vy
och hållas uppdaterat från folkbokföringens flaggor. Med Citizens egna värden kan verksamheten avgöra hanteringen utan
att förlita sig på party-overviews tolkning.

## Beslut 28: Skyddsstatus från Citizen vid skapandet och varje natt

Folkbokföringens skyddsstatus hämtas från Citizen (`ShowClassified=true`) när en privatkund eller enskild firma skapas,
och varje natt för dem och för kunder med okänd status. Natten tar med varje sådan kund vars senaste kontroll är äldre
än körningens start ([§5.4]). Ett företag som LegalEntity har svarat 404 för väntar enligt [beslut 64]. Personerna i
`registry_person` kontrolleras också varje natt ([§5.4]).

**Valdes bort:** Att bara kontrollera vid skapandet, eller att bara lita på meddelandena. Att välja kunder vars senaste
kontroll är äldre än en fast ålder, t.ex. ett dygn.

**Skäl:** Skyddet kan tillkomma eller upphöra när som helst, och vägledningen kräver att uppgifterna hålls uppdaterade.
En fast ålder skulle missa kunder som skapades eller kontrollerades under dagen före körningen.

## Beslut 29: Den tekniska loggen raderas inte automatiskt vid GDPR-radering

Den tekniska loggen raderas inte automatiskt när en kund raderas på begäran enligt GDPR. Den gallras efter sin
lagringstid ([beslut 60]) och kan raderas manuellt ([§8.2]).

**Valdes bort:** Att radera loggen direkt.

**Skäl:** partyId är ett pseudonymt id, och loggen innehåller inga andra personuppgifter.

## Beslut 30: De administrativa endpoints ligger i samma API

De administrativa endpoints (kundradering, teknisk logg och partsroller) ligger i samma API som resten.

**Valdes bort:** En egen API-produkt i gatewayen.

**Skäl:** API:et är internt, och användarna är förvaltningen själv.

## Beslut 31: Dead letter-köer utan TTL, med larm

Dead letter-köerna har ingen TTL, och drift och utvecklare har åtkomst. En health-indikator i party-overview blir `DOWN`
när någon av dem innehåller meddelanden, och Spring Boot Admin larmar då till Slack ([§12.1]).

**Valdes bort:** TTL på köerna, eller larm i brokerns egen övervakning.

**Skäl:** Inget meddelande får försvinna utan att någon har tittat på det. Spring Boot Admin med Slack-notifiering
används redan.

## Beslut 32: Organisationsnummer slås upp direkt mot LegalEntity

Organisationsnummer mappas till partyId med LegalEntitys `GET /{municipalityId}/{organizationNumber}/guid`, med numret
i URL:en. Anropet undantas från dept44:s payload-logg ([§7.1]).

**Valdes bort:** En ny POST-endpoint i api-service-party med numren i kroppen.

**Skäl:** LegalEntity saknar ett uppslag med numret i kroppen och är inte vår tjänst. En POST-endpoint i Party skulle
ändå anropa LegalEntity med numret i URL:en. Den skulle bara flytta URL:en ett steg och kräva en ändring i en annan
tjänst. För en enskild firma är numret ägarens personnummer. Det hålls borta från party-overviews payload-logg. Att det
syns i gatewayens och LegalEntitys loggar är en accepterad risk ([§13.3]).

## Beslut 33: Partsrollerna sätts via REST vid driftsättning

Partsrollerna sätts via REST vid driftsättning, för varje källa ([§12]):

- contract: avtalsparternas roller som kundroller, `CONTACT_PERSON` som kontaktroll, och `SIGNATORY` och
  fullmaktsrollerna (`POWER_OF_ATTORNEY_*`) som `IGNORED`, eftersom en avtalspart skickas med alla sina roller ([§11.4])
- Casedata: `APPLICANT` och `FELLOW_APPLICANT` som kundroller och `CONTACT_PERSON` som kontaktroll. Övriga roller i
  `SBK_MEX` klassas utifrån produktionens data.
- party-assets: `OWNER` som kundroll ([§11.3])
- SupportManagement: de roller som produktionens metadata visar, klassade tillsammans med verksamheterna

**Valdes bort:** Att lägga in startvärdena i en Flyway-migrering.

**Skäl:** Migreringarna ska vara oberoende av kommun.

## Beslut 34: Casedata publicerar ärenden, och sekretessärenden som DELETE

Casedata är en publicerare av `ERRAND` med källan `CASE_DATA` ([§11.5]). Ärenden med `confidential = true` publiceras
som `DELETE`.

**Valdes bort:** Att publicera sekretessmarkerade ärenden.

**Skäl:** Beställaren räknar Casedata som ärendekälla. Ett ärende markerat som sekretess ska inte synas för hela
kommunen.

## Beslut 35: Ingen särskild loggning av läsningar av skyddade kunder

Läsningar av skyddade kunder loggas inte särskilt i version 1.

**Valdes bort:** En egen åtkomstlogg.

**Skäl:** Utlämnandet prövas av konsumenten utifrån skyddsnivån ([beslut 27]). Läsningar kräver inte `X-Sent-By`, så
party-overview vet inte vilken användare som läser. Det står som risk i [§13.3].

## Beslut 36: En notering skapas bara med partyId

En notering kan bara skapas med partyId. En tjänst som bara har personnummer hämtar partyId från Party först.

**Valdes bort:** Att skapa en notering direkt med personnummer.

**Skäl:** Beställarens val.

## Beslut 37: Casedata publicerar bara SBK_MEX tills vidare

Casedata publicerar bara namespacet `SBK_MEX` tills dataskyddsombudet har tagit ställning till parkeringstillstånd och
färdtjänst.

**Valdes bort:** Att publicera alla namespaces från början.

**Skäl:** Parkeringstillstånd och färdtjänst kan avslöja hälsorelaterade förhållanden.

## Beslut 38: Egen OpenSearch-container per miljö

party-overview får en egen OpenSearch-container per miljö i väntan på ett riktigt kluster, som SupportManagement har.
Indexnamnet `customer` behöver därför inget miljöprefix.

**Valdes bort:** Att dela kluster med SupportManagement.

**Skäl:** Indexet byggs alltid om från databasen, så en enkel container räcker tills klustret finns.

## Beslut 39: Vad som är sökbart

Följande om kunden är sökbart: namn, adresser, kontaktuppgifter, kontaktpersonerna på kundens engagemang, ett företags
funktionärer ([beslut 70]) och kontaktpersonerna som lagts till via REST, med deras adresser, företagsprofilen och
arbetsställena, engagemangens referens, titel och kategori, samt noteringarna. Det gäller också en skyddad kund, och
träffen har kundens skyddsnivå ([§6.2]).

**Valdes bort:**

- Att bara söka på namn, adress och kontaktuppgifter.
- Att bara göra engagemangens referenser sökbara för en skyddad kund.

**Skäl:** Beställarens krav. Uppgifterna sparas också för skyddade kunder, och skyddsnivån i träffen gör att frontend
kan hantera dem som skyddade ([beslut 27]).

## Beslut 40: Sökindexets relay körs efter commit och var 10:e sekund

Sökindexets relay körs direkt efter varje commit som markerar kunder, och var 10:e sekund som skyddsnät. En ändring blir
sökbar inom någon sekund.

**Valdes bort:** Bara schemalagd körning.

**Skäl:** Med OpenSearch som eget system kan indexeringen inte ingå i databasens transaktion utan att meddelandeflödet
blir beroende av OpenSearch och håller lås medan det väntar. Med körning direkt efter commit blir fördröjningen ändå
kort.

## Beslut 41: Tolkningen av Citizens skyddsflaggor

`classified = "J"` i Citizen tolkas som sekretessmarkering, och `protectedNR = "J"` som skyddad folkbokföring.
Värdena sparas också som Citizen gav dem, i `registry_classified` och `registry_protected_nr` ([§5.4]).

**Valdes bort:** Den omvända tolkningen, som api-service-caremanagement har i sin javadoc. Att bara spara den tolkade
nivån.

**Skäl:** "NR" betyder folkbokföring (jämför fältet `nrDate`), och Citizen-dokumentationen skiljer på sekretessmarkerade
personer och personer med skyddad folkbokföring. Det verifieras mot testpersoner i C3. Med Citizens värden sparade kan
verksamheten avgöra hanteringen själv, också om tolkningen visar sig vara fel.

## Beslut 42: Kontaktkanalens typ är en fast uppräkning

Kontaktkanalens typ är `EMAIL` eller `PHONE` i meddelandet och i API:et. Varje publicerare mappar sina egna typer och
skickar inte kanaler av andra typer.

**Valdes bort:** Att ta emot källans typ som fritext.

**Skäl:** Telefonsökningen ([§6.2]) och sammanslagningen av kontakter ([§5.3]) kräver en känd typ, och källorna använder
olika värden: fritext i SupportManagement, `CELLPHONE`/`PHONE`/`EMAIL` i Casedata och separata fält i contract.

## Beslut 43: Kommun-id bara där det används

`municipality_id` finns bara på kunden och på tabeller som läses utan att gå via en kund ([§4.2]).

**Valdes bort:** Kommun-id på alla barnrader.

**Skäl:** Ingen fråga på barnraderna använder det, och inget håller värdet i synk med kunden.

## Beslut 44: Objektets id i källan heter objectId

Objektets id i källsystemet heter `objectId` i meddelandet och API:et och `object_id` i tabellerna. Det bildar par med
`objectType`.

**Valdes bort:** `sourceId` och `source_id`.

**Skäl:** `source_id` läses som källans id och ser ut som en FK, t.ex. till `source_object`. Publicerarnas outbox kallar
redan värdet `object_id`.

## Beslut 45: RabbitMQ ingår inte i readiness

RabbitMQ:s health-indikator ingår i `/actuator/health` men inte i `readiness`.

**Valdes bort:** Att ta med `rabbit` i readiness.

**Skäl:** Readiness styr bara REST-trafiken. Läs-API:t fungerar utan brokern och ska inte gå ner när den gör det.

## Beslut 46: Sökfrågan skickas i kroppen

Kundsökningen är `POST …/customers/search` med frågan i kroppen.

**Valdes bort:** `GET` med frågan i URL:en.

**Skäl:** En sökfråga kan innehålla namn, adresser och personnummer. dept44:s payload-logg sparar hela URL:en, även
query-strängen, och en URL kan också hamna i gatewayens loggar. En kropp loggas bara med de fält som står i allowlisten.

## Beslut 47: Meddelandets id finns bara i den tekniska loggen

Meddelandets `message-id` lagras bara i `technical_log.message_id`. Engagemang, historik, kontakter och
versionsregistret har inget meddelande-id ([§4.2]).

**Valdes bort:** `message_id` och `last_message_id` på varje rad som ett meddelande skriver.

**Skäl:** Den tekniska loggen har redan id:t för varje meddelande och kund, även för inaktuella meddelanden.
Applikationsloggarna maskerar id:t, eftersom det är ett UUID ([§12]). Det är den tekniska loggen som knyter en ändring
till sitt meddelande. Kolumnerna på datatabellerna skulle inte ge någon spårbarhet utöver den.

## Beslut 48: Okänd skyddsstatus när Citizen inte svarar

Ett meddelande som skapar en kund eller skriver en kontaktperson tillämpas även om Citizen eller LegalEntity inte
svarar, svarar 5xx, har en öppen circuit breaker eller saknar en post för personen. Kunden eller kontaktpersonen får då
skyddsstatusen `UNKNOWN`, som ska hanteras som skyddad tills den nattliga kontrollen har fått svar ([§5.4]). När en
notering eller en kontaktperson från REST skapar kunden blir svaret i stället 503, eftersom partytypen då inte går att
avgöra ([§7.2]). Vid 401 dead-letteras meddelandet, eftersom det är ett konfigurationsfel. README, javadoc och
OpenAPI-specen beskriver värdet.

En kontaktperson som inte kan kontrolleras alls, för att den saknar id eller för att det inte går att avgöra om ett
företag är en enskild firma, får i stället `UNVERIFIED` ([§5.3]). Den nattliga kontrollen ersätter inte det värdet. Det
ersätts bara när källan skriver om kontaktpersonen. Det räknas inte i sökträffens `hasProtectedContacts` ([§6.2]).

**Valdes bort:**

- Att göra om meddelandet tills Citizen svarar, och att sedan dead-lettera det.
- Att kontaktpersoner som inte kan kontrolleras får `UNKNOWN`.

**Skäl:** Med single-active-consumer skulle hela källans kö stå still i cirka 8 minuter per meddelande. Meddelanden som
flyttas tillbaka från dead letter-kön kommer i fel ordning och tappar sina aktiviteter. Uppgifterna sparas, och
`UNKNOWN` visar att de ska hanteras som skyddade tills statusen är känd. Ett eget värde visar att statusen är okänd, i
stället för en skyddsnivå som personen kanske inte har. `UNKNOWN` betyder alltid att den nattliga kontrollen ersätter
värdet. Kontaktpersoner utan id är vanliga. Med `UNKNOWN` för dem skulle `hasProtectedContacts` vara sant för nästan
alla företag och inte skilja ut dem som har skyddade kontaktpersoner.

## Beslut 49: contract publicerar bara avtalsparter

contract skickar bara stakeholders med minst en partsroll eller rollen `CONTACT_PERSON`, och aldrig stakeholders av
typen `MUNICIPALITY` ([§11.4]). Den som bara skriver under (`SIGNATORY`) eller har fullmakt (`POWER_OF_ATTORNEY_*`)
skickas inte.

**Valdes bort:** Att skicka alla stakeholders, eller att göra `SIGNATORY` och fullmaktsrollerna till kontaktroller.

**Skäl:** Kommunen själv kan vara part, t.ex. som `LESSOR`, och skulle då bli kund med ett engagemang per kontrakt. Den
som bara skriver under eller har fullmakt företräder en part och är inte själv kund. När de inte skickas hålls deras
personuppgifter borta från kön.

## Beslut 50: party-assets publicerar bara asset-typer på en vitlista

party-assets publicerar bara assets vars typ står på en vitlista i dess konfiguration. Parkeringstillstånd för
rörelsehindrade står inte på listan förrän dataskyddsombudet har svarat ([§11.3]).

**Valdes bort:** Att publicera alla asset-typer.

**Skäl:** Ett parkeringstillstånd för rörelsehindrade är en hälsouppgift, och engagemang syns för hela kommunen.
Casedatas parkeringsärenden hålls redan tillbaka ([beslut 37]), och party-assets får inte bli en väg runt det.

## Beslut 51: Namespaces med åtkomstkontroll i SM publiceras inte

Ett namespace i SupportManagement med `accessControl = true` kan inte slå på publiceringen till party-overview
([§11.2]).

**Valdes bort:** Att bara publicera ärenden utan åtkomstetiketter, och bara de fält som får läsas vid begränsad läsrätt.

**Skäl:** I sådana namespaces styr SM läsrätten per ärende med etiketter och trimmar fälten per roll. party-overview
visar engagemang för hela kommunen och kan inte återskapa den kontrollen.

## Beslut 52: Kontaktpersoner kontrolleras mot skyddad identitet

En kontaktperson sparas oavsett skydd och har en egen skyddsnivå med Citizens värden ([§5.3]). Kontaktraden sparar
partyId, källans `protection` och `registry_party_id`, som är personen själv eller, för en enskild firma, ägaren.
Folkbokföringens status lagras en gång per person i `registry_person`, med samma ordningsvakt som för kunden, och hålls
aktuell av meddelandeflödet, den nattliga kontrollen och ändringskön. Nivån räknas fram när raden läses: den högsta
av källans `protection` och kundens nivå om kontaktpersonen är kund i kommunen, annars personens status. Raderna från
källornas objekt tas bort när personen raderas, också automatiskt. Anger källan fortfarande personen som kontaktperson
kommer raden tillbaka nästa gång objektet ändras i källan. Funktionärernas rader tas bara bort när personen raderas med
`CUSTOMER DELETE` eller via REST ([beslut 70]).

**Valdes bort:**

- Att skyddet bara gäller kunden och inte kontaktpersonen.
- Att inte spara en skyddad kontaktperson.
- Att bara sätta nivån när raden skrivs, utan partyId på raden.
- Att kopiera personens status till varje kontaktrad.
- Att aldrig spara en enskild firma som kontaktperson.
- Att raderna ligger kvar när personen raderas automatiskt som kund.

**Skäl:** Vägledningen räknar arbetsgivare som en skyddsvärd uppgift, och en kontaktperson visar kopplingen till ett
företag eller en annan person. Uppgifterna får sparas, men nivån ska synas och vara aktuell ([beslut 27]). Utan partyId
på raden skulle varken ett nytt skydd eller en radering enligt GDPR nå personens kontaktrader, och utan ägaren skulle
ägarens skydd inte nå en enskild firmas rader. Med statusen kopierad till varje rad skulle ett äldre svar från Citizen
kunna skriva över ett nyare på alla personens rader, och en ny nivå skulle skriva om och låsa tusentals rader för en
person som är kontaktperson på många ärenden. Med en rad per person räcker en skrivning, och ordningsvakten fungerar som
för kunden. Uppgifter sparas inte längre än de behövs, och källan anger personen igen när objektet ändras.

## Beslut 53: Skyddet från `CUSTOMER`-meddelanden lagras per källa

Varje källas skydd från ett `CUSTOMER`-meddelande lagras i en egen rad i `asserted_protection`, och den högsta gäller
([§4.3]).

**Valdes bort:** En kolumn på kunden, som det senaste meddelandet skriver över.

**Skäl:** Med en kolumn skulle ett meddelande utan skydd från en källa ta bort skyddet som en annan källa har angett,
och då skulle en skyddad person visas som oskyddad.

## Beslut 54: Enskilda firmor kontrolleras mot folkbokföringen

För en enskild firma, där organisationsnumret är ägarens personnummer, hämtas ägarens skydd från folkbokföringen på
samma sätt som för en privatkund, också i den nattliga kontrollen ([§5.4]). Ett legalId för `ENTERPRISE` får därför
vara ett personnummer med 10 eller 12 siffror, och det slås alltid upp mot LegalEntity ([§3.1]). Ett tiosiffrigt
nummer får det århundrade som gör ägaren yngre än 100 år. Numret sparas aldrig, utan mappas till partyId:t.

**Valdes bort:** Att bara kontrollera privatkunder. Att slå upp numret mot Citizen när det är ett personnummer.

**Skäl:** En enskild firmas namn och adress är ofta ägarens, och en skyddad ägare ska vara utmärkt också via sin firma.
Slogs numret upp mot Citizen skulle firmans engagemang hamna på ägarens privatkund. Hundraårsregeln är Skatteverkets
regel för skiljetecknet i ett tiosiffrigt personnummer.

## Beslut 55: Kommunikation i SM publiceras inte

SupportManagement publicerar bara när ärendet självt ändras. E-post, sms och webbmeddelanden till och från ärendet
publiceras inte ([§11.2]).

**Valdes bort:** Att publicera kommunikationen som aktiviteter i historiken.

**Skäl:** Kundbilden visar ärendet, inte meddelandena som skickas till och från det. Kommunikationen ökar inte ärendets
version, så den kan inte publiceras som en egen ändring.

## Beslut 56: party-overview anropar inte api-service-party

partyId slås upp direkt mot Citizen och LegalEntity, och partytypen avgörs med de anrop som ändå görs mot dem
([§7.1]). party-overview anropar inte api-service-party.

**Valdes bort:** Att slå upp partyId och partytyp via api-service-party.

**Skäl:** api-service-party är ett tunt lager ovanpå Citizen och LegalEntity, och partyId är deras id: Citizens
`personId` för en person och LegalEntitys id för ett företag. Tjänsten anropar redan båda, så Party skulle bara ge ett
beroende till. Med direkta anrop syns fel per element i Citizens batchsvar, som Party gör om till att numret inte finns.
Partytypen avgörs också utan att personnummer hämtas.

## Beslut 57: En kund kan ha flera adresser, och en kontaktperson har en

En kund har noll eller flera adresser i en egen tabell, `customer_address`, var och en med en typ: postadress,
besöksadress, fakturaadress eller folkbokföringsadress ([§5.2]). En kontaktperson har en adress på sin kontaktrad, vald
efter typ ([§5.3]). Formatet har en lista med adresser per part, och publicerarna skickar alla adresser som källan har.

**Valdes bort:**

- Adresskolumner på `customer`, som bara rymmer en adress.
- En gemensam adresstabell för kunder och kontaktpersoner, som skulle ha en ägare av två slag och ingen FK med kaskad.
- Alla adresser för en kontaktperson.

**Skäl:** Casedata har flera adresser per part, och contract och LegalEntity skiljer på postadress och besöksadress. En
kontaktperson behöver bara en adress för att gå att nå, och färre adresser är färre personuppgifter.

## Beslut 58: Företagsuppgifter lagras i en egen profil

Uppgifterna om ett företag från LegalEntity, som storleksklass för anställda och omsättning, juridisk form, pågående
konkurs och arbetsställen, lagras i `enterprise_profile`, en rad per företagskund, med typade kolumner. Arbetsställena,
med sina adresser och SNI-koder, och de pågående förfarandena ligger i egna tabeller under profilen ([§5.5]).
Företagets SNI-koder räknas fram ur arbetsställenas när profilen läses, med huvudarbetsställets först. Kodernas sökväg i
SNI lagras i ett eget träd ([beslut 72]).

**Valdes bort:**

- Kolumner direkt på `customer`.
- En JSON-kolumn eller en tabell med nyckel och värde.
- Att spara företagets framräknade SNI-koder.
- Arbetsställets adresser som kolumner på arbetsställets rad.
- Firmateckningen (`fullSignatureDescription`).
- Organisationsnumret.
- Ett tak för antalet arbetsställen.

**Skäl:** Kundraden låses i varje meddelande och ska vara smal, och kolumnerna gäller inte privatkunder. Profilen hämtas
i en egen takt och ersätts som en helhet. Med typade kolumner ser schemakontrollen fälten, och SNI-koderna går att fråga
på. SCB:s företagsnivå är aggregerad från arbetsställena, men LegalEntity ger bara arbetsställenas koder, utan andelar.
Företagets koder följer av arbetsställenas, så att spara dem vore att spara samma uppgift två gånger. Arbetsställets
adresser har samma form som kundens och kontaktpersonens. Firmateckningen är fritext med namn och personnummer, och
funktionärerna sparas i stället som kontaktpersoner ([beslut 70]). Kunden identifieras med partyId:t, och ett
organisationsnummer mappas alltid till det ([beslut 54]). Ett tak skulle ge en lista som inte stämmer med antalet
arbetsställen, utan att det syns. Ett företag med hundratals arbetsställen skrivs om bara när profilen har ändrats, och
då i JDBC-batchar ([§13.3]).

## Beslut 59: Ändringskön uppdaterar företagsprofilen och kontrollerar personers skydd

party-overview lyssnar på en kö med ändringar av alla företag och personer. För ett meddelande om ett företag som är
kund hämtar party-overview företaget från LegalEntity och skriver profilen. Den senaste hämtningen vinner. Ett
meddelande om en person som är kund, äger en enskild firma som är kund eller är kontaktperson startar kontrollen av
skyddad identitet direkt ([§5.5]). Övriga meddelanden kastas.

**Valdes bort:**

- Att läsa profilen ur meddelandet.
- Att hämta alla profiler med ett schemalagt jobb.
- Att bara låta den nattliga kontrollen fånga ändrat skydd för personer.

**Skäl:** När LegalEntitys och Citizens svar avgör spelar meddelandenas ordning och dubbletter ingen roll, och
party-overview behöver bara partyId:t, partens typ och vad som har hänt. Ett schemalagt jobb skulle anropa LegalEntity
för varje företag, också de som inte har ändrats, och ändå visa en ändring först efter nästa körning. Ett nytt skydd
slår igenom inom minuter i stället för nästa natt.

## Beslut 60: Den tekniska loggen gallras efter en lagringstid

Rader i den tekniska loggen som är äldre än `technical-log.retention` raderas av ett schemalagt jobb varje natt
([§8.2]). Förvalet är fem år. Lagringstiden bestäms med arkivfunktionen och dataskyddsombudet ([§13.1]).

**Valdes bort:** Att loggen bara raderas manuellt.

**Skäl:** GDPR:s krav på lagringsbegränsning. Loggen innehåller partyId och vem som gjorde vad, och den behövs för
spårbarhet en längre tid men inte för alltid.

## Beslut 61: Noteringar ändras med optimistisk låsning

En notering har en version, som API:t returnerar som `ETag`. `PATCH` och `DELETE` kräver `If-Match` med den versionen
([§6.1]). `POST` har ingen idempotensnyckel.

**Valdes bort:**

- Att den som sparar sist vinner.
- En `Idempotency-Key` på `POST`.

**Skäl:** Utan versionen skriver den som sparar sist över en annans ändring utan att någon märker det. `If-Match` med
`ETag` är HTTP:s standard för det (RFC 9110). En notering som skapas två gånger syns och kan raderas, så en
idempotensnyckel är inte värd sin komplexitet.

## Beslut 62: Meddelandeformaten följer CloudEvents

Det gemensamma formatet (F1) och ändringskön (F2) följer CloudEvents, med AMQP-bindningen: `id` är meddelandets id,
`source` källan, `type` vad som har hänt, `time` när det hände och `subject` objektets eller partens id ([§3.1],
[§5.5]).

**Valdes bort:** Egna fältnamn för kuvertet.

**Skäl:** CloudEvents är branschstandarden för händelsekuvert, och AsyncAPI och de flesta verktyg stödjer den.

## Beslut 63: Primärnycklar är slumpmässiga UUID

Primärnycklarna är UUID version 4 från `@UuidGenerator`, som i organisationens andra tjänster ([§4.2]).

**Valdes bort:** Tidsordnade UUID (version 7).

**Skäl:** Tidsordnade nycklar ger InnoDB färre siddelningar, men volymerna är måttliga, och samma konvention som i
övriga tjänster gör förvaltningen enklare.

## Beslut 64: Den nattliga kontrollen väntar efter 404 från LegalEntity

När LegalEntity svarar 404 för en kund i den nattliga kontrollen anropas LegalEntity inte för kunden igen förrän
`protection.legal-entity-retry` har gått, med förvalet en vecka ([§5.4]). Kontrollen mot Citizen påverkas inte.

**Valdes bort:**

- Att anropa LegalEntity varje natt.
- Att aldrig anropa LegalEntity igen för kunden.

**Skäl:** Ett företag som LegalEntity inte känner skulle annars slås upp varje natt så länge kunden finns. Svarar
LegalEntity senare med företaget får kunden sin status och profil utan manuell åtgärd. Ett tillfälligt fel ger inte
404 och ger därför inget uppehåll.

## Beslut 65: Verksamheter lägger till egna kontaktpersoner via REST

Kontaktpersoner kan läggas till, ändras och raderas på en kund via REST ([§5.3], [§6.1]). Tillsammans med ett företags
funktionärer från LegalEntity är det kundens enda kontaktpersoner, eftersom kontaktpersonerna från källorna bara hör
till sitt objekt ([beslut 15], [beslut 70]). Namespace är valfritt och anges i kroppen, och en kontaktperson utan
namespace är global. Kundens kontaktpersoner från REST hämtas alla, eller filtrerade på ett eller flera namespaces, som
i [beslut 7]. Kontaktpersonerna lagras i `manual_contact_person` och ändras med optimistisk låsning, som en notering
([beslut 61]). Skyddsnivån räknas fram som för kontaktpersoner från källorna ([beslut 52]). Finns kunden inte skapas
den, som av en notering ([beslut 6]). En kund som har sådana kontaktpersoner raderas inte automatiskt. En persons
kontaktpersoner från REST hos andra kunder raderas bara när personen raderas med `CUSTOMER DELETE` eller via REST, inte
vid en automatisk radering, eftersom ingen källa kan skapa dem igen ([§3.6]).

**Valdes bort:**

- Att kontaktpersoner bara kommer från källornas objekt.
- Att lagra dem i `contact`, bland raderna från meddelandena.
- Att namespace krävs.
- Att en kontaktperson aldrig kan vara global.

**Skäl:** Verksamheterna behöver kontaktpersoner som inget källsystem känner till, och varje verksamhet ska kunna ha
sina egna. Alla kontaktpersoner hör inte till en verksamhet, och den som läser ska kunna se alla eller välja
namespaces. Raderna i `contact` ersätts som helhet av meddelandena och uppdateras aldrig, medan en kontaktperson från
REST ändras på plats, har en version och visar vem som skapade och ändrade den.

## Beslut 66: Köernas inställningar sätts med policyer

Dead-lettering, `delivery-limit`, `dead-letter-strategy` och `overflow` sätts med en policy per kö i tjänstens
driftkonfiguration. Bara kötypen och `x-single-active-consumer` är kö-argument ([§3.2]).

**Valdes bort:** Att sätta alla inställningar som kö-argument när kön skapas.

**Skäl:** En befintlig kö kan inte få nya eller ändrade argument utan att skapas om, och en kö som skapas om måste
först tömmas. En policy kan läggas till och ändras på en befintlig kö. Kötypen och single-active-consumer går inte att
sätta med en policy.

## Beslut 67: Utkast publiceras inte i party-assets och contract

En asset eller ett kontrakt med status `DRAFT` publiceras som `DELETE`, som andra objekt som inte ska synas
([§11.1]). När objektet lämnar `DRAFT` har versionen ökat, och det publiceras som `UPDATE`.

**Valdes bort:** Att publicera utkast som vanliga objekt.

**Skäl:** Ett utkast är inget engagemang än, som i SupportManagement ([beslut 26]). party-assets håller också utkast
utanför sin vanliga lista över assets (`GET /{municipalityId}/assets`).

## Beslut 68: Lyssnarna använder direct-containern

party-overviews lyssnare använder Spring AMQP:s direct-container med `missing-queues-fatal: false` ([§12]), i stället
för dept44:s förval, simple-containern. Saknas en kö eller AMQP-användaren försöker containern igen tills de finns.
`DeadLetterHealthIndicator` kontrollerar köerna och larmar under tiden ([§12.1]).

**Valdes bort:** Simple-containern. När den saknar sin kö eller sin användare stoppas den för gott, och dept44-startern
markerar då liveness som `BROKEN`, så att podden startas om.

**Skäl:** En omstart tar ner REST-API:t också, och tjänsten startar om i en loop tills kön eller användaren finns. En
felkonfigurerad kö ska larma, inte ta ner kundvyn för hela kommunen. Startern kör varje direct-konsument i en egen
virtuell tråd, så köerna väntar inte på varandra.

## Beslut 69: Den nattliga kontrollen raderar kunder utan innehåll

Den nattliga kontrollen raderar de kunder som inte har något kvar: varken engagemang, noteringar, kontaktpersoner från
REST eller `CUSTOMER`-data ([§3.6], [§5.4]). En sådan kund uppstår när en person raderas med `CUSTOMER DELETE` eller
via REST, och kunden därmed förlorar sin sista kontaktperson från REST ([beslut 65]). Raderingen är automatisk, som i
[beslut 14], och loggas med kanalen `SCHEDULER`.

**Valdes bort:**

- Att radera kunden direkt, i samma transaktion som personen.
- Att låta kunden ligga kvar.

**Skäl:** Personen kan vara kontaktperson hos många kunder. En direkt radering skulle låsa och kontrollera dem alla i
personens transaktion, med fler lås och större risk för deadlock. En kund som ligger kvar är sökbar med namn och
adress utan att ha något i kommunen, och ingenting skulle någonsin radera den. Kontrollen fångar också en kund som har
blivit tom på något annat sätt.

## Beslut 70: Ett företags funktionärer blir kontaktpersoner

När ett företags profil skrivs blir varje funktionär i LegalEntitys `officers`, t.ex. styrelseledamöter, firmatecknare
och revisorer, en kontaktperson på företagskunden, med namn och funktion ([§5.5]). Funktionärens personnummer, eller
organisationsnummer för t.ex. ett revisionsbolag, slås upp till partyId och sparas aldrig. Skyddet kontrolleras som för
andra kontaktpersoner ([beslut 52]). Funktionärerna ersätts som helhet med profilen och är globala. En funktionärs rad
tas bara bort när personen raderas med `CUSTOMER DELETE` eller via REST, som en kontaktperson från REST.

**Valdes bort:**

- Att inte spara funktionärerna.
- Att visa kontaktpersonerna från kundens objekt på kunden ([beslut 22]).
- Att ta bort en funktionärs rad också när personen raderas automatiskt.

**Skäl:** Kundens kontaktpersoner ska vara de som företräder kunden, inte alla som förekommer i dess ärenden.
Funktionärerna företräder företaget enligt Bolagsverket och hålls aktuella med profilen. Numret behövs bara för
uppslaget av partyId, och med partyId fungerar skyddet och raderingen av en person som för andra kontaktpersoner.
Profilen skrivs bara om när den har ändrats, så en rad som togs bort automatiskt skulle inte komma tillbaka.

## Beslut 71: Kontaktinställningarna sparas för alla med partyId

ContactSettings publicerar sina kontaktinställningar som objekttypen `CONTACT_SETTING`, med outbox och grundladdning som
de andra publicerarna ([§11.6]). party-overview sparar varje inställning som har ett partyId i `contact_setting`, också
för personer som inte är kunder. När personen är kund visas inställningens kanaler bland kundens kontaktuppgifter och
är sökbara ([§5.6]). En inställning skapar ingen kund och håller ingen kund kvar.

**Valdes bort:**

- Att läsa ContactSettings när kunden visas.
- Att bara spara inställningen när personen är kund, och hämta den från ContactSettings när kunden skapas.
- Att en inställning skapar en kund.

**Skäl:** Kontaktuppgifterna ska vara sökbara, och ändringar ska nå party-overview utan att varje läsning anropar
ContactSettings. Den som har en inställning med partyId har en koppling till kommunen, så uppgifterna får sparas också
innan personen är kund. Då behövs varken ett anrop när kunden skapas eller en nattlig hämtning, och inställningen syns
direkt när personen blir kund. En inställning är inget engagemang och gör ingen till kund.

## Beslut 72: SNI-kodernas sökväg kommer från LegalEntity och lagras som ett träd

Varje SNI-kod visas med sin sökväg i SNI, från avdelningen till koden själv, med alla fem nivåerna ([§5.5]). LegalEntity
ger sökvägen (F3, [§13.1]). party-overview sparar den som ett träd i `sni_classification`, med en nod per kod och en
pekare till föräldern ([§4.3]). Arbetsställets kod pekar på sin nod med koden, utan FK, och sökvägen slås upp när
profilen läses.

**Valdes bort:**

- SCB:s struktur för SNI, inläst i party-overview med Flyway.
- Sökvägen sparad på varje kodrad under arbetsstället.
- En FK från arbetsställets kod till trädet.
- Bara avdelning, huvudgrupp och koden.

**Skäl:** LegalEntity ger redan kodernas namn. Ger den också sökvägen behöver ingen klassifikation underhållas i
party-overview, inte heller när SNI byter version. Sökvägen hör till koden och inte till arbetsstället. I ett träd
lagras den en gång, en ändrad nod gäller direkt för alla kunder, och koderna går att gruppera i SQL på varje nivå. På
varje kodrad skulle samma sökväg upprepas för tusentals arbetsställen, och en ändring skulle bara nå de profiler som
skrivs om. Med en FK skulle varje kod behöva en nod innan dess sökväg är känd, och en kod som inte följer SNI:s form
skulle inte gå att spara. Med alla fem nivåerna väljer konsumenten vilka den visar.

[§4.2]: 04-datamodell.md#42-gemensamt-för-alla-tabeller
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§6.2]: 06-rest-api.md#62-kundsökning-opensearch
[§8.2]: 08-teknisk-logg.md#82-radering-av-den-tekniska-loggen
[§11.5]: 11-publicerare.md#115-casedata-errand-källan-case_data
[§12.1]: 12-drift.md#121-larm-för-dead-letter-köer
[§11.4]: 11-publicerare.md#114-contract-contract
[beslut 15]: #beslut-15-partsrollerna-avgör-vem-som-är-kund-och-kontaktperson
[beslut 19]: #beslut-19-supportmanagement-publicerar-bara-namespaces-med-opt-in
[beslut 34]: #beslut-34-casedata-publicerar-ärenden-och-sekretessärenden-som-delete
[beslut 37]: #beslut-37-casedata-publicerar-bara-sbk_mex-tills-vidare
[beslut 48]: #beslut-48-okänd-skyddsstatus-när-citizen-inte-svarar
[beslut 27]: #beslut-27-skyddad-identitet-med-tre-nivåer
[beslut 41]: #beslut-41-tolkningen-av-citizens-skyddsflaggor
[§3.6]: 03-meddelanden.md#36-kundradering
[§9]: 09-kodstruktur.md
[beslut 6]: #beslut-6-en-notering-på-en-okänd-kund-skapar-kunden
[beslut 61]: #beslut-61-noteringar-ändras-med-optimistisk-låsning
[beslut 65]: #beslut-65-verksamheter-lägger-till-egna-kontaktpersoner-via-rest
[§4.3]: 04-datamodell.md#43-tabellerna
[§11.2]: 11-publicerare.md#112-supportmanagement-errand
[§11.3]: 11-publicerare.md#113-party-assets-asset
[beslut 50]: #beslut-50-party-assets-publicerar-bara-asset-typer-på-en-vitlista
[beslut 51]: #beslut-51-namespaces-med-åtkomstkontroll-i-sm-publiceras-inte
[beslut 53]: #beslut-53-skyddet-från-customer-meddelanden-lagras-per-källa
[§7.1]: 07-integrationer.md#71-uppslag-av-partyid
[§7.2]: 07-integrationer.md#72-citizen-och-legalentity
[§13.3]: 13-risker-och-oppna-fragor.md#133-risker
[beslut 52]: #beslut-52-kontaktpersoner-kontrolleras-mot-skyddad-identitet
[§3.1]: 03-meddelanden.md#31-krav-på-det-gemensamma-formatet
[§3.5]: 03-meddelanden.md#35-tillämpning
[§3.7]: 03-meddelanden.md#37-felhantering
[§12]: 12-drift.md
[§5.2]: 05-synlighet-och-kunddata.md#52-namn-och-adress
[beslut 58]: #beslut-58-företagsuppgifter-lagras-i-en-egen-profil
[§5.5]: 05-synlighet-och-kunddata.md#55-företagsuppgifter
[beslut 54]: #beslut-54-enskilda-firmor-kontrolleras-mot-folkbokföringen
[beslut 64]: #beslut-64-den-nattliga-kontrollen-väntar-efter-404-från-legalentity
[beslut 60]: #beslut-60-den-tekniska-loggen-gallras-efter-en-lagringstid
[§13.1]: 13-risker-och-oppna-fragor.md#131-öppna-frågor
[§6.1]: 06-rest-api.md#61-endpoints
[beslut 30]: #beslut-30-de-administrativa-endpoints-ligger-i-samma-api
[§5.1]: 05-synlighet-och-kunddata.md#51-synlighet-per-namespace
[beslut 7]: #beslut-7-synlighet-för-engagemang-kontaktuppgifter-och-noteringar
[§3.2]: 03-meddelanden.md#32-topologi
[§11.1]: 11-publicerare.md#111-gemensamt-för-alla-publicerare
[beslut 26]: #beslut-26-supportmanagement-publicerar-inte-utkast
[beslut 14]: #beslut-14-tre-sätt-att-radera-en-kund
[beslut 69]: #beslut-69-den-nattliga-kontrollen-raderar-kunder-utan-innehåll
[beslut 70]: #beslut-70-ett-företags-funktionärer-blir-kontaktpersoner
[beslut 22]: #beslut-22-kontaktuppgifter-lagras-per-engagemang
[§11.6]: 11-publicerare.md#116-contactsettings-contact_setting
[§5.6]: 05-synlighet-och-kunddata.md#56-kontaktinställningar
[beslut 71]: #beslut-71-kontaktinställningarna-sparas-för-alla-med-partyid
[beslut 72]: #beslut-72-sni-kodernas-sökväg-kommer-från-legalentity-och-lagras-som-ett-träd

