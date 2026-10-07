# Hexic2048 APK – olvasási célú technikai elemzés

**Vizsgálat dátuma:** 2026-10-07, Europe/Budapest.

**Repository:** `ae6820dc/test-bench-1`; vizsgált állapot: `e16e2f3`.

**Vizsgált fájl:** [`../Hexic2048.apk`](../Hexic2048.apk).

## 1. Fő megállapítások

Az APK **Java Android-keretre épülő, C++/Cocos2d-x 3.17 játékmotort használó natív játék**, OpenGL ES megjelenítéssel. A játékteret nem HTML, JavaScript vagy WebView mozgatja. A csomagban WebView-támogató és Unity Ads osztályok is vannak, de ezekből nem következik WebView-játékmotor vagy Unity-játék: a tényleges játéklépéseket és csempeanimációkat a `libMyGame.so` tartalmazza.

A csempék a Cocos2d-x `MoveTo` akcióival, időalapú interpolációval mozognak. A mozgás után `CallFunc` visszahívás következik. Egy táblaszintű animációszámláló nullára csökkenése indítja az új csempe létrehozását, az állapotmentést, a játék végének ellenőrzését és – bekapcsolt Auto esetén – a következő automatikus lépést. **A vizsgált APK Auto útvonala nem a HTML-változatok rögzített 320 + 320 + 50 ms ciklusa.**

A beállított képkockacél körülbelül 60 FPS. A mozgás időtartama a sebességbeállítástól függően 300, 150, 100 vagy 75 ms. Ez a kódban található célidő, nem Android-eszközön mért idő vagy FPS.

**A vizsgálat statikus elemzés volt.** Az APK nem került telepítésre, futtatásra, újracsomagolásra vagy módosításra. Nem készült új játék vagy APK, és egyetlen meglévő HTML-program sem változott. Az APK SHA-256 lenyomata az elemzés előtt és után azonos.

## 2. Azonosítás és eredetellenőrzés

| Tulajdonság | Kiolvasott érték | Bizonyíték |
| --- | --- | --- |
| Pontos fájlnév | `1122/Hexic2048.apk` | Git-fájllista |
| Fájlméret | **16 856 587 bájt**, kb. **16,076 MiB** | Fájlrendszer/ZIP |
| APK SHA-256 | `da6756ae140af125dfbee6359d519a8c3cbda5b95398a5cb37b119150b63de0d` | `sha256sum` |
| Alkalmazásnév | `Hexic2048` | `res/values/strings.xml`, `app_name` |
| Csomagnév | `com.smartplayland.hexic2048` | Manifest és DEX `BuildConfig` |
| Verziónév | **1.5.0** | Manifest, apktool metaadat, `BuildConfig` |
| Verziókód | **42** | Ugyanezek |
| Build-típus | `release`, `DEBUG=false` | DEX `BuildConfig` |
| Minimum Android API | **16**, Android 4.1 | Manifest `uses-sdk` |
| Target / compile SDK | **30 / 30**, Android 11 | Manifest |
| Natív ABI-k | `armeabi-v7a`, `arm64-v8a` | `lib/` |
| Cocos2d-x verzió | **3.17** | `cocos2d::cocos2dVersion()` által visszaadott `cocos2d-x-3.17` karakterlánc |
| NDK-jelölés | **r21, 6113669** | ELF Android build-megjegyzés, `file` |

A ZIP 1980-as, illetve érvénytelen hónap/nap értékű bejegyzés-időbélyegeket mutat. Ezekből **nem állapítható meg a valódi kiadás vagy fordítás dátuma**. A tanúsítvány dátuma sem a játék builddátuma.

A `META-INF/CERT.RSA` tanúsítványt a `keytool -printcert -jarfile` kiolvasta: subject/issuer `CN=Hwangkyoosung, OU=Joylol`; SHA-256 tanúsítványlenyomat `1C:27:B6:0F:45:ED:CA:10:54:77:B3:0D:6B:DC:1C:44:A7:26:6B:CB:61:CA:C3:B1:8F:6D:7C:D8:0D:3A:EE:2A`. A kiolvasott tanúsítvány SHA1withRSA algoritmusú, 1024 bites RSA-kulccsal; a Java ezekre figyelmeztetett. Ez **tanúsítvány-kiolvasás**, nem teljes modern APK-aláírás-ellenőrzés vagy a kiadó hitelességének igazolása. `apksigner` nem állt rendelkezésre; v2/v3 aláírás-ellenőrzési eredményt nem állítok.

## 3. Módszer és eszközök

Elérhető volt Java/OpenJDK 21, Python, `unzip`, `readelf`, `nm`, `c++filt`, `strings`, `file` és `keytool`. Kezdetben **nem volt telepített `apktool`, `jadx`, `aapt`, `apkanalyzer` vagy `adb`**. Az elemzéshez külön, repositoryn kívüli munkamappába letöltöttem a hivatalos **apktool 2.11.1** és **jadx 1.5.3** kiadásokat; a natív kód olvasásához **pyelftools 0.33** és **Capstone 5.0.9** került ugyanoda.

