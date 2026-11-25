# 3. Meddelanden

Del av [arkitekturen för party-overview](README.md).

## 3.1 Krav på det gemensamma formatet

Formatet bygger på AsyncAPI och fastställs separat. party-overview kräver följande. Ett fält markerat **krav** saknas
inte i ett giltigt meddelande, och ett meddelande som bryter mot kraven dead-letteras direkt.

**AMQP-egenskaper**

|    Egenskap    |                                         Krav                                          |
|----------------|---------------------------------------------------------------------------------------|
| `message-id`   | **krav**, UUID. Sparas i den tekniska loggen för spårbarhet.                          |
| `content-type` | `application/json`                                                                    |
| `x-request-id` | Sätts av dept44-starter-rabbitmq och lagras i den tekniska loggen.                    |
| Routing key    | `<objektTyp>.<operation>` med gemener, t.ex. `errand.update` eller `customer.delete`. |

**Kropp**

|                                     Fält                                      |                             Typ                             |                                  Krav                                  |                                                                         Beskrivning                                                                         |
|-------------------------------------------------------------------------------|-------------------------------------------------------------|------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `operation`                                                                   | `UPDATE` \| `DELETE`                                        | **krav**                                                               | `UPDATE` innebär hela tillståndet och används även vid grundladdning.                                                                                       |
| `objectType`                                                                  | `ERRAND` \| `ASSET` \| `CONTRACT` \| `CUSTOMER`             | **krav**                                                               |                                                                                                                                                             |
| `municipalityId`                                                              | sträng                                                      | **krav**                                                               | Valideras med `@ValidMunicipalityId`.                                                                                                                       |
| `namespace`                                                                   | sträng                                                      | valfri                                                                 | Valideras med `@ValidNamespace`. Saknas den räknas objektet och dess kontaktuppgifter som globala.                                                          |
| `objectId`                                                                    | sträng                                                      | **krav** utom för `CUSTOMER`                                           | Objektets id i källan, aldrig ett personnummer.                                                                                                             |
| `version`                                                                     | heltal (long)                                               | **krav**                                                               | Ökar vid varje publicerad ändring av objektet. En `DELETE` bär minst den senaste versionen.                                                                 |
| `sourceModified`                                                              | tidpunkt med zon                                            | **krav** vid `UPDATE`                                                  | När objektet senast ändrades i källan. Sorteringsnyckel och mått för "nyast vinner".                                                                        |
| `activities[]`                                                                | lista                                                       | valfri                                                                 | Det som hände i den här ändringen. Listan är tom vid grundladdning.                                                                                         |
| `activities[].type`                                                           | sträng                                                      | **krav**                                                               | T.ex. `CREATED`, `UPDATED`, `STATUS_CHANGED`. Källan bestämmer värdena.                                                                                     |
| `activities[].description`                                                    | sträng ≤ 1024                                               | valfri                                                                 | Kort text utan personuppgifter.                                                                                                                             |
| `activities[].performedBy.type`                                               | sträng                                                      | valfri                                                                 | T.ex. `AD_ACCOUNT`, `PARTY_ID` eller `SYSTEM`.                                                                                                              |
| `activities[].performedBy.value`                                              | sträng                                                      | valfri                                                                 | Den som utförde ändringen, från `X-Sent-By` i källan.                                                                                                       |
| `activities[].occurredAt`                                                     | tidpunkt med zon                                            | **krav**                                                               |                                                                                                                                                             |
| `object.reference`                                                            | sträng                                                      | valfri                                                                 | Visningsid, t.ex. ärendenummer, `assetId` eller `contractId`.                                                                                               |
| `object.title`                                                                | sträng                                                      | valfri                                                                 |                                                                                                                                                             |
| `object.category`                                                             | sträng                                                      | valfri                                                                 | Typ eller klassificering i källan.                                                                                                                          |
| `object.status`                                                               | sträng                                                      | valfri                                                                 | Källans statuskod. Frontend översätter den.                                                                                                                 |
| `object.validFrom`, `object.validTo`                                          | datum                                                       | valfri                                                                 |                                                                                                                                                             |
| `object.sourceCreated`                                                        | tidpunkt med zon                                            | valfri                                                                 |                                                                                                                                                             |
| `parties[]`                                                                   | lista                                                       | **krav** vid `UPDATE` (kan vara tom), för `CUSTOMER` även vid `DELETE` | Objektets parter. För `CUSTOMER` exakt en, och den pekar ut kunden.                                                                                         |
| `parties[].partyId`                                                           | UUID                                                        | partyId eller legalId                                                  | Bör alltid skickas när publiceraren har det.                                                                                                                |
| `parties[].legalId`                                                           | sträng                                                      | partyId eller legalId                                                  | Personnummer eller organisationsnummer enligt reglerna under tabellen. Används bara när partyId saknas, och party-overview mappar det och kastar det sedan. |
| `parties[].partyType`                                                         | `PRIVATE` \| `ENTERPRISE`                                   | **krav** när partyId eller legalId finns                               | En part vars typ publiceraren inte kan mappa skickas utan id ([§11.1]).                                                                                     |
| `parties[].roles[]`                                                           | strängar                                                    | valfri                                                                 | Källans rollkoder.                                                                                                                                          |
| `parties[].name`                                                              | sträng                                                      | valfri                                                                 | Visningsnamn: för- och efternamn, eller organisationsnamn.                                                                                                  |
| `parties[].addresses[]`                                                       | lista, högst 10                                             | valfri                                                                 | Partens adresser ([§5.2]).                                                                                                                                  |
| `parties[].addresses[].type`                                                  | `POSTAL_ADDRESS` \| `VISITING_ADDRESS` \| `INVOICE_ADDRESS` | **krav**                                                               | Publiceraren mappar källans adresstyper till de här värdena ([§11.1]).                                                                                      |
| `parties[].addresses[].careOf`, `.street`, `.postalCode`, `.city`, `.country` | sträng                                                      | valfri                                                                 |                                                                                                                                                             |
| `parties[].contactChannels[]`                                                 | lista                                                       | valfri                                                                 | Partens kontaktkanaler.                                                                                                                                     |
| `parties[].contactChannels[].type`                                            | `EMAIL` \| `PHONE`                                          | **krav**                                                               | Publiceraren mappar källans kanaltyper till de här värdena ([§11.1]).                                                                                       |
| `parties[].contactChannels[].value`                                           | sträng                                                      | **krav**                                                               | E-postadressen eller telefonnumret.                                                                                                                         |
| `parties[].protection`                                                        | `SECRECY_MARKING` \| `PROTECTED_REGISTRATION`               | valfri                                                                 | Sätts när källan vet att parten har skyddade personuppgifter. Då skickas varken `name`, `addresses` eller `contactChannels`.                                |

