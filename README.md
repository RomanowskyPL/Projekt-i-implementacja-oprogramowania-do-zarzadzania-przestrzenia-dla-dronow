# Projekt i implementacja oprogramowania do zarządzania przestrzenią dla dronów

Repozytorium zawiera kompletny material projektowy oraz implementacyjny wykonany w ramach **pracy inżynierskiej**

## Zakres pracy

Praca inżynierska została podzielona na cztery główne etapy:
1. **QGIS**
   Wyznaczenie korytarzy powietrznych oraz bezpiecznych tras lotu drona z uwzględnieniem stref ograniczonych, infrastruktury krytycznej oraz uwarunkować przestrzennych.

2. **Baza danych (PostgreSQL / PostGIS)**
   Projekt i implementacja relacyjnej bazy danych umożliwiającej:
   - zapis tras lotów,
   - przechowywanie informacji o operatorach i dronach,
   - rejetrację historii lotów oraz danych telemetrycznych,
   - walidację spójności danych przy użyciu triggerów i funkcji bazodanowych.

3. **Backend (API)**
   Implementacja warstwy serwerowej odpowiedzialnej za:
   - komunikacje pomiędzy aplikacją mobilną a bazą danych,
   - obsługę logiki biznesowej systemu,
   - zarządzanie lotami, trasmi oraz użytkownikami.

4. **Aplikacja mobilna (DJI MSDK v5)**
   Apliakcja mobilna umożliwiająca:
   - inicjalizację połączenia z dronem,
   - pobieranie danych z backendu,
   - wczytywanie zaplanowanych tras,
   - realizację automatycznych misji typu *Waypoint*,
   - rejestrajcę danych telemetrycznych w trakcie lotu.

## Struktura repozytorium

Repozytorium zawiera następujace katalogi:

- **Aplikacja mobilna/** - kod apliakcji mobilnej odpowiedzialnej za realizację misji drona, komunikację z backendem oraz rejestrację danych lotu.
- **Backend/** - implemetrajcja warstwy serwerowej systemu, obsługującej logikę aplikacji oraz komunikację z bazą danych
- **QGIS/** - projekt QGIS oraz dane wykorzystane do analiz przestrzennych i planowania tras
- **Baza danych.backup** - plik kopi zapasowej bazy danych zawierający strukturę tabeli, dane testowe oraz obiekty bazodanowe



## Informacje dodatkowe

Repozytorium stanowi **załącznik do pracy inżynierskiej** i zawiera komplet materiałów niezbędnych do analizy, uruchomienia oraz dalszego rozwoju systemu.

## Uruchomienie (lokalnie)

Aby uruchomić projekt lokalnie, należy uzupełnić konfigurację:
1. **Aplikacja mobilna**
   - Api.kt - ustawić wartość `BASE_HOST` (adres hosta/serwera backend).
   - res/values/strings.xml - uzupełnić wymagane wartości konfiguracyjne (klucze dla usług mapowych oraz DJI)
 
