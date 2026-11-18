# buildSrc

The build's own Gradle tasks.

- `GenerateIcons`: Squash's icons from `design/icons/*.svg`, for `ui-design`.
- `PackTemplates`: bundles `templates/` into `character`'s resources.
- `CheckTestSources`: part of `check`; fails on timed waits and reflection in test sources.