En part som saknar både partyId och legalId kan bara bli kontaktperson ([§5.3]). Annars ignoreras den.

legalId skrivs utan bindestreck, och formatet beror på partytypen ([beslut 54]):

- `PRIVATE`: ett personnummer med 12 siffror (`ÅÅÅÅMMDDNNNN`), som dept44:s `ValidPersonalNumber`.
- `ENTERPRISE`: ett organisationsnummer med 10 siffror, som dept44:s `ValidOrganizationNumber`, eller för en enskild
  firma ägarens personnummer med 10 eller 12 siffror. `ValidOrganizationNumber` räcker inte ensam, eftersom den kräver
  att tredje siffran är 2–9, och i ett personnummer är den 0 eller 1.
- Kontrollsiffran ska stämma (Luhn på de tio sista siffrorna).

Publiceraren normaliserar, och ett värde som ändå inte följer formatet skickas inte. party-overview kontrollerar samma
format och slår upp ett `PRIVATE`-nummer mot Citizen och ett `ENTERPRISE`-nummer mot LegalEntity ([§7.1]).

Textfälten har maxlängder, som valideras med `@Size` och motsvarar kolumnerna i [§4.3]. Ett längre värde är ett
valideringsfel. Publiceraren håller sig därför inom gränserna och kortar en titel som blir för lång.

- 16: `postalCode`
- 32: `activities[].performedBy.type`
- 64: `activities[].type`, `parties[].roles[]`
- 255: `objectId`, `object.reference`, `object.category`, `object.status`, `activities[].performedBy.value`,
  `parties[].name`, adressfälten utom `postalCode`, `contactChannels[].value`
