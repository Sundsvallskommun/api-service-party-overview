# 11. Publicerarna

Del av [arkitekturen för party-overview](README.md).

## 11.1 Gemensamt för alla publicerare

1. **Beroende:** `dept44-starter-rabbitmq` och `Dept44RabbitPublisher`.
2. **Outbox ([beslut 20]):**
   - Tabellen `party_overview_outbox` (`id`, `object_type`, `object_id`, `municipality_id`, `activities` som JSON,
     `version`, `created`) skrivs i samma transaktion som ändringen. `version` sätts bara vid en radering, eftersom
     objektet inte finns kvar när relayn körs, och blir versionen i `DELETE`-meddelandet.
   - Raden skrivs av en `@TransactionalEventListener(phase = BEFORE_COMMIT)`, eftersom Hibernates entity-callbacks inte
     får skriva.
   - Händelsen skickas av servicemetoderna, inte från entity-callbacks. En callback som `@PostPersist` körs när
     Hibernate flushar vid commit, efter att BEFORE_COMMIT-lyssnarna redan har körts, och då skrivs ingen rad.
   - Det blir en rad per objekt och transaktion, med transaktionens alla aktiviteter samlade på raden.
3. **Relay:**
   - Körs direkt efter commit och dessutom schemalagt med ShedLock.
   - Väntande rader grupperas per objekt i den ordning de skrevs. Relayn läser objektets **aktuella** tillstånd, bygger
     ett meddelande med alla väntande aktiviteter och publicerar `UPDATE`, eller `DELETE` om objektet är borta.
   - Relayn tar ett delat lås på objektets rad (`SELECT … FOR SHARE`) innan den läser raderna och tillståndet, så att
     de hör ihop. Utan låset kan en ändring committas mellan läsningarna. Tillståndet blir då version N+1 medan bara
     raden för N är läst, och när raden för N+1 publiceras senare har den samma version. party-overview tolkar den som
     en rättning, och dess aktiviteter hamnar aldrig i historiken ([§3.4]). Låset hålls tills brokern har bekräftat, och
     en skrivning till objektet väntar under tiden.
   - Raderna tas bort först när brokern har bekräftat meddelandet. En rad som misslyckas blockerar senare rader för
     samma objekt.
   - `message-id` är ett nytt UUID per publicering. Det återanvänds om samma publicering görs om efter `TIMEOUT`.
4. **Version:** objektets `@Version`. Den ska öka vid varje ändring som publiceras.
5. **Vem:** `performedBy` fångas när raden skrivs, från dept44:s `Identifier` (`X-Sent-By`).
6. **Parter:** partyId när publiceraren har det. legalId bara när partyId saknas, och alltid med `partyType`. När
   publiceraren vet att en part har skyddade personuppgifter sätts `protection`, och då skickas varken namn, adress eller
   kontaktkanaler för den parten.
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
8. **AsyncAPI:** publiceraren exponerar sitt AsyncAPI-dokument enligt det gemensamma formatet. Mönster: party-assets
   `origin/publish-asset-events` `AssetEventsAsyncApi`.

## 11.2 SupportManagement (`ERRAND`)

- **Opt-in:** En ny egenskap i `namespace_config` slår på publiceringen per namespace. Namespaces utan den publicerar
  inget.
- **Publiceringspunkter:** Alla anrop till `publishToProcess` i `service/EventService.java`: i dag `createErrandEvent`,
  `createErrandEventWithoutNotification`, `createProcessCommandEvent`, `createDecisionEvent` och
  `publishDeletionToProcess`. Det täcker även ändringar från `EmailReaderWorker` och `MessageExchangeSyncService`. Ett
  anrop ger flera events, men det blir en outbox-rad per ärende och transaktion. Mönster: `ProcessEventPublisher` och
  `ProcessEventRelay`.
- **Radering:** Soft delete publiceras som `DELETE`. Restore publiceras som `UPDATE` med högre version, vilket väcker
  objektet till liv ([§3.4]). Hård radering och purge publiceras som `DELETE`.
- **Version:** `ErrandEntity.version`. Det måste verifieras att en ändring som bara rör stakeholders ökar versionen. Gör
  den inte det tvingas en ökning fram (`OPTIMISTIC_FORCE_INCREMENT`).
- **Envelope:** `reference` = `errandNumber`, `title`, `category` = classification category/type, `status`,
  `sourceCreated` = `created`, `sourceModified` = `modified` (annars `created`, eftersom `modified` sätts först vid den
  första ändringen), `namespace`.
