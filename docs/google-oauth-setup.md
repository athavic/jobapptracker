# Google OAuth setup (needed before phase 5c)

Phase 5c replaced `X-Actor` with a real Google sign-in. That needs an OAuth
client, which only you can create — it lives in your Google account, and the
client secret is a credential that should never pass through a chat window or a
commit.

This is the whole list. Roughly ten minutes.

---

## 1. Create a project

<https://console.cloud.google.com/projectcreate>

Name it something you will recognise in a year — `jobapptracker` is fine. No
billing account required; nothing here costs anything.

## 2. Configure the consent screen

**APIs & Services → OAuth consent screen**, which recent consoles present as
**Google Auth Platform**, split into *Branding*, *Audience*, *Clients* and
*Data Access*. The settings are the same; only the page they live on moved.

| Field | Value | Why |
|---|---|---|
| User type / Audience | **External** | "Internal" only exists with a Google Workspace organisation. External is correct for a personal Gmail account. |
| App name | shown on the sign-in screen | |
| User support email | your address | Required. |
| Developer contact | your address | Required. |

**Leave the publishing status as "Testing."** It needs no verification review.

### Testing does not restrict who can sign in here

This is the part that surprises people, and it is documented behaviour rather
than a misconfiguration. Google's Testing rules - trusted-user list, warning
screen, authorizations expiring after seven days - have an explicit exception:

> The only exception to this behavior is if your app requests a subset of the
> following: name, email address, and user profile (through the
> `userinfo.email`, `userinfo.profile`, `openid` scopes or their OpenID Connect
> equivalents). For such requests, your users do not need to be in the trusted
> user list, they will not see a warning message, and their authorizations will
> not expire after 7 days. If your app uses Sign in with Google to authenticate
> users then this exception also applies.

That is exactly this app's scope set. So:

- **Adding test users changes nothing while the scopes stay as they are.** Any
  Google account can complete the sign-in. This was observed directly: four
  accounts signed in with one test user configured, and Google counted two,
  because the other two never consumed test-user quota.
- **No seven-day expiry**, and no unverified-app warning screen.
- **Phase 7 ends the exception.** `gmail.readonly` is a restricted scope, and
  requesting anything beyond the three above means the trusted-user list starts
  being enforced, the warning screen appears, and authorizations begin expiring
  after a week. Plan for that when Gmail ingestion arrives; it is a change in
  behaviour, not just a new permission.

**The consequence worth internalising: Google's consent screen is not this
application's access control, and cannot be made into it.** Anyone who can
authenticate gets an `app_user` row and a workspace of their own. What protects
your applications is workspace scoping - phase 5d - and membership. If you want
sign-up itself to be closed, that is a deliberate feature (an allowlist, or
invite-only registration against `workspace_invite`), not a console setting.

## 3. Scopes: three, and you may not have to touch this at all

The app needs exactly:

- `openid`
- `.../auth/userinfo.email`
- `.../auth/userinfo.profile`

These give what V6's `app_user` table is designed around: the `sub` claim (the
permanent account identifier), the email address, and a display name plus avatar.

**You do not have to declare them anywhere for sign-in to work.** Scopes are
requested by the application, at sign-in, in the authorization request - in this
codebase that is `spring.security.oauth2.client.registration.google.scope`. All
three are non-sensitive, and Google grants non-sensitive scopes without their
being pre-registered. An app using only non-sensitive scopes is also exempt from
verification.

If you want the consent screen to list them explicitly, they go under **Data
Access** (older consoles: *Add or remove scopes* on the consent screen). That is
a separate page from client creation, which is why it is easy to finish setup
without ever seeing it. Nothing is broken if you skipped it.

**Do not add any Gmail scope yet.** Beyond the verification cost, it flips the
Testing behaviour described above.

## 4. Create the client

**APIs & Services → Credentials → Create credentials → OAuth client ID**

- **Application type:** Web application
- **Name:** `jobtracker-local`
- **Authorised redirect URIs** — add exactly this:

```
http://localhost:8080/login/oauth2/code/google
```

Three things about that URI, each of which is a way people lose an afternoon:

- **Port 8080, not 5173.** The redirect comes back to the *API*, not the Vite dev
  server. The browser goes to Google, Google returns to Spring, and Spring sets
  the session cookie and then sends the browser on to the UI.
