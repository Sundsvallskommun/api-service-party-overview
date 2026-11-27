# AGENTS.md

party-overview is a Spring Boot service built on Sundsvall municipality's dept44 framework. It keeps a lightweight view
of the municipality's customers and their engagements: errands, assets, contracts and notes. Data comes in over RabbitMQ
and is read over REST.

## Documentation

The design is in `docs/arkitektur/`, written in Swedish. All development in this repository, and the changes in the
publishing services, starts from it.

- Start with `docs/arkitektur/README.md`. It is the index, with one line per file.
- Read only the files the task needs. A reference such as §5.4 points to section 5.4 in the file whose name starts with
  `05-`, and "beslut 12" points to decision 12 in `docs/arkitektur/00-beslutslogg.md`.
- The tasks, with their acceptance criteria, are in `docs/arkitektur/14-uppgifter.md`.
- The code on `main` is a proof of concept that task C1 replaces. Do not build on it.
- When a change alters something the documentation describes, update the documentation in the same change. The rules
  for that are in `docs/AGENTS.md`.

## Build

- `mvn clean verify` builds the service and runs all tests, including the integration tests with Testcontainers.
- Run `mvn dept44-formatting:apply` before committing. It formats Java, SQL, Markdown and `pom.xml`.