- ZIP-leltár, kibontás és CRC-ellenőrzés: Python `zipfile`; `testzip()` nem talált hibás bejegyzést.
- Manifest/erőforrások/smali: apktool dekódolás, építés nélkül. A két DEX külön smali-könyvtárba dekódolódott.
- Java-jellegű DEX-visszafejtés: jadx; a futás **6 jelentett hibával** fejeződött be, ezért nem tekinthető hibamentes, teljes forrásrekonstrukciónak. Egyes függvények `Method not decompiled` jelölést kaptak; például `CustomUrlReceiveActivity.onCreate`, valamint több hirdetési és HTTP-segédfüggvény. Ezek hiányzó részeit nem pótoltam feltételezett kóddal.
- A vizsgált `Cocos2dxRenderer.onDrawFrame` Java-visszafejtését a dekódolt DEX-smali `nativeRender`, időintervallum- és `Thread.sleep` utasításaival is ellenőriztem.
- Natív rész: ELF-dinamikus szimbólumok, C++ névfeloldás, ARM64 disassembly, PLT-hívások és ELF-relokációk alapján feloldott virtuális függvénytáblák.

A részletes gépi kódos következtetések **az ARM64 könyvtárból** származnak. Az ARMv7 változat fejlécét és megfelelő játékfüggvény-szimbólumait is ellenőriztem, de nem történt a két architektúra minden gépi utasítására kiterjedő ekvivalenciabizonyítás.

Az eszközök, a kibontott tartalom és a nyers visszafejtések a `/workspace/work/apk-analysis/` ideiglenes munkaterületen maradtak. A repositoryba csak ez a jelentés kerül. A natív címek itt ELF virtuális címek, nem APK-fájlon belüli byte-offsetek; futtatáskor az ASLR miatti betöltési bázist hozzájuk kellene adni.

## 4. APK-szerkezet

**208 ZIP-bejegyzés**; összes kibontott bejegyzésméret **41 425 962 bájt**. A bejegyzések tömörített adatméretének összege 16 816 082 bájt; ez nem azonos a teljes APK méretével, mert az archívum fejlécei és egyéb szerkezeti adatai is helyet foglalnak.

| Terület | Tartalom |
| --- | --- |
| `AndroidManifest.xml` | 15 780 bájtos bináris Android XML |
| `resources.arsc` | 210 336 bájtos fordított erőforrástábla |
| `classes.dex` | 8 566 616 bájt |
| `classes2.dex` | 723 600 bájt |
| `lib/arm64-v8a/libMyGame.so` | 17 472 392 bájt |
| `lib/armeabi-v7a/libMyGame.so` | 13 107 568 bájt |
| `assets/` | 23 bejegyzés: képek, atlasz, bitmap font, hangok, lokalizáció |
| `res/` | 110 bejegyzés: Android UI-erőforrások és több sűrűségű képek |
| `META-INF/` | 27 bejegyzés: aláíráshoz tartozó állományok és AndroidX-verziójelölések |
| Gyökérbeli `.properties` fájlok | Firebase/Google Play Services összetevő-verziók |

A ZIP-ben **nincs HTML-, JavaScript-, Lua-forrás, Unity `libunity.so`, Flutter `libflutter.so` vagy külön Unity/Flutter játékadatcsomag**. Önmagában a fájlhiány nem kizárási bizonyítás minden technológiára; itt a megfigyelt Java → JNI → natív játékfüggvény hívási út adja a döntő bizonyítékot.

### 4.1. DEX-ek

Mindkét DEX fejléce `dex\n035\0`, tehát DEX 035 formátumú.

| Fejlécben szereplő darabszám | `classes.dex` | `classes2.dex` |
| --- | ---: | ---: |
| String ID | 50 690 | 6 825 |
| Type ID | 12 258 | 1 212 |
| Method ID | 65 502 | 6 368 |
| Class definition | 10 512 | 617 |

A Method ID-szám a hivatkozási táblát jelenti, **nem ennyi alkalmazássaját metódus implementációját**. A nagy első DEX és a `MultiDexApplication` összhangban vannak a multidex csomagolással. A jadx 5 923 feldolgozási egységet jelzett; ez a visszafejtő feldolgozási darabszáma, nem a DEX-fejléc osztályszámának helyettesítője.

Főbb csomagok: `com.smartplayland.hexic2048`, `com.joylol.joylolSDK`, `org.cocos2dx.lib`, Google/Firebase, Unity Ads, AndroidX/Android support multidex, valamint HTTP-segédkönyvtárak. A DEX-ek túlnyomó részének jelenléte SDK-kból ered; a játék fő szabályai nem Java-kódként vannak ezekben.

### 4.2. Natív könyvtárak

Mindkét `libMyGame.so` dinamikusan linkelt, **stripped** ELF. A részletes debug-információ és a szokásos teljes `.symtab` hiányzik; a `.dynsym` azonban sok beszédes C++ játék- és motorfüggvénynevet megőrzött.

ARM64: ELF64/AArch64, Android 21 buildjelölés; ARMv7: ELF32/ARM EABI5, Android 16 buildjelölés. Az ARM64 minimuma nem írja felül a teljes APK manifestben deklarált API 16 minimumát: az Android 64 bites támogatása eleve későbbi.

