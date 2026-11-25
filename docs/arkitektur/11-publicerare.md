# 11. Publicerarna

Del av [arkitekturen för party-overview](README.md).

## 11.1 Gemensamt för alla publicerare

1. **Beroende:** `dept44-starter-rabbitmq` och `Dept44RabbitPublisher`, som finns från dept44 8.2.0.
2. **Outbox ([beslut 20]):**
   - Tabellen `party_overview_outbox` (`id`, `object_type`, `object_id`, `municipality_id`, `activities` som JSON,
     `version`, `created`) skrivs i samma transaktion som ändringen. `version` sätts bara vid en radering, eftersom
     objektet inte finns kvar när relayn körs, och blir versionen i `DELETE`-meddelandet.
   - Raden skrivs av en `@TransactionalEventListener(phase = BEFORE_COMMIT)`, eftersom Hibernates entity-callbacks inte
     får skriva.
   - Händelsen skickas av servicemetoderna, inte från entity-callbacks. En callback som `@PostPersist` körs när
     Hibernate flushar vid commit, efter att BEFORE_COMMIT-lyssnarna redan har körts, och då skrivs ingen rad.
   - Varje publiceringspunkt körs i en transaktion. Utan transaktion kastas händelsen tyst, eftersom en
     `@TransactionalEventListener` har `fallbackExecution = false` som förval. Där en publiceringspunkt inte har någon
     transaktion, t.ex. `PR3Importer` i party-assets, skrivs outbox-raden i stället direkt i en egen transaktion, som
     SM:s `ProcessEventPublisher` gör.
   - Det blir en rad per objekt och transaktion, med transaktionens alla aktiviteter samlade på raden.
3. **Relay:**
   - Körs direkt efter commit och dessutom schemalagt med ShedLock.
   - Väntande rader grupperas per objekt i den ordning de skrevs. Relayn läser objektets **aktuella** tillstånd, bygger
     ett meddelande med alla väntande aktiviteter och publicerar `UPDATE`. Den publicerar `DELETE` när objektet är borta
     eller inte ska synas: soft-deletat (SM), sekretessmarkerat (Casedata), i ett namespace utan opt-in eller med
     åtkomstkontroll (SM), eller av en asset-typ som inte står på vitlistan (party-assets).
   - Relayn tar ett delat lås på objektets rad innan den läser raderna och tillståndet, så att de hör ihop. I MariaDB
     heter det `SELECT … LOCK IN SHARE MODE`, eftersom `FOR SHARE` ger syntaxfel i MariaDB, både i 10.6 och i 11.4. I
     JPA används `@Lock(PESSIMISTIC_READ)`, som Hibernates MariaDB-dialekt skriver om till `lock in share mode`. Utan låset kan en ändring committas mellan läsningarna. Tillståndet blir då version N+1 medan bara
     raden för N är läst, och när raden för N+1 publiceras senare har den samma version. party-overview tolkar den som
     en rättning, och dess aktiviteter hamnar aldrig i historiken ([§3.4]). Låset hålls tills brokern har bekräftat, och
     en skrivning till objektet väntar under tiden.
   - Raderna tas bort först när brokern har bekräftat meddelandet. En rad som misslyckas blockerar senare rader för
     samma objekt.
   - En health-indikator blir `DOWN` när den äldsta väntande raden är äldre än en konfigurerbar gräns, som
     `ProcessEventRelay.findHealthFault` i SM. Annars märks det inte om publiceringen har stannat, t.ex. för att
     brokern returnerar meddelandena.
   - `message-id` är ett nytt UUID per publicering. Det återanvänds om samma publicering görs om efter `TIMEOUT`.
