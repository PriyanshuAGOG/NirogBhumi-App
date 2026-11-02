# Getting 5 to 7 people testing: your steps, and what to send them

Contact address for everything below: **priyanshu@nirogbhumi.com**.

How testers get the app: **Firebase App Distribution**. You send each tester one invite link. They open it on their
Android phone, sign in with any Google account, and install the app. Every new build is delivered to them
automatically, with an email and a notification. No file sharing, no "install unknown apps" fiddling beyond a one-time
prompt, and nothing public: only people in the group can download it.

---

## Part A. What you do (about 20 minutes, once)

### A1. Make the tester key and register it (fixes phone sign-in)

Phone-number sign-in checks that the app really is the one Firebase knows. The earlier test builds were signed with a
shared key that was deliberately never registered, so Firebase refused the SMS ("reCAPTCHA checks were unsuccessful").
This step creates a private key, registers it, creates the tester group and stores the key for the build robot.

In **Google Cloud Shell**, signed in as `priyanshu@nirogbhumi.com`:

```bash
curl -fsSL https://raw.githubusercontent.com/PriyanshuAGOG/NirogBhumi-App/claude/firebase-setup-apk-build-s8b1ol/scripts/setup_tester_release.sh -o setup_tester_release.sh && bash setup_tester_release.sh
```

It will ask you to sign in to GitHub once (open the link it prints, paste the code). At the end it prints "Done".

**Back up two files** from Cloud Shell (three-dot menu, Download): `nirog-tester.keystore` and
`nirog-tester-secrets.txt`. Keep them in your password manager. If they are lost, every tester has to uninstall and
reinstall the app once.

If the script prints an error, paste it to me.

### A2. Start the first signed build

In GitHub: Actions > *Build Firebase-connected debug APK* > Run workflow (branch
`claude/firebase-setup-apk-build-s8b1ol`, or `main` once merged). It takes about 3 minutes. The run's log should say
**"Signing with the private tester key."** (If it shows a yellow warning instead, A1 did not finish.)

### A3. Turn on the invite link

Firebase Console > **App Distribution** > *Testers & Groups* > group **Nirog Bhumi testers** > **Invite link** > create
it, and turn it on. Copy the link. (If the group is missing, add it with the ID `nirog-testers`.) You can also add
people by email on the same page.

### A4. Get a batch access code ready

Testers should see the Care+ part too. In the console: *Programs*, open a batch, *Generate code*, set the maximum uses
to the number of testers. They enter it in the app under Care.

### A5. Check phone sign-in yourself before inviting anyone

1. Uninstall any older Nirog Bhumi test build from your phone (the signing key changed, so Android will not update over
   it). Your data is in the cloud, so signing in again brings it back.
2. Install from the invite link.
3. Sign in with your phone number. The SMS should arrive within a minute.

If it still fails, send me the **exact message on the screen** (it is now plain language) and, if you can, the log line
tagged `NirogAuth` (Android Studio Logcat, or `adb logcat -s NirogAuth`). Also check these three console settings:

- Authentication > Sign-in method > **Phone** is enabled.
- Authentication > Settings > **SMS region policy** allows India (+91).
- The project is on the **Blaze** plan (Spark limits real SMS to a handful a day).

**Fallback that always works for testers:** email and password sign-in is on the same screen. For people whose SMS
does not arrive, tell them to use it. You can also add *phone numbers for testing* (Authentication > Sign-in method >
Phone) which sign in with a fixed code and send no SMS: good for the first run-through, not for real testing of SMS.

### A6. Send the invite

Copy the message in Part B, put in the invite link and the batch code, and send it to your 5 to 7 people.

### A7. Collect what they find

- Their messages and emails to priyanshu@nirogbhumi.com.
- Console > *Error Reports*: problems testers report from inside the app.
- Firebase Console > Crashlytics: crashes, with the phone model.
- App Distribution shows who installed which build.

Send me anything that breaks and I fix it; a fixed build reaches every tester on its own.

---

## Part B. Message to send to testers (copy and paste)

