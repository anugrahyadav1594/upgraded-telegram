# UX Design

Design goal: **reduce cognitive load around one primary task** —
*find someone → open conversation → communicate*.

Everything that does not serve that task is either secondary or removed. The interface is calm,
high-contrast, keyboard-friendly and honest about system state.

---

## 1. UX principles

| Principle | Consequence in the UI |
| --- | --- |
| One primary action per screen | Login screen has one button. Chat screen's only persistent action is the composer. |
| State is always visible | Connection pill, sending ticks, typing dots, unread badges, skeleton loaders. The user is never guessing whether something worked. |
| Optimistic, never lying | Outgoing bubbles appear instantly with a clock icon; ticks only advance on server/peer confirmation. |
| Short, literal copy | "No users found. Try another username." — no exclamation marks, no marketing speak. |
| Keyboard-first | `Enter` sends, `Shift+Enter` newline, `/` focuses search, `Esc` closes dialogs, arrows navigate the conversation list, `Alt+↑/↓` switches conversations. |
| Privacy is legible | Each privacy toggle states its effect in one sentence next to the control. |
| Calm by default | No sounds, no auto-scroll hijacking when the user is reading history (a "Jump to latest" pill appears instead). |

---

## 2. Information architecture

```
Unauthenticated
├── /login                     Login
├── /register                  Register
├── /forgot-password           Request reset link
└── /reset-password?token=…    Set new password

Onboarding (authenticated, profile incomplete)
└── /onboarding                Username → display name → avatar → bio → privacy (single page, 3 steps)

Authenticated shell
├── /                          Conversation list (+ empty state)   ← default route
├── /c/@username               Conversation list + open chat (desktop 3-pane / mobile push)
├── /new                       Find people (search)
├── /u/:username               Public profile view
├── /settings
│   ├── /settings/profile      Username, display name, avatar, bio
│   ├── /settings/privacy      Online status, last seen, search visibility, allowMessagesFrom, receipts
│   ├── /settings/notifications Notification preferences
│   ├── /settings/appearance   Theme, message density
│   ├── /settings/security     Sessions, password change, logout all
│   └── /settings/blocked      Blocked users
└── /notifications             Notification centre
```

Route guard rules: unauthenticated → `/login`; authenticated but missing username → `/onboarding`;
unknown route → friendly 404 with a link back to the conversation list.

---

## 3. Screen designs

### 3.1 Authentication

**Login** — single column, 400 px max width, vertically centred on desktop, full-width on mobile.

```
┌──────────────────────────────────────────┐
│                                          │
│              ● REALCHAT                  │
│        Private conversations,            │
│        by username only.                 │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ Email or @username                 │  │
│  └────────────────────────────────────┘  │
│  ┌────────────────────────────────────┐  │
│  │ Password                    (👁)   │  │
│  └────────────────────────────────────┘  │
│                                          │
│  [            Log in              ]      │
│                                          │
│  Forgot password?      Create account    │
│                                          │
│  ⚠ Email or password is incorrect.       │
└──────────────────────────────────────────┘
```

Rules: errors appear *below the form*, never as toasts that vanish; the button shows an inline
spinner and is disabled while the request is in flight; the password field has a show/hide toggle
with `aria-pressed`; `autocomplete="username"`/`"current-password"` is set so password managers
work.

**Register** — same shell, fields: email, username (`@` prefix visual, live availability + policy
feedback), display name, password (with strength meter and policy checklist), confirm password,
privacy summary line ("Your email is never shown to other users."), and a required checkbox for the
pseudonymity notice.

**Forgot / Reset** — one field (email) with a neutral confirmation: *"If that email is registered,
we've sent a reset link."* Reset page shows the token validity ("This link expires in 30 minutes"),
new password + confirmation, and, on success, routes to login with a success banner rather than
auto-login.

### 3.2 Onboarding

Completing the profile is required before the app shell opens. One screen, three short steps with a
progress indicator; each step states why it is needed.

```
Step 1 of 3 · Choose your username
  @[ ghost_42            ]   ✓ available
  Your public identity. Lowercase letters, numbers and _ (3–32).

Step 2 of 3 · How should people see you?
  Display name [ Ghost            ]
  Avatar       (⚪ initial preview)  [Upload] [Pick a colour]

Step 3 of 3 · Privacy
  (•) People can find me by username        → explained in one line
  ( ) Anyone can message me                 → CONTACTS | EVERYONE
  [x] Show online status   [x] Show last seen
  [ Continue to chats ]
```

