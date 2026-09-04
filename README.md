# WeatherService — Vindsurfarens spotguide

En backend-tjänst byggd i **Quarkus (Java)** som hjälper vindsurfare att hitta de bästa spoterna baserat på aktuella vindförhållanden. Kartan visar spotar i södra Sverige och rankar dem i realtid mot varje spots idealvind.

---

## Vad tjänsten gör

- Hämtar aktuell vind per GPS-koordinat från Open-Meteo
- Beräknar ett poäng (0–1) för varje spot baserat på vindriktning, vindstyrka och byighet
- Returnerar sorterad lista med spots, vinddata och konditionsomdöme (**Utmärkt / Bra / Godkänt / Dåligt / Ingen vind**)
- Renderar en interaktiv Leaflet-karta med färgkodade markörer och GPS-positionering
- Låter användare lägga till spotar genom att klicka på kartan, redigera befintliga och se ändringshistorik
- Skyddar skrivoperationer med en delad bomkod
- Loggar alla ändringar med användarnamn, tidsstämpel och möjlighet att återställa

---

## Starta tjänsten

```bash
./gradlew quarkusDev
```

Öppna sedan `http://localhost:8080` i webbläsaren.

Swagger UI (API-dokumentation) finns på `http://localhost:8080/q/swagger-ui`.

---

## API-endpoints

| Metod | Sökväg | Beskrivning |
|---|---|---|
| `GET` | `/spots/top` | Hämta rankad lista med spots för ett område |
| `POST` | `/spots` | Lägg till ett nytt spot (kräver bomkod) |
| `PUT` | `/spots/{id}` | Uppdatera data på ett befintligt spot (kräver bomkod) |
| `GET` | `/spots/{id}/history` | Ändringshistorik för ett specifikt spot |
| `POST` | `/spots/{id}/restore/{logId}` | Återställ spot till tidigare version (kräver bomkod) |
| `POST` | `/spots/import/osm` | Importera spots från OpenStreetMap (kräver bomkod) |
| `GET` | `/settings` | Hämta appinställningar (sidrubrik) |
| `PUT` | `/settings` | Uppdatera appinställningar (kräver bomkod) |

### Exempel — hämta spots nära Varberg

```
GET http://localhost:8080/spots/top?lat=57.1&lon=12.3&radius_km=200&limit=20
```

---

## Autentisering

Alla skrivoperationer (POST, PUT) kräver headern `X-Api-Key` med rätt bomkod:

```bash
curl -X PUT http://localhost:8080/spots/sandhamn \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: <bomkod>" \
  -d '{"description": "Uppdaterad beskrivning"}'
```

Bomkoden konfigureras i `application.properties`:

```properties
windsurf.api-key=<din-bomkod>
```

I webbgränssnittet cachas bomkoden i webbläsarens localStorage i **7 dagar** — man behöver bara ange den en gång per vecka.

---

## Ändringslogg

Varje POST och PUT loggas automatiskt i tabellen `change_log` med:

| Fält | Innehåll |
|---|---|
| `spotExternalId` | Spotens ID |
| `spotName` | Spotens namn vid ändringstillfället |
| `action` | `CREATED`, `UPDATED` eller `RESTORED` |
| `changedBy` | Användarnamnet (anges i webbgränssnittet) |
| `changedAt` | Tidsstämpel |
| `changeJson` | Snapshot av hela spotens tillstånd (JSON) |
| `restoredFromLogId` | Vid `RESTORED`: vilket logg-id som återställdes från |

Via **📋 Historik**-knappen i varje spot-popup kan man se alla versioner och återställa till en tidigare version. Senaste versionen kan inte återställas (den är redan nuläget).

---

## Spotkällor

Det finns tre typer av spotar i databasen, markerade med `source`-fältet:

| Källa | Beskrivning |
|---|---|
| `SEED` | Handkurerade favoritspot, läggs in automatiskt vid uppstart om de saknas |
| `OSM` | Importerade från OpenStreetMap via Overpass API |
| `USER` | Tillagda av användare via kartan eller API |

SEED-spottarna matchas på `externalId` vid uppstart — de skrivs inte över om de redan finns, vilket innebär att redigerade seed-spotar bevaras vid omstart.

---

## Poängsättning

Varje spot poängsätts mot aktuell vind med formeln:

```
poäng = riktningspoäng × 0.4
      + fartpoäng      × 0.4
      + bypoäng        × 0.2
```

- **Riktningspoäng** — 1.0 om vinden matchar spotens bästa riktningar, annars 0.3
- **Fartpoäng** — skalas mot spotens eget intervall: 0.0 vid min/max-vind, 1.0 vid idealvind (linjär ramp)
- **Bypoäng** — beräknas från gust/medelvind-kvoten; används neutralt (0.6) om bydata saknas

Om vinden är utanför spotens min/max-intervall sätts poänget direkt till 0 ("Ingen vind / utanför intervall").

---

## Datamodell — ett spot

