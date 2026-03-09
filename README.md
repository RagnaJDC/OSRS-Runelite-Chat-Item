# Chat Item Pricer & Tooltips

A RuneLite plugin that enriches your chat experience by highlighting item names, showing live price information, and providing hover tooltips with full item stats — all directly in the chatbox.

---

## What It Does

### 💬 Clan & Friends Chat Drop Broadcasts
When a clanmate or friend receives a drop, clue reward, or collection log item and the game broadcasts it to chat, the plugin:
- **Highlights the item name** in a colour based on its value
- **Replaces or annotates the coin value** shown in the message with GE price, High Alch value, or both
- Works for **drop broadcasts**, **clue item rewards**, and **collection log notifications**

### 🎒 Personal Valuable Drop Notifications
When *you* receive a valuable drop (the game's own "Valuable drop:" message), the plugin:
- Colours the item name based on its rarity tier
- Appends or replaces the coin value with GE price, High Alch value, or both
- Requires the in-game **Valuable drops** notification to be enabled

### 🔍 Item Tooltips on Hover
Hover your mouse over any highlighted item name in chat to see a tooltip showing:
- Item icon
- GE price and High Alch value
- Equipment stats (attack/defence bonuses, slot)
- Item weight

### 🖱️ Wiki Right-Click Lookup
Right-click any highlighted item name in chat to get a **"Lookup on Wiki"** context menu option that opens the Old School RuneScape Wiki page for that item in your browser.

### 🗣️ Free-Form Chat Scanning *(optional)*
Enable the **Highlight items in player chat** option to scan public chat, private messages, clan chat, and friends chat for item names typed naturally in conversation. Recognised names are coloured and become hoverable — perfect for trading or bossing discussions.

---

## How It Works

1. At login, the plugin optionally builds a full item name index by scanning item IDs 0–30,000 using the RuneLite `ItemManager`. This is a one-time background operation chunked across client ticks to avoid any hitching.
2. Incoming chat messages are matched against known drop/clue/collection log patterns using regex.
3. Matched item names are looked up for GE price and High Alch value, then wrapped in RuneLite colour tags.
4. The overlay tracks your mouse position over the chatbox. When hovering a coloured item name, it resolves the item and renders the tooltip.

---

## Configuration

### Clan / Friends Chat
| Option | Description | Default |
|--------|-------------|---------|
| Friends Chat drops | Recolour item names and replace values in Friends Chat drop broadcasts | ✅ On |
| Clan Chat drops | Recolour item names and replace values in Clan Chat drop broadcasts | ✅ On |
| Display price | Show **GE**, **High Alch**, or **Both** in broadcast messages | Both |
| Format prices (1.2M) | Show compact notation (e.g. `1.2M`) instead of exact numbers | ❌ Off |

### Personal Drop Notification
| Option | Description | Default |
|--------|-------------|---------|
| Enable personal drops | Modify the "Valuable drop" game message with rarity colouring and price info | ✅ On |
| Display price | Show **GE**, **High Alch**, or **Both** | Both |
| Format prices (1.2M) | Show compact notation in personal drop messages | ❌ Off |
| Message colour | Colour used for the notification text surrounding the item name | Red |

### Item Tooltips
| Option | Description | Default |
|--------|-------------|---------|
| Enable tooltips | Show the hover tooltip for highlighted item names | ✅ On |
| Highlight items in player chat | Scan free-form chat for item names and colour them | ✅ On |
| Tooltip position | Show tooltip **Above** or **Below** the cursor | Below |
| Tooltip font size (px) | Font size for tooltip text (8–24 px) | 16 |
| Tooltip opacity % | Background opacity of the tooltip (10–100%) | 75% |
| Show item image | Display the item icon inside the tooltip | ✅ On |
| Show prices | Display GE and High Alch prices in the tooltip | ✅ On |
| Show equipment stats | Display attack/defence bonuses and slot info for equippable items | ✅ On |
| Show weight | Display item weight for equippable items | ✅ On |
| Require key to show tooltip | Only show the tooltip while a hotkey is held | ❌ Off |
| Tooltip hotkey | The key to hold when the above option is enabled | Alt |
| Filter items (comma-separated) | Item names that will never show a tooltip | `coins` |
| Wiki right-click lookup | Add "Lookup on Wiki" to the right-click menu when hovering an item | ✅ On |

### Item Colours
Colour thresholds used to highlight item names based on their GE value. All colours are customisable.

| Tier | Default Threshold | Default Colour |
|------|-------------------|----------------|
| Common | < 10,000 gp | White |
| Uncommon | 10,000 gp+ | Green |
| Rare | 100,000 gp+ | Blue |
| Epic | 1,000,000 gp+ | Purple |
| Legendary | 10,000,000 gp+ | Orange |

---

## Screenshots

*Drop broadcast with item highlighted and value replaced:*

<!-- ![Drop broadcast example](images/drop_broadcast.png) -->

*Hover tooltip showing item icon, prices, and equipment stats:*

<!-- ![Tooltip example](images/tooltip.png) -->

*Right-click wiki lookup:*

<!-- ![Wiki right-click example](images/wiki_rightclick.png) -->

*Personal valuable drop notification:*

<!-- ![Personal drop example](images/personal_drop.png) -->

---

## Notes

- The free-form chat scan (**Highlight items in player chat**) performs a one-time full item scan at login and is enabled by default. Disable it if you only want drop broadcasts highlighted and don't need item names in typed conversation to be detected.
- Items added to the **Filter items** list will never show tooltips, useful for ignoring noise like `coins`, `bones`, or `feather`.
- Tooltip hotkey gating is useful during combat to avoid accidental tooltip popups.