Az ARM64 `DT_NEEDED` függőségei: `libOpenSLES.so`, `libGLESv2.so`, `liblog.so`, `libandroid.so`, `libEGL.so`, `libdl.so`, `libc.so`, `libm.so`. Ezek a hangkezelést, GL/EGL megjelenítést és Android rendszerintegrációt támasztják alá. A Cocos2d-x és több további komponens kódja a nagy `libMyGame.so` állományon belül van, nem külön Cocos `.so` fájlként.

Natív könyvtár-lenyomatok:

```text
arm64-v8a:   2cb148649124a37685c17ae5b2b6777918da8cb954349729d47ecd7d83f2b140
armeabi-v7a: d8def08f9520444e8e971f360b654a99a91515dd1908a149c029bdc9b7fadaf1
```

### 4.3. Erőforrások és assets

A játék képi atlasza `assets/image/img_common.plist` + `img_common.png`. A `baseCell::initCustom` a `SpriteFrameCache::getSpriteFrameByName` függvényt hívja: a csempe grafikai alapja sprite, nem Android layout-cella vagy DOM-elem. A szöveghez `fonts/helvetica80.fnt` + `helvetica80.png` bitmapfont-erőforrás található. Vannak külön oktatóképek (`image/howto/howto1.png`–`howto3.png`), ikonok és WAV hangok, köztük `sound/cell_move.wav`, `click.wav`, `you_win.wav`.

A játék assets-lokalizációi angol, japán és koreai fájlokban vannak; az angol fájlban `Grid`, `Auto`, `Mode`, `Undo`, `Corner`, `Swing`, `Swirl`, `Random`, `Classic`, `Survival`, `X-Tile`, `Animation Speed` és sebességcímkék szerepelnek. Hex 2×2×2–5×5×5, valamint Box 4×4–7×7 feliratok láthatók. Ezek **erőforrásként rendelkezésre álló feliratok**, nem minden runtime menühelyzet teljes kipróbálásának eredménye.

Az Android `res/` része külön UI-támogatást tartalmaz: többek között 23 layout-bejegyzést különböző konfigurációkkal, állapot/drawable erőforrásokat és hat launcher ikonméretet. A játékassets és az SDK-k Android-erőforrásai megkülönböztetendők.

## 5. Manifest és Android-integráció

Indító Activity: `com.smartplayland.hexic2048.AppActivity`, `MAIN` / `LAUNCHER`; álló orientáció, teljes képernyős cím nélküli téma, `singleTask`, `keyboardHidden|orientation|screenSize` konfigurációváltás-kezelés. Az alkalmazás `android.support.multidex.MultiDexApplication`, `allowBackup=true`; a `android.app.lib_name=MyGame` metaadat választja ki a natív könyvtárat.

A manifest 11 Activityt, 7 Service-t, 4 Receivert és 2 Providert deklarál. Ezek közt Google/AdMob, Unity Ads, számlázási, Firebase-, bejelentkezési és értesítési komponensek vannak. A `AlarmReceiver` külön `:remote` folyamatot kap. Egy DEX-ben meglévő Activity – például a részlegesen visszafejtett `CustomUrlReceiveActivity` – puszta osztályjelenléte nem bizonyítja, hogy a manifestből indítható komponens.

Az OpenGL ES 2.0 funkció `glEsVersion=0x00020000` értékkel szerepel. A manifest 12 `uses-permission` deklarációja:

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`;
- `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `WRITE_SETTINGS`;
- `WAKE_LOCK`, `VIBRATE`;
- `com.android.vending.BILLING`;
- `com.google.android.c2dm.permission.RECEIVE`;
- `com.smartplayland.hexic2048.permission.C2D_MESSAGE`;
- `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE`.

A saját `C2D_MESSAGE` permission `signature` védelmi szintű. A deklarált jogosultság **nem egyenlő a modern Android által ténylegesen megadott hozzáféréssel**, és nem bizonyítja önmagában, hogy az adott képesség minden játékútvonalon használatban van.

Az SDK-verziójelölések közül közvetlenül kiolvasható Firebase Core/Analytics 17.0.0, Firebase Ads 18.0.0 és Play Services Games 18.0.0; Unity Ads `BuildConfig.VERSION_NAME=2.2.0`. A Unity Ads jelenléte hirdetési integrációt mutat, **nem Unity játékmotort**. A csomag statikus adataiból a mai külső szolgáltatások elérhetősége vagy működése nem következik.

## 6. Végrehajtási architektúra: miért natív játék?

A megfigyelt betöltési út:

```text
AppActivity
  → joylolCocos2dxActivity
  → Cocos2dxActivity
  → System.loadLibrary(manifest android.app.lib_name = "MyGame")
  → Cocos2dxGLSurfaceView + Cocos2dxRenderer
  → JNI nativeRender()
  → cocos2d::Director::mainLoop()
  → Scheduler / ActionManager / scene graph / OpenGL renderer
```

A `Cocos2dxActivity` a GL view-hoz `Cocos2dxRenderer` példányt kapcsol. A `Cocos2dxRenderer` JNI metódusai közt `nativeInit`, `nativeRender`, érintés-, felületváltás-, pause/resume kezelők vannak. Az ARM64 `Java_org_cocos2dx_lib_Cocos2dxRenderer_nativeRender` valóban a `Director::mainLoop()` útvonalára lép.