4. **Version:** objektets `@Version`. Den ska öka vid varje ändring som publiceras.
5. **Vem:** `performedBy` fångas när raden skrivs, från dept44:s `Identifier` (`X-Sent-By`).
6. **Parter:** partyId när publiceraren har det. legalId bara när partyId saknas, och alltid med `partyType`. När
   publiceraren vet att en part har skyddade personuppgifter sätts `protection`, och då skickas varken namn, adress eller
   kontaktkanaler för den parten. Ingen av de fyra publicerarna har i dag någon sådan uppgift, så fältet finns för
   kommande publicerare.
   - legalId normaliseras till formatet i [§3.1]: bindestreck tas bort. Ett värde som ändå inte valideras skickas inte,
     och parten skickas utan id.
   - Källans partytyp mappas till `PRIVATE` eller `ENTERPRISE`. En part vars typ inte går att mappa skickas utan id. Den
     kan då bara bli kontaktperson, och resten av meddelandet tillämpas.
   - Textfälten hålls inom maxlängderna i [§3.1].
   - Kontaktkanalernas typ mappas till `EMAIL` eller `PHONE`. Kanaler med en annan typ skickas inte, eftersom ett okänt
     värde dead-letterar hela meddelandet ([§3.7]). En kanal som förekommer flera gånger skickas en gång.
7. **Grundladdning:**
   - En admin-endpoint eller ett jobb skriver outbox-rader utan aktiviteter för alla objekt i kommunen, i batchar med
     keyset-paginering.
   - Den körs asynkront, med ShedLock så att den inte startar två gånger.
   - Den använder samma outbox som vanliga ändringar och behåller därför ordningen per objekt.
   - Den kan köras för ett namespace som just har stängt av opt-in. Relayn publicerar då `DELETE` för namespacets
     objekt, eftersom de inte ska synas.
8. **AsyncAPI:** publiceraren exponerar sitt AsyncAPI-dokument enligt det gemensamma formatet. Mönster: party-assets
   `origin/publish-asset-events` `AssetEventsAsyncApi`.

## 11.2 SupportManagement (`ERRAND`)

- **Opt-in:** En ny egenskap i `namespace_config` slår på publiceringen per namespace. Namespaces utan den publicerar
  inget. Ett namespace med åtkomstkontroll (`accessControl = true`) kan inte slå på publiceringen, eftersom SM där
  styr läsrätten per ärende med etiketter och trimmar fälten per roll ([beslut 51]). Slås åtkomstkontrollen på för ett
  namespace som publicerar, behandlas det som att opt-in stängs av ([§13.3]).
- **Publiceringspunkter:** De ställen där ärendet självt ändras: när det skapas, ändras (även parter, status och
  klassning), raderas eller återställs. Det är de anrop till `publishToProcess` i `service/EventService.java` som
  följer en sådan ändring. Kommunikation till och från ärendet, alltså e-post, sms och webbmeddelanden via REST,
  `EmailReaderWorker`, `WebMessageCollectorWorker` och `MessageExchangeSyncService`, publiceras inte, så länge den inte
  också ändrar ärendet ([beslut 55]). Ett anrop ger flera events, men det blir en outbox-rad per ärende och
  transaktion. Mönster: `ProcessEventPublisher` och `ProcessEventRelay`.
- **Radering:** Soft delete publiceras som `DELETE`. Restore publiceras som `UPDATE` med högre version, vilket väcker
  objektet till liv ([§3.4]). Hård radering och purge publiceras som `DELETE`.
- **Version:** `ErrandEntity.version`. Det måste verifieras att en ändring som bara rör stakeholders ökar versionen. Gör
  den inte det tvingas en ökning fram (`OPTIMISTIC_FORCE_INCREMENT`).
- **Envelope:** `reference` = `errandNumber`, `title`, `category` = classification category/type, `status`,
  `sourceCreated` = `created`, `sourceModified` = `modified` (annars `created`, eftersom `modified` sätts först vid den
  första ändringen), `namespace`.
