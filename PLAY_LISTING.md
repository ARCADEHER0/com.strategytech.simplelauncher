# Google Play Console: copy-and-paste guide

Replace anything in [SQUARE BRACKETS] before submitting.

## Store listing

**App name** (max 30 characters)
Senior Simple Launcher

**Short description** (max 80 characters)
Big buttons, big clock, easy calls and texts. A simple home screen for seniors.

**Full description** (max 4000 characters)

Senior Simple Launcher turns your Android phone into a calm, easy-to-use home screen with big buttons, big text, and nothing confusing.

WHAT YOU GET
• A large, bright clock and date, always on screen
• Four giant buttons: Phone Call, Messages, Grandkids Photos and Emergency Help
• "My Apps": a shelf where a family member can put the few apps you really use
• Emergency Help opens your phone's dialer with your local emergency number, or a family member's number, already typed in
• Adjustable text size: Normal, Large or Extra large
• Settings are hidden behind a press-and-hold gear, so nothing changes by accident

SET UP IN A MINUTE
Install, press Home, and choose Senior Simple Launcher as your home app. A family member can then add an emergency contact and favorite apps in Settings.

PRIVATE BY DESIGN
No account. No ads. No tracking. No internet access. Your settings stay on your phone.

IMPORTANT
Emergency Help is a shortcut to your phone's dialer. You still press the green call button to place the call. It is not a monitored alert service and it does not send your location. If you cannot use the phone in an emergency, call your local emergency number directly.

**Category:** Personalization (Apps)
**Tags:** Launcher, Accessibility (pick what Play offers)
**Contact email:** [YOUR EMAIL]
**Website (optional):** [YOUR SITE]
**Privacy policy URL:** [PUBLIC URL WHERE YOU HOSTED store/PRIVACY_POLICY.md]

## Graphics you must upload

| Asset | Requirement | Status |
|---|---|---|
| App icon | 512 x 512 PNG | Provided: `store/icon-512.png` |
| Feature graphic | 1024 x 500 PNG or JPEG, no transparency | You need to make this |
| Phone screenshots | At least 2, JPEG or PNG, 16:9 or 9:16, each side 320 to 3840 px | Take them from the emulator or your phone; they must show the real app |

Take the screenshots of the real running app. Play rejects listings whose screenshots
misrepresent the app, so do not use the old HTML mockup.

## App content section (Policy > App content)

| Question | Answer |
|---|---|
| Privacy policy | Your hosted URL |
| Ads | No, the app contains no ads |
| App access | All functionality is available without special access, login or credentials |
| Content rating | Fill in the questionnaire honestly: no violence, no user-generated content, no sharing of location. Expect "Everyone". |
| Target audience | Adults only (18+). Do **not** pick child age groups, or you enter the Families policy. |
| News app / COVID-19 / Government / Financial / Health | No / No / No / No / No (the Emergency button is a dialer shortcut, not a health or medical feature) |

## Data safety form

The app, as written, never sends anything off the device (it has no INTERNET permission).
Google defines "collected" as transmitted off the device, so:

- Does your app collect or share any of the required user data types? **No**
- The emergency contact name and number are stored only in private on-device storage.
- Security practices: "data is encrypted in transit" does not apply because nothing is transmitted. For data deletion, uninstalling the app removes everything it stored.

If you ever add analytics, crash reporting, ads, cloud backup or any network feature, the
answers change and you must update this form before shipping that version.

## Permissions

The manifest requests **no permissions**. Nothing to declare. Do not add
`QUERY_ALL_PACKAGES`, `CALL_PHONE` or `SEND_SMS` without a strong reason, because Play
restricts them and needs a declaration form for each.

## Launcher-specific policy checklist

- The app only becomes the Home app when the user chooses it in the Android prompt. It never changes this silently.
- The description plainly says the Emergency button is a dialer shortcut, not a monitored service.
- Do not use Windows, Microsoft, Android-robot or any other company's logos or names in the icon, title or screenshots. (The old prototype's window emoji was removed for this reason.)
- Do not claim medical, safety or fall-detection features.

## Release steps

1. Create a Play Console developer account (one-time registration fee) and verify your identity.
2. Create the app. Choose App, Free, and accept the declarations.
3. Complete Store listing and App content above.
4. Upload `app-release.aab` to a **Closed testing** track and add testers.
5. **If your developer account is a personal account created after 13 November 2023**, Google
   requires a closed test with **at least 12 testers opted in continuously for 14 days**
   before you can apply for production access. Plan for about two weeks. Organization
   accounts are handled differently.
6. When eligible, apply for production access, then create a Production release with the same bundle (or a newer one) and send it for review.
7. First reviews can take several days.

Requirement check: new apps must target Android 16 (API 36). This project already does.