A Cocos keretben `Cocos2dxWebViewHelper`, a Joylol Activityben WebView-segéd és például user-agent lekérés is van, a hirdetési SDK pedig saját WebView-okat tartalmaz. **Ezek mellékfunkciók/támogatási lehetőségek:** a játéktábla vizsgált mozgatási útvonalán nem WebView, CSS-transition, `setTimeout` vagy JavaScript `requestAnimationFrame` hajtja az animációt. Cocos általános script-engine szimbólumokból sem következik JavaScriptben írt játék.

## 7. Játékmotor és csempeszabályok

A beszédes natív osztályok `baseGameBoard`, `hexGameBoard`, `rectGameBoard`, `baseCell`, `hexCell`, `rectCell`, `GameManager`, `mainScene`, `undoSystem`. Közös motorhoz külön hexagonális és négyszögletes táblaváltozat tartozik.

### 7.1. Mozgási logika

`baseGameBoard::moveDir` irányonként előre tárolt vonalakat jár be; a tényleges tömörítést/összeolvasztást `moveLine` végzi. A vonalak `short` indexlisták; az ARM64 kód negatív végjelölőket vizsgál, és legfeljebb kilenc pozíciót kezel egy ilyen sorban. Az alap tárolás 9×9-es indexeléssel látható: a cellaindexekből osztás/maradék műveletekkel állnak elő a koordináták, a tábla cellamutatókat és célpozíciókat külön tárol.

A `moveLine` sorrendben keresi az üres helyeket és az összevonható csempéket. Egyenlő belső számkód esetén összeolvasztási ág következik, a `0xFE` kódú különleges csempe kivételével; ezt az `X tile cannot be summed` erőforrás is alátámasztja. Összeolvadáskor `setRemoveTargetCell`, `incNumber` és `moveAnimation` hívások történnek. A tábla cellamutatói már a célhelyre rendeződnek, miközben a sprite még oda tart.

A natív `incNumber` a függőben levő számkódot eggyel növeli. A pontszám-útvonal `ldexp`-et használ; az értékek kettes hatványaihoz tartozó számozási logika látható. Ez **nem a korábbi HTML-programok minden csempét 1-esként tartó változata**, és nem indokolt automatikusan azonos játékszabályokat feltételezni.

Az üres pozíció kiválasztásánál `arc4random` szerepel. A `makeNewCell` előbb véletlen pozíciókat próbál, majd korlátozott sikertelen próbálkozás után keresési ágra tér. A véletlenszám-forrás és a működési ág visszafejthető, de egy korábbi felhasználói játék pontos véletlen eseménysora az APK-ból nem állítható helyre.

### 7.2. Input és állapotvédelem

Az Android érintési eseményeket a GL view `queueEvent` segítségével viszi a renderelő szálra; nem az Android UI-szál közvetlenül módosítja a natív csempéket. A `baseGameBoard::onTouchesMoved` elutasítja a bemenetet, ha a táblaszintű animációszámláló pozitív. A megfigyelt útvonalon 10-es mozgási küszöb, továbbá időbeli ismétléskorlátozás is van. Az input koordinátái és küszöbei a natív view/design koordinátakezeléshez tartoznak, nem feltétlenül fizikai képernyőpixelek.

Az Auto engedélyezésekor `setAutoType` a tábla touch-kezelését kikapcsolja, Auto leállításakor visszaengedi; `setKeepScreenOn` integráció is látszik. Ez csökkenti a kézi és automatikus lépések közvetlen ütközésének lehetőségét.

Az Undo, a Classic/Survival/X-Tile mód és a helyi/felhő mentés külön függvényekkel jelen vannak. A `makeNewCellInSurvival` és `makeNewCellInSurvival2` elkülönült időzített létrehozási útvonalak; az itt részletesen leírt Auto/move befejezési lánc nem állítja, hogy a Survival összes életciklus-esete azonos a Classic-éval.

## 8. Csempemozgás és animáció – részletes vizsgálat

### 8.1. A mozgás életciklusa

Az ARM64 `baseCell::moveAnimation` a következő műveleteket mutatja:

1. Növeli a csempe saját és a tábla közös aktív animációszámlálóját.
2. Ellenőrzi a korábban tárolt mozgási akciót; szükség esetén leállítja és elengedi azt.
3. A sebességbeállításból kiszámítja az időtartamot.
4. Létrehozza a `MoveTo(duration, targetPosition)` akciót.
5. A mozgáshoz egy `CallFunc` befejezési visszahívást fűz `Sequence`-ben.
6. A node-on futtatja és referenciával megőrzi az akciót.

A mozgás tehát nem pillanatnyi sprite-pozíciócsere: a logikai táblafrissítés mellett külön, véges időtartamú vizuális mozgás fut. A célhelyig megtett távolság nem szorzó az itt látott időképletben; hosszabb csúszás ugyanazon beállítás mellett nagyobb vizuális sebességgel ér célba.

A befejezési `moveAnimationComplete` feldolgozza az összeolvadásban eltávolítandó célcsempét, megállítja annak akcióit, eltávolítási útvonalat hív, aktiválja az értékváltozás animációját, majd csökkenti az animációszámlálókat. A sprite-ek nem minden képkockán kerülnek új DOM-szerű struktúrából felépítésre: meglevő Cocos node-ok pozíciói/skálái változnak.

### 8.2. Pontos, statikusan kiolvasható időképletek

