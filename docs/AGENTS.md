# AGENTS.md: rules for `docs/`

## Structure

- The architecture documentation in `arkitektur/` is written in Swedish, in short, plain sentences.
- `arkitektur/README.md` is the index, with one line per file. A new file gets a line there.
- There is one file per top-level section, named `NN-short-name.md`. Each file starts with `# N. Title`, followed by the
  line `Del av [arkitekturen för party-overview](README.md).`
- Section numbers (§N.M), decision numbers and task ids are stable, since other files and Jira refer to them. Never
  renumber. New sections, decisions and tasks go at the end.

## References

- Refer to sections and decisions with reference-style links: write `[§5.4]` or `[beslut 12]` in the text, and put the
  definition at the end of the file, e.g. `[§5.4]: 05-synlighet-och-kunddata.md#54-skyddad-identitet`. A reference to
  the same file has only the anchor.
- Anchors are GitHub's heading slugs: lower case, punctuation removed, spaces replaced by hyphens, å, ä and ö kept.
- A heading that others link to keeps its text. If it must change, update every definition that points to it.

## Decisions and tasks

- Decisions are in `arkitektur/00-beslutslogg.md`, one heading each: `## Beslut N: Title`, then the decision, then
  `**Valdes bort:**` and `**Skäl:**`. When a decision changes, update its entry. The rejected alternatives and the
  reasons belong there and nowhere else.
- Tasks are in `arkitektur/14-uppgifter.md`: a row in the table (step, id, title, dependencies) and a section
  `## Id — Title` with `**Bygg:**` and `**Acceptans:**`. The title is the same in the table and in the heading. Every
  acceptance criterion must be possible to verify, and a task depends on every task whose work it uses.

## Writing

- Describe what the system does, not how the text came about.
- Keep prose lines at most 120 characters. Run `mvn dept44-formatting:apply` after editing. It aligns the tables.
- Before finishing, check that every reference has a definition, and that every link and anchor leads to a file and a
  heading that exist.

