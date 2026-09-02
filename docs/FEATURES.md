# Feature guide

Arena AI Mobile is a full mobile browser whose home is the arena.

## Everyday use

| You want to… | Do this |
|---|---|
| Chat / battle | Dock → **Arena**, or the “Arena Battle” tile |
| Talk to one model | Home → **Direct chat** (`arena.ai/direct`) |
| Images / Video / Code / Agents | Home tiles or dock shortcuts |
| Log in with Google | Open any arena.ai page → **Sign in** → *Continue with Google*. The whole OAuth flow (incl. reCAPTCHA) runs in the app; your session persists until you sign out or clear browsing data. |
| Attach a photo/file to a chat | Tap the paperclip/upload button in arena.ai, then pick **Files / Camera** — the file picker incl. multiple-select and “take photo/video” is fully wired |
| Speak / use the camera on a site | Grant the site camera/mic in the prompt (e.g. for voice chats) |
| Search the web | Type in the address bar (non-URL text searches Google/DuckDuckGo/…) |
| Download a file | Tap a download link — it saves to **Downloads** with a notification; long-press an image → *Save image* saves to **Pictures/Arena AI** (Gallery) |
| Watch a video | HTML5/YouTube video expands to fullscreen; swipe/back exits |
| Open a second thing | `target=_blank` links & popups stay in-app |
| Share a page | Menu → Share this page |
| Send a link *into* the app | Share → Arena AI from any other app |
| Read a long page | Settings → Text size, or pinch-zoom |
| Desktop layout | Menu → **Desktop site** toggle |
| Dark mode everywhere | Menu → **Dark web content** toggle |

## Privacy & blocking

* **Block ads & trackers** (default on): host-level blocklist + cosmetic
  hiding. Keeps Google login, reCAPTCHA, and site logins working by design.
* **Allow ads on this site**: per-site opt-out via the menu.
* **Clear browsing data** in Settings removes cookies/storage/cache
  (signs you out everywhere).

## Updates

Settings → *Check for updates now*, or Menu → *Check for updates*. The app
compares against the newest GitHub release of this repository and can
download & install it (Android asks once to allow “install unknown apps”).

> Requires a stable signing key to install over an existing copy — see
> docs/SIGNING.md.

## Settings map

| Setting | Effect |
|---|---|
| Search engine | Google, Bing, DuckDuckGo, Startpage, Yahoo |
| Start page | Arena quick start (branded page) or straight to `https://arena.ai` or a custom URL |
| Block ads & trackers | master switch for host + cosmetic blocking |
| Desktop site | forces the desktop user agent + wide viewport |
| Force dark web content | algorithmic darkening of light sites (Android 10+) |
| Text size | WebView text zoom 75–200% |
| Check for updates daily | background GitHub release check (max 1/day) |
| Clear browsing data | cookies, WebStorage, cache |