A speed mező három XOR-olt értékpárral tárolt integer; a kód összehasonlítja a visszanyert értékeket. A beállító menü 0–3 értékeket tárol, a feliratok `Slow`, `Medium`, `Fast`, `Very Fast`. A `privateInfo` alapértéke és a hiányzó adat JSON-alapértéke **1**, vagyis Medium; egy létező felhasználói mentés ezt felülírhatja.

Legyen `s` ez a sebességkód. A binárisban a `0.2` double-konstans `0xd6b5d0` címen található; a mozgás szorzója `1.5`:

```text
mozgás:            Tmove = 0,2 / (s + 1) × 1,5 másodperc
első skálafázis:   Tscale1 = 0,2 / (s + 1) másodperc
második fázis:     Tscale2 = Tscale1 × 0,5
```

| Sebesség | `s` | Mozgás | Skálafázis 1 | Skálafázis 2 | Skálasorozat összesen |
| --- | ---: | ---: | ---: | ---: | ---: |
| Slow | 0 | 300 ms | 200 ms | 100 ms | 300 ms |
| Medium | 1 | 150 ms | 100 ms | 50 ms | 150 ms |
| Fast | 2 | 100 ms | kb. 66,67 ms | kb. 33,33 ms | 100 ms |
| Very Fast | 3 | 75 ms | 50 ms | 25 ms | 75 ms |

Ha a három sebességmásolat ellenőrzése nem egyezik, a megfigyelt ágak mozgáshoz 0,3 s, skálázáshoz 0,2 és 0,1 s fallback értékeket használnak. Ezek nem eszközön mért időtartamok, hanem lebegőpontos konstansokból és hívási paraméterekből kiolvasott értékek.

A `changeAnimation` előbb nullás skálát állít, majd `ScaleTo` akcióval **1,1× alapméretre** növel, végül az alapméretre visszaáll; az `1.1` konstans `0xd6e760` címen van. Két `ScaleTo` akciót fűz `Sequence`-be. Ez megjelenési/értékváltozási effektus, nem külön frame-sprite sorozat. A számszöveg frissítése és a pontszámváltozás is ebben az útvonalban látható.

### 8.3. Interpoláció és köztes képkockák

A `cocos2d::ActionInterval::step(float)` első futási lépésben nullázza az eltelt időt, később hozzáadja az adott `dt`-t, majd lényegében ezt számolja:

```text
t = clamp(elapsed / duration, 0, 1)
update(t)
```

A `MoveTo` a kiinduló és célpozícióból eltérésvektort képez; az örökölt `MoveBy::update` időarányos pozícióinterpolációt végez, és a többi mozgás miatti pozícióváltozást is kezeli. A játék `moveAnimation` útvonalán nem láttam `Ease*` wrapper hívást: a feltárt mozgás közvetlen `MoveTo`, tehát ezen az úton lineáris időparaméterrel dolgozik.

60 FPS esetén 150 ms névleges mozgás kb. kilenc képkockaintervallumot fed le; 75 ms kb. négy-ötöt. **Ez elméleti arány, nem mért megjelenített képkockaszám.** A kezdő tick, akcióütemezés, vsync és a sorozat befejezési időpontja miatt a tényleges darabszám ettől eltérhet.

Az időalapú interpoláció nem garantál minden köztes állapot megjelenítését: ha a renderelő szál késik és nagy `dt` érkezik, a következő pozíció messzebb kerülhet. A feltárt kód nem rajzolja visszamenőleg a kimaradt képkockákat. **Az APK statikus vizsgálatából nem jelenthető ki, hogy a csempék soha nem ugranak vagy egyetlen látható köztes állapot sem marad ki.**

## 9. Auto mód – sorrend, továbbhaladás és korlátok

### 9.1. Indítás és lépésválasztás

A `setAutoType(int, bool)` a tábla Auto-típusát tárolja. Pozitív típusnál nullázza a próbálkozási/választási segédállapot egy részét, kikapcsolja a touch-kezelést és közvetlenül a virtuális `autoMove()` útvonalra lép. Külön `hexGameBoard::autoMove()` és `rectGameBoard::autoMove()` implementáció van.

Négy belső Auto-típushoz eltérő irányválasztási ágak tartoznak; a felületi erőforrások Corner, Swing, Swirl és Random módokat neveznek meg. A determinisztikus módok utolsó irányt és váltakozási/próbálkozási állapotot használnak; nem egyszerűen ugyanazt a véletlen algoritmust futtatják.

A Random ág konkrétan:

- hex táblán `arc4random()` modulo 6 alapján választ;
- rect táblán `arc4random() & 3` alapján a **`[0, 1, 3, 4]` irány-enum táblából** választ.

A négy rect értéket nem nevezem el önkényesen „fel/jobbra/le/balra” iránynak: a koordináta-konvenciót ehhez külön kellene teljesen leképezni. A biztos megállapítás a négy elemű numerikus táblából való választás.

**A Random ág itt nem készít előre listát a sikeresen mozgatható irányokról.** Választott iránnyal `moveDir` fut; ha nincs mozgás, `checkAutoMove` további próbálkozásokat indíthat. A feltárt kiválasztási részben nincs minimax/expectimax keresés, ürescellaszám-optimalizálás vagy „legnagyobb összeolvadási nyereség” kiértékelés. Ez nem egyenértékű a korábbi HTML 14-es `movable()`-listából való Random választásával.

