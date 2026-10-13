# Changesets

This folder is used by [changesets](https://github.com/changesets/changesets) to
track semver-relevant changes to `vue-agent-start`.

Workflow:

1. After a user-visible change, run `pnpm changeset` and describe the change
   (patch / minor / major).
2. Commit the generated `.changeset/*.md` file with the code change.
3. `pnpm version-packages` bumps `package.json` and folds the entries into
   `CHANGELOG.md`.
4. `pnpm release` typechecks, tests, builds and publishes.
