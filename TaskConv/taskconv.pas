{$X+}
{$Mode ObjFPC}
Uses
  Classes, GetOpts, SysUtils;
{
        Classes: TFileStream (for copying)
        GetOpts: TOption, GetLongOpts (for ParamStr parsing)
        SysUtils: system-oriented procedures
}

CONST
  // help (-h)
  HelpLines = 13;
  Help          : Array [1..HelpLines] Of AnsiString = (
    'USAGE:',
    '   taskconv -h|--help',
    '   taskconv [-v|--verbose] [-q|--quiet] [-m|--move] [-t TaskType|--type=TaskType] [-o OutFile|-output=OutFile]',
    '            [-i|--infilesonly] [-n TaskName|--name=TaskName] -d TaskDir|--directory=TaskDir|TaskDir',
    ' --help shows this help',
    ' --verbose sets verbose mode, --quiet sets quiet mode',
    ' --move sets move mode (instead of copying)',
    ' --infilesonly sets infiles-only mode (for tasks without outfiles)',
    ' all other options are used to set values:',
    'TaskType should refer to a known type of task',
    'OutFile should be a valid filename',
    'TaskName should be a word used as taskname',
    'TaskDir must be an existing directory'
  );
  // -v: type detecting lines
  Assuming      = ' types detected, assuming ';
  Detected      = 'Detected task type ';
  // -v: arrows for moving/copying files
  CopyArrow     = '->';
  MoveArrow     = '=>';

CONST
  // RageExit lines
  TooMany       = 'You cannot specify an option twice!';
  NoDir         = 'No TaskDir specified!';
  BadDir        = 'No such directory exists!';
  BadType       = 'Task type not recognized!';
  OutFileExists = 'I refuse to rewrite existing OutFile!';
  NewNameExists = 'I refuse to rewrite existing file!';
  DetectFailed  = 'Failed to detect task type!';
  NoMatch       = 'Infiles and outfiles do not match!';

CONST
  // possible options
  OptionNumber = 10;
  Options       : Array [1..OptionNumber] Of TOption = (
   // help
    (Name       : 'help';
     Has_Arg    : No_Argument;
     Flag       : NIL;
     Value      : 'h'),
   // suppress messages output
    (Name       : 'quiet';
     Has_Arg    : No_Argument;
     Flag       : NIL;
     Value      : 'q'),
   // print out what''s done
    (Name       : 'verbose';
     Has_Arg    : No_Argument;
     Flag       : NIL;
     Value      : 'v'),
   // move instead of copying
    (Name       : 'move';
     Has_Arg    : No_Argument;
     Flag       : NIL;
     Value      : 'm'),
   // allow for zero output files
    (Name       : 'infilesonly';
     Has_Arg    : No_Argument;
     Flag       : NIL;
     Value      : 'i'),
   // Task type (IOI, CEOI etc)
    (Name       : 'type';
     Has_Arg    : Required_Argument;
     Flag       : NIL;
     Value      : 't'),
   // Output filename (result)
    (Name       : 'output';
     Has_Arg    : Required_Argument;
     Flag       : NIL;
     Value      : 'o'),
   // Task directory
    (Name       : 'directory';
     Has_Arg    : Required_Argument;
     Flag       : NIL;
     Value      : 'd'),
   // Task name (used for sake of convenience)
    (Name       : 'name';
     Has_Arg    : Required_Argument;
     Flag       : NIL;
     Value      : 'n'),
   // zero length option - required by GetLongOpts
    (Name       : '';
     Has_Arg    : No_Argument;
     Flag       : NIL)
  );

TYPE
  // setting value
  TValue = Record
    Value       : AnsiString;
    Specified   : Boolean;
  End;
  // information of tried type, for sake of choosing the best fitting one
  TTryType = Record
    Name        : AnsiString;
    Number      : Word;
  End;
  // file search pattern, generated from infile/outfile pattern
  TFilePattern = Record
    BasePattern,
    SearchPattern,
    SearchPattern1      : AnsiString;
  End;
  // infile/outfile information
  TTestFile     = Record
    Name,
    NewName     : TFilename;
    Test,
    SubTest,
    TestNum     : Word;
    Valid       : Boolean;
  End;

