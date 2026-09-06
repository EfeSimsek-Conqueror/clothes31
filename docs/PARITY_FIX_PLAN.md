# Fitrater — iOS ↔ Android Parite ve Düzeltme Planı
**Tarih:** 2026-08-15 · **Branch:** `fix/app-store-3-2-2-and-aasa`
**Kapsam:** Önceki incelemede çıkan tüm farklar + inceleme sırasında bulunan yeni kusurlar için
kök neden, çözüm seçenekleri, önerilen çözüm, uygulama adımları, doğrulama ve sıralama.

---

## 0. Yönetici özeti

Tespit edilen 24 madde 4 kovaya ayrılıyor:

| Kova | Adet | Özü |
|---|---|---|
| **P0 — Para / mağaza riski** | 5 | Android'de satın alma yapmayan paywall; parası alınıp çalışmayan "brutal" özelliği; iOS build numarası belirsizliği; deploy edilmemiş backend; sahte AI cevabı |
| **P1 — Ölü / yanıltıcı UI** | 6 | Tıklanmayan chat çipleri, hiçbir şey yapmayan bildirim toggle'ları, Pro'ya kilitli stub butonlar |
| **P2 — Parite açıkları** | 9 | Studio 2.0, Stylist chat, Journal analitiği, paylaşım kartları, konum, App Links, Crashlytics |
| **P3 — Süreç / hijyen** | 4 | Sürüm şeması, repoda olmayan edge function'lar, CI yokluğu, artık dosyalar |

**Ana tez:** Tekil hataları tek tek yamamak yetmez. Bu farkların çoğu tek bir yapısal
sebepten doğuyor: *aynı ürün kuralları iki dilde ikişer kez elle yazılıyor ve hiçbir yerde
tek bir doğruluk kaynağı yok.* Bu yüzden plan iki katmanlı: **(A) yangın söndürme**,
**(B) drift'i kalıcı olarak imkânsız kılan altyapı.**