Skipping is allowed for avatar/bio only; username and display name are mandatory (the product's
identity model depends on them).

### 3.3 Main application — desktop (≥ 1024 px)

```
┌──────────┬───────────────────────────┬────────────────────────────────────────────┐
│          │  Search people…           │  ← Ghost                          ⋮        │
│   (👤)   ├───────────────────────────┤    @ghost · ● online                       │
│  Avatar  │                           │  ────────────────────────────────────────  │
│          │  Ghost                    │                                            │
│   💬     │  See you tomorrow  ✓✓     │                    Hey!                    │
│   🔔 3   │  10:42 PM                 │                    10:41                   │
│   ⚙      │                           │                                            │
│          │  Alex                     │   What's up?                               │
│          │  typing…                  │   10:42  ✓✓                               │
│          │  9:17 PM                  │                                            │
│          │                           │                     I'm testing the app 😂  │
│          │  @anonymous_user          │                     10:42 ✓✓              │
│          │  Hey                      │  ────────────────────────────────────────  │
│          │  Yesterday                │  😊  Message…                        ➤     │
└──────────┴───────────────────────────┴────────────────────────────────────────────┘
   64 px           320–380 px (resizable, persisted)              flexible
```

- **Navigation rail** (64 px): avatar (→ own profile), Chats, Notifications (badge), Settings.
  Tooltips on hover, `aria-label` always.
- **Conversation list**: search field, then rows ordered by last activity. Each row shows avatar,
  name (with `@username` on a second line when the display name is ambiguous), a one-line preview
  (`You: …` prefix for own messages, "This message was deleted" for tombstones), time, unread pill,
  mute icon, and a **typing…** replacement for the preview. Active row has a 3 px accent bar and
  filled background.
- **Chat pane**: sticky header (back, avatar, display name, `@username`, presence line, overflow
  menu: View profile / Mute / Archive / Block / Report / Delete conversation), message list, grouping
  dividers by day, and the composer.
- **Overflow menu** uses a labelled popover with destructive actions visually separated
  (red text, confirmation dialog for Block and Delete).

### 3.4 Main application — tablet (768–1023 px)

Two panes: the navigation rail collapses to a horizontal icon bar (or a bottom bar in
portrait), and the conversation list is 280 px. Opening a conversation replaces the list with the
chat if width < 900 px, otherwise both are visible.

### 3.5 Main application — mobile (< 768 px)

Distinct navigation model, not a squeezed desktop:

```
[ Conversation list ]  →  [ Active chat ]  →  [ Profile / Settings ]
        ↑ tap row              ↑ tap header        ↑ back swipe / ‹ button
```

- Header is 56 px tall with a 44×44 px back button and a safe-area top inset.
- The composer sticks above the keyboard (`visualViewport` aware), with the emoji and attachment
  controls inside the field row.
- Long-press (500 ms) on a message opens the action sheet (Reply, React, Copy, Edit, Delete,
  Report) — the same actions are available via the `⋮` menu for keyboard/AT users.
- The conversation list supports swipe-left for Mute/Delete with a 12 px confirmation nib.
- Pull-to-refresh at the top of the message list triggers a history page load (`hasMore` guard).

### 3.6 Chat screen anatomy (required elements)

| Element | Desktop | Mobile |
| --- | --- | --- |
| Conversation header | sticky, 64 px | 56 px |
| Avatar | 40 px in header, 32 px in list | 36 px in header |
| Display name + `@username` | name bold 15 px, username muted 13 px | same |
| Online / last seen | second line: `● online` / `last seen 12 min ago` / hidden | same |
| Message list | virtualisation-ready `<ol role="log">` | same |
| Composer | textarea auto-grow 1–6 lines (max-height 160 px) | same, `enterKeyHint="send"` |
| Attachment control | paperclip button (disabled with tooltip "Attachments coming soon" until implemented) | inside field row |
| Emoji control | smiley button → 32-emoji quick picker + search | same, opens above keyboard |
| Send button | 40 px accent circle, disabled when empty, `aria-label="Send message"` | 44 px |
| Typing indicator | dots above composer + "typing…" in the list row | dots at the bottom of the list |