- 512: `object.title`
- 1 024: `activities[].description`

Exempel:

```json
{
  "operation": "UPDATE",
  "objectType": "ERRAND",
  "municipalityId": "2281",
  "namespace": "CONTACTCENTER",
  "objectId": "f0882f1d-06bc-47fd-b017-1d8307f5ce95",
  "version": 12,
  "sourceModified": "2026-11-20T10:15:30.123+01:00",
  "activities": [
    {
      "type": "STATUS_CHANGED",
      "description": "Status ändrad till ONGOING",
      "performedBy": { "type": "AD_ACCOUNT", "value": "joe01doe" },
      "occurredAt": "2026-11-20T10:15:30.123+01:00"
    }
  ],
  "object": {
    "reference": "KC-26110001",
    "title": "Fråga om bygglov",
    "category": "BUILDING_PERMIT",
    "status": "ONGOING",
    "sourceCreated": "2026-11-18T08:00:00.000+01:00"
  },
  "parties": [
    {
      "partyId": "8e2c9f0b-5a3d-4c11-9d0e-2f1b7a6c4d21",
      "partyType": "ENTERPRISE",
      "roles": ["APPLICANT"],
      "name": "Företaget AB",
      "addresses": [
        { "type": "POSTAL_ADDRESS", "street": "Box 123", "postalCode": "85102", "city": "Sundsvall" },
        { "type": "VISITING_ADDRESS", "street": "Storgatan 1", "postalCode": "85230", "city": "Sundsvall" }
      ],
      "contactChannels": [{ "type": "EMAIL", "value": "info@foretaget.se" }]
    },
    {
      "partyType": "PRIVATE",
      "roles": ["CONTACT_PERSON"],
      "name": "Anna Andersson",
      "addresses": [{ "type": "VISITING_ADDRESS", "street": "Storgatan 1", "postalCode": "85230", "city": "Sundsvall" }],
      "contactChannels": [{ "type": "PHONE", "value": "070-1234567" }]
    }
  ]
}
```

party-overview har en lokal kopia av formatets scheman i `src/main/resources/integrations/party-overview-messages.yaml`
och genererar sina modeller från den. Filen är ett AsyncAPI-dokument, som openapi-generator inte kan läsa, så modellerna
genereras med ZenWave SDK ([§9]).

## 3.2 Topologi

|    Publicerare    |             Exchange              |              Kö för party-overview               |   Källa (`source`)   |
|-------------------|-----------------------------------|--------------------------------------------------|----------------------|
| SupportManagement | `api-fabriken.support-management` | `api-fabriken.party-overview.support-management` | `SUPPORT_MANAGEMENT` |
| party-assets      | `api-fabriken.party-assets`       | `api-fabriken.party-overview.party-assets`       | `PARTY_ASSETS`       |
| contract          | `api-fabriken.contract`           | `api-fabriken.party-overview.contract`           | `CONTRACT`           |
| Casedata          | `api-fabriken.case-data`          | `api-fabriken.party-overview.case-data`          | `CASE_DATA`          |

Varje kö deklareras i rabbitmq-topology som en quorum-kö med följande:

- dead-letter-exchange och en egen dead letter-kö (`….dlq`)
- `x-delivery-limit: 5`
- `x-dead-letter-strategy: at-least-once` och `x-overflow: reject-publish`. Utan dem är dead-letteringen i en
  quorum-kö at-most-once, och ett meddelande kan försvinna på vägen till dead letter-kön.
- `x-single-active-consumer: true`. Argumentet går inte att lägga till på en befintlig quorum-kö, så det måste finnas
  när kön skapas.
- bindning till publicerarens exchange med `#`. Kön tar emot alla objekttyper från källan.

Dead letter-köerna har ingen TTL, och ett meddelande ligger kvar tills någon har tagit hand om det. Köerna kan
innehålla personuppgifter, t.ex. ett legalId eller namn, och därför har bara drift och utvecklare åtkomst. Det larmas
när en dead letter-kö inte är tom ([§12.1]).

party-overview deklarerar ingen topologi själv, och dess AMQP-användare saknar configure-rättighet.

## 3.3 Flödet genom tjänsten

