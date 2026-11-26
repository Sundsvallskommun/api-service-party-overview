# 1. Översikt

Del av [arkitekturen för party-overview](README.md).

```mermaid
flowchart LR
    SM[SupportManagement] -->|outbox + relay| EXSM((api-fabriken.<br/>support-management))
    PA[party-assets] -->|outbox + relay| EXPA((api-fabriken.<br/>party-assets))
    CT[contract] -->|outbox + relay| EXCT((api-fabriken.<br/>contract))
    CD[Casedata] -->|outbox + relay| EXCD((api-fabriken.<br/>case-data))
    EXSM --> QSM[[api-fabriken.party-overview.<br/>support-management]]
    EXPA --> QPA[[api-fabriken.party-overview.<br/>party-assets]]
    EXCT --> QCT[[api-fabriken.party-overview.<br/>contract]]
    EXCD --> QCD[[api-fabriken.party-overview.<br/>case-data]]
    QSM --> PO[party-overview]
    QPA --> PO
    QCT --> PO
    QCD --> PO
    QREG[[ändringskö för företag och personer]] -->|företag och personer som ändrats| PO
    PO --> DB[(MariaDB)]
    PO -->|sökindex via kö + relay| OS[(OpenSearch)]
    PO -->|personnummer → partyId; skyddsstatus; partytyp, namn/adress när REST skapar kunden| CIT[Citizen]
    PO -->|organisationsnummer → partyId; företagsprofil; partytyp, namn/adress när REST skapar kunden| LE[LegalEntity]
    FE[Kundvy, frontend] -->|REST| PO
    FE -->|detaljer om ett objekt| SM
    FE -->|detaljer om ett objekt| PA
    FE -->|detaljer om ett objekt| CT
    FE -->|detaljer om ett objekt| CD
```

|      Del       |                                                                                                              Ansvar                                                                                                               |
|----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Publicerare    | Äger sina objekt. Varje ändring publiceras som hela objektets tillstånd (`UPDATE`) eller som `DELETE`. Kan publicera om allt (grundladdning).                                                                                     |
| RabbitMQ       | En exchange per publicerare och en kö per källa för party-overview, och en kö för ändringar av företag och personer. Varje kö har en dead letter-kö. Topologin deklareras i varje tjänsts driftkonfiguration (spring-boot-chart). |
| party-overview | Gör upsert av kunder och engagemang, för historik och teknisk logg, äger noteringarna och exponerar kundvyn via REST.                                                                                                             |
| OpenSearch     | Sökindex för fritextsökning bland kunderna. MariaDB är master, och indexet kan alltid byggas om därifrån.                                                                                                                         |
| Frontend       | Bygger kundvyn från party-overview och hämtar detaljer om ett objekt från källsystemet med `source` och `objectId`.                                                                                                               |

