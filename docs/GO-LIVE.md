# Going live

This takes Flakewatch from "code in the repository" to a public link with real data. It uses free
tiers only and takes about an hour the first time. Do the steps in order.

| Step | What | Time |
|---|---|---|
| 1 | Get CI green on the branch | depends |
| 2 | Create the database (Neon) | 10 min |
| 3 | Create the web service (Render) | 15 min |
| 4 | Connect GitHub to Render | 5 min |
| 5 | Create the demo project and its token | 5 min |
| 6 | Start the demo repository | 15 min |
| 7 | Check it works | 5 min |

---

## 1. Get CI green

Push the branch, open a pull request, and wait for every job to pass: `docs`, `java`, `web`,
`action` and `image`.

The server code and its integration tests run for the first time in CI, so expect a few small
fixes. The most likely ones are:

- **Library details** in `server`. For example, an annotation or class that moved in Spring Boot 4.
  The compiler error names the exact line.
- **Integration test expectations** in `server/src/test/java/.../scenario/`. Each test name says which
  requirement it checks, so a failure points at the behaviour to look at.

Merge only when everything is green.

## 2. Create the database (Neon)

1. Sign in at [neon.tech](https://neon.tech) and create a project with **PostgreSQL 17**. Pick the
   region closest to where Render will run (for example Frankfurt or Singapore).
2. Create a database called `flakewatch`.
3. Copy the **direct** connection details (not the "pooled" ones). You need the host, user and
   password.

The JDBC address looks like this:

```
jdbc:postgresql://<host>/flakewatch?sslmode=require
```

## 3. Create the web service (Render)

1. Merging to `main` pushes the image `ghcr.io/kuroh02/flakewatch` to GitHub's registry. Make it
   public: on GitHub, open your profile → **Packages** → `flakewatch` → **Package settings** →
   **Change visibility** → Public.
2. Sign in at [render.com](https://render.com) and create a **Web Service** from **an existing image**:
   `ghcr.io/kuroh02/flakewatch:latest`. Choose the **Free** instance type.
3. Add these environment variables:

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | the JDBC address from step 2 |
| `SPRING_DATASOURCE_USERNAME` | the Neon user |
| `SPRING_DATASOURCE_PASSWORD` | the Neon password |
| `FLAKEWATCH_PUBLIC_URL` | the Render address, for example `https://flakewatch.onrender.com` |
| `PORT` | `8080`, the port the app listens on (Render's default is 10000) |

4. Set the **health check path** to `/actuator/health/liveness`.
5. Deploy. The first start creates the database tables. Open `https://<your-address>/api/v1/version`
   and you should see the version and commit.

## 4. Connect GitHub to Render

So that every merge to `main` deploys by itself:

1. In Render, open the service → **Settings** → **Deploy Hook**, and copy the URL.
2. In the GitHub repository → **Settings** → **Secrets and variables** → **Actions**, add:
   - secret `RENDER_DEPLOY_HOOK`: the deploy hook URL;
   - secret `RENDER_APP_URL`: the Render address.

The `Deploy` workflow then builds the image, deploys it, waits for the new version, and runs a
smoke test after each merge.

## 5. Create the demo project and its token

Projects are created with a one-off command until GitHub sign-in exists (M2). Run the image once
with the same database settings, on your own machine:

```bash
docker run --rm \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL='jdbc:postgresql://<host>/flakewatch?sslmode=require' \
  -e SPRING_DATASOURCE_USERNAME='<user>' \
  -e SPRING_DATASOURCE_PASSWORD='<password>' \
  -e FLAKEWATCH_WORKERS=0 \
  ghcr.io/kuroh02/flakewatch:latest \
  --flakewatch.bootstrap.project=demo --flakewatch.bootstrap.visibility=PUBLIC
```

`FLAKEWATCH_WORKERS=0` stops this one-off copy from processing uploads alongside the live service. It prints an ingest token **once**. Copy it straight into the demo repository's secrets (next step),
and nowhere else.

Run it again with `--flakewatch.bootstrap.project=flakewatch-self` to get a token for Flakewatch's
own test results.

## 6. Start the demo repository

1. Create a public repository, for example `flakewatch-demo`.
2. Copy the contents of [`examples/demo-project`](../examples/demo-project/) into it.
3. In its settings, add the variable `FLAKEWATCH_URL` (the Render address) and the secret
   `FLAKEWATCH_INGEST_TOKEN` (the demo token).
4. Push. Its tests run now and then every 3 hours, on Java 21 and 25.

For Flakewatch's own results ("dogfooding"), add to **this** repository the variable
`FLAKEWATCH_URL` and the secret `FLAKEWATCH_SELF_INGEST_TOKEN`.

## 7. Check it works

- `https://<your-address>/` lists the demo project.
- After the demo's first run, its overview shows tests. Within a day or two, `CheckoutTest` shows
  **Flaky** with evidence, `RefundTest` shows **Broken**, and the rest are **Stable** (success
  criterion SC-06).

## Good to know about the free tiers

- **The service sleeps** after 15 minutes without visitors, and takes about a minute to wake up.
  The upload action waits up to 2 minutes, so CI uploads still arrive. The first page view after a
  quiet spell is slow.
- **The database sleeps too**, after 5 minutes. Flakewatch doesn't poll it, so it can stay asleep
  when nobody is using the app.
- **Daily clean-up and re-checks** run when the app wakes up (on the first visit or upload of the
  day), not on a timer, because a sleeping app can't run a timer.
