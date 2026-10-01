# Real-device QA matrix and acceptance scripts

Automated tests cover logic, rules, functions and screens in isolation (see `docs/PRODUCTION_READINESS_V2.md`). What they
cannot cover is a real phone, a real network, real SMS and real Health Connect. This is the checklist for that. Record
the result of each row (pass / fail / not run) with the build number from Settings > Developer (version and commit).

## Devices (minimum)

| # | Device class | Android | Why |
|---|---|---|---|
| 1 | Low-end phone, 2-3 GB RAM, small screen (360x640 dp) | 8.0 (API 26, our minimum) | layout, memory, slow storage |
| 2 | Mid-range phone | 12-13 | most common |
| 3 | Current flagship or Pixel | 15-16 (API 35-36, our target) | edge-to-edge, predictive back, photo picker |
| 4 | Tablet or foldable (unfolded) | any recent | wide layouts |
| 5 | Phone with a Hindi system language and font scale 1.3 | any | text expansion, number formatting |
| 6 | Phone with Health Connect and a real watch/band or the Health Connect test data | 14+ (built in) or 9-13 with the app | import path |

## Auth acceptance (run on devices 2 and 3; a fresh install each time)

1. Phone OTP: correct code; wrong code (clear message, retry allowed); resend; airplane mode during send (clear error, no crash); number of another country.
2. Email + password: sign up, sign in, wrong password, password reset email arrives, reset works.
3. Google sign-in: new and existing account; cancel at the chooser.
4. Process death: start sign-in, kill the app from recents, reopen: no crash, lands on the right step.
5. Sign out, sign in as a different member: no data from the first member is visible (Today, Track, Health File, chat).
6. Account deletion: typed phrase, wrong phrase (button stays off); password/OTP confirmation; banner with the date on every tab; "Keep my account" clears it; export still works while a deletion is pending.

## Health data (devices 2, 3, 6)

- Log sugar, BP, weight, sleep, walk; each appears on Today, Track, the metric screen, Insights/Trends and the Health File **without leaving and re-entering the app**.
- Edit a just-logged reading (inside 60 minutes) on every metric; wait until 60 minutes have passed (changing the phone's clock does not help: the server decides) and confirm Edit is gone on that row, and that a dialog left open past the limit answers "can no longer be changed" when saved.
- Sleep: 10:30 PM to 6:30 AM across midnight = 8h; same start and end is refused with a plain message; wake time in the future refused; a nap of 30 minutes accepted; change the phone to 24-hour time and confirm the app still shows AM/PM.
- Offline: turn on airplane mode, log a reading, see it appear; reconnect, it stays; open Learn: saved articles with the "You're offline" line.
- Health Connect: grant permissions, sync twice; one step total per day, weight shows as kg, no duplicate entries; imported rows have no Edit button and explain why.
- Time zone change (travel): "today" follows the phone; cross midnight with the app open: "checked in today" resets.

## Export and privacy

- Request an export on an email account: "Preparing..." then "Ready"; Download opens a ZIP; it contains `data.json`, CSVs and README; the email arrives (after the owner installs the mail extension) and its link works, then stops working after 3 hours; "Make a fresh export" respects the one-per-hour limit with a readable message.
- Request an export on a phone-only account: no email is promised; the in-app notice arrives.
- Support message: arrives in the support inbox (after the owner sets `SUPPORT_EMAIL`).
- Privacy notice banner: on an account that accepted `2025-07`, the "updated privacy notice" banner appears; "I agree" makes it go away and adds a receipt.

## WebView and link security (only if a store address is configured for the build)

Open the store page, then try each from a page you control or by editing the store page in a test environment:
1. A link to `http://nirogbhumi.com` (plain http) is refused. 2. `https://nirogbhumi.com.evil.example` and `https://nirogbhumi.com@evil.example` open in the **browser**, never in the app. 3. `intent://`, `market://`, `upi://`, `file://`, `javascript:` do nothing. 4. A certificate error page shows "couldn't load", never a "proceed anyway". 5. A file download link does nothing. 6. A page calling `window.open` does not open a second window. 7. Closing the screen and reopening shows no previous page or cookies from the last visit that should be gone.

## Accessibility (TalkBack on device 2; font scale 1.3 and 2.0; dark mode)

- Every tappable control is reachable and has a name; 48 dp touch targets; charts expose a list version; the pending-deletion and privacy banners are announced; colour is never the only signal for high/low/in range.
- Font scale 2.0 on Today, Track, the check-in, the sleep dialog, Data controls: nothing clipped or unreachable.

## Performance and stability

- Cold start to Today under 3 s on device 1 after the first launch; scrolling Track and Insights without jank.
- Leave the app on Today for 10 minutes, then check Firestore usage in the console: listeners are bounded per collection (see `HealthDataStore`) and do not multiply when switching tabs or returning from another screen.
- Rotate the phone on the check-in and the sleep dialog: typed values survive.
- Play pre-launch report and Android Vitals (ANR, crash) clean on the internal track before widening.
