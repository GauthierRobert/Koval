---
name: native-image-safety
description: Use when moving, renaming, or deleting Java classes in the backend, or when editing files under `backend/src/main/resources/META-INF/native-image/`. GraalVM native image builds break silently when `reflect-config.json` has stale fully-qualified class names; this skill enforces the audit step that catches it before the Cloud Run deploy fails.
---

# Native Image Safety

## Why this skill exists

The backend is deployed as a **GraalVM native image** (`mvn -Pnative native:compile`, see `backend/Dockerfile`). Native builds need a hand-maintained `reflect-config.json` so Spring AI tool adapters, MongoDB documents, and Jackson-deserialized polymorphic types survive AOT compilation.

When you **move a class between packages or rename it**, the reflection config still references the old fully-qualified name. The JVM build passes. The native build also "succeeds" — but the class is missing at runtime, and the failure mode is a `ClassNotFoundException` deep inside Spring AI tool resolution that only surfaces on Cloud Run.

The settings file in this repo records past evidence of this exact problem (a long `sed` invocation rewriting FQNs in `reflect-config.json` after the `ai/tools/` refactor).

## File location

```
backend/src/main/resources/META-INF/native-image/com.example/training-planner-backend/reflect-config.json
```

(Path may vary slightly per GraalVM plugin version — check `pom.xml` for the active group/artifact pair.)

## Required workflow

### Before moving/renaming/deleting a Java class

1. Note the **old FQN** (`com.koval.trainingplannerbackend.foo.OldName`).

### After moving/renaming/deleting

2. Grep `reflect-config.json` for the old FQN:
   ```bash
   grep -n "com\.koval\.trainingplannerbackend\.foo\.OldName" backend/src/main/resources/META-INF/native-image/**/reflect-config.json
   ```
3. If matches exist:
   - **Renamed/moved**: update the `"name"` field to the new FQN
   - **Deleted**: remove the entire JSON object
4. Verify the JSON still parses:
   ```bash
   python3 -m json.tool backend/src/main/resources/META-INF/native-image/com.example/training-planner-backend/reflect-config.json > /dev/null
   ```

### When adding a class that needs reflection

Add an entry when the class is any of:

- A Spring AI `@Tool` adapter (under `ai/tools/**` or `mcp/`)
- A Mongo `@Document`
- A Jackson `@JsonTypeInfo`-discriminated subtype (e.g. a new `Training` subclass, a new `BlockType`-bound element)
- A record/class used as a Spring AI tool's request/response DTO
- A class instantiated by name (e.g. via `Class.forName(...)`)

Entry shape:

```json
{
  "name": "com.koval.trainingplannerbackend.foo.NewClass",
  "allDeclaredFields": true,
  "allDeclaredMethods": true,
  "allDeclaredConstructors": true
}
```

Match the granularity of nearby entries for the same domain — don't invent a new shape.

### When unsure whether a class needs reflection

Check whether **the JVM build calls it via reflection** at any point. Quick heuristics:

- `@Tool` / `@McpTool` adapters → **yes**
- `@Document` Mongo entities → **yes** (Spring Data uses reflection)
- Plain DTOs only used in compiled call paths (no Jackson polymorphism) → **no**
- Internal services wired by Spring (`@Service`, `@Component`) → **no**, AOT handles them

When in doubt, add the entry — false positives are cheap, false negatives cause production crashes.

## Verification before merge

If the change touches any class under `ai/tools/`, `mcp/`, `training/` (polymorphic `Training`), or adds a new `@Document`, run:

```bash
cd backend && mvn -Pnative native:compile
```

This is slow (5–10 min). If the change is small/contained, at minimum run:

```bash
cd backend && mvn -q -DskipTests compile
```

…and confirm `reflect-config.json` has no FQNs that no longer exist by spot-checking 3–5 entries.

## Anti-patterns

| Don't | Do Instead |
|---|---|
| Move a Java class and assume Spring will find it | Audit `reflect-config.json` for the old FQN |
| Bulk `sed` rewrite without parsing | Edit JSON object-by-object, then `python3 -m json.tool` to validate |
| Add reflection entries "just in case" for every class | Add only for the categories listed above |
| Skip the native build "because the JVM build works" | Native is what runs in prod — verify before deploy |
| Trust IDE refactor tools to update `reflect-config.json` | They don't — string-match-only file, audit manually |