> Hi! Thank you for helping us test **Nirog Bhumi**, our health companion app (for tracking blood sugar, blood pressure,
> weight, sleep and walking, with coach support). It is an early test version, so please expect rough edges. That is
> exactly what we want to hear about.
>
> **Install (Android phone, 5 minutes)**
> 1. Open this link on your phone: **[INVITE LINK]**
> 2. Sign in with any Google account and accept the invitation.
> 3. Tap **Download** (or install the "App Tester" app if it asks), then **Install**. If Android asks, allow installs
>    from this source once.
> 4. Open Nirog Bhumi. Sign in with your phone number (an SMS code is sent) or with email.
> 5. In **Care**, enter this batch code to join the coaching group: **[BATCH CODE]**
>
> **Please try (about 20 minutes, over a few days is fine)**
> - Sign up, fill in your profile and accept the privacy choices.
> - Add a blood sugar reading (fasting and after a meal), blood pressure, weight, and sleep. Look at Today, Track and
>   Insights afterwards. Do the numbers and wording make sense?
> - Open **Health File**: switch between Weekly and Monthly, and try sharing the report or the link.
> - **Explore**: read an article, try the search and the topic filters.
> - **Care**: send your coach a question, and look in the batch chat.
> - If you use Health Connect (Samsung Health, Google Fit, etc.): Profile > Devices > Health Connect, then review and sync steps.
> - Anything confusing, slow, ugly, or in words you did not understand.
>
> Please use made-up or real health numbers as you prefer, but know that this is a test and your entries may be reset.
>
> **Tell us what you found**: just reply to this message or write to priyanshu@nirogbhumi.com. A screenshot plus one
> line ("I was on the Track screen and tapped X, expected Y, got Z") is perfect. Good things too!
>
> Thank you!

---

## Sign-in settings only you can change (Firebase Console)

**1. The SMS wording.** The text members get today reads "123456 is your verification code for
nirogbhumi.app.firebaseapp.com". To change it:

- Authentication > **Templates** > **SMS verification** > edit (pencil). Set the message to:

  > Thank you for joining Nirog Bhumi, your diabetes wellness companion. Your OTP is %LOGIN_CODE%. Please do not share it with anyone.

  Keep `%LOGIN_CODE%` exactly as written: Firebase replaces it with the 6-digit code.
- Project settings (gear) > General > **Public-facing name**: set it to `Nirog Bhumi`, so any place Firebase names the
  app, it says that and not the web address.

If the console does not let you edit the text, the project may need *Identity Platform* turned on (Authentication shows a
button for it; it is free at this scale). Indian mobile networks also require SMS text to be pre-registered (DLT), so
Google may keep its own wording for Indian numbers whatever you type. If that happens, the "Public-facing name" change is
the part that still applies. Tell me what you see and I will adjust the app side.

**2. Why a "confirm you're human" web page appears.** Google checks that the app asking for an SMS is genuine. On a build
installed from a link (not from the Play Store) that silent check cannot pass, so Google falls back to a quick web
check. Two things reduce the wait: tester builds now go straight to the web check, and **builds installed from the
Play Store do the check silently with no web page at all**. For the smoothest first run, move testers to a Play Store
*internal testing* track (steps in `docs/CLOSED_TEST_AND_ROLLOUT.md`). Also make sure **Play Integrity API** is enabled:
Google Cloud Console > APIs & Services > Library > "Play Integrity API" > Enable.

**3. Developer settings.** The screen is now visible only when signed in with priyanshu@nirogbhumi.com or
gk@nirogbhumi.com **and** that email is verified. Sign in with Google using that address, or verify the email from the
message Firebase sends, to see it.

---

## Notes

- **Each build replaces the last.** Testers get an email and a notification when a new build is ready; they tap and
  update. The app also checks for updates itself.
- **Only Android.** There is no iPhone build.
- **Real data.** Testers use the real production project, so what they enter is real stored data governed by the same
  privacy policy. Tell them it is a test, and that they can delete their account in Profile > Export or delete my data.
- **Make the GitHub repository private** before wider testing (Settings > General > Danger Zone). While public, anyone
  can download the build artifacts attached to workflow runs.
- This is a *testing* channel. Play Store internal and closed tracks (`docs/CLOSED_TEST_AND_ROLLOUT.md`) are the next
  step and need the Play signing steps in `docs/GO_LIVE_GUIDE.md`.