- **Parter:** Stakeholders: `externalId` som partyId om det är ett UUID, och `externalIdType` som partytyp.
  `externalIdType` är fritext per namespace, med värden som `PRIVATE`, `ENTERPRISE`, `EMPLOYEE` och `ADMINISTRATOR`.
  Bara `PRIVATE` och `ENTERPRISE` mappas, utan hänsyn till skiftläge, och andra skickas utan id ([§11.1]). `role`, namn,
  adress och `contactChannels` följer med. Kanaltypen är fritext i SM och mappas utan hänsyn till skiftläge: `EMAIL`
  blir `EMAIL`, och `PHONE` och `SMS` blir `PHONE` ([§11.1]). Stakeholders utan partyId skickas med, eftersom de kan
  vara kontaktpersoner. `parameters` skickas aldrig.
- **Utkast:** Ärenden med `lifecycle = DRAFT` publiceras inte ([beslut 26]). När ett utkast skickas in publiceras det
  som `UPDATE`.
- **Skyddad identitet:** Om SM vet att en stakeholder har skyddade personuppgifter sätts `protection`, och då skickas
  varken namn, adress eller kontaktkanaler ([§3.1]).
- **Grundladdning:** En ny `JobType` som går igenom ärendena per namespace med keyset, som
  `ErrandPurgeRunner.findIdsAfter`, och med ShedLock per namespace.

## 11.3 party-assets (`ASSET`)

Grenen `publish-asset-events` publicerar redan events med en entity-listener och `@TransactionalEventListener` efter
commit. Den har också en republish-endpoint och ett AsyncAPI-dokument. Den byggs om enligt [§11.1]:

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

- **Tidsstämplar:** Nya kolumner `created` och `modified` på kontraktet. Det är en additiv migrering, och befintliga
  rader får `modified = created = now()`. `modified` sätts även när kontraktet skapas, eftersom den blir
  `sourceModified`.
- **Vem:** `Identifier` fångas vid varje skrivning. Klienterna måste skicka `X-Sent-By`.
- **Outbox:** En egen CRM-outbox. Den befintliga är byggd för billing, har ett enda mål och tar bort raden.
  Publiceringspunkter: `ContractService` (create, patch, update, delete), `ContractTerminationWorker` och
  `ContractAutoExtensionWorker`, även vid vanlig periodförlängning, som saknar outbox-rad idag.
- **Version:** `lockVersion`.
- **Envelope:** `reference` = `contractId`, `category` = `type`, `status`, `validFrom`/`validTo` =
  `startDate`/`endDate`, `sourceCreated` = `created`, `sourceModified` = `modified`. Ingen titel. Inget namespace, så
  engagemangen blir globala ([beslut 23]).
- **Parter:** Stakeholders med `roles`, namn, adress och kontaktkanaler, där `phoneNumber` blir `PHONE` och
  `emailAddress` blir `EMAIL`. `type` blir partytyp: `PERSON` blir `PRIVATE`, och `ORGANIZATION`, `ASSOCIATION`,
  `MUNICIPALITY` och `REGION` blir `ENTERPRISE`. `OTHER` och null skickas utan id ([§11.1]). partyId om det finns,
  annars `organizationNumber` som legalId med `ENTERPRISE`. `CONTACT_PERSON` skickas som en vanlig part, och
  party-overview kopplar den till företagen ([§5.3]).
- **Grundladdning:** Sidvis intern genomgång utan bilagor, som skriver outbox-rader.

## 11.5 Casedata (`ERRAND`, källan `CASE_DATA`)

Casedata har ungefär 5 000 ärenden. Det finns ingen outbox och ingen RabbitMQ idag, och tjänsten måste uppgraderas
från dept44 8.0.12 till den version där dept44-starter-rabbitmq är släppt. Analysen är gjord på `origin/main`
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
  partyId saknas. `roles`, namn, adress (`POSTAL`, annars `VISITING_ADDRESS`) och `contactChannels` följer med, där
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
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§11.1]: #111-gemensamt-för-alla-publicerare
[§13.1]: 13-risker-och-oppna-fragor.md#131-öppna-frågor
[beslut 20]: 00-beslutslogg.md#beslut-20-transaktionell-outbox-hos-varje-publicerare
[beslut 23]: 00-beslutslogg.md#beslut-23-contract-och-party-assets-publicerar-utan-namespace
[beslut 26]: 00-beslutslogg.md#beslut-26-supportmanagement-publicerar-inte-utkast
[beslut 37]: 00-beslutslogg.md#beslut-37-casedata-publicerar-bara-sbk_mex-tills-vidare

