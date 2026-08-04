# Vendora - Point of Sale

An offline-first Android Point of Sale (POS) and inventory management app for
local shops. Built with **Jetpack Compose**, **Room Database**, and
**Supabase** (database + storage only - no Supabase Auth), tuned to run
smoothly on lower-tier devices with zero-friction barcode scanning.

Formerly "Dukaan POS" - rebranded to **Vendora**, with a fully custom
phone-number-and-password account system and a refreshed, animated UI
throughout.

---

## 🚀 Key Features

### 1. Accounts, Shops & Employees - fully custom, no Supabase Auth
- **Phone number + password**, nothing else - no email anywhere, no OTP, no
  SMS cost, ever. Supabase's own Phone auth provider actually can't be
  turned on without configuring a paid SMS gateway first (even with phone
  confirmation disabled), so Vendora doesn't use Supabase Auth *at all*.
  Registration, login, and session handling are done through a handful of
  custom Postgres functions instead (see `supabase/schema.sql`) - zero
  Supabase dashboard configuration required beyond running that one script.
- **One combined sign-up, not a multi-screen gate**: an owner enters phone +
  password, then shop details (name, owner name, address, optional GST -
  never required), then uploads one verification item (shopfront photo,
  business document, or a photo of themselves at the shop). The account is
  only actually created at the very end, once all of that is in - there's
  no half-finished "signed in but nothing set up yet" state.
- **Employees join with a permanent code, no SMS needed**: the owner gets a
  6-character shop code from Settings and shares it however they like. An
  employee signs up with their own phone + password and that code, and
  they're instantly working from the same shared inventory and sales.
- **Verification never blocks usage**: it's for you to review later (see
  below), not a gate the owner has to wait behind.
- Every product and sale is automatically backed up to **Supabase Postgres**
  after each change, shared across everyone in the shop.
- Sign in on a new device (or as a newly-joined employee) and the shop's
  inventory/sales history is pulled down automatically, only when the local
  database is empty.
- A manual **"Sync Now"** button on the Settings screen for on-demand backup.

### 2. High-Speed, Zero-Friction Scanner Module
An in-app **CameraX + Google ML Kit Barcode Scanning** pipeline, tuned for
budget Android devices (3GB+ RAM, Android 8.0+):
- **Fixed 720p Resolution** to minimize CPU/GPU load and heat.
- **10 FPS Rate Limiter** - analyzes at most one frame every 100ms.
- **Memory-Safe Frame Release** - closes `ImageProxy` frames immediately to
  avoid Out-of-Memory crashes.
- **1200ms Duplicate Debounce** to ignore accidental repeat scans.

### 3. Sell & Checkout Screen
- Full cart state management (add, decrement, delete items) with animated
  quantity changes and item transitions.
- Fast search to look up any product instantly.
- Smart checkout: Cash / UPI / Split / Khata (credit) payment modes, with
  live change-due calculation, realistic cash denominations, and a
  scrollable dialog that can't overflow off-screen.

### 4. Inventory & Stock Management
- Add products with barcode, selling price, unit (`pcs`, `kg`, `ltr`, etc.),
  and stock - with low-stock highlighting.
- Automatic stock decrement on each sale.

### 5. Khata (Customer Ledger)
- Track customer credit, dues, and payment history.

### 6. Utilities & Reports
- **QR Code Generation** (ZXing) for loose/unpackaged items.
- CSV export and printable PDF reports (QR sheets, restock orders).

---

## 🎨 What changed from the original build

- **Rebrand**: app id `com.dukaan.pos` → `com.vendora.app`, app name, launcher
  icon, splash screen, local DB name, and generated file names all updated.
- **Firebase removed, Supabase added - but not Supabase Auth**: the old
  Firebase Realtime Database backup was replaced with Supabase Postgres +
  Storage for data and file uploads, but authentication is 100% custom
  (see "Accounts" above and `supabase/schema.sql`) rather than using
  Supabase's Auth product, since its Phone provider requires a paid SMS
  gateway to even enable.
- **Shop/employee account model**: a real `shops` + `shop_accounts` +
  `shop_sessions` data model (not just one user per account), a permanent
  join code for employees, and a document/photo shop-verification flow
  collected as part of one signup, not a separate gated step afterward.
- **Visual redesign**: replaced the original dark/glass "generated UI" look
  with a light, always-on-brand emerald-and-amber palette (`ui/theme/Color.kt`),
  a bigger/bolder type scale, and a simplified checkout flow (payment method
  as clear tap targets, one scrollable dialog).
- **Animation pass**: a shared motion system (`ui/theme/Motion.kt`) drives
  consistent list-entrance, item-reorder, counter, and screen-transition
  animations across every screen (Sell, Inventory, History, Ledger,
  Settings).
- **Toolchain bump**: Kotlin 2.2.0, Compose BOM 2026.06.00, compileSdk/
  targetSdk 36, minSdk raised 24 → 26 (required by the Supabase Kotlin SDK).

