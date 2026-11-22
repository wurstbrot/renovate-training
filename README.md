# Renovate-Training: Docker, Maven und Android

In diesem Training richtest du [Renovate](https://docs.renovatebot.com/) für ein absichtlich veraltetes Repository ein.
Renovate läuft selbst gehostet über GitHub Actions. Es startet bei jedem Push auf `main`, auch in deinem Fork.

| Paket-Manager (Renovate-Manager) | Datei(en) | Absichtlich veraltet |
|---|---|---|
| Docker (`dockerfile`) | `Dockerfile` | Java-Basis-Image `eclipse-temurin:17.0.8_7-jre-jammy`, Build-Image `maven:3.9.4-eclipse-temurin-17` |
| Maven (`maven`) | `pom.xml` | **Log4j 2.14.1**, anfällig für **Log4Shell (CVE-2021-44228)** |
| Android/Gradle (`gradle`, `gradle-wrapper`) | `android/gradle/libs.versions.toml`, `android/app/build.gradle`, `android/gradle/wrapper/gradle-wrapper.properties` | Android Gradle Plugin 7.4.2, Gradle 7.5.1, Android SDK API 33, **OkHttp 3.12.0** (CVE-2021-0341) |

## Lernziele

1. Renovate über GitHub Actions selbst hosten. Der Workflow startet bei jedem Push und funktioniert ohne Anpassung in jedem Fork.
2. Verstehen, wie Renovate Updates in Pull Requests (PRs) aufteilt: nach Update-Typ (Major/Minor/Patch), nach Paket-Gruppen und nach gemeinsam genutzten Versions-Properties.
3. Sicherheitslücken wie Log4Shell gezielt über Schwachstellen-Datenbanken schließen.
4. **Automerge für das Java-Basis-Image einrichten.** Minor-, Patch- und Digest-Updates werden automatisch gemerged, **Major-Updates nicht**.
5. Die Grenzen von Renovate erkennen. Beispiel: Die Android-SDK-Level `compileSdk`/`targetSdk` aktualisiert Renovate nicht.

## Aufbau des Repositorys

```text
.
├── .github/workflows/
│   ├── renovate.yml     # startet Renovate bei Push auf main (und manuell)
│   ├── ci.yml           # Check "build": Docker-Image bauen (inkl. Maven) + Smoke-Test
│   └── android.yml      # Check "android-build": nur bei Änderungen unter android/
├── Dockerfile           # Multi-Stage: Maven-Build + Java-Runtime-Image
├── pom.xml              # Java-17-Anwendung mit Log4j 2.14.1
├── src/                 # Minimale Java-Anwendung (loggt eine Benutzereingabe)
├── android/             # Minimale Android-App (Gradle, Version Catalog, Wrapper)
├── renovate.json        # Ausgangskonfiguration: noch OHNE Automerge
└── loesung/             # Musterlösung – erst nach den Übungen ansehen
```

---

## Teil 0: Einrichtung (ca. 10 Minuten)

1. **Repository forken.** Rechts oben auf *Fork* klicken. „Copy the `main` branch only“ kann aktiviert bleiben.
2. **GitHub Actions im Fork aktivieren.** In Forks sind Workflows zunächst deaktiviert.
   Im Fork den Tab *Actions* öffnen und „I understand my workflows, go ahead and enable them“ klicken.
3. **Actions dürfen PRs anlegen.**
   *Settings → Actions → General → Workflow permissions:* „Read and write permissions“ wählen und
   „Allow GitHub Actions to create and approve pull requests“ aktivieren. Danach *Save* klicken.
4. **Issues aktivieren** (für das *Dependency Dashboard*):
   *Settings → General → Features → Issues* anhaken. In Forks ist das standardmäßig aus.
5. **Token wählen.** Eine der beiden Varianten genügt:

   | Variante | Einrichtung | Verhalten |
   |---|---|---|
   | **A: `GITHUB_TOKEN`** (Standard, nichts zu tun) | – | Funktioniert sofort. CI-Läufe auf Renovate-PRs muss ein Mensch aber per „Approve and run workflows“ freigeben. |
   | **B: Personal Access Token (empfohlen)** | Token (classic) mit Scope `repo` bzw. `public_repo` erzeugen und im Fork unter *Settings → Secrets and variables → Actions* als `RENOVATE_TOKEN` speichern | CI startet auf Renovate-PRs automatisch. PRs und Commits erscheinen unter deinem Benutzer. |

   Der Workflow nimmt automatisch `RENOVATE_TOKEN`, falls vorhanden, sonst `GITHUB_TOKEN`.

6. **Ersten Lauf starten.** Entweder eine beliebige Änderung auf `main` pushen
   (z. B. diese README anpassen) oder unter *Actions → Renovate → Run workflow* manuell starten.

> **Warum läuft das im Fork ohne Anpassung?**
> `renovate.yml` setzt `RENOVATE_REPOSITORIES: ${{ github.repository }}`. Renovate bearbeitet also immer das Repository,
> in dem der Workflow gerade läuft. Mit `RENOVATE_FORK_PROCESSING: enabled` ignoriert Renovate Forks nicht.
> Weil `renovate.json` schon im Repository liegt, ist das Onboarding abgeschaltet (`RENOVATE_ONBOARDING: "false"`).

---

## Teil 1: Bestandsaufnahme (ca. 15 Minuten)

Nach dem ersten Lauf sollten im Fork folgende PRs existieren (Stand der Vorlage: 22.11.2026; neuere Versionen sind möglich):

| PR (Branch) | Update | Typ |
|---|---|---|
| `renovate/eclipse-temurin-17.x` | `17.0.8_7-jre-jammy` → `17.0.21.1_2-jre-jammy` | Patch |
| `renovate/eclipse-temurin-25.x` | `17.0.8_7-jre-jammy` → `25.0.5.1_2-jre-jammy` | **Major** |
| `renovate/maven-3.x` | `maven:3.9.4-…` → `3.10.0-eclipse-temurin-17` | Minor |
| `renovate/log4j2-monorepo` | Log4j `2.14.1` → `2.26.1` (api + core gemeinsam) | Minor |
| `renovate/okhttp` | OkHttp `3.12.0` → `3.14.9` | Minor |
| `renovate/major-okhttp` | OkHttp `3.12.0` → `5.5.1` | **Major** |
| `renovate/appcompat` | AppCompat `1.6.1` → `1.8.0` | Minor |
| `renovate/major-agp` | Android Gradle Plugin `7.4.2` → `9.5.2` | **Major** |
| `renovate/gradle-7.x` / `renovate/gradle-9.x` | Gradle-Wrapper `7.5.1` → `7.6.6` / `9.9.2` | Minor / **Major** |

Aufgaben:

1. Öffne das Issue **Dependency Dashboard** und vergleiche es mit der PR-Liste.
2. Öffne den Log4j-PR. Warum enthält **ein** PR beide Artefakte (`log4j-api` und `log4j-core`)?
   *Tipp:* `pom.xml` → `<log4j.version>` sowie das Preset `group:monorepos` in `config:recommended`.
3. Warum schlägt Renovate für Java **25** vor und nicht 23 oder 24?
   *Tipp:* Das Preset `workarounds:javaLTSVersions` (Teil von `config:recommended`) lässt nur LTS-Versionen zu.
4. Warum gibt es für Java 17 → 25 einen **eigenen** PR?
   *Tipp:* `separateMajorMinor` ist standardmäßig aktiv.
5. In `android/app/build.gradle` stehen `compileSdk 33` und `targetSdk 33`. Warum gibt es dafür **keinen** PR?
   *Antwort:* Renovate hat keine Datenquelle für Android-API-Level. SDK-Level, Play-Store-Vorgaben zum Target-API-Level
   usw. musst du selbst nachhalten. Renovate kann nur Abhängigkeiten aktualisieren, zu denen es eine Datenquelle (*datasource*) kennt.
6. Gab es im Log des Workflows (*Actions → Renovate → Job-Log*) Warnungen? Das Log-Level steht auf `debug`.

---

## Teil 2: Log4Shell und andere Schwachstellen gezielt schließen (ca. 20 Minuten)

Die Java-Anwendung (`src/main/java/.../App.java`) schreibt eine Benutzereingabe ins Log.
Mit Log4j 2.14.1 ist genau dieses Muster der Angriffsweg von Log4Shell.
Für Renovate ist das aktuell aber nur ein normales Minor-Update ohne Hinweis auf die Sicherheitslücke.

**Aufgabe:** Erweitere `renovate.json` so, dass Renovate bekannte Schwachstellen erkennt und
Sicherheits-PRs mit dem Label `security` anlegt. Committe direkt auf `main`, dann startet Renovate automatisch.

Hinweise:

- [`osvVulnerabilityAlerts`](https://docs.renovatebot.com/configuration-options/#osvvulnerabilityalerts) fragt die Datenbank
  [OSV.dev](https://osv.dev/) ab. Das braucht keine zusätzlichen Token-Rechte und funktioniert deshalb auch mit `GITHUB_TOKEN`.
- [`vulnerabilityAlerts`](https://docs.renovatebot.com/configuration-options/#vulnerabilityalerts) steuert, wie Sicherheits-PRs aussehen.

Beobachtungen und Fragen:

1. Die PR-Titel enthalten nun `[SECURITY]`, und die PR-Beschreibung listet die Schwachstellen auf.
   Für Log4j sind das unter anderem CVE-2021-44228 (behoben in 2.15.0), CVE-2021-45046 (2.16.0), CVE-2021-45105 (2.17.0) und CVE-2021-44832 (2.17.1).
   Die Liste reicht aber bis zu Schwachstellen, die erst in 2.25.x behoben sind.
2. Standardmäßig wählt Renovate bei Schwachstellen die **niedrigste** Version, die alle bekannten Lücken schließt (`vulnerabilityFixStrategy: "lowest"`).
   Bei Log4j führt das zu einem Problem: Laut OSV ist `log4j-api` erst ab 2.25.5 und `log4j-core` schon ab 2.25.4 abgesichert.
   Beide nutzen aber dieselbe Property `log4j.version`. Welche Einstellung löst das?
3. Bei OkHttp ist CVE-2021-0341 erst ab **4.9.2** behoben. Der Sicherheits-PR ist deshalb zwangsläufig ein **Major-Update** (3 → 5),
   und der Minor-PR auf 3.14.9 verschwindet. Darf so ein PR automatisch gemerged werden?
4. Sicherheits-PRs ignorieren standardmäßig Zeitpläne (`schedule`), Freigaben im Dashboard, das Mindestalter (`minimumReleaseAge`)
   und das Limit gleichzeitig offener PRs. Warum ist das sinnvoll?

---

## Teil 3: Automerge für das Java-Basis-Image (Kernübung, ca. 30 Minuten)

Ausgangslage: In `renovate.json` ist **kein** Automerge eingerichtet. Jeder PR muss manuell gemerged werden.

**Ziel:**

- Updates des Java-Basis-Images `eclipse-temurin` vom Typ **Minor, Patch und Digest** werden automatisch gemerged,
  sobald die CI grün ist.
- **Major-Updates** (z. B. Java 17 → 25) werden **nicht** automatisch gemerged.
- Alle anderen Abhängigkeiten bleiben unverändert (manuelles Mergen).

Hinweise:

- [`packageRules`](https://docs.renovatebot.com/configuration-options/#packagerules) mit `matchDepNames` bzw.
  `matchPackageNames`, `matchDatasources` und `matchUpdateTypes`
- [`automerge`](https://docs.renovatebot.com/configuration-options/#automerge) und [Key concepts → Automerge](https://docs.renovatebot.com/key-concepts/automerge/)
- Spätere Regeln in `packageRules` überschreiben frühere. Eine abschließende Regel „Major nie automergen“ ist ein gutes Sicherheitsnetz.

**So kommt der Automerge zustande:**

Renovate merged nur, wenn **alle** Status-Checks des PRs grün sind. PRs ohne Checks werden nie automatisch gemerged.
Den Check `build` liefert `.github/workflows/ci.yml`.

1. Neue Konfiguration auf `main` pushen. Renovate läuft und markiert den Temurin-Patch-PR als Automerge-Kandidaten.
   In der PR-Beschreibung steht dann „Automerge: Enabled“.
2. CI auf dem PR abwarten:
   - **Variante A (`GITHUB_TOKEN`):** Im PR bzw. unter *Actions* „Approve and run workflows“ klicken.
   - **Variante B (`RENOVATE_TOKEN`):** CI startet automatisch.
3. Merge:
   - Ist im Fork *Settings → General → Pull Requests → „Allow auto-merge“* aktiv **und** verlangt eine Branch-Ruleset für `main`
     den Status-Check `build`, aktiviert Renovate GitHubs natives Auto-Merge. GitHub merged dann selbst, sobald `build` grün ist.
     Mache `android-build` **nicht** zur Pflicht: Dieser Check läuft nur bei Änderungen unter `android/`.
   - Andernfalls merged Renovate beim **nächsten Lauf** selbst. Starte dazu *Actions → Renovate → Run workflow* oder pushe auf `main`.

**Prüfe das Ergebnis:**

- [ ] Der PR `eclipse-temurin` 17.0.8 → 17.0.21 wurde automatisch gemerged.
- [ ] Der PR `eclipse-temurin` 17 → 25 (Major) ist weiterhin offen, die Beschreibung zeigt „Automerge: Disabled“.
- [ ] `maven:3.9.4-…` (Build-Image, Minor) wurde **nicht** automatisch gemerged.
- [ ] Log4j-, OkHttp- und Android-PRs sind weiterhin offen.

Diskussion: Sollte das Build-Image `maven` ebenfalls automatisch gemerged werden? Was spricht dafür, was dagegen?

---

## Teil 4: Android – wenn ein Update die CI bricht (ca. 15 Minuten)

Die Android-PRs werden vom Check `android-build` (`.github/workflows/android.yml`) gebaut.

1. Welche Android-PRs werden grün, welche rot? Lies bei roten PRs das Build-Log.
   - Ein Major-Update des Android Gradle Plugin benötigt in der Regel auch eine neuere Gradle-Version. Renovate legt beides aber als **getrennte** PRs an.
   - Neuere AndroidX-Bibliotheken setzen oft ein höheres `compileSdk` voraus. Das alte SDK-Level kann also normale Updates blockieren.
2. Wie würdest du AGP- und Gradle-Updates zusammenfassen? *Tipp:* `groupName` in einer `packageRule`.
3. Erhöhe `compileSdk`/`targetSdk` manuell in einem eigenen PR und beobachte, welche Renovate-PRs danach grün werden.
4. **Gradle-Wrapper und `allowedUnsafeExecutions`:** Öffne den PR für den Gradle-Wrapper und sieh dir `gradle-wrapper.properties` an.
   Renovate aktualisiert dort `distributionUrl` **und** `distributionSha256Sum`. Für die Prüfsumme muss Renovate `./gradlew :wrapper` ausführen.
   Das ist nur erlaubt, weil `renovate.yml` die Variable `RENOVATE_ALLOWED_UNSAFE_EXECUTIONS: '["gradleWrapper"]'` setzt.
   - Entferne die Zeile testweise. Im Dependency Dashboard erscheint dann
     `Gradle wrapper command was requested to run, but gradleWrapper is not permitted in the allowedUnsafeExecutions`.
     Der Wrapper-PR enthält danach die neue Gradle-Version, aber die **alte** Prüfsumme. Gradle bricht dann mit einem Prüfsummenfehler ab, und `android-build` wird rot.
   - Diskussion: Warum gilt das als „unsafe“? Der Gradle-Build-Code aus dem Repository läuft dabei im Renovate-Prozess, der das Token besitzt.
     Warum lässt sich die Option nur global setzen (im Workflow) und nicht in `renovate.json`?

---

## Bonus-Aufgaben

- **Digests pinnen:** `docker:pinDigests` erweitern. Was ändert sich im `Dockerfile`, und welche Update-Typen entstehen dadurch (`pinDigest`, `digest`)?
- **Reifezeit:** Automerge erst, wenn eine Version mindestens 3 Tage alt ist (`minimumReleaseAge`).
- **Majors nur auf Freigabe:** `dependencyDashboardApproval` für Major-Updates. PRs entstehen dann erst nach Haken im Dependency Dashboard.
- **Zeitplan:** Automerge nur außerhalb der Arbeitszeit (`automergeSchedule`).
- **Konfiguration validieren:** `npx --package renovate -- renovate-config-validator renovate.json` (benötigt Node.js 24).

## Musterlösung

Die Musterlösung liegt unter [`loesung/renovate.json`](loesung/renovate.json). Bitte erst nach Teil 2 und 3 ansehen.
Um sie zu übernehmen, die Datei nach `renovate.json` kopieren und auf `main` pushen.

## Fehlersuche

| Symptom | Ursache / Lösung |
|---|---|
| Workflow startet im Fork nicht | Actions im Fork aktivieren (Teil 0, Schritt 2) |
| `Resource not accessible by integration` beim Anlegen von PRs | „Allow GitHub Actions to create and approve pull requests“ aktivieren (Teil 0, Schritt 3) |
| Kein Dependency Dashboard | Issues im Fork aktivieren (Teil 0, Schritt 4) |
| Automerge passiert nicht | CI auf dem PR nicht gelaufen (Variante A: freigeben), CI rot, oder es gab danach keinen weiteren Renovate-Lauf bzw. „Allow auto-merge“ fehlt |
| Warnung `gradleWrapper is not permitted in the allowedUnsafeExecutions` im Dependency Dashboard | In `renovate.yml` fehlt `RENOVATE_ALLOWED_UNSAFE_EXECUTIONS: '["gradleWrapper"]'`. Ohne diese Variable hat der Wrapper-PR eine falsche Prüfsumme (siehe Teil 4, Aufgabe 4). |
| PR wird nicht aktualisiert, obwohl `main` sich geändert hat | Renovate rebased erst beim nächsten Lauf. *Run workflow* starten oder im PR die Checkbox „rebase/retry“ setzen und erneut laufen lassen |

## Hinweise für Trainer

- Der Renovate-Workflow läuft auch im **Original-Repository** bei jedem Push.
  Renovate-PRs im Original **nicht mergen**, sonst ist die Vorlage nicht mehr veraltet.
  Alternativ kann Actions im Original-Repository deaktiviert werden.
- Die erwarteten Versionen in Teil 1 stammen aus einem lokalen Probelauf (`renovate --platform=local --dry-run=lookup`, Renovate 44.257.9) vom 22.11.2026.
  Neuere Releases ändern die Zielversionen, aber nicht die Struktur der Übungen.
- Die Java-Anwendung nutzt Log4Shell **nicht** aus. Sie zeigt nur das verwundbare Muster (Benutzereingabe im Log).