- **Parter:** Stakeholders: `externalId` som partyId om det är ett UUID, och `externalIdType` som partytyp.
  `externalIdType` är fritext per namespace, med värden som `PRIVATE`, `ENTERPRISE`, `EMPLOYEE` och `ADMINISTRATOR`.
  Bara `PRIVATE` och `ENTERPRISE` mappas, utan hänsyn till skiftläge. Stakeholders med `EMPLOYEE` eller `ADMINISTRATOR`
  skickas inte, på samma sätt som handläggarna i Casedata. De kan ändå aldrig bli kunder, och deras uppgifter ska inte
  gå över kön. Andra typer skickas utan id ([§11.1]). `role`, namn,
  adress och `contactChannels` följer med. Kanaltypen är fritext i SM och mappas utan hänsyn till skiftläge: `EMAIL`
  blir `EMAIL`, och `PHONE` och `SMS` blir `PHONE` ([§11.1]). Stakeholders utan partyId skickas med, eftersom de kan
  vara kontaktpersoner. `parameters` skickas aldrig.
- **Utkast:** Ärenden med `lifecycle = DRAFT` publiceras inte ([beslut 26]). När ett utkast skickas in publiceras det
  som `UPDATE`.
- **Skyddad identitet:** SM har i dag ingen uppgift om skyddade personuppgifter på stakeholders och skickar därför inte
  `protection`. Skyddet för SM:s kunder kommer från folkbokföringen ([§5.4]).
- **Grundladdning:** En ny `JobType` som går igenom ärendena per namespace med keyset, som
  `ErrandPurgeRunner.findIdsAfter`, och med ShedLock per namespace.

## 11.3 party-assets (`ASSET`)

Grenen `publish-asset-events` publicerar redan events med en entity-listener och `@TransactionalEventListener` efter
commit. Den har också en republish-endpoint och ett AsyncAPI-dokument. Den byggs om enligt [§11.1]:

- **Asset-typer:** bara typer som står på en vitlista i party-assets konfiguration
  (`integration.party-overview.asset-types`) publiceras ([beslut 50]). Parkeringstillstånd för rörelsehindrade
  (`PARKINGPERMIT` och `PARKING_PERMIT`) står inte på listan förrän dataskyddsombudet har svarat ([§13.1]), som för
  Casedatas parkeringsärenden ([beslut 37]). Tas en typ bort från listan publiceras dess assets som `DELETE` vid nästa
  grundladdning, och läggs en typ till publiceras de som `UPDATE`.
- **Outbox:** Servicemetoderna och `PR3Importer` skickar en Spring-händelse, och en `BEFORE_COMMIT`-lyssnare skriver
  outbox-raden. `AssetEntityListener` kan inte skicka händelsen, eftersom dess callbacks körs när Hibernate flushar vid
  commit och då kommer efter BEFORE_COMMIT ([§11.1]). Det täcker `createAsset`, `updateAsset` (även originalet som blir
  `REPLACED`), `deleteAsset`, `AssetExpirationWorker` och `PR3Importer`, som sparar direkt i repositoryt.
- **Version:** `AssetEntity.version`, inte `revision`, som inte ökar för drafts och delete.
- **Vem:** `Identifier` sparas med typ, även vid delete.
- **Envelope:** `reference` = `assetId`, `title`, `category` = `type`, `status`, `validFrom` = `issued`, `validTo`,
  `sourceModified` = `updated`, annars `created`. Äldre rader saknar båda och får en fast tidpunkt,
  `1970-01-01T00:00:00Z`, så att de aldrig räknas som nyast. Inget namespace, så engagemangen blir globala
  ([beslut 23]).
- **Parter:** Exakt ett: `partyId` och `partyType`, utan kontaktuppgifter.
- **Grundladdning:** `POST /{municipalityId}/asset-events/republish` skriver outbox-rader i batchar i stället för att
  publicera direkt.

## 11.4 contract (`CONTRACT`)

