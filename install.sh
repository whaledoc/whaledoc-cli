#!/bin/sh
# WhaleDoc CLI installer for macOS and Linux.
#
#   curl -fsSL https://whaledoc.io/install | bash
#
# Environment variables:
#   WHALEDOC_VERSION           Version to install, e.g. 1.2.0 (default: latest)
#   WHALEDOC_INSTALL_DIR       Install directory (default: ~/.whaledoc/bin)
#   WHALEDOC_NO_MODIFY_PATH=1  Do not add the install directory to your shell profile

set -eu

REPO="whaledoc/whaledoc-cli"
VERSION="${WHALEDOC_VERSION:-latest}"
INSTALL_DIR="${WHALEDOC_INSTALL_DIR:-$HOME/.whaledoc/bin}"

info() { printf '%s\n' "$*"; }
error() { printf 'error: %s\n' "$*" >&2; exit 1; }

download() {
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --retry 3 -o "$2" "$1"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$2" "$1"
    else
        error "curl or wget is required to install WhaleDoc"
    fi
}

sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d ' ' -f 1
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | cut -d ' ' -f 1
    else
        error "sha256sum or shasum is required to verify the download"
    fi
}

detect_target() {
    case "$(uname -s)" in
        Linux) os="linux" ;;
        Darwin) os="macos" ;;
        *) error "unsupported operating system: $(uname -s). On Windows, use install.ps1" ;;
    esac

    case "$(uname -m)" in
        x86_64 | amd64) arch="x64" ;;
        aarch64 | arm64) arch="arm64" ;;
        *) error "unsupported CPU architecture: $(uname -m)" ;;
    esac

    # A shell running under Rosetta reports x86_64 on Apple Silicon; prefer the native build
    if [ "$os" = "macos" ] && [ "$arch" = "x64" ] && [ "$(sysctl -n sysctl.proc_translated 2>/dev/null || echo 0)" = "1" ]; then
        arch="arm64"
    fi

    echo "$os-$arch"
}

add_to_path() {
    case ":$PATH:" in
        *":$INSTALL_DIR:"*) return ;;
    esac

    if [ "${WHALEDOC_NO_MODIFY_PATH:-0}" = "1" ]; then
        info "Add $INSTALL_DIR to your PATH to use whaledoc."
        return
    fi

    case "$(basename "${SHELL:-sh}")" in
        zsh) profile="${ZDOTDIR:-$HOME}/.zshrc"; line="export PATH=\"$INSTALL_DIR:\$PATH\"" ;;
        bash) profile="$HOME/.bashrc"; line="export PATH=\"$INSTALL_DIR:\$PATH\"" ;;
        fish) profile="$HOME/.config/fish/config.fish"; line="fish_add_path \"$INSTALL_DIR\"" ;;
        *) profile="$HOME/.profile"; line="export PATH=\"$INSTALL_DIR:\$PATH\"" ;;
    esac

    if [ -f "$profile" ] && grep -qF "$INSTALL_DIR" "$profile"; then
        return
    fi

    mkdir -p "$(dirname "$profile")"
    printf '\n# WhaleDoc CLI\n%s\n' "$line" >> "$profile"
    info "Added $INSTALL_DIR to PATH in $profile"
    info "Restart your terminal or run: $line"
}

main() {
    target="$(detect_target)"
    archive="whaledoc-$target.tar.gz"

    if [ "$VERSION" = "latest" ]; then
        base_url="https://github.com/$REPO/releases/latest/download"
    else
        base_url="https://github.com/$REPO/releases/download/v${VERSION#v}"
    fi

    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT

    info "Downloading WhaleDoc CLI ($VERSION, $target)..."
    download "$base_url/$archive" "$tmp/$archive" || error "download failed: $base_url/$archive"
    download "$base_url/checksums.txt" "$tmp/checksums.txt" || error "download failed: $base_url/checksums.txt"

    expected="$(grep " $archive\$" "$tmp/checksums.txt" | cut -d ' ' -f 1)"
    [ -n "$expected" ] || error "no checksum found for $archive"
    [ "$(sha256 "$tmp/$archive")" = "$expected" ] || error "checksum mismatch for $archive"

    tar -xzf "$tmp/$archive" -C "$tmp"
    mkdir -p "$INSTALL_DIR"
    mv -f "$tmp/whaledoc" "$INSTALL_DIR/whaledoc"
    chmod +x "$INSTALL_DIR/whaledoc"

    info "Installed $("$INSTALL_DIR/whaledoc" --version) to $INSTALL_DIR/whaledoc"
    add_to_path
    info "Run 'whaledoc --help' to get started."
}

main