```
MessageListener (alla köer)                          ingen transaktion
  └─ MessageService.handle(source, messageId, message)
       1. mappa till ObjectChange och validera (@Validated)  ─ ogiltigt → dead letter direkt
       2. läs kontaktpersonsroller för (kommun, källa)
       3. dela upp parterna i kunder och kontaktpersoner (§5.3)
       4. legalId → partyId för kundparter och kontaktpersoner (§7.1): PRIVATE via Citizen (batch),
          ENTERPRISE via LegalEntity
                                                             ─ saknas eller fel format → parten hoppas över
                                                               (WARN utan PII)
          för nya ENTERPRISE-kunder, och ENTERPRISE-kontaktpersoner som inte finns som kunder:
          organisationsnumret från meddelandet eller LegalEntity, och ägarens partyId om det är en
          enskild firma (§5.4)                               ─ svarar inte → UNKNOWN
       5. folkbokföringens skyddsstatus för nya kunder med registry_party_id, och för kontaktpersoner
          som inte finns som kunder: personen själv, eller ägaren till en enskild firma
          (CitizenIntegration, batch, ShowClassified=true, §5.3, §5.4)
                                                             ─ svarar inte eller 5xx → UNKNOWN
       6. MessageWriter.apply(change)                         @Transactional(READ_COMMITTED), egen bean
            a. lås källobjektet (source_object: IODKU, sedan SELECT … FOR UPDATE)
            b. avgör enligt versionsmatrisen (§3.4)           ─ inaktuellt → bara teknisk logg
            c. lås alla berörda kunder i partyId-ordning, före alla kontroller: meddelandets kundparter
               (IODKU och FOR UPDATE), och kunderna med engagemang i objektet och kontaktpersonerna som
               finns som kunder (bara FOR UPDATE)
            d. tillämpa UPDATE eller DELETE (§3.5) och räkna om kundernas skyddsnivå (§5.4).
               Kontaktpersoner med en skyddsnivå som inte är NONE sparas inte (§5.3).
            e. skriv teknisk logg för varje berörd kund och markera kunden för sökindexering
               (search_index_queue, §6.2)
            f. radera de kunder som ska raderas automatiskt (§3.6)
```

- Feign-anropen görs utanför transaktionen, så att en långsam Citizen eller LegalEntity inte håller lås i databasen.
- Kunderna med engagemang i objektet läses efter att `source_object` har låsts. Låset hindrar andra meddelanden om
  samma objekt, men inte en kundradering via REST eller `CUSTOMER DELETE`, som tar bort engagemangen genom kaskaden.
  Därför låses de kunderna bara med `FOR UPDATE`, och en kund som har hunnit raderas hoppas över i stället för att
  skapas på nytt. Objektets engagemang läses om efter kundlåsen, och steg d–f utgår från den läsningen.
- Ett legalId som inte finns hos Citizen eller LegalEntity, eller som har fel format, ger ingen part, men resten av
  meddelandet tillämpas, även steg 5 i [§3.5], enligt [beslut 16]. Ett tillfälligt fel görs om ([§3.7]) och når aldrig
  tillämpningen.
- Kontaktpersonernas skyddsnivå avgörs efter kundlåsen. För en kontaktperson som finns som kund gäller kundens nivå,
  och annars nivån från steg 5. En kontaktperson som var kund i steg 5 men har hunnit raderas saknar nivå och sparas
  inte. Låset gör att en kontaktrad för en person som är kund inte kan skrivas medan personens kontaktrader raderas
  ([§5.3]).
- `MessageWriter` körs med READ COMMITTED, som SM:s `ProcessEventRelay`. Med MariaDB:s förval REPEATABLE READ läser en
  vanlig fråga ögonblicksbilden från transaktionens första läsning. En kontroll efter en låsväntan skulle då missa det
  som en annan transaktion hann skriva, t.ex. ett nytt engagemang på en kund som annars raderas automatiskt och tar det
  nya engagemanget med sig i kaskaden.
- `MessageListener` tar källan ur headern `AmqpHeaders.CONSUMER_QUEUE` och slår upp den i konfigurationen
  `integration.sources` ([§12]).
- Native insert kör inte `@PrePersist`, så IODKU-satserna sätter själva `id`, `created` och `modified`.

