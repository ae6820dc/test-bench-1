# Hexic2048 – Native Motion Engine: megvalósítás és mérés

Vizsgálat: 2026-10-07 (UTC). A játék: [sample1.html](sample1.html). Közvetlen Pages-cím: <https://ae6820dc.github.io/test-bench-1/experiments/native-motion-engine/sample1.html>.

Az új, önálló HTML-program a módosított HTML-játékszabályokat egy eseményvezérelt, Web Animations API-ra (WAAPI) épített motorral futtatja. A mozgás végét az `Animation.finished` ígéret jelzi; az Auto folytatásához az adott lépés **minden mozgásának** be kell fejeződnie. A megjelenési skálaeffektus külön fut, ezért átfedhet a következő lépéssel. Nincs a mozgás végét helyettesítő időzítő.

## 1. Források és sértetlenség

- [Az APK elemzése](../../1122/analysis/REPORT.md), különösen a 8–10. fejezet: mozgás, skálaeffektus, Auto és képkockakezelés.
- [Eredeti APK](../../1122/Hexic2048.apk): kizárólag olvasási célú referencia; a játék Cocos2d-x 3.17 alapú, natív C++/OpenGL alkalmazás.
- [14-es motor, 19-es felület](../../source/14-ui19/sample1.html): játékszabály és felület.
- [Megtisztított HTML](../../3506/gemini-code-1791410667859.html): felületi referencia és megőrzött `HINTTEXT`.

A már elkészült APK-jelentés a szükséges `MoveTo`, `CallFunc`, animációszámláló, sebességkonstans és skálaútvonalakat elegendő részletességgel tartalmazta. Új Android-futtatás vagy új APK készítése nem történt. A jelentésben feltárt vezérlési elveket rekonstruáltam; a Cocos és a böngésző képkockáinak teljes azonosságát nem állítom.

Az új változat kizárólag az `experiments/native-motion-engine/` mappában hoz létre fájlokat. A korábbi programok és az APK változatlanok. A referenciafájlok SHA-256 ellenőrzőösszegei a munka előtt és után azonosak:

| Fájl | SHA-256 |
|---|---|
| `1122/Hexic2048.apk` | `da6756ae140af125dfbee6359d519a8c3cbda5b95398a5cb37b119150b63de0d` |
| `1122/analysis/REPORT.md` | `0c6e883acc21271e0cf30d2c704eb5b3f32eeea2befb8807ec04bba5f18d4b42` |
| `source/14-ui19/sample1.html` | `641d1207559b09599361704eea24d0090bf06a4973b46f32c84fa6a1463dd3de` |
| `3506/gemini-code-1791410667859.html` | `983b0fb1a94ecf68fb513438ad0cebf8fcbadbd0c0d6c2d0b8581ca658d354de` |

A megtisztított referenciából átvett azonosító karakterről karakterre: `57AAA6919E46D193E4D3A219CC4145DB`. Ez a `HINTTEXT`, nem újonnan generált érték. A másik referenciában található eltérő azonosító nem került át az új fájlba. A korábbi localStorage-adatokhoz a program nem nyúl, és nem migrálja őket.

## 2. Architektúra

### Logikai állapot és megjelenítés

`state.tiles` tartalmazza a logikai csempéket: stabil azonosító, rácskoordináta, BigInt-érték és véletlen szín. `views` külön Map az azonosítónként újrahasznált DOM-elemekkel és az utolsó befejezett vizuális pozícióval. Mozgás közben az aktuális megjelenítési pozíciót a WAAPI effektusa adja; a logikai koordináta már a célkoordináta.

A külső `.tw` elem `translate` transzformációval mozog, a belső `.t` elem `scale` transzformációval növekszik. Ezért egy új csempe megjelenése és ugyanazon csempe következő mozgása nem írja felül egymás transzformációját. A motor nem építi újra a teljes táblát minden képkockán, és nincs képkockánkénti JavaScript-pozíciófrissítés. A cellák és a csempék teljes újrarajzolása rácsváltáskor, visszatöltéskor, új játéknál vagy méretváltáskor szükséges.

Összeolvadáskor a logikailag törölt csempe vizuális eleme a tranzakció végéig megmarad. Így nem tűnik el a mozgás közepén. A közös befejezési kapu eltakarítja ezeket az elemeket, frissíti a túlélők színét és indítja a skálaeffektusokat.

