# 13. Risker och öppna frågor

Del av [arkitekturen för party-overview](README.md).

## 13.1 Öppna frågor

| # |                                                                                                                                                 Fråga                                                                                                                                                 |                                                                                                            Förslag tills den är besvarad                                                                                                             |
|---|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Det gemensamma meddelandeformatet (AsyncAPI) definieras senare.                                                                                                                                                                                                                                       | [§3.1] är kravlistan. Uppgifterna som beror på F1 (C5, SM1, CD1, PA1, CT2 och CS1), och de som bygger på C5, kan inte avslutas innan formatet är klart.                                                                                              |
| 2 | Får Casedatas namespaces `SBK_PARKING_PERMIT` och `ANGE_PARKING_PERMIT` publiceras, och party-assets parkeringstillstånd för rörelsehindrade (`PARKINGPERMIT`, `PARKING_PERMIT`)? Parkeringstillstånd och färdtjänst kan avslöja hälsorelaterade förhållanden, och engagemang syns för hela kommunen. | Nej, inte förrän dataskyddsombudet har svarat ([beslut 37], [beslut 50]).                                                                                                                                                                            |
| 3 | Ändringskön för företag och personer finns inte ännu, och dess meddelandemodell är inte fastställd ([§5.5]). Vem förvaltar den, och vad heter exchangen?                                                                                                                                              | Kraven i [§5.5]: partyId, partens typ, vad som har hänt och ett id per meddelande, enligt CloudEvents ([beslut 62]). C14, C15 och T3 kan inte avslutas innan kön finns. Tills dess skrivs profilen när kunden skapas och av den nattliga kontrollen. |
| 4 | Hur länge ska den tekniska loggen sparas ([beslut 60])?                                                                                                                                                                                                                                               | Fem år, tills arkivfunktionen och dataskyddsombudet har svarat.                                                                                                                                                                                      |
| 5 | Kan LegalEntity ge företagets egna SNI-koder, rangordnade som i SCB:s företagsregister?                                                                                                                                                                                                               | Fråga LegalEntitys förvaltare. Tills dess räknas koderna fram ur arbetsställenas ([§5.5]).                                                                                                                                                           |
| 6 | Kan LegalEntity ge varje SNI-kods sökväg, med kod och namn för varje nivå från avdelningen till koden (F3)? Ger LegalEntity koderna i SNI 2025, som gäller sedan december 2025?                                                                                                                       | Fråga LegalEntitys förvaltare, tillsammans med fråga 5. Tills dess har alla koder en tom sökväg ([§5.5]), och C18 kan inte avslutas. Trädet följer den version som LegalEntity ger ([beslut 72]).                                                    |

## 13.2 Förutsättningar före driftsättning

|                                                                    Förutsättning                                                                     |                                                                            Behövs för                                                                             |
|------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| party-overviews applikation i WSO2 är registrerad som behörig för `ShowClassified=true` i Metakatalogens tabell ([§5.4]).                            | Meddelandeflödet (C11), noteringar och kontaktpersoner som skapar kunder (C8, C16) och den nattliga skyddskontrollen (C10). Utan behörigheten svarar Citizen 401. |
| En egen OpenSearch-container per miljö (T2).                                                                                                         | Sökningen (C12, C13)                                                                                                                                              |
| Köer och exchanges i driftkonfigurationen (T1).                                                                                                      | Meddelandeflödet (C11)                                                                                                                                            |
| Ändringskön i driftkonfigurationen (T3).                                                                                                             | Ändringskön (C14, C15)                                                                                                                                            |
| Partsrollerna är satta via REST innan lyssnarna slås på ([§12]). SM:s roller klassas utifrån produktionens metadata, tillsammans med verksamheterna. | Meddelandeflödet (C11). Utan rollerna blir ingen part kund.                                                                                                       |

## 13.3 Risker

- Behörigheten för `ShowClassified=true` saknas eller försvinner. Då svarar Citizen 401, och alla meddelanden som skapar
  nya privatkunder dead-letteras.

  **Hantering:** Behörigheten kontrolleras före driftsättningen ([§13.2]). Larmet för dead letters gör att felet syns
  direkt. Meddelandena flyttas tillbaka när behörigheten finns ([§7.2]).

