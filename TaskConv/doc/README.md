# TaskConv

Utility tool for converting group tests to DL format.
Version 2.0.1

## Usage

```
usage: TaskConv {-h|[-d] <path>} [[-w] <path>] [options]
 -a,--auto                decide on task type automatically if result is
                          ambiguous
 -d,--directory <path>    task directory (required)
 -h,--help                print this message
 -i,--infiles-only        process input files only (for tasks without
                          output files)
 -k,--keep                when used with --move, don't remove empty
                          directories recursively from task directory
                          after processing files
 -l,--level <int>         depth level (default is 0)
 -m,--move                move files instead of copying
 -n,--name <taskname>     task name (predefined instead of detecting)
 -o,--output <filename>   output filename (result); default is "marks.txt"
 -q,--quiet               suppress all messages
 -t,--type <prefix>       required prefix of the task type (IOI, CEOI etc)
 -v,--verbose             verbose output
 -w,--workdir <path>      output directory (default is task directory)
```

Options with arguments can be written in following ways:  
`-d TaskDir`  
`-dTaskDir`  
`--directory=TaskDir`  
`--directory TaskDir`  
You can also write short options together:  
`TaskConv -mvco test.txt --directory taskpath`

TaskType is a required prefix for task type names. That is, `-t IOI` will limit program's choice
to the type names beginning with `IOI`: `IOI`, `IOI11`, `IOI10a`, `IOI 2013`, etc.

You may also use loose arguments (without options); such arguments are processed after all others
by following rules:
 * if task directory (`-d`) wasn't set, argument is considered to describe it
 * otherwise, if output directory (`-w`) wasn't set, argument is considered to describe it
 * otherwise the argument is illegal and the program will refuse to run.

Depth level is number of levels program needs to descend from TaskDir to get to the tasks; that is,
ALL subdirectories on that level are considered tasks of the SAME TYPE. If ANY of them fails
type detection, it fails for the whole group.  
Usage example:
 * tasks are placed like `Baltica2011/Day 1/task1`
 * program is called like `TaskConv Baltica2011 -l2`


## Config

The `TaskConv.yaml` file contains configuration in the YAML format, including tokens and task types.
It's expected to be placed in the same directory as the program.

Tokens are "placeholders" used in file patterns for a task type to define a field value. Each token
has a name (which must consist of *only* latin alphabet letters), as well as some of the following
fields:
 * `key`: identifies the value matched by the token (defaults to token name if not found); the
   following keys have special meaning:
   - `name` (task name, a keyword assigned to the task by the author – can be predefined using `-n`)
   - `group` (group number, typically an integer identifying index of the group)
   - `test` (test number, typically an integer identifying index of the test within the group)
 * `regex` (*required*): regular expression pattern that describes the token (note that if the `key`
   matches a CLI argument name, it can be used to override the pattern with a literal string)
 * `value`: defines how the value is parsed (it's a string by default); the following values are
   currently recognized: `int`, `letternum` (a "letters-only" number, counted starting from `1`),
   `zero` (a fixed value `0`)
 * `fixed` (boolean, defaults to `no`) matches are required to be consistent between all files in
   the task (this may lead to detection failures – you can use `-t`/`-n` to deal with those)

Task types define recognized task layouts; each has a name (only affecting output and `-t` filter),
and a list of items:
 * 1st item = input filename pattern (required)
 * 2nd item = output filename pattern (required)
 * 3rd and following = test definitions (for automatic config testing), each containing fields:
   - `test`: list of files in the task folder (before running the conversion)
   - `marks`: resulting `marks.txt` file

A pattern is a string containing file path with tokens injected by name, as either required
(`${Token}`) or optional (`$[Token]`) match; i.e. `tests/${S}$[SL].in`.

Additionally, there's a special token `$|` which separates alternative patterns (like `|` in regex).
Note that it separates _entire_ patterns, which are tested in order until a match is found.

The (optional) test definitions are run agains the configuration loaded from the config file,
and the code is expected to detect the task type unambiguously and produce a specified number of
input/output file pairs, as well as generate a `marks.txt` file with specified groups information.
(Additionally they serve as examples for anyone viewing the file.)


## Execution notes

If the task wasn't detected to be of any type whatsoever, the program will refuse to work. You may
want to try verbose output (`-v`) to find out why this happened. _A likely reason may be that the
task has no output files: in this case it wouldn't be recognized unless `-i` is supplied._

If the task was detected to be of more than one type, the program will sort types by the number of
detected input files (if `-i` was used) or input/output file pairs (otherwise). After that, if
program is running in automatic mode (`-a`), it will just choose the type with the maximum number of
detected files; otherwise it will ask user for input (`0` to cancel, `1` is first matched type,
etc.).


## Scripts

The `scripts/` subfolder contains a few convenience scripts, for testing and compilation.
Unless you add `--keep` to the script call, it removes its temporary files after execution.

The `init` script contains shared initialization code; run it once to download & install
dependencies required for other scripts (otherwise it will be done on every run).

The `run-tests` script runs unit tests as well as config tests, both located in `TaskConv_test.py`.
In case any of them fail, you'll get additional output containing additional information.

The `gen-tests` script generates a `tests/` folder containing files from the config test, for sake
of manual testing. (When there's more than one "task" in a type, they're placed in subfolders.)

The `compile` script produces a standalone executable file which doesn't require any Python
dependencies to run. (It still needs the `TaskConv.yaml` config file.)
