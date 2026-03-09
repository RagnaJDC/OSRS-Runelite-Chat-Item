package com.chatitem;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.input.KeyManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.QuantityFormatter;

/**
 * Draws a tooltip when the mouse hovers over a coloured item name in the chatbox.
 */
@Slf4j
public class ChatItemOverlay extends Overlay
{
// Matches <col=RRGGBB>item name</col>
private static final Pattern COL_ITEM_PATTERN = Pattern.compile(
"<col=([0-9a-fA-F]{6})>([^<]{2,}?)(?:\\s+x(\\d+))?</col>",
Pattern.CASE_INSENSITIVE
);

// Strips any RuneLite tag so we can measure plain-text widths
private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");

// Rarity colours (hex, no #)
static final String COLOR_COMMON    = "ffffff";
static final String COLOR_UNCOMMON  = "1eff00";
static final String COLOR_RARE      = "0070dd";
static final String COLOR_EPIC      = "a335ee";
static final String COLOR_LEGENDARY = "ff8000";

// Tooltip layout
private static final int PAD      = 5;
private static final int ICON_SZ  = 36;   // item icon
private static final int LINE_GAP = 2;

private static final Color BORDER    = new Color(80, 80, 80);
private static final Color BORDER_IN = new Color(35, 35, 35);
private static final Color COL_SEP   = new Color(0x55, 0x55, 0x55);
private static final Color COL_LABEL = new Color(0x99, 0x99, 0x99);
private static final Color COL_GOLD  = new Color(0xff, 0xd7, 0x00);
private static final Color COL_MEMBER= new Color(0x00, 0xcc, 0x44);
private static final Color COL_POS   = new Color(0x00, 0xff, 0x00);
private static final Color COL_NEG   = new Color(0xff, 0x44, 0x44);
private static final Color COL_VALUE = new Color(0xff, 0xff, 0x00);
private static final Color COL_WHITE = Color.WHITE;

private static final int MOVE_THRESHOLD = 3;

// Per-frame state cache
private int         lastMx = -1, lastMy = -1;
private Widget      cachedWidget;
private TooltipData cachedTooltip;

// LRU item info cache, capped at 64
private final Map<Integer, TooltipData> itemCache =
new LinkedHashMap<Integer, TooltipData>(64, 0.75f, true)
{
@Override
protected boolean removeEldestEntry(Map.Entry<Integer, TooltipData> e)
{
return size() > 64;
}
};

private final Client         client;
private final ChatItemConfig config;
private final ItemManager    itemManager;
private final KeyManager     keyManager;

// Tracks whether the tooltip hotkey is currently held
private volatile boolean hotkeyHeld = false;
private HotkeyListener hotkeyListener;

@Inject
ChatItemOverlay(Client client, ChatItemConfig config, ItemManager itemManager,
KeyManager keyManager)
{
this.client      = client;
this.config      = config;
this.itemManager = itemManager;
this.keyManager  = keyManager;

// Must be created after config is assigned (lambda captures config)
hotkeyListener = new HotkeyListener(() -> config.tooltipKey())
{
@Override
public void hotkeyPressed()  { hotkeyHeld = true; }
@Override
public void hotkeyReleased() { hotkeyHeld = false; }
};

setLayer(OverlayLayer.ALWAYS_ON_TOP);
setPosition(OverlayPosition.DYNAMIC);
setMovable(false);
setResizable(false);
setSnappable(false);
setPreferredLocation(new java.awt.Point(0, 0));
setPreferredSize(new java.awt.Dimension(0, 0));
setMinimumSize(1);
}

/** Called by ChatItemPlugin.startUp() */
public void registerHotkey()
{
keyManager.registerKeyListener(hotkeyListener);
}

/** Called by ChatItemPlugin.shutDown() */
public void unregisterHotkey()
{
keyManager.unregisterKeyListener(hotkeyListener);
hotkeyHeld = false;
}

/** Pre-warm the cache for an item the plugin just coloured in chat. */
public void cacheItem(int itemId)
{
if (itemCache.containsKey(itemId)) return;
try
{
ItemComposition comp = itemManager.getItemComposition(itemId);
int gePrice = itemManager.getItemPrice(itemId);

TooltipData d = new TooltipData();
d.itemId  = itemId;
d.name    = comp.getName();
d.gePrice     = gePrice;
d.haPrice     = comp.getHaPrice();
d.members     = comp.isMembers();
d.isTradeable = comp.isTradeable();

		ItemStats stats = itemManager.getItemStats(itemId);
		if (stats != null)
		{
			d.isEquipable    = stats.isEquipable();
			d.equipmentStats = stats.getEquipment();
			d.weight         = stats.getWeight();
		}itemCache.put(itemId, d);
}
catch (Exception ex)
{
log.debug("ChatItemOverlay: cacheItem {} failed", itemId, ex);
}
}

/** Called on shutDown() to avoid stale data appearing at next startUp(). */
public void clearCache()
{
itemCache.clear();
cachedWidget  = null;
cachedTooltip = null;
lastMx = -1;
lastMy = -1;
}

// ---------------------------------------------------------------
// Overlay render
// ---------------------------------------------------------------

@Override
public Dimension render(Graphics2D g)
{
if (!config.enableTooltips()) return null;
if (config.requireKeyHeld() && !hotkeyHeld) return null;

Point mouse = client.getMouseCanvasPosition();
if (mouse == null) return null;
int mx = mouse.getX(), my = mouse.getY();

boolean moved = Math.abs(mx - lastMx) > MOVE_THRESHOLD
|| Math.abs(my - lastMy) > MOVE_THRESHOLD;

if (!moved && cachedTooltip != null && cachedWidget != null)
{
Rectangle b = cachedWidget.getBounds();
if (b != null && b.contains(mx, my))
{
drawTooltip(g, cachedTooltip, mx, my);
return null;
}
}

lastMx = mx;
lastMy = my;

Widget line = findHoveredChatLine(mx, my);
if (line == null)
{
cachedWidget  = null;
cachedTooltip = null;
return null;
}

String text = line.getText();
if (text == null || !text.contains("<col="))
{
cachedWidget  = null;
cachedTooltip = null;
return null;
}

Font font = FontManager.getRunescapeSmallFont().deriveFont((float) config.tooltipFontSizePx());
g.setFont(font);
FontMetrics fm = g.getFontMetrics();

TooltipData tooltip = findHoveredItem(text, line, mx, fm);

cachedWidget  = line;
cachedTooltip = tooltip;

if (tooltip != null) drawTooltip(g, tooltip, mx, my);
return null;
}

// ---------------------------------------------------------------
// Item detection
// ---------------------------------------------------------------

private TooltipData findHoveredItem(String text, Widget widget, int mouseX, FontMetrics fm)
{
Rectangle bounds = widget.getBounds();
if (bounds == null) return null;

final int originX = bounds.x + 2;

java.util.List<CandidateSpan> candidates = new java.util.ArrayList<>();
Matcher m = COL_ITEM_PATTERN.matcher(text);
while (m.find())
{
String name = m.group(2).trim();
if (!name.matches(".*[a-zA-Z]{2,}.*")) continue;
// Strip leading "N x " quantity prefix (e.g. "14 x black knife")
name = name.replaceAll("^\\d+\\s+x\\s+", "").trim();
// Strip trailing " xN" suffix
name = name.replaceAll("\\s+x\\d+$", "").trim();
if (isFiltered(name)) continue;
TooltipData td = resolveByName(name);
if (td != null)
candidates.add(new CandidateSpan(m.start(), m.end(), name, td));
}

if (candidates.isEmpty()) return null;
if (candidates.size() == 1) return candidates.get(0).data;

// Multiple items: measure pixel position and pick closest to mouseX
int cursorX = 0;
int pos = 0;
int[] spanCursorX = new int[candidates.size()];
int candidateIdx = 0;

while (pos < text.length() && candidateIdx < candidates.size())
{
while (candidateIdx < candidates.size()
&& pos == candidates.get(candidateIdx).startIdx)
{
spanCursorX[candidateIdx] = cursorX;
candidateIdx++;
}
if (candidateIdx >= candidates.size()) break;

char c = text.charAt(pos);
if (c != '<')
{
cursorX += fm.charWidth(c);
pos++;
continue;
}
int tagEnd = text.indexOf('>', pos);
if (tagEnd == -1) { pos++; continue; }
String tagLower = text.substring(pos + 1, tagEnd).trim().toLowerCase();
if (tagLower.startsWith("img=")) cursorX += fm.getHeight();
else if (tagLower.startsWith("col="))
{
boolean isCandidateSpan = false;
for (CandidateSpan cs : candidates)
if (cs.startIdx == pos) { isCandidateSpan = true; break; }
if (isCandidateSpan)
{
int depth = 1, j = tagEnd + 1;
while (j < text.length() && depth > 0)
{
if (text.regionMatches(true, j, "<col=", 0, 5)) depth++;
else if (text.regionMatches(true, j, "</col>", 0, 6)) depth--;
if (depth > 0) j++;
}
String inner = text.substring(tagEnd + 1, j);
String display = TAG_PATTERN.matcher(inner).replaceAll("");
cursorX += fm.stringWidth(display);
pos = j + 6;
continue;
}
}
pos = tagEnd + 1;
}
for (int k = candidateIdx; k < candidates.size(); k++)
spanCursorX[k] = cursorX;

TooltipData best = null;
int bestDist = Integer.MAX_VALUE;
for (int k = 0; k < candidates.size(); k++)
{
CandidateSpan cs = candidates.get(k);
int spanW = fm.stringWidth(cs.name);
int mid = originX + spanCursorX[k] + spanW / 2;
int dist = Math.abs(mouseX - mid);
if (dist < bestDist) { bestDist = dist; best = cs.data; }
}
return best;
}

/** Returns true if the item name is in the user's filter list. */
private boolean isFiltered(String name)
{
String filter = config.tooltipFilter();
if (filter == null || filter.trim().isEmpty()) return false;
for (String entry : filter.split(","))
{
if (entry.trim().equalsIgnoreCase(name.trim())) return true;
}
return false;
}

private static class CandidateSpan
{
final int startIdx;
final String name;
final TooltipData data;
CandidateSpan(int s, int e, String n, TooltipData d)
{ startIdx = s; name = n; data = d; }
}

private TooltipData resolveByName(String name)
{
String lower = name.toLowerCase();
for (TooltipData d : itemCache.values())
if (d.name != null && d.name.equalsIgnoreCase(lower)) return d;

// Check alias map: allow abbreviations like "tbow", "bcp", "bp" etc.
String canonical = ItemAliases.MAP.get(lower);
String lookupName = (canonical != null) ? canonical : name;

int id = lookupIdByName(lookupName);
if (id < 0) return null;
cacheItem(id);
TooltipData cached = itemCache.get(id);
// Reject NPC/entity items that are not real player items.
// Real items satisfy at least one of: tradeable on GE, has HA value, has GE price, or is equipable.
// Entity items ("Man", "Run", etc.) satisfy none of these.
if (cached != null && !cached.isTradeable && cached.gePrice <= 0 && cached.haPrice <= 0 && !cached.isEquipable)
return null;
return cached;
}

// ---------------------------------------------------------------
// Widget walking
// ---------------------------------------------------------------

private Widget findHoveredChatLine(int mx, int my)
{
for (int child = 0; child < 256; child++)
{
Widget w = client.getWidget(InterfaceID.CHATBOX, child);
if (w == null || w.isHidden()) continue;
Widget found = checkWidget(w, mx, my);
if (found != null) return found;
}

for (int child = 0; child < 32; child++)
{
Widget w = client.getWidget(InterfaceID.PM_CHAT, child);
if (w == null || w.isHidden()) continue;
Widget found = checkWidget(w, mx, my);
if (found != null) return found;
}

return null;
}

private Widget checkWidget(Widget w, int mx, int my)
{
if (w == null || w.isHidden()) return null;
Rectangle b = w.getBounds();
if (b == null || !b.contains(mx, my)) return null;

String text = w.getText();
if (text != null && text.contains("<col="))
{
Matcher m = COL_ITEM_PATTERN.matcher(text);
while (m.find())
{
if (m.group(2).matches(".*[a-zA-Z].*"))
return w;
}
}

return checkChildren(w, mx, my);
}

private Widget checkChildren(Widget parent, int mx, int my)
{
if (parent == null || parent.isHidden()) return null;
Widget[][] groups = {
parent.getDynamicChildren(),
parent.getStaticChildren(),
parent.getNestedChildren()
};
for (Widget[] group : groups)
{
if (group == null) continue;
for (Widget child : group)
{
Widget found = checkWidget(child, mx, my);
if (found != null) return found;
}
}
return null;
}

// ---------------------------------------------------------------
// Item ID lookup
// ---------------------------------------------------------------

private int lookupIdByName(String itemName)
{
List<net.runelite.http.api.item.ItemPrice> results =
itemManager.search(itemName);
if (results == null || results.isEmpty()) return -1;
// Exact match only — never fall back to a fuzzy result.
// Fuzzy fallback caused e.g. "Yes" → "Jars of eyes" and "tbow" → "Shortbow".
for (net.runelite.http.api.item.ItemPrice p : results)
if (p.getName().equalsIgnoreCase(itemName)) return p.getId();
return -1;
}

// ---------------------------------------------------------------
// Wiki opener
// ---------------------------------------------------------------

/** Returns the item currently under the mouse cursor, or null. */
public TooltipData getHoveredItem()
{
return cachedTooltip;
}

static void openWiki(String itemName)
{
try
{
String encoded = URLEncoder.encode(itemName.replace(' ', '_'), "UTF-8");
URI uri = new URI("https://oldschool.runescape.wiki/w/" + encoded);
Desktop.getDesktop().browse(uri);
}
catch (Exception ex)
{
log.debug("ChatItemOverlay: failed to open wiki for '{}'", itemName, ex);
}
}

// ---------------------------------------------------------------
// Tooltip drawing
// ---------------------------------------------------------------

private void drawTooltip(Graphics2D g, TooltipData d, int mx, int my)
{
g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

Font font = FontManager.getRunescapeSmallFont().deriveFont((float) config.tooltipFontSizePx());
g.setFont(font);
FontMetrics fm = g.getFontMetrics();
int lh = fm.getHeight() + LINE_GAP;

List<Line> lines = buildLines(d, fm);

// Measure max text width
int textW = 0;
for (Line l : lines)
{
int lw = 0;
for (Seg s : l.segs) lw += fm.stringWidth(s.text);
textW = Math.max(textW, lw);
}

boolean showIcon = config.showTooltipImage();
BufferedImage icon = showIcon
	? itemManager.getImage(d.itemId, Math.max(1, d.quantity), d.quantity > 1)
	: null;
int iconAreaW = (icon != null) ? ICON_SZ + PAD : 0;
// Icon overlaps the text area (top-right corner) rather than expanding box width.
// iconAreaW is still used for separator line shortening so they clear the icon.
int w = textW + PAD * 2;
int h = Math.max(icon != null ? ICON_SZ + PAD * 2 : 0,
lines.size() * lh + PAD * 2);

int cx = client.getCanvasWidth(), cy = client.getCanvasHeight();

// Horizontal: prefer right of cursor, flip left if it would overflow, then clamp
int tx = mx + 12;
if (tx + w > cx) tx = mx - w - 4;
tx = Math.max(0, Math.min(tx, cx - w));

// Vertical: strictly honour config direction, never flip.
// Just clamp to screen edge so the box stays fully visible when possible,
// but never crosses to the other side of the cursor.
int ty;
if (config.tooltipPosition() == TooltipPosition.BELOW)
{
	ty = my + 18;
	// Hard clamp to bottom border - tooltip stops at edge, never clips off-screen
	if (ty + h > cy) ty = cy - h;
	// Only apply the below-cursor floor if it doesn't push us off the bottom
	if (my + 1 <= cy - h) ty = Math.max(ty, my + 1);
}
else
{
	ty = my - h - 4;
	// Hard clamp to top border
	if (ty < 0) ty = 0;
	// Only apply the above-cursor ceiling if it doesn't push us off the top
	if (my - 1 >= h) ty = Math.min(ty, my - 1);
}

// Background with configurable opacity
int alpha = (int) Math.round(config.overlayOpacity() / 100.0 * 255);
g.setColor(new Color(20, 20, 20, alpha));
g.fillRect(tx, ty, w, h);
g.setColor(BORDER_IN);
g.drawRect(tx, ty, w - 1, h - 1);
g.setColor(BORDER);
g.drawRect(tx + 1, ty + 1, w - 3, h - 3);

// Item icon (right side)
if (icon != null)
{
int slotX = tx + w - PAD - ICON_SZ - 1;
int slotY = ty + PAD - 1;
g.setColor(new Color(30, 30, 30));
g.fillRect(slotX, slotY, ICON_SZ + 2, ICON_SZ + 2);
g.setColor(new Color(60, 60, 60));
g.drawRect(slotX, slotY, ICON_SZ + 1, ICON_SZ + 1);
int ix = slotX + 1 + (ICON_SZ - icon.getWidth())  / 2;
int iy = slotY + 1 + (ICON_SZ - icon.getHeight()) / 2;
g.drawImage(icon, ix, iy, null);
}

// Text lines
int lx = tx + PAD;
int ly = ty + PAD;
for (Line l : lines)
{
if (l.isSeparator)
{
int sepY = ly + lh / 2;
g.setColor(COL_SEP);
g.drawLine(lx, sepY, tx + w - PAD - iconAreaW - 1, sepY);
ly += lh;
continue;
}

int xoff = 0;
int centerY   = ly + lh / 2;
int baselineY = centerY + (fm.getAscent() - fm.getDescent()) / 2;

for (Seg s : l.segs)
{
g.setColor(s.color);
g.drawString(s.text, lx + xoff, baselineY);
xoff += fm.stringWidth(s.text);
}
ly += lh;
}
}

// ---------------------------------------------------------------
// Line builder
// ---------------------------------------------------------------

private List<Line> buildLines(TooltipData d, FontMetrics fm)
{
Color rarity = parseHex(getRarityColor(d.gePrice, config));
List<Line> out = new ArrayList<>();

// Name
Line nameLine = new Line();
nameLine.addText(d.name, rarity);
if (d.quantity > 1)
nameLine.addText(" x" + QuantityFormatter.formatNumber(d.quantity), COL_LABEL);
if (d.members)
nameLine.addText(" (P2P)", COL_MEMBER);
if (d.isTradeable)
nameLine.addText(" (Tradeable)", COL_POS);
else
nameLine.addText(" (Untradeable)", COL_NEG);
out.add(nameLine);

boolean showPrices = config.showTooltipPrices();
boolean showStats  = config.showTooltipStats();
boolean showWeight = config.showTooltipWeight();

boolean hasEquip  = d.isEquipable && d.equipmentStats != null;
boolean hasPriceContent = showPrices && (d.gePrice > 0 || d.haPrice > 0);
boolean hasStatsContent = showStats && hasEquip;
boolean hasWeightContent = showWeight && hasEquip;

// Separator after name only if something follows
if (hasPriceContent || hasStatsContent || hasWeightContent)
	out.add(Line.separator());

// Prices
int qty = Math.max(1, d.quantity);

if (showPrices && d.gePrice > 0)
{
long total = (long) d.gePrice * qty;
Line l = new Line();
l.addText("GE: ", COL_LABEL);
l.addText(QuantityFormatter.quantityToStackSize(total) + " gp", COL_GOLD);
if (d.quantity > 1)
l.addText("  (" + QuantityFormatter.quantityToStackSize(d.gePrice) + " ea)", COL_VALUE);
out.add(l);
}

if (showPrices && d.haPrice > 0)
{
long totalHa = (long) d.haPrice * qty;
int  laPrice = d.haPrice * 2 / 3;
long totalLa = (long) laPrice * qty;
Line ha = new Line();
ha.addText("HA: ", COL_LABEL);
ha.addText(QuantityFormatter.quantityToStackSize(totalHa) + " gp", COL_POS);
ha.addText("   LA: ", COL_LABEL);
ha.addText(QuantityFormatter.quantityToStackSize(totalLa) + " gp", COL_VALUE);
out.add(ha);
}

// Separator between prices and stats/weight only when prices were shown
if (hasPriceContent && (hasStatsContent || hasWeightContent))
	out.add(Line.separator());

// Equipment stats
if (showStats && hasEquip)
{
ItemEquipmentStats eq = d.equipmentStats;

// Slot + speed
Line slotLine = new Line();
slotLine.addText("Slot: ", COL_LABEL);
slotLine.addText(slotName(eq.getSlot()), COL_WHITE);
if (eq.isTwoHanded()) slotLine.addText(" (2H)", COL_WHITE);
if (eq.getAspeed() > 0)
slotLine.addText("  Speed: " + eq.getAspeed(), COL_LABEL);
out.add(slotLine);

		// Attack bonuses
			boolean hasAtk = eq.getAstab() != 0 || eq.getAslash() != 0 || eq.getAcrush() != 0
					|| eq.getAmagic() != 0 || eq.getArange() != 0;
			if (hasAtk)
			{
				Line header = new Line();
				header.addText("Attack:", COL_LABEL);
				out.add(header);
				Line l = new Line();
				l.addText("  Stab: ",   COL_LABEL); l.addText(bonus(eq.getAstab())  + "  ", bonusColor(eq.getAstab()));
				l.addText("Slash: ",    COL_LABEL); l.addText(bonus(eq.getAslash()) + "  ", bonusColor(eq.getAslash()));
				l.addText("Crush: ",    COL_LABEL); l.addText(bonus(eq.getAcrush()) + "  ", bonusColor(eq.getAcrush()));
				l.addText("Magic: ",    COL_LABEL); l.addText(bonus(eq.getAmagic()) + "  ", bonusColor(eq.getAmagic()));
				l.addText("Ranged: ",   COL_LABEL); l.addText(bonus(eq.getArange()),          bonusColor(eq.getArange()));
				out.add(l);
			}

			// Defence bonuses
			boolean hasDef = eq.getDstab() != 0 || eq.getDslash() != 0 || eq.getDcrush() != 0
					|| eq.getDmagic() != 0 || eq.getDrange() != 0;
			if (hasDef)
			{
				Line header = new Line();
				header.addText("Defence:", COL_LABEL);
				out.add(header);
				Line l = new Line();
				l.addText("  Stab: ",   COL_LABEL); l.addText(bonus(eq.getDstab())  + "  ", bonusColor(eq.getDstab()));
				l.addText("Slash: ",    COL_LABEL); l.addText(bonus(eq.getDslash()) + "  ", bonusColor(eq.getDslash()));
				l.addText("Crush: ",    COL_LABEL); l.addText(bonus(eq.getDcrush()) + "  ", bonusColor(eq.getDcrush()));
				l.addText("Magic: ",    COL_LABEL); l.addText(bonus(eq.getDmagic()) + "  ", bonusColor(eq.getDmagic()));
				l.addText("Ranged: ",   COL_LABEL); l.addText(bonus(eq.getDrange()),          bonusColor(eq.getDrange()));
				out.add(l);
			}

			// Other bonuses
			boolean hasOther = eq.getStr() != 0 || eq.getPrayer() != 0
					|| eq.getRstr() != 0 || eq.getMdmg() != 0;
			if (hasOther)
			{
				Line header = new Line();
				header.addText("Other:", COL_LABEL);
				out.add(header);
				Line l = new Line();
				if (eq.getStr()    != 0) { l.addText("  Str: ",        COL_LABEL); l.addText(bonus(eq.getStr())              + "  ", bonusColor(eq.getStr())); }
				if (eq.getRstr()   != 0) { l.addText("  Ranged str: ", COL_LABEL); l.addText(bonus(eq.getRstr())             + "  ", bonusColor(eq.getRstr())); }
				if (eq.getMdmg()   != 0) { l.addText("  Magic dmg: ",  COL_LABEL); l.addText(bonus((int) eq.getMdmg()) + "%  ", bonusColor((int) eq.getMdmg())); }
				if (eq.getPrayer() != 0) { l.addText("  Prayer: ",      COL_LABEL); l.addText(bonus(eq.getPrayer()),           bonusColor(eq.getPrayer())); }
				out.add(l);
			}
}

// Weight -- independent of stats toggle, but only for equippable items
if (showWeight && hasEquip)
{
	Line wLine = new Line();
	wLine.addText("Weight: ", COL_LABEL);
	wLine.addText(String.format("%.2f", d.weight) + " kg", COL_WHITE);
	out.add(wLine);
}
return out;
}

private static String slotName(int slot)
{
switch (slot)
{
case 0:  return "Head";
case 1:  return "Cape";
case 2:  return "Neck";
case 3:  return "Weapon";
case 4:  return "Body";
case 5:  return "Shield";
case 7:  return "Legs";
case 9:  return "Gloves";
case 10: return "Boots";
case 12: return "Ring";
case 13: return "Ammo";
default: return "Equipment";
}
}

private static String bonus(int v)
{
return v >= 0 ? "+" + v : String.valueOf(v);
}

private static Color bonusColor(int v)
{
if (v > 0) return COL_POS;
if (v < 0) return COL_NEG;
return COL_VALUE;
}

static String getRarityColor(int gePrice, ChatItemConfig config)
{
if (gePrice >= 10_000_000) return colorToHex(config.colorLegendary());
if (gePrice >=  1_000_000) return colorToHex(config.colorEpic());
if (gePrice >=    100_000) return colorToHex(config.colorRare());
if (gePrice >=     10_000) return colorToHex(config.colorUncommon());
return colorToHex(config.colorCommon());
}

private static String colorToHex(java.awt.Color c)
{
return String.format("%06x", c.getRGB() & 0xFFFFFF);
}

private static Color parseHex(String hex)
{
try   { return new Color(Integer.parseInt(hex, 16)); }
catch (Exception e) { return Color.WHITE; }
}

// ---------------------------------------------------------------
// Inner types
// ---------------------------------------------------------------

private static class Line
{
final List<Seg> segs = new ArrayList<>();
final boolean isSeparator;

Line() { isSeparator = false; }
private Line(boolean sep) { isSeparator = sep; }

static Line separator() { return new Line(true); }

Line addText(String text, Color color)
{
segs.add(new Seg(text, color));
return this;
}
}

private static class Seg
{
final String text;
final Color  color;
Seg(String t, Color c) { text = t; color = c; }
}

static class TooltipData
{
int    itemId;
String name;
int    gePrice;
int    haPrice;
boolean members;
boolean isTradeable;
int    quantity = 1;
String colHex;
boolean isEquipable;
double weight;
ItemEquipmentStats equipmentStats;
}
}
