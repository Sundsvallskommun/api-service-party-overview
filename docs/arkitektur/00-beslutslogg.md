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

Kunddata (namn, adress, kontaktuppgifter och kontaktpersoner) kommer från meddelanden och går bara att läsa via REST.
Undantaget är namn och adress från Citizen och LegalEntity när en notering skapar kunden ([beslut 6]), och när den
nattliga kontrollen fyller i dem för en privatkund ([§5.4]).

**Valdes bort:** Att party-overview är master för kunddata.

**Skäl:** Källsystemen har redan datan, och det ska bara finnas en skrivväg.

## Beslut 5: Noteringar ägs av party-overview och hör till ett namespace

Noteringar ägs av party-overview och skapas, ändras och raderas via REST. En notering hör alltid till ett namespace, och
det finns inga globala noteringar.

**Valdes bort:** Noteringar från källsystem, t.ex. api-service-notes. Deras noteringar är ärendedata, inte
envelope-data, och samlas inte in.

**Skäl:** Noteringar är kundvyns egna anteckningar, och kravet är att en verksamhet bara ser sina egna.

## Beslut 6: En notering på en okänd kund skapar kunden

En notering på en kund som inte finns skapar kunden. Party avgör partytypen, och Citizen eller LegalEntity ger namn och
adress.

**Valdes bort:** Att svara 404 för okända kunder.

**Skäl:** Handläggaren ska kunna anteckna om en kund som inget källsystem har nämnt än.

## Beslut 7: Synlighet för engagemang, kontaktuppgifter och noteringar

Engagemang syns för alla verksamheter i kommunen, med namespace som valfritt filter. Kontaktuppgifter syns för det egna
namespacet plus de globala. Noteringar syns bara för det egna namespacet.

**Valdes bort:** Att begränsa engagemang till eget namespace, eller att göra synligheten konfigurerbar.

**Skäl:** Kundvyn ska ge helheten. Engagemang kan synas för hela kommunen, eftersom det som är känsligt aldrig
publiceras. SupportManagement publicerar bara namespaces som har slagit på det ([beslut 19]). Casedata publicerar inte
sekretessmarkerade ärenden ([beslut 34]) och tills vidare bara `SBK_MEX` ([beslut 37]).

## Beslut 8: En engagemangsrad per kund

Ett engagemang lagras som en rad per kund. Ett ärende med tre kunder blir alltså tre rader.

**Valdes bort:** En delad rad med kopplingstabell.

**Skäl:** Det är det enda alternativet där `DELETE FROM customer` direkt i databasen tar med allt och inte lämnar
föräldralösa rader med personuppgifter. Det som dupliceras är bara envelope-data.

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

En kund raderas av ett CUSTOMER DELETE-meddelande, av REST DELETE eller automatiskt när kunden saknar både engagemang
och noteringar.

**Valdes bort:** —

**Skäl:** Beställarens krav.

## Beslut 15: party-overview härleder kontaktpersoner

party-overview härleder kontaktpersoner. En part med kontaktpersonsroll blir kontaktperson på objektets företagskunder
men blir aldrig själv kund. Vilka roller som räknas som kontaktpersonsroller konfigureras per kommun och källa via REST.

**Valdes bort:** Att publiceraren kopplar ihop dem, eller en lista i `application.yml`.

**Skäl:** Beställarens val. I SM är rollerna metadata per namespace, så nya roller ska inte kräva en deploy.

## Beslut 16: Personnummer och organisationsnummer mappas till partyId

Publicerarna skickar partyId när de har det. Det som ändå kommer som personnummer eller organisationsnummer mappar
party-overview till partyId via api-service-party. Sådana nummer lagras och loggas aldrig. En part som Party inte hittar
hoppas över.

**Valdes bort:** Att bara party-overview mappar, eller att bara publicerarna gör det.

**Skäl:** Personnummer ska färdas över köerna så sällan som möjligt, men kravtexten gäller fortfarande. Ett 404 från
Party blir inte bättre av omförsök.

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

**Skäl:** SM har åtkomstkontroll per namespace, och party-overview får inte kringgå den.

## Beslut 20: Transaktionell outbox hos varje publicerare

Varje publicerare har en transaktionell outbox och en relay som körs direkt efter commit och dessutom schemalagt med
ShedLock. Grundladdningen går genom samma outbox.

**Valdes bort:** En gemensam outbox i dept44, eller att publicera efter commit utan outbox.

**Skäl:** Inga meddelanden får tappas, och mönstret finns redan i SM.

## Beslut 21: Namn och adress: nyast vinner

Namn och adress följer regeln "nyast vinner", mätt på källans `sourceModified`.

**Valdes bort:** Att den som skrev senast vinner.

