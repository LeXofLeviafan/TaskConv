#!/usr/bin/env python3
from argparse import ArgumentParser
from collections import Counter
from functools import reduce
from glob import glob
import re, yaml, os, shutil, sys

VERSION = "2.0"
TOKEN = r"(?i)\$(\{[a-z]+\}|\[[a-z]+\])"
TOKEN_NAME = r"(?i)[a-z]+$"
ABC = ''.join(chr(i) for i in range(ord('a'), ord('z')+1))
CWD = os.getcwd()

def letternum (s):
    digits = [ABC.index(c)+1 for c in s.lower()]
    return reduce(lambda acc, x: acc*len(ABC)+x, digits, 0)

valparser = lambda k: {'int': int, 'letternum': letternum}.get(k, str)


## file matching

def match_file (filename, pattern, tokens, previous={}):
    match = pattern.match(filename)
    if not match:
        return None
    res = {}
    for k, v in match.groupdict().items():
        k, v = tokens[k]['key'], v and valparser( tokens[k].get('value') )(v)
        if v and res.get(k, v) != v:
            return None
        res[k] = v
    for k in sorted(res):
        if k in previous and previous[k] != res[k]:
            raise ValueError("File \"%s\" is detected with a changed value of '%s'." % (filename, k))
    return res

def match_files (tasks, tokens, typename, infile, outfile, *, infiles_only=False, **kwargs):
    ins, outs, fixed = {}, {}, {it['key'] for it in tokens.values() if it.get('fixed')}
    for task, files in tasks.items():
        previous, infiles, outfiles = {}, {}, {}
        for s in files:
            _in = match_file(s, infile, tokens, previous)
            _out = not infiles_only and match_file(s, outfile, tokens, previous)
            if _in and _out:
                raise ValueError("File \"%s\" is detected as both input and output file for type '%s'." % (s, typename))
            if _in:
                infiles[_in.get('group') or 0, _in.get('test') or 0] = s
            if _out:
                outfiles[_out.get('group') or 0, _out.get('test') or 0] = s
            if _in or _out:
                for k in (_in or _out):
                    if k in fixed and (_in or _out).get(k) is not None:
                        previous[k] = (_in or _out)[k]
        if not infiles:
            return None
        if not infiles_only and set(infiles) != set(outfiles):
            raise ValueError(("Input and output file sets do not match" if outfiles else "No output files detected")
                              + " for type '%s'." % (typename,))
        ins[task], outs[task] = infiles, outfiles
    return ins, outs


## config parsing

def tokenize (pattern, tokens):
    keys = []
    for i, s in enumerate( re.split(TOKEN, pattern) ):
        if i % 2 == 0:  # even matches are text literals
            yield re.escape(s)
        else:           # odd matches are tokens
            optional = (s[0] == "[")
            k = s[1:-1]
            if k not in tokens:
                raise ValueError("unknown token '%s'" % (k,))
            token = tokens[k]
            yield (("(?P={})" if k in keys else "(?P<{}>{})") + ("?" if optional else "")).format(k, token['regex'])
            keys += [k]

parser = lambda pattern, tokens: re.compile("(?i)%s$" % "".join( tokenize(pattern, tokens) ))

def load_config (config, **kwargs):
    data = yaml.load(config, yaml.Loader)
    tokens = {}
    for token, params in data['tokens'].items():
        if not re.match(TOKEN_NAME, token):
            raise ValueError("Token name `%s` is not allowed" % (token,))
        params.setdefault('key', token)
        pattern = kwargs.get(params['key']) or ''
        try:
            params['regex'] = re.compile(re.escape(pattern) or params.get('regex')).pattern
        except (re.error, TypeError):
            raise ValueError("In token '%s': `%s` is not a valid regex" % (token, params.get('regex')))
        tokens[token] = params
    types = {}
    for tasktype, patterns in data['types'].items():
        try:
            assert len(patterns) >= 2 and set(map(type, patterns[:2])) == {str}, "Expected 2 patterns"
            types[tasktype] = tuple(parser(s, tokens) for s in patterns[:2])
        except Exception as e:
            raise ValueError("In tasktype '%s': %s" % (tasktype, e))
    return tokens, types


def argparse (argv=None):
    argparser = ArgumentParser(description="Utility tool for converting group tests to DL format (version %s)" % VERSION)
    argument = argparser.add_argument
    _argument = lambda *flags, help="", add=argument: add(*flags, action='store_const', const=True, default=False, help=help)
    _argument('-a', '--auto', help="decide on task type automatically if result is ambiguous")
    verbosity = argparser.add_mutually_exclusive_group()
    _argument('-q', '--quiet', help="suppress all messages (also applies --auto)", add=verbosity.add_argument)
    _argument('-v', '--verbose', help="verbose output", add=verbosity.add_argument)
    _argument('-m', '--move', help="move files instead of copying")
    _argument('-k', '--keep', help="when used with --move, don't remove empty directories recursively from task directory after processing files")
    _argument('-i', '--infiles-only', help="process input files only (for tasks without output files)")
    argument('-t', '--type', metavar='PREFIX', default="", help="required prefix of the task type (IOI, CEOI etc)")
    argument('-o', '--output', metavar='FILENAME', default="marks.txt", help="output filename (result); default is \"marks.txt\"")
    argument('-l', '--level', metavar='INT', type=int, default=0, help="depth level (default is 0)")
    argument('-n', '--name', metavar='TASKNAME', help="task name (predefined instead of detecting)")
    argument('-w', '--workdir', metavar='DIRECTORY', help="output directory (default is task directory)")
    directory = argparser.add_mutually_exclusive_group(required=True)
    directory.add_argument('-d', '--directory', metavar='DIRECTORY', help="task directory (required)")
    directory.add_argument('directory_', metavar='TASKDIR', nargs='?', help="task directory (required)")
    argument('workdir_', metavar='WORKDIR', nargs='?', help="output directory (default is task directory)")
    args = argparser.parse_args(argv)
    args.auto = args.quiet or args.auto
    args.directory = args.directory or args.directory_
    args.workdir = args.workdir or args.workdir_ or args.directory
    del args.directory_
    del args.workdir_
    return args


