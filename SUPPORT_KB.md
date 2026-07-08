# OnyxLauncher - Baza Wiedzy i Pomoc Techniczna (Support KB)
Ostatnia aktualizacja: 2026-06-29

Witaj w bazie wiedzy OnyxLauncher! Poniżej znajdziesz rozwiązania najczęstszych problemów i błędów, które mogą wystąpić podczas uruchamiania gry lub korzystania z modów.

---

## 🚀 Błędy przy uruchamianiu gry

### 1. Błąd: "unable to read version JSON for version X.XX.X"
* **Objawy:** Gra nie chce się uruchomić, pojawia się komunikat o niemożności odczytania pliku JSON dla określonej wersji (np. `1.21.1`).
* **Przyczyna:** Modyfikacje typu Fabric, Quilt czy Forge dziedziczą po czystej wersji Minecraft (np. Fabric 1.21.1 dziedziczy po 1.21.1). Jeśli uruchamiasz launcher bez połączenia z internetem lub po restarcie aplikacji, launcher nie ma w pamięci podręcznej informacji o adresie URL czystej wersji.
* **Rozwiązanie automatyczne:** W najnowszej wersji OnyxLauncher ten problem został rozwiązany automatycznie — launcher sam pobiera manifest Mojang w tle i naprawia brakujące pliki.
* **Rozwiązanie manualne:** Upewnij się, że masz aktywne połączenie z internetem podczas pierwszego uruchamiania danej wersji modloadera, aby launcher mógł pobrać wymaganą wersję bazową.

---

### 2. Błąd: "Cannot find compatible Java runtime" lub błąd środowiska Java
* **Objawy:** Gra wykrzyca błąd dotyczący braku kompatybilnego środowiska Java (np. wymagana wersja 17 lub 21).
* **Przyczyna:** Nowsze wersje Minecrafta wymagają nowszych wersji Java:
  * Minecraft 1.16.5 i starsze: **Java 8**
  * Minecraft 1.17 - 1.20.4: **Java 17**
  * Minecraft 1.20.5 / 1.21+: **Java 21 / 25**
* **Rozwiązanie automatyczne:** Launcher automatycznie wykrywa wymaganą wersję i pobiera odpowiednią paczkę JRE.
* **Rozwiązanie manualne:** Przejdź do **Ustawień** -> **Zarządzaj środowiskami Java** i upewnij się, że pobrana jest właściwa wersja Java (np. `Internal-21`).

---

## 🛠️ Błędy i crashe podczas gry (Wydajność / Mody)

### 3. OpenGL ES (GL4ES) vs Vulkan (Zink) - Różnice w renderowaniu i shaderach
* **Opis renderera OpenGL ES (gl4es):**
  * Służy głównie jako translator starszych instrukcji OpenGL 2.1 na instrukcje OpenGL ES 2.0/3.0.
  * Znakomicie radzi sobie z renderowaniem podstawowej gry, interfejsu (2D) oraz modów niewymagających nowoczesnego potoku renderowania.
  * **Ograniczenie:** Nie wspiera zaawansowanych funkcji OpenGL 3.3+ (takich jak shadery geometrii czy obliczeniowe), dlatego **nowoczesne shadery 3D (Iris/OptiFine) nie mogą na nim działać** i wywołają natychmiastowy crash gry (SIGSEGV / Kod błędu 6) przy wejściu na świat.
* **Opis renderera Vulkan (Zink):**
  * Jest to nowoczesny translator, który mapuje pełne OpenGL 3.3 Core Profile bezpośrednio na instrukcje Vulkan (wykorzystując sterownik Turnip).
  * Jest to jedyny renderer, który umożliwia działanie nowoczesnych shaderów 3D na telefonie.
  * **Problem na Snapdragon 8 Gen 3 (Adreno 750):** Sterowniki Turnip/Zink w starszych wersjach mogą wywołać natychmiastowy crash shaderów na Adreno 750 ze względu na nowy podsystem pamięci GPU firmy Qualcomm.

---

### 3b. Paczki zasobów (TXT) crashują na rendererze GL4ES
* **Objawy:** Gra wyłącza się przy ładowaniu zwykłej paczki tekstur (Resource Pack / TXT) na rendererze GL4ES.
* **Przyczyna:** Domyślne ustawienie generowania mipmap w gl4es (`LIBGL_MIPMAP=3`) powoduje przepełnienie bufora pamięci sterownika graficznego na nowszych układach Adreno.
* **Rozwiązanie automatyczne:** W najnowszej wersji OnyxLauncher wyłączyliśmy mipmapowanie po stronie gl4es (`LIBGL_MIPMAP=0`), przekazując pełną kontrolę nad mipmapami silnikowi Minecrafta. Dzięki temu **paczki tekstur (TXT) działają stabilnie na gl4es**.

