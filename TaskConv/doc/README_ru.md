# TaskConv

Утилита для конверсии групповых тестов в формат DL.
Версия 2.0

## Применение

```
usage: TaskConv {-h|[-d] <путь>} [[-w] <путь>] [опции]
 -a,--auto                определить тип задачи автоматически при
                          неопределённом результате
 -d,--directory <путь>    папка задачи (обязательная)
 -h,--help                выводит это сообщение
 -i,--infiles-only        обрабатывать только входные файлы (для задач без
                          выходных файлов)
 -k,--keep                при использовании с --move, не удалить рекурсивно
                          пустые папки из папки задачи после обработки файлов
 -l,--level <int>         уровень глубины (по умолчанию 0)
 -m,--move                перемещать файлы вместо копирования
 -n,--name <название>     название задачи (заданное, а не определяемое)
 -o,--output <имя файла>  файл вывода (результат); по умолчанию "marks.txt"
 -q,--quiet               подавлять вывод всех сообщений
 -t,--type <префикс>      требуемый префикс типа задачи (IOI, CEOI и т.п.)
 -v,--verbose             подробный вывод
 -w,--workdir <путь>      директория вывода (по умолчанию папка задачи)
```

Опции с аргументами могут быть заданы следующими способами:  
`-d TaskDir`  
`-dTaskDir`  
`--directory=TaskDir`  
`--directory TaskDir`  
Также можно писать короткие опции вместе:  
`TaskConv -mvco test.txt --directory taskpath`

TaskType – требуемый префикс имени типа задачи. Т.е., `-t IOI` ограничит выбор программы типами с
именами, начинающимися с `IOI`: `IOI`, `IOI11`, `IOI10a`, `IOI 2013` и т.д.

Также можно использовать свободные аргументы (без опций); такие аргументы обрабатываются после
всех остальных, по следующим правилам:
 * если папка задачи (`-d`) не была задана, считается, что аргумент задаёт её
 * иначе, если директория вывода (`-w`) не была задана, считается, что аргумент задаёт её
 * иначе аргумент нелегален и программа откажется запускаться

Уровень глубины – количество уровней, на которое нужно опуститься из TaskDir, чтобы добраться до
задачи; а именно, ВСЕ подпапки на этом уровне считаются задачами ОДНОГО ТИПА. Если ЛЮБАЯ из них
не пройдёт определение типа, он не определится для всей группы.  
Пример применения:
 * задачи размещены так: `Baltica2011/Day 1/task1`
 * запускаем программу так: `TaskConv Baltica2011 -l2`


Файлы в папке "DB/" соответствуют известным типам. Можно спокойно добавлять свои (но обязательно
проверять их на правильность перед использованием). Их формат следующий:

1-я строка = шаблон input файла
2-я строка = шаблон output файла
Оба шаблона используют символ '/' как разделитель директорий.

детали шаблона:
	${varname} - переменная
	$[varname] - переменная может быть опущена (только SS или SL)
переменные:
	TaskName - название задачи
	S - номер группы тестов
	SS - номер теста в группе
	SL - буква теста в группе (Alphabet[SS])

TaskName – единственная переменная, не имеющая отношения к структуре тестов.
Если программе по какой-то причине не удаётся определить её, можно задать значение опцией "-n".

Необязательный файл ".cfg" содержит символы для проверки путей:
	В первой строке содержатся правильные символы для TaskName (по умолчанию латинские буквы).
Проверка путей регистронечувствительна.


## Конфигурация

Файл `TaskConv.yaml` содержит конфигурацию в формате YAML, включая токены и типы задач. Он должен
находиться в одной папке с программой.

Токены – это «заполнители» используемые в шаблонах файлов типа задач, определяющие значение поля. У
каждого токена есть название (состоящее *только* из латинских букв), а также некоторые из
нижеследующих полей:
 * `key`: идентифицирует значение, соответствующее токену (если не указан, используется имя токена);
   следующие ключи имеют особое значение:
   - `name` (название задачи – кодовое имя задачи, определённое автором; может быть фиксировано
     параметром `-n`)
   - `group` (номер группы, как правило целое число определяющее индекс группы)
   - `test` (номер теста, как правило целое число определяющее индекс теста в группе)
 * `regex` (*обязателен*): шаблон-регулярное выражение, описывающий токен (внимание: если `key`
   совпадает с названием CLI-параметра, его значение замещает этот шаблон строкой-литералом)
 * `value`: определяет как парсится значение (по умолчанию остаётся строкой); на данный момент
   поддерживаются следующие значения: `int`, `letternum` ("число из букв", считается начиная с `1`)
 * `fixed` (булевский, по умолчанию `no`) – значения таких токенов должны совпадать для всех файлов
   задачи (если это мешает определению типа задачи, можно воспользоваться параметрами `-t`/`-n`)

Типы задач определяют распознаваемые конфигурации файлов; у каждого есть название (влияет только на
выводимые сообщения и фильтрацию по `-t`) и список элементов:
 * 1-й элемент = шаблон input-файла (обязателен)
 * 2-й элемент = шаблон output-файла (обязателен)
 * 3-й и последующие = определения тестов (для автоматического тестирования конфигурации), каждый
   включает следующие поля:
   - `test`: список файлов в папке задачи (до запуска конверсии)
   - `marks`: файл `marks.txt` получаемый в результате

Шаблон – строка содержащая путь файла с токенами, подставленными по названию, либо с обязательным
размещением (`${Token}`), либо с необязательным (`$[Token]`); например, `tests/${S}$[SL].in`.

Определения тестов (необязательные) запускаются с конфигурацией загруженной из конфиг-файла, и от
кода ожидается что он сможет однозначно определить тип задачи и выдать заданное количество пар
input-/output-файлов, а также сгенерировать файл `marks.txt` с заданной информацией по группам.
(Также они могут служить примерами для читателей файла.)


## Замечания по использованию

Если для задачи не удалось определить подходящий тип, программа откажется работать. Можно
попробовать запустить её с подробным выводом (`-v`), чтобы узнать, почему это случилось. _Вероятной
причиной может быть отсутствие output-файлов в задаче: в этом случае она не будет распознана без
параметра `-i`._

Если для задачи определилось более одного типа, программа сортирует типы по количеству найденных
input-файлов (если был использован `-i`) или пар input-/output- (в противном случае). После этого,
если программа работает в автоматическом режиме (`-a`), она просто выберет тип с наибольшим
количеством совпадений; в противном случае она запросит ввод пользователя (`0` для отмены, `1` для
типа по умолчанию и т.д.).


## Скрипты

Папка `scripts/` содержит несколько вспомогательных скриптов, для тестирования и компиляции.
При отсутствии параметра `--keep` в строке запуска скрипт удаляет созданные им временные файлы.

Скрипт `init` содержит общий код инициализации; если его запустить вручную, он скачает и установит
зависимости, необходимые для других скриптов (иначе это будет делаться при каждом запуске).

Скрипт `run-tests` запускает юнит-тесты (и тесты конфигурации) из файла `TaskConv_test.py`. Если
какой-то тест отработает с ошибкой, будет выведена дополнительная информация.

Скрипт `gen-tests` генерирует папку `tests/` с файлами из тестов конфигурации, с целью тестирования
вручную. (При наличии у типа более одной «задачи» они размещаются в подпапки.)

Скрипт `compile` генерирует самостоятельный исполнимый файл, не требующий Python и
библиотек-зависимостей для запуска. (Ему всё ещё нужен конфиг-файл `TaskConv.yaml`.)