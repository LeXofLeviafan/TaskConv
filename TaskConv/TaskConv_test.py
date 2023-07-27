#!/usr/bin/env python3
from pytest import raises, mark, fixture
import os, shutil, re, json, yaml, sys

from TaskConv import CWD, letternum, tokenize, parser, load_config, match_file, match_files, sort_matches, process

S = (r"\/" if sys.version_info < (3, 4) else "/")
TOKENS = {'TaskName': {'key': 'name',  'regex': "[a-z]+", 'fixed': True},
          'S':        {'key': 'group', 'regex': "[0-9]+", 'value': 'int'},
          'SS':       {'key': 'test',  'regex': "[0-9]+", 'value': 'int'},
          'SL':       {'key': 'test',  'regex': "[a-z]+", 'value': 'letternum'}}


@mark.parametrize("string, number", [
    ('a',  1),      ('c',  3),     ('z',  26),
    ('aa', 26+1),   ('ac', 26+3),  ('az', 26*2),
    ('ba', 26*2+1), ('bz', 26*3),  ('ca', 26*3+1),
    ('yz', 26*26),  ('zz', 26*27), ('aaa', 26*27+1),
])
def test_letternum (string, number):
    assert letternum(string) == number


@mark.parametrize("pattern, result", [
    ("x",                                            ["x"]),
    ("${TaskName}.in.${S}$[SL]",                     ["", r"(?P<TaskName>[a-z]+)", r"\.in\.", r"(?P<S>[0-9]+)", "", r"(?P<SL>[a-z]+)?", ""]),
    ("appeal/Subtask${S}-data/grader.in.${SS}-${S}",
     ["appeal%sSubtask" % S, r"(?P<S>[0-9]+)", r"\-data%sgrader\.in\." % S, r"(?P<SS>[0-9]+)", r"\-", r"(?P=S)", ""]),
])
def test_tokenize (pattern, result):
    assert list( tokenize(pattern, TOKENS) ) == result


@mark.parametrize("pattern, result", [
    ("x",                                            r"(?i)x$"),
    ("${TaskName}.in.${S}$[SL]",                     r"(?i)(?P<TaskName>[a-z]+)\.in\.(?P<S>[0-9]+)(?P<SL>[a-z]+)?$"),
    ("appeal/Subtask${S}-data/grader.in.${SS}-${S}", r"(?i)appeal%sSubtask(?P<S>[0-9]+)\-data%sgrader\.in\.(?P<SS>[0-9]+)\-(?P=S)$" % (S, S)),
])
def test_parser (pattern, result):
    assert parser(pattern, TOKENS).pattern == result


@mark.parametrize("config, kwargs, result", [
    ("tokens: {}\ntypes: {}", {}, ({}, {})),
    ("tokens:\n foo-bar: {}", {}, ValueError("Token name `foo-bar` is not allowed")),
    ("tokens:\n foo: {}",     {}, ValueError("In token 'foo': `None` is not a valid regex")),
    ("tokens:\n foo:\n  regex: '[a-z'",              {}, ValueError("In token 'foo': `[a-z` is not a valid regex")),
    ("tokens:\n foo:\n  regex: '[a-z]'\ntypes: {}",  {}, ({'foo': {'key': 'foo', 'regex': "[a-z]"}}, {})),
    ("tokens:\n foo: {}\ntypes: {}", {'foo': "bar-baz"}, ({'foo': {'key': 'foo', 'regex': r"bar\-baz"}}, {})),
    ("tokens: %s\ntypes: {}" % json.dumps(TOKENS), {}, (TOKENS, {})),
    ("tokens: %s\ntypes:\n foo: []" % json.dumps(TOKENS), {}, ValueError("In tasktype 'foo': Expected 2 patterns")),
    ("""
     tokens: %s
     types:
       foo:
         - ${TaskName}.in.${S}$[SL]
         - appeal/Subtask${S}-data/grader.in.${SS}-${S}""" % json.dumps(TOKENS), {},
     (TOKENS, {'foo': (re.compile(r"(?i)(?P<TaskName>[a-z]+)\.in\.(?P<S>[0-9]+)(?P<SL>[a-z]+)?$"),
                       re.compile(r"(?i)appeal%sSubtask(?P<S>[0-9]+)\-data%sgrader\.in\.(?P<SS>[0-9]+)\-(?P=S)$" % (S, S)))})),
])
def test_load_config (config, kwargs, result):
    if not isinstance(result, Exception):
        assert load_config(config, **kwargs) == result
    else:
        with raises(type(result)) as e:
            load_config(config, **kwargs)
        assert str(e.value) == str(result)  # match rejects exceptions generated in catch…


