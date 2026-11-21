# Spec: quest description editor (reflibquests)

Behavioural spec for the in-game description editor and the description renderer. It was written from the user's
screen recording of the upstream mod (observed behaviour only) plus the public docs. Nothing here is copied code,
art or text; the implementation is our own. Items marked **(?)** were not fully observed.

## 1. Data model

- A quest description is an **ordered list of lines** (`List<String>`). It is stored in the translation table under
  `quest.<id>.quest_desc` (same key family as titles), not inline in the quest data, once any translation exists.
  The importer already reads both places; the exporter already writes the lang form.
- One list entry = one paragraph line in the panel. An **empty entry** is a blank line.
- **Page breaks** split the description into pages. A page break is one entry whose whole text is exactly
  `{@pagebreak}` (seen in the real pack's lang files). A new page starts with placeholder text so it is never empty.
- Inside one entry, the two characters `\n` (backslash, n) are a **line break** (the real pack uses them to make
  paragraphs inside a single entry); `\n\n` is a blank line. Display splits on them; storage keeps them as typed.
- Editing a description produces a translation update for the **currently selected locale**, plus (when the book
  has a fallback locale) leaves the fallback untouched.

## 2. Line markup (what a line may contain)

A line is plain text with these inline constructs, all optional:

1. **Colour/style codes**: `&` + one of `0-9a-f` (colour), `k l m n o` (obfuscated, bold, strikethrough,
   underline, italic), `r` (reset). Case-insensitive. Display converts `&x` to the game's section-sign codes;
   storage keeps `&x`. A literal ampersand is written `&&`.
2. **Inline image**: `{image:<texture id> width:<n> height:<n> align:<left|center|right> fit:<true|false>}`.
   `texture id` is a resource location (`modid:textures/...png`). Defaults: width 100, height 100,
   align center, fit false. `fit:true` scales to panel width keeping aspect. An image takes its own row(s).
3. **JSON text component line**: if the whole line starts with `{` or `[`, it is parsed as a JSON text
   component (or list). Supported fields: `text`, `extra`, `color`, `bold`, `italic`, `underlined`,
   `strikethrough`, `obfuscated`, `clickEvent{action,value}`, `hoverEvent` (text only).
   Click actions: `open_url` (opens the safe-link dialog, section 5) and `change_page` (value = quest id: opens
   that quest; if unknown, shows a red "unknown quest" toast). Invalid JSON renders as the raw text.

Unknown braces or codes are shown literally, never throw.

## 3. Editor screen

A large centred panel titled "Quest Description Editor":

- **Text area**: multi-line plain-text box, one line per list entry, shows the raw markup (no live preview).
  Supports caret, selection, copy/paste, undo/redo (Ctrl+Z / Ctrl+Y), long-line wrapping that does not alter
  the data, and a scrollbar. Enter inserts a new line (new list entry). Paste of multi-line text splits into entries.
- **Header buttons**: green check = Accept, red X = Cancel (confirm if text changed **(?)**).
- **Toolbar** (left to right), each with a hover tooltip showing its name and shortcut:

  | Button | Action | Shortcut |
  |---|---|---|
  | Accept | save and close | Shift+Enter |
  | Undo | undo last edit | Ctrl+Z |
  | B / I / U / S | wrap selection (or insert at caret) with `&l` `&o` `&n` `&m` ... `&r` | Alt+B / I / U / S |
  | Palette | opens a 16-swatch grid; picks insert `&<code>` ... `&r` around selection | - |
  | Clear Formatting | strip all `&x` codes from the selection (whole line if no selection) | Alt+R |
  | Insert Quest Link | opens a searchable quest picker ("Select Quest Object": chapters' quests by title); inserts the `change_page` JSON line | Alt+Q |
  | Insert URL/Docs Link | asks for a URL (and optional label); inserts the `open_url` JSON line | Alt+L |
  | Add Page Break | inserts a page-break entry at the caret | Alt+P |
  | Image | opens "Add Image" dialog (section 4) | probably Alt+M **(?)** |
  | Convert line to JSON | converts the caret line's `&` codes to the equivalent JSON component; holding Shift converts every line | - |

- The toolbar must work with the keyboard alone; tooltips list the shortcut in the same bracket style.

## 4. "Add Image" dialog

Fields: Image Location (texture picker: search box + grid of available textures + a "none" entry), Alignment
(left / center / right), Fit to Panel Width (bool), Image Height, Image Width. Accept inserts the `{image:...}`
line at the caret. Width/height default to the texture size when the pack provides it **(?)**.

## 5. Rendering (quest detail panel)

- Lines render top to bottom, wrapped to the panel width; blank entries are blank lines.
- Colour and style codes apply until `r` or the end of the line (they do not leak to the next line).
- Images render inline at the configured size, honouring align and fit; they never overflow the panel.
- A page break starts a new page; the panel shows page controls (wheel / Page Up/Down) and scrolls within a page.
- Hovering a link underlines it; clicking:
  - `open_url`: a confirmation dialog "Are you sure you want to open the following website?" with the URL, a
    warning "Never open links from people that you don't trust!", and buttons Yes / Copy to Clipboard / No.
    Only `http` and `https` are allowed; anything else is refused with a toast.
  - `change_page`: switches the open quest; Backspace returns to the previous one (history setting).
- Missing translations are highlighted when the player preference is on **(?)**.

## 6. Permissions and sync

- Editing requires editing mode (op level 2, same gate as other edits). The server validates and applies a
  `set_text` style edit for the description list; limits: 60 lines, 300 chars per line (already enforced by
  `BookEditor.setText`), image dimensions 1..4096, quest-link ids must exist.
- The edit is added to the change log as "Update Translation Quest quest description '...'" with Undo/Redo.
- Saving shows the toast "Quests saved on server" and, for lang-only edits, a reminder that text now lives in a
  language file.

## 7. Acceptance tests

1. Round trip: import a pack with `&` codes, JSON lines, images, page breaks; export; re-import; description lists
   are identical (raw).
2. Display conversion: `&6Gold&r plain` renders gold then default; `&&` renders `&`; codes never leak across lines.
3. Image tag parse: all four attributes, defaults, and a malformed tag shown literally.
4. JSON line: `change_page` opens the quest; `open_url` shows the confirm dialog; invalid JSON shows raw text.
5. Editor: every toolbar shortcut works from the keyboard; undo/redo restores text and caret; Clear Formatting
   (Alt+R) strips codes; Add Page Break (Alt+P) inserts a break entry; Convert line to JSON (and Shift for all).
6. Server: a non-op cannot edit; oversize lines/lists are rejected without changing the book.

## 8. Build order

1. Pure Java `RichText` parser/renderer model (codes, image tag, JSON, pages) with the tests above (no GL).
2. Display layer in `QuestBookWidget` (wrapping, images, links, page control, link-confirm dialog).
3. Replace `TextEditScreen`'s description field with the editor screen and toolbar.
4. Server: image/link validation in `QuestEdits`, change-log entry.

## 9. Extra facts from the player's instance (language files only, no code read)

- Cancel/Esc with unsaved changes asks "Discard Unsaved Changes?".
- The URL button's prompt reads "URL or 'docs:' link": a link may be `http(s)://...` or `docs:<page>`; click
  actions also include "Show a Docs Page". Our docs-page support is optional (treated as unknown link, refused).
- The palette has three tabs: Chat Colors (the 16 `&` colours), Dye Colors, Recent Colors.
- Chapter images have more fields than inline images: X, Y, Rotation, Image Tint, Image Alpha, Hover Text,
  Click Action (none / open URI / jump to quest / run command / post custom event / show recipes / show docs),
  Click Action Data, Lock Position, Only visible in Editing Mode, Draw Title Text on Image (+ shadow, inset,
  H/V alignment: start/middle/end). Out of scope for this spec; tracked for the chapter-image editor.
- Editor-mode key reference: Edit Description, Insert Page Break, Insert Image and Insert Line exist as panel
  actions (the upstream lang file has the last two labels swapped; ours must not).

## 10. Still unknown

Image button shortcut, whether Enter/paste splitting matches upstream exactly, default image size.