- OpenSearch-containern har inte samma drift och backup som ett riktigt kluster.

  **Hantering:** Indexet byggs alltid om från databasen med `POST /search/reindex`, så att förlora det kostar bara en
  reindexering.

- Stora företagskunder ger stora sökdokument, eftersom alla engagemangsreferenser indexeras. Det tar tid att bygga om
  dem.

  **Hantering:** Titlar och noteringar har tak ([§6.2]). Kön slår ihop ändringar per kund, så dokumentet byggs om högst
  en gång per relaykörning.

- Dead letter-köerna innehåller personuppgifter (legalId och namn) och har ingen TTL.

  **Hantering:** Publicerarna skickar partyId när de kan. Bara drift och utvecklare har åtkomst. Larmet ([§12.1]) gör
  att köerna töms snabbt.

- Grundladdningen av SupportManagement (cirka 800 000 ärenden) går via en enda konsument.

  **Hantering:** Prefetch 1 och 10–20 ms per meddelande ger 50–100 meddelanden/s, så grundladdningen tar 2–5 timmar. Det
  blir långsammare när nya privatkunder kräver ett Citizen-anrop. Grundladdningen körs utanför kontorstid, och tiden
  mäts i test först. Casedata (cirka 5 000) tar några minuter.

- Den nattliga skyddskontrollen belastar Citizen: antalet privatkunder och enskilda firmor plus antalet personer i
  `registry_person`, delat med 100, anrop per natt. En person som både är kund och kontaktperson kontrolleras två
  gånger.

  **Hantering:** `batch-size` är konfigurerbar. Belastningen stäms av med den som förvaltar Citizen.

- Om Citizen inte svarar får nya privatkunder och kontaktpersoner skyddsstatusen `UNKNOWN` tills den nattliga kontrollen
  har fått svar ([§5.4]).

  **Hantering:** Avsiktligt ([beslut 48]). Uppgifterna sparas, frontend hanterar `UNKNOWN` som skyddat tills statusen
  är känd, och köerna står inte still medan Citizen är nere.

- Uppgifter om skyddade kunder och kontaktpersoner sparas, går att söka på och returneras av API:t, också
  envelope-data och noteringar ([beslut 27]). Att de inte lämnas ut utan prövning beror på att varje konsument visar och
  respekterar skyddsnivån. Läsningar loggas inte särskilt ([beslut 35]).

  **Hantering:** Skyddsnivån och Citizens flaggor finns på kunden, på varje kontaktperson och i varje sökträff, och
  sökträffen visar med `hasProtectedContacts` om en kontaktperson är skyddad ([§6.2]). OpenAPI-specen beskriver vad
  fälten betyder ([§5.4]). Åtkomsten till API:t avgör vem som kan läsa uppgifterna ([beslut 30]).

- Den tekniska loggen växer under lagringstiden, som är fem år som förval.

  **Hantering:** Indexen klarar stora volymer, och gallringen raderar äldre rader varje natt ([§8.2]).

- Om skyddet tillkommer i folkbokföringen för en befintlig kund eller kontaktperson, ser party-overview det först när
  ändringskön säger att personen har ändrats, eller vid nästa nattliga kontroll. Fram till dess visas den gamla nivån.

  **Hantering:** Med ändringskön är fönstret några minuter, och utan den högst ett dygn, eftersom kontrollen tar med
  alla privatkunder, enskilda firmor och kontaktpersoner varje natt ([§5.4], [§5.5]). Ingen publicerare skickar i dag
  `protection`, så skyddet kommer bara från folkbokföringen ([§11.1]).

- När opt-in stängs av för ett namespace i SM eller Casedata, eller en asset-typ tas bort från party-assets vitlista,
  ligger engagemangen kvar i party-overview.

  **Hantering:** Publiceraren kör grundladdningen för namespacet eller typen, och relayn publicerar `DELETE` för
  objekten, eftersom de inte ska synas ([§11.1], SM2, CD2, PA2). Slås opt-in på igen, eller läggs typen tillbaka,
  kommer ett objekt tillbaka först när dess version har ökat, eftersom tombstonen har samma version ([§3.4]). En typ
  eller ett namespace som aldrig har publicerats har inga tombstones och kommer in direkt.