---

## 4. Component states

Every major component is designed for all states, not only the happy path.

### Conversation list

| State | Presentation |
| --- | --- |
| Loading (first load) | 6 skeleton rows, shimmer disabled under `prefers-reduced-motion` |
| Empty | `Start a conversation` + illustration + primary button "Find people" → `/new` |
| Error | "Couldn't load your chats." + "Try again" button + `X-Request-Id` in a copyable details line |
| Offline | Cached list stays visible with a dimmed overlay-free banner: "Offline — showing your last synced chats" |

### Message history

| State | Presentation |
| --- | --- |
| Loading (first page) | 5 skeleton bubbles |
| Loading (older page) | Inline spinner at the very top after scroll-up; preserves scroll position |
| Empty | Centred: "This is the beginning of your conversation." + the peers' avatar |
| Error | Bubble-area error with "Retry" |
| Reconnecting | Thin amber bar under the header: "Connection lost · Reconnecting…" with attempt counter |
| Offline send attempt | Bubble shows clock icon; on failure: red "!" + "Message failed to send · Retry" |

### Search

| State | Presentation |
| --- | --- |
| Idle | Recent contacts (max 5) + hint "Search by username or name" |
| Loading | 3 skeleton rows |
| No results | "No users found. Try another username." |
| Error | "Search is unavailable right now." + Retry |
| Rate limited | "Too many searches. Try again in 30 seconds." |

### Presence & typing

| State | Presentation |
| --- | --- |
| Online | `● online` in the accent colour |
| Away | `◐ away` (derived from 5 min of inactivity) |
| Last seen | `last seen 12:04` / `last seen yesterday` / `last seen recently` (> 7 days) |
| Hidden by privacy | the presence line is simply absent — never "hidden", never "unknown" |
| Typing | Three animated dots + `typing…` in the list; suppressed if blocked |

### Global connection states

```
CONNECTED     → no indicator
DISCONNECTED  → amber banner: "Connection lost · Reconnecting…"
RECONNECTING  → banner gains "attempt 3 · next try in 8s"
FAILED        → red banner: "Can't reach the server" + [Retry now]
```

Banners are `role="status"` and announced once (not on every retry).

---

## 5. Message presentation rules

- **Own vs other**: own bubbles right-aligned, accent background, 18 px radius with the corner
  nearest the edge sharpened; other bubbles left-aligned, surface background, avatar only on the
  first message of a group.
- **Grouping**: consecutive messages from the same author within 5 minutes are grouped; only the
  last bubble in a group carries the timestamp and ticks; group gets a single avatar + author line
  (in group conversations, future).
- **Day dividers**: sticky pill `Today` / `Yesterday` / `12 September 2026`.
- **Status icons** (own messages only, 12 px): `🕘 sending` → `✓ sent` → `✓✓ delivered` →
  `✓✓ read (accent)`. A tooltip spells the word out; the accessible label reads it too.
- **Replies**: quoted block inside the bubble (author + one line, truncated at 80 chars) that is
  clickable to scroll to the parent; if the parent was deleted, render "Original message deleted".
- **Reactions**: chips attached to the bubble's bottom edge — `😂 3` with the reactor avatars in a
  tooltip; own reaction outlined; clicking toggles; a `+` chip opens the quick picker.
- **Links**: auto-linked, `rel="noopener noreferrer nofollow"`, opening in a new tab with a
  confirmation for untrusted domains (roadmap: URL reputation list).
- **Accessibility**: the list is `role="log" aria-live="polite" aria-relevant="additions"`; each
  item has a visually-hidden prefix `Ghost, 10:42: `; reactions and status are exposed in the
  accessible name; the composer is a labelled `textarea`.

---

## 6. Copy deck (short, literal, actionable)

| Context | Copy |
| --- | --- |
| Empty conversation list | `No conversations yet.` / `Search for someone to start chatting.` |
| Empty history | `This is the beginning of your conversation.` |
| Degraded grid | `Connection lost` / `Reconnecting…` |
| Reconnect failure | `Can't reach the server.` / `Retry now` |
| Message failure | `Message failed to send` / `Retry` |
| Deleted message | `This message was deleted` |
| Search empty | `No users found.` / `Try another username.` |
| Blocked send attempt | `You can't message this person.` |
| Rate limited | `Too many attempts. Try again in {n} seconds.` |
| Registration success | `Account created. Let's set up your profile.` |
| Reset confirmation | `If that email is registered, we've sent a reset link.` |
| Presence hidden | *nothing rendered* |