### Befejezési kapu és érvényesség

`engine.transaction` egyszerre legfeljebb egy elfogadott lépést tárol. `engine.movements` az aktív mozgásokat, `engine.actions` az összes aktív mozgás- és skálaakciót tartja nyilván. A mozgások `finished` ígéreteire épített `Promise.all` a natív táblaszámláló megfelelője. A skálaakciók nem szerepelnek ebben a kapuban.

Minden akció a létrehozáskor érvényes `epoch` generációt kapja. Megszakításkor először nő az epoch, majd a motor törli a képkockakérést és megszakítja az akciókat. A régi ígéretek ettől még rendeződhetnek, de a generáció- és tranzakcióazonosság ellenőrzése megakadályozza az elavult folytatást. A tranzakció `finalized` jelzője megakadályozza a dupla befejezést és dupla spawn-t.

A befejezett akció végértéke az elem alapstílusába kerül, majd az animáció `cancel()` hívással felszabadul. Így a `fill: both` nem tart meg folyamatosan növekvő számú befejezett animációt. A megszakított `finished` ígéretek elutasítása kezelt; nem marad kezeletlen Promise-hiba. Egy váratlan, külső mozgásmegszakítás rendezett tranzakciólezárást vált ki, így nem marad örökké zárt bemeneti kapu.

### Miért WAAPI?

A WAAPI közvetlenül támogatja a szükséges befejezési ígéretet, a lineáris interpolációt, a megállítást/folytatást és a megszakítást. A mozgás és a skála külön elemeken futhat. A CSS-transition eseménykezelésénél kényelmesebben fejezhető ki a közös befejezési kapu és az akciók életciklusa. Saját JavaScript-interpolációs ciklus vagy canvas-renderelő ehhez a táblamérethez nem bizonyult szükségesnek.

## 3. Natív animációs elvek és sebességek

| Beállítás | Lineáris mozgás | Skála 0 → 1,1 | Skála 1,1 → 1 |
|---|---:|---:|---:|
| Slow | 300 ms | 200 ms | 100 ms |
| Medium, alapérték | 150 ms | 100 ms | 50 ms |
| Fast | 100 ms | 66,67 ms | 33,33 ms |
| Very Fast | 75 ms | 50 ms | 25 ms |

A mozgás `easing: "linear"`. A skála három kulcsképkockája `scale(0)`, `scale(1.1)`, `scale(1)`, rendre 0, 2/3 és 1 offsettel; összideje megegyezik a választott mozgásidővel. Ez a feltárt közvetlen `MoveTo`, illetve két egymás utáni `ScaleTo` időarányát követi. A skálát az összeolvadt túlélők és az új csempe kapják.

Az Auto nem várja meg a skála befejezését. A következő lépést egyetlen `requestAnimationFrame` kérés ütemezi a közös mozgásbefejezés után. Ez képkockához igazított folytatás, nem a mozgás végét helyettesítő várakozás. Nincs 320 + 320 + 50 ms ciklus, nincs spawn-effektushoz hozzáadott kötelező szünet. A programban lévő 900 ms-os `setTimeout` kizárólag a Save visszajelzését állítja vissza.

## 4. A lépés és az Auto pontos sorrendje

1. Bemeneti kapu: nincs aktív tranzakció, a lap nincs felfüggesztve, a játék elindult és nincs vége. Auto alatt a felületi kézi bemenet tiltott.
2. A motor másolatot készít a kiinduló csempékről, és a változatlan referencia-`step()` függvényt futtatja egy másik másolaton.
3. Eredménytelen iránynál nem indul tranzakció és nem keletkezik új csempe.
4. Sikeres lépésnél létrejön az Undo-előzmény, frissül a logikai túlélőlista, a BigInt-pontszám, a rekord és a lépésszám. A törlendő csempék a tranzakció vizuális listájában maradnak.
5. Az összes elmozduló csempe – a törlendők is – saját WAAPI-mozgást kap. A kézi új lépés blokkolva marad.
6. Az egyes `finished` ígéretek beírják a vizuális célpozíciót és felszabadítják az akciót. Egyetlen csempe befejezése még nem folytathatja a lépést.
7. Az összes szükséges mozgás sikeres befejezésekor, érvényes generációban a tranzakció egyszer lezárul. Törlődnek a vizuális maradványok, frissülnek a túlélők, indulnak az összeolvadási skálaeffektusok.
8. Pontosan egy új csempe keletkezik, saját megjelenési skálával. Frissül a felület, megtörténik a játék végének ellenőrzése, és minden 100. elfogadott lépés stabil állapota mentésre kerül.
9. Bekapcsolt Auto esetén a motor egy következő böngészőképkockát kér. Ott a HTML-referencia `movable()` listájából Random irányt választ, és újra a 1. pont kapuján keresztül lép. A skálaeffektusok még futhatnak.

