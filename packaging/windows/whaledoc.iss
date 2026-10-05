; Windows installer for the WhaleDoc CLI, built with Inno Setup (https://jrsoftware.org/isinfo.php).
;
; Installs per user (no administrator rights needed) into %LOCALAPPDATA%\Programs\whaledoc,
; the same directory install.ps1 uses, and adds it to the user's PATH. Uninstalling from
; Settings > Apps removes the executable and the PATH entry.
;
; Build from the repository root after the native build:
;   iscc /DAppVersion=1.2.0 /DFileVersion=1.2.0 packaging\windows\whaledoc.iss

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif

#ifndef FileVersion
  #define FileVersion "0.0.0"
#endif

[Setup]
; Never change AppId: Windows uses it to recognise upgrades of the same app
AppId={{B79A02DD-1B74-475B-A8AC-0D1432E49F3B}
AppName=WhaleDoc CLI
AppVersion={#AppVersion}
AppVerName=WhaleDoc CLI {#AppVersion}
AppPublisher=WhaleDoc
AppPublisherURL=https://whaledoc.io
AppSupportURL=https://github.com/whaledoc/whaledoc-cli/issues
VersionInfoVersion={#FileVersion}
DefaultDirName={autopf}\whaledoc
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DisableDirPage=yes
DisableProgramGroupPage=yes
DisableReadyPage=yes
ChangesEnvironment=yes
UninstallDisplayName=WhaleDoc CLI
UninstallDisplayIcon={app}\whaledoc.exe
OutputDir=..\..\dist
OutputBaseFilename=whaledoc-setup-x64
Compression=lzma2
SolidCompression=yes
WizardStyle=modern

[Files]
Source: "..\..\target\whaledoc.exe"; DestDir: "{app}"; Flags: ignoreversion

[Registry]
Root: HKCU; Subkey: "Environment"; ValueType: expandsz; ValueName: "Path"; ValueData: "{olddata};{app}"; Check: NeedsAddPath(ExpandConstant('{app}'))

[Messages]
FinishedLabel=WhaleDoc CLI has been installed.%n%nOpen a new terminal and run "whaledoc --help" to get started.

[Code]
function NeedsAddPath(Dir: string): Boolean;
var
  Path: string;
begin

  if not RegQueryStringValue(HKCU, 'Environment', 'Path', Path) then
  begin
    Result := True;
    exit;
  end;

  Result := Pos(';' + Uppercase(Dir) + ';', ';' + Uppercase(Path) + ';') = 0;
end;

procedure RemovePath(Dir: string);
var
  Path: string;
  Position: Integer;
begin

  if not RegQueryStringValue(HKCU, 'Environment', 'Path', Path) then
    exit;

  Path := ';' + Path + ';';
  Position := Pos(';' + Uppercase(Dir) + ';', Uppercase(Path));

  if Position = 0 then
    exit;

  Delete(Path, Position, Length(Dir) + 1);
  Path := Copy(Path, 2, Length(Path) - 2);
  RegWriteExpandStringValue(HKCU, 'Environment', 'Path', Path);
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin

  if CurUninstallStep = usPostUninstall then
    RemovePath(ExpandConstant('{app}'));
end;
