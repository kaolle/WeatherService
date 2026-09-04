# WeatherService — Vindsurfarens spotguide

En backend-tjänst byggd i **Quarkus (Java)** som hjälper vindsurfare att hitta de bästa spoterna baserat på aktuella vindförhållanden. Kartan visar spotar i södra Sverige och rankar dem i realtid mot varje spots idealvind.

---

## Vad tjänsten gör

- Hämtar aktuell vind per GPS-koordinat från Open-Meteo
- Beräknar ett poäng (0–1) för varje spot baserat på vindriktning, vindstyrka och byighet
- Returnerar sorterad lista med spots, vinddata och konditionsomdöme (Utmärkt / Bra / Godkänt / Dåligt)
- Renderar en interaktiv Leaflet-karta i webbläsaren med färgkodade markörer
- Låter användare lägga till egna spotar och redigera befintliga

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
| `GET` | `/spots/top` | Hämta rankat lista med spots för ett område |
| `POST` | `/spots` | Lägg till ett nytt spot (användarbidrag) |
| `PUT` | `/spots/{id}` | Uppdatera data på ett befintligt spot |
| `POST` | `/spots/import/osm` | Importera spots från OpenStreetMap (Overpass) |

### Exempel — hämta spots nära Varberg

```
GET /spots/top?lat=57.1&lon=12.3&radius_km=200&limit=20
```

---

## Spotkällor

Det finns tre typer av spotar i databasen, markerade med `source`-fältet:

| Källa | Beskrivning |
|---|---|
| `SEED` | Hårdkodade favoritspot (vår lista), läggs in automatiskt vid uppstart |
| `OSM` | Importerade från OpenStreetMap via Overpass API |
| `USER` | Tillagda av användare via POST /spots |

SEED-spottarna innehåller handkurerade data (idealvind, riktningar, beskrivning) och utgör en bas som alltid finns med oavsett vad OSM-datan innehåller. Om ett SEED-spot redan finns i databasen (matchas på `externalId`) läggs det inte in igen — data kan alltså redigeras utan att skrivas över vid omstart.

---

## Poängsättning

Varje spot poängsätts mot aktuell vind med formeln:

```
poäng = riktningspoäng × 0.4
      + fartpoäng      × 0.4
      + bypoäng        × 0.2
```

- **Riktningspoäng** — 1.0 om vinden matchar spotens bästa riktningar, annars 0.3
- **Fartpoäng** — minskar ju mer aktuell vindstyrka avviker från spotens idealvind (max avvikelse 8 m/s = noll poäng)
- **Bypoäng** — minskar om byar är kraftiga relativt medelvinden (hög bykvot = opålitlig vind)

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

---

## Externa tjänster

### Open-Meteo (vinddata) — används

- **URL**: `https://api.open-meteo.com/v1/forecast`
- **Kostnad**: Gratis, ingen API-nyckel krävs
- **Gräns**: 10 000 anrop/dag, 300 000/månad
- **Vad vi hämtar**: Aktuell vindstyrka (km/h, konverteras till m/s) och vindriktning (grader)
- **Caching**: Vinddata cachas i 30 minuter per koordinat i minnet för att hålla anropen låga

**Varför Open-Meteo?** Gratis utan registrering, pålitlig, 1 km upplösning globalt och returnerar data direkt utan OAuth-flöde. Perfekt för ett MVP.

**Begränsning**: `current_weather=true`-parametern (legacy API) returnerar inte byvindar. Bydata är därmed alltid 0 i nuläget — bypoänget i algoritmen används inte fullt ut.

---

### Overpass API (OpenStreetMap) — används

- **URL**: `https://overpass-api.de/api/interpreter`
- **Kostnad**: Gratis, öppen data (ODbL-licens)
- **Vad vi gör**: Frågar efter noder med `sport=windsurfing`, `sport=kitesurfing` eller `sport=kiteboarding` inom bbox södra Sverige (lat 55–61.5, lon 10.5–25)
- **Importeras manuellt** via `POST /spots/import/osm` — startas inte automatiskt för att undvika långsam uppstart

**Begränsning**: Täckningen i OSM är ojämn. Välkända spots som Appelviken och Träslövsläge saknar `sport`-taggar i OSM och importeras därför inte — dessa finns istället som SEED-data.

---

## Valda bort — alternativa tjänster

### Windy API
- **Varför inte**: Gratis-nivån ger bara GFS-modellen (lägre noggrannhet). ECMWF-modellen (mer precis för vindprognos) är betald. Windy är bättre för UI-lager (iframe-baserad kartvisualisering) än för rå API-data.
- **Intressant framöver**: Windy inkluderar vågdata och svall — relevant för WAVES-spotar.

### Stormglass.io
- **Varför inte**: Gratis-nivån tillåter bara 10 anrop per dag, vilket är otillräckligt när tjänsten hämtar vind per spot vid varje request. Betald plan börjar på ~$29/månad.
- **Intressant framöver**: Stormglass har bäst marin data — vågperiod, svallriktning, vattentemperatur. Värt att integrera för WAVES-spots om man cacchar aggressivt.

### OpenWeatherMap
- **Varför inte**: Kräver API-nyckel och registrering. Ger inte byvind på gratis-nivå. Inget svall- eller vågdata. Open-Meteo är överlägset för det vi behöver just nu.

### NOAA / NWS
- **Varför inte**: Täcker primärt USA. Datan är tillgänglig globalt via NDFD men API:et är komplext (GeoJSON-tungt) och inte optimerat för punktfrågor i Sverige.

---

## Teknikval

### Quarkus (Java) — valt

Valt framför Spring Boot av tre skäl:

1. **Lågt minnesfotavtryck**: Quarkus JVM-läge använder ~150–200 MB RAM mot Spring Boots ~350–500 MB. Direktöversatt till lägre molnkostnad.
2. **Snabbt att komma igång**: Hibernate ORM Panache och RESTEasy Reactive ger lite kodbrus jämfört med Spring Data JPA.
3. **GraalVM native** (framtida option): Quarkus kan kompileras till en native binary (~50 MB RAM, startar på <100 ms) om molnkostnaden behöver pressas ytterligare.

### H2 (filbaserad databas) — används nu

Enklast möjliga setup — ingen separat databasprocess, filen `windsurf-db.*` skapas i projektkatalogen. Överlever omstarter och fungerar för ett MVP eller lokalt bruk.

**Framöver**: Bör bytas till PostgreSQL inför deploy i molnet. Quarkus stöder enkelt switch via `application.properties` (`quarkus.datasource.db-kind=postgresql`).

---

## Planerade utbyggnader

- **Fotouppladdning per spot** — användare ska kunna lägga till bilder för att visa förhållandena vid spoten
- **Spot-recensioner / betyg** — möjlighet att lämna kommentarer och stjärnbetyg per spot
- **Prognos** — visa vindutveckling per timme kommande 6–12 timmar per spot (Open-Meteo stöder detta via `hourly`-parametern)
- **Byvindar** — byta till Open-Meteo `current=wind_gusts_10m` (eller komplettera med Stormglass) för att aktivera bypoänget i scoringalgoritmen
- **Användarautentisering** — JWT-baserad inloggning för att koppla bidrag till konton och skydda redigeringsendpointen
- **Push-notiser / larm** — "meddela mig när vinden på Appelviken överstiger 8 m/s från SW"
- **PostgreSQL i moln** — byta från H2 till managed PostgreSQL (Railway eller Fly.io) inför produktionssättning
