# 13. Risker och öppna frågor

Del av [arkitekturen för party-overview](README.md).

## 13.1 Öppna frågor

| # |                                                                                                                                                 Fråga                                                                                                                                                 |                        Förslag tills den är besvarad                        |
|---|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| 1 | Det gemensamma meddelandeformatet (AsyncAPI) definieras senare.                                                                                                                                                                                                                                       | [§3.1] är kravlistan. C5 och C11 kan inte avslutas innan formatet är klart. |
| 2 | Får Casedatas namespaces `SBK_PARKING_PERMIT` och `ANGE_PARKING_PERMIT` publiceras, och party-assets parkeringstillstånd för rörelsehindrade (`PARKINGPERMIT`, `PARKING_PERMIT`)? Parkeringstillstånd och färdtjänst kan avslöja hälsorelaterade förhållanden, och engagemang syns för hela kommunen. | Nej, inte förrän dataskyddsombudet har svarat ([beslut 37], [beslut 50]).   |

## 13.2 Förutsättningar före driftsättning

|                                                       Förutsättning                                                       |                                                                Behövs för                                                                |
|---------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| party-overviews applikation i WSO2 är registrerad som behörig för `ShowClassified=true` i Metakatalogens tabell ([§5.4]). | Meddelandeflödet (C11), noteringar som skapar kunder (C8) och den nattliga skyddskontrollen (C10). Utan behörigheten svarar Citizen 401. |
| En egen OpenSearch-container per miljö (T2).                                                                              | Sökningen (C12, C13)                                                                                                                     |
| Köer och exchanges i rabbitmq-topology (T1).                                                                              | Meddelandeflödet (C11)                                                                                                                   |
| Kontaktpersonsrollerna är satta via REST innan lyssnarna slås på ([§12]). SM:s roller tas fram ur produktionens metadata. | Meddelandeflödet (C11). Utan rollerna blir kontaktpersoner med id kunder.                                                                |

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

  **Hantering:** Prefetch 1 och 10–20 ms per meddelande ger 50–100 meddelanden/s, alltså 2–5 timmar. Det blir
  långsammare när nya privatkunder kräver ett Citizen-anrop. Grundladdningen körs utanför kontorstid, och tiden mäts i
  test först. Casedata (cirka 5 000) tar några minuter.

- Den nattliga skyddskontrollen belastar Citizen: antalet privatkunder delat med 100 anrop per natt.

  **Hantering:** `batch-size` är konfigurerbar. Belastningen stäms av med den som förvaltar Citizen.

- Om Citizen inte svarar skapas nya privatkunder med skyddsstatusen `UNKNOWN`. De visas utan namn och adress tills den
  nattliga kontrollen har fått svar från Citizen ([§5.4]).

  **Hantering:** Avsiktligt ([beslut 48]). Kunden behandlas som skyddad tills statusen är känd, så inga uppgifter sparas
  för tidigt, och köerna står inte still medan Citizen är nere.

- Envelope-data (t.ex. en ärendetitel) eller en notering kan innehålla skyddsvärda uppgifter om en skyddad kund.

  **Hantering:** Det ansvaret ligger hos källsystemen och verksamheterna. Kunden visas som skyddad i varje vy, så den
  som läser vet att uppgifterna är känsliga.

- Den tekniska loggen växer utan gräns.

  **Hantering:** Indexen klarar stora volymer. Rader kan raderas med `DELETE /{municipalityId}/technical-log` ([§8.2]).

- Om skyddet tillkommer i folkbokföringen hos en befintlig kund, ser party-overview det först vid nästa nattliga
  kontroll. Fram till dess kan ett meddelande hinna spara namn och adress.

  **Hantering:** Fönstret är högst ett dygn, eftersom kontrollen tar med alla privatkunder varje natt ([§5.4]). Ingen
  publicerare skickar i dag `protection`, så skyddet kommer bara från folkbokföringen ([§11.1]).

- När opt-in stängs av för ett namespace i SM eller Casedata ligger dess engagemang kvar i party-overview.

  **Hantering:** Publiceraren kör grundladdningsjobbet för namespacet, och relayn publicerar `DELETE` för objekten,
  eftersom namespacet saknar opt-in ([§11.1], SM2, CD2). Slås opt-in på igen kommer ett objekt tillbaka först när
  dess version har ökat, eftersom tombstonen har samma version ([§3.4]).

