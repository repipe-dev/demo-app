# demo-app

A small, deliberately boring Python web service — the kind of app every
company has ten of. It exists to be a migration showcase: the same app,
built and shipped two ways, side by side.

- `.teamcity/` — the **TeamCity** pipeline (Kotlin DSL), as this app
  would have shipped from a TeamCity estate: self-hosted agents,
  TeamCity-hosted secrets, snapshot + artifact dependencies.
- `.github/workflows/` — the **GitHub Actions** equivalent after the
  repipe: hosted + self-hosted runner strategy, OIDC to AWS (no static
  keys), environments with approvals, container pushed to ECR.
- `MIGRATION.md` — every cutover decision, mapped line by line.

Pipeline parity is verified: both pipelines run the same test suite,
produce the same container image, and deploy it the same way.

## Layout

```
src/            the app (FastAPI, one endpoint, one dependency)
tests/          pytest suite
deploy/         Kubernetes manifests (the deploy target)
.teamcity/      TeamCity Kotlin DSL — before
.github/        GitHub Actions — after
MIGRATION.md    the decisions, in order
```

## The app

```
curl http://localhost:8000/health      # {"status":"ok","version":"…"}
```

Everything else in this repo is infrastructure. The app is not the point;
the pipes are.
