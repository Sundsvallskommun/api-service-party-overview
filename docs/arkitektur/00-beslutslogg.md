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
Undantaget är namn och adress från Citizen och LegalEntity när en notering skapar kunden ([beslut 6]), och när den
nattliga kontrollen fyller i dem för en privatkund ([§5.4]). Företagsprofilen kommer från LegalEntity ([beslut 58]).

**Valdes bort:** Att party-overview är master för kunddata.

**Skäl:** Källsystemen har redan datan, och det ska bara finnas en skrivväg.

## Beslut 5: Noteringar ägs av party-overview och hör till ett namespace

Noteringar ägs av party-overview och skapas, ändras och raderas via REST. En notering hör alltid till ett namespace, och
det finns inga globala noteringar.

**Valdes bort:** Noteringar från källsystem, t.ex. api-service-notes. Deras noteringar är ärendedata, inte
envelope-data, och samlas inte in.

**Skäl:** Noteringar är kundvyns egna anteckningar, och kravet är att en verksamhet bara ser sina egna.

## Beslut 6: En notering på en okänd kund skapar kunden

En notering på en kund som inte finns skapar kunden. Citizen eller LegalEntity avgör partytypen och ger namn och adress
([§7.2]).

**Valdes bort:** Att svara 404 för okända kunder.

**Skäl:** Handläggaren ska kunna anteckna om en kund som inget källsystem har nämnt än.

## Beslut 7: Synlighet för engagemang, kontaktuppgifter och noteringar

Engagemang syns för alla verksamheter i kommunen, med namespace som valfritt filter. Kontaktuppgifter syns för det egna
namespacet plus de globala. Noteringar syns bara för det egna namespacet.

**Valdes bort:** Att begränsa engagemang till eget namespace, eller att göra synligheten konfigurerbar.

**Skäl:** Kundvyn ska ge helheten. Engagemang kan synas för hela kommunen, eftersom det som är känsligt aldrig
publiceras. SupportManagement publicerar bara namespaces som har slagit på det ([beslut 19]) och aldrig namespaces med
åtkomstkontroll ([beslut 51]). Casedata publicerar inte sekretessmarkerade ärenden ([beslut 34]) och tills vidare bara
`SBK_MEX` ([beslut 37]). party-assets publicerar bara asset-typer på en vitlista ([beslut 50]).

## Beslut 8: En engagemangsrad per kund

Ett engagemang lagras som en rad per kund. Ett ärende med tre kunder blir alltså tre rader.

**Valdes bort:** En delad rad med kopplingstabell.

**Skäl:** Det är det enda alternativet där `DELETE FROM customer` direkt i databasen tar med allt kunden äger och inte
lämnar föräldralösa rader med personuppgifter. Det som dupliceras är bara envelope-data. Personens kontaktrader hos
andra företag ägs av företagen och raderas via `contact.party_id` ([beslut 52]).

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

Den tekniska loggen har ingen FK till kunden. Den finns kvar efter en kundradering och sparas tills den raderas manuellt
via REST ([§8.2]).

**Valdes bort:** Mjukradering, att loggen kaskadraderas med kunden, eller automatisk gallring.

**Skäl:** Loggen ska kunna visa att kunden raderades. Beställaren vill behålla loggen tills någon aktivt raderar den.

## Beslut 12: Versionsvakt i ett register per källobjekt

Versionsvakten ligger i ett register per källobjekt, `source_object`, som saknar FK till kunderna. En DELETE lämnar en
tombstone, och en senare UPDATE med högre version väcker objektet till liv igen. Tombstones sparas för alltid, utom för
`CUSTOMER`, vars rader har kundens partyId och raderas med kunden ([§3.6]).

**Valdes bort:** En vakt på engagemangsraderna, ett register över message-id, eller gallring av tombstones.

**Skäl:** När objektet är raderat finns inga engagemangsrader kvar att jämföra mot, och en gammal UPDATE från en
grundladdning skulle då väcka det till liv. Övriga tombstones innehåller inga personuppgifter.

## Beslut 13: Radlås och single-active-consumer

Samtidighet hanteras med radlås på `source_object` och kunder (IODKU + `SELECT … FOR UPDATE`, kunderna i
partyId-ordning) med READ COMMITTED, plus `x-single-active-consumer` på varje kö.

**Valdes bort:** Att lita enbart på single-active-consumer, som POC:en gör.