A DOM sorrendje stabil csempeazonosítókhoz kötött; a háttércellák a csempék alatt vannak. A böngésző tényleges compositing és kijelzőprezentációs sorrendje nem azonosítható a Cocos OpenGL rajzolási sorrendjével. A befejezési kapu önmagában nem ígéri, hogy minden végállapot külön teljes kijelzőképkockát kap.

## 5. Megszakítások, mentés és életciklus

| Esemény | Szabály |
|---|---|
| Auto leállítás | Auto szándék törlése, epochváltás, összes akció törlése; a már elfogadott lépés egyszer, effektus nélkül stabil állapotba kerül. |
| Rácsváltás | Az aktuális lépés rendezése; kimenő rács saját állapotának mentése az Auto szándékával; a célrács saját mentésének betöltése, vagy új tábla. |
| Új játék | A régi tranzakció eldobása és összes akció megszakítása; a referencia szerinti kezdőcsempék létrehozása. Nincs régi tranzakcióból utólagos spawn. |
| Load game | A snapshot teljes ellenőrzése után régi akciók érvénytelenítése és rendezése, majd a mentett állapot felépítése; az elmentett Auto szándék visszaállítása. |
| Save | Mozgás közben stabil határra rendezés, snapshot és tárolás; siker/hiba visszajelzése; bekapcsolt Auto folytatható. |
| Sebességváltás | Aktuális lépés rendezése; az új sebesség a következő akcióktól érvényes. |
| Lap elrejtése | Auto-képkockakérés törlése, aktív animációk `pause()`; előtérben `play()` és szükség esetén Auto-folytatás. |
| `pagehide` / `pageshow` | Stabil állapot mentése és felfüggesztés; visszatéréskor a lap láthatósága alapján folytatás. |
| Átméretezés | Egy összevont RAF-feladat rendezi a tranzakciót, újraméretezi a táblát és folytatja az Auto-t. A ResizeObserver csak méretváltozásra reagál. |
| Váratlan mozgás-`cancel()` | Rendezett lezárás és érvényes Auto-folytatás; nincs örök várakozás. |

**Tudatos megszakításkor a folyamatban lévő mozgás a véghelyére ugrik.** Ez a stabil lezárási szabály része, nem a folyamatos Auto normál animációja. Rácsváltáskor, mentéskor, sebességváltáskor vagy átméretezéskor nem őrzünk meg félkész pixelpozíciót. A rendes mozgás során a WAAPI köztes pozíciókat interpolál; normál Auto alatt nincs ilyen kézi lezárás.

Saját localStorage-kulcsok:

- `hexic2048.native-motion-engine.v1.manual`
- `hexic2048.native-motion-engine.v1.slots`

A rácsslotok `classic_0`–`classic_13` bejegyzései a második kulcson belül vannak. A mentés tartalmazza a csempéket, színeket, pontszámot, rácsot, rekordokat, tíz Undo-előzményt, Undo-keretet, azonosítószámlálót, lépésszámot, sebességet és Auto szándékát. A BigInt értékek decimális karakterláncként, `n` utótaggal íródnak ki és ellenőrzés után állnak vissza. A validator ellenőrzi többek között az egyedi azonosítókat és koordinátákat, a rácson belüli helyet, az 1n csempeértéket, a színt és a számlálókat. Sérült mentés nem cserélheti le a játékot.

A localStorage írási hibája kezelt; nem állítja le a motort. A két tárolókulcs külön írása nem böngészőszintű atomikus adatbázis-tranzakció: tárhelyhiba esetén az egyik írás már sikerülhetett. A játékállapot snapshotja viszont csak stabil lépéshatáron készül. A korábbi játékok kulcsait a program nem olvassa és nem írja.

