# Typing and touch on the KDE desktop from a phone

How a desktop session takes the Android keyboard, touch and the phone toolbar, and what is still missing.

## Text from the Android keyboard

```
Gboard / Samsung Keyboard
  └─ KeyboardHost.TypingConnection (plain text editor, no visible-password lock)
       └─ ImeComposition (composition, commit, finish)
            └─ DesktopTextInput → DesktopTextRouter → DesktopTyping (ordered steps)
                 ├─ IME:       GuestImeChannel → agent/ime-input (FIFO) → droiddeck-ime
                 │              → zwp_input_method_context_v1 commit_string / preedit_string → KWin → app text-input
                 ├─ KEYS:      KeyboardHost.type → dispatchKeyEvent → GuestKeys → evdev → compositor → KWin
                 └─ CLIPBOARD: SessionClipboard.share → guest clipboard, then Ctrl+V on KEYS
```

- A field holds the input method when KWin activated droiddeck-ime for it (`ime.json`, `AgentGuest.desktopTextField`).
  Wayland Qt, GTK and Firefox fields do; X11 programs under Xwayland and Chromium/Electron without
  `--enable-wayland-ime` do not.
- With such a field, all text goes through droiddeck-ime, including Latin, so it is never reordered
  against itself. Enter and Tab stay keys so chat boxes send and forms advance.
- Without one, US-keyboard characters are keys and the rest is pasted (Settings > paste fallback).
- Key presses, input-method text and clipboard pastes reach the guest through routes that are not ordered
  against each other. `DesktopTyping` runs steps in order and holds a step on another route until the
  previous route has settled (keys 40 ms, input method 80 ms, clipboard 300 ms).
- The composition shows inline through `preedit_string` when a field holds the input method, and in a
  preview above the keyboard otherwise.
- Steam sessions keep the old key-press connection: `KeyboardHost` only gets a typist when the session is
  a desktop session.

droiddeck-ime reads one JSON object per line from the FIFO:

| Request | Sent to KWin |
| --- | --- |
| `{"op": "commit", "text": "中文"}` | clears a shown preedit, then `commit_string(serial, text)` in chunks of at most 3000 bytes |
| `{"op": "preedit", "text": "ㄓㄨ", "cursor": 1}` | `preedit_cursor(byte offset)` then `preedit_string(serial, text, "")` |

The serial is the context's last `commit_state`. Text with no active context is dropped and logged.

## Touch

- Touchpad (desktop): tap = left click, double-tap and drag = drag, long press = right-click (setting;
  off = hold to drag as before), two-finger tap = right-click, two-finger slide = scroll (speed setting),
  pinch = Ctrl+wheel zoom in the app, three-finger tap = PC keyboard.
- Direct touch keeps the gesture settings' hold behaviour. The toolbar's right-click arms the next tap.
- The toolbar switches between direct touch and Auto, never to Touchpad, so a Steam session that shares
  the setting still gets direct touch.

## Desktop scale

`droiddeck-desktop-scale set <percent>` runs `kscreen-doctor output.<name>.scale.<factor>` on KWin's
nested display (`wayland-1`, in-process KWayland backend). KWin's `0001` patch keeps touch and pointer
mapping right at any nested scale. The virtual resolution in the Display page is a separate setting that
changes how many pixels the desktop has, not the size of its UI.

## Not done

- External monitor as the desktop with the phone as touchpad and keyboard: the smart-glasses path
  (`GlassesScreen`, a `Presentation` that takes the compositor output while the phone shows a panel) is
  the starting point. It only targets recognised glasses today; a "monitor" target and a phone panel for
  it need their own work and testing. Samsung DeX already moves the whole app to the monitor.
- Panning the desktop when the Android keyboard covers the field: KWin does not give input-method v1
  clients the cursor rectangle.
- Chinese in X11 programs is pasted, not composed in place (no XIM bridge).
