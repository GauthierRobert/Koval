---
name: openapi-contract
description: Use when editing Spring Boot controllers or files under `openapi/`. Keeps the per-domain OpenAPI specs (auth, ai, clubs, coach, goals, groups, notifications, pacing, races, schedule, sessions, strava, trainings, zones) and the actual controller endpoints in sync — schema drift breaks the frontend's typed client generation.
---

# OpenAPI Contract Discipline

## Overview

`openapi/` holds 14 hand-maintained specs, one per product domain. They are the **published contract** between backend and frontend (and any third-party MCP/OpenAPI client). When a controller and its spec diverge, the frontend's generated types lie.

| Domain | Spec file | Owning backend package |
|---|---|---|
| Auth | `auth.yaml` | `auth/` |
| AI chat | `ai.yaml` | `ai/` (disabled — see `CLAUDE.md`) |
| Clubs | `clubs.yaml` | `club/` |
| Coach | `coach.yaml` | `coach/` |
| Goals | `goals.yaml` | `race/goal/` |
| Groups | `groups.yaml` | `training/group/` |
| Notifications | `notifications.yaml` | `notification/` |
| Pacing | `pacing.yaml` | `pacing/` |
| Races | `races.yaml` | `race/` |
| Schedule | `schedule.yaml` | `coach/` (ScheduleService) |
| Sessions | `sessions.yaml` | `training/history/` |
| Strava | `strava.yaml` | `auth/strava/` |
| Trainings | `trainings.yaml` | `training/` |
| Zones | `zones.yaml` | `training/zone/` |

## When the contract changes

A change to **any** of these counts as a contract change:
- New endpoint (`@GetMapping` / `@PostMapping` / etc.)
- Removed endpoint
- Changed path, HTTP method, or response status code
- Changed request body field (added, removed, renamed, retyped)
- Changed response body field (same)
- Changed query/path parameter
- New or changed error response schema

### Required steps

1. **Identify the spec** that owns the controller (table above).
2. **Update the spec** in the same change — `paths`, `components/schemas`, and any examples.
3. **Diff-check field names**: backend DTOs are Java records (`camelCase`); the spec must mirror them exactly. Frontend will trip over `start_time` vs `startTime`.
4. **Keep nullability honest**: if the controller returns `Optional<T>` mapped to a nullable field, mark `nullable: true` in the spec. If the field is always populated, mark `required`.
5. **Status codes** in `@PreAuthorize`-gated endpoints must list `403` in the spec.
6. **Polymorphic types** (`Training` subtypes, `WorkoutElement` block types): the spec must use `oneOf` + a `discriminator` matching Jackson's `@JsonTypeInfo` (`property: sport` for `Training`, `property: type` for `WorkoutElement`).

## Schema conventions

- **Records → schemas**: one record = one schema; reuse via `$ref` across specs only if the type is genuinely shared (rare — prefer per-domain copies to keep specs self-contained).
- **Enums**: Java enum → `type: string` + `enum: [VALUE_A, VALUE_B]` (uppercase, matching Java).
- **Durations**: seconds, `type: integer`, `format: int32`, `minimum: 0`. Always document the unit in `description`.
- **IDs**: `type: string` (Mongo ObjectId hex), `pattern: '^[a-f0-9]{24}$'` when worth enforcing.
- **Timestamps**: `type: string`, `format: date-time` (ISO 8601). Stored as `Instant` in Mongo.
- **Power targets**: `% of FTP`, `type: number`, `format: double`, `minimum: 0`. Document the unit explicitly.

## Don't

- Don't add a controller endpoint and leave the spec untouched
- Don't rename a DTO field "just in the spec" to make it look nicer — the field is the contract; rename both
- Don't drop required fields from a response — that's a breaking change for the frontend's typed client
- Don't reference a schema that doesn't exist (`$ref: '#/components/schemas/Foo'` with no `Foo`) — YAML parsers won't catch it
- Don't put example values that contradict the schema (e.g. enum example outside the enum)
- Don't add MCP-only tools to OpenAPI specs — MCP tools are registered separately; the specs are for the REST API only

## Verifying

If a tool like `openapi-cli` or `spectral` is wired up, run it. Otherwise, at minimum:

```bash
# YAML syntax sanity
python3 -c "import yaml; yaml.safe_load(open('openapi/trainings.yaml'))"
```

And spot-check the affected endpoint by reading both the spec stanza and the controller side-by-side.