## 3.4 Versionsmatrisen

`s` är versionen i `source_object`, och `v` är meddelandets version.

| Lagrat `source_object` |                                           `UPDATE` med version v                                            |                                    `DELETE` med version v                                    |
|------------------------|-------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| saknas                 | Tillämpa. Raden skapas med version v.                                                                       | Spara en tombstone (`deleted = true`, version v).                                            |
| aktivt, version s      | `v < s`: inaktuellt.<br/>`v = s`: tillämpa som rättning, utan historik.<br/>`v > s`: tillämpa med historik. | `v < s`: inaktuellt.<br/>`v ≥ s`: radera objektets engagemang och markera raden som raderad. |
| raderat, version s     | `v ≤ s`: inaktuellt.<br/>`v > s`: objektet väcks till liv. Tillämpa med historik.                           | `v ≤ s`: inaktuellt.<br/>`v > s`: höj versionen, inget mer.                                  |

- Ett inaktuellt meddelande ändrar ingenting utom den tekniska loggen (`IGNORED_STALE`).
- För `CUSTOMER` sparas aldrig någon tombstone, eftersom `object_id` är kundens partyId. En `DELETE` som inte är
  inaktuell raderar kunden om den finns, även när `source_object` saknas, och raderar alla rader i `source_object` för
  `CUSTOMER` med partyId:t, även när kunden saknas. Kunden kan ha skapats av engagemang från andra källor ([§3.5]).
- Om samma meddelande levereras igen blir det en rättning med samma version, och därmed ingen dubblerad historik.
  Därför behövs inget register över message-id.
- En grundladdning medan publiceraren är i drift är ofarlig. En ögonblicksbild som är äldre än en DELETE stoppas av
  tombstonen.
- Raderade `source_object`-rader (tombstones) sparas för alltid och innehåller inga personuppgifter, eftersom rader för
  `CUSTOMER` aldrig blir tombstones utan tas bort med kunden ([§3.6]).

## 3.5 Tillämpning

**UPDATE för `ERRAND`, `ASSET` och `CONTRACT`**

1. För varje kundpart ([§5.3]):
   - Skapa kunden om den saknas (`CUSTOMER_CREATED`). En ny privatkund får folkbokföringens skyddsstatus från steg 5 i
     [§3.3].
2. Gör upsert av engagemangsraden per kund, nycklad på `(municipality_id, object_type, source, object_id, customer_id)`:
   - envelope-fälten, roller, namespace och `party_protection` (partens `protection`)
   - `ENGAGEMENT_CREATED` om raden är ny, annars `ENGAGEMENT_UPDATED`
3. Räkna om kundens skyddsnivå ([§5.4]). Om kunden är oskyddad uppdateras namn och adresser enligt "nyast vinner"
   ([§5.2]). Radens kontaktuppgifter ersätts ([§5.3]), men för en skyddad kund sparas ingen rad med kundens egna
   kontaktuppgifter.
4. Skriv meddelandets `activities` som historik på raden, men bara om raden är ny eller om `v > s`.
5. Radera engagemangsrader för kunder som inte längre är parter (`ENGAGEMENT_REMOVED`), och räkna om skyddsnivån för
   dem som har engagemang kvar.
6. Sätt `source_object.version = v` och `deleted = false`.
7. Radera de kunder från steg 5 som ska raderas automatiskt ([§3.6]).

En kund som förekommer flera gånger i `parties` blir en enda rad, och rollerna slås ihop.

**DELETE för `ERRAND`, `ASSET` och `CONTRACT`**

Radera objektets alla engagemangsrader (`ENGAGEMENT_REMOVED` för varje kund), räkna om skyddsnivån för kunder som har
engagemang kvar och markera `source_object` som raderat med version v. Radera sist de kunder som ska raderas
automatiskt ([§3.6]).

**UPDATE för `CUSTOMER`**

- Parten är alltid kunden och klassas inte på roller som i [§5.3]. Den behöver partyId eller ett legalId som går att
  mappa, annars ignoreras meddelandet.
- Skapa kunden om den saknas.
- Sätt källans rad i `asserted_protection` till partens `protection`, eller ta bort raden om `protection` saknas, och
  räkna om skyddsnivån ([§5.4]). En annan källas skydd påverkas inte.
- Om kunden är oskyddad:
  - Uppdatera namn och adresser enligt "nyast vinner" ([§5.2]).
  - Ersätt kundens kontaktuppgifter från den här källan (rader med `engagement_id is null` och samma `source`). De får
    meddelandets namespace, och saknas det blir de globala.
- Logga `CUSTOMER_UPDATED`.

Versionsvakten nycklas på kundens partyId, efter en eventuell mappning från legalId.

**DELETE för `CUSTOMER`**

Radera kunden med allt den äger ([§3.6]), om den finns, och logga `CUSTOMER_DELETED`. Det gäller i varje cell i
versionsmatrisen som inte är inaktuell ([§3.4]). Alla rader i `source_object` för `CUSTOMER` med partyId:t raderas, även
när kunden saknas, så ingen tombstone med partyId sparas. Personens kontaktrader hos företag raderas också ([§5.3]).

## 3.6 Kundradering

En kund raderas i följande fall:

|                                        Orsak                                        | Kanal i loggen |          Objekttyp i loggen          |
|-------------------------------------------------------------------------------------|----------------|--------------------------------------|
| Ett `CUSTOMER DELETE`-meddelande                                                    | `MESSAGE`      | `CUSTOMER`                           |
| `DELETE /{municipalityId}/customers/{partyId}`                                      | `REST`         | —                                    |
| Sista engagemanget är borta, och kunden har varken noteringar eller `CUSTOMER`-data | `MESSAGE`      | det objekt som tog bort engagemanget |
| Sista noteringen raderas, och kunden har varken engagemang eller `CUSTOMER`-data    | `REST`         | —                                    |

De två sista är automatisk radering. `CUSTOMER`-data betyder att kunden har en rad i `source_object` för `CUSTOMER`,
alltså att någon källa har skickat ett `CUSTOMER UPDATE` för den. Raden bär källans kontaktuppgifter och skydd
([§3.5]), och de ska inte försvinna för att kundens sista engagemang gör det.

- Raderingen görs som en bulk-delete (`@Modifying(flushAutomatically = true, clearAutomatically = true)`).
  Databasens kaskad tar resten. Hibernate får alltså inte ha barnrader laddade som det sedan försöker skriva tillbaka.
- Raderingen görs sist i transaktionen, efter `source_object` och all omräkning. `clearAutomatically` kopplar loss alla
  laddade entiteter, så en ändring som görs på en entitet efter raderingen skrivs aldrig.
- Den tekniska loggraden `CUSTOMER_DELETED` skrivs i samma transaktion som raderingen.
- Vid ett `CUSTOMER DELETE` och en radering via REST raderas också personens kontaktrader hos företag i kommunen
  (`contact.party_id`, [§5.3]). Vid en automatisk radering ligger de kvar, eftersom källorna fortfarande anger personen
  som kontaktperson.
- Med kunden raderas också raderna i `source_object` för `CUSTOMER` med kundens partyId, eftersom de innehåller
  partyId. Ett senare, äldre `CUSTOMER UPDATE` kan då skapa kunden igen.
- Ett senare meddelande som nämner kunden skapar den på nytt. Det är avsiktligt, eftersom källsystemet är master.
- En kund som bara har `CUSTOMER`-data raderas aldrig automatiskt, bara av ett `CUSTOMER DELETE` eller via REST.
- En radering via REST låser först kundens rader i `source_object` för `CUSTOMER` och sedan kunden, i samma ordning som
  meddelandeflödet ([§3.3]).
- När en notering skapas låses kunden först med IODKU och `FOR UPDATE`. När en notering ändras eller raderas låses
  kunden bara med `FOR UPDATE`, och saknas kunden blir svaret 404, så att en raderad kund inte skapas på nytt.
  Transaktionen körs med READ COMMITTED, som i [§3.3]. Annars kan ett meddelande hinna ge kunden ett engagemang som
  kontrollen inte ser, eller radera kunden medan en ny notering väntar på sin FK-kontroll.