- En reindexering som avbryts, t.ex. av en omstart, lämnar ett halvbyggt index.

  **Hantering:** Låset släpps inom fem minuter ([§6.2]). README beskriver att reindexeringen då körs igen.

- Varje namespace ger upp till tre fält i sökindexet (`contacts_ns_`, `phones_ns_` och `notes_ns_`). OpenSearch
  tillåter förvalt 1 000 fält per index, och noteringarnas namespace väljs av anroparen.

  **Hantering:** Antalet namespaces är några tiotal. `index.mapping.total_fields.limit` höjs i indexinställningarna om
  det behövs, och ett mappningsfel syns som `attempts ≥ 5` i `OpenSearchHealthIndicator`.

- En kund som bara har noteringar syns för alla verksamheter, och den tekniska loggen visar vem som skapade den. Den
  som kan anropa API:t kan också ta reda på en persons skyddsstatus genom att skapa en notering och läsa kunden.

  **Hantering:** Åtkomsten till API:t avgör vem som kan göra det ([beslut 30]).

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

- Namn och adress kan skilja sig mellan källor.

  **Hantering:** "Nyast vinner" på `sourceModified` ger ett deterministiskt resultat. Källorna bör vårda sina uppgifter.

- Ett meddelande som är äldre än lagrad version tappar sina aktiviteter.

  **Hantering:** Single-active-consumer och outbox per objekt ger ordning. Det kan bara hända om ett meddelande läggs
  tillbaka efter ett fel.

- Index i Flyway och entiteter glider isär.

  **Hantering:** `SchemaParityIT`.

- En kund som raderats väcks till liv av ett nytt engagemang.

  **Hantering:** Avsiktligt, eftersom källan är master. Vid radering enligt GDPR måste även källorna radera.

- Det tar tid innan en ändring av kontaktpersonsrollerna slår igenom.

  **Hantering:** Dokumenterat ([§5.3]). En grundladdning från källan räknar om allt.

- En part som har både en kontaktpersonsroll och en annan roll, t.ex. `APPLICANT` och `CONTACT_PERSON`, blir bara
  kontaktperson och aldrig kund ([beslut 15]). Saknar objektet en företagskund ignoreras parten helt.

  **Hantering:** Accepteras tills vidare. SM har en roll per stakeholder, så det gäller Casedata och contract, där en
  part kan ha flera roller.

- party-overview slår upp organisationsnummer mot LegalEntity med numret i URL:en
  (`GET /{municipalityId}/{organizationNumber}/guid`). För en enskild firma är numret ett personnummer, som då kan
  hamna i gatewayens och LegalEntitys loggar.

  **Hantering:** Accepteras ([beslut 32]). LegalEntity saknar ett uppslag med numret i kroppen och är inte vår tjänst.
  Anropet undantas från party-overviews payload-logg, och PII-maskningen är påslagen ([§7.1]).

- En kontaktperson utan partyId och legalId kan inte kontrolleras mot folkbokföringen och sparas med namn och
  kanaler. En kontaktperson som får skydd efter att raden sparades, utan att själv vara kund, ligger kvar tills objektet
  ändras i källan ([§5.3]).

  **Hantering:** Accepteras. Publicerarna skickar partyId när de har det, och nästa ändring av objektet kontrollerar
  kontaktpersonen igen.

- För en enskild firma med ett tiosiffrigt organisationsnummer framgår inte århundradet, som Citizens uppslag av
  personnummer behöver ([§5.4]).

  **Hantering:** C3 verifierar i vilket format LegalEntity ger en enskild firmas nummer, och hur århundradet avgörs.
  Går ägaren inte att avgöra får kunden `UNKNOWN`.

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
[beslut 15]: 00-beslutslogg.md#beslut-15-party-overview-härleder-kontaktpersoner
[beslut 30]: 00-beslutslogg.md#beslut-30-de-administrativa-endpoints-ligger-i-samma-api
[beslut 32]: 00-beslutslogg.md#beslut-32-organisationsnummer-slås-upp-direkt-mot-legalentity
[beslut 48]: 00-beslutslogg.md#beslut-48-okänd-skyddsstatus-när-citizen-inte-svarar
[§13.2]: #132-förutsättningar-före-driftsättning
[beslut 37]: 00-beslutslogg.md#beslut-37-casedata-publicerar-bara-sbk_mex-tills-vidare
[beslut 50]: 00-beslutslogg.md#beslut-50-party-assets-publicerar-bara-asset-typer-på-en-vitlista
[§7.1]: 07-integrationer.md#71-uppslag-av-partyid