- **Tidsstämplar:** Nya, nullbara kolumner `created` och `modified` på kontraktet. Det är en additiv migrering, och
  befintliga rader behåller null. `created` och `modified` sätts när ett kontrakt skapas, och `modified` vid varje
  ändring. Saknar ett kontrakt `modified` publiceras `sourceModified` som den fasta tidpunkten `1970-01-01T00:00:00Z`,
  som i party-assets. Med `now()` på befintliga rader skulle varje gammalt kontrakt räknas som nyast, och contracts
  namn och adress skulle då skriva över de andra källornas efter grundladdningen ([§5.2]).
- **Vem:** `Identifier` fångas när `X-Sent-By` finns, och blir `performedBy`. Utan headern saknas `performedBy`, så att
  contracts befintliga klienter inte behöver ändras.
- **Outbox:** En egen CRM-outbox. Den befintliga är byggd för billing, har ett enda mål och tar bort raden.
  Publiceringspunkter: `ContractService` (create, patch, update, delete), `ContractTerminationWorker` och
  `ContractAutoExtensionWorker`, även vid vanlig periodförlängning, som saknar outbox-rad idag.
- **Version:** `lockVersion`.
- **Envelope:** `reference` = `contractId`, `category` = `type`, `status`, `validFrom`/`validTo` =
  `startDate`/`endDate`, `sourceCreated` = `created`, `sourceModified` = `modified`. Ingen titel. Inget namespace, så
  engagemangen blir globala ([beslut 23]).
- **Parter:** Stakeholders med `roles`, namn, adress och kontaktkanaler, där `phoneNumber` blir `PHONE` och
  `emailAddress` blir `EMAIL` ([beslut 49]).
  - Bara stakeholders med minst en partsroll skickas: `LESSEE`, `LESSOR`, `BUYER`, `SELLER`, `LEASEHOLDER`, `GRANTOR`,
    `LAND_RIGHT_OWNER`, `PROPERTY_OWNER`, `PRIMARY_BILLING_PARTY` eller `CONTACT_PERSON`. Den som bara är `SIGNATORY`
    eller har en `POWER_OF_ATTORNEY_*`-roll skickas inte. En skickad stakeholder har alla sina roller med sig.
  - Stakeholders av typen `MUNICIPALITY` skickas aldrig, eftersom det är kommunen själv.
  - `type` blir partytyp: `PERSON` blir `PRIVATE`, och `ORGANIZATION`, `ASSOCIATION` och `REGION` blir `ENTERPRISE`.
    `OTHER` och null skickas utan id ([§11.1]).
  - partyId om det finns, annars `organizationNumber` som legalId med `ENTERPRISE`. `CONTACT_PERSON` skickas som en
    vanlig part, och party-overview kopplar den till företagen ([§5.3]).
- **Grundladdning:** Sidvis intern genomgång utan bilagor, som skriver outbox-rader.

## 11.5 Casedata (`ERRAND`, källan `CASE_DATA`)

Casedata har ungefär 5 000 ärenden. Det finns ingen outbox och ingen RabbitMQ idag, och tjänsten måste uppgraderas
från dept44 8.0.12 till 8.2.0, där dept44-starter-rabbitmq finns. Analysen är gjord på `origin/main`
(2026-10-19).

- **Opt-in:** Egenskapen `integration.party-overview.namespaces` i `application.yml`, eftersom Casedata saknar
  `namespace_config`. Namespaces utan opt-in skriver inga outbox-rader. Bara `SBK_MEX` slås på tills vidare
  ([beslut 37], [§13.1]).
- **Sekretess:** Ett ärende med `confidential = true` publiceras som `DELETE`. När flaggan tas bort publiceras `UPDATE`
  med högre version, och ärendet kommer tillbaka ([§3.4]).
- **Publiceringspunkter:** `ErrandService` (`create`, `update`, `delete`), `StatusService.addToErrand` och
  `StakeholderService` (`update`, båda `replaceOnErrand`, `addToErrand`, `delete`). Servicemetoderna skickar en egen
  Spring-händelse. Den befintliga `ErrandEntity`-händelsen, som triggar Camunda, ska inte återanvändas, eftersom den
  saknas på flera ställen och Camundas anrop inte får ändras. Camunda skriver via REST-API:t och täcks därför.
  Schedulers, noteringar, beslut, fastigheter och bilagor publiceras inte.
