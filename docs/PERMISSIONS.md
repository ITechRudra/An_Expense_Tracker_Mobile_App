# Permissions

The app requests six permissions and **no others**. Each is listed below with
why it is needed, what it enables, and what happens if the user declines —
which is exactly what the onboarding flow tells them, in those words.

## What is NOT requested

**`android.permission.INTERNET` is deliberately absent.**

Every rupee this app sees comes from an SMS on the device and stays in an
app-private database. Omitting the permission entirely means that promise is
enforced by the platform rather than by our good intentions, and anyone can
verify it by reading the manifest or running `aapt dump permissions`. No
analytics, no crash reporting, no sync. There is no server.

Also not requested: location, contacts, camera, storage, `READ_PHONE_STATE`,
`FOREGROUND_SERVICE`, `SCHEDULE_EXACT_ALARM`, or battery-optimisation
exemption. None of them are needed, so none of them are asked for.

## Runtime permissions

### `RECEIVE_SMS`

- **Why:** the app reads incoming bank alerts to extract the amount, account,
  bank and payee.
- **Enables:** automatic transaction detection — the entire premise of the app.
- **If denied:** nothing is detected automatically. Manual entry, the dashboard,
  budgets, analytics, search and export all keep working.

The app is **not** the default SMS handler and does not consume the broadcast.
The user's messaging app receives every message exactly as before.

### `READ_SMS`

- **Why:** the one-off "import my recent bank messages" action in Settings.
- **Enables:** back-filling the last 30 days so the dashboard is not empty on
  day one.
- **If denied:** everything still works; the history simply starts from today.

This is used **only** when the user explicitly taps that action, and it reads a
bounded 30-day window rather than trawling the whole inbox.

### `POST_NOTIFICATIONS` (Android 13+)

- **Why:** a detected transaction needs to ask what it was for.
- **Enables:** one-tap categorisation from the shade, lock screen and Now Bar.
- **If denied:** detected transactions collect under "Waiting for a category" on
  the home screen. Nothing is lost, it just needs the app opened.

## Manifest-only permissions

### `RECEIVE_BOOT_COMPLETED`

Re-schedules daily housekeeping (pruning the diagnostics log) after a reboot.
Detection itself needs no restoration — the SMS receiver is manifest-declared,
so the system re-registers it automatically.

### `POST_PROMOTED_NOTIFICATIONS`

Non-runtime. Opts the categorisation prompt into **Android 16 Live Updates**, so
it can appear as a status-bar chip, on the lock screen, and in Samsung's Now Bar.
Ignored by releases before Android 16. See [SAMSUNG_FEATURES.md](SAMSUNG_FEATURES.md).

### `USE_BIOMETRIC`

Backs the optional app lock. Only ever prompted if the user turns app lock on.

## Google Play distribution

This is the constraint that shapes the whole architecture, so it is stated
plainly rather than buried.

`READ_SMS` and `RECEIVE_SMS` are **restricted permissions** under Google Play's
[SMS and Call Log Permissions policy](https://support.google.com/googleplay/android-developer/answer/10208820).
An app that is not the default SMS, Phone or Assistant handler may use them only
for a use case on Google's approved list, granted through the Permissions
Declaration Form.

**Automatic expense tracking from bank SMS is not on that list.** Developers who
have applied for it have been rejected. Assume this app **cannot be published on
Google Play in its current form**, and do not plan around getting an exception.

That is a distribution limitation, not a legal or technical one. Reading your own
SMS on your own device, with a permission you granted, is entirely legitimate.
So the app is built for:

- **Sideloading / personal builds** — the intended use. Build the debug APK and
  install it.
- **Distribution channels without Play's restricted-permission policy** — F-Droid,
  direct APK, or an enterprise/managed deployment.

### The architecture does not bet on SMS

`IngestSmsUseCase` is the only component that knows an SMS exists. Everything
downstream — duplicate detection, category learning, storage, prompting —
operates on a `ParsedTransaction`, and `TransactionSource` already distinguishes
`SMS`, `MANUAL` and `IMPORTED`.

Adding a Play-compliant ingestion source (an Account Aggregator / RBI AA feed, a
bank's own API, a user-supplied statement import) means writing one class that
produces `ParsedTransaction` and calling the same pipeline. No UI, no database
and no business logic would change. A Play-distributable build would drop the two
SMS permissions and the receiver, and lose nothing else.

**What this project does not do:** attempt to bypass Play policy, use
`BIND_NOTIFICATION_LISTENER_SERVICE` to scrape bank notifications instead,
misuse the accessibility APIs, or require root. Those are all either policy
violations, unreliable, or both.