---

## 🛠️ Architecture & Tech Stack

- **UI Framework**: Jetpack Compose (Material 3) with a shared motion system
- **Local Database**: Room (offline-first source of truth)
- **Cloud Backend**: Supabase - `postgrest-kt` for data + RPC calls,
  `storage-kt` for verification photo/document uploads. **No `auth-kt`, no
  Supabase Auth** - see the security note below.
- **Camera Pipeline**: Jetpack CameraX & Google ML Kit Barcode Scanning
- **Image Loading**: Coil 3, for the verification-photo preview
- **Build System**: Gradle 8.13 with the Gradle Wrapper included

### ⚠️ A real security trade-off, worth understanding
Supabase Auth (even just its free Email provider) is what normally lets Row
Level Security automatically keep one shop's data invisible to another,
because RLS checks the identity in a cryptographically-signed session token
that Supabase itself issues. Since Vendora doesn't use Supabase Auth at all,
that automatic protection isn't available - so instead:
- **Every real table** (`shops`, `shop_accounts`, `products`, `sales`) has
  Row Level Security **enabled with zero policies attached**, meaning the
  standard Postgrest REST endpoints for these tables always return nothing
  / accept nothing, for anyone, always - direct table access is a dead end
  by design.
- **All actual reads/writes go through Postgres functions** (`register_shop`,
  `login_shop_account`, `get_products`, `replace_products`, etc.) that
  independently verify a session token before touching any data. This is a
  legitimate pattern, but it means Vendora's security now depends entirely
  on those functions being correct, not on Supabase's well-tested RLS/JWT
  system.
- **Session tokens are long-lived** (365 days) and stored in plain
  `SharedPreferences` on-device, standing in for what Supabase Auth's SDK
  would otherwise manage. This is broadly consistent with how most apps
  store auth tokens, but it's still worth knowing there's no
  automatic-refresh/short-lived-token safety net here.
- **The verification-upload Storage bucket accepts anonymous uploads**,
  since a brand-new registration needs to upload its photo before an
  account (and therefore a session token) exists yet. Paths are random
  UUIDs, not guessable, but the bucket isn't locked down by identity.

None of this is unusual for a small-scale app built this way, but it's a
real, deliberate trade-off against Supabase's built-in security - go in
with eyes open, especially if this app ever handles more sensitive data.

---

## ⚙️ Setup

### 1. Create your Supabase project
1. Go to [supabase.com](https://supabase.com) and create a new project (the
   free tier is plenty for this app).
2. Open **SQL Editor → New query**, paste the contents of
   [`supabase/schema.sql`](supabase/schema.sql), and run it. This creates
   every table, function, and the `shop-verification` Storage bucket.
   **That's it - no Authentication dashboard configuration at all.**
3. Open **Project Settings → API** and copy your **Project URL** and
   **anon / publishable key**.

### 2. Reviewing shop verifications
There's no admin dashboard built for this yet - review happens directly in
Supabase:
1. **Table Editor → shops**: see every shop's `verification_status`
   (`pending` / `verified` / `rejected`), `verification_method`, and
   `verification_file_path`.
2. **Storage → shop-verification**: browse to the path in
   `verification_file_path` (a random folder name, e.g.
   `unregistered/3f9a.../verification.jpg`) to view the uploaded file.
3. Edit the `verification_status` cell directly in Table Editor to
   `verified` or `rejected` once you've checked it.

### 3. Configure the Android project
Copy `local.properties.example` to `local.properties` (same folder) - Android
Studio usually creates `local.properties` for you already with `sdk.dir` set,
so just add these two lines to your existing file:

```properties
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-anon-public-key
```

`local.properties` is already git-ignored, so your keys never get committed.

### 4. Open & build
Open the project root in Android Studio (Ladybug or newer), let Gradle sync,
then run on a device or emulator (**API 26+**). Or from the command line:

```bash
# Set Java Home if your system default is older than Java 17
export JAVA_HOME=/path/to/jdk-17-or-newer

./gradlew assembleDebug
```

The compiled APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Prerequisites
- **Android SDK**: API Level 36 (compileSdk) / API Level 26 (minSdk)
- **Java Runtime**: JDK 17 or newer

---

## 👥 How the owner/employee flow works

1. **Owner registers**: phone + password → shop name / owner name / address
   / optional GST → uploads one verification photo or document → account is
   created and they land in the app immediately. Verification review
   happens later, on your side, and never blocks them.
2. **Owner shares their shop code**: Settings screen shows a permanent
   6-character code (e.g. `7F3KQ9`) under the Shop section.
3. **Employee signs up**: phone + password + that shop code → immediately
   linked to the same shop, sees the same inventory/sales, no verification
   step (only owners submit that).
4. **Anyone signs back in**: just phone + password from then on, whether
   they're the owner or an employee, on any device.
