# LBSCEK Attendance Widget

A native Android home-screen widget that logs into LBSCEK's Etlab portal
(`lbscek.etlab.app`) and shows your live attendance percentage, refreshed
automatically a few times a day.

## How it works
- **Login**: replays Etlab's own login form (`POST /user/login`) with your
  username/password.
- **Scrape**: fetches `/ktuacademics/student/viewattendancesubject/{semester}`
  and parses the attendance table with Jsoup.
- **Storage**: credentials + last result are kept in `EncryptedSharedPreferences`,
  on-device only.
- **Refresh**: a WorkManager job re-fetches every 3 hours; you can also tap
  "Refresh now" on the widget itself.
- **Widget**: built with Jetpack Glance (Compose for App Widgets).

## Setup
1. Open this folder in **Android Studio** (Giraffe or newer). It will
   generate the Gradle wrapper automatically on first sync.
2. Let Gradle sync — it needs internet access to `google()` and
   `mavenCentral()` the first time, to pull dependencies.
3. Run the app on a device/emulator (min SDK 26).
4. Enter your Etlab username/password and your current semester, tap
   **Save & fetch attendance**.
5. Long-press your home screen → Widgets → "LBSCEK Attendance" → drag it on.

## Known limitations / things to check
- The app icon uses a placeholder system icon (`@android:drawable/sym_def_app_icon`)
  — swap in your own via Android Studio's Image Asset tool if you want a
  custom launcher icon.
- The HTML scraping selectors (`table.items`) match the standard Etlab/Etuwa
  layout used by the reference `rit-etlab-api` project. If LBSCEK's instance
  renders anything differently, open the attendance page's HTML source and
  compare it against `EtlabRepository.parseAttendance()` — you may need to
  tweak the parsing logic slightly.
- If Etlab ever adds CAPTCHA or 2FA to login, this scraping approach will
  break (this is unofficial and not affiliated with Etlab/Etuwa Concepts).
- Semester is currently a single number you set once; if you want it to
  auto-advance each semester, that'd need a small addition.

## Project layout
```
app/src/main/java/in/lbscek/attendance/
  data/     EtlabRepository (login+scrape), AttendancePrefs (encrypted storage), models
  work/     AttendanceWorker (periodic background refresh)
  widget/   AttendanceWidget (Glance UI), AttendanceWidgetReceiver
  ui/       MainActivity (setup screen)
```
