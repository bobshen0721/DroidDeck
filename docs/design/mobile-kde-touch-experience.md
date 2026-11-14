# Driving the KDE desktop from a phone

Builds on `mobile-linux-desktop-quick-controls.md`.

## Android keyboard and Chinese input

KWin runs nested in the app's compositor, so Linux programs talk to KWin, not to
the app. Key presses alone cannot carry text that has no key on the guest's US
layout (Chinese, punctuation such as `，`, emoji, accents).

- `KeyboardHost` now opens an ordinary text editor (`TYPE_CLASS_TEXT`). The old
  `VISIBLE_PASSWORD` variation forced Gboard and Samsung Keyboard onto Latin
  layouts, which hid Zhuyin, Pinyin and Cangjie.
- The input connection tracks the input method's composition. Composing text is
  shown in a strip above the keyboard and sent to the focused field as preedit;
  backspace while composing edits the composition only.
- Committed lines of printable ASCII still go out as evdev key presses, which
  every program understands, including X11 programs and terminals without an
  input method. A line holding any other character is committed whole through
  `droiddeck-ime`, so its characters are never reordered between the two paths.
- `droiddeck-ime` (KWin's `zwp_input_method_v1` client, which already reported
  text-field focus) creates a FIFO, `agent/ime-input`, and commits what the app
  writes there with `zwp_input_method_context_v1.commit_string` /
  `preedit_string`, using the serial from `commit_state`. Requests are split
  between characters to stay under libwayland's 4096-byte message limit.
- When no field has text-input focus, non-ASCII text cannot be delivered. The
  app says so once instead of dropping it silently. X11-only programs need an
  input method of their own inside Linux (for example fcitx5).

## PC keys over the Android keyboard

While the Android keyboard is up in a desktop session, a row above it offers
Esc, Tab, Ctrl, Alt, Shift (one-shot latches), Super, arrows, Home/End,
PgUp/PgDn, Del, Paste and Hide. Latched modifiers wrap the next key or typed
text and are released with it; hiding the keyboard, losing window focus or
pausing releases everything. Arrow keys stay pressed while held, so the guest's
own key repeat applies. Paste commits the Android clipboard through the input
method, or presses Ctrl+V when no text field is focused (the session clipboard
already mirrors Android's).

## Desktop toolbar

On phones the floating keyboard button became a draggable toolbar: keyboard and
a handle when collapsed; PC keys, trackpad/direct switch, right-click, KDE scale,
external display and the session menu when expanded. Targets are 48 dp, the bar
keeps out of display cutouts, and its position is stored as a fraction of the
free area so it survives rotation. It is hidden in Steam sessions, behind
overlays and while the Android keyboard is up.

Right-click clicks at the cursor in trackpad mode. In direct touch it arms a
one-shot: the next tap is a right click at that spot.

## Scaling: three different things

- **KDE interface scale** (100–200 %): `droiddeck-ime` runs `kscreen-doctor
  output.<name>.scale.<n>` on KWin's socket. Plasma redraws text, panels and
  window decorations larger at full resolution; window positions are kept.
  Requests are debounced in the app and in the guest.
- **Virtual resolution** (from the earlier change): renders fewer pixels and
  stretches them. Larger but softer, and it resizes KWin's output.
- **Pinch on the trackpad** sends Ctrl+wheel, which zooms inside programs that
  support it. It is not a viewport magnifier.

## Trackpad

Desktop sessions get their own pointer speed, scroll speed and long-press
action. Long press now right-clicks by default on the desktop (double-tap and
drag still drags; the old hold-to-drag is one setting away). Game sessions keep
their per-layout trackpad settings.

## External displays

Unchanged and still not a full desktop move. The second-screen modes show
controls or the bottom-desktop panel on a presentation display. The compositor
output can move to a Presentation today only through the smart-glasses path
(`GlassesScreen`), for glasses DroidDeck recognises; a plain USB-C monitor does
not take that path. Extending it to any monitor needs its own output state
rather than the glasses flags, and testing with real hotplug.

## Known limits

- The keyboard can still cover a text field near the bottom of the screen:
  `zwp_input_method_v1` gives no cursor rectangle, and moving the surface would
  shift every touch mapping.
- Nothing here has been checked on a device yet.
