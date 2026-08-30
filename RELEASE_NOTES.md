<div align="center">

# دوبله پارسی — Doble Parsi V1.0.0

**دوبلهٔ زندهٔ صدای گوشی با هوش مصنوعی، با خروجی صوتی و زیرنویس هم‌نام ویدئو**
_AI live dubbing for anything playing on your phone, with audio and subtitles named after the video_

Android 10+ (API 29) · فارسی / English · ساخته‌شده توسط [QW-AI-Code](https://github.com/QW-AI-Code)

</div>

---

## کدام فایل را دانلود کنم؟ / Which APK?

| فایل / File | برای چه / For |
|---|---|
| `DobleParsi-1.0.0-universal.apk` | همهٔ گوشی‌های اندروید ۱۰+ با هر معماری / every Android 10+ phone, any ABI |

**فقط یک APK، و دلیلش:** در ریلیز قبلی هر سه فایل دقیقاً ۴۱٫۸ مگابایت بودند. این اپ
هیچ کتابخانهٔ بومی (`.so`) ندارد، پس تفکیک ABI چیزی برای جدا کردن نداشت و هر سه APK
کپی بایت‌به‌بایت هم بودند. حالا یک APK یونیورسال منتشر می‌شود و حجم واقعی با R8 و حذف
منابع بی‌استفاده کم شده است.

**One APK, and why:** in the previous release all three files were exactly 41.8 MB.
The app ships no native libraries at all, so the ABI splits had nothing to strip and
each one was a byte-for-byte copy. A single universal APK is published now, with its
real size cut by R8 + resource shrinking.

چک‌سام‌ها در `SHA256SUMS.txt` همین ریلیز است. / Checksums are in `SHA256SUMS.txt`.

---

<div dir="rtl">

## فارسی

### آموزش سریع
۱. APK را نصب کن (نصب از منابع نامشخص را روشن کن).
۲. اپ را باز کن و کلید API جِمینای را از [Google AI Studio](https://aistudio.google.com/apikey) بگیر و وارد کن؛ کلید فقط روی همین گوشی و در حافظهٔ خصوصی اپ می‌ماند.
۳. زبان دوبله را انتخاب کن (پیش‌فرض فارسی، ۳۱ زبان).
۴. **شروع دوبله** را بزن و اجازه‌ها را بده: **میکروفون** و **ضبط صدای دستگاه** («Start now»)؛ برای دکمهٔ شناور هم **نمایش روی برنامه‌های دیگر**.
۵. برو در یوتیوب/تلگرام/مرورگر و ویدئو را پخش کن؛ دوبله همان لحظه پخش می‌شود.
۶. پایان: دکمهٔ شناور، یا **پایان** در اعلان، یا **پایان دوبله** در خود اپ.
۷. خروجی‌ها در `Download/Doble Parsi/<نام ویدئو>/` ذخیره می‌شوند.

> برای این‌که فایل‌ها **هم‌نام ویدئو** ذخیره شوند، یک بار دسترسی **«اعلان‌ها»** را برای
> دوبله پارسی روشن کن؛ نام ویدئوی درحال‌پخش در اندروید فقط از این راه خوانده می‌شود.

### دسترسی‌ها
| دسترسی | برای چه |
|---|---|
| میکروفون | شرط سیستم برای ضبط صدای پخش‌شده؛ صدای میکروفون تو ضبط و ارسال نمی‌شود |
| ضبط صدا/تصویر دستگاه | گرفتن صدای درحال‌پخش گوشی (MediaProjection) |
| اعلان | کنترل جلسه در نوار اعلان |
| نمایش روی برنامه‌های دیگر (اختیاری) | دکمهٔ شناور |
| دسترسی اعلان‌ها (اختیاری) | خواندن نام ویدئو برای نام‌گذاری فایل‌ها |

### تازه‌های این بیلد (نسخه بدون تغییر: ۱.۰.۰)
- **دکمهٔ شناور حرفه‌ای**: بدنهٔ شیشه‌ای با حلقهٔ گرادیان چرخان و کمان ثانیه‌شمار؛ هنگام
  دوبله پنج میله داخل دکمه با **بلندی واقعی صدای دوبله** حرکت می‌کنند (انگار یک نفر
  دارد حرف می‌زند) و هالهٔ نبض‌دار دور دکمه ظاهر می‌شود.
- **تایمر ضبط دوبله روی دکمهٔ شناور** (دقیقه:ثانیه، با ارقام زبان رابط).
- **دکمه بعد از توقف دوبله از بین نمی‌رود**: بار اول با زدن «شروع دوبله» ظاهر می‌شود و
  بعد از پایان سرِ جایش می‌ماند؛ برای ویدئوی بعدی همان دکمه را بزن تا دوبلهٔ جدید شروع
  شود. لمس طولانی، دکمه را تا شروع بعدی حذف می‌کند.
- **تنظیمات تازهٔ دکمه**: ماندن/رفتن بعد از پایان، نمایش تایمر، کم‌رنگ شدن، جمع شدن در
  لبهٔ صفحه، جابه‌جایی خودکار به نزدیک‌ترین لبه، حالت هوشمند و اندازهٔ دکمه.
- **دوبله دقیقاً همزمان با صدای اصلی شروع می‌شود** (بافر ۶۰ میلی‌ثانیه، تاخیر عمدی صفر،
  با امکان تنظیم دستی) و **صدای اصلی خیلی کم می‌ماند** (ducking در کل جلسه + سهم ۱۸٪
  صدای اصلی در فایل خروجی، قابل تنظیم).
- **مانیتور مصرف توکن API جِمینای**: مصرف امروز، ورودی/خروجی، توکن صوتی، تعداد
  نوبت‌ها و جلسه‌ها، سقف روزانه و باقی‌مانده، زمان بازنشانی (نیمه‌شب به وقت پسیفیک)،
  نمایش خطای سهمیهٔ ۴۲۹ با زمان تلاش مجدد، تاریخچهٔ روزهای قبل و لینک داشبورد رسمی
  گوگل. اعداد از بلوک `usageMetadata` خودِ Gemini Live خوانده می‌شود؛ گوگل API رسمی
  برای «باقی‌ماندهٔ سهمیه» ندارد، پس عدد تقریبی است و مرجع، داشبورد گوگل است.
- **کارت سازنده و کپی‌رایت**: آیکون رسمی گیت‌هاب با انیمیشن (حلقهٔ چرخان، نبض و چرخش با
  لمس)، لینک [github.com/QW-AI-Code](https://github.com/QW-AI-Code)، متن کپی‌رایت رسمی،
  امضای رندوم در هر اجرا و هدر کپی‌رایت روی همهٔ فایل‌های سورس.
- **یک APK یونیورسال به‌جای سه APK هم‌حجم** + R8 و حذف منابع بی‌استفاده + گزارش حجم و
  تعداد کتابخانه‌های بومی در خلاصهٔ هر بیلد.

### چه چیزی در ۱.۰.۰ درست و اضافه شد
- **رابط کاربری کامل بازطراحی شد**: دارک سورمه‌ای حرفه‌ای، گرادیان برند از روی آیکون اپ، کارت‌بندی تازه و رنگ صریح روی هر متن؛ باگ «نوشته‌های مشکی و دیده‌نشدن متن» ریشه‌ای حل شد.
- **آیکون رسمی برنامه** در همهٔ چگالی‌ها، آیکون تطبیقی و مونوکروم اندروید ۱۳، و همان آیکون در هدر اپ (به‌جای آیکون عمومی قبلی)، اعلان و دکمهٔ شناور.
- **سوییچ زبان**: انگلیسی حالا کامل LTR و کامل انگلیسی است، حتی متن اعلان‌ها.
- **اعلان «در حال ذخیره» دیگر فریز نمی‌شود**: پایان جلسه تایم‌باکس و واچ‌داگ دارد، اعلان همیشه پاک می‌شود و یک اعلان «خروجی‌ها ذخیره شد» با فهرست فایل‌ها می‌آید.
- **نام فایل‌ها هم‌نام خودِ ویدئو**: عنوان در طول جلسه رصد و در پایان تثبیت می‌شود.
- **یک APK یونیورسال** در هر ریلیز + `SHA256SUMS.txt` + بررسی امضا با `apksigner`.
- امضای ثابت با کیستور اختصاصی (راهنما در ردمی) تا آپدیت‌ها روی نسخهٔ قبلی نصب شوند.

### محدودیت‌ها
اندروید ۱۰ و بالاتر. تماس صوتی/تصویری (`VOICE_COMMUNICATION`) و اپ‌های DRM مثل
نتفلیکس طبق تصمیم سیستم قابل ضبط نیستند. خروجی صوتی `m4a/AAC` است.

### امنیت
ترافیک فقط با TLS و فقط به `generativelanguage.googleapis.com`؛ بدون تلمتری،
آنالیتیکس و تبلیغ؛ کلید API در حافظهٔ خصوصی اپ و بیرون از بکاپ؛ بدون کد بومی و
بدون SDK ثالث (به‌جز okhttp و Compose). ممیزی کامل در ردمی.

</div>

---

## English

### Quick start
1. Install the APK (allow installs from unknown sources).
2. Open the app, paste a Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey). It never leaves the device.
3. Pick the dub language (Persian by default, 31 available).
4. Hit **Start dubbing** and grant **microphone** + **device audio capture** ("Start now"); also **display over other apps** if you want the floating bubble.
5. Play something in YouTube/Telegram/a browser. The dub plays in real time.
6. Stop from the bubble, the notification, or **Stop dubbing** in the app.
7. Output lands in `Download/Doble Parsi/<video name>/`.

> For files **named after the video**, turn on **notification access** once: on
> Android that is the only way to read the title of what is playing.

### Permissions
| Permission | Why |
|---|---|
| Microphone | the OS gate for playback capture; your mic audio is never recorded or sent |
| Device audio/screen capture | taps the phone playback (MediaProjection) |
| Notifications | session control in the shade |
| Display over other apps (optional) | floating bubble |
| Notification listener (optional) | reads the media title to name files |

### New in this build (version unchanged: 1.0.0)
- **Professional floating control**: glass body, rotating gradient ring and a seconds
  arc; while dubbing, five bars inside the bubble move with the **real dub loudness**
  (it looks like someone is actually talking) and a halo pulses around it.
- **The dub recording timer is drawn on the bubble** (mm:ss, in the UI language's digits).
- **The bubble no longer disappears when a dub stops**: it shows up the first time you
  press "Start dubbing" and stays put afterwards, so the next video's dub starts with
  one tap on it. A long press removes it until the next start.
- **New bubble settings**: keep or hide after a session, show the timer, idle fade,
  collapse into an edge tab, auto-move to the nearest edge, smart mode and bubble size.
- **The dub starts exactly with the original audio** (60 ms buffer, zero deliberate
  delay, still adjustable) and **the original stays very quiet** (ducking held for the
  whole session plus an 18% original share in the saved mix, adjustable).
- **Gemini API token monitor**: today's usage, input/output, audio tokens, counted
  turns and sessions, daily budget and remaining, reset countdown (midnight Pacific),
  429 quota errors with their retry delay, per-day history and a link to Google's usage
  dashboard. Numbers come from Gemini Live's own `usageMetadata`; Google publishes no
  endpoint for "remaining quota", so the figure is an estimate and the dashboard stays
  the authority.
- **Developer & copyright card**: the official GitHub mark with a live animation
  (orbiting ring, breathing pulse, spin on tap), a link to
  [github.com/QW-AI-Code](https://github.com/QW-AI-Code), the formal copyright line, a
  random author signature per launch and a copyright header on every source file.
- **One universal APK instead of three identical ones**, plus R8 + resource shrinking
  and a size / native-library report in every build summary.

### Fixed and new in 1.0.0
- **UI rebuilt from scratch**: professional navy dark theme, brand gradient from the app icon, new card layout, explicit colour on every text — the "black, invisible text" bug is fixed at the root.
- **Official app icon** at all densities, adaptive + Android 13 monochrome, reused in the header (replacing the generic glyph), the notification and the bubble.
- **Language switch**: English is fully LTR and fully English, notification text included.
- **No more frozen "Saving output…"**: shutdown is time-boxed and watchdogged; the ongoing notification always clears and a dismissible "output saved" notification lists the files.
- **Files named after the video**: the media title is tracked during the session and finalised when it ends.
- **One universal APK** per release plus `SHA256SUMS.txt` and `apksigner` verification.
- Stable signing with your own keystore (guide in the README) so updates install over the previous build.

### Limits
Android 10+. Voice/video calls (`VOICE_COMMUNICATION`) and DRM apps such as Netflix
cannot be captured — an OS decision. Audio output is `m4a/AAC`.

### Security
TLS only, to `generativelanguage.googleapis.com` only. No telemetry, analytics or
ads. The API key lives in app-private storage and is excluded from backups. No
native code, no third-party SDKs beyond okhttp and Compose. Full audit in the README.

---

© 2026 [QW-AI-Code](https://github.com/QW-AI-Code)