**Skäl:** Flera köer och REST kan skriva till samma kund. Single-active-consumer behövs ändå för att objektets historik
ska komma i rätt ordning.

## Beslut 14: Tre sätt att radera en kund

En kund raderas av ett CUSTOMER DELETE-meddelande, av REST DELETE eller automatiskt när kunden saknar engagemang,
noteringar och data från `CUSTOMER`-meddelanden ([§3.6]).

**Valdes bort:** —

**Skäl:** Beställarens krav.

## Beslut 15: party-overview härleder kontaktpersoner

party-overview härleder kontaktpersoner. En part med kontaktpersonsroll blir kontaktperson på objektets företagskunder
men blir aldrig själv kund. Vilka roller som räknas som kontaktpersonsroller konfigureras per kommun och källa via REST.

**Valdes bort:** Att publiceraren kopplar ihop dem, eller en lista i `application.yml`.

**Skäl:** Beställarens val. I SM är rollerna metadata per namespace, så nya roller ska inte kräva en deploy.

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

## Beslut 17: En lyssnare och en mappning för alla köer

En lyssnarklass och en mappning hanterar alla köer, med en listener-container per kö ([§9]). Källan bestäms av kön,
inte av något i meddelandet.

**Valdes bort:** Ett paket och en översättare per källa, som i POC:en, eller en enda container för alla köer.

**Skäl:** Det gemensamma formatet gör översättarna onödiga. Kön kan dessutom bara nås via källans egen exchange. Med en
enda container delar köerna en konsumenttråd, och ett omförsök på en källa stoppar alla.

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

Varje publicerare har en transaktionell outbox och en relay som körs direkt efter commit och dessutom schemalagt med
ShedLock. Grundladdningen går genom samma outbox.

**Valdes bort:** En gemensam outbox i dept44, eller att publicera efter commit utan outbox.

**Skäl:** Inga meddelanden får tappas, och mönstret finns redan i SM.

## Beslut 21: Namn och adress: nyast vinner

Namn och adresser följer regeln "nyast vinner", mätt på källans `sourceModified`. De ersätts som helhet, så kunden har
namnet och adresserna från det senaste meddelandet som hade något av dem ([§5.2]).

**Valdes bort:**

- Att den som skrev senast vinner.
- Att slå ihop adresserna per typ eller per källa.

**Skäl:** Ordningen blir deterministisk oavsett i vilken ordning meddelandena kommer, även vid grundladdning. Slogs
adresserna ihop per typ eller källa skulle en adress som källan har tagit bort ligga kvar för alltid, eftersom inget
meddelande säger att den ska bort.

## Beslut 22: Kontaktuppgifter lagras per engagemang

Kontaktuppgifter lagras per engagemang och ersätts som helhet varje gång ett meddelande appliceras. Dubbletter slås ihop
vid läsning.

**Valdes bort:** Ett separat kontaktregister per kund.

**Skäl:** Uppgifterna följer källobjektet och försvinner med det. Det finns aldrig några inaktuella kontaktuppgifter att
städa bort.

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
sköter mappning, schema, fråge-DSL och MassIndexer, men dess automatiska lyssnare är avstängda. I stället markeras
kunder för indexering i tabellen `search_index_queue` i samma transaktion som ändringen, och en relay skriver till
indexet.

**Valdes bort:** SM:s asynkrona lyssnare med nattlig reindexering, eller opensearch-java utan Hibernate Search.

**Skäl:** Lyssnarna ser varken IODKU, bulk-delete eller databasens kaskad. Asynkron indexering utan kö kan tappa
ändringar. Kön ger leveransgaranti och slår ihop ändringar per kund, vilket behövs vid grundladdning.

## Beslut 25a: Ingen databastrigger för raderingar i sökindexet

Ingen databastrigger fångar raderingar för sökindexet. Tjänsten markerar sina egna raderingar. En kund som raderas
direkt i databasen filtreras bort ur sökträffarna och försvinner ur indexet vid nästa reindexering.

**Valdes bort:** En `after delete`-trigger på `customer`.

**Skäl:** En trigger kräver särskilda databasrättigheter och syns inte i `schema.sql`. Att radera direkt i databasen är
en manuell undantagsåtgärd.

## Beslut 25b: Begränsad frågesyntax och kontaktfält per namespace