**Karar gerektiren 5 çatal** (§7'de detaylı):
1. Android birinci sınıf platform olarak devam edecek mi? (planın yarısı buna bağlı)
2. Roast/brutal Android'den de kaldırılsın mı? (öneri: evet)
3. Push bu sürümde mi çıkacak, yoksa toggle'lar gizlensin mi? (öneri: gizle, sonra çık)
4. Journal hedefi "iOS editoryal düzen + Android analitiği" mi? (öneri: evet)
5. Paywall RevenueCat Paywalls v2'ye mi taşınsın? (öneri: bir sonraki döngüde)

---

## 1. Bulgu envanteri

Her madde: **belirti → kanıt → etki → kök neden**.

### P0-1 · Android paywall gerçek bir paywall değil
- **Kanıt:** `android/.../ui/screens/PaywallScreen.kt:95` "Start 7-day free trial" → `onContinue()`;
  `:98-102` "Restore purchases" → yine `onContinue()`. `MainActivity.kt:209-213` bu ekranı
  Shell'e yönlendiriyor. Fiyatlar hardcode: `:82` `"7 days free, then $59.99 / year"`, `:90` `"$9.99 / month"`.
  Ekrana **6 ayrı yerden** yönlendirme var (`MainActivity.kt:247,267,294,307,313,406`).
- **Etki:** Android'de feature-gate'ten paywall'a düşen her kullanıcı satın alma yapamadan
  uygulamaya geri dönüyor → doğrudan gelir kaybı. Ayrıca yerelleştirilmemiş USD fiyat + çalışmayan
  "restore" Google Play ödeme politikası ve tüketici mevzuatı açısından risk.
- **Kök neden:** Gerçek satın alma akışı sonradan `YouSheet.kt:560 CreditsSheetContent` içine
  yazıldı, eski onboarding paywall ekranı temizlenmeden bırakıldı.

### P0-2 · Android'de "Brutal mode" parası alınan ama sunucuda etkisiz özellik
- **Kanıt:** Android hâlâ brutal'ı Pro arkasında satıyor: `YouSheet.kt:259` (`proLocked = option == "brutal" && !isPro`),
  `CameraMenuSheet.kt:142-151` "Roast this — Brutal mode, locked", `ManageProScreen.kt` Pro faydası.
  Sunucu tarafında ise `supabase/functions/score-outfit/index.ts:14-22` `resolveHonesty()` **her değeri
  `honest`'a çeviriyor**. iOS bu kararı Ağustos'ta uyguladı (`YouSheet.swift:41` honesty picker gizli,
  roast girişi chat ile değiştirildi — bkz. `ios/store/SESSION_2026-08-08.md §1.2`).
- **Etki:** Android Pro kullanıcısı, satın aldığı "brutal" tonu asla alamıyor (fonksiyon deploy edildiği anda
  kesinleşir). İki mağazada iki farklı ürün vaadi.
- **Kök neden:** Ürün kararı yalnız iOS + backend'de uygulandı, Android'e yansıtılmadı.

### P0-3 · iOS sürüm numarası iki yerde, hangisi kazandığı belirsiz
- **Kanıt:** `project.pbxproj:670` `GENERATE_INFOPLIST_FILE = NO`, `:671` `INFOPLIST_FILE = FitScore/Info.plist`,
  `:669` `CURRENT_PROJECT_VERSION = 15`; ama `Info.plist:13` `<key>CFBundleVersion</key><string>4</string>`
  ve `:12` `CFBundleShortVersionString 1.0` literal. `ios/store/submit.sh` plist'e hiç dokunmuyor.
  `GENERATE_INFOPLIST_FILE = NO` iken build ayarları plist'e enjekte **edilmez** — literal değer gider.
- **Etki:** "build 14 processing kuyruğunda takıldı" (commit `6dd7e4e`) semptomu, aslında yüklenen binary'nin
  beklenen build numarasını taşımaması olabilir. Her yükleme için sürüm çakışması / kafa karışıklığı riski.
- **Kök neden:** Tek doğruluk kaynağı yok.

### P0-4 · Backend deploy edilmemiş, üstelik bu oturumda da erişim yok
- **Kanıt:** `SESSION_2026-08-08.md §3`: `stylist-chat`, `compose-cover`, `score-outfit` deploy edilmedi;
  `0005_stylist_memory.sql` uygulanmadı. Bugün Supabase MCP çağrısı: `Unauthorized — provide a valid access token`.
- **Etki:** iOS build 15 App Store'da chat'i canlı bir fonksiyona bağlayamıyor; compose-cover düzeltmeleri canlı değil.
- **Kök neden:** Süresi dolmuş `SUPABASE_ACCESS_TOKEN` + `--read-only` MCP.

### P0-5 · Chat hata durumunda sahte AI cevabı üretiyor
- **Kanıt:** `App/MainTabView.swift:259-276` — `StylistApi.send()` **her** hatayı yutup `canned(for:)`
  ile uydurma bir stilist cevabı döndürüyor (ör. "For a wedding: linen suit…").
- **Etki:** Fonksiyon deploy edilmediği/kotalar dolduğu anda kullanıcı, AI cevabı sandığı sabit metni görüyor.
  Güven kaybı; Apple tarafında "AI özelliği çalışmıyor" reddi ihtimali; hata hiçbir yere raporlanmıyor.
- **Kök neden:** Geliştirme kolaylığı için konan fallback, release'e kadar kaldı.

### P1-6 · iOS chat çipleri tıklanamıyor
- **Kanıt:** `MainTabView.swift:455-465` — `ChatChip` değerleri `Button` değil düz `Text`; buton gibi
  görünüyor (kapsül + hairline), hiçbir aksiyonu yok. Çipler: "🪞 Try it on", "🔁 3 alternatives", "📌 Save this".

### P1-7 · Bildirim ayarları: iki tarafta da arkası boş
- **Kanıt:** Android'de 3 toggle var (`YouScreen.kt:155-191`) ve DB'ye yazıyor; iOS'ta
  `Repo.swift:548,560` fonksiyonları var ama **hiçbir ekran çağırmıyor**. Her iki uygulamada da
  APNs/FCM entegrasyonu yok (`AndroidManifest.xml`'de `POST_NOTIFICATIONS` yok, `firebase-messaging`
  bağımlılığı yok; iOS'ta Push capability ve `UNUserNotificationCenter` kullanımı yok), gönderim tarafında
  cron/edge fn yok.
- **Etki:** Android'de var olmayan bir özelliğin ayarları açılıyor → kullanıcı bekliyor, bildirim gelmiyor.

### P1-8 · iOS'ta Pro'ya kilitli stub'lar
- **Kanıt:** `SESSION_2026-08-08.md §4`: TryOn "URL paste" (Pro-gated → sadece bilgi alert'i),
  "Season swap" (Pro-gated → alert), Weekly Wrapped paylaş butonu (haptic stub).
- **Etki:** Pro satın alan kullanıcı, Pro rozetli üç düğmeden hiçbir sonuç alamıyor.

### P1-9 · iOS'ta ulaşılamayan ölü kod
- **Kanıt:** `Camera/RoastView.swift` (199 satır) — girişi kaldırıldı, dosya duruyor
  (`SESSION_2026-08-08.md §1.2`: "kept but unreachable dead code").

### P1-10 · Kredi maliyetleri iki platformda farklı
- **Kanıt:** iOS `Data/SupaClient.swift:55-72` Studio 2.0 fiyatlaması (`singlePieceCost = 10`,
  `outfitTwoCost = 18`, `outfitThreeCost = 24`, `outfitFourCost = 32`, `outfitExtraPieceCost = 8`,
  `extraAlternativesCost = 5`, `studioCombineStandaloneCost = 6`). Android `data/SupabaseClient.kt:39-52`
  bu bloğun **tamamından yoksun**; Studio üretimi hâlâ `GENERATE_COST = 15`.
- **Etki:** Aynı kullanıcı, aynı işlem için iOS'ta 10, Android'de 15 kredi ödüyor — kredi bakiyesi
  ortak (sunucu tarafı) olduğu için bu doğrudan bir adalet/şeffaflık sorunu.
- **Not:** Cap'ler tutuyor (`trialDailyCap 20`, `subMonthlyCap 1200`, `subAnnualMonthlyCap 750` her iki tarafta aynı).

### P1-11 · Android satın alma yüzeyinde yasal bağlantı ve restore yok
- **Kanıt:** iOS `You/CreditsSheet.swift:40-41,197-201,288-311` → Terms, Privacy, gerçek `restore()`.
  Android `YouSheet.kt` credits sheet'inde ne Terms/Privacy linki ne restore var; tek "Restore"
  metni ölü paywall'da (P0-1).

### P2-12 · Studio 2.0 tamamen iOS'ta (en büyük özellik açığı)
- **Kanıt:** iOS `Studio/StudioCreateView.swift` 3146 satır: `StudioCreateGateway`, `OutfitWizardView`
  (compose → iterate → combining → done), 19 parçalı `OutfitPiece` enum'u, alt tür soruları (yaka, kapama,
  paça, bel), "FROM YOUR CLOSET" şeridi, front+side çift render, `linked_piece_id` ile eşlenmiş kayıt.
  Android `StudioCreateScreen.kt` 2060 satır ve `presetType` var ama wizard/çoklu parça/closet şeridi yok.

### P2-13 · Stylist chat sadece iOS'ta
- **Kanıt:** `MainTabView.swift:143-500`; Android'de `chat` geçen tek satır yok.
  Backend sözleşmesi basit ve durumsuz: `supabase/functions/stylist-chat/index.ts`
  (`{messages[], image_base64?}` → `{text}`), yani port maliyeti düşük.

### P2-14 · Journal: Android analitik, iOS editoryal — kesişim küçük
- **Android'de olup iOS'ta olmayan:** arama (`JournalScreen.kt:623`), faceted filtre paneli (`:1450`),
  iki kombini yan yana karşılaştırma (`:846 CompareSheet` — `compare-outfits` fonksiyonuna gidiyor),
  sparkline (`:1012`), palet şeridi (`:1035`), throwback (`:1181`), deterministik insight'lar (`:1385`),
  milestone'lar (`JournalExtras.kt:449`), aylık refleksiyon kartı (`:239`), **PDF export** (`util/JournalPdfExport.kt`, 308 satır).
- **iOS'ta olup Android'de olmayan:** masthead + "chapter" düzeni, roman rakamlı ay başlıkları,
  Style DNA kartı, hero/occasion-plan kart tipolojisi, `safeAreaInset` ile sabitlenen filtre çubuğu.
- **Not:** iOS'ta `HemService.compare` (`Data/HemService.swift:107`) zaten var — sadece UI eksik.

### P2-15 · Konum bazlı hava durumu sadece Android'de
- **Kanıt:** Android `data/weather/Weather.kt` FusedLocation + izinler (`AndroidManifest.xml` COARSE+FINE).
  iOS `Util/Weather.swift:8` koordinat sabit: İstanbul (41.01, 28.98).
- **Yan etki:** Android konum izni istiyor → Play Data Safety'de konum beyanı gerekiyor; iOS'ta
  gerek yok. Yani parite yönü "iOS'a konum ekle" değil olabilir (bkz. çözüm).

### P2-16 · Görsel paylaşım kartları sadece Android'de
- **Kanıt:** `util/ShareCard.kt` (209 satır) roast ve versus için 9:16 kart render edip paylaşıyor
  (`RoastScreen.kt:156`, `VersusScreen.kt:148`). iOS'ta Roast düz metin `ShareLink` (`RoastView.swift:130`),
  Versus'ta paylaşım hiç yok; `WrapShareCard` render ediliyor ama paylaşıma bağlı değil.

### P2-17 · Android App Links yok
- **Kanıt:** iOS: `Fitrater.entitlements` `applinks:fitrater.ai` + `web/app/.well-known/apple-app-site-association/route.ts`
  (Team `NCYFB69WBK`, bundle `com.fitrater.app`, `paths:["*"]`, `webcredentials`).
  Android: `AndroidManifest.xml` `android:autoVerify="false"`, sadece `com.fitrater.app://auth-callback`
  custom scheme; `assetlinks.json` **yok**.
- **Etki:** fitrater.ai linkleri Android'de uygulamayı açmıyor; magic-link/OAuth dönüşü custom scheme'e bağımlı.

### P2-18 · Crashlytics/Analytics asimetrisi
- **Kanıt:** Android: `firebase-crashlytics-ktx` + `firebase-analytics-ktx` + `MainActivity.kt:78`
  global uncaught exception handler. iOS: sadece `FirebaseApp.configure()` (`App/FitraterApp.swift:17`),
  Crashlytics ürünü hiç kullanılmıyor.
- **Etki:** iOS'ta production crash'leri kör nokta (App Store Organizer dışında telemetri yok).

### P2-19 · Yükleme/iskelet durumları asimetrisi
- Android: `components/Shimmer.kt` + `HomeSkeleton` (8 dosyada shimmer). iOS: 2 dosya.
  Algılanan hız farkı; düşük öncelik ama parite listesinde.

### P2-20 · Android targetSdk geride
- `compileSdk = 36` ama `targetSdk = 35`, `minSdk = 26`, `versionCode 8` / `versionName 1.0.6`.

### P3-21 · Üretimdeki 4 edge function repoda yok
- **Kanıt:** İstemcilerin çağırdığı ama `supabase/functions/` altında bulunmayanlar:
  **`generate-piece`** (tüm Studio üretimi buna bağlı), **`compare-outfits`**, **`decode-outfit`**, **`occasion-coach`**.
- **Etki:** Prod'da çalışan kritik kodun kaynağı versiyon kontrolünde değil; geri alınamaz, gözden geçirilemez.

### P3-22 · Sürüm şeması ayrışmış
- iOS `MARKETING_VERSION 1.0` / build 15 · Android `versionName 1.0.6` / `versionCode 8`.
  Destek ve bug raporlarında hangi sürümün ne içerdiği izlenemez.

### P3-23 · CI ve test yok
- `.github` yok; her iki projede de test hedefi yok. Tek doğrulama: elle build + mağaza yüklemesi.

### P3-24 · Repo hijyeni
- `Info.plist.bak`, `Fitrater.entitlements.bak` (eski domain `fitrater.app`), `Info.plist:27`
  yorumunda başka projeden kalma `com.cloudgeng.mealmind` ifadesi, kökte isimsiz `file` (13 KB oturum notu),
  `supabase/.temp/*` untracked.

---

## 2. P0 çözümleri (yangın söndürme)

### Ç0-1 · Android paywall'ı gerçek satın almaya bağla

**Seçenekler**

| # | Yaklaşım | Artı | Eksi |
|---|---|---|---|
| A | `PaywallScreen`'i sil, tüm `onOpenPaywall` → mevcut `CreditsSheetContent` | En hızlı (30 dk), tek satın alma yolu | Onboarding'de tam ekran editoryal sunum kaybolur |
| B | `PaywallScreen`'i RevenueCat'e bağla (**önerilen**) | Tasarım korunur, iOS ile aynı deneyim, 3–4 saat | İki satın alma yüzeyi bakımı |
| C | RevenueCat Paywalls v2 (uzaktan yapılandırılan paywall) | Kopya/fiyat mağaza yayını olmadan değişir, iki platform otomatik eşlenir | `purchases-ui` bağımlılığı, tasarım birebir taşınmaz, ~1 gün |

**Öneri:** Şimdi **B**, bir sonraki döngüde **C** değerlendirmesi (RevenueCat MCP zaten bağlı;
`create-paywall-ai` / `get-offering-prices` araçları mevcut).

**Uygulama adımları (B)**
1. İmzayı değiştir: `PaywallScreen(context: String?, onDone: () -> Unit, onDismiss: () -> Unit)`.
2. `LaunchedEffect(Unit) { offering = RcBilling.currentOffering() }`; `annual = offering?.annual`, `monthly = offering?.monthly`.
3. Fiyatı **paketten** yaz: `pkg.product.price.formatted` (asla hardcode USD değil).
4. Deneme süresini **paketten türet**: `pkg.product.defaultOption?.freePhase` yoksa "7 gün ücretsiz" iddiasını gösterme.
5. Satın alma: `val activity = LocalContext.current as? Activity` → `RcBilling.purchase(activity, pkg)`.
   Hata eşlemesi: `PurchasesErrorCode.PurchaseCancelledError` → "Purchase canceled", diğerleri → "Purchase failed — no charge".
6. Restore: `Purchases.sharedInstance.awaitRestorePurchases()` → entitlement kontrolü → toast + state güncelle.
7. Başarıda: `RcBilling.refreshCustomerInfo()`, `Repo.markPaywallShown()`, `onDone()`.
8. Yasal blok (P1-11 ile ortak): Terms + Privacy linkleri, otomatik yenileme ve iptal cümlesi
   ("Cancel anytime in Google Play"). Aynı bloğu `CreditsSheetContent`'e de ekle.
9. `paywallContext` → iOS `PaywallContext` enum'unun birebir Kotlin karşılığı (`tryon` / `letter`;
   `brutal` Ç0-2 ile kalkıyor) ve hero kopyası ondan gelsin.

**Doğrulama**
- Play Console'da internal test track + lisans test hesabı; annual + monthly satın alma, iptal, restore,
  yeniden kurulum sonrası entitlement.
- Farklı ülke hesabıyla fiyatın yerelleştiğini gör (TRY/EUR).
- `adb logcat -s RcBilling` ile purchase sonucu; RevenueCat dashboard'da transaction görünmeli.

---

### Ç0-2 · Brutal/roast'ı Android'de de kaldır

**Seçenekler:** (A) iOS'u aynala · (B) sunucuda brutal'ı geri getir · (C) sunucu bayrağıyla yönet.

**Öneri:** **A** hemen, **C** altyapı dalgasında (`app_config.honesty_modes`).
B ürün kararına aykırı ve App Store tarafında geri adım demek.

**Adımlar**
1. `CameraMenuSheet.kt:142-151` "Roast this" satırını sil → yerine "Chat with Hem" (Ç2-13 ile birlikte).
2. `YouSheet.kt:250-275` honesty segmenti kaldır (iOS'taki gibi fonksiyonu dosyada bırak, çağırma).
3. `ManageProScreen.kt` Pro faydaları listesinden brutal maddesini çıkar.
4. `FaqScreen.kt` honesty modları sorusunu iOS'taki yeni metinle değiştir.
5. `RoastScreen.kt` + `Nav.Route.Roast` + `ROAST_COST` kullanımını kaldır; `ShareCard.renderRoast`
   kullanımını Versus'a taşı (Ç2-16'da iOS'a da lazım olacak).
6. `Repo.updateHonesty` çağrılarını "honest" sabitine indir (geriye dönük veri bozulmasın diye
   fonksiyon kalsın).
7. Mağaza metinleri: Play listing açıklamasında "brutal/roast" geçiyorsa temizle.

**Doğrulama:** Yeni kurulumda hiçbir yerde "brutal" kelimesi görünmemeli:
`grep -ri "brutal\|roast" android/app/src/main | grep -v "\.bak"` → sadece kasıtlı bırakılan yorumlar.

---

### Ç0-3 · iOS sürümünü tek kaynağa indir

**Adımlar**
1. `Info.plist`:
   ```xml
   <key>CFBundleShortVersionString</key><string>$(MARKETING_VERSION)</string>
   <key>CFBundleVersion</key><string>$(CURRENT_PROJECT_VERSION)</string>
   ```
2. Bump artık yalnızca pbxproj'de (veya `xcrun agvtool next-version -all`).
3. `submit.sh`'a arşiv sonrası ön kontrol ekle:
   ```bash
   plutil -p "$ARCHIVE_PATH/Products/Applications/Fitrater.app/Info.plist" \
     | grep -E 'CFBundleVersion|CFBundleShortVersionString'
   ```
   ve beklenen değerle eşleşmezse `exit 1`.
4. Opsiyonel: aynı ASC API anahtarıyla mevcut build listesini sorgulayıp aynı numarayı ikinci kez
   yüklemeyi engelle (ITMS-4238 hatasını baştan kes).

**Doğrulama:** `xcodebuild -showBuildSettings | grep -E 'MARKETING_VERSION|CURRENT_PROJECT_VERSION'`
çıktısı ile arşivdeki plist birebir aynı olmalı; TestFlight'ta build numarası beklenen değerde görünmeli.

---

### Ç0-4 · Backend'i canlıya al ve erişimi kalıcı düzelt

```bash
supabase login                                   # token yenile
cd /Users/efe/clothes31
supabase link --project-ref ilrzqifdmjvooeyqvexd
supabase db push                                 # 0005_stylist_memory
supabase functions deploy stylist-chat
supabase functions deploy compose-cover
supabase functions deploy score-outfit
supabase functions list                          # doğrulama
```
MCP'yi yazılabilir yap:
```bash
claude mcp remove supabase
claude mcp add supabase -- npx -y @supabase/mcp-server-supabase@latest --project-ref=ilrzqifdmjvooeyqvexd
```
Ardından **P3-21**: repoda olmayan 4 fonksiyonu indir ve commit'le:
```bash
for f in generate-piece compare-outfits decode-outfit occasion-coach; do
  supabase functions download "$f"
done
```
Kalıcılaştır: `supabase/deploy.sh` (tek komutla tüm fonksiyonlar + migration) ve `FAL_KEY` gibi
secret'ların varlığını kontrol eden bir preflight.

**Doğrulama:** Chat'ten bir mesaj gönder; `supabase functions logs stylist-chat` içinde 200 gör.
`select count(*) from chat_threads;` sorgusu hata vermemeli (migration uygulandı).

---

### Ç0-5 · Sahte AI cevabını kaldır

`MainTabView.swift:259-276`:
- `canned(...)` fallback'ini `#if DEBUG` içine al.
- Release'te hata → mesaj balonu yerine görünür hata durumu: "Hem'e ulaşılamadı" + **Retry** düğmesi
  + `error` alanının kullanıcıya gösterilmesi (zaten `@Published var error` var, sadece kullanılmıyor).
- Hatayı Crashlytics'e non-fatal olarak logla (Ç2-18 ile birlikte).
- Aynı desen `HemService.compare` / `decode` içindeki sessiz `catch`'ler için de gözden geçirilmeli
  (`Data/HemService.swift:107-128`).

---

## 3. P1 çözümleri (ölü / yanıltıcı UI)

### Ç1-6 · Chat çipleri
- **"🪞 Try it on"** → chat'i kapat, `FeatureGates.requireTryon()` kontrolü, `CameraMenuBus.shared.request(.tryon)`.
- **"🔁 3 alternatives"** → aynı `StylistApi.send` akışına "Give me 3 alternatives to that look, numbered." mesajını enjekte et.
- **"📌 Save this"** → `chat_messages` tablosu canlıya alınana kadar **kaldır** (Ç0-4 sonrası eklenebilir).
- Çipler `Button` olmalı, `Haptic.chip()` + basılı durum. Bağlanamayacak çip ekranda durmasın.

### Ç1-7 · Bildirimler — iki aşamalı
**Aşama 1 (bu sürüm, ~30 dk):** Android toggle'larını `BuildConfig.PUSH_ENABLED = false` arkasına al
(veya "Coming soon" olarak devre dışı göster). Var olmayan özelliğin ayarı kullanıcıya vaat sayılır.

**Aşama 2 (2–3 gün, tam çözüm):** Tek sağlayıcı üzerinden — **FCM HTTP v1 ile her iki platform**
(APNs auth key'i Firebase'e yükleyerek). Firebase zaten iki projede de var.
```sql
create table push_tokens (
  token       text primary key,
  user_id     uuid not null references auth.users(id) on delete cascade,
  platform    text not null check (platform in ('ios','android')),
  tz          text,
  updated_at  timestamptz not null default now()
);
alter table push_tokens enable row level security;
create policy push_tokens_own on push_tokens for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
```
- **iOS:** Push Notifications capability + `aps-environment` entitlement, `UNUserNotificationCenter.requestAuthorization`
  (izin istemenin doğru anı: ilk skor sonrası, uygulama açılışında değil — opt-in oranı 2–3 kat fark eder),
  `didRegisterForRemoteNotificationsWithDeviceToken` → `push_tokens` upsert. iOS'a **eksik olan ayar UI'ı** eklenir.
- **Android:** `firebase-messaging` + API 33+ için `POST_NOTIFICATIONS` runtime izni + `FirebaseMessagingService`
  (token refresh, bildirime tıklayınca deep link).
- **Sunucu:** `send-push` edge fn (service-role) + `pg_cron`:
  sabah kartı (kullanıcının `tz`'sine göre 08:00), Pazar mektubu, ay sonu Wrapped.
  Her gönderimde `push_settings` toggle'ı kontrol edilir.
- **Uyum:** App Privacy yanıtları (`ios/store/APP_PRIVACY_ANSWERS.md`) ve Play Data Safety güncellenir.

### Ç1-8 · iOS Pro-gated stub'lar
Sıra: **önce gizle, sonra yap.**
- `tryon-from-url`, `season swap`, Wrapped share → sunucudan gelen bayrakla (`app_config.flags`) gizlenir;
  bayrak yoksa varsayılan `false`.
- **Wrapped share** zaten ucuz: `WrapShareCard` bir SwiftUI view → `ImageRenderer(content:).uiImage`
  → `UIActivityViewController`. Yarım gün, bayrağa gerek kalmadan bitirilebilir (**önerilen**).
- `tryon-from-url`: `og:image` scrape eden edge fn (1 gün) + istemci akışı; season swap: `tryon-outfit`'e
  `season` parametresi (yarım gün).

### Ç1-9 · Ölü kod
`RoastView.swift` sil, `CameraFlow.roast` case'ini kaldır (Ç0-2 ile Android'de de aynı temizlik).
Git geçmişi zaten koruyor, dosyayı "belki lazım olur" diye tutmak parite denetimini kirletiyor.

### Ç1-10 · Kredi maliyeti drift'i
- **Kısa vade (aynı gün):** iOS'taki Studio 2.0 blokunu Android `SupabaseClient.kt`'ye birebir kopyala
  ve Android Studio üretimini `SINGLE_PIECE_COST = 10`'a çek (kullanıcı lehine düzeltme, geri bildirim gerekmez).
- **Doğru çözüm:** Fiyatlar sunucudan (**Ç4-B / Ç4-C**). Kredi düşme işlemi zaten sunucuda
  (`spend_credits`) olduğu için maliyet tablosunun da orada olması doğal.
- **Ek öneri:** İstemci maliyeti göstersin ama **düşme sunucuda doğrulansın**; şu an istemci
  hangi tutarı gönderirse o düşüyor (istemciye güven = manipülasyon yüzeyi).

### Ç1-11 · Android satın alma yüzeyinde yasal blok
`CreditsSheetContent` ve yeni paywall'a: Terms + Privacy linkleri (`fitrater.ai/terms`, `/privacy`,
`HelpPrivacySheet.kt:136-143`'te URL'ler zaten var), otomatik yenileme cümlesi, restore düğmesi.

---

## 4. P2 çözümleri (parite)

Parite kararı özellik bazında verilir — "her şey her iki tarafta" hedefi bu ekip büyüklüğünde gerçekçi değil.
Aşağıdaki tablo **hedef durumu** tanımlar:

| Özellik | Bugün | Hedef | Yön | Tahmin |
|---|---|---|---|---|
| Studio 2.0 wizard | iOS | Her iki taraf | iOS → Android | 6–10 gün |
| Stylist chat | iOS | Her iki taraf | iOS → Android | 1–1.5 gün |
| Journal arama + facet | Android | Her iki taraf | Android → iOS | 1.5 gün |
| Journal compare | Android | Her iki taraf | Android → iOS (backend hazır) | 1 gün |
| Journal PDF export | Android | Her iki taraf | Android → iOS | 1 gün |
| Milestone/throwback/insight | Android | Her iki taraf | Android → iOS (saf mantık) | 1 gün |
| Editoryal chapter düzeni | iOS | Her iki taraf | iOS → Android | 2 gün |
| Paylaşım kartları (roast/versus/wrapped) | Android | Her iki taraf | Android → iOS | 1 gün |
| Konum bazlı hava | Android | **Hiçbiri** (sunucuya taşı) | ↓ aşağı bak | 0.5 gün |
| Crashlytics + funnel analytics | Android(kısmi) | Her iki taraf | Android → iOS + ikisine event | 1 gün |
| Shimmer/skeleton | Android | Her iki taraf | Android → iOS | 0.5 gün |
| App Links / Universal Links | iOS | Her iki taraf | iOS → Android | 0.5 gün |
| Apple ile giriş | iOS | Sadece iOS (doğru) | — | — |

### Ç2-12 · Studio 2.0 portu (en büyük iş)
Sırayla: (1) `OutfitPiece` enum'u + `placementInstruction` metinleri (saf veri — birebir taşınabilir,
ideal olarak **paylaşılan JSON**'dan üretilir), (2) Gateway ekranı, (3) compose fazı + closet şeridi,
(4) iterate fazı (mevcut `StudioCreateScreen`'i `presetType`+`presetSubtype` kilidiyle yeniden kullan —
Android'de `presetType` zaten var, `presetSubtype` eklenecek), (5) combine (iki paralel `generate-piece`
çağrısı, front+side), (6) `linked_piece_id` ile kayıt ve detayda pager.
**Kritik uyarı:** Prompt metinleri (v4 mankeni, HARD RULES bloğu) iki dilde iki kopya olursa çıktı kalitesi
platformlar arasında ayrışır. **Prompt'lar `generate-piece` edge function'ına taşınmalı**, istemci sadece
yapılandırılmış parametre göndermeli. Bu port sırasında yapılacak en değerli mimari düzeltme budur.

### Ç2-13 · Chat portu (Android)
`stylist-chat` durumsuz olduğu için: `ChatScreen.kt` (transcript + composer + foto ekleme),
`StylistApi.kt` (`functions.invoke("stylist-chat")`), giriş noktaları: CameraMenuSheet satırı + Home invite bar.
iOS'taki fallback hatasını **tekrarlama** (Ç0-5).

### Ç2-14 · Journal birleştirme
Hedef: **iOS'un editoryal kabuğu + Android'in analitik gücü.**
Önce saf mantık fonksiyonlarını taşı (`applyFacets`, `detectMilestones`, `reflectionsFor`,
`deterministicInsights`, `sparklineFor`, `paletteFor`) — bunlar deterministik, test edilebilir ve
ileride paylaşılan katmana adaydır. Sonra UI: arama alanı + facet sheet, `CompareSheet`
(iOS'ta `HemService.compare` hazır), PDF (`UIGraphicsPDFRenderer` + `ImageRenderer`).
Ardından Android'e masthead/chapter kabuğunu getir.

### Ç2-15 · Hava durumunu sunucuya taşı (parite değil, sadeleştirme)
Yeni `weather` edge fn: istek IP'sinden konum çöz (yoksa opsiyonel lat/lon parametresi), open-meteo'ya sor,
`Cache-Control: public, max-age=1800` ile dön.
Kazanç: Android'den `ACCESS_COARSE/FINE_LOCATION` izinleri **kaldırılır** (Play Data Safety beyanı sadeleşir,
kurulum dönüşümü artar), iOS'a izin diyaloğu hiç eklenmez, iki istemci de 10 satıra iner, sağlayıcı
değişimi tek yerden yapılır. Hassas konum gerekirse sonradan opt-in olarak eklenir.

### Ç2-16 · Paylaşım kartları (iOS)
`ShareCard.kt`'nin karşılığı: tek bir `ShareCardRenderer.swift` — `ImageRenderer` ile 9:16,
`fitrater.ai` filigranı, versus/wrapped/score varyantları, `UIActivityViewController` ile paylaşım.
Wrapped share (Ç1-8) bunun ilk müşterisi olur.

### Ç2-17 · Android App Links
1. Play Console → App Signing → **SHA-256** parmak izini al (upload değil, app signing key).
2. `web/app/.well-known/assetlinks.json/route.ts` (AASA ile aynı desende, `force-static`):
   ```json
   [{"relation":["delegate_permission/common.handle_all_urls"],
     "target":{"namespace":"android_app","package_name":"com.fitrater.app",
               "sha256_cert_fingerprints":["<SHA256>"]}}]
   ```
3. Manifest: `autoVerify="true"` + `https://fitrater.ai` host filtresi (custom scheme kalsın).
4. Doğrulama: `adb shell pm verify-app-links --re-verify com.fitrater.app` ve
   `adb shell pm get-app-links com.fitrater.app` → `verified`.
   Ayrıca AASA'nın canlıda redirect'siz ve `application/json` döndüğünü kontrol et
   (`curl -sI https://fitrater.ai/.well-known/apple-app-site-association`).

### Ç2-18 · Crashlytics (iOS) + funnel event'leri (iki taraf)
iOS target'a `FirebaseCrashlytics` ürünü + dSYM upload run script; `RcBilling`, `Repo`, `StylistApi`
hatalarını non-fatal kaydet. İki tarafta ortak minimum event seti: `paywall_view(context)`,
`purchase_start/success/fail(code)`, `score_complete`, `studio_generate(pieces)`, `chat_message`,
`credits_exhausted`. Event isimleri **tek bir JSON'dan** üretilmeli (Ç4-B), yoksa analizde iki ayrı isim çıkar.

### Ç2-20 · targetSdk 36
Bump + edge-to-edge kontrolü (Android 15'te zorunlu), predictive back, `POST_NOTIFICATIONS` (push gelirse),
foto seçici davranışı. Play'in yıllık hedef API zorunluluğundan önce yapılmalı.

---

## 5. P3 — drift'i kalıcı bitiren altyapı (asıl çözüm)

Tekil yamaların ömrü kısa; bu bölüm sorunun tekrar üretilmesini engeller.

### Ç4-A · Platform politikası (bugün, 0 maliyet)
- **iOS lider platform**, Android tanımlı bir gecikmeyle takip eder (öneri: ≤2 hafta).
- Her özellik PR'ı `docs/PARITY.md`'deki matrisi güncellemek zorunda: `feature | iOS | Android | sunucu | not`.
- "Sadece iOS" kararı meşru; **kayıt altına alınmamış** olması sorun.

### Ç4-B · Paylaşılan spesifikasyon + kod üretimi (yarım gün)
`shared/spec/` altında JSON: `pricing.json` (kredi maliyetleri, cap'ler), `copy.json` (paywall/FAQ metinleri),
`analytics.json` (event isimleri), `pieces.json` (OutfitPiece + placement talimatları).
`tools/gen.ts` → `SupaGenerated.swift` + `SupaGenerated.kt`. CI, üretilen dosya ile commit'lenen dosya
farklıysa build'i düşürür. **P1-10 gibi hataları yapısal olarak imkânsız kılar.**

### Ç4-C · Sunucu tarafı yapılandırma (1 gün)
```sql
create table app_config (
  key         text primary key,     -- 'flags','pricing','copy'
  value       jsonb not null,
  min_build   int default 0,
  updated_at  timestamptz not null default now()
);
```
İstemciler açılışta çeker, 24 saat cache'ler, ağ yoksa gömülü varsayılana düşer.
Kazanç: brutal kaldırma, stub gizleme, fiyat değişimi → **mağaza yayını gerektirmez**.
Bugünkü P0-2 ve P1-8 problemleri bu mekanizma olsaydı bir bayrak çevirmekle biterdi.

### Ç4-D · KMP değerlendirmesi (koşullu, 1–2 hafta)
`Repo` (1404 + 1120 satır) ve `Models` (467 + 436 satır) neredeyse birebir aynı.
Kotlin Multiplatform ile veri katmanı paylaşılırsa ~2.000 satırlık ikizlenme biter, UI native kalır.
**Yalnızca Android en az 6 ay daha birinci sınıf platform olacaksa** yatırım mantıklı. Değilse Ç4-B+C yeter.

### Ç4-E · CI (yarım gün)
`.github/workflows/ci.yml`:
- `android`: `./gradlew assembleDebug lintDebug`
- `ios`: macOS runner, `xcodebuild -scheme Fitrater -destination 'generic/platform=iOS' build`
- `functions`: `deno check supabase/functions/**/index.ts`
- `spec`: codegen diff kontrolü + `docs/PARITY.md` güncellendi mi kontrolü
İlk testler: saf mantık fonksiyonları (kredi hesabı, facet filtreleri, milestone tespiti, tarih yardımcıları).

### Ç4-F · Sürüm politikası (Ç0-3 ile birlikte)
Kökte tek `VERSION` dosyası → her iki platformun marketing sürümü. Bir sonraki yayında ikisi de **1.1.0**'a
senkronlanır. Build numaraları platforma özel ve monoton (iOS `CURRENT_PROJECT_VERSION`, Android `versionCode`).

### Ç4-G · Hijyen (30 dk)
`*.bak` sil · `Info.plist:27` yorumundaki `com.cloudgeng.mealmind` düzelt · `.gitignore`'a `supabase/.temp/`
· kökteki `file` → `docs/sessions/2026-08-05.md` · oturum loglarını `ios/store/` yerine `docs/sessions/`
altında topla (ikisi de platform-üstü içerik).

---

## 6. Sıralama (dalgalar ve bağımlılıklar)

```
Dalga 0 (bugün, ~2 sa)     Ç0-4 backend deploy ─┬─> Ç0-5 chat fallback
                            Ç4-G hijyen         └─> Ç2-13 chat portunun önkoşulu
                                                    
Dalga 1 (1–2 gün)          Ç0-1 paywall ──┬── Ç1-11 yasal blok
  "para ve mağaza"         Ç0-2 brutal    │
                           Ç0-3 sürüm     └── Ç1-10 kredi maliyetleri
                           Ç1-7 A (toggle gizle) · Ç1-8 (stub gizle + wrapped share) · Ç1-6 çipler · Ç1-9 ölü kod

Dalga 2 (3–5 gün)          Ç2-13 chat portu · Ç2-16 iOS paylaşım kartları · Ç2-15 weather edge fn
  "hızlı parite"           Ç2-17 App Links · Ç2-20 targetSdk 36 · Ç2-18 Crashlytics + event'ler

Dalga 3 (1–2 hafta)        Ç2-14 Journal birleştirme · Ç1-7 B push uçtan uca
  "derin parite"           Ç2-12 Studio 2.0 portu (prompt'ları sunucuya taşıyarak)

Dalga 4 (paralel/sürekli)  Ç4-A politika · Ç4-B codegen · Ç4-C app_config · Ç4-E CI · Ç4-D KMP kararı
```

**Kritik yol:** Ç0-4 (backend erişimi) her şeyin önünde — deploy edilmeden chat ne iOS'ta doğru çalışır
ne Android'e port edilebilir, `score-outfit` honesty coerce'ü canlı olmadan Ç0-2'nin etkisi ölçülemez.

---

## 7. Karar bekleyen sorular

1. **Android yatırımı sürecek mi?** "Evet" ise Dalga 3 + Ç4-D anlamlı; "hayır/bakım modu" ise
   Dalga 1–2 yapılır, Android özellik olarak dondurulur ve mağaza açıklamasında kapsam netleştirilir.
2. **Brutal/roast Android'den kalksın mı?** (öneri: evet — sunucu zaten `honest`'a çeviriyor)
3. **Push bu sürümde mi?** (öneri: hayır — toggle'ları gizle, Dalga 3'te uçtan uca çık)
4. **Journal hedefi "editoryal kabuk + analitik" mi?** (öneri: evet)
5. **Paywall RevenueCat Paywalls v2'ye taşınsın mı?** (öneri: Dalga 1'de native B, Dalga 4'te değerlendir)
6. **`generate-piece` prompt'ları sunucuya taşınsın mı?** (öneri: evet, Studio portundan **önce**)

---

## 8. Yayın öncesi kontrol listesi (her iki mağaza)

- [ ] Satın alma: annual + monthly + credit pack, iptal, restore, yeniden kurulum (iki platform)
- [ ] Fiyatlar mağaza/paket kaynaklı ve yerelleştirilmiş; deneme süresi iddiası pakete uygun
- [ ] Satın alma ekranlarında Terms + Privacy + otomatik yenileme bildirimi
- [ ] Pro'ya kilitli hiçbir düğme "hiçbir şey yapmıyor" durumunda değil
- [ ] AI hatası kullanıcıya hata olarak görünüyor (uydurma cevap yok)
- [ ] Sürüm/build numaraları tek kaynaktan, arşivdeki plist ile eşleşiyor
- [ ] Universal Links + App Links canlıda doğrulandı (`curl` + `pm get-app-links`)
- [ ] Hesap silme + veri dışa aktarma iki platformda çalışıyor (mağaza zorunluluğu)
- [ ] Data Safety / App Privacy beyanları güncel (konum kaldırıldıysa güncellenmeli)
- [ ] Crash raporlama iki platformda da veri topluyor
- [ ] `docs/PARITY.md` güncel