- **Vem:** `Identifier`: `AD_ACCOUNT` eller `PARTY_ID`. Annars `SYSTEM`.
- **Radering:** Bara hård radering finns, och den publiceras som `DELETE`. Ärendet finns inte kvar när relayn körs, så
  versionen sparas på outbox-raden ([§11.1]). Det finns ingen restore.
- **Version:** `ErrandEntity.version` (`@Version int`, skickas som long). Publiceringspunkten sätter `updated` på
  ärendet, så att både versionen och `sourceModified` ändras även när bara en part ändras. Det verifieras med ett IT.
- **Envelope:** `reference` = `errandNumber`, `title` = `case_type.display_name` följt av `caseTitleAddition`,
  `category` = `caseType`, `status` = `status.statusType`, `sourceCreated` = `created`, `sourceModified` = `updated`
  (annars `created`), `namespace`. `externalCaseId`, `description` och `phase` skickas inte.
- **Parter:** `errand.stakeholders`, där `PERSON` blir `PRIVATE` och `ORGANIZATION` blir `ENTERPRISE`. `personId`
  skickas som partyId bara om det är ett UUID, och aldrig annars. `organizationNumber` skickas som legalId bara när
  partyId saknas. `roles`, namn, adress (`POSTAL_ADDRESS`, annars `VISITING_ADDRESS`) och `contactChannels` följer med, där
  `CELLPHONE` och `PHONE` blir `PHONE`. Parter med rollen `ADMINISTRATOR` eller med `adAccount`, alltså handläggare,
  skickas inte. Parter utan id skickas med, eftersom de kan vara kontaktpersoner. `extraParameters` och
  `authorizedSignatory` skickas aldrig.
- **Loggning:** `StakeholderEntity.toString` skriver ut personId och organisationsnummer, och `ErrandEntity` är `@Data`.
  Publiceraren får aldrig logga entiteter.
- **Grundladdning:** En admin-endpoint startar ett asynkront jobb med ShedLock, enligt mönstret i
  `MessageExchangeScheduler`. Jobbet går igenom ärendena per namespace med opt-in, med keyset på `id`. Det läser bara
  id:n, 500 per batch med en transaktion per batch, och skriver outbox-rader utan aktiviteter.

[§3.1]: 03-meddelanden.md#31-krav-på-det-gemensamma-formatet
[§3.4]: 03-meddelanden.md#34-versionsmatrisen
[§3.7]: 03-meddelanden.md#37-felhantering
[§5.2]: 05-synlighet-och-kunddata.md#52-namn-och-adress
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§11.1]: #111-gemensamt-för-alla-publicerare
[§13.1]: 13-risker-och-oppna-fragor.md#131-öppna-frågor
[beslut 20]: 00-beslutslogg.md#beslut-20-transaktionell-outbox-hos-varje-publicerare
[beslut 23]: 00-beslutslogg.md#beslut-23-contract-och-party-assets-publicerar-utan-namespace
[beslut 26]: 00-beslutslogg.md#beslut-26-supportmanagement-publicerar-inte-utkast
[beslut 37]: 00-beslutslogg.md#beslut-37-casedata-publicerar-bara-sbk_mex-tills-vidare
[beslut 49]: 00-beslutslogg.md#beslut-49-contract-publicerar-bara-avtalsparter
[§13.3]: 13-risker-och-oppna-fragor.md#133-risker
[beslut 50]: 00-beslutslogg.md#beslut-50-party-assets-publicerar-bara-asset-typer-på-en-vitlista
[beslut 51]: 00-beslutslogg.md#beslut-51-namespaces-med-åtkomstkontroll-i-sm-publiceras-inte
[beslut 55]: 00-beslutslogg.md#beslut-55-kommunikation-i-sm-publiceras-inte