- När Citizen eller LegalEntity inte svarar får nya kunder och kontaktpersoner `UNKNOWN` ([§5.3], [§5.4]). Den
  nattliga kontrollen ersätter nivån för kunderna och för personerna i `registry_person`. En kontaktperson med
  `ENTERPRISE` där det inte gick att avgöra om det är en enskild firma saknar `registry_party_id` och får
  `UNVERIFIED`, som står kvar tills objektet ändras i källan ([beslut 48]).

  **Hantering:** Varje sådant fel loggas på WARN med källan och antalet berörda kunder och kontaktpersoner ([§7.2]).
  En ny grundladdning av källan ger de kvarvarande kontaktraderna rätt nivå. README beskriver det.

- En kö eller AMQP-användare som saknas stoppar inte tjänsten, men källans meddelanden tillämpas inte förrän den finns
  ([beslut 68]).

  **Hantering:** `DeadLetterHealthIndicator` är `DOWN` med köns namn, och Spring Boot Admin larmar ([§12.1]). Kön
  driftsätts före källans rad i `integration.sources` ([§12]).

- En reindexering som avbryts, t.ex. av en omstart, lämnar ett halvbyggt index.

  **Hantering:** Låset släpps inom fem minuter ([§6.2]). README beskriver att reindexeringen då körs igen.

- Varje namespace ger upp till sex fält i sökindexet (`contacts_ns_`, `engagements_ns_`, `notes_ns_`, `labels_ns_`,
  `phones_ns_` och `references_ns_`). OpenSearch tillåter förvalt 1 000 fält per index, och namespacet för
  noteringar, kontaktpersoner och etiketter väljs av anroparen.

  **Hantering:** Antalet namespaces är några tiotal. `index.mapping.total_fields.limit` höjs i indexinställningarna om
  det behövs, och ett mappningsfel syns som `attempts ≥ 5` i `OpenSearchHealthIndicator`.

- En kund som bara har noteringar syns för alla verksamheter, och den tekniska loggen visar vem som skapade den. Den
  som kan anropa API:t kan skapa en notering eller en kontaktperson på ett partyId och sedan läsa kundens namn,
  folkbokföringsadress, skyddsnivå och Citizens värden, också för en skyddad person ([§7.2]). PartyId:t måste komma
  från annat håll, eftersom uppslaget ger 404 för den som inte är kund ([§6.1]).

  **Hantering:** Accepteras ([beslut 6]). Kunden behöver namn och adress för att synas i kundvyn och i sökningen, och
  skyddade uppgifter får sparas ([beslut 27]). Åtkomsten till API:t avgör vem som kan göra det ([beslut 30]).

- Utan filter returnerar API:t alla verksamheters kontaktuppgifter, kontaktpersoner och noteringar för en kund, och
  sökningen söker i dem alla. Noteringar och kontaktpersoner kan läsas, ändras och raderas av alla som kan anropa
  API:t, oavsett namespace ([§5.1]).

  **Hantering:** Accepteras ([beslut 7]). Namespace är ett attribut och ett filter, inte en behörighet. Frontend
  filtrerar på verksamhetens namespace där det behövs. Den tekniska loggen och noteringarnas historik visar vem som
  gjorde vad ([§8.1]), och åtkomsten till API:t avgör vem som kan göra det ([beslut 30]).

- Datakvaliteten i Casedata: `personId` som inte är UUID, och ärenden utan `updated`.

  **Hantering:** Räknas i produktion innan CD2 körs. Sådana parter skickas utan partyId ([§11.5]).

- OpenSearch innehåller personuppgifter: namn, adress, kontaktuppgifter och noteringar.

  **Hantering:** Åtkomsten till klustret och dess snapshots begränsas som för databasen. En raderad kund tas bort ur
  indexet via kön. Loggar innehåller bara antal och `customer.id`.

- Sökningen är tom medan reindexering pågår.

  **Hantering:** Det tar några minuter och sker sällan, på begäran.

