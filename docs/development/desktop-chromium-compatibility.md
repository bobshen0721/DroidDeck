# Chromium / Electron desktop compatibility

DroidDeck's Ubuntu desktop runs in an Android application using PRoot. Android's
application sandbox still applies, but this is **not** a normal privileged Linux
installation and PRoot is **not** a security boundary. Chromium's unprivileged
user namespace and setuid sandbox paths may be unavailable.

The desktop launcher already adds `--no-sandbox --ozone-platform=x11` for some
Chromium/Electron applications when creating generated KDE `.desktop` entries.
This is a **compatibility workaround, not a Chromium sandbox fix**: the resulting
application has less internal process isolation. Do not use it to open untrusted
web content or sensitive company data. Android's own application sandbox still
restricts access to other apps, but shared Android storage and guest files that
DroidDeck can access remain exposed to a compromised Chromium process.

## Detection

`droiddeck-desktop-gpu` looks for Chromium-specific files beside the resolved
executable or in its parent directory: `chrome-sandbox`,
`chrome_100_percent.pak`, and `v8_context_snapshot.bin`. Some packages omit
`chrome-sandbox`. The official ChatGPT ARM64 `.deb` is additionally recognized
only when both the desktop `Name=ChatGPT` and command basename `chatgpt` match
and the executable exists. This is intentionally narrow; an arbitrary desktop
app whose command happens to include `chatgpt` is not matched.

The generated `.desktop` entries are rebuilt when the Linux desktop session
starts. User-maintained `.desktop` overrides are not replaced.

## Read-only diagnosis

Inside the KDE desktop open Konsole and run:

```sh
python3 /usr/local/bin/droiddeck-desktop-gpu --diagnose chatgpt
```

The command reports the kernel-visible UID, `NoNewPrivs`, `Seccomp`, `CapEff`,
matching desktop entries, detected Chromium signatures and whether the entry's
`Exec` disables Chromium's sandbox. It does not change the filesystem or run the
app. If the system desktop entry is recognized but no generated user entry
appears, quit and reopen the desktop to rebuild menu entries.

Do **not** run the plain `python3 /usr/local/bin/droiddeck-desktop-gpu` while
troubleshooting: without `--diagnose` that is the launcher's normal *mutating*
menu-rewrite operation.

Exit code 137 means a process was killed by SIGKILL. It is **not by itself**
evidence of out-of-memory; check the DroidDeck session log and Android logcat
for memory-pressure / LMKD messages before deciding the cause.

## Limits

This change does **not** restore Chromium's own sandbox, make Linux user
namespaces available, fix Linux desktop GPU composition, or validate Codex's
separate command-execution sandbox. Those need Android/PRoot-level changes and
on-device testing. Do not conflate launching the ChatGPT graphical window with
successfully executing a Codex agent.
