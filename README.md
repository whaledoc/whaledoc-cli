# WhaleDoc CLI

[![Build](https://github.com/whaledoc/whaledoc-cli/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/whaledoc/whaledoc-cli/actions/workflows/build.yml)
![Platforms](https://img.shields.io/badge/platforms-macOS%20%7C%20Linux%20%7C%20Windows-FBAA53)
![Native executable](https://img.shields.io/badge/native-GraalVM-FBAA53)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

The official command-line interface for [WhaleDoc](https://whaledoc.io).

Use WhaleDoc CLI to authenticate, listen for webhook events, and forward events to your local development environment.

**With the WhaleDoc CLI, you can:**

- Authenticate with your WhaleDoc account
- Listen for webhook events in real time
- Filter events by event type
- Forward webhook events to a local HTTP endpoint
- Develop and test webhook integrations without exposing your application to the internet

## Installation

WhaleDoc CLI is a single native executable for macOS (Intel and Apple Silicon), Linux (x64 and arm64), and Windows (x64). It does not require Java.

### macOS and Linux with Homebrew

```sh
brew install whaledoc/tap/whaledoc
```

### macOS and Linux with the install script

```sh
curl -fsSL https://github.com/whaledoc/whaledoc-cli/releases/latest/download/install.sh | bash
```

The script downloads the right binary for your system, verifies its checksum, installs it to `~/.whaledoc/bin`, and adds that directory to your `PATH`. Restart your terminal afterwards.

### Windows

Download **[whaledoc-setup-x64.exe](https://github.com/whaledoc/whaledoc-cli/releases/latest/download/whaledoc-setup-x64.exe)** and run it. The installer doesn't need administrator rights, adds `whaledoc` to your `PATH`, and can be uninstalled from **Settings → Apps**. Open a new terminal afterwards.

Or install from PowerShell:

```powershell
irm https://github.com/whaledoc/whaledoc-cli/releases/latest/download/install.ps1 | iex
```

Both install `whaledoc.exe` to `%LOCALAPPDATA%\Programs\whaledoc`.

### Verify the installation

```sh
whaledoc --version
```

### Install options

The install scripts read these environment variables:

| Variable | Description |
|---|---|
| `WHALEDOC_VERSION` | Install a specific version, for example `1.2.0`. Defaults to the latest release. |
| `WHALEDOC_INSTALL_DIR` | Install to a different directory. |
| `WHALEDOC_NO_MODIFY_PATH` | Set to `1` to leave your shell profile unchanged (macOS and Linux). |

For example, to install a specific version on macOS or Linux:

```sh
curl -fsSL https://github.com/whaledoc/whaledoc-cli/releases/latest/download/install.sh | WHALEDOC_VERSION=1.2.0 bash
```

### Manual installation

Download the archive for your platform from the [GitHub Releases](https://github.com/whaledoc/whaledoc-cli/releases) page, extract it, and put `whaledoc` (or `whaledoc.exe`) in a directory on your `PATH`. Each release includes a `checksums.txt` file to verify the download.

## Getting Started

Authenticate the CLI with your WhaleDoc account:

```sh
whaledoc login
```

Once authenticated, you can start listening for webhook events:

```sh
whaledoc listen
```

The CLI remains attached to your terminal and receives events in real time until you stop it with `Ctrl+C`.

Example:

```text
Listening for webhook events. (^C to quit)

2026-10-05 12:30:41  --> template.published
2026-10-05 12:30:45  --> job.completed
```

## Commands

### `login`

Authenticate the CLI with your WhaleDoc account.

```sh
whaledoc login
```

The CLI opens your browser so you can authorize access to your WhaleDoc account. Check that the browser shows the
same code as your terminal, then choose the workspace the CLI starts in. To work in another one later, see
[`workspace`](#workspace).

Your sessions in WhaleDoc (Settings → Security) list the login under this computer's name. To use another name, for
example on a shared or CI machine:

```sh
whaledoc login --device-name ci-runner
```

The login belongs to your account, not to one workspace. While it works, `whaledoc login` won't start another one: run
`whaledoc logout` first to log in with another account.

### `status`

Show the account you're logged in as, the workspace the CLI works in, and this computer's name.

```sh
whaledoc status
```

```text
Logged in as mara@acme.com (Mara Jansen)
Workspace  Acme HR  acme-hr
Device     dev-laptop
```

### `logout`

Log out of your WhaleDoc account.

```sh
whaledoc logout
```

Logging out removes the locally stored authentication credentials.

### `listen`

Listen for WhaleDoc webhook events in real time.

```sh
whaledoc listen
```

By default, the CLI listens for all supported webhook events.

### Filtering events

Listen for a specific event:

```sh
whaledoc listen --events job.completed
```

Listen for multiple events:

```sh
whaledoc listen --events job.completed,job.failed
```

The supported events are listed under [Webhook Events](#webhook-events).

### Forwarding events

Forward webhook events to a local HTTP endpoint:

```sh
whaledoc listen --forward-to http://localhost:8080/events
```

The CLI continues listening for events and forwards each matching event to the specified endpoint. These are your
workspace's real events, and test events you send with `whaledoc trigger`. Your workspace's webhook endpoints still
receive the real events as usual.

You can combine event filtering and forwarding:

```sh
whaledoc listen \
  --events job.completed,job.failed \
  --forward-to http://localhost:8080/events
```

This makes it possible to develop and test webhook integrations locally without exposing your application to the internet.

### `trigger`

Send a test webhook event with sample data to your own `whaledoc listen`. Start `listen` first, in another
terminal, then trigger events:

```sh
whaledoc listen --forward-to localhost:8080/events
whaledoc trigger job.completed
```

The event reaches `listen` like any other event, marked as a test, so you can try your integration without running a
real job. Test events are only for local development: they never go to your workspace's webhook endpoints or to
anyone else's `listen`. Any event from [Webhook Events](#webhook-events) works.

### `workspace`

Your login can use every workspace where you may sign in the CLI. `listen` and `trigger` work in the current one;
you choose the first when you log in.

```sh
whaledoc workspace list
```

```text
┌───┬───────────┬───────────┐
│   │ NAME      │ SLUG      │
├───┼───────────┼───────────┤
│ * │ Acme HR   │ acme-hr   │
├───┼───────────┼───────────┤
│   │ Northwind │ northwind │
└───┴───────────┴───────────┘
```

`*` marks the current workspace. Piped into another command or a file, the list prints one tab-separated line per
workspace instead, without the frame or the header, so `whaledoc workspace list | cut -f3` gives the slugs. Switch by slug, or by name when no other workspace has it:

```sh
whaledoc workspace switch northwind
whaledoc workspace current
```

### `update`

Update the CLI to the latest version.

```sh
whaledoc update
```

The CLI downloads the release for your platform, verifies its checksum, and replaces itself. Use `--check` to only see whether a newer version is available.

## Webhook Events

WhaleDoc sends these webhook events. The CLI works with any event WhaleDoc sends, also ones added after your CLI version: `whaledoc trigger` and `whaledoc listen --events` take any event name.

| Event | Description |
|---|---|
| `job.completed` | A job finished and made at least one document |
| `job.failed` | A job finished without making any documents |
| `job.cancelled` | A job was cancelled |
| `usage.document_limit_reached` | The workspace used all the documents its plan includes |
| `usage.document_spending_cap_reached` | Extra documents stopped at the workspace's spending cap |
| `template.published` | A template version was published |
| `template.deleted` | A template was deleted |

## Help

Get help for the CLI:

```sh
whaledoc --help
```

Get help for a specific command:

```sh
whaledoc listen --help
```

You can also get the version of the installed CLI:

```sh
whaledoc --version
```

## Upgrading

Update to the latest version:

```sh
whaledoc update
```

To only check whether a newer version is available:

```sh
whaledoc update --check
```

Running the install command or the Windows installer again also installs the latest version.

If the CLI is installed in a system directory such as `/usr/local/bin`, run `sudo whaledoc update`.

If you installed the CLI with Homebrew, update it with Homebrew instead:

```sh
brew upgrade whaledoc
```

## Uninstalling

Uninstalling intentionally keeps your login and settings, so reinstalling or upgrading doesn't log you out. Steps 1 and 3 are only needed if you want to remove everything.

### 1. Log out (optional)

Run this while the CLI is still installed, so your login is also ended on WhaleDoc's side:

```sh
whaledoc logout
```

### 2. Remove the CLI

**Windows, installed with the installer:** uninstall **WhaleDoc CLI** from **Settings → Apps → Installed apps**. This also removes it from your `PATH`.

**Windows, installed with the PowerShell script:**

```powershell
Remove-Item -Recurse "$env:LOCALAPPDATA\Programs\whaledoc"
$path = ([Environment]::GetEnvironmentVariable('Path', 'User') -split ';' | Where-Object { $_ -notlike '*\Programs\whaledoc' }) -join ';'
[Environment]::SetEnvironmentVariable('Path', $path, 'User')
```

**macOS and Linux, installed with Homebrew:**

```sh
brew uninstall whaledoc
brew untap whaledoc/tap
```

**macOS and Linux, installed with the install script:**

```sh
rm -rf ~/.whaledoc
```

Then remove the `# WhaleDoc CLI` line the installer added to your shell profile (`~/.zshrc`, `~/.bashrc`, `~/.config/fish/config.fish` or `~/.profile`).

### 3. Delete settings and logs (optional)

**Windows:**

```powershell
Remove-Item -Recurse -Force "$env:APPDATA\WhaleDoc", "$env:LOCALAPPDATA\WhaleDoc", "$HOME\.whaledoc" -ErrorAction SilentlyContinue
```

**macOS:**

```sh
rm -rf ~/.config/whaledoc ~/Library/Logs/WhaleDoc
```

**Linux:**

```sh
rm -rf "${XDG_CONFIG_HOME:-$HOME/.config}/whaledoc" "${XDG_STATE_HOME:-$HOME/.local/state}/whaledoc"
```

The CLI keeps its files in each operating system's standard locations:

| Platform | Settings and login | Logs |
|---|---|---|
| Windows | `%APPDATA%\WhaleDoc` | `%LOCALAPPDATA%\WhaleDoc\logs` |
| macOS | `~/.config/whaledoc` | `~/Library/Logs/WhaleDoc` |
| Linux | `$XDG_CONFIG_HOME/whaledoc` (default `~/.config/whaledoc`) | `$XDG_STATE_HOME/whaledoc/logs` (default `~/.local/state/whaledoc/logs`) |

## Documentation

Visit the [WhaleDoc documentation](https://docs.whaledoc.io) for detailed information about using WhaleDoc and the CLI.

For CLI-specific help, you can also run:

```sh
whaledoc --help
```

or:

```sh
whaledoc <command> --help
```

## Feedback

Found a bug or have an idea for improving the WhaleDoc CLI?

Open an issue on [GitHub Issues](https://github.com/whaledoc/whaledoc-cli/issues).

When reporting a bug, include:

- The WhaleDoc CLI version
- Your operating system
- The command you were running
- The error message or unexpected behavior
- Steps to reproduce the issue, if possible

## Development

### Requirements

- Java 25+
- [GraalVM](https://www.graalvm.org/) for JDK 25, only to build the native executable
- Maven is not required: use the included Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows)

Clone the repository:

```sh
git clone https://github.com/whaledoc/whaledoc-cli.git
cd whaledoc-cli
```

Build the project:

```sh
./mvnw clean package
```

Run the CLI:

```sh
java -jar target/whaledoc.jar
```

### Running tests

Run the complete test suite:

```sh
./mvnw test
```

### Local API

When developing against a local WhaleDoc API instance, build the CLI with the local API URL:

```sh
./mvnw clean package -Dwhaledoc.api.url=http://localhost:8080
```

### Native executable

Build the native executable with GraalVM (on Windows this also requires the Visual Studio C++ build tools):

```sh
./mvnw -Pnative package
./target/whaledoc --version
```

Native images can't discover reflection at runtime. When you add a class that Jackson reads or writes, register it in `src/main/resources/META-INF/native-image/io.whaledoc/cli/reachability-metadata.json`, otherwise it fails only in the native executable.

### Releasing

Every pull request builds and smoke-tests native executables for all platforms.

To publish a release, open **Actions → Release → Run workflow** on `main` and choose which part of the version to increase, following [Semantic Versioning](https://semver.org):

| Bump | Example | Use for |
|---|---|---|
| `major` | `1.4.2` → `2.0.0` | Breaking changes: removed or renamed commands or flags, changed output or config format |
| `minor` | `1.4.2` → `1.5.0` | New commands, flags or events that don't break anything |
| `patch` | `1.4.2` → `1.4.3` | Bug fixes |

The first release is created with `major` (`0.0.0` → `1.0.0`). To publish a pre-release such as `1.5.0-beta.1`, also fill in the pre-release label (`beta.1`). Pre-releases are not installed by default; testers can install one with `WHALEDOC_VERSION=1.5.0-beta.1`.

The workflow calculates the next version from the latest release tag, builds and tests every platform, and only then creates the tag and a GitHub release with the executables, a `checksums.txt` file, the install scripts and generated release notes.

You can also release a specific version by pushing its tag:

```sh
git tag v1.2.3
git push origin v1.2.3
```

The version in `pom.xml` stays at `0.0.0-SNAPSHOT`; releases take their version from the tag.

## Contributing

Contributions and feedback are welcome.

If you want to contribute code, documentation, or improvements to the CLI, please open an issue first to discuss the change or submit a pull request on GitHub. By submitting a contribution, you agree that it is licensed under the Apache License 2.0, as described in section 5 of the license.

## License

WhaleDoc CLI is licensed under the [Apache License 2.0](LICENSE). Copyright 2026 WhaleDoc.

The license covers the code, not the WhaleDoc name or logo; see [NOTICE](NOTICE).
