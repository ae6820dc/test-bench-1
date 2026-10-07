# 14-es motor, 19-es felület

A `sample1.html` alapja a `source/14/sample1.html`. Az eredeti 14-es és 19-es fájlok változatlanok.

A 19-esből átvett felület: háromgombos Grid / Auto / Save sáv, közvetlen Auto kapcsoló, csendes Saved! / Error mentési visszajelzés, New game / Share menü, eredeti színek és elrendezés. Az Auto gomb a **14-es eredeti Random módját** indítja. A 14-es alapértelmezett állapota és minden motorfüggvénye megmaradt.

Változatlan a lépésválasztás, összeolvadás, renderelési sorrend, animáció, időzítés és mentési adatformátum. Egy Auto ciklus: 320 ms mozgás, 320 ms megjelenési animáció, 50 ms várakozás. A 19-es motorja nem került át. A mentések külön kulcsot kapnak, így a két eredeti program mentéseit nem írják felül.

A Mode és Undo gombok a 19-es főfelületét követve nem láthatók. A hozzájuk tartozó eredeti 14-es kód és rejtett DOM-elemek megmaradtak, hogy a renderelést ne kelljen módosítani. A Game Over képernyő a 14-esé, az Undo lehetőséggel együtt; ez megőrzi az eredeti lezárási folyamatot. A korábbi Auto módok belső működése szintén megmaradt, de a főfelület az eredeti Random módot kapcsolja.

## Ellenőrzés

A `tests/auto-regression.cjs` Playwright és Chromium segítségével:

- szövegszinten ellenőrzi minden eredeti motorfüggvény változatlanságát; csak a `saveGame` visszajelzése térhet el;
- mind a 14 táblaméreten azonos véletlenszámokkal több mint 100 Random Auto lépés teljes állapot-, render- és időnyomát hasonlítja össze az eredeti 14-essel, a 100. lépés automatikus mentésén áthaladva;
- hexagonális és négyszögletes táblán ellenőrzi a Corner, Swing és Swirl módot is;
- valódi böngészőképkockákban ellenőrzi a mozgás több látható köztes transzformációját;
- ellenőrzi az Auto kapcsolót, mentést és visszatöltést, a 14 elemű Grid menüt, valamint a mobil- és asztali táblaméretezést.

Futtatás a repository gyökeréből, elérhető `playwright` Node-modullal:

```sh
node source/14-ui19/tests/auto-regression.cjs
```

A Chromium alapértelmezett útvonala `/usr/bin/chromium`; a `CHROMIUM_PATH` környezeti változóval felülírható. A teszt csak a tesztoldalba illeszt mérőkódot, a programfájlt és az eredeti forrásokat nem módosítja. A képkockateszt a tesztelt környezet folyamatosságát igazolja; más eszközök teljesítményére nem ad általános garanciát.

### Teszteredmény (2026-10-07)

Chromium / Playwright: 41 változatlan motorfüggvény; 20 sikeres Auto összehasonlítás; mind a 14 táblaméreten 106 Random lépés; 690 ms lépésköz. A valódi animációmintában 20 képkockán mindkét mozgó csempének 20 különböző köztes transzformációja volt. Az Auto kapcsoló, Save és visszatöltés, Grid menü, mobil- és asztali méretezés sikeres; JavaScript futási hiba nem jelentkezett. Az animációs CSS-szabályok és a 320 / 320 ms időzítési konstansok az eredeti 14-essel egyeznek.