- Under grundladdningen hinner relayn inte med, trots att den körs efter varje commit. Kön växer men slås ihop per kund.

  **Hantering:** `search.relay.batch-size` är konfigurerbar. Efter grundladdningen kan reindexering köras, och den
  tömmer kön på det som lades till före starten ([§6.2]).

- Det är inte verifierat att Hibernate Search kastar ett fel när en explicit indexeringsplan misslyckas med `writeSync`.
  Gör den inte det tas köraden bort trots att indexeringen misslyckades.

  **Hantering:** En spike i C12 verifierar det innan relayn byggs vidare.

- Det tar lång tid att radera en stor företagskund, eftersom alla rader försvinner i en transaktion.

  **Hantering:** Accepteras. Kaskaden görs av databasen och låser bara kundens rader.

- Namn och adresser kan skilja sig mellan källor. Kunden har adresserna från det senaste meddelandet, så en källa som
  bara har en postadress tar bort en fakturaadress som en annan källa skickade tidigare.

  **Hantering:** "Nyast vinner" på `sourceModified` ger ett deterministiskt resultat ([beslut 21]). Källorna bör vårda
  sina uppgifter.

- Ett meddelande som är äldre än lagrad version tappar sina aktiviteter.

  **Hantering:** Single-active-consumer och outbox per objekt ger ordning. Det kan bara hända om ett meddelande läggs
  tillbaka efter ett fel.

- Index i Flyway och entiteter glider isär.

  **Hantering:** `SchemaParityIT`.

- En kund som raderats väcks till liv av ett nytt engagemang.

  **Hantering:** Avsiktligt, eftersom källan är master. Vid radering enligt GDPR måste även källorna radera.

- Det tar tid innan en ändring av partsrollerna slår igenom.

  **Hantering:** Dokumenterat ([§5.3]). En grundladdning från källan räknar om allt.

- En roll som saknas i partsrollerna gör att parten ignoreras. Är det en kundroll får en ny kund inte engagemanget, och
  ett nytt objekt där ingen part har en kundroll syns inte alls. Det gäller särskilt SupportManagement, där
  verksamheterna kan lägga till nya roller ([beslut 15]).

  **Hantering:** Varje roll som saknas loggas på WARN med källan och rollkoden. Ett befintligt engagemang står kvar
  oförändrat med sin historik ([§5.3]). När rollen har lagts till räknas objektet om vid nästa ändring i källan eller
  vid en grundladdning.

- En kontaktperson i en privatpersons ärende, t.ex. en anhörig eller ett ombud, sparas och är sökbar. En sökning på
  kontaktpersonens namn ger den kund vars ärende personen är kontaktperson i ([beslut 15]).

  **Hantering:** Accepteras. Kontaktpersonen har sin egen skyddsnivå, och träffen visar `hasProtectedContacts` ([§6.2]).
  Kontaktpersonen visas bara på ärendets engagemang, inte på kunden.

- party-overview sparar kontaktinställningar från ContactSettings också för personer som inte är kunder ([§5.6]).

  **Hantering:** Accepteras ([beslut 71]). Den som har en inställning med partyId har en koppling till kommunen. Raden
  följer ContactSettings och tas bort när inställningen tas bort där.

- Ett företags funktionärer sparas med namn och funktion, och de är sökbara ([§5.5]). En sökning på en styrelseledamots
  namn ger företaget.

  **Hantering:** Accepteras ([beslut 70]). Uppgifterna är offentliga hos Bolagsverket. Personnumret sparas aldrig, och
  en skyddad funktionär visas med sin skyddsnivå.

- party-overview slår upp organisationsnummer mot LegalEntity med numret i URL:en
  (`GET /{municipalityId}/{organizationNumber}/guid`). För en enskild firma är numret ett personnummer, som då kan
  hamna i gatewayens och LegalEntitys loggar.

  **Hantering:** Accepteras ([beslut 32]). Anropet undantas från party-overviews payload-logg, och PII-maskningen är
  påslagen ([§7.1]).

