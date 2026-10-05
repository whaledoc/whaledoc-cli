# WhaleDoc CLI

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

### macOS and Linux

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

2026-10-05 12:30:41  --> document.created
2026-10-05 12:30:45  --> document.completed
```

## Commands

### `login`

Authenticate the CLI with your WhaleDoc account.

```sh
whaledoc login
```

The CLI opens your browser so you can authorize access to your WhaleDoc account.

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
whaledoc listen --events document.created
```

Listen for multiple events:

```sh
whaledoc listen --events document.created,document.completed
```

Supported events:

- `document.created`
- `document.completed`
- `document.failed`

### Forwarding events

Forward webhook events to a local HTTP endpoint:

```sh
whaledoc listen --forward-to http://localhost:8080/events
```

The CLI continues listening for events and forwards each matching event to the specified endpoint.

You can combine event filtering and forwarding:

```sh
whaledoc listen \
  --events document.created,document.completed \
  --forward-to http://localhost:8080/events
```

This makes it possible to develop and test webhook integrations locally without exposing your application to the internet.

### `update`

Update the CLI to the latest version.

```sh
whaledoc update
```

The CLI downloads the release for your platform, verifies its checksum, and replaces itself. Use `--check` to only see whether a newer version is available.

## Webhook Events

The WhaleDoc CLI currently supports the following webhook events:

| Event | Description |
|---|---|
| `document.created` | A document has been created |
| `document.completed` | Document generation has completed |
| `document.failed` | Document generation has failed |

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

## Uninstalling

### macOS / Linux

Delete the executable:

```sh
rm -rf ~/.whaledoc/bin
```

Then remove the `# WhaleDoc CLI` line the installer added to your shell profile (`~/.zshrc`, `~/.bashrc`, `~/.config/fish/config.fish` or `~/.profile`).

### Windows

If you used the installer, uninstall **WhaleDoc CLI** from **Settings → Apps → Installed apps**. This also removes it from your `PATH`.

If you used the PowerShell script, delete the install directory and remove it from your user `PATH`:

```powershell
Remove-Item -Recurse "$env:LOCALAPPDATA\Programs\whaledoc"
$path = ([Environment]::GetEnvironmentVariable('Path', 'User') -split ';' | Where-Object { $_ -notlike '*\Programs\whaledoc' }) -join ';'
[Environment]::SetEnvironmentVariable('Path', $path, 'User')
```

### Configuration and logs

Uninstalling does not remove your WhaleDoc configuration, credentials or logs. To remove them too, delete:

| Platform | Configuration | Logs |
|---|---|---|
| macOS | `~/.config/whaledoc` | `~/.whaledoc/logs` |
| Linux | `$XDG_CONFIG_HOME/whaledoc` or `~/.config/whaledoc` | `~/.whaledoc/logs` |
| Windows | `%APPDATA%\WhaleDoc` | `%USERPROFILE%\.whaledoc\logs` |

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

If you want to contribute code, documentation, or improvements to the CLI, please open an issue first to discuss the change or submit a pull request on GitHub.

## License

Copyright © WhaleDoc. All rights reserved.