### 9.2. A következő Auto lépés feltétele

Az alábbi pszeudokód a feltárt hívási sorrendet szemlélteti; nem eredeti C++ forrás:

```text
setAutoType(típus)
  → autoMove()
  → moveDir(választott irány)
  → moveLine(...) minden érintett vonalra
  → mozgó csempék MoveTo + CallFunc akciói

moveAnimationComplete()
  → összeolvadás és eltávolítás kezelése
  → aktív animációszámláló csökkentése

amikor a táblaszámláló eléri a nullát:
  → makeNewCell(-1)
  → aktuális táblakép / Undo-lépés / játékállapot mentése
  → Undo gomb frissítése
  → gameEndCheck()
  → játék vége, VAGY bekapcsolt Auto esetén autoMove()
```

A központi kapu `baseGameBoard::addAnimationCnt`: a számláló nulla értékén fut a felsorolt folytatás. A virtuális hívások célját az ELF-vtable relokációival ellenőriztem: `+0x650` táblakép-kiolvasás, `+0x658` `gameEndCheck`, `+0x680` `autoMove`, `+0x688` `checkAutoMove` az ARM64 objektum virtuális táblájában.

**Lényeges finomság:** a feltárt `changeAnimation` skálaeffektus nem növeli ezt a mozgási számlálót. A nulla számláló után az új csempe létrehozása és a következő Auto lépés indítása között ezen az útvonalon nincs külön „várd meg a teljes spawn/pop skálaeffektust” kapu és nincs 50 ms szünet. A skálaanimáció így átfedhet a következő mozgással. A tényleges frame-en belüli akciókezelés további képkockahatárokat jelenthet; a névleges Auto periódust emiatt sem szabad egyszerűen valamelyik időösszeggel mért tényként azonosítani.

A számláló arra ad biztosítékot, hogy a program **logikailag** a regisztrált mozgásakciók befejezése után folytat. Nem bizonyítja, hogy a mozgás végállapotát a kijelző külön teljes képkockán megmutatta, mielőtt a következő akció létrejött: az akcióvisszahívások a frame frissítési szakaszában történnek, a render később következik.

### 9.3. Sikertelen irányok és leállítás

A `moveDir` visszajelzési útja a megmozgatott/összevont elemek számával hívja `checkAutoMove`-ot. Sikeres mozgásnál a hibáspróbálkozás-számláló nullázódik; eredménytelen próbánál növekszik, és a mód saját irányváltási logikája lép működésbe.

Az ARM64 hex implementáció a növelés előtti számlálót `0x2F`-fel (47), a rect implementáció `0x1F`-fel (31) hasonlítja össze: nulláról indulva a 48., illetve 32. sikertelen próbánál kerülhet a leállítás vizsgálatához. Ehhez két táblamező összehasonlítása is társul a leállítási feltételben. Ezért a kód nem írható le feltétel nélküli „pontosan N próbálkozás után mindig leáll” szabállyal. Ha a feltétel teljesül, `mainScene::stopAuto(false)` következik. Más ágakon új `autoMove()` indulhat azonnal, akció/időzítő-várakozás nélkül.

Ezek a gyors, eredménytelen próbák egy frame frissítésén belül több irányvizsgálatot is okozhatnak. A pontos legrosszabb hívásmélység, elérhető állapotok és megállási garancia formális bizonyítása nem történt meg; ebből önmagában sem stackhibát, sem tényleges akadást nem állítok.

## 10. Képkockakezelés, renderelés és teljesítmény

### 10.1. A frame célértéke és Android-oldali ütemezése

`AppDelegate::applicationDidFinishLaunching` a `0x3c888889` float-bitmintát adja `Director::setAnimationInterval`-nak: ez kb. **0,0166666675 s = 1/60 s**. A Java renderer alapértéke 16 666 668 ns, és `setAnimationInterval(float)` a másodperces paramétert nanomásodpercre alakítja.

Az `onDrawFrame` két útvonalat tartalmaz:

- körülbelül 1/60 s vagy rövidebb célintervallumnál közvetlenül `nativeRender()`;
- hosszabb intervallumnál `System.nanoTime()` alapján ellenőrzi az eltelt időt, szükség esetén egész milliszekundumos `Thread.sleep`, majd `nativeRender()`.

A játék által beállított 60 FPS-es cél a közvetlen útvonalnak felel meg. A Java-réteg ezen az ágon nem saját, minden frame előtti alvással garantálja a 60 FPS-t; a tényleges ütemezést a GLSurfaceView/EGL/driver és Android kijelzőút is befolyásolja. A feltárt saját kódban nem egy alkalmazássaját `Choreographer` callback a fő frame-motor, hanem a `GLSurfaceView.Renderer`.

### 10.2. Natív frame-frissítés

A `Director::calculateDeltaTime` monoton `std::chrono::steady_clock` órát használ, az időeltérésből másodperces `dt`-t képez, és a negatív értéket nullára korlátozza. Van nullás következő-delta kezelési ág is. A feltárt függvényben nem látható olyan felső korlát, amely minden hosszú frame `dt`-jét például 1/60 s-re szorítaná.

