# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

SubLearn is a very early-stage scaffold. There is no application logic yet:

- `BE/SubLearn/` — a Spring Boot 4.1.0 (Java 17) project generated from Spring Initializr. It currently contains only the default `SubLearnApplication` main class and one placeholder test (`SubLearnApplicationTests`). No controllers, services, entities, or persistence layer exist yet.
- `FE/` — empty directory. No frontend project has been initialized here yet (no `package.json`).
- `_specs/` — feature spec documents live here (see Spec workflow below). Currently only contains `template.md`.

When adding the first real code to either side, follow whatever conventions are established by that point; until then there is no existing architecture to conform to beyond standard Spring Boot / Maven layout.

## Backend (BE/SubLearn)

Build tool: Maven, via the wrapper. Run all commands from `BE/SubLearn/`.

```
./mvnw.cmd spring-boot:run       # run the app (Windows)
./mvnw.cmd test                  # run all tests
./mvnw.cmd test -Dtest=ClassName # run a single test class
./mvnw.cmd package               # build a jar
```

Package root: `org.example.sublearn`. Java version: 17.

## Spec workflow

This repo uses a `/spec` slash command (`.claude/commands/spec.md`) to turn a short feature idea into a spec file plus a dedicated branch:

- Creates a git branch named `claude/feature/<feature-slug>`.
- Writes a spec markdown file to `_specs/<feature-slug>.md` following the structure in `_specs/template.md` (Summary, Functional Requirements, Figma Design Reference, Edge Cases, Acceptance Criteria, Open Questions, Testing Guidelines).
- Refuses to run if the working directory has uncommitted/unstaged/untracked changes — commit or stash first.
- If the feature references a Figma component (`figma: <link>` in the input), it delegates to the `figma-design-extractor` subagent to pull design tokens before drafting the spec.

Note: the `figma-design-extractor` agent definition (`.claude/agents/figma-design-extractor.md`) references a different project's stack (Next.js/React/Tailwind, "Pocket Heist") — it's a carried-over template and does not reflect this repo's actual (Spring Boot backend, not-yet-started frontend) stack. Treat its stack-specific implementation guidance as inapplicable here.

## Checking Documentation
 
**Important**: When implementing any lib/framework-specific features, ALWAYS check the appropriate documentation using the Context7 MCP server before writing any code.