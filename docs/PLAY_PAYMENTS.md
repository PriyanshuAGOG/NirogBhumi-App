# Google Play payments classification

Play requires **Google Play Billing** for digital goods and features unlocked inside an app (subscriptions, premium
content, coins, unlocks). **Physical goods and real-world services** may use other payment methods. This page says
which category each Nirog Bhumi feature is in, so nobody ships a payment flow the policy does not allow.

| Feature | What is sold or given | Category | Payment path |
|---|---|---|---|
| Tracking, insights, trends, Health File, reminders | Free | Not a purchase | none |
| Care+ program (batch, coach, calendar, chat, plans) | Joined with a **code from the coach** (free in the app; any fee is agreed with the coach offline) | Not sold in the app | none in the app. Do **not** add "Buy Care+" without Play Billing |
| Consultation with an expert | A real-world service with a person (video/phone/clinic) | Service | Fee is arranged by the team; **the app takes no payment** (request, confirm, reschedule only) |
| Articles (Learn) | Free editorial content from nirogbhumi.com | Free | none |
| Store page (kits, tools) | **Physical goods** sold on nirogbhumi.com | Physical goods | Website checkout, opened only in the locked-down in-app page or the browser (STORE_URL). Not launched: the Learn tab shows "coming soon" until a store address is configured for the build |
| Data export / deletion | Privacy rights | Free | none |

## Rules for changes

- Any new **digital** paid feature (premium insights, paid content, paid subscription, in-app coins) needs Google Play
  Billing and a Play Console product. Until that exists, it must not be sold or unlocked in the app.
- The in-app store page may sell **physical goods only**. Its checkout must stay on our own https host; other hosts open
  in the browser (see `UrlPolicy`). Gift cards, digital downloads and subscriptions are not physical goods.
- Tell Play about the store and consultations in the Console "Payments / Monetization" declarations: paid features =
  physical goods and services only.
- No Razorpay/UPI keys in the app (there are none today; `docs/RELEASE_CHECKLIST.md` keeps secrets in Firebase secret management).