A `Director::drawScene` megfigyelt fő sorrendje: delta/állapotfrissítés, update-események és `Scheduler::update(dt)`, renderer/FBO törlés, a scene frame-frissítése és renderparancsainak előállítása, GL-renderelés, frame utáni események és statisztikakezelés. A `Director::init` létrehozza és schedulerhez kapcsolja az `ActionManager`-t; az akciófrissítés ezen a frame-útvonalon történik, nem csempénként külön Java timerből.

A globális update → render sorrend jól kiolvasható. Az összes konkrét sprite, címke, overlay és reklám pontos képernyőn belüli rajzolási sorrendjének teljes rekonstruálása – futó scene graph, z-sorrend, render queue és GL állapotok együtt – nem készült el. Ezért nem állítok minden objektumra kiterjedő render-order bizonyítást.

### 10.3. A feltárt megoldás teljesítményi következményei

**A kódból alátámasztható előnyök:** a mozgás egyszer létrehozott akciókon és meglevő sprite-okon fut; nincs minden képkockán HTML-layout/DOM-frissítés. Az atlasz és bitmapfont támogatja a grafikai adatok újrahasználatát. A GL-szálra sorolt input és az animációszámláló rendezik a lépésfolytatást.

**Mérendő területek:** mozgásonként `MoveTo`, `CallFunc`, `Sequence` objektumok jönnek létre; összeolvadás/létrehozás skálaakciókat is indít. A nullára csökkenő számláló útvonalán mentési műveletek, Undo-adatkezelés és UI-frissítés történik, vagyis a következő Auto lépés előtt a renderelő szálon is lehet munka. A sikertelen Auto próbálkozások gyors ismétlése szintén CPU-terhelés forrása lehet. A reklámok, hang, hálózati SDK-k és háttér/előtéres életciklus további változókat jelentenek.

Ezek **lehetséges költségek, nem kimért szűk keresztmetszetek**. Az atlasz jelenléte nem bizonyít konkrét draw-call számot vagy tökéletes batchinget. A nagy `.so` vagy a sok DEX-osztály nem közvetlen FPS-mérőszám; a fájlméret és a futási sebesség közé nem lehet egyszerű egyenlőségjelet tenni.

A `applicationDidEnterBackground` a Director animációját leállító útvonalat tartalmaz, az előtérbe kerülés Cocos-szálra ütemezett folytatást használ. A tényleges pause/resume utáni vizuális viselkedést futtatás nélkül nem teszteltem.

## 11. Bizonyosság és a visszafejtés korlátai

| Állítás / kérdés | Megbízhatóság és korlát |
| --- | --- |
| Fájlnév, byte-méret, hash, ZIP-bejegyzések | Közvetlenül ellenőrzött |
| Csomagnév, verzió, SDK, komponensek, permissionök | Manifestből, részben DEX-szel keresztellenőrzött |
| Cocos2d-x 3.17, GL/JNI motor | Tényleges betöltési és renderhívási lánccal alátámasztott |
| Mozgás- és skálaidőképletek | ARM64 konstansokból és hívási paraméterekből kiolvasott |
| 60 FPS cél | Kódból igazolt; **nem tényleges FPS-mérés** |
| Auto folytatási sorrend | Animációszámláló és vtable-célok alapján erős statikus bizonyíték |
| Random irányválasztás | ARM64 ágakból és konstans iránytáblából igazolt |
| Összes állapotban teljes játékszabály | Csak részlegesen rekonstruált; szélső esetek nem dinamikusan teszteltek |
| ARMv7 és ARM64 teljes működési azonossága | Nem bizonyított; közös megfelelő szimbólumok jelen vannak |
| Tényleges FPS, jank, kihagyott frame, akkufogyasztás, RAM | Eszközös mérés nélkül nem állapítható meg |
| Minden csempe végállapota külön képkockán látszik-e | A statikus callback-sorrendből nem következik |
| Eredeti C++/Java forrás, kommentek, buildbeállítások | Nem állíthatók teljesen helyre a fordított csomagból |
| Minden JADX-visszafejtett függvény helyes-e | Nem; részleges dekompilációs hibák dokumentáltak |
| Kiadó/terjesztési eredet, mai szolgáltatásműködés | Tanúsítvány/SDK-jelenlét alapján nem igazolható |

A „teljes körű” vizsgálat itt az APK azonosítását, összes fő szerkezeti területét és a kért motor/animáció/Auto útvonalak részletes statikus elemzését jelenti. **Nem minden SDK minden függvényének teljes visszafejtését vagy az alkalmazás összes futási állapotának bizonyítását.**

## 12. Milyen mérés dönthetné el a tényleges simaságot?

Ebben a feladatban ezek nem futottak le, és nincs belőlük eredmény: Android SDK/`adb`, ARM-kompatibilis teszteszköz és tényleges APK-futtatás nem állt rendelkezésre.

Egy későbbi, külön engedélyezett eszközös vizsgálatban az eredeti APK módosítása nélkül érdemes lenne Auto módonként, táblaméretenként és a négy animációsebességen mérni a frame-időket. Perfetto/SurfaceFlinger frame timeline és a GL-szál trace-e elkülöníthetné az akciófrissítés, mentés, GL-render, reklám és kijelzőprezentáció költségeit. Vizuális felvételnek időbélyegzett csempemozgás-mintával kellene együtt futnia: a 60 FPS-es cél önmagában nem mondja meg, mely köztes állapotokat látta a felhasználó. A GL-játékhoz a hagyományos Android View `gfxinfo` adatai önmagukban nem feltétlenül fedik le a teljes rajzolási útvonalat.

