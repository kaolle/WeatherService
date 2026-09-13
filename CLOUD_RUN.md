# Cloud Run via GitLab och Cloud Build

`cloudbuild.yaml` använder samma upplägg som booking-service: Google Buildpacks
bygger och publicerar containerbilden, därefter uppdaterar `gcloud run deploy`
Cloud Run-tjänsten. Ingen Dockerfile behövs för detta flöde.

## Inställningar innan första bygget

1. Skapa/välj Cloud Run-tjänsten och koppla GitLab-repot till en Cloud Build-trigger.
   Välj **befintlig Cloud Build-konfigurationsfil** och ange `cloudbuild.yaml`.
2. Kontrollera substitutionerna i YAML eller ange dem i triggern:
   - `_SERVICE`: `windsurf` — måste matcha din Cloud Run-tjänst.
   - `_REGION`: `europe-west1`.
   - `_REPOSITORY`: `cloud-run-source-deploy` — ett befintligt Docker-repository
     i Artifact Registry i samma region. Skapa det om det saknas.
   `$PROJECT_ID` och `$BUILD_ID` sätts automatiskt av Cloud Build. Bilden får en unik
   tagg per bygge, även när bygget körs manuellt utan en Git-commit.
3. Aktivera Cloud Build, Artifact Registry och Cloud Run API:erna i projektet.
   Byggkontot behöver Artifact Registry Writer på repositoryt, Cloud Run Developer
   för tjänsten, Service Account User på tjänstens runtime-konto och Logs Writer.
   Ge även runtime-kontot Secret Accessor på de hemligheter som används.
4. Lägg in `MONGODB_URL` och `WINDSURF_API_KEY` i Cloud Run, helst som referenser
   till Secret Manager. `MONGODB_DATABASE` är valfri och har standardvärdet `windsurf`.
   Konfigurera dem innan första appstarten; de ska inte vara byggvariabler.
5. Använd port 8080 och lämna Cloud Runs fält för containerkommando och argument tomma
   så att Buildpacks startkommando används. Välj publik åtkomst om kartan ska vara publik.
   Inställningar för minne, skalning och nätverk görs i Cloud Run.

Atlas måste tillåta nätverksanslutningen från Cloud Run. Om du väljer vanlig dynamisk
utgående IP behöver Atlas tillåta `0.0.0.0/0`; det öppnar nätverksåtkomsten men behåller
databasautentisering och TLS. Begränsad IP-åtkomst kräver exempelvis fast utgående IP.
YAML-filen skapar inget nätverk och ändrar ingen Atlas-konfiguration.

## Bygge och uppdatering

Buildpacks använder Java 17 och kör `./gradlew clean build --no-daemon`.
`GOOGLE_BUILDABLE=build/quarkus-app` pekar ut Quarkus körbara paket, och
`GOOGLE_ENTRYPOINT` startar `build/quarkus-app/quarkus-run.jar`. Hela fast-jar-paketet,
inklusive bibliotek och statiska webbfiler, behövs i bilden.

Vid push på triggerns valda gren byggs och publiceras bilden innan deploymentsteget körs.
Deploymentsteget byter bilden; hemligheter, miljövariabler och andra manuella
Cloud Run-inställningar anges inte av YAML-filen.

Efter deployment: öppna kartan och kontrollera `/q/health/ready` samt `/settings`.
Cloud Build måste köras i ditt projekt för att verifiera GitLab-koppling och IAM.

Referenser: [Google Buildpacks för Java](https://docs.cloud.google.com/docs/buildpacks/java),
[byggvariabler](https://docs.cloud.google.com/docs/buildpacks/service-specific-configs).