Sökfrågan är `simple_query_string` med AND, och klienten får inte använda full Lucene-syntax. Kontaktuppgifter indexeras
i ett fält per namespace (`contacts_ns_<namespace>`) och i ett globalt fält, inte som `nested`.

**Valdes bort:** SM:s fulla Lucene-syntax och `nested`-dokument.

**Skäl:** Full syntax kostade SM ett 40-tal rättelser. Med ett fält per namespace blir synligheten en del av frågan, och
AND fungerar över namn och kontaktuppgifter.

## Beslut 26: SupportManagement publicerar inte utkast

SupportManagement publicerar inte utkast (`lifecycle = DRAFT`).

**Valdes bort:** Att publicera utkast.

**Skäl:** Ett utkast är inget engagemang än.

## Beslut 27: Skyddad identitet med tre nivåer

Skyddad identitet ([§5.4]) är en egenskap på kunden med tre nivåer: `NONE`, `SECRECY_MARKING` och
`PROTECTED_REGISTRATION`. Den effektiva nivån är den högsta av folkbokföringens (Citizen), CUSTOMER-meddelandenas (en
per källa, [beslut 53]) och engagemangsmeddelandenas. Är kunden skyddad lagras varken namn, adress eller kundens egna
kontaktuppgifter, och de indexeras inte för sökning. När folkbokföringens status inte är känd används värdet `UNKNOWN`,
som räknas som skyddat ([beslut 48]).

**Valdes bort:** Att lagra uppgifterna och bara dölja dem i API:et, eller att bara lita på publicerarnas flagga.

**Skäl:** Skatteverkets och eSams vägledning: namn, adress, telefon och e-post är skyddsvärda, man ska inte hämta eller
spara mer än nödvändigt, och skyddet ska hållas uppdaterat från folkbokföringens flaggor. SPAR gör likadant och tar bort
namn och adress.

## Beslut 28: Skyddsstatus från Citizen vid skapandet och varje natt

Folkbokföringens skyddsstatus hämtas från Citizen (`ShowClassified=true`) när en privatkund eller enskild firma
skapas, och dessutom varje natt för dem och för kunder med okänd status. Natten tar med varje sådan kund vars senaste
kontroll är äldre än körningens start ([§5.4]).

**Valdes bort:** Att bara kontrollera vid skapandet, eller att bara lita på meddelandena. Att välja kunder vars senaste
kontroll är äldre än en fast ålder, t.ex. ett dygn.

**Skäl:** Skyddet kan tillkomma eller upphöra när som helst, och vägledningen kräver att uppgifterna hålls uppdaterade.
En fast ålder skulle missa kunder som skapades eller kontrollerades under dagen före körningen.

## Beslut 29: Den tekniska loggen raderas inte automatiskt vid GDPR-radering

Den tekniska loggen raderas inte automatiskt när en kund raderas på begäran enligt GDPR. Den kan raderas manuellt
([§8.2]).

**Valdes bort:** Att radera loggen direkt.

**Skäl:** partyId är ett pseudonymt id, och loggen innehåller inga andra personuppgifter.

## Beslut 30: De administrativa endpoints ligger i samma API

De administrativa endpoints (kundradering, teknisk logg och kontaktpersonsroller) ligger i samma API som resten.

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
ändå ha anropat LegalEntity med numret i URL:en, så den skulle bara flytta URL:en ett steg och kräva en ändring i en
annan tjänst. För en enskild firma är numret ägarens personnummer. Det hålls borta från party-overviews payload-logg,
och att det syns i gatewayens och LegalEntitys loggar är en accepterad risk ([§13.3]).

## Beslut 33: Kontaktpersonsrollerna sätts via REST vid driftsättning

Kontaktpersonsrollerna sätts via REST vid driftsättning: `CONTACT_PERSON` för contract och Casedata, och för
SupportManagement de roller som produktionens metadata visar ([§12]).

**Valdes bort:** Att lägga in startvärdena i en Flyway-migrering.

**Skäl:** Migreringarna ska vara oberoende av kommun.

## Beslut 34: Casedata publicerar ärenden, och sekretessärenden som DELETE

Casedata blir en publicerare av `ERRAND` med källan `CASE_DATA` ([§11.5]). Ärenden med `confidential = true` publiceras
som `DELETE`.

**Valdes bort:** Att publicera sekretessmarkerade ärenden.