## I/O

def choice (types):
    print("More than one type was detected. Asking for input.")
    sys.stdin.flush()
    while True:
        print()
        print("Choose one of the following:")
        print("[ 0] cancel")
        for i, t in enumerate(types, start=1):
            print("[%2d] %s" % (i, t))
        print("> ", end="")  # prompt
        sys.stdout.flush()
        try:
            n = int(sys.stdin.readline())
        except ValueError:
            print("Input is invalid!")
            continue
        if n == 0:
            return None
        try:
            return types[n - 1]
        except IndexError:
            print("Incorrect choice!")

def write (infiles, outfiles, *, directory=None, workdir=None, output='marks.txt',
           quiet=False, verbose=False, move=False, keep=False, infiles_only=False, **kwargs):
    _print = (print if verbose else lambda *a, **kw: None)
    def _copy (src, dst):
        (shutil.move if move else shutil.copy2)(src, dst)
        _print("\"%s\" %s \"%s\"" % (src, ("=>" if move else "->"), dst))
    # moving/copying files
    _print()
    marks = {}
    os.chdir(CWD)
    for task in sorted(infiles):
        ins, outs = infiles[task], outfiles[task]
        marks[task] = Counter(group for group, test in ins)
        _dir = workdir + '/' + task
        if not os.path.exists(_dir):
            os.makedirs(_dir, exist_ok=True)
            _print("Creating task directory \"%s\"." % (_dir,))
        for i, k in enumerate(sorted(ins), start=1):
            _copy("/".join([directory, task, ins[k]]), "%s/%d.in" % (_dir, i))
            if not infiles_only:
                _copy("/".join([directory, task, outs[k]]), "%s/%d.out" % (_dir, i))
    if not keep:
        _print()
        os.chdir(directory)
        for it, _, _ in os.walk('.', topdown=False):
            if not os.listdir(it):
                os.rmdir(it)
                _print("Removed empty directory: \"%s\"." % (it,));
    _print()
    # generating marks
    os.chdir(CWD)
    failed = False
    for task in sorted(marks):
        try:
            outfile = "/".join([workdir, task, output])
            _print("Writing to \"%s\"..." % (outfile,), end="")
            with open(outfile, 'w') as fout:
                for group, tests in sorted(marks[task].items()):
                    for _ in range(1, tests):
                        print(-1, file=fout)
                    print(1, file=fout)
            _print(" done.")
        except IOError:
            failed = True
            _print(" can't write to OutFile!")
    if failed:
        raise IOError("Couldn't write to some OutFiles!")
    if not quiet:
        print("Conversion finished successfully.")

sort_matches = lambda m: sorted(m, key=lambda k: -sum(len(xs) for ins, outs in [m[k]] for xs in ins.values()))
normalize = lambda s: re.sub(r"\./", "", s.replace("\\", "/").rstrip("/"))
all_files = lambda: [it+"/"+f for it, _, fs in os.walk(".") for f in fs]

def process (tokens, types, *, directory=None, level=0, type='', quiet=False, verbose=False, auto=False, **kwargs):
    _print = (print if not quiet else lambda *a, **kw: None)
    _directory = os.path.abspath(directory)
    # scanning task files
    os.chdir(_directory)
    tasks = {}
    for task in glob("*/"*level or "."):
        os.chdir(_directory + '/' + task)
        tasks[normalize(task)] = sorted([normalize(s) for s in all_files()])
    if not tasks:
        raise ValueError("No subdirectories found at specified level!")
    # detecting types
    matches = {}
    filtered = [s for s in types if s.startswith(type)]
    if not filtered:
        raise ValueError("Task type not recognized!")
    for tasktype in filtered:
        try:
            match = match_files(tasks, tokens, tasktype, *types[tasktype], **kwargs)
            if match:
                matches[tasktype] = match
        except ValueError as e:
            verbose and _print(e, file=sys.stderr)
    names = sort_matches(matches)
    if names:
        _type = names[0] if auto or len(names) == 1 else choice(names)
    else:
        raise ValueError("Failed to detect task type!")
    if len(names) == 1:
        _print("Detected task type", _type)
    elif auto:
        _print(len(names), "types detected, assuming", _type)
    elif not _type:
        _print("Received exit command")
        return
    # applying changes
    write(*matches[_type], directory=directory, quiet=quiet, verbose=verbose, **kwargs)

def main (argv=None, config=(os.path.dirname(sys.argv[0]) or ".") + "/TaskConv.yaml"):
    args = argparse(argv)
    with open(config) as fin:
        tokens, types = load_config(fin, **vars(args))
    try:
        process(tokens, types, **vars(args))
    except ValueError as e:
        args.quiet or print(e)

if __name__ == '__main__':
    main()
