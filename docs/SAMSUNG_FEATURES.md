# Samsung and live-notification features

Researched against current Android and One UI behaviour before implementation.
Every claim here is backed by a runtime capability check in
`notification/DeviceCapabilities.kt` — nothing is assumed from the model name.

## Summary

| Feature | Status | Implementation |
| --- | --- | --- |
| Live Updates (status-bar chip, lock screen) | Supported, Android 16+ | `POST_PROMOTED_NOTIFICATIONS` + promoted ongoing notification |
| Samsung Now Bar | Supported via Live Updates, One UI 8+ | same notification; no separate SDK exists |
| Quick Settings tile | Supported, all versions | `TileService` |
| Home-screen widget | Supported, all versions | Glance `AppWidget` |
| **True lock-screen widget** | **Not app-controllable** | widget declares `keyguard`; the system decides |
| Notification actions | Supported, capped at 3 | 2 categories + "More…" |

## Live Updates (Android 16 / API 36)

Android 16 introduced **Live Updates**: an ongoing notification the system may
promote to a status-bar chip and give priority placement on the lock screen and
in the shade.

Requirements, all of which this app meets:

- `android.permission.POST_PROMOTED_NOTIFICATIONS` in the manifest (non-runtime)
- the notification is `ongoing`
- `contentTitle` is set
- a supported style — this app uses `BigTextStyle`
- **no** custom `RemoteViews`, **no** `setColorized(true)`, not a group summary,
  and not on an `IMPORTANCE_MIN` channel

Promotion is requested through the platform extra
`android.requestPromotedOngoing`, with `android.shortCriticalText` supplying the
amount for the narrow status-bar chip. The extras are set directly rather than
through a compat setter so the code behaves identically across AndroidX
versions; pre-Android-16 releases simply ignore them.

Promotion is never guaranteed. `NotificationManager.canPostPromotedNotifications()`
reports whether the user has allowed it for this app, and OEMs may add their own
criteria. `DeviceCapabilities.supportsLiveUpdates` checks the API level **and**
that setting, and when either fails the app posts an ordinary high-importance
notification with the same actions. The user loses the chip, not the feature.

### Lifecycle

1. Alert parsed → ongoing promoted notification: "₹32 spent · Q528800175@ybl ·
   What was this for?" with category actions.
2. User taps a category → notification is replaced by a brief confirmation
   ("Saved ₹32 · Food") which auto-dismisses after four seconds.
3. Clearing the ongoing state is what removes the entry from the Now Bar.

`setDeleteIntent` catches a swipe-away; dismissing deletes the unconfirmed row,
because the user has said this is not something they want tracked.

## Samsung Now Bar

The Now Bar arrived in One UI 7 for Samsung's own apps. **One UI 8 (Android 16)
opened it to third parties, and it does so by consuming Android 16 Live Updates.**

There is no public Samsung SDK, no separate Now Bar API, and no partner
integration to apply for. **Posting a compliant Live Update *is* the supported
Now Bar integration.** Any library or trick claiming otherwise is not official.

`DeviceCapabilities.supportsNowBar` therefore requires: Samsung manufacturer,
Live Updates supported and permitted, and One UI 8 or later. One UI version is
read defensively from `Build.VERSION.SEM_PLATFORM_INT` (Samsung-only, encoded as
`90000 + major * 10000`) inside a `runCatching`; an unreadable value is treated
as "unknown, assume capable" rather than a hard no.

Settings shows the user exactly what their device supports and what they get
instead when it does not.

## Lock-screen Quick Add: what is actually possible

The brief asked for a lock-screen "Add Expense" button. Researched conclusion:

**No Android API lets an app place itself on the lock screen.** Lock-screen
widgets returned in Android 16 QPR1 / AOSP, and One UI 8 can surface third-party
widgets, but in every case it is the *system picker* that offers a widget and the
*user* who places it. An app can only make itself eligible.

So the app ships three official surfaces and is honest about each:

1. **Quick Settings tile** (`QuickAddTileService`) — the most reliable path. The
   QS shade is reachable from the lock screen on every Android version and every
   OEM skin. Uses the `PendingIntent` form of `startActivityAndCollapse` on API
   34+, where the older overload is a no-op.
2. **Glance app widget** declared `android:widgetCategory="home_screen|keyguard"` —
   eligible for the lock-screen widget area on devices that have one, and for
   the home screen everywhere.
3. **Launcher shortcut** — long-press the app icon.

All three open the same transparent `QuickAddActivity`, declared
`showWhenLocked="true"` so a payment can be recorded without a full unlock.

**What was deliberately not done:** no `SYSTEM_ALERT_WINDOW` overlay pretending
to be a lock-screen widget, no accessibility-service abuse, no root. If the
platform does not offer it, the app says so in Settings rather than faking it.

## Notification action limit

Android shows at most **three** notification actions. The app spends them on the
two most likely categories (the learned suggestion first, when there is one)
plus "More…", which opens the quick-add sheet with the full picker and a
description field. This is a platform cap, not a design choice.

## Testing on a device without Samsung hardware

Live Updates can be exercised on any Android 16 device or emulator image — the
chip and lock-screen placement are AOSP behaviour. The Now Bar itself needs a
Samsung device on One UI 8. On anything older, verify the fallback: a normal
heads-up notification carrying the same three actions.
