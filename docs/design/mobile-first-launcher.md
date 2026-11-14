# Mobile-first launcher navigation

DroidDeck has a landscape gamepad-first layout and a portrait bottom bar. On narrow
phones, the desktop/gamepad rail can take too much horizontal space, and the old
portrait bar may scroll many destinations out of view.

This branch adds an adaptive phone navigation policy in Android Compose:

- Android configurations with `smallestScreenWidthDp < 600` use bottom navigation
  in **portrait and landscape**. Dual-screen mode keeps its existing behavior.
- Up to four visible primary destinations are kept on the bar, prioritizing Steam,
  Library, Linux Desktop, and Setup while respecting their saved relative order.
- Remaining enabled destinations, including user-shelved tabs, appear under More.
  Hidden or disabled features are never reintroduced.
- The phone bottom bar always renders icons **and labels**, with at least a 56 dp
  touch width for each item on ordinary phone widths. Non-phone preferences remain.
- A running session appears as an action on the More page instead of consuming a
  sixth permanent phone-nav slot.
- Badges and download counts aggregate over **mobile overflow**, not just the tabs
  explicitly shelved on desktop.
- Mobile landscape content uses compact/narrow pane spacing rather than tablet
  margins. Tablets retain the existing side rail and customization behavior.

## Interaction requirements

- Both touch and gamepad focus must work, including selection and back navigation
  from tabs reached through mobile More.
- All tabs remain available via More and keep their original onSelect actions.
- Device rotation preserves the selected page and page contents.
- Small portrait widths should not rely on sideways scrolling to discover key tabs.
- Android IME should still hide the agent bottom bar when the keyboard is open.

## Tests

`MobileNavigationTest` tests the pure tab allocation policy; `NavTabsTest`
covers the 5-item phone bar and responsive overflow view. Existing
`PortraitLayoutTest` and navigation tests protect tablet/desktop behavior.

## Scope

This is a first mobile launcher iteration, not a redesign of the Linux KDE
desktop, on-screen gamepad editor, touchpad gestures, or Windows VM support.
It does not modify runtime isolation, Steam/Proton, or device security.
