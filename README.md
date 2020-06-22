## Protokół przedstawiający warcaby w wersji amerykańskiej.

Zasady gry:

> Move rules
> 
> There are two different ways to move in English draughts:
> 
> `Simple move: A simple move consists of moving a piece one square
> diagonally to an adjacent unoccupied dark square. Uncrowned pieces can
> move diagonally forward only; kings can move in any diagonal
> direction. Jump: A jump consists of moving a piece that is diagonally
> adjacent an opponent's piece, to an empty square immediately beyond it
> in the same direction (thus "jumping over" the opponent's piece front
> and back ). Men can jump diagonally forward only; kings can jump in
> any diagonal direction. A jumped piece is considered "captured" and
> removed from the game. Any piece, king or man, can jump a king.
> 
> Multiple jumps are possible, if after one jump, anotgither piece is
> immediately eligible to be jumped—even if that jump is in a different
> diagonal direction. If more than one multi-jump is available, the
> player can choose which piece to jump with, and which sequence of
> jumps to make. The sequence chosen is not required to be the one that
> maximizes the number of jumps in the turn; however, a player must make
> all available jumps in the sequence chosen. Kings If a man moves into
> the kings row on the opponent's side of the board, it is crowned as a
> king and gains the ability to move both forward and backward. If a man
> moves into the kings row or if it jumps into the kings row, the
> current move terminates; the piece is crowned as a king but cannot
> jump back out as in a multi-jump, until another move. `

    
(Źródło: https://en.wikipedia.org/wiki/English_draughts#Starting_position)

Funkcjonalności serwera - tworzenie wielu pokojów, obsługiwanie wielu użytkowników jednocześnie. Każdy gracz ma zdefiniowaną w kodzie łączny czas na ruch (10 minut), czas jest odejmowany z każdym ruchem gracza. 
Będąc w pokoju gracz może dokonać ruchu, który następnie jest walidowany przez serwer oraz przenoszony na tablicę w przypadku pomyślnej walidacji. Gra się kończy, gdy albo gracze zgodzą się na remis, wszystkie pionki jednego gracza znikną z planszy lub czas jednego z gracza dobiegnie do końca. 

W przypadku opuszczenia rozgrywki jest możliwość połączenia ponownie, jednak pokój jest usuwany z pamięci serwera jeżeli obydwoje z graczy opuszczą pokój. Pokój jest tylko wtedy usuwany, gdy żaden z graczy nie jest z nim połączony. Pokoje są przechowywane w pamięci RAM (dokładniej mówiąc w strukturze danych przystosowanej do wielowątkowej obsługi). 

Komunikacja odbywa się na zasadzie request-response. Z uwagi na to, że pokój zawiera niewiele informacji - 
mniej niż 200 znaków, to klienci informacje o stanie planszy odbierają poprzez pobieranie całego stanu gry - planszy i innych zmiennych. 

Serwer przyjmuje komendy w formacie:
`Nazwa_komendy argumenty_do_komendy`
Gdzie każda komenda musi być zakończona znakiem nowej linii: \n, \r lub \r\n. 

Analogicznie odpowiedzi z serwera są w  podobnym formacie:
`KOD_ODPOWIEDZI NAZWA_ZMIENNEJ=[wartość] `
Zmiennych może być więcej lub wcale - w zależności od requesta, który wywołujemy. W przypadku błędów
zamiast zmiennych może się pojawić notatka po spacji od kodu odpowiedzi dlaczego walidacja się nie powiodła - nie powinna być uwzględniana w programie klienta, dlatego jeżeli nie spodziewamy się zmiennych po spacji, request powinien być interpretowany tylko po kodzie odpowiedzi.

Tabela requestów z ich kodami odpowiedzi: 

|Request  | Kod odpowiedzi  | Opis | 
|--|--|--|
|`QUIT`| (Brak - zakończenie komunikacji)  | Kończy komunikacje i opuszcza pokój | 
|`JOIN [UUID-POKOJU] [UUID-KOLORU]` | `ROOM_JOINED lub ROOM_NOT_FOUND`  | Dołącza do istniejącego pokoju, należy podać dwa UUID, jedno identyfikuje jednoznacznie pokój, a drugi gracza w pokoju | 
|`CREATE`|`ROOM_CREATED ROOM_ID=[] PLAYER_COLOR_ID=[] ENEMY_COLOR_ID=[]` | Tworzy pokój oraz zwraca wszystkie 3 uuid wykorzystywane przez graczy - uuid pokoju, uuid gracza 1 i gracza 2. Kolor jest generowany losowo i identyfikowany po uuid gracza. Kolor można poznać przy użyciu `GET_STATE`. Po stworzeniu requesta, automatycznie twórca dołącza do pokoju bez potrzeby `JOIN`.|
|`GET_STATE`| `STATUS_OK STATE=[] PLAYER_TURN=[] WHITE_WANTS_DRAW=[] BLACK_WANTS_DRAW=[] BLACK_TIME=[] WHITE_TIME=[] WHITE_ONLINE=[] BLACK_ONLINE=[] BOARD=[]`  | Zwraca status obecnego pokoju. `STATE` może przyjmować wartości: `WAITING` - oczekiwanie na drugiego gracza aż dołączy, `PLAYING` - w trakcie rozgrywki, `DRAW` - remis, `WHITE_WON` - białe zwyciężyły, `BLACK_WON` - czarne zwyciężyły. `PLAYER_TURN` może przyjmować wartości `BLACK` lub `WHITE`. `WHITE_WANTS_DRAW`, `BLACK_WANTS_DRAW`, `WHITE_ONLINE`, `BLACK_ONLINE` przyjmują wartości logiczne `TRUE` lub `FALSE` i oznaczają to na co nazwa wskazuje. `BLACK_TIME` oraz `WHITE_TIME` opisują ile czasu dla danego gracza pozostało w milisekundach - zaczynając od 10 000 i odliczając od czasu gdy status jest `PLAYING` (drugi gracz dołączy do pokoju). Pole board jest ciągiem znaków opisujący planszę. Pod znajduje się będzie przykładowy ciąg znaków i jak go interpretować.  
|`MOVE [lokalizacja-pionka][cel][cel2][cel3][..]` | `MOVE_OK` lub `MOVE_FAIL` | Po `MOVE` należy podać ciąg ruchów oddzielonych spacją. Ruch składa się z dwóch znaków - pierwszy określa kolumne [a-h] a drugi numer rzędu [1-8]. Kolumny są liczone od lewej, a rzędy od dołu. Zawsze wymagane są co najmniej dwa "ruchy", ponieważ pierwszy zawsze określa, którym pionkiem się ruszamy. Więcej niż dwa ruchy pojawiają się w przypadku, gdy przeskakujemy przez więcej niż jedną figurę przeciwnika. `MOVE_FAIL` pojawia się w przypadku niezgodnego z załączonymi zasadami ruchu - np. niewykorzystaniu skoku, który możemy wykonać.
|`LEAVE`| `ROOM_LEFT` | Wychodzimy z obecnego pokoju. Można użyć ponownie komendy `JOIN` jeżeli drugi gracz nie wyszedł. Uwaga: czas jest dalej odliczany w opuszczonym pokoju. Polecenie nie rozłącza się z serwerem.
| `REQUEST_A_DRAW` | `DRAW_OK` lub `DRAW_FAIL` | Wysyłamy prośbę, że chcemy zakończyć grę remisem. W statusie rozgrywki widnieje ta informacja i drugi gracz może również taki request wysłać co powoduje, że gra jest zakończona remisem. Polecenie zwróci `DRAW_FAIL` jeżeli wcześniej już została taka prośba wysłana i nie została anulowana przez gracza.
| `CANCEL_DRAW_REQUEST` | `DRAW_CANCEL_OK` lub `DRAW_CANCEL_FAIL` | Anuluje prośbę o remis. Polecenie zadziała tylko jeżeli wcześniej prosiliśmy o remis, a gra nie zakończyła się jeszcze remisem.

Uwagi:
`JOIN` oraz `CREATE` mogą być tylko używane gdy nie jesteśmy w pokoju, pozostałe komendy mogą być używane tylko gdy się znajdujemy w pokoju. Wyjątkiem jest polecenie `QUIT`, które można wywołać zawsze.
W przypadku opuszczenia pokoju należy pamiętać, że jeżeli przeciwnik również to zrobi, to pokój przestanie istnieć (zapobieganie wyciekom pamięci). 

**Board zwrócony przez serwer**
Board z polecenia `GET_STATE` może przyjąć taką postać:
`0b0b0b0bb0b0b0b00b0b0b0b0000000000000000w0w0w0w00w0w0w0ww0w0w0w0`
Oznaczenia pól:
`0` - pole puste
`b` - pole z czarną figurą
`B` - pole z czarną królową
`w` - pole z białą figurą
`W` - pole z białą królową

W celu interpretacji planszy należy traktować każde 8 znaków jako jeden rząd, gdzie rząd na samej górze ma indeks 8:
```
0b0b0b0b 8
b0b0b0b0 7
0b0b0b0b 6
00000000 5
00000000 4
w0w0w0w0 3
0w0w0w0w 2
w0w0w0w0 1
abcdefgh
```
Przykładowym pierwszym ruchem przez białego gracza może być `MOVE a3 b4`

## SSL 
Komunikacja jest szyfrowana za pomocą tls. W repozytorium znajduje się przykładowy certyfikat z kluczem
prywatnym w formacie .jks ssl/ceritifacte.jks, który jest domyślnie uruchamiany wraz z aplikacją i może być
zmieniony przy pomocy zmiennych środowiskowych. 
Certyfikat jest przykładowy i pochodzi z oficjalnego poradnika Oracle. 

https://docs.oracle.com/javase/7/docs/technotes/guides/security/jsse/samples/index.html

## Zbudowanie aplikacji
Do zbudowania aplikacji wymagana jest Java w wersji 8 oraz system budowania Maven. 
`mvn clean install`
Do zbudowania aplikacji.
Zbudowany jar można odpalić poleceniem
`java -jar [nazwa-wyprodukowanego-jara]`