**Skäl:** Beställaren räknar Casedata som ärendekälla. Ett ärende markerat som sekretess ska inte synas för hela
kommunen.

## Beslut 35: Ingen särskild loggning av läsningar av skyddade kunder

Läsningar av skyddade kunder loggas inte särskilt i version 1.

**Valdes bort:** En egen åtkomstlogg.

**Skäl:** party-overview lagrar inga namn, adresser eller kontaktuppgifter för skyddade kunder.

## Beslut 36: En notering skapas bara med partyId

En notering kan bara skapas med partyId. En tjänst som bara har personnummer hämtar partyId från Party först.

**Valdes bort:** Att skapa en notering direkt med personnummer.

**Skäl:** Beställarens val.

## Beslut 37: Casedata publicerar bara SBK_MEX tills vidare

Casedata publicerar bara namespacet `SBK_MEX` tills dataskyddsombudet har tagit ställning till parkeringstillstånd och
färdtjänst. Kontaktpersonsrollen för `CASE_DATA` är bara `CONTACT_PERSON`.

**Valdes bort:** Att publicera alla namespaces från början.

**Skäl:** Parkeringstillstånd och färdtjänst kan avslöja hälsorelaterade förhållanden.

## Beslut 38: Egen OpenSearch-container per miljö

party-overview får en egen OpenSearch-container per miljö i väntan på ett riktigt kluster, som SupportManagement har.
Indexnamnet `customer` behöver därför inget miljöprefix.

**Valdes bort:** Att dela kluster med SupportManagement.

**Skäl:** Indexet byggs alltid om från databasen, så en enkel container räcker tills klustret finns.

## Beslut 39: Vad som är sökbart

I princip allt om kunden är sökbart: namn, adress, kontaktuppgifter och kontaktpersoner, engagemangens referens, titel
och kategori, samt det egna namespacets noteringar. För en skyddad kund är bara engagemangens referenser sökbara
([§6.2]).

**Valdes bort:** Att bara söka på namn, adress och kontaktuppgifter.

**Skäl:** Beställarens krav. En referens som ärendenummer avslöjar inget om en skyddad person, men fritext i titlar och
noteringar kan göra det.

## Beslut 40: Sökindexets relay körs efter commit och var 10:e sekund

Sökindexets relay körs direkt efter varje commit som markerar kunder, och dessutom var 10:e sekund som skyddsnät. En
ändring blir sökbar inom någon sekund.

**Valdes bort:** Bara schemalagd körning.

**Skäl:** Med OpenSearch som eget system kan indexeringen inte ingå i databasens transaktion utan att meddelandeflödet
blir beroende av OpenSearch och håller lås medan det väntar. Med körning direkt efter commit blir fördröjningen ändå
kort.

## Beslut 41: Tolkningen av Citizens skyddsflaggor

`classified = "J"` i Citizen tolkas som sekretessmarkering, och `protectedNR = "J"` som skyddad folkbokföring.

**Valdes bort:** Den omvända tolkningen, som api-service-caremanagement har i sin javadoc.

**Skäl:** "NR" betyder folkbokföring (jämför fältet `nrDate`), och Citizen-dokumentationen skiljer på sekretessmarkerade
personer och personer med skyddad folkbokföring. Det verifieras mot testpersoner i C3.

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

**Skäl:** Den tekniska loggen har redan id:t för varje meddelande och kund, även för inaktuella meddelanden. I
applikationsloggarna maskeras id:t, eftersom det är ett UUID ([§12]), så det är den tekniska loggen som knyter en
ändring till sitt meddelande. Kolumnerna på datatabellerna gav ingen spårbarhet utöver den.

## Beslut 48: Okänd skyddsstatus när Citizen inte svarar

Om Citizen eller LegalEntity inte svarar, svarar 5xx, har en öppen circuit breaker eller saknar en post för personen
när ett meddelande skapar en kund, får kunden skyddsstatusen `UNKNOWN` och behandlas som skyddad tills den nattliga
kontrollen har fått svar ([§5.4]). Meddelandet tillämpas ändå. När en notering skapar kunden blir svaret i stället 503,
eftersom partytypen då inte går att avgöra ([§7.2]). 401 dead-letteras som tidigare, eftersom det är ett
konfigurationsfel. README, javadoc och OpenAPI-specen beskriver värdet.