- En kontaktperson utan partyId och legalId kan inte kontrolleras mot folkbokföringen. Den sparas med `UNVERIFIED`,
  som inte räknas i sökträffens `hasProtectedContacts` ([§5.3], [beslut 48]). En sådan kontaktperson kan ändå ha
  skyddade personuppgifter.

  **Hantering:** Accepteras. Publicerarna skickar partyId när de har det, och en källa som vet om skyddet anger det i
  `protection`. Frontend visar `UNVERIFIED` som okontrollerat.

- För en enskild firma med ett tiosiffrigt organisationsnummer framgår inte århundradet, som Citizens uppslag av
  personnummer behöver. party-overview antar att ägaren är yngre än 100 år ([§5.4]).

  **Hantering:** Accepteras. En ägare som är 100 år eller äldre får fel århundrade. Då hittas ingen ägare, och kunden
  får `UNKNOWN`. I värsta fall hittas en annan person med samma nummer i ett annat århundrade.

- En företagsprofil är inaktuell om ett meddelande i ändringskön har dead-letterats, och tills kön finns ([§5.5]).

  **Hantering:** Dead letter-kön larmar ([§12.1]), och meddelandet kan flyttas tillbaka när LegalEntity svarar igen.
  `fetched` visar hur gammal profilen är.

- Ändringskön innehåller alla företag och personer, och det mesta berör inte party-overview.

  **Hantering:** Ett meddelande som inte berör någon kund eller kontaktrad kastas efter en indexuppslagning, utan anrop
  till LegalEntity eller Citizen ([§5.5]).

- En kontaktperson som källan fortfarande anger försvinner från andra kunders engagemang när personen raderas
  automatiskt som kund, och kommer tillbaka först när objektet ändras i källan ([§3.6]).

  **Hantering:** Accepteras ([beslut 52]).

- Företagets SNI-koder räknas fram ur arbetsställenas, eftersom LegalEntity inte ger företagets egna. För ett företag
  med flera arbetsställen kan ordningen skilja sig från SCB:s, som bygger på andelar av omsättningen ([beslut 58]).

  **Hantering:** Accepteras. För ett företag med ett arbetsställe är koderna desamma. Om LegalEntity börjar ge
  företagets koder används de i stället.

- Ett företag kan ha hundratals arbetsställen. Profilen ersätts då som helhet med alla arbetsställen, deras adresser och
  SNI-koder, under kundens lås, och ett meddelande om samma kund väntar under tiden ([§5.5]).

  **Hantering:** Accepteras, utan tak ([beslut 58]). Profilen skrivs bara om när den har ändrats, och raderna skrivs i
  JDBC-batchar. C5 testar en profil med 2 000 arbetsställen.

- SNI-kodernas sökväg kräver en ändring i LegalEntity (F3). Innan den finns har alla koder en tom sökväg. Efter
  ändringen får en kod sin sökväg först när ett företag med koden hämtas, och ett företag vars koder ingen annan har
  väntar tills dess profil hämtas igen ([§5.5]).

  **Hantering:** Accepteras. Alla kunder delar trädet, så de vanliga koderna får sin sökväg snabbt. Kommer F3 före
  driftsättningen finns ingen lucka.

- När SNI byter version kan en kod få en ny betydelse. Nästa hämtning med koden skriver över noden, och ett företag vars
  profil fortfarande har den gamla versionens kod visas då med den nya sökvägen tills profilen hämtas igen
  ([beslut 72]).

  **Hantering:** Accepteras. SNI byter version sällan, och kodens eget namn på arbetsstället följer alltid profilen.

- LegalEntitys svar innehåller personnummer och namn på företagets funktionärer.

  **Hantering:** Funktionärerna blir kontaktpersoner med partyId, och numren slås upp och kastas utan att sparas eller
  loggas ([beslut 70], [§7.2]). `fullSignatureDescription` mappas aldrig. C3 verifierar det.

- Etiketternas namn är fritext som verksamheterna själva skriver, och en etikett på en kund är en uppgift om kunden. En
  etikett som "Hotfull" kan vara en känslig bedömning av en person. Alla som kan anropa API:t ser kundens etiketter,
  kan skapa nya och kan radera en etikett för alla kunder i kommunen, i alla namespaces ([§5.7]).

  **Hantering:** Accepteras, som för noteringar. Den tekniska loggen visar vem som lade på och tog bort varje etikett,
  också när en etikett raderades för alla kunder ([§8.1]). Vilka etiketter som får skapas bestäms av verksamheterna.
  Etikettnamnen skrivs aldrig till applikationsloggen.