@mark.parametrize("regex, filename, previous, result", [
    (r"(?i)(?P<SS>[0-9]+)$", "42",  {}, {'test': 42}),
    (r"(?i)(?P<SS>[0-9]+)$", "a42", {}, None),
    (r"(?i)(?P<SS>[0-9]+)$", "42a", {}, None),
    (r"(?i)(?P<SS>[0-9]+)$", "a",   {}, None),
    (r"(?i)appeal/Subtask(?P<S>[0-9]+)\-data/grader\.in\.(?P<SS>[0-9]+)\-(?P=S)$", "appeal/Subtask3-data/grader.in.42-3", {}, {'group': 3, 'test': 42}),
    (r"(?i)appeal/Subtask(?P<S>[0-9]+)\-data/grader\.in\.(?P<SS>[0-9]+)\-(?P=S)$", "appeal/Subtask3-data/grader.in.42-9", {}, None),
    (r"(?i)(?P<TaskName>[a-z]+)\.in\.(?P<S>[0-9+])(?P<SL>[a-z]+)?$", "kruhologija.in.2aj", {}, {'name': "kruhologija", 'group': 2, 'test': 36}),
    (r"(?i)(?P<TaskName>[a-z]+)\.in\.(?P<S>[0-9+])(?P<SL>[a-z]+)?$", "kruhologija.in.2aj", {'name': "foo"},
     ValueError("File \"kruhologija.in.2aj\" is detected with a changed value of 'name'.")),
    (r"(?i)(?P<SS>[0-9]+)-(?P<SL>[a-z]+)$", "3-c", {}, {'test': 3}),
    (r"(?i)(?P<SS>[0-9]+)-(?P<SL>[a-z]+)$", "3-d", {}, None),
])
def test_match_file (regex, filename, previous, result):
    pattern = re.compile(regex)
    if not isinstance(result, Exception):
        assert match_file(filename, pattern, TOKENS, previous) == result
    else:
        with raises(type(result), match=str(result)):
            match_file(filename, pattern, TOKENS, previous)

@mark.parametrize("tasks, patterns, kwargs, result", [
    ({".": ["foo.in.1", "foo.out.2a", "foo.in.2b", "problem.xml", "foo.out.2b", "foo.in.2a", "foo.out.1", "foo.in.3aj", "foo.out.3aj"]},
     ("${TaskName}.in.${S}$[SL]", "${TaskName}.out.${S}$[SL]"), {},
     ({".": {(1, 0): "foo.in.1",   (2, 2): "foo.in.2b",  (2, 1): "foo.in.2a", (3, 36): "foo.in.3aj"}},
      {".": {(2, 1): "foo.out.2a", (2, 2): "foo.out.2b", (1, 0): "foo.out.1", (3, 36): "foo.out.3aj"}})),
    ({".": ["1-1.txt"]}, ("${S}-${SS}.txt", "${S}-${SS}.txt"), {}, ValueError("File \"1-1.txt\" is detected as both input and output file for type 'X'.")),
    ({".": ["foo.in.1"]}, ("${TaskName}.in.${S}$[SL]", "${TaskName}.out.${S}$[SL]"), {'infiles_only': True},
     ({".": {(1, 0): "foo.in.1"}}, {'.': {}})),
    ({".": ["foo.in.1"]}, ("${TaskName}.in.${S}$[SL]", "${TaskName}.out.${S}$[SL]"), {},
     ValueError("No output files detected for type 'X'.")),
    ({".": ["foo.in.1", "bar.out.1"]}, ("${TaskName}.in.${S}$[SL]", "${TaskName}.out.${S}$[SL]"), {},
     ValueError("File \"bar.out.1\" is detected with a changed value of 'name'.")),
    ({".": ["foo.in.1", "foo.out.2"]}, ("${TaskName}.in.${S}$[SL]", "${TaskName}.out.${S}$[SL]"), {},
     ValueError("Input and output file sets do not match for type 'X'.")),
    ({".": ["foo.in.1", "foo.in.2", "foo.out.2"]}, ("${TaskName}.in.${S}$[SL]", "${TaskName}.out.${S}$[SL]"), {},
     ValueError("Input and output file sets do not match for type 'X'.")),
    ({"foo/": ["foo.in.1"], "bar/": ["bar.in.1a"]}, ("${TaskName}.in.${S}$[SL]", "${TaskName}.out.${S}$[SL]"), {'infiles_only': True},
     ({"foo/": {(1, 0): "foo.in.1"}, "bar/": {(1, 1): "bar.in.1a"}}, {"foo/": {}, "bar/": {}})),
])
def test_match_files (tasks, patterns, kwargs, result):
    patterns = [parser(s, TOKENS) for s in patterns]
    if not isinstance(result, Exception):
        assert match_files(tasks, TOKENS, "X", *patterns, **kwargs) == result
    else:
        with raises(type(result), match=str(result)):
            match_files(tasks, TOKENS, "X", *patterns, **kwargs)