- REST-anropen som skriver och den nattliga kontrollens batchar görs om, högst tre gånger, vid deadlock eller lock
  timeout (`PessimisticLockingFailureException`). Omförsöket görs utanför transaktionen, så att hela transaktionen körs
  om. Meddelanden görs om av startern ([§3.7]).

## 3.7 Felhantering

Fel klassas av dept44-starter-rabbitmq (`TransientFailures`):

|                                                                               Fel                                                                                |                                                                                               Följd                                                                                               |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Valideringsfel (`ConstraintViolationException`), okänd objekttyp eller kanaltyp, saknad version                                                                  | Dead letter direkt                                                                                                                                                                                |
| Ett legalId finns inte hos Citizen eller LegalEntity, eller har fel format och skickas därför aldrig ([§7.1])                                                    | Parten hoppas över och resten av meddelandet tillämpas, även raderingen av engagemang för kunder som inte längre är parter ([beslut 16]). WARN loggas med källa och `objectId`, men utan legalId. |
| 5xx från Citizen eller LegalEntity när partyId slås upp (`ServerProblem`)                                                                                        | Omförsök i cirka 8 minuter, sedan dead letter. Utan partyId kan parten inte tillämpas.                                                                                                            |
| 429 eller 408, eller öppen circuit breaker, när partyId slås upp                                                                                                 | Avbrott: omförsök, sedan tillbaka till kön. Integrationen gör om svaret till ett avbrott, eftersom startern annars dead-letterar en 4xx direkt.                                                   |
| Citizen svarar inte, svarar 5xx, 429 eller 408, eller har öppen circuit breaker när skyddsstatusen hämtas                                                        | Inget fel. Nya kunder får skyddsstatusen `UNKNOWN`, och meddelandet tillämpas ([§5.4]).                                                                                                           |
| LegalEntity eller Citizen svarar inte, svarar 5xx, 429 eller 408, eller har öppen circuit breaker när en ny företagskunds organisationsnummer eller ägare hämtas | Inget fel. Kunden får `UNKNOWN` utan `registry_party_id`, och den nattliga kontrollen gör om uppslaget ([§5.4]).                                                                                  |
| Citizen svarar 401                                                                                                                                               | Dead letter direkt (`ClientProblem`), så att larmet går. Behörigheten i Metakatalogen saknas ([§7.2]).                                                                                            |
| Databasen svarar inte                                                                                                                                            | Avbrott: omförsök, sedan tillbaka till kön, räknas mot `x-delivery-limit`                                                                                                                         |
| Deadlock eller lock timeout (`TransientDataAccessException`)                                                                                                     | Omförsök                                                                                                                                                                                          |
| `DataIntegrityViolationException`                                                                                                                                | Dead letter direkt. Ska inte kunna inträffa tack vare IODKU, så det är en bugg om det händer.                                                                                                     |

Ett meddelande som misslyckas rullas tillbaka helt och syns inte i den tekniska loggen. Det syns i starterns WARN-rad
och i dead letter-kön.

Omförsöken görs i lyssnarens tråd. Varje kö har en egen listener-container med en egen konsument ([§9]), så under ett
omförsök står bara den källans kö still.

[§3.3]: #33-flödet-genom-tjänsten
[§3.4]: #34-versionsmatrisen
[§3.5]: #35-tillämpning
[§3.6]: #36-kundradering
[§4.3]: 04-datamodell.md#43-tabellerna
[§5.2]: 05-synlighet-och-kunddata.md#52-namn-och-adress
[§5.3]: 05-synlighet-och-kunddata.md#53-kunder-kontaktpersoner-och-kontaktuppgifter
[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet
[§11.1]: 11-publicerare.md#111-gemensamt-för-alla-publicerare
[§12]: 12-drift.md
[§12.1]: 12-drift.md#121-larm-för-dead-letter-köer
[§7.2]: 07-integrationer.md#72-citizen-och-legalentity
[§9]: 09-kodstruktur.md
[§7.1]: 07-integrationer.md#71-uppslag-av-partyid
[beslut 16]: 00-beslutslogg.md#beslut-16-personnummer-och-organisationsnummer-mappas-till-partyid
[beslut 54]: 00-beslutslogg.md#beslut-54-enskilda-firmor-kontrolleras-mot-folkbokföringen
[§3.7]: #37-felhantering

