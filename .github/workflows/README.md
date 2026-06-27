# GitHub Actions

`release.yml` builds the plugin and publishes a GitHub Release on every push to `main`.

The workflow does not run for pull request updates, so package PRs can be reviewed and merged without publishing temporary builds.