Voice: second person, present tense, no jokes in error states, no exclamation marks except in the
user's own messages.

---

## 7. Visual system

| Token | Light | Dark |
| --- | --- | --- |
| `--bg-canvas` | `#ffffff` | `#0f1419` |
| `--bg-surface` | `#f7f8fa` | `#161b22` |
| `--bg-elevated` | `#ffffff` | `#1c2430` |
| `--text-primary` | `#11161d` | `#e7edf3` |
| `--text-secondary` | `#5a6472` | `#9aa7b6` |
| `--border` | `#e3e7ec` | `#26303c` |
| `--accent` | `#2f6fed` | `#5b8dff` |
| `--accent-contrast` | `#ffffff` | `#0b1220` |
| `--bubble-own` | `#2f6fed` | `#3f6fd8` |
| `--bubble-other` | `#f1f3f6` | `#222b36` |
| `--danger` | `#d13438` | `#ff6b6b` |
| `--success` | `#1a7f37` | `#4ade80` |
| `--warning` | `#b26a00` | `#f2b544` |

- **Type**: system UI stack (`-apple-system, Segoe UI, Roboto, Inter, sans-serif`); scale 12/13/15/17/20/24;
  line-height 1.45 for messages, 1.3 for chrome.
- **Spacing**: 4 px base grid (4/8/12/16/24/32).
- **Radii**: 8 px controls, 18 px bubbles, 999 px pills, 50 % avatars.
- **Elevation**: two levels only (surface, popover) to keep the interface flat and calm.
- **Motion**: 120 ms ease-out for hovers, 180 ms for panel transitions, 0 ms when
  `prefers-reduced-motion: reduce`.
- **Avatars**: deterministic colour from the username hash (8 hue-rotated accents, all AA-contrast
  against white text) with the first grapheme of the display name; uploaded avatars are centre-cropped
  to a circle.
- **Focus**: 2 px `--accent` outline with 2 px offset, never removed, visible on all interactive
  elements including list rows and message bubbles' action triggers.

---

## 8. Accessibility checklist (verified per screen)

- [x] Every control is a real `button`/`a`/`input` with an accessible name (icon-only buttons use `aria-label`).
- [x] Focus order follows visual order; dialogs trap focus, restore it on close, and close on `Esc`.
- [x] New messages, typing and connection changes are announced via `role="status"`/`role="log"` (polite).
- [x] Colour is never the only signal (ticks carry text alternatives; unread badges carry `aria-label="3 unread messages"`).
- [x] Scroll containers are keyboard scrollable and have `tabindex="0"` with `aria-label`.
- [x] Skip-link "Skip to conversation" is the first focusable element in the app shell.
- [x] Contrast checked for both themes (text ≥ 4.5:1, non-text UI ≥ 3:1).
- [x] Forms use `label` + `aria-describedby` for help/error text; errors are announced with `role="alert"`.
- [x] Reduced motion: typing dots, shimmer, and panel transitions disabled.
- [x] Touch targets ≥ 44×44 px; nothing relies on hover only.

---

## 9. Responsive breakpoints

| Breakpoint | Layout | Notes |
| --- | --- | --- |
| `≥ 1440 px` | 3 panes; list 380 px | optional right-hand "contact info" rail on wide screens |
| `1024–1439 px` | 3 panes; list 320 px | navigation rail 64 px |
| `768–1023 px` | 2 panes (list + chat) | navigation becomes a top icon bar; list 280 px |
| `< 768 px` | 1 pane, push navigation | sticky headers, bottom-anchored composer, safe-area insets |
| `< 420 px` | 1 pane, compact | smaller avatars (32 px), tighter padding (12 px), timestamps inside bubbles only |

Testing: 320, 375, 414, 768, 1024, 1280, 1440 widths; landscape mobile; 200 % zoom; iOS Safari and
Android Chrome with the on-screen keyboard open.
