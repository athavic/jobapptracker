# Google OAuth setup (needed before phase 5c)

Phase 5c replaces `X-Actor` with a real Google sign-in. That needs an OAuth
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
*Data Access*. The settings below are the same either way; only the page they
live on has moved.

| Field | Value | Why |
|---|---|---|
| User type / Audience | **External** | "Internal" only exists if you have a Google Workspace organisation. External is correct for a personal Gmail account. |
| App name | `Job Tracker` | Shown on the sign-in screen. |
| User support email | your address | Required. |
| Developer contact | your address | Required. |

**Leave the publishing status as "Testing."** A Testing app needs no verification
review. The limit is 100 users, which is 99 more than this app currently needs.

> **This part is not optional.** While the app is in Testing, add your own Google
> account under **Test users** (in the newer console: *Audience → Test users*).
> An account that is not on that list cannot sign in, and the failure is a
> generic "app is blocked" rather than anything that explains itself.

One consequence worth knowing: in Testing, Google expires refresh tokens after
7 days. That would matter if the app kept long-lived offline access. It does not
— 5c uses a server-side session cookie, so a sign-in lasts as long as the
session and re-authenticating is a redirect, not an outage.

## 3. Scopes: three, and you may not have to touch this at all

The app needs exactly:

- `openid`
- `.../auth/userinfo.email`
- `.../auth/userinfo.profile`

These give what V6's `app_user` table is designed around: the `sub` claim (the
permanent account identifier), the email address, and a display name plus avatar.

**You do not have to declare them anywhere to make sign-in work.** Scopes are
requested by the application, at sign-in, in the authorization request — in 5c
that is `spring.security.oauth2.client.registration.google.scope`. All three are
*non-sensitive*, and Google grants non-sensitive scopes without their being
pre-registered. An app using only non-sensitive scopes is also not required to
complete verification.

If you want the consent screen to list them explicitly, they go under **Data
Access** (older consoles: *Add or remove scopes* on the consent screen). That is
a separate page from client creation, which is why it is easy to finish setup
without ever seeing it. Nothing is broken if you skipped it.

**Do not add any Gmail scope yet.** Phase 7 reads application confirmation
emails, and `gmail.readonly` is a *restricted* scope — requesting it pushes the
app into Google's verification process, including a possible security
assessment. That is a phase 7 problem, and adding it early would mean passing
that review before being able to sign in at all.

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

### The file is not enough on its own

**Spring Boot does not read `.env` files.** Nothing in the framework looks for
one. `${GOOGLE_CLIENT_ID}` in `application.yml` resolves against real environment
variables and the usual Spring property sources, and finds nothing if the value
exists only in a file.

Today that file is read by exactly one thing: **docker compose**, which picks up
a root `.env` automatically. That is why the `DB_*` variables look like they
work — Compose reads them, and the API separately falls back to identical
defaults in `application.yml`. The API is not reading the file.

Phase 5c closes the gap with the `spring-dotenv` dependency, which loads `.env`
into Spring's `Environment` at startup, so the `${...}` placeholders already used
throughout `application.yml` keep working unchanged. The alternative — pasting
the values into IntelliJ's run configuration — also works, but lives in IDE
settings rather than in the project, so it does not survive a fresh clone and
nobody else can see what it sets.

Nothing to do about this now. The file and its contents are right; they just
need one dependency before anything reads them.

## 6. Tell me it exists

That is all 5c needs from you. Say the client is created and I will wire it up:
the `spring-boot-starter-oauth2-client` dependency, the security filter chain,
the session cookie, first-sign-in adoption of the bootstrap workspace, and
`GET /api/v1/me`.

---

## Later, not now

- **Phase 8 (deploy)** adds a second redirect URI for the real hostname. Add it
  alongside the localhost one — a client can hold several, so local development
  keeps working.
- **Phase 7 (Gmail)** is where the restricted scope and its review arrive.