[§3.1]: 03-meddelanden.md#31-krav-på-det-gemensamma-formatet
[§3.4]: 03-meddelanden.md#34-versionsmatrisen
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§6.2]: 06-rest-api.md#62-kundsökning-opensearch
[§7.2]: 07-integrationer.md#72-citizen-och-legalentity
[§8.2]: 08-teknisk-logg.md#82-radering-av-den-tekniska-loggen
[§11.1]: 11-publicerare.md#111-gemensamt-för-alla-publicerare
[§11.5]: 11-publicerare.md#115-casedata-errand-källan-case_data
[§12]: 12-drift.md
[§12.1]: 12-drift.md#121-larm-för-dead-letter-köer
[beslut 15]: 00-beslutslogg.md#beslut-15-partsrollerna-avgör-vem-som-är-kund-och-kontaktperson
[beslut 30]: 00-beslutslogg.md#beslut-30-de-administrativa-endpoints-ligger-i-samma-api
[beslut 32]: 00-beslutslogg.md#beslut-32-organisationsnummer-slås-upp-direkt-mot-legalentity
[beslut 48]: 00-beslutslogg.md#beslut-48-okänd-skyddsstatus-när-citizen-inte-svarar
[beslut 27]: 00-beslutslogg.md#beslut-27-skyddad-identitet-med-tre-nivåer
[beslut 35]: 00-beslutslogg.md#beslut-35-ingen-särskild-loggning-av-läsningar-av-skyddade-kunder
[§13.2]: #132-förutsättningar-före-driftsättning
[beslut 37]: 00-beslutslogg.md#beslut-37-casedata-publicerar-bara-sbk_mex-tills-vidare
[beslut 50]: 00-beslutslogg.md#beslut-50-party-assets-publicerar-bara-asset-typer-på-en-vitlista
[§7.1]: 07-integrationer.md#71-uppslag-av-partyid
[beslut 21]: 00-beslutslogg.md#beslut-21-namn-och-adress-nyast-vinner
[§5.5]: 05-synlighet-och-kunddata.md#55-företagsuppgifter
[beslut 58]: 00-beslutslogg.md#beslut-58-företagsuppgifter-lagras-i-en-egen-profil
[beslut 60]: 00-beslutslogg.md#beslut-60-den-tekniska-loggen-gallras-efter-en-lagringstid
[beslut 62]: 00-beslutslogg.md#beslut-62-meddelandeformaten-följer-cloudevents
[§3.6]: 03-meddelanden.md#36-kundradering
[beslut 52]: 00-beslutslogg.md#beslut-52-kontaktpersoner-kontrolleras-mot-skyddad-identitet
[§6.1]: 06-rest-api.md#61-endpoints
[§8.1]: 08-teknisk-logg.md#81-utfall-i-den-tekniska-loggen
[beslut 6]: 00-beslutslogg.md#beslut-6-en-notering-på-en-okänd-kund-skapar-kunden
[§5.1]: 05-synlighet-och-kunddata.md#51-synlighet-per-namespace
[beslut 7]: 00-beslutslogg.md#beslut-7-synlighet-för-engagemang-kontaktuppgifter-och-noteringar
[beslut 68]: 00-beslutslogg.md#beslut-68-lyssnarna-använder-direct-containern
[§5.6]: 05-synlighet-och-kunddata.md#56-kontaktinställningar
[beslut 71]: 00-beslutslogg.md#beslut-71-kontaktinställningarna-sparas-för-alla-med-partyid
[beslut 70]: 00-beslutslogg.md#beslut-70-ett-företags-funktionärer-blir-kontaktpersoner
[beslut 72]: 00-beslutslogg.md#beslut-72-sni-kodernas-sökväg-kommer-från-legalentity-och-lagras-som-ett-träd
[§5.7]: 05-synlighet-och-kunddata.md#57-etiketter