CONST
  // constants used for processing
  DataDir       = './DB/';                              // types DB dir
  Cfg           = '.cfg';                               // changeable values
  TypePack      = $10;                                  // number of types checked in one go

CONST
  Letters       = 'abcdefghijklmnopqrstuvwxyz';         // letters (${SL})
  Digits        = '0123456789';                         // digits (${S}, ${SS})
  Sl            = '/';                                  // directory separator

VAR
  Marks         : Array Of ShortInt;                    // contents of OutFile
  ABC           : AnsiString;                           // chars (${TaskName})
  Arr           : AnsiString;                           // -v: arrow for moving/copying files
  // settings
  TaskName      : TValue = (Value: '*';         Specified: False);
  OutFile       : TValue = (Value: 'marks.tmp'; Specified: False);
  TaskType      : TValue = (Value: '';          Specified: False);
  TaskDir       : TValue = (Value: '.';         Specified: False);
  Verbose       : Boolean = False;
  Quiet         : Boolean = False;
  Move          : Boolean = False;
  InFilesOnly   : Boolean = False;

  // error exit
  Procedure RageExit(S  : AnsiString);
  Begin
    If (Not Quiet)
      Then WriteLn(S);
    Halt(1);
  End;

  // init: parsing arguments, preprocessing settings
  Procedure INIT();

    // generate list of short options (both for processing and for search)
    Function ShortOpts(Options : Array Of TOption; Var List : AnsiString) : AnsiString;
    Var
      S     : AnsiString;
      i, j  : Byte;

    Begin
      S:='-';                                   // starting from '-' forbids sorting arguments
      List:='';
      For i:=0 To High(Options)
        Do With (Options[i]) Do Begin
          S:=S+Value;
          List:=List+Value;
          For j:=1 To Has_Arg                   // value of Has_Arg is given by number of ':'s
            Do S:=S+':';
        End;
      ShortOpts:=S;
    End;

    // help exit
    Procedure WriteHelp;
    Var
      i   : Byte;

    Begin
      For i:=1 To HelpLines
        Do WriteLn(Help[i]);
      Halt;
    End;

    // change setting
    Procedure SetVal(Var Val: TValue; OptNum  : LongInt;  OptArg  : AnsiString);
    Begin
      With (Val) Do Begin
        If (Specified)
          Then RageExit(TooMany+ ' { --'+Options[OptNum].Name+'="'+OptArg+'" }' );
        Specified:=True;
        Value:=OptArg;
      End;
    End;

    // process arguments
    Procedure ProcessArgs();
    Var
      S, OptList  : AnsiString;
      Opt         : Char;
      OptNum      : LongInt;

    Begin
      OptErr:=True;
      S:=ShortOpts(Options, OptList);
      Repeat
        Opt := GetLongOpts(S, @Options[1], OptNum);
        If (Opt = '?') Or (Opt = ':')           // unknown option
          Then WriteHelp;
        If (Opt = #0)                           // -d is default
          Then Opt:='d';
        OptNum := Pos(Opt, OptList);            // correct OptNum
        // processing
        Case (Opt) Of
          'h'     : WriteHelp;
          'd'     : SetVal(TaskDir,  OptNum, OptArg);
          'o'     : SetVal(OutFile,  OptNum, OptArg);
          't'     : SetVal(TaskType, OptNum, OptArg);
          'n'     : SetVal(TaskName, OptNum, OptArg);
          'q'     : Begin
            Quiet:=True;
            Verbose:=False;
          End;
          'v'     : Begin
            Verbose:=True;
            Quiet:=False;
          End;
          'm'     : Move:=True;
          'i'     : InFilesOnly:=True;
        End;
      Until (Opt = EndOfOptions);
    End;

    // preprocessing settings
    Procedure PrepareVals;
    Var
      Info      : TSearchRec;

    Begin
      With (TaskDir) Do Begin
        If (Not Specified) Or (Length(Value) = 0)
          Then RageExit(NoDir);                 // -d MUST be set
        If (Not DirectoryExists(Value))
          Then RageExit(BadDir);                // incorrect value
        If (Value[ Length(Value) ] <> Sl)
          Then Value:=Value+Sl;                 // adding '/' (no more than one allowed)
        If (Not Quiet)
          Then WriteLn('Processing directory "'+TaskDir.Value+'".');
      End;

      With (OutFile) Do Begin
        Value:=TaskDir.Value+Value;
        If (DirectoryExists(Value))
          Or (FileExists(Value))                // rewriting existing outfile is forbidden
            Then RageExit(OutFileExists+' ('+Value+')');
      End;

      With (TaskType) Do Begin
        Value := Value+'*';
        If (FindFirst(DataDir+Value, faAnyFile, Info) <> 0)
          Then RageExit(BadType);               // incorrect value
        FindClose(Info);
      End;

      With (TaskName) Do Begin
        // nothing to do
      End;

      If (Move)                                 // setting arr
        Then Arr:=MoveArrow
        Else Arr:=CopyArrow;
    End;

  Begin
    // reading and processing config file
    Assign(Input, Cfg);
    Reset(Input);
    ReadLn(ABC);
    Close(Input);
    ABC:=AnsiLowerCase(ABC);

    ProcessArgs();
    PrepareVals();
  End;


  // body: detecting type, listing files, detecting values, moving/renaming, calculating OutFile contents
  Procedure Body();
  Var
    Info        : TSearchRec;           // going through task types
    Ambiguous   : Boolean = False;      // is task type ambiguous
    Types       : Array Of TTryType;    // types array
    TypesNum    : Word = TypePack;      // types array block size
    N, Max      : Word;                 // detected types number, index of fittest

    // generate search pattern(s) from base pattern
    Procedure DoPat(Var Pattern  : TFilePattern);

      // replace all occurrences of variable (Fro) with wildcard (To_)
      Procedure Replace(Var S  : AnsiString; Fro, To_ : AnsiString);
      Var
        P       : Word;

      Begin
        P:=Pos(Fro, S);
        While (P > 0) Do Begin
          S:=Copy(S, 1, P-1) + To_ + Copy(S, P+Length(Fro), Length(S));
          P:=Pos(Fro, S);
        End;
      End;

    Begin
      With (Pattern) Do Begin
        // normal pattern
        SearchPattern:=BasePattern;
        Replace(SearchPattern, '${TaskName}', TaskName.Value);
        Replace(SearchPattern,        '${S}',            '*');
        Replace(SearchPattern,       '${SS}',            '*');
        Replace(SearchPattern,       '${SL}',            '?');
        SearchPattern1:='';
        If (Pos('$[SS]', SearchPattern)+Pos('$[SL]', SearchPattern) = 0)
          Then Exit;
        // uncertain existance, doing 2 patterns instead of one
        SearchPattern1:=SearchPattern;
        Replace(SearchPattern,       '$[SS]',            '*');
        Replace(SearchPattern,       '$[SL]',            '?');
        Replace(SearchPattern1,      '$[SS]',             '');
        Replace(SearchPattern1,      '$[SL]',             '');
      End;
    End;

    // checking type for existance of files
    Procedure TryType(Name  : TFilename);
    Var
      InCount, OutCount,
      OutRequired        : Word;
      InPat, OutPat      : TFilePattern;

      // counting files matching search pattern
      Function CountPat(Pattern  : TFilePattern)  : Word;
      Var
        Count  : Word = 0;

        // recursive processing
        Function CountRec(Found, Pat  : AnsiString) : Word;
        Var
          Info   : TSearchRec;
          Files  : Word;

        Begin
          Files:=0;
          If (Pos(Sl, Pat) = 0) Then Begin
            // counting matching files in current dir
            If (FindFirst(Found+Pat, faAnyFile, Info) = 0)
              Then Repeat
                If (DirectoryExists(Found+Info.Name))
                  Then Continue;
                Inc(Files);
              Until (FindNext(Info) <> 0);
          End Else Begin
            // going inside directories
            If (FindFirst(Found+Copy(Pat,1,Pos(Sl,Pat)-1), faAnyFile, Info) = 0)
              Then Repeat
                If (Info.Name = '.') Or (Info.Name = '..')
                  Then Continue;
                If (Not DirectoryExists(Found+Info.Name))
                  Then Continue;
                Inc(Files, CountRec( Found+Info.Name+Sl, Copy(Pat, Pos(Sl, Pat)+1, Length(Pat)) ));
              Until (FindNext(Info) <> 0);
          End;
          FindClose(Info);
          CountRec:=Files;
        End;

      Begin
        With (Pattern) Do Begin
          Count:=CountRec(TaskDir.Value, SearchPattern);
          If (SearchPattern1 <> '')             // counting also 2nd pattern
            Then Inc(Count, CountRec(TaskDir.Value, SearchPattern1));
          CountPat:=Count;
        End;
      End;

    Begin
      // reading patterns
      Assign(Input, Name);
      Reset(Input);
      ReadLn(InPat.BasePattern);
      ReadLn(OutPat.BasePattern);
      Close(Input);

      // genering search patterns
      DoPat(InPat);
      DoPat(OutPat);

      InCount:=0;
      OutCount:=0;

      // counting matching files
      InCount:=CountPat(InPat);
      If (InCount = 0)
        Then Exit;
      OutCount:=CountPat(OutPat);
      OutRequired:=InCount;
      If (InFilesOnly)
        Then OutRequired:=0;
      If (OutCount <> OutRequired) Then Begin
        // number of outfiles has not matched required number
        If (Verbose)
          Then WriteLn('For task type '+Copy(Name, Length(DataDir)+1, Length(Name))+', detected ',
                        InCount, ' input files and ', OutCount, ' output files (', OutRequired, ' expected); skipping!');
        Exit;
      End;

      // adding type to the list of detected
      If (N >= TypesNum) Then Begin
        Inc(TypesNum, TypePack);
        SetLength(Types, TypesNum);
      End;
      Types[N].Name:=Name;
      Types[N].Number:=InCount;
      If (InCount > Types[Max].Number)
        Then Max:=N;                            // choosing maximal
      Inc(N);
    End;

    // selecting best detected type
    Procedure BestShot(TypeName  : TTryType);
    Var
      InFiles, OutFiles  : Array Of TTestFile;  // infiles/outfiles data
      InPat, OutPat      : TFilePattern;        // patterns for infiles/outfiles
      InCount, OutCount,                        // number of validated infiles/outfiles
      OutRequired        : Word;                // required number of outfiles

      // generate infile/outfile list
      Procedure ListFiles(Pattern  : TFilePattern;  Var Files  : Array Of TTestFile);
      Var
        S, Z,
        S1, Z1  : AnsiString;
        i       : Word;

        // recursive add
        Procedure AddRec(Found, Pat  : AnsiString;  Sub  : Word);
        Var
          Info   : TSearchRec;

        Begin
          If (Pos(Sl, Pat) = 0) Then Begin
            // adding files
            If (FindFirst(Found+Pat, faAnyFile, Info) = 0)
              Then Repeat
                If (DirectoryExists(Found+Info.Name))
                  Then Continue;
                Files[i].Name:=Found+Info.Name;
                Files[i].SubTest:=Sub;
                Files[i].Valid:=True;
                Inc(i);
              Until (FindNext(Info) <> 0);
          End Else Begin
            // going through subdirectories
            If (FindFirst(Found+Copy(Pat,1,Pos(Sl,Pat)-1), faAnyFile, Info) = 0)
              Then Repeat
                If (Info.Name = '.') Or (Info.Name = '..')
                  Then Continue;
                If (Not DirectoryExists(Found+Info.Name))
                  Then Continue;
                AddRec( Found+Info.Name+Sl, Copy(Pat, Pos(Sl, Pat)+1, Length(Pat)), Sub );
              Until (FindNext(Info) <> 0);
          End;
          FindClose(Info);
        End;

        // extract number from beginning of the string (S, SS)
        Procedure RmNBeg(Pat  : String;  Var Num  : Word;  Var Valid : Boolean);
        Begin
          Num:=0;
          Valid:=False;
          While (Length(Z) > 0)
            Do If (Pos(Z[1], Digits) > 0)
              Then Begin
                Valid:=True;
                Num:=Num*10+(Pos(Z[1], Digits)-1);
                Delete(Z, 1, 1);
              End Else Break;
          Delete(Z1, 1, Length(Pat));
        End;

        // extract number from end of the string (S, SS)
        Procedure RmNEnd(Pat  : String;  Var Num  : Word;  Var Valid : Boolean);
        Var
          D     : Word = 1;

        Begin
          Num:=0;
          Valid:=False;
          While (Length(Z) > 0)
            Do If (Pos(Z[Length(Z)], Digits) > 0)
              Then Begin
                Num:=Num+D*(Pos(Z[Length(Z)], Digits)-1);
                D:=D*10;
                Valid:=True;
                Delete(Z, Length(Z), 1);
              End Else Break;
          Delete(Z1, Length(Z1)-Length(Pat)+1, Length(Pat));
        End;

        // extract character from beginning of the string (SL)
        Procedure RmCBeg(Pat  : String;  Var Num  : Word;  Var Valid : Boolean);
        Begin
          Num:=Pos(Z[1], Letters);
          Delete(Z, 1, 1);
          Delete(Z1, 1, Length(Pat));
          Valid:=(Num > 0);
        End;

        // extract character from end of the string (SL)
        Procedure RmCEnd(Pat  : String;  Var Num  : Word;  Var Valid : Boolean);
        Begin
          Num:=Pos(Z[Length(Z)], Letters);
          Delete(Z, Length(Z), 1);
          Delete(Z1, Length(Z1)-Length(Pat)+1, Length(Pat));
          Valid:=(Num > 0);
        End;

        // extract word from beginning of the string (TaskName)
        Procedure RmSBeg(Pat  : String;  Var Valid : Boolean);
        Begin
          Valid:=False;
          While (Length(Z) > 0)
            Do If (Pos(Z[1], ABC) > 0) Then Begin
              Delete(Z, 1, 1);
              Valid:=True;
            End Else Break;
          Delete(Z1, 1, Length(Pat));
        End;

      Begin
        i:=0;
        // generate list
        AddRec(TaskDir.Value, Pattern.SearchPattern, 0);
        If (Pattern.SearchPattern1 <> '')
          Then AddRec(TaskDir.Value, Pattern.SearchPattern1, 1);

        // going through list, calculating numbers and validating
        For i:=0 To High(Files)
          Do With (Files[i]) Do Begin
            S:=Name+Sl;
            S1:=TaskDir.Value+Pattern.BasePattern+Sl;
            While (Length(S) > 0) And (Valid) Do Begin
              // splitting both paths into directories to avoid misdetection
              Z:=AnsiLowerCase( Copy(S, 1, Pos(Sl, S)-1) );
              Delete(S, 1, Pos(Sl, S));
              Z1:=AnsiLowerCase( Copy(S1, 1, Pos(Sl, S1)-1) );
              Delete(S1, 1, Pos(Sl, S1));
              While (Length(Z) > 0) And (Valid) Do Begin
                If (Length(Z1) = 0) Then Begin
                  Valid:=False;
                  Break;
                End;
                // removing regular characters
                If (Z = Z1)
                  Then Break;
                While (Z[1] = Z1[1]) Do Begin
                  Delete(Z, 1, 1);                      // from beginning
                  Delete(Z1, 1, 1);
                End;
                While (Z[Length(Z)] = Z1[Length(Z1)]) Do Begin
                  Delete(Z, Length(Z), 1);              // and from the end
                  Delete(Z1, Length(Z1), 1);
                End;

                // removing $[ss] $[sl] from the beginning
                If (Pos('$[ss]', Z1) = 1) Or (Pos('$[sl]', Z1) = 1) Then Begin
                  If (SubTest = 0)                      // checking if 0th or 1st search pattern was used
                    Then If (Z1[4] = 's')
                      Then RmNBeg('$[ss]', SubTest, Valid)
                      Else RmCBeg('$[sl]', SubTest, Valid)
                    Else Delete(Z1, 1, Length('$[sl]'));
                  Continue;
                End;

                // removing $[ss] $[sl] from the end
                If (Length('$[ss]') <= Length(Z1)) And ((Pos('$[sl]', Z1)+Length('$[ss]') = Length(Z1)+1)
                    Or (Pos('$[sl]', Z1)+Length('$[sl]') = Length(Z1)+1)) Then Begin
                  If (SubTest = 0)                      // checking if 0th or 1st search pattern was used
                    Then If (Z1[Length(Z1)-1] = 's')
                      Then RmNEnd('$[ss]', SubTest, Valid)
                      Else RmCEnd('$[sl]', SubTest, Valid)
                    Else Delete(Z1, Length(Z1)-Length('$[ss]')+1, Length('$[sl]'));
                  Continue;
                End;

                // removing ${sl} from beginning
                If (Pos('${sl}', Z1) = 1) Then Begin
                  RmCBeg('${sl}', SubTest, Valid);
                  Continue;
                End;
                // removing ${SL} from the end
                If ( Length('${sl}') <= Length(Z1) )
                    And ( Pos('${sl}', Z1) + Length('${sl}') = Length(Z1)+1 ) Then Begin
                  RmCEnd('${sl}', SubTest, Valid);
                  Continue;
                End;

                // removing ${ss} from beginning
                If (Pos('${ss}', Z1) = 1) Then Begin
                  RmNBeg('${ss}', SubTest, Valid);
                  Continue;
                End;
                // removing ${ss} from the end
                If ( Length('${ss}') <= Length(Z1) )
                    And ( Pos('${ss}', Z1) + Length('${ss}') = Length(Z1)+1 ) Then Begin
                  RmNEnd('${ss}', SubTest, Valid);
                  Continue;
                End;

                // removing ${s} from beginning
                If (Pos('${s}', Z1) = 1) Then Begin
                  RmNBeg('${s}', Test, Valid);
                  Continue;
                End;
                // removing ${s} from the end
                If ( Length('${s}') <= Length(Z1) )
                    And ( Pos('${s}', Z1) + Length('${s}') = Length(Z1)+1 ) Then Begin
                  RmNEnd('${s}', Test, Valid);
                  Continue;
                End;

                // removing ${TaskName} (will be processed the last - for sake of avoiding worst detection problems)
                If (TaskName.Specified)
                  Then Z1:=AnsiLowerCase(TaskName.Value) + Copy(Z1, Length('${taskname}')+1, Length(Z1))
                  Else RmSBeg('${taskname}', Valid);
              End;// Z~Z1 cycle
            End;// S~S1 cycle
          End;// Files[i] cycle
      End;

      // calculating both test numbers and marks, result is the number of valid tests
      Function CalcTests(Var Files  : Array Of TTestFile): Word;
      Var
        i, j, M, Cur, Sum       : Word;         // files, groups, GroupNum, current group size, total files counted
        ZeroSubTest             : Boolean;

      Begin
        // counting valid tests
        Sum:=0;
        For i:=0 To High(Files)
          Do If (Files[i].Valid)
            Then Inc(Sum);
        SetLength(Marks, Sum);
        CalcTests:=Sum;
        // maximum test group number
        M:=0;
        For i:=0 To High(Files)
          Do If (Files[i].Valid) And (Files[i].Test > M)
            Then M:=Files[i].Test;
        Sum:=0;
        // doing test groups
        For j:=0 To M Do Begin
          Cur:=0;
          ZeroSubTest:=False;
          // counting files of current group, checking for 0th subtest (shouldn''t really exist, but...)
          For i:=0 To High(Files)
            Do With (Files[i]) Do Begin
              If (Not Valid)
                Then Continue;
              If (Test <> j)
                Then Continue;
              TestNum:=Sum+SubTest;             // deciding result number by test number
              ZeroSubTest:=ZeroSubTest Or (SubTest = 0);
              Inc(Cur);
            End;// Files[i] cycle
          // doing files of current group, setting marks for it
          For i:=0 To High(Files)
            Do With (Files[i]) Do Begin
              If (Not Valid)
                Then Continue;
              If (Test <> j)
                Then Continue;
              If (ZeroSubTest)
                Then Inc(TestNum);
              Marks[TestNum-1]:=-1;
            End;// Files[i] cycle
          Inc(Sum, Cur);
          If (Cur > 0)
            Then Marks[Sum-1]:=1;
        End;// test groups cycle
      End;

      // copy/move filed (depending on mode)
      Procedure DoFiles();
      Var
        i       : Word;

        // copy/move file (depending on mode)
        Function DoFile(Name, NewName : TFileName): Boolean;
        Var
          Dest, Source  : TFileStream;
          Done          : Boolean;

        Begin
          Done:=False;
          If ( FileExists(NewName) )                    // rewriting files is forbidden
            Then RageExit(NewNameExists+' ('+NewName+')');

          // trying to rename instead of moving (in case destination on same partition)
          If ( Move ) Then Begin
            Done:=RenameFile(Name, NewName);
            DoFile:=Done;
            If (Done)
              Then Exit;
          End;

          Try
            // trying to copy
            Source:=TFileStream.Create(Name, fmOpenRead);
            Dest:=TFileStream.Create(NewName, fmCreate Or fmOpenWrite);
            Dest.CopyFrom(Source, Source.Size);
            Done:=True;
          Finally
            Dest.Destroy;
            Source.Destroy;
            // removing original if needed
            If (Done And Move)
              Then Done:=DeleteFile(Name);
          End;
          DoFile:=Done;
        End;

      Begin
        // doing infiles
        For i:=0 To High(InFiles) Do Begin
          With (InFiles[i]) Do Begin
            If (Not Valid)
              Then Continue;
            // generating new filename
            Str(TestNum, NewName);
            NewName:=TaskDir.Value+NewName+'.in';
            // copying/moving file
            If ( DoFile(Name, NewName) )
              Then If (Verbose)
                Then WriteLn('"'+Name+'" '+Arr+' "'+NewName+'"');
          End;
        End;
        If (InFilesOnly)
          Then Exit;
        // doing outfiles
        For i:=0 To High(InFiles) Do Begin
          With (OutFiles[i]) Do Begin
            If (Not Valid)
              Then Continue;
            // generating new filename
            Str(TestNum, NewName);
            NewName:=TaskDir.Value+NewName+'.out';
            // copying/moving file
            If ( DoFile(Name, NewName) )
              Then If (Verbose)
                Then WriteLn('"'+Name+'" '+Arr+' "'+NewName+'"');
          End;
        End;
      End;

    Begin
      With (TypeName) Do Begin
        // setting sizes of arrays
        SetLength(Marks, Number);
        SetLength(InFiles, Number);
        SetLength(OutFiles, Number);

        // rereading type file
        Assign(Input, Name);
        Reset(Input);
        ReadLn(InPat.BasePattern);
        ReadLn(OutPat.BasePattern);
        Close(Input);

        // recalculating patterns
        DoPat(InPat);
        DoPat(OutPat);

        // generating infile/outfile lists
        ListFiles(InPat, InFiles);
        ListFiles(OutPat, OutFiles);

        // calculating test numbers
        OutCount:=CalcTests(OutFiles);
        InCount:=CalcTests(InFiles);
        OutRequired:=InCount;
        If (InFilesOnly)
          Then OutRequired:=0;
        If (OutCount <> OutRequired) Then Begin
          // number outfiles doesn''t match required number
          If (Verbose)
            Then WriteLn('For task type '+Name+', found ', InCount, ' input files and ', OutCount, ' output files!');
          If (Verbose)
            Then WriteLn('(', OutCount, '/', OutRequired, ')');
          RageExit(NoMatch);
        End;
        // doing files
        DoFiles();
      End;
    End;

  Begin
    SetLength(Types, TypesNum);
    N:=0;
    Max:=0;
    // generating list of detected types
    FindFirst(DataDir+TaskType.Value, faAnyFile, Info);
    Repeat
      If (FileExists(DataDir+Info.Name)) And (Not DirectoryExists(DataDir+Info.Name))
        Then TryType(DataDir+Info.Name);
    Until (FindNext(Info) <> 0);
    FindClose(Info);
    // choosing most fitting type
    If (N = 0)
      Then RageExit(DetectFailed);
    Ambiguous:=(N > 1);
    If (Verbose)
      Then If (Ambiguous)
        Then WriteLn( N, Assuming+Copy(Types[Max].Name, Length(DataDir)+1, Length(Types[Max].Name)) )
        Else WriteLn( Detected+Copy(Types[Max].Name, Length(DataDir)+1, Length(Types[Max].Name)) );
    // doing chosen type
    BestShot(Types[Max]);
  End;

  // POut: writing OutFile
  Procedure POut();

    // printing marks
    Procedure PrintArray(Marks  : Array Of ShortInt);
    Var
      i  : Word;

    Begin
      For i:=0 To High(Marks)
        Do WriteLn(Marks[i]:3);
    End;

  Begin
    If (Verbose)
      Then Write('Writing to "'+OutFile.Value+'"...');
    // writing file
    Assign(Output, OutFile.Value);
    Rewrite(Output);
    PrintArray(Marks);
    Close(Output);
    // switching back to stdout
    Assign(Output, '');
    Rewrite(Output);
    If (Verbose)
      Then WriteLn(' done.');
  End;


BEGIN
  Init();
  Body();
  POut();
END.