**Skäl:** Ordningen blir deterministisk oavsett i vilken ordning meddelandena kommer, även vid grundladdning.

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
kroppen. Numret mappas till partyId via Party och kastas sedan.

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
`PROTECTED_REGISTRATION`. Den effektiva nivån är den högsta av folkbokföringens (Citizen), CUSTOMER-meddelandenas och
engagemangsmeddelandenas. Är kunden skyddad lagras varken namn, adress eller kundens egna kontaktuppgifter, och de
indexeras inte för sökning. När folkbokföringens status inte är känd används värdet `UNKNOWN`, som räknas som skyddat
([beslut 48]).

**Valdes bort:** Att lagra uppgifterna och bara dölja dem i API:et, eller att bara lita på publicerarnas flagga.

**Skäl:** Skatteverkets och eSams vägledning: namn, adress, telefon och e-post är skyddsvärda, man ska inte hämta eller
spara mer än nödvändigt, och skyddet ska hållas uppdaterat från folkbokföringens flaggor. SPAR gör likadant och tar bort
namn och adress.

## Beslut 28: Skyddsstatus från Citizen vid skapandet och varje natt

Folkbokföringens skyddsstatus hämtas från Citizen (`ShowClassified=true`) när en privatkund skapas, och dessutom för
alla privatkunder varje natt.

**Valdes bort:** Att bara kontrollera vid skapandet, eller att bara lita på meddelandena.

**Skäl:** Skyddet kan tillkomma eller upphöra när som helst, och vägledningen kräver att uppgifterna hålls uppdaterade.

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

## Beslut 32: Organisationsnummer skickas i kroppen mot Party

Organisationsnummer mappas till partyId med en ny endpoint i Party, `POST /{municipalityId}/ENTERPRISE/partyIds`, med
numren i kroppen. Den byggs i P1 ([§14]).

**Valdes bort:** Den befintliga `GET /{municipalityId}/ENTERPRISE/{legalId}/partyId`, med numret i URL:en.

**Skäl:** För en enskild firma är organisationsnumret ägarens personnummer, och Party tar emot det som legalId för
`ENTERPRISE`. En URL hamnar i gatewayens och Party:s loggar, och personnummer ska aldrig stå i en URL ([beslut 24]).

## Beslut 33: Kontaktpersonsrollerna sätts via REST vid driftsättning

Kontaktpersonsrollerna sätts till `CONTACT_PERSON` för SupportManagement, contract och Casedata via REST vid
driftsättning.

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

**Skäl:** Den tekniska loggen har redan id:t för varje meddelande och kund, även för inaktuella meddelanden, och
dept44-starter-rabbitmq loggar samma id för varje meddelande hos både publiceraren och party-overview. Kolumnerna på
datatabellerna gav ingen spårbarhet utöver det.

## Beslut 48: Okänd skyddsstatus när Citizen inte svarar

Om Citizen inte svarar, svarar 5xx, har en öppen circuit breaker eller saknar en post för personen när en privatkund
skapas, får kunden skyddsstatusen `UNKNOWN` och behandlas som skyddad tills den nattliga kontrollen har fått svar
([§5.4]). Meddelandet eller noteringen tillämpas ändå. 401 dead-letteras som tidigare, eftersom det är ett konfigurationsfel. README, javadoc
och OpenAPI-specen beskriver värdet.

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

[§4.2]: 04-datamodell.md#42-gemensamt-för-alla-tabeller
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§6.2]: 06-rest-api.md#62-kundsökning-opensearch
[§8.2]: 08-teknisk-logg.md#82-radering-av-den-tekniska-loggen
[§11.5]: 11-publicerare.md#115-casedata-errand-källan-case_data
[§12.1]: 12-drift.md#121-larm-för-dead-letter-köer
[§11.4]: 11-publicerare.md#114-contract-contract
[§14]: 14-uppgifter.md#p1--party-partyid-för-organisationsnummer-i-batch
[beslut 15]: #beslut-15-party-overview-härleder-kontaktpersoner
[beslut 19]: #beslut-19-supportmanagement-publicerar-bara-namespaces-med-opt-in
[beslut 24]: #beslut-24-uppslag-på-personnummer-eller-organisationsnummer
[beslut 34]: #beslut-34-casedata-publicerar-ärenden-och-sekretessärenden-som-delete
[beslut 37]: #beslut-37-casedata-publicerar-bara-sbk_mex-tills-vidare
[beslut 48]: #beslut-48-okänd-skyddsstatus-när-citizen-inte-svarar
[§3.6]: 03-meddelanden.md#36-kundradering
[§9]: 09-kodstruktur.md
[beslut 6]: #beslut-6-en-notering-på-en-okänd-kund-skapar-kunden