## 6. Megtartott HTML-szabályok és szándékos eltérések

### Megtartott működés

Mind a 14 rács megmaradt: Hex 2×2×2–8×8×8, Box 4×4–10×10. Hexen hat, Boxon nyolc irány használható, tehát a Box átlói is. A csempeérték `1n`, az összeolvadás nyeresége `2n`; nem az APK eredeti 2/4/8 értéksorát használjuk. A színkezelés és az Auto/manuális színválasztási szabály a referencia `step()` függvényéből változatlanul került át. A teszt a függvény szövegszerű azonosságát és minden rácson/irányban az eredményeit is ellenőrzi.

A látható Classic/Random felületet, a Grid/Auto/Save gombokat, menüt, Share/Load/New game műveleteket, a játékvégi Undo-t és a referencia új játékhoz használt `1111` jelszókérését megtartottam. Mobilon swipe, asztalon billentyűzet és egér használható. A referenciában elrejtett Mode/Undo főgombok és nem hozzáférhető Survival/X-Tile, Corner/Swing/Swirl változatok ebben az első kísérletben nem kaptak új kezelőfelületet. A mozgási szabály megtartott Classic/Random használatra vonatkozik.

### Eltérések az APK-tól és a korábbi HTML-től

- A natív C++/OpenGL node-ok helyett DOM és WAAPI működik. A natív akciószámláló helyett Set + ígéretkapu van.
- A skálaeffektusok a közös lépéslezárás után indulnak; nem rekonstruáljuk az egyes natív csempék egymáshoz képesti, frame-en belüli callback-sorrendjét.
- A következő Auto-lépés böngésző-RAF-ra kerül. Ez szándékos ütemezési különbség a Cocos frame-frissítéséhez képest.
- A Random Auto a HTML sikeresen mozgatható iránylistájából választ, nem az APK sikertelen irányokat újrapróbáló ágából. Az APK négyirányú rect-szabálya helyett megmaradnak a HTML nyolcirányú Box-mozgásai.
- Nincs natív hang, reklám, felhőmentés, Android SDK/JNI vagy Cocos renderelő. Ezek nem az animációs kísérlet részei.
- A korábbi HTML késleltetéses mozgás–pop–Auto láncát a befejezési kapu váltja fel. A feladat itt kifejezetten új animációs architektúrát kért.
- Az automatikus mentés a 100. lépés **befejezett** snapshotját írja; a natív program mentési gyakoriságát nem másoljuk át.
- A pontszám és a rekord logikailag az elfogadott lépés elején frissül. A skála/szín vizuális frissítése a lezárási kapunál történik. Ez nem a natív szöveganimáció pontos másolata.
- A `prefers-reduced-motion: reduce` felhasználói beállítás 1 ms-os effektusokat használ. A közös befejezési kapu és a képkockánként legfeljebb egy Auto-folytatás megmarad.

## 7. Valódi böngészőtesztek

Az automatizált teszt: [tests/regression.cjs](tests/regression.cjs). A futtatás valódi Chromium **151.0.7922.173** böngészővel, Playwright segítségével történt. A teszt helyi HTTP-válaszként adja át a HTML-t, és csak a tesztpéldányba injektál állapotelérést. A kiadott HTML-ben nincs `window.harness` vagy külső tesztfüggőség. A véletlenszám-generátor csak a tesztben determinisztikus.

