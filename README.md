# PartyOverview

_Collects what the source systems report about a party (a person or an organization) and serves it as one overview._

The service is not the master of any data. Source systems publish events on RabbitMQ about their own objects, in their
own model, and this service translates and stores what arrives. It never calls a source system.

So far it handles assets from party-assets.

## How it works

1. A source system publishes an event on its own exchange, e.g. `api-fabriken.party-assets`.
2. The service consumes it from a queue of its own for that source, e.g. `api-fabriken.party-overview.party-assets`.
3. The source's translator turns the event into an `ObjectChange`, the service's own model. From there on every source
   is handled alike.
4. Each version of an object is kept once in the `event` table, as the history of the object.
5. The newest version of each object is kept in a table of its own (`asset` so far), linked to its parties.
6. `GET /{municipalityId}/parties/{partyId}/overview` reads the newest versions.

## Sources

Each source has a package of its own under `integration/source`, with a listener and a translator. The translator also
holds what the source does not know itself, such as which committee (nämnd) a namespace belongs to or which role means
applicant. The rest of the service knows nothing about the sources.

The service keeps a copy of each source's event format in `src/main/resources/integrations` and generates its models
from it, as for any other integration. The copy holds the fields the service reads. Other fields are ignored.

|    Source    |          Exchange           |              Event format               |                Skipped                 |
|--------------|-----------------------------|-----------------------------------------|----------------------------------------|
| party-assets | `api-fabriken.party-assets` | `integrations/party-assets-events.yaml` | Drafts, like party-assets' own listing |

A source publishes in its own model, but the service relies on a few things from every source:

- An event carries the whole object, not the change. The service has nowhere to fetch the rest.
- An object and a version identify a change. The version grows with every change of the object, including changes that
  are not shown. Events may arrive out of order: an older version is kept as history but does not replace a newer one.
- The same version received again replaces what is stored, both the object and its history. A redelivery is therefore
  harmless, and the AMQP message id is kept for tracing only. It must be a UUID.
- A deletion is published, and it is final: the source never reuses the id of the object. The service removes the
  history of the object, keeps the object as a tombstone without any personal data, and ignores every later event for
  it.
- An object concerns at least one party, given by partyId, since the service makes no lookups of its own.
- The source can publish everything it has again on request. That is how the service is loaded the first time, and how
  it recovers if a translator has had a bug. Publishing again corrects the newest version of each object, in the
  overview and in the history. Older versions in the history stay as they were translated, unless the source can
  publish those versions again too.

An event that breaks these rules is dead-lettered, so the dead letter queue of each source needs an alert. A deletion
that ends up there leaves personal data behind until someone moves it back.

## Getting started

### Prerequisites

- Java 25
- Maven
- MariaDB
- RabbitMQ

### Run

```bash
mvn spring-boot:run
```

`mvn verify` needs a `.git` directory with at least one commit, for the git-commit-id plugin. In a fresh clone without
commits, add `-Dmaven.gitcommitid.skip=true`.

## Configuration

|                    Property                     |                   Description                   |
|-------------------------------------------------|-------------------------------------------------|
| `dept44.rabbitmq.enabled`                       | `true` starts the listeners. Off by default     |
| `integration.party-assets.queue`                | The queue for party-assets' events              |
| `spring.rabbitmq.host`, `port`, `virtual-host`  | The broker                                      |
| `spring.rabbitmq.username`, `password`          | The service's AMQP user, from rabbitmq-topology |
| `spring.datasource.url`, `username`, `password` | The database                                    |
| `spring.flyway.enabled`                         | `true` to run the migrations at startup         |

The service never declares exchanges, queues or bindings. They are declared in
[rabbitmq-topology](https://github.com/Sundsvallskommun/rabbitmq-topology), and the AMQP user has no configure
permission.

Each source's queue is bound to the source's exchange, has a dead letter queue of its own, and must be declared with
`x-single-active-consumer: true`. The argument cannot be added to a quorum queue afterwards, so it has to be there when
the queue is first created. Single active consumer keeps two replicas from handling the same object at once. During
the short overlap around a redelivery it still can happen, and then the consumer that commits last collides on the
object's lock version or unique key. Its listener requeues the message, and handling it again reads what the other
consumer committed.

### RabbitMQ

The listeners run on `dept44-starter-rabbitmq`. It reads each message as the type of the listener's parameter, retries
a failure that may pass, such as a database that does not answer, and dead-letters the rest. Its README has the details.
With `dept44.rabbitmq.enabled` off, the default, the service does not connect to the broker and the events wait in the
queues.

## API documentation

The OpenAPI specification is served at `/api-docs` and kept in
[src/integration-test/resources/api/openapi.yaml](src/integration-test/resources/api/openapi.yaml).

## License

This project is licensed under the [MIT License](LICENSE).

Copyright (c) 2026 Sundsvalls kommun
