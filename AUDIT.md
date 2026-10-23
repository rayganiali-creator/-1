# ممیزی BlockerApp — Stage 1 (2026-10-23)

Repo: `rayganiali-creator/-1`, folder `BlockerApp/`, branch `main` @ `ccd26bd`, working tree clean, 4 commits.

## Stack فعلی
- Kotlin 1.9.22, AGP 8.2.2, Gradle 8.4, compile/target SDK 34, min SDK 24
- Views + ViewBinding (نه Compose)، تم MaterialComponents (نه Material 3)، بدون Room/DataStore — فقط SharedPreferences
- تک‌ماژول `:app`، فقط ۶ فایل Kotlin (~۵۰۰ خط)، هیچ تست (نه unit، نه instrumented)

## چه چیزهایی الان واقعاً هست و کار می‌کند (طبق کد)
1. بلاک اپ: انتخاب اپ از لیست (AppsListActivity + RecyclerView) و بلاک با AccessibilityService
2. بلاک دامنه: خواندن URL bar مرورگرهای معروف (۱۰ شناسه) و تطبیق substring
3. بلاک کلمه‌کلیدی: جمع‌کردن متن نودهای accessibility (حداکثر ۴۰۰ نود) و تطبیق substring با lowercase
4. صفحه بلاک (BlockedScreenActivity) + رفتن به Home قبلش؛ debounce ۱٫۵ ثانیه برای رخداد تکراری
5. ذخیره‌سازی: PrefsManager با StringSet در SharedPreferences

## چه چیزهایی اصلاً وجود ندارد (از لیست Master Prompt)
- سشن/تایمر (هیچ‌کدام)، Normal/Strict Mode، خروج چندمرحله‌ای و cooling-off
- دیلی قابل تنظیم (۰/۳/۵/۱۰/۱۵ ثانیه) — فقط debounce هست، دیلی واقعی نیست
- پروفایل، زمان‌بندی، گزارش/تاریخچه، بکاپ/ریستور، آنبوردینگ، صفحه تشخیص (Diagnostics)
- Foreground Service، Boot Receiver، WorkManager/Alarm، نوتیفیکیشن سشن
- DeviceAdmin/DeviceOwner، `setUninstallBlocked`، هیچ محافظت حذف/Force Stop
- ریکاوری اضطراری مستقل

## باگ‌ها و ریسک‌های پیدا‌شده
1. **CI خراب به احتمال زیاد:** ورک‌فلو در روت ریپوست ولی پروژه داخل `BlockerApp/` است؛ `gradle assembleDebug` در روت اجرا می‌شود و مسیر artifact هم `app/...` است (باید `BlockerApp/app/...` باشد).
2. `local.properties` با مسیر ویندوزی SDK داخل گیت کامیت شده — نباید باشد.
3. مجوز `SYSTEM_ALERT_WINDOW` در مانیفست هست ولی هیچ‌جای کد استفاده نمی‌شود.
4. `KEY_SERVICE_ENABLED` در PrefsManager تعریف شده و هرگز استفاده نمی‌شود؛ وضعیت واقعی فعال‌بودن Accessibility هیچ‌جا در UI نشان داده نمی‌شود.
5. نرمال‌سازی فارسی وجود ندارد: ي/ی، ك/ک، ارقام عربی/فارسی، نیم‌فاصله، تطبیق داخل کلمه‌ی نامربوط (substring) — همه غایب.
6. `collectText` نودها را recycle نمی‌کند؛ Prefs روی هر accessibility event از نو خوانده می‌شود.
7. StringSet ترتیب و متادیتای قانون (فعال/غیرفعال، دسته، whole-word) را نگه نمی‌دارد — برای قوانین نسخه‌دار Strict Mode کافی نیست.
8. `onBackPressed` deprecated؛ ایونت‌های سرویس با try/catch کلی بلعیده می‌شوند.
9. اعتبارسنجی ورودی دامنه/کلمه تقریباً صفر است (فقط trim/lowercase).

## صداقت فنی (ثابت برای کل پروژه)
- اپ معمولی نمی‌تواند جلوی Uninstall یا Force Stop را بگیرد. این فقط با Device Owner واقعی ممکن است که نیازمند پروویژن (معمولاً فکتوری ریست برای نصب اولیه روی گوشی شخصی) است — هرگز بی‌اجازه انجام نمی‌شود.
- Accessibility فقط متنِ در دسترس نودها را می‌بیند؛ پوشش همگانی (WebView امن، کیبورد سفارشی، متن داخل عکس) تضمین‌پذیر نیست. VPN فقط دامنه را می‌بیند، نه کلمه داخل HTTPS.
- پس سطح محافظت در UI باید رده‌بندی شود: Standard / Enhanced / Managed (فقط اگر واقعاً enroll و verify شده باشد) / Degraded.

## پلن اجرا (به ترتیب Master Prompt)
- Stage 2: مدل سشن با ساعت monotonic + wall-clock و boot count، ذخیره اتمیک، ریکاوری بعد از reboot/process death، پایان idempotent، تست یونیت محاسبات تایمر
- Stage 3: موتور matching جدا و تست‌پذیر: نرمال‌سازی فارسی/عربی، whole-word/phrase، قانون نسخه‌دار با دسته و enable/disable، دیلی واقعی ۰/۳/۵/۱۰/۱۵ با کنسل و revalidate قبل از اجرا، allowlist
- Stage 4–5: Strict Mode به‌صورت state واقعی قفل‌کننده کانفیگ سشن (snapshot immutable)، خروج چندمرحله‌ای با cooling-off قابل تنظیم، نمایش سطح محافظت واقعی؛ کد DeviceAdmin/Owner و `setUninstallBlocked` فقط به‌صورت opt-in با توضیح و تأیید صریح، بدون enrollment خودکار
- Stage 6: ریکاوری اضطراری مستقل از دیتابیس اصلی (فایل state حداقلی جدا + مسیر مستند)
- Stage 7: UI Material 3 + RTL + لایت/دارک — طبق قانون همیشگی سید: اول ماکاپ تصویری، بعد از تأیید او ساخت
- Stage 8–9: آنبوردینگ با چک واقعی پرمیشن‌ها، Diagnostics با وضعیت واقعی سرویس‌ها، پروفایل/زمان‌بندی/گزارش لوکال/بکاپ JSON با schema و validation
- Stage 10: فیکس CI (working-directory درست)، تست‌های یونیت، بیلد و گزارش صادقانه نتایج