| Ellenőrzés | Eredmény |
|---|---|
| Referencia-`step` azonosság, mind a 14 rács, minden irány, Auto/manuális színek | Sikeres. |
| Kézi lépés minden rácson és minden Box-átlóban | Sikeres; valódi WAAPI-akciók befejezése után ellenőrzött invariánsok. |
| Megállított Slow mozgás 380 ms várakozással | Nem zárult le időtúllépésből. Egyetlen csempe befejezése sem nyitotta ki a közös kaput; minden mozgás után pontosan egy spawn történt. |
| Mind a négy időtartam, lineáris easing, 2:1 skálaidő és 1,1 csúcs | A valódi Animation effektusokból ellenőrizve. |
| Valós köztes pozíciók Slow mozgásban | 19 RAF-minta, 19 különböző computed transform; részletek lent. |
| Asztali billentyűzet és mobilérintés | Sikeres; a swipe Chromium CDP `Input.dispatchTouchEvent` eseményekkel a kiadott eseménykezelőket használta. |
| Tíz kézi új lépés kísérlete aktív mozgás alatt | Egy elfogadott lépés; nincs egymásra csúszó tranzakció. |
| Tizenkét gyors Auto indítás/leállítási ciklus | Nincs aktív akció, függő Auto-RAF vagy dupla spawn a leállítás után. |
| Mentés Auto-mozgás közben, BigInt és visszatöltött Auto | Sikeres; 30 jegyű pontszám pontosan kezelhető. Régi referencia-kulcsban elhelyezett jelző változatlan maradt. |
| Tíz rácsváltás mozgás közben | Sikeres; nincs elavult folytatás vagy vizuális maradvány. |
| Új játék és Load aktív mozgás alatt | Külön funkcionális futásban sikeres; régi callbackből nincs utólagos lépés/spawn. |
| Kényszerített localStorage írási hiba | Külön funkcionális futásban Error visszajelzés, működő játék, kezeletlen kivétel nélkül. |
| Külső `animation.cancel()` | A tranzakció rendeződik, nincs beragadt várakozás. |
| Láthatóság és pagehide/pageshow | Szintetikus életcikluseseményekkel sikeres pause/resume és lezárási szabály. Ez nem fizikai mobilos háttérteszt. |
| Méretváltás aktív Auto alatt | Sikeres. További 320×568, 844×390 és 1280×800 nézetben a tábla a képernyőn belül maradt. |
| Reduced motion és sérült mentések | Sikeres; duplikált csempe, hibás pontszám és érvénytelen indított állapot elutasítva. |
| Valódi oldal-újratöltés | Saját névterezett mentés visszaállt. |
| 3000 gyorsított lépés, Box 10×10 | Sikeres; valódi animációobjektumok, programozott `.finish()` és befejezési ígéretek. |
| 2000 lépés valódi időben, Very Fast Auto, Hex 4×4×4 | Sikeres; természetes `finished` események, időgyorsítás nélkül. |

A futtatásban a csempeazonosítók és helyek egyediek, minden élő csempe a rácson van, az érték 1n, az azonosítószámláló nem marad le, az Undo-előzmény legfeljebb tíz. A vizuális lista mozgás alatt csak az élő és a tranzakció törlendő csempéit tartalmazza; nyugalmi állapotban pontosan az élő csempéket. A DOM `.tw` elemszáma megegyezik a views méretével.

### Hosszúteszt számai

| Mérés | Érték |
|---|---:|
| Gyorsított elfogadott / lezárt lépések | 3000 / 3000 |
| Következő lépés kezdetén még futó skálaeffektust tartalmazó eset | 2999 |
| Gyorsított teszt legnagyobb csempe-view száma | 12 |
| Gyorsított teszt legnagyobb aktív akciószáma | 19 |
| Gyorsított teszt végén aktív akciók | 0 |
| Valós Auto elfogadott lépések | 2000 |
| Valós Auto mérési ideje, indítással/leállítással együtt | 166 813 ms, kb. 166,8 s |
| JS heap, explicit GC után, előtte / utána | 1 274 328 / 1 803 124 bájt |
| CDP teljes DOM-node metrika, előtte / utána | 202 / 210 |
| Leállítás után élő csempék / views | 5 / 5 |
| Leállítás után akciók / mozgások / Auto-RAF | 0 / 0 / 0 |
| Leállítás után Undo-előzmények | 10 |
| Böngésző `pageerror` események | 0 |

A heap a két mintavétel között **528 796 bájttal nőtt**. Ez két mérési pont, nem egy korlátlan idejű memóriaszivárgás-bizonyítás. A minták eltérő táblát, előzményeket és böngésző/JIT állapotot tartalmazhatnak. A DOM/view invariánsok és a nulla aktív akció konkrétan igazolták, hogy a teszt végére nem maradt törölt csempe vagy befejezett WAAPI-akció a motor nyilvántartásában. Többórás heap-idősor és objektum-retenciós elemzés további feladat lehet.

### Képkockaminta és az FPS-állítás határa