**Valdes bort:** Att göra om meddelandet tills Citizen svarar, och att sedan dead-lettera det.

**Skäl:** Med single-active-consumer stod hela källans kö still i cirka 8 minuter per meddelande, och meddelanden som
flyttades tillbaka från dead letter-kön kom i fel ordning och tappade sina aktiviteter. Med `UNKNOWN` sparas ändå inga
uppgifter innan statusen är känd. Ett eget värde visar att statusen är okänd, i stället för en skyddsnivå som personen
kanske inte har.

## Beslut 49: contract publicerar bara avtalsparter

contract skickar bara stakeholders med minst en partsroll eller rollen `CONTACT_PERSON`, och aldrig stakeholders av
typen `MUNICIPALITY` ([§11.4]). Den som bara skriver under (`SIGNATORY`) eller har fullmakt (`POWER_OF_ATTORNEY_*`)
skickas inte.

**Valdes bort:** Att skicka alla stakeholders, eller att göra `SIGNATORY` och fullmaktsrollerna till
kontaktpersonsroller.

**Skäl:** Kommunen själv kan vara part, t.ex. som `LESSOR`, och skulle då bli kund med ett engagemang per kontrakt. Den
som bara skriver under eller har fullmakt företräder en part och är inte själv kund. Som kontaktpersonsroll skulle
`SIGNATORY` hindra en privat hyresgäst som också skriver under från att bli kund, eftersom en part med
kontaktpersonsroll aldrig blir kund ([beslut 15]).

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

En kontaktperson med partyId kontrolleras mot sin skyddsnivå och sparas inte om den inte är `NONE`. Kontaktpersonens
partyId sparas, så att raderna kan tas bort när personen raderas via REST eller ett `CUSTOMER DELETE`, eller får skydd
([§5.3]). En automatisk radering av personen som kund lämnar raderna kvar, eftersom källorna fortfarande anger
personen som kontaktperson.

**Valdes bort:** Att skyddet bara gäller kunden och inte kontaktpersonen.

**Skäl:** Vägledningen räknar arbetsgivare som en skyddsvärd uppgift, och en kontaktperson visar just kopplingen till
ett företag. Utan partyId på raden skulle en radering enligt GDPR inte nå personens kontaktrader.

## Beslut 53: Skyddet från `CUSTOMER`-meddelanden lagras per källa

Varje källas skydd från ett `CUSTOMER`-meddelande lagras i en egen rad i `asserted_protection`, och den högsta gäller
([§4.3]).

**Valdes bort:** En kolumn på kunden, som det senaste meddelandet skriver över.

**Skäl:** Med en kolumn skulle ett meddelande utan skydd från en källa ta bort skyddet som en annan källa har angett,
och då skulle namn och adress sparas för en skyddad person.

## Beslut 54: Enskilda firmor kontrolleras mot folkbokföringen

För en enskild firma, där organisationsnumret är ägarens personnummer, hämtas ägarens skydd från folkbokföringen på
samma sätt som för en privatkund, också i den nattliga kontrollen ([§5.4]). Ett legalId för `ENTERPRISE` får därför
vara ett personnummer med 10 eller 12 siffror, och det slås alltid upp mot LegalEntity ([§3.1]). Ett tiosiffrigt
nummer får det århundrade som gör ägaren yngre än 100 år. Numret sparas aldrig, utan mappas till partyId:t.

**Valdes bort:** Att bara kontrollera privatkunder. Att slå upp numret mot Citizen när det är ett personnummer.

**Skäl:** En enskild firmas namn och adress är ofta ägarens, och en skyddad ägare ska inte bli synlig via sin firma.
Slogs numret upp mot Citizen skulle firmans engagemang hamna på ägarens privatkund. Hundraårsregeln är Skatteverkets
regel för skiljetecknet i ett tiosiffrigt personnummer.

## Beslut 55: Kommunikation i SM publiceras inte

SupportManagement publicerar bara när ärendet självt ändras. E-post, sms och webbmeddelanden till och från ärendet
publiceras inte ([§11.2]).

**Valdes bort:** Att publicera kommunikationen som aktiviteter i historiken.

**Skäl:** Kundbilden visar ärendet, inte meddelandena som skickas till och från det. Kommunikationen ökar dessutom inte
ärendets version, så den kunde ändå inte publiceras som en egen ändring.

## Beslut 56: party-overview anropar inte api-service-party