---

### 3c. Jednoczesne korzystanie z Paczek Zasobów (TXT) oraz Shaderów (Iris / OptiFine)
* **Objawy:** Wyjście z błędem `IllegalStateException: Could not parse GL version from ""` lub wywalenie z kodem 6 podczas ładowania świata z podpiętą paczką zasobów (Resource Pack) i aktywnymi shaderami.
* **Przyczyna:** Mody shaderowe (np. Iris) próbują parsować ciąg wersji OpenGL z emulatora `gl4es`. Bez specjalnej konfiguracji zmiennych środowiskowych translator gl4es dostarczał format ciągu wersji nieczytelny dla silnika Iris.
* **Rozwiązanie automatyczne:** W najnowszej wersji OnyxLauncher ten problem został **w pełni rozwiązany i zautomatyzowany**. Launcher samoczynnie wstawia zmienne środowiskowe `LIBGL_CUSTOMVERSION="3.3.0"` oraz optymalizuje profil renderera przy wykryciu Iris/OptiFine, co pozwala na jednoczesne granie z paczkami zasobów (TXT) oraz shaderami bez jakiejkolwiek ingerencji użytkownika!

---

### 4. Błąd: "Preparing spawn area" utknął na 51% lub gra wolno się ładuje
* **Objawy:** Podczas generowania nowego świata pasek postępu zatrzymuje się na 51% na dłuższą chwilę.
* **Przyczyna:** Proces generowania terenu przez procesor telefonu przy mocno ograniczonej pamięci RAM.
* **Rozwiązanie:**
  1. Wejdź w profil gry i zwiększ przydzieloną pamięć **RAM** (zalecane minimum 2048 MB dla nowszych wersji).
  2. Zmniejsz odległość renderowania (Render Distance) w ustawieniach gry do 8-12 chunków.

---

### 5. Mody nie ładują się lub wywalają błąd przy starcie
* **Objawy:** Minecraft zgłasza błąd podczas ładowania modów lub Fabric/Forge informuje o braku zależności.
* **Przyczyna:**
  * Pobrano mod dedykowany dla Forge na profilu z Fabric (lub odwrotnie).
  * Wersja modu nie zgadza się z dokładną wersją Minecrafta.
  * Brak wymaganego modu pomocniczego (np. brak **Fabric API** dla modów Fabric).
* **Rozwiązanie:** Upewnij się, że pobierasz mody z zaufanych źródeł (Modrinth/CurseForge), zawsze sprawdzaj zakładkę z wymaganymi zależnościami (Dependencies) oraz upewnij się, że instalujesz **Fabric API** jako pierwszy mod.

---

## ℹ️ Ostrzeżenia w logach (Można bezpiecznie zignorować)

Poniższe błędy i ostrzeżenia pojawiają się w logach gry, ale **nie wpływają** na stabilność i można je bezpiecznie zignorować:

1. **`Could not find any graphics adapters! Probably the device is not on a bus we can probe`**
   * Sodium szuka dedykowanej karty graficznej przez szynę PCI (jak w komputerze stacjonarnym). Na Androidzie taka szyna nie istnieje. Ostrzeżenie bez znaczenia.
2. **`Error while loading the narrator / Unable to load library 'flite'`**
   * System syntezy mowy (narrator) nie jest wspierany na systemie Android w bibliotekach Minecrafta. Dźwięk w grze działa normalnie.
3. **`LWJGL Incompatible Java and native library versions detected`**
   * Ostrzeżenie biblioteki LWJGL dotyczące ścieżek natywnych na urządzeniach mobilnych.
4. **`ALSOFT (EE) Failed to set real-time priority for thread`**
   * System Android ze względów bezpieczeństwa blokuje nadawanie priorytetu czasu rzeczywistego dla wątków audio. OpenAL działa poprawnie.

---

## 📋 Jak znaleźć i wysłać logi w razie problemów?

Jeśli napotkasz problem, którego nie ma w bazie wiedzy:
1. W OnyxLauncher wejdź w ustawienia profilu.
2. Kliknij **Udostępnij logi** lub przejdź do folderu instancji `.minecraft/latestlog.txt`.
3. Prześlij plik `latestlog.txt` lub `hs_err_pidXXX.log` do zespołu wsparcia technicznego.
