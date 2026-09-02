# arena.ai endpoint research — applied

Condensed from a passive-recon report on arena.ai (compiled 2026-09-02:
HTML/JS bundle analysis, robots.txt, sitemaps, benign status-code probing).
Full original: attached by repo owner during development.

## Key conclusions that shaped this app

1. **arena.ai = LMArena** (formerly “LMSYS Chatbot Arena”, UC Berkeley LMSYS).
   It is an AI evaluation / leaderboard platform: chat anonymously with
   models side-by-side, vote, and see rankings.
2. **There is NO public developer API.** No keys, no documented REST/GraphQL.
   Chat/evaluation endpoints (`/nextjs-api/stream/create-chat`,
   `create-evaluation`, …) require an authenticated session cookie +
   Google reCAPTCHA tokens; generation streams over Trigger.dev SSE.
3. Leaderboard data is server-rendered into the HTML of `/leaderboard/*`
   pages (the one unauthenticated JSON endpoint found is
   `/nextjs-api/factuality/ratings`).
4. **Google login is part of the web app** (`/nextjs-api/sign-in/google` →
   accounts.google.com OAuth + reCAPTCHA). Therefore a *full browser
   container with persistent cookies, popup handling and per-origin
   permissions* — not a thin API wrapper — is the only correct client.
   Google login therefore works in-app with zero extra credentials.
5. Streaming is SSE over HTTPS (no WebSockets), i.e. plain WebView
   capability. No special networking is needed.

## Verified route map used for shortcuts

Source: arena.ai sitemap/robots.txt.

| Area | Routes |
|---|---|
| Chat modes | `/` (battle), `/side-by-side`, `/direct`, `/text` (+ sxs/direct), `/search` (+sxs/direct), `/image`, `/video`, `/code`, `/agent` |
| History | `/history/search` |
| Conversations | `/c/{sessionId}` |
| Leaderboards | `/leaderboard`, `/leaderboard/overview`, `/leaderboard/text`, `/leaderboard/agent`, `/leaderboard/vision`, `/leaderboard/document`, `/leaderboard/text-to-image`, `/leaderboard/image-edit`, `/leaderboard/search`, `/leaderboard/text-to-video`, `/leaderboard/image-to-video`, `/leaderboard/video-edit`, `/leaderboard/code/webdev` (+`/html`, `/react`), `/leaderboard/code/image-to-webdev` |
| Info | `/about`, `/how-it-works`, `/faq`, `/blog`, `/privacy-policy`, `/terms-of-use` |
| Adjacent | `status.arena.ai` (incident.io), `help.arena.ai` (Pylon), `x.com/arena`, LinkedIn `/company/arenaai/`, YouTube `@ArenaAIOfficial` |

The quick-start page and dock use the top-level modes; Settings lets the
user point the start page at any route (e.g. `https://arena.ai/direct`).

## Internal API surface (informational only)

| Prefix | Purpose | Auth |
|---|---|---|
| `/nextjs-api/sign-up`, `sign-in/email`, `sign-in/google`, `sign-out`, `resend-verification`, `reset-password/*` | accounts | public entry, session afterwards |
| `/nextjs-api/stream/*` | create/post/stop/rerun/resample chats & evaluations | session + reCAPTCHA |
| `/api/me` | current user | session |
| `/api/evaluation/*/stream-credentials` | Bearer token for SSE streams | session |
| `/api/coding/*` | GitHub connect, repos/branches for coding agent | session |
| `/api/chat/workspace/cas/*` | content-addressable storage files | session |
| `/ai-proxy/realtime/v1/streams/*` | Trigger.dev SSE relay (Authorization Bearer) | token |

Unauthenticated probes return `{"message":"User not found"}` / 401 — no
data leaks. “Fuzzed-and-confirmed-NOT-exposed”: `/api/models`,
`/api/leaderboard`, `/api/votes`, `/api/conversations`, etc.

## Privacy notes applied

* Chat requires an account; the site discloses conversations to model
  providers and may use them for automated evaluation (opt-out:
  `privacy@arena.ai`). The app adds its own local ad/tracker blocking and
  per-site permissions, but arena.ai's own terms still govern the content
  you send through it.
* The app's blocklist deliberately **never** blocks `accounts.google.com`,
  `*.gstatic.com`, `google.com/recaptcha*`, `connect.facebook.net` or
  `staticxx.facebook.com`, so sign-in widgets and reCAPTCHA always work.