partyId slås upp direkt mot Citizen och LegalEntity, och partytypen avgörs med de anrop som ändå görs mot dem
([§7.1]). party-overview anropar inte api-service-party.

**Valdes bort:** Att slå upp partyId och partytyp via api-service-party.

**Skäl:** api-service-party är ett tunt lager ovanpå Citizen och LegalEntity, och partyId är deras id: Citizens
`personId` för en person och LegalEntitys id för ett företag. Tjänsten anropar redan båda, så Party gav bara ett
beroende till. Direkt syns fel per element i Citizens batchsvar, som Party annars gör om till att numret inte finns,
och partytypen avgörs utan att personnummer hämtas.

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

Uppgifterna om ett företag från LegalEntity, som SNI-koder, storleksklass för anställda och omsättning, juridisk form
och pågående konkurs, lagras i `enterprise_profile`, en rad per företagskund, med typade kolumner. SNI-koderna och de
pågående förfarandena ligger i egna tabeller under profilen ([§5.5]). SNI-koderna hör till företaget och räknas fram ur
arbetsställenas koder, med huvudarbetsställets först.

**Valdes bort:**

- Kolumner direkt på `customer`.
- En JSON-kolumn eller en tabell med nyckel och värde.
- Arbetsställena som egna rader, med adresser och SNI-koder per arbetsställe.
- En egen tabell med SCB:s SNI-klassifikation.
- Funktionärer och firmateckning (`officers`, `fullSignatureDescription`).
- Organisationsnumret.

**Skäl:** Kundraden låses i varje meddelande och ska vara smal, och kolumnerna gäller inte privatkunder. Profilen
hämtas i en egen takt och raderas som en helhet när kunden blir skyddad. Med typade kolumner ser schemakontrollen
fälten, och SNI-koderna går att fråga på. SCB:s företagsnivå är aggregerad från arbetsställena, men LegalEntity ger
bara arbetsställenas koder, utan andelar. Arbetsställena behövs inte för kundbilden, och LegalEntity ger kodernas namn,
så ingen klassifikation behöver underhållas, inte heller när SNI 2025 ersatte SNI 2007. Funktionärerna anges med namn
och personnummer. Kunden identifieras med partyId:t, och ett organisationsnummer mappas alltid till det ([beslut 54]).

## Beslut 59: Företagsprofilen uppdateras från en ändringskö genom att LegalEntity hämtas

party-overview lyssnar på en kö med ändringar av företag och personer. Ett meddelande om ett företag som är kund får
party-overview att hämta företaget från LegalEntity och skriva profilen ([§5.5]). Den senaste hämtningen vinner.
Meddelanden om personer ignoreras tills vidare.

**Valdes bort:**

- Att läsa profilen ur meddelandet.
- Att hämta alla profiler med ett schemalagt jobb.

**Skäl:** När LegalEntitys svar avgör spelar meddelandenas ordning och dubbletter ingen roll, och party-overview
behöver inte känna till mer av köns format än partyId:t. Ett schemalagt jobb skulle anropa LegalEntity för varje
företag, också de som inte har ändrats, och ändå visa en ändring först efter nästa körning.

[§4.2]: 04-datamodell.md#42-gemensamt-för-alla-tabeller
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§6.2]: 06-rest-api.md#62-kundsökning-opensearch
[§8.2]: 08-teknisk-logg.md#82-radering-av-den-tekniska-loggen
[§11.5]: 11-publicerare.md#115-casedata-errand-källan-case_data
[§12.1]: 12-drift.md#121-larm-för-dead-letter-köer
[§11.4]: 11-publicerare.md#114-contract-contract
[beslut 15]: #beslut-15-party-overview-härleder-kontaktpersoner
[beslut 19]: #beslut-19-supportmanagement-publicerar-bara-namespaces-med-opt-in
[beslut 34]: #beslut-34-casedata-publicerar-ärenden-och-sekretessärenden-som-delete
[beslut 37]: #beslut-37-casedata-publicerar-bara-sbk_mex-tills-vidare
[beslut 48]: #beslut-48-okänd-skyddsstatus-när-citizen-inte-svarar
[§3.6]: 03-meddelanden.md#36-kundradering
[§9]: 09-kodstruktur.md
[beslut 6]: #beslut-6-en-notering-på-en-okänd-kund-skapar-kunden
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