| Fält | Typ | Beskrivning |
|---|---|---|
| `name` | String | Spotens namn |
| `latitude` / `longitude` | double | GPS-koordinat |
| `region` | String | Geografisk region (t.ex. "Hallandskusten") |
| `type` | enum | `FLAT_WATER`, `WAVES`, `BUMP_N_JUMP` |
| `difficulty` | enum | `BEGINNER`, `INTERMEDIATE`, `ADVANCED`, `EXPERT` |
| `idealWindSpeed` | double | Idealvind i m/s |
| `minWindSpeed` / `maxWindSpeed` | double | Acceptabelt vindintervall i m/s |
| `bestDirections` | String | Kommaseparerade riktningar, t.ex. `"W,SW,NW"` |
| `description` | String | Beskrivning av spoten |
| `accessInfo` | String | Tillgänglighet, parkering, hinder |
| `source` | enum | `SEED`, `OSM`, `USER` |
| `approved` | boolean | Visas bara om true |
| `createdBy` / `updatedBy` | String | Användarnamn för skapare / senaste ändring |
| `createdAt` / `updatedAt` | LocalDateTime | Tidsstämplar |

---

## Externa tjänster

### Open-Meteo (vinddata) — används

- **URL**: `https://api.open-meteo.com/v1/forecast`
- **Kostnad**: Gratis, ingen API-nyckel krävs
- **Gräns**: 10 000 anrop/dag, 300 000/månad
- **Vad vi hämtar**: Aktuell vindstyrka (km/h, konverteras till m/s) och vindriktning (grader)
- **Caching**: Vinddata cachas i 30 minuter per koordinat i minnet

**Varför Open-Meteo?** Gratis utan registrering, pålitlig, 1 km upplösning globalt och returnerar data direkt utan OAuth-flöde.

**Begränsning**: `current_weather=true`-parametern (legacy API) returnerar inte byvindar. Bydata är 0 i nuläget — bypoänget används med neutralt fallback (0.6).

---

### Overpass API (OpenStreetMap) — används

- **URL**: `https://overpass-api.de/api/interpreter`
- **Kostnad**: Gratis, öppen data (ODbL-licens)
- **Vad vi gör**: Frågar efter noder med `sport=windsurfing`, `sport=kitesurfing` eller `sport=kiteboarding` inom bbox södra Sverige (lat 55–61.5, lon 10.5–25)
- **Importeras manuellt** via `POST /spots/import/osm` — startas inte automatiskt för att undvika långsam uppstart

**Begränsning**: Täckningen i OSM är ojämn. Välkända spots som Appelviken och Träslövsläge saknar `sport`-taggar och finns istället som SEED-data.

---

## Valda bort — alternativa tjänster

### Windy API
- **Varför inte**: Gratis-nivån ger bara GFS-modellen (lägre noggrannhet). ECMWF-modellen är betald. Windy är bättre för UI-visualisering (iframe) än för rå API-data.
- **Intressant framöver**: Inkluderar vågdata och svall — relevant för WAVES-spotar.

### Stormglass.io
- **Varför inte**: Gratis-nivån tillåter bara 10 anrop per dag. Betald plan ~$29/månad.
- **Intressant framöver**: Bäst marin data — vågperiod, svallriktning, vattentemperatur. Värt att integrera för WAVES-spots med aggressiv caching.

### OpenWeatherMap
- **Varför inte**: Kräver API-nyckel. Ingen byvind på gratis-nivå. Inget svall- eller vågdata.

### NOAA / NWS
- **Varför inte**: Täcker primärt USA. API:et är komplext (GeoJSON-tungt) och inte optimerat för punktfrågor i Sverige.

---

## Teknikval

### Quarkus (Java) — valt

Valt framför Spring Boot av tre skäl:

1. **Lågt minnesfotavtryck**: Quarkus JVM-läge använder ~150–200 MB RAM mot Spring Boots ~350–500 MB.
2. **Panache ORM**: Hibernate ORM Panache ger lite kodbrus jämfört med Spring Data JPA.
3. **GraalVM native** (framtida option): Kan kompileras till en native binary (~50 MB RAM, <100 ms starttid).

### H2 (filbaserad databas) — används nu

Ingen separat databasprocess. Filen `windsurf-db.mv.db` skapas i projektkatalogen och överlever omstarter.

**Framöver**: Bör bytas till PostgreSQL inför deploy i molnet. Switch görs via `application.properties`:

```properties
%prod.quarkus.datasource.db-kind=postgresql
%prod.quarkus.datasource.jdbc.url=${DATABASE_URL}
```

---

## Planerade utbyggnader

- **Fotouppladdning per spot** — bilder som visar förhållandena vid spoten
- **Spot-recensioner / betyg** — kommentarer och stjärnbetyg per spot
- **Prognos** — vindutveckling per timme kommande 6–12 timmar (Open-Meteo `hourly`-parametern)
- **Byvindar** — integrera bydata från Open-Meteo eller Stormglass för att aktivera bypoänget fullt ut
- **Användarkonton** — Google OAuth för att koppla bidrag och historik till specifika personer
- **Push-notiser / larm** — "meddela mig när vinden på Appelviken överstiger 8 m/s från SW"
- **Admin-sida** — vy för ändringsloggen med filtrering per spot och användare
- **PostgreSQL i moln** — Railway eller Fly.io inför produktionssättning