- **`/login/oauth2/code/google` is Spring Security's default callback path.**
  It is not something we choose; it is what the framework registers. The last
  segment is the registration id, which will be `google` in `application.yml`.
- **It must match character for character.** A trailing slash, `https` instead of
  `http`, or `127.0.0.1` instead of `localhost` all produce
  `Error 400: redirect_uri_mismatch`.

**Authorised JavaScript origins:** leave empty. Those are for browser-side
flows. This is the authorization code flow, where the exchange happens
server-to-server and the client secret never reaches the browser — which is the
reason to use it.

## 5. Put the credentials where they belong

Google shows a client ID and a client secret. Create `.env` at the repository
root — the folder holding `docker-compose.yml`, `api/` and `web/`:

```
GOOGLE_CLIENT_ID=...apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=...
```

Keep them alongside the `DB_*` variables described in `.env.example`. One file,
one place to look.

`.env` is already in `.gitignore`, and it does not belong to any branch — an
ignored file is untracked, so git leaves it alone when you switch. Confirm it
stays that way before committing anything:

```bash
git check-ignore -v .env
```

Nothing should print the secret to a terminal, paste it into a chat, or write it
into `application.yml`. The client ID is not secret — it is sent to the browser
on every sign-in — but the secret is a password for your OAuth client. If it does
get exposed, the fix is one button: **Credentials → the client → Reset secret**,
which invalidates the old value immediately.

### How the API reads it

**Spring Boot has no special support for `.env` files** — and needs none, because
a `.env` *is* a properties file: `KEY=VALUE`, one per line. Phase 5c added this
to `application.yml`:

```yaml
spring:
  config:
    import:
      - optional:file:./.env[.properties]
      - optional:file:../.env[.properties]
```

`[.properties]` tells Boot how to parse a file whose name gives no hint. Two
paths because the working directory depends on how the app was started — `api/`
for `mvn -f api/pom.xml spring-boot:run` and most IDE run configurations, the
repository root for anything launched from there. `optional:` means a missing
file is not an error, which is what lets a fresh clone and CI start normally.

Before 5c, only **docker compose** read this file, which is why the `DB_*`
variables appeared to work: Compose read them, and the API separately fell back
to identical defaults. Both read it now.

One consequence worth knowing: values are parsed as Java properties, so a
backslash escapes and an unquoted `#` starts a comment. Google credentials
contain neither.

## 6. Generate the worker's service key

One more secret, unrelated to Google. From 5c the API requires a principal on
every request, and the Python worker has no browser and therefore no session
cookie. It presents a shared key instead.

```bash
python -c "import secrets; print(secrets.token_urlsafe(48))"
```

The same value goes in two places — two names because each side namespaces its
own configuration, one secret because it is one credential:

- `AUTOMATION_SERVICE_KEY` in the root `.env`, which the API reads
- `JOBTRACKER_SERVICE_KEY` in `automation/.env`, which the worker reads

Leave it blank and service authentication is simply off: the worker gets 401s,
and the API logs a warning at startup saying so. It acts across every workspace,
which makes it the one secret in this project that must never reach a browser.

## 7. Sign in

```bash
docker compose up -d
mvn -B -f api/pom.xml spring-boot:run
```

then `npm run dev` in `web/`, open <http://localhost:5173>, and press
**Continue with Google**.

The first person to sign in adopts the workspace the V7 migration created — the
one holding every application that existed before there were users. Everyone
after that gets a workspace of their own.

If it fails, the usual cause is a redirect URI that does not match character for
character — a trailing slash, `https` instead of `http`, or `127.0.0.1` instead
of `localhost` all produce `redirect_uri_mismatch`. The test-user list is *not*
a likely cause here; see section 2 for why it does not gate this scope set.

---

## Later, not now

- **Phase 8 (deploy)** adds a second redirect URI for the real hostname. Add it
  alongside the localhost one — a client can hold several, so local development
  keeps working.
- **Phase 7 (Gmail)** is where the restricted scope arrives, bringing its
  verification review *and* an end to the Testing exception in section 2 — the
  trusted-user list starts being enforced, the unverified-app warning appears,
  and authorizations begin expiring after seven days.
