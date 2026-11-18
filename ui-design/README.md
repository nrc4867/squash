# ui-design

Presentation layer. What Squash's screens draw with that Stretch does not provide.

- Squash's own icons for its tools, drawers, part types and parts, generated from `design/icons/*.svg` by `generateIcons`. `verifyIcons`, part of `check`, fails when they are stale.
- Every user-facing string, as keys in `composeResources/values`.
- Squash-only components, drawn in Stretch's tokens.

**Uses:** `stretch-theme`, Compose resources.
**Used by:** `screens-core`.
