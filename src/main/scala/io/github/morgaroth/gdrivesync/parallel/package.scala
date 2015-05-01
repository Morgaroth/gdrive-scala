package io.github.morgaroth.gdrivesync

package object parallel {
  /**
   *  Koncepcja:
   *    - zrównoloeglanie działania, przede wszystkim pobierania/wysyłania
   *    - ale z ograniczeniem - wiadomo, X na raz to niekoniecznie X razy szybciej
   *    - prosto realizowalne przez pulę aktorów obsługujących zadania
   *        - upload
   *        - update
   *        - download
   *       - trzy zadania obsługiwane przez jednego aktora - przesyłacza
   *      - tworzenie i sprawdzanie folderów i plików może bez problemu dziać się w aktorach synchronizujących
   *        - również skończona i ograniczona ilość jednakże większa od ilości wysyłanych
   *        - obliczanie plików które potrzebują być synchronizowane
   *        - tworzenie folderów zarówno lokalnie, jak zdalnie
   *        - in-out:
   *          - wejście ->
   *            - para folderów, lokalny i zdalny (oba mają istnieć)
   *            - dane do ignorowania plików & folderów
   *            - poboczne dane: aktualna ścieżka - do logów
   *          - wyjście ->
   *            - lista działań na plikach (uobiektowionych - case class) - odfiltrowana z ignorowanych
   *            - lista par folderów do kolejnego działania - odfiltrowana z ignorowanych
   *      - takie oddzielenie pozwoli na ograczanie ruchu, ale zrównoleglanie czynności
   *    - całością zajmuje się master
   *      - posiada dwóch aktorów - przesyłacza i synchronizatora
   *      - rozsyła zadania
   *      - odbiera wyniki zadań
   *    - ograniczenie informacji w konsoli też może odbywać się za pomocą pliku konfiguracyjnego
   *      - poziomy logów - standard
   *      - aspekty logów -
   *        - pobieranie, wysyłanie, progres
   *        - synchronizacja folderów
   *        - logi z analizy - on/off
   *    - tryb: only analyze
   *
   *
   *    - żeby jak najbardziej zbliżyć decycję o akcji (wysłanie pliku, pobieranie itd) do rzeczywistego
   *      wykonania tej akcji można te akcje ubrać jednak we funkcje, a aktorzy mogliby odbierać polecenia
   *      analizy konkretnych bytów, i tak:
   *      - pliki -> parą plików do synchronizacji może być typ posiadający:
   *        * lokalny rodzic (musi istnieć)
   *        * zdalny rodzic (musi istnieć)
   *        * opcjonalny plik lokalny
   *        * opcjonalny plik zdalny
   *        na tej podstawie można szybko ocenić jaka akcja ma być wykonana i od razu ją wykonać,
   *        - pobranie i wysłanie pliku ocenia się prosto - jeden z plików jest None
   *        - negocjacja wersji gdy oba pliki istnieją
   *      - foldery -> zasada taka jak dla plików i dodatkowo po synchronizacji do kolejki powinny być wstawione
   *        kolejne pary plików i folderów -> istniejące w synchronizowanych
   *        * ewentualnie dodatkowo na koniec kolejki mógłby trafić ten folder jeszcze raz dla pewności, że
   *          w czasie synchronizacji zawartości folderu nic się nie zmieniło w jego liście plików
   *          (dodatkowy narzut na negocjację, aczkolwiek negocjacja zabiera czas ale nie łącze,
   *          a to łącze jest kłopotliwe bardziej
   *      - całość zaczyna się poza po uzgodnieniu korzeni folderów do synchronizacji
   *        - lokalny musi istnieć i być czytelny
   *        - zdalny musi być dostępny -> autoryzacja
   */

}