A 300 ms-os Slow mintában 19 RAF-időpont és 19 különböző megjelenítési transzformáció volt; az első és utolsó minta közti idő 300,1 ms, a legnagyobb mintaköz 16,8 ms. Ez ebben a headless böngészőfutásban a köztes pozíciók egyenletes, közel 60 Hz-es **mintavételezését** támasztja alá. Nem fizikai kijelzőn mért, prezentált FPS, és nem bizonyít minden készülékre 60 FPS-t. A 2000 lépés ideje Auto-áteresztőképesség, nem képkockasebesség.

### Újrafuttatás

A játék futtatásához semmilyen csomag telepítése nem kell. A teszthez Node.js, Playwright és Chromium szükséges. Ezeket a saját tesztkörnyezetben kell biztosítani, majd a repository gyökeréből:

```sh
node experiments/native-motion-engine/tests/regression.cjs
```

A `CHROMIUM_PATH` környezeti változó megadhat más böngészőútvonalat; alapértéke `/usr/bin/chromium`. A `RESULT_PATH` a JSON mérési eredmény opcionális kimeneti fájlja. A gyors funkcionális ellenőrzés a két hosszúteszt nélkül:

```sh
node experiments/native-motion-engine/tests/regression.cjs --functional-only
```

## 8. Ismert korlátok

- Modern, BigInt-, WAAPI-, Pointer Events- és matchMedia-eseményeket támogató böngésző szükséges. Régi WebView-khoz nincs időzítős pótló motor.
- A futtatott böngésző Chromium volt; Safari/iOS, Firefox és fizikai Android teljesítménye nincs ezzel a méréssel igazolva.
- A natív elemzés statikus volt. A pontos Cocos frame-en belüli sorrend, mobil GPU és tényleges kijelzőprezentáció nem rekonstruálható ebből a HTML-ből.
- A háttérbe kerülés és page lifecycle tesztje az alkalmazás eseménykezelési szabályát ellenőrizte szintetikus eseményekkel. Operációs rendszer általi processzkilövés és minden BFCache-helyzet nincs lefedve.
- A megszakítások tudatosan végállapotba rendeznek; e műveleteknél látható azonnali pozícióváltás megengedett. A mentés félkész animációból nem tárol pixelpozíciót.
- A Share a böngésző Web Share/Clipboard jogosultságaitól függ; rendszermegosztási panelt nem automatizáltam.
- A tárolás böngészőeredethez kötött. A külön névtér védi a korábbi programok adatait; más origin, privát mód vagy tiltott tárhely nem biztosít tartós mentést.
- A GitHub Pages közvetlen HTTP-lekérése az elemző környezetből 403-as proxykorlátozásba ütközött. A közzététel a repository meglévő Pages build/deploy folyamatával történik; a hálózati korlátot nem kezeljük játéktesztként.

## 9. További optimalizálási lehetőségek

1. Fizikai mobilon frame timeline és hosszabb heap-idősor mérése, azonos kezdőtáblával, mind a négy sebességen. Ez választaná szét a WAAPI ütemezését és a tényleges kijelzőprezentációt.
2. A `movable()` másolatainak és többszöri sor-/rácsbejárásának profilozása nagy Hex/Box táblákon. Csak a referencia szabályának és véletlenszám-fogyasztásának megtartásával érdemes gyorsítani.
3. A localStorage JSON-serializálás idejének mérése sok rácsslot és nagy BigInt mellett. Szükség esetén IndexedDB vagy ritkább checkpoint külön változatban vizsgálható.
4. A transzformációk compositor-viselkedésének ellenőrzése. `will-change` csak mért előnynél, ideiglenesen kerüljön be; minden csempén tartósan alkalmazva GPU-memóriát növelhet.
5. Több böngészőn megszakítási/láthatósági tesztek és hosszabb object-retention vizsgálat. A két heap-mintából ennél erősebb következtetés nem vonható le.

## 10. Indexelés és közzététel

Az új mappa a repository gyökerében, a meglévő `.github/workflows/generate-index.yml` által bejárt fában található. A `main` ágra történő push indítja ezt a folyamatot; a workflow generálja és bot-committal frissíti az `index.html` fájlt. A munkában a régi indexet és a workflow-t nem írtam át. A felhasználó által kért automatikus indexfrissítés a meglévő mechanizmus feladata.

Létrehozott fájlok: `sample1.html`, ez a `REPORT.md` és `tests/regression.cjs`. A játék egyetlen önálló HTML, a jelentés és a teszt nem futásidejű függősége.
