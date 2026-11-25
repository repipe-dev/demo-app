# TeamCity → GitHub Actions: the migration ledger

The pipeline in `.teamcity/` is the before; `.github/workflows/` is the
after. This document is the part the importers don't do — every decision
between them, in the order we make it on a real engagement.

GitHub's Actions Importer can convert most step bodies mechanically.
What it cannot decide — and what actually breaks productions — is listed
below. This is the deliverable a repipe client is paying for.

## 1. Inventory first (the step everyone skips)

| TeamCity object | in this repo | GitHub Actions equivalent |
|---|---|---|
| Build configuration `Build and Test` | `.teamcity/pipelines/demo-app.kts` | job `build-and-test` |
| Build configuration `Deploy → staging` | same file | job `deploy-staging` |
| Snapshot dependency | `dependencies { snapshot {} }` | `needs:` chain |
| Artifact dependency (junit.xml) | `artifactRules` | `actions/upload-artifact` |
| Parameters (`env.*`) | `params` block | `vars:` + environment secrets / OIDC |
| VCS trigger | `triggers { vcs {} }` | `on: push` / `on: pull_request` |
| Agent requirement: docker-capable pool | implicit | `runs-on: [self-hosted, docker]` |

Rule: the inventory table is written before a single workflow file, and
it's the rollback plan's table of contents.

## 2. Runners: the cost and capability decision

TeamCity's shared agent pool became two different things:

- **build-and-test** → self-hosted runner, labeled `[self-hosted, docker]`.
  Same machine class the TeamCity agents used (existing hardware, no
  cold-start tax, pip cache stays warm).
- **container build/deploy** → hosted `ubuntu-latest`, because GitHub's
  hosted runners are cheap for bursty docker builds and eliminate the
  "who patches the docker daemon on agent-3" question entirely.

The general rule from real cutovers: **classify steps by statefulness,
not by volume.** Test suites with warm caches love self-hosted; bursty
image builds love hosted. Estates that put everything on self-hosted
usually did it for one legacy constraint nobody remembers.

## 3. Secrets: static keys → OIDC (the change that matters most)

TeamCity's model — long-lived AWS keys in TC parameters, possibly
nightly-synced from Secrets Manager — is the shape auditors flag and
leaks kill. The migration:

1. Create two IAM roles with a trust policy scoped to the repo and
   (for deploy) to the `staging` environment:
   `gha-demo-app-ci` (ECR push), `gha-demo-app-deploy` (EKS set-image).
2. `aws-actions/configure-aws-credentials@v4` with `id-token: write`
   assumes the role per job; credentials live for the job's lifetime.
3. Every static key the old pipeline used is revoked at cutover +1 week
   (the week is the rollback safety margin).

This is the step where "pipeline works" and "pipeline is done" diverge.
A converted pipeline with pasted keys "works".

## 4. Artifact chains

TeamCity snapshot + artifact dependencies became `needs:` + upload/
download-artifact. Two differences worth knowing before you promise a
timeline:

- Artifacts are immutable per run — an advantage over the TC pattern of
  rebuilding artifacts into the same configuration.
- `needs:` is parallel-by-default, which reorders some TC chains that
  were accidentally serial. Watch the first staging deploy closely.

## 5. Image tags: build.number → run_number + sha

Old: `%build.number%`. New: `${{ github.run_number }}` **plus**
`sha-<commit>` — the immutable tag ArgoCD should reference. Run numbers
are not identifiers; they reset on workflow changes. Every deploy
manifest change should reference a sha tag, never a floating one.

## 6. Deploys and gates

The old manual-gated deploy (a button in TC) became a GitHub
**environment** with required reviewers. Same pause, but the approval
and its context live in the PR where the code was written.

## 7. The cutover itself

| Phase | TeamCity | GitHub Actions |
|---|---|---|
| 1 — shadow | active, all builds | running on PRs only |
| 2 — split | tags only | branches + PRs |
| 3 — cutover | disabled, read-only | all triggers |
| 4 — decommission | agents drained, then powered off | — |

Phase 1 runs for two weeks minimum. The kill switch is "disable the TC
trigger", reversible in seconds. No big-bang migration, ever.

## 8. What does NOT migrate (and clients find out late)

- Build history/statistics: export as CSV before decommission, or accept
  the loss. Nothing imports cleanly.
- Build queues/agent clouds: replaced by runner groups + autoscaling.
- Per-TC-user notifications: each user re-subscribes to GH notifications.
- The TC license line itself: retire it in the same fiscal quarter as
  the migration or the savings won't materialize.

---

*This repo is a self-contained demonstration. On a real engagement the
inventory table in §1 is generated from your TeamCity server, and every
section above cites your estate's specific names, ARNs and thresholds.*
