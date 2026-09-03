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

**APIs & Services → OAuth consent screen**

| Field | Value | Why |
|---|---|---|
| User type | **External** | "Internal" only exists if you have a Google Workspace organisation. External is correct for a personal Gmail account. |
| App name | `Job Tracker` | Shown on the sign-in screen. |
| User support email | your address | Required. |
| Developer contact | your address | Required. |

**Leave the publishing status as "Testing."** A Testing app needs no
verification review, and you add yourself under **Test users** to be allowed in.
The limit is 100 users, which is 99 more than this app currently needs.

One consequence worth knowing: in Testing, Google expires refresh tokens after
7 days. That would matter if the app kept long-lived offline access. It does not
— 5c uses a server-side session cookie, so a sign-in lasts as long as the
session and re-authenticating is a redirect, not an outage.

## 3. Scopes: ask for three, and only three

**Add or remove scopes** → select:

- `openid`
- `.../auth/userinfo.email`
- `.../auth/userinfo.profile`

These are the non-sensitive scopes. They give exactly what V6's `app_user` table
is designed around: the `sub` claim (the permanent account identifier), the
email address, and a display name plus avatar.

**Do not add any Gmail scope yet.** Phase 7 reads application confirmation
emails, and `gmail.readonly` is a *restricted* scope — requesting it pushes the
app into Google's verification process, including a possible security
assessment. That is a phase 7 problem, and adding it early would mean doing that
review before you can sign in at all.

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
root:

```
GOOGLE_CLIENT_ID=...apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=...
```

`.env` is already in `.gitignore`. Check that it stays untracked before you
commit anything:

```bash
git check-ignore -v .env
```

Nothing should print the secret to a terminal, paste it into a chat, or write it
into `application.yml`. The client ID is not secret — it is sent to the browser
on every sign-in — but the secret is a password for your OAuth client, and the
only place it belongs is that file and, later, the deployment's environment.

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