@mark.parametrize("matches, order", [
    ({'foo': ({'.': {(1, 1): "foo1a", (2, 1): "foo1b"}}, {'.': {}}), 'bar': ({'.': {(1, 0): "dummy.in.1"}}, {'.': {}})}, ['foo', 'bar']),
    ({'foo': ({'.': {(1, 0): "dummy.in.1"}}, {'.': {}}), 'bar': ({'.': {(1, 1): "foo1a", (2, 1): "foo1b"}}, {'.': {}})}, ['bar', 'foo']),
])
def test_sort_matches (matches, order):
    assert sort_matches(matches) == order


def _gen_type_tests (raw_config):
    for tasktype, items in raw_config['types'].items():
        for test in items[2:]:
            assert isinstance(test['test'], list) and set(map(type, test['test'])) == {str}
            assert isinstance(test['marks'], list) and set(test['marks']) <= {1, -1}
            assert len(test['test']) >= len(test['marks'])*2
            yield tasktype, test['test'], test['marks']

def read_config ():
    with open(os.path.dirname(__file__) + "/TaskConv.yaml") as fin:
        text = fin.read()
    config = load_config(text)
    type_tests = list(_gen_type_tests( yaml.load(text, yaml.Loader) ))
    return type_tests, config

try:
    type_tests, config = read_config()
except Exception:
    type_tests, config = [], ({}, {})

def test_config_init ():
    read_config()

@mark.parametrize("type, before, marks", type_tests)
def test_config_type (type, before, marks, fs):
    fs.create_dir(CWD)
    os.chdir(CWD)
    for it in before:
        fs.create_file(it)
    process(*config, directory='.', workdir='.')
    for i, x in enumerate(marks, start=1):
        assert os.path.exists("%d.in" % i), "missing input file #%d" % i
        assert os.path.exists("%d.out" % i), "missing output file #%d" % i
    assert not os.path.exists("%d.in" % (len(marks) + 1)), "too many input files produced"
    assert not os.path.exists("%d.out" % (len(marks) + 1)), "too many output files produced"
    with open("marks.txt") as fin:
        for i, it in enumerate(marks, start=1):
            assert fin.readline().strip() == str(it), "marks.txt: line #%d mismatch" % i
        assert fin.read().strip() == "", "marks.txt: excessive output"


if __name__ == '__main__':
    os.chdir(os.path.dirname(__file__) or ".")
    with open("TaskConv.yaml") as fin:
        config = yaml.load(fin, yaml.Loader)

    shutil.rmtree("tests/", ignore_errors=True)
    print("Generating test folders:")
    for tasktype, (infile, outfile, *tests) in sorted(config['types'].items()):
        for i, test in enumerate(tests, start=1):
            idx = ("" if len(tests) == 1 else "/"+str(i))
            print("   ", tasktype+idx)
            for s in test['test']:
                _s = "/".join(["tests", tasktype+idx, s])
                os.makedirs(os.path.dirname(_s), exist_ok=True)
                with open(_s, 'w') as fout:
                    print(s, file=fout)
