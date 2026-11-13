# Touch-first Linux desktop quick controls

The Android session drawer's Controller page now starts with a **Mobile desktop
controls** panel when the guest runs in Linux desktop mode.

- The Android keyboard and on-screen PC keyboard are reachable with one tap.
  On phone-sized devices, the existing floating keyboard entry is visible
  throughout an active desktop session, without waiting for focus detection.
  Drawer/modal/IME/suspended states hide it to avoid covering controls.
  Three-finger trackpad tap now opens the Android keyboard in desktop mode;
  Steam keeps its original PC keyboard shortcut.
- Three guest-resolution steps (100%, 150%, 200%) use existing
  `SessionDisplay.FOLLOW_SCREEN` / `followScaled` and live
  `nativeResizeOutput` paths. These enlarge the Linux window UI by reducing
  the virtual guest resolution. Windows may rearrange. This is **not** an Android
  surface pinch-to-zoom or compositor viewport crop.
- Quick touch selection exposes trackpad, direct touch and custom gestures.
  Existing two-finger scroll/right-click and pinch-to-app-zoom remain unchanged.
  The full gesture editor is one tap away.
- If a presentation-capable external display is detected, users can choose
  the existing second-screen keyboard/trackpad panel or (when supported) the
  BottomDesktop panel, and can select among multiple displays. These actions
  **do not** move the full KDE compositor to the secondary display.

No changes to Chromium sandbox flags, PRoot isolation, or Steam/Proton.
No physical-device tests yet. Check portrait/landscape, Android IME insets,
KDE guest relayout, pointer capture, external display connect/disconnect and
gamepad focus before merging.
