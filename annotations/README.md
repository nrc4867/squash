# annotations

Core layer. The two annotations the serializable model uses to describe itself: `@Range` (a parameter's allowed bounds) and `@Doc` (a one-line description). `schema-gen` reads them when it writes the JSON Schemas and the recipe reference.

- **Uses:** `kotlinx-serialization-core` only.
- **Used by:** `character`, `recipe`, `schema-gen`. Nothing else may depend on it.
