# Smart Notes

A simple, Google Keep-style notes app for Android with **automatic calculation**.

## Features
### Calculation (Menu → Calculation for settings)
Each note has a mode: **Auto**, **Manual** or **Off** (app default: Auto).

- **Line math**: end a line with `=`. Words are skipped.
  `rice 50 * 2=` → `100` · `egg 10 + beef 10 =` → `20` · `rice 50*2 taka =` → `100` · `১০+৫ =` → `১৫`
- **Lists** (Auto): a word before `=` works on the numbers above it.
  ```
  My budget 30
  Bazar 1:
  Egg 10
  beef 10
  total = 10 + 10 = 20
  remaining = 30 - 20 = 10
  ```
  - `total`, `sum`, `মোট`, `যোগ` → **+** (lines since the last heading)
  - `remaining`, `left`, `balance`, `বাকি` → **−** (first number minus the rest, back to the last blank line)
  - any other word or a plain `=` → the symbol you choose in settings (default +)
  - add your own words with + − × ÷ in settings
  - a heading ending in `:` is never counted; a blank line starts a new list
  - each line counts its **last number**; counted numbers show in colour
  - put the cursor on a coloured number and tap **Leave … out** (or **Add … back**)
- **Manual**: type `total =`, tap the numbers you want (`+` is added for you, or type `-`), then type `=` or press **Done**.
- **Unit conversion**: `5 km to m =`, `100 c to f =`, `2 kg to lb =`, `1 bigha to katha =`

### Notes
- Bangla + English (Menu → Language)
- Checklists, pin, colours, labels
- Search, Archive, Trash (deleted after 7 days) with Undo
- Share as image, PDF or text, keeping colour and layout
- Backup / Restore to a file (Menu → Back up notes)
- Offline only; ready for a later login + cloud sync

## Get the APK (no Android Studio needed)
1. Create a new GitHub repo and upload this folder (keep the `.github` folder).
2. Open the repo's **Actions** tab. The "Build APK" workflow runs on every push (or press *Run workflow*).
3. When it finishes, download **SmartNotes-apk** from the run page, unzip, and install
   `app-release.apk` on your phone (allow "install unknown apps").

## Or build in Android Studio
Open the folder → wait for Gradle sync → Run ▶.