## 13. Bizonyítékhelyek és megismételhetőség

### 13.1. Fontos ARM64 függvénycímek

Az alábbi címek a vizsgált `lib/arm64-v8a/libMyGame.so` lenyomathoz tartoznak:

| Függvény | ELF virtuális kezdőcím |
| --- | --- |
| `AppDelegate::applicationDidFinishLaunching()` | `0x6e47b0` |
| `baseGameBoard::addAnimationCnt(int)` | `0x6faa14` |
| `baseGameBoard::moveDir(...)` | `0x6fc948` |
| `baseGameBoard::moveLine(...)` | `0x6fcae8` |
| `baseGameBoard::setAutoType(int,bool)` | `0x6fcda4` |
| `baseCell::changeAnimation()` | `0x6fe2d0` |
| `baseCell::moveAnimation(...)` | `0x6fe4c0` |
| `baseCell::moveAnimationComplete()` | `0x6fe6d0` |
| `rectGameBoard::autoMove()` / `checkAutoMove(...)` | `0x6fa800` / `0x6fa91c` |
| `hexGameBoard::autoMove()` / `checkAutoMove(...)` | `0x6ffa34` / `0x6ffbbc` |
| `setting1Scene::showCurSpeed()` | `0x712b94` |
| `setting1Scene::actionMenu(...)` | `0x713040` |
| `privateInfo::clear()` / `resetData(...)` | `0x6ed070` / `0x6ed1ec` |
| JNI `nativeRender` | `0x71db64` |
| `cocos2d::Director::drawScene()` | `0x973b34` |
| `cocos2d::Director::calculateDeltaTime()` | `0x973e28` |
| `cocos2d::ActionInterval::step(float)` | `0x8d7434` |
| `cocos2d::MoveBy::update(float)` | `0x8dacb8` |
| `cocos2d::cocos2dVersion()` | `0x9fbf6c` |

Néhány döntő utasításrészlet, szöveges hívásfeloldással:

```text
0x6e4820–0x6e4828: float 0x3c888889 előállítása
0x6e4834: Director::setAnimationInterval(float)

0x6fe56c: 0.2 double betöltése
0x6fe584–0x6fe598: speed + 1; 0.2 / (speed + 1)
0x6fe59c–0x6fe5a8: szorzás 1.5-tel
0x6fe5c0: MoveTo::create(duration, target)
0x6fe5f8: CallFunc::create(...)
0x6fe608: Sequence::create(...)

0x6faa3c–0x6faa44: táblaszámláló += paraméter
0x6faa4c: nem nulla számláló → visszatérési ág
0x6faa58: makeNewCell(-1)
0x6fab98: undoSystem::saveCurGameData(...)
0x6fabb8–0x6fabbc: virtuális gameEndCheck
0x6fabd0–0x6fabe4: Auto aktív → virtuális autoMove

0x8d7470–0x8d7478: elapsed += dt
0x8d7484–0x8d7490: elapsed / duration; korlátozás [0,1]-re
```

### 13.2. Olvasási parancsok

A dekódolás eredményét külön munkamappába kell írni; sem `apktool b`, sem APK újraaláírás, sem forrás-APK felülírás nem szükséges:

```sh
sha256sum 1122/Hexic2048.apk
unzip -l 1122/Hexic2048.apk

java -jar /workspace/work/apk-analysis/tools/apktool.jar d \
  -p /workspace/work/apk-analysis/framework \
  -o /workspace/work/apk-analysis/apktool \
  1122/Hexic2048.apk

XDG_CONFIG_HOME=/workspace/work/apk-analysis/config \
XDG_CACHE_HOME=/workspace/work/apk-analysis/cache \
  /workspace/work/apk-analysis/tools/jadx/bin/jadx \
  -d /workspace/work/apk-analysis/jadx 1122/Hexic2048.apk

readelf -h /workspace/work/apk-analysis/extracted/lib/arm64-v8a/libMyGame.so
readelf -d /workspace/work/apk-analysis/extracted/lib/arm64-v8a/libMyGame.so
nm -D -C /workspace/work/apk-analysis/extracted/lib/arm64-v8a/libMyGame.so
keytool -printcert -jarfile 1122/Hexic2048.apk
```

Az ARM64 utasítások elemzéséhez a Capstone az ELF `.dynsym` címei/méretei alapján kapta a kódbájtokat; a PLT-nevek `.rela.plt`, a virtuális hívási célok `.rela.dyn` alapján oldódtak fel. Ez a jelentésben szereplő címekkel és az adott könyvtárhash-sel ellenőrizhető, és nem támaszkodik a szimbólumnevekből önmagukban kitalált működésre.

**Megőrzési ellenőrzés:** az elemzés végén az APK SHA-256 értéke ismét `da6756ae140af125dfbee6359d519a8c3cbda5b95398a5cb37b119150b63de0d`; a Git-változtatás kizárólag a `1122/analysis/REPORT.md` új jelentésre korlátozódik.
