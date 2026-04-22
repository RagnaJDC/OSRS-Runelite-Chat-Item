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

	// Tooltip layout constants
	private static final int PAD      = 5;
	private static final int ICON_SZ  = 36;
	private static final int LINE_GAP = 2;

	private static final Color BORDER     = new Color(80, 80, 80);
	private static final Color BORDER_IN  = new Color(35, 35, 35);
	private static final Color COL_SEP    = new Color(0x55, 0x55, 0x55);
	private static final Color COL_LABEL  = new Color(0x99, 0x99, 0x99);
	private static final Color COL_GOLD   = new Color(0xff, 0xd7, 0x00);
	private static final Color COL_MEMBER = new Color(0x00, 0xcc, 0x44);
	private static final Color COL_POS    = new Color(0x00, 0xff, 0x00);
	private static final Color COL_NEG    = new Color(0xff, 0x44, 0x44);
	private static final Color COL_VALUE  = new Color(0xff, 0xff, 0x00);
	private static final Color COL_WHITE  = Color.WHITE;

	private static final int MOVE_THRESHOLD = 3;

	// Per-frame state cache
	private int lastMouseX = -1;
	private int lastMouseY = -1;
	private Widget cachedWidget;
	private TooltipData cachedTooltip;

	// LRU item info cache, capped at 64 entries
	private final Map<Integer, TooltipData> itemCache =
		new LinkedHashMap<Integer, TooltipData>(64, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<Integer, TooltipData> eldest)
			{
				return size() > 64;
			}
		};

	private final Client client;
	private final ChatItemConfig config;
	private final ItemManager itemManager;
	private final KeyManager keyManager;

	// Tracks whether the tooltip hotkey is currently held
	private volatile boolean hotkeyHeld = false;
	private HotkeyListener hotkeyListener;

	@Inject
	ChatItemOverlay(Client client, ChatItemConfig config, ItemManager itemManager, KeyManager keyManager)
	{
		this.client = client;
		this.config = config;
		this.itemManager = itemManager;
		this.keyManager = keyManager;

		// Must be created after config is assigned (lambda captures config)
		hotkeyListener = new HotkeyListener(() -> config.tooltipKey())
		{
			@Override
			public void hotkeyPressed()
			{
				hotkeyHeld = true;
			}

			@Override
			public void hotkeyReleased()
			{
				hotkeyHeld = false;
			}
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
		if (itemCache.containsKey(itemId))
		{
			return;
		}

		try
		{
			ItemComposition itemComposition = itemManager.getItemComposition(itemId);
			int grandExchangePrice = itemManager.getItemPrice(itemId);

			TooltipData tooltipData = new TooltipData();
			tooltipData.itemId      = itemId;
			tooltipData.name        = itemComposition.getName();
			tooltipData.gePrice     = grandExchangePrice;
			tooltipData.haPrice     = itemComposition.getHaPrice();
			tooltipData.members     = itemComposition.isMembers();
			tooltipData.isTradeable = itemComposition.isTradeable();

			ItemStats itemStats = itemManager.getItemStats(itemId);
			if (itemStats != null)
			{
				tooltipData.isEquipable    = itemStats.isEquipable();
				tooltipData.equipmentStats = itemStats.getEquipment();
				tooltipData.weight         = itemStats.getWeight();
			}

			itemCache.put(itemId, tooltipData);
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
		cachedWidget = null;
		cachedTooltip = null;
		lastMouseX = -1;
		lastMouseY = -1;
	}

	// ---------------------------------------------------------------
	// Overlay render
	// ---------------------------------------------------------------

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.enableTooltips())
		{
			return null;
		}
		if (config.requireKeyHeld() && !hotkeyHeld)
		{
			return null;
		}

		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return null;
		}

		int mouseX = mouse.getX();
		int mouseY = mouse.getY();

		boolean mouseMoved = Math.abs(mouseX - lastMouseX) > MOVE_THRESHOLD
			|| Math.abs(mouseY - lastMouseY) > MOVE_THRESHOLD;

		if (!mouseMoved && cachedTooltip != null && cachedWidget != null)
		{
			Rectangle widgetBounds = cachedWidget.getBounds();
			if (widgetBounds != null && widgetBounds.contains(mouseX, mouseY))
			{
				drawTooltip(g, cachedTooltip, mouseX, mouseY);
				return null;
			}
		}

		lastMouseX = mouseX;
		lastMouseY = mouseY;

		Widget hoveredLine = findHoveredChatLine(mouseX, mouseY);
		if (hoveredLine == null)
		{
			cachedWidget = null;
			cachedTooltip = null;
			return null;
		}

		String lineText = hoveredLine.getText();
		if (lineText == null || !lineText.contains("<col="))
		{
			cachedWidget = null;
			cachedTooltip = null;
			return null;
		}

		Font font = FontManager.getRunescapeSmallFont().deriveFont((float) config.tooltipFontSizePx());
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();

		TooltipData tooltip = findHoveredItem(lineText, hoveredLine, mouseX, fm);

		cachedWidget = hoveredLine;
		cachedTooltip = tooltip;

		if (tooltip != null)
		{
			drawTooltip(g, tooltip, mouseX, mouseY);
		}
		return null;
	}

// ---------------------------------------------------------------
// Item detection
// ---------------------------------------------------------------

	private TooltipData findHoveredItem(String text, Widget widget, int mouseX, FontMetrics fm)
	{
		Rectangle bounds = widget.getBounds();
		if (bounds == null)
		{
			return null;
		}

		final int originX = bounds.x + 2;

		List<CandidateSpan> itemCandidates = new ArrayList<>();
		Matcher colorTagMatcher = COL_ITEM_PATTERN.matcher(text);
		while (colorTagMatcher.find())
		{
			String candidateName = colorTagMatcher.group(2).trim();
			if (!candidateName.matches(".*[a-zA-Z]{2,}.*"))
			{
				continue;
			}

			// Strip leading "N x " quantity prefix (e.g. "14 x black knife")
			candidateName = candidateName.replaceAll("^\\d+\\s+x\\s+", "").trim();
			// Strip trailing " xN" suffix
			candidateName = candidateName.replaceAll("\\s+x\\d+$", "").trim();

			if (isFiltered(candidateName))
			{
				continue;
			}

			TooltipData resolvedData = resolveByName(candidateName);
			if (resolvedData != null)
			{
				itemCandidates.add(new CandidateSpan(colorTagMatcher.start(), colorTagMatcher.end(), candidateName, resolvedData));
			}
		}

		if (itemCandidates.isEmpty())
		{
			return null;
		}
		if (itemCandidates.size() == 1)
		{
			return itemCandidates.get(0).data;
		}

		// Multiple items: measure pixel position and pick closest to mouseX
		int cursorX = 0;
		int pos = 0;
		int[] spanCursorX = new int[itemCandidates.size()];
		int candidateIdx = 0;

		while (pos < text.length() && candidateIdx < itemCandidates.size())
		{
			while (candidateIdx < itemCandidates.size()
				&& pos == itemCandidates.get(candidateIdx).startIdx)
			{
				spanCursorX[candidateIdx] = cursorX;
				candidateIdx++;
			}
			if (candidateIdx >= itemCandidates.size())
			{
				break;
			}

			char ch = text.charAt(pos);
			if (ch != '<')
			{
				cursorX += fm.charWidth(ch);
				pos++;
				continue;
			}

			int tagEnd = text.indexOf('>', pos);
			if (tagEnd == -1)
			{
				pos++;
				continue;
			}

			String tagLower = text.substring(pos + 1, tagEnd).trim().toLowerCase();
			if (tagLower.startsWith("img="))
			{
				cursorX += fm.getHeight();
			}
			else if (tagLower.startsWith("col="))
			{
				boolean isItemSpan = false;
				for (CandidateSpan span : itemCandidates)
				{
					if (span.startIdx == pos)
					{
						isItemSpan = true;
						break;
					}
				}

				if (isItemSpan)
				{
					int depth = 1;
					int j = tagEnd + 1;
					while (j < text.length() && depth > 0)
					{
						if (text.regionMatches(true, j, "<col=", 0, 5))
						{
							depth++;
						}
						else if (text.regionMatches(true, j, "</col>", 0, 6))
						{
							depth--;
						}
						if (depth > 0)
						{
							j++;
						}
					}
					String innerText = text.substring(tagEnd + 1, j);
					String displayText = TAG_PATTERN.matcher(innerText).replaceAll("");
					cursorX += fm.stringWidth(displayText);
					pos = j + 6;
					continue;
				}
			}
			pos = tagEnd + 1;
		}

		for (int k = candidateIdx; k < itemCandidates.size(); k++)
		{
			spanCursorX[k] = cursorX;
		}

		TooltipData closestItem = null;
		int closestDistance = Integer.MAX_VALUE;
		for (int k = 0; k < itemCandidates.size(); k++)
		{
			CandidateSpan span = itemCandidates.get(k);
			int spanWidth = fm.stringWidth(span.name);
			int spanMidX = originX + spanCursorX[k] + spanWidth / 2;
			int distance = Math.abs(mouseX - spanMidX);
			if (distance < closestDistance)
			{
				closestDistance = distance;
				closestItem = span.data;
			}
		}
		return closestItem;
	}

	/** Returns true if the item name is in the user's filter list. */
	private boolean isFiltered(String itemName)
	{
		String filter = config.tooltipFilter();
		if (filter == null || filter.trim().isEmpty())
		{
			return false;
		}

		for (String filterEntry : filter.split(","))
		{
			if (filterEntry.trim().equalsIgnoreCase(itemName.trim()))
			{
				return true;
			}
		}
		return false;
	}

	private static class CandidateSpan
	{
		final int startIdx;
		final String name;
		final TooltipData data;

		CandidateSpan(int startIndex, int endIndex, String itemName, TooltipData tooltipData)
		{
			this.startIdx = startIndex;
			this.name = itemName;
			this.data = tooltipData;
		}
	}

	private TooltipData resolveByName(String name)
	{
		String nameLower = name.toLowerCase();
		for (TooltipData cachedData : itemCache.values())
		{
			if (cachedData.name != null && cachedData.name.equalsIgnoreCase(nameLower))
			{
				return cachedData;
			}
		}

		// Check alias map: allow abbreviations like "tbow", "bcp", "bp" etc.
		String canonicalName = ItemAliases.MAP.get(nameLower);
		String lookupName = (canonicalName != null) ? canonicalName : name;

		int itemId = lookupIdByName(lookupName);
		if (itemId < 0)
		{
			return null;
		}

		cacheItem(itemId);
		TooltipData resolved = itemCache.get(itemId);

		// Reject NPC/entity items that are not real player items.
		// Real items satisfy at least one of: tradeable, has HA value, has GE price, or is equipable.
		// Entity items ("Man", "Run", etc.) satisfy none of these.
		if (resolved != null
			&& !resolved.isTradeable
			&& resolved.gePrice <= 0
			&& resolved.haPrice <= 0
			&& !resolved.isEquipable)
		{
			return null;
		}
		return resolved;
	}

// ---------------------------------------------------------------
// Widget walking
// ---------------------------------------------------------------

	private Widget findHoveredChatLine(int mouseX, int mouseY)
	{
		for (int childIndex = 0; childIndex < 256; childIndex++)
		{
			Widget widget = client.getWidget(InterfaceID.CHATBOX, childIndex);
			if (widget == null || widget.isHidden())
			{
				continue;
			}
			Widget found = checkWidget(widget, mouseX, mouseY);
			if (found != null)
			{
				return found;
			}
		}

		for (int childIndex = 0; childIndex < 32; childIndex++)
		{
			Widget widget = client.getWidget(InterfaceID.PM_CHAT, childIndex);
			if (widget == null || widget.isHidden())
			{
				continue;
			}
			Widget found = checkWidget(widget, mouseX, mouseY);
			if (found != null)
			{
				return found;
			}
		}

		return null;
	}

	private Widget checkWidget(Widget widget, int mouseX, int mouseY)
	{
		if (widget == null || widget.isHidden())
		{
			return null;
		}

		Rectangle widgetBounds = widget.getBounds();
		if (widgetBounds == null || !widgetBounds.contains(mouseX, mouseY))
		{
			return null;
		}

		String widgetText = widget.getText();
		if (widgetText != null && widgetText.contains("<col="))
		{
			Matcher colorMatcher = COL_ITEM_PATTERN.matcher(widgetText);
			while (colorMatcher.find())
			{
				if (colorMatcher.group(2).matches(".*[a-zA-Z].*"))
				{
					return widget;
				}
			}
		}

		return checkChildren(widget, mouseX, mouseY);
	}

	private Widget checkChildren(Widget parent, int mouseX, int mouseY)
	{
		if (parent == null || parent.isHidden())
		{
			return null;
		}

		Widget[][] childGroups = {
			parent.getDynamicChildren(),
			parent.getStaticChildren(),
			parent.getNestedChildren()
		};

		for (Widget[] group : childGroups)
		{
			if (group == null)
			{
				continue;
			}
			for (Widget child : group)
			{
				Widget found = checkWidget(child, mouseX, mouseY);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

// ---------------------------------------------------------------
// Item ID lookup
// ---------------------------------------------------------------

	private int lookupIdByName(String itemName)
	{
		List<net.runelite.http.api.item.ItemPrice> searchResults = itemManager.search(itemName);
		if (searchResults == null || searchResults.isEmpty())
		{
			return -1;
		}

		// Exact match only — never fall back to a fuzzy result.
		// Fuzzy fallback caused e.g. "Yes" → "Jars of eyes" and "tbow" → "Shortbow".
		for (net.runelite.http.api.item.ItemPrice itemPrice : searchResults)
		{
			if (itemPrice.getName().equalsIgnoreCase(itemName))
			{
				return itemPrice.getId();
			}
		}
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

	private void drawTooltip(Graphics2D g, TooltipData tooltipData, int mouseX, int mouseY)
	{
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		Font font = FontManager.getRunescapeSmallFont().deriveFont((float) config.tooltipFontSizePx());
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int lineHeight = fm.getHeight() + LINE_GAP;

		List<Line> lines = buildLines(tooltipData, fm);

		// Measure max text width
		int textWidth = 0;
		for (Line line : lines)
		{
			int lineWidth = 0;
			for (Seg segment : line.segs)
			{
				lineWidth += fm.stringWidth(segment.text);
			}
			textWidth = Math.max(textWidth, lineWidth);
		}

		boolean showIcon = config.showTooltipImage();
		BufferedImage icon = showIcon
			? itemManager.getImage(tooltipData.itemId, Math.max(1, tooltipData.quantity), tooltipData.quantity > 1)
			: null;
		int iconAreaWidth = (icon != null) ? ICON_SZ + PAD : 0;
		// Icon overlaps the text area (top-right corner) rather than expanding box width.
		// iconAreaWidth is still used for separator line shortening so they clear the icon.
		int boxWidth = textWidth + PAD * 2;
		int boxHeight = Math.max(
			icon != null ? ICON_SZ + PAD * 2 : 0,
			lines.size() * lineHeight + PAD * 2
		);

		int canvasWidth = client.getCanvasWidth();
		int canvasHeight = client.getCanvasHeight();

		// Horizontal: prefer right of cursor, flip left if it would overflow, then clamp
		int tooltipX = mouseX + 12;
		if (tooltipX + boxWidth > canvasWidth)
		{
			tooltipX = mouseX - boxWidth - 4;
		}
		tooltipX = Math.max(0, Math.min(tooltipX, canvasWidth - boxWidth));

		// Vertical: strictly honour config direction, never flip.
		int tooltipY;
		if (config.tooltipPosition() == TooltipPosition.BELOW)
		{
			tooltipY = mouseY + 18;
			if (tooltipY + boxHeight > canvasHeight)
			{
				tooltipY = canvasHeight - boxHeight;
			}
			if (mouseY + 1 <= canvasHeight - boxHeight)
			{
				tooltipY = Math.max(tooltipY, mouseY + 1);
			}
		}
		else
		{
			tooltipY = mouseY - boxHeight - 4;
			if (tooltipY < 0)
			{
				tooltipY = 0;
			}
			if (mouseY - 1 >= boxHeight)
			{
				tooltipY = Math.min(tooltipY, mouseY - 1);
			}
		}

		// Background with configurable opacity
		int alpha = (int) Math.round(config.overlayOpacity() / 100.0 * 255);
		g.setColor(new Color(20, 20, 20, alpha));
		g.fillRect(tooltipX, tooltipY, boxWidth, boxHeight);
		g.setColor(BORDER_IN);
		g.drawRect(tooltipX, tooltipY, boxWidth - 1, boxHeight - 1);
		g.setColor(BORDER);
		g.drawRect(tooltipX + 1, tooltipY + 1, boxWidth - 3, boxHeight - 3);

		// Item icon (right side)
		if (icon != null)
		{
			int slotX = tooltipX + boxWidth - PAD - ICON_SZ - 1;
			int slotY = tooltipY + PAD - 1;
			g.setColor(new Color(30, 30, 30));
			g.fillRect(slotX, slotY, ICON_SZ + 2, ICON_SZ + 2);
			g.setColor(new Color(60, 60, 60));
			g.drawRect(slotX, slotY, ICON_SZ + 1, ICON_SZ + 1);
			int iconX = slotX + 1 + (ICON_SZ - icon.getWidth()) / 2;
			int iconY = slotY + 1 + (ICON_SZ - icon.getHeight()) / 2;
			g.drawImage(icon, iconX, iconY, null);
		}

		// Text lines
		int lineX = tooltipX + PAD;
		int lineY = tooltipY + PAD;
		for (Line line : lines)
		{
			if (line.isSeparator)
			{
				int separatorY = lineY + lineHeight / 2;
				g.setColor(COL_SEP);
				g.drawLine(lineX, separatorY, tooltipX + boxWidth - PAD - iconAreaWidth - 1, separatorY);
				lineY += lineHeight;
				continue;
			}

			int xOffset = 0;
			int centerY = lineY + lineHeight / 2;
			int baselineY = centerY + (fm.getAscent() - fm.getDescent()) / 2;

			for (Seg segment : line.segs)
			{
				g.setColor(segment.color);
				g.drawString(segment.text, lineX + xOffset, baselineY);
				xOffset += fm.stringWidth(segment.text);
			}
			lineY += lineHeight;
		}
	}

// ---------------------------------------------------------------
// Line builder
// ---------------------------------------------------------------

	private List<Line> buildLines(TooltipData tooltipData, FontMetrics fm)
	{
		Color rarityColor = parseHex(getRarityColor(tooltipData.gePrice, config));
		List<Line> lines = new ArrayList<>();

		// Name line
		Line nameLine = new Line();
		nameLine.addText(tooltipData.name, rarityColor);
		if (tooltipData.quantity > 1)
		{
			nameLine.addText(" x" + QuantityFormatter.formatNumber(tooltipData.quantity), COL_LABEL);
		}
		if (tooltipData.members)
		{
			nameLine.addText(" (P2P)", COL_MEMBER);
		}
		if (tooltipData.isTradeable)
		{
			nameLine.addText(" (Tradeable)", COL_POS);
		}
		else
		{
			nameLine.addText(" (Untradeable)", COL_NEG);
		}
		lines.add(nameLine);

		boolean showPrices = config.showTooltipPrices();
		boolean showStats  = config.showTooltipStats();
		boolean showWeight = config.showTooltipWeight();

		boolean hasEquipment    = tooltipData.isEquipable && tooltipData.equipmentStats != null;
		boolean hasPriceContent = showPrices && (tooltipData.gePrice > 0 || tooltipData.haPrice > 0);
		boolean hasStatsContent = showStats && hasEquipment;
		boolean hasWeightContent = showWeight && hasEquipment;

		// Separator after name only if something follows
		if (hasPriceContent || hasStatsContent || hasWeightContent)
		{
			lines.add(Line.separator());
		}

		int quantity = Math.max(1, tooltipData.quantity);

		// Grand Exchange price
		if (showPrices && tooltipData.gePrice > 0)
		{
			long totalGe = (long) tooltipData.gePrice * quantity;
			Line geLine = new Line();
			geLine.addText("GE: ", COL_LABEL);
			geLine.addText(QuantityFormatter.quantityToStackSize(totalGe) + " gp", COL_GOLD);
			if (tooltipData.quantity > 1)
			{
				geLine.addText("  (" + QuantityFormatter.quantityToStackSize(tooltipData.gePrice) + " ea)", COL_VALUE);
			}
			lines.add(geLine);
		}

		// High Alch price
		if (showPrices && tooltipData.haPrice > 0)
		{
			long totalHa = (long) tooltipData.haPrice * quantity;
			int lowAlchPrice = tooltipData.haPrice * 2 / 3;
			long totalLa = (long) lowAlchPrice * quantity;
			Line haLine = new Line();
			haLine.addText("HA: ", COL_LABEL);
			haLine.addText(QuantityFormatter.quantityToStackSize(totalHa) + " gp", COL_POS);
			haLine.addText("   LA: ", COL_LABEL);
			haLine.addText(QuantityFormatter.quantityToStackSize(totalLa) + " gp", COL_VALUE);
			lines.add(haLine);
		}

		// Separator between prices and stats/weight
		if (hasPriceContent && (hasStatsContent || hasWeightContent))
		{
			lines.add(Line.separator());
		}

		// Equipment stats
		if (showStats && hasEquipment)
		{
			ItemEquipmentStats equipStats = tooltipData.equipmentStats;

			// Slot + attack speed
			Line slotLine = new Line();
			slotLine.addText("Slot: ", COL_LABEL);
			slotLine.addText(slotName(equipStats.getSlot()), COL_WHITE);
			if (equipStats.isTwoHanded())
			{
				slotLine.addText(" (2H)", COL_WHITE);
			}
			if (equipStats.getAspeed() > 0)
			{
				slotLine.addText("  Speed: " + equipStats.getAspeed(), COL_LABEL);
			}
			lines.add(slotLine);

			// Attack bonuses
			boolean hasAttackBonuses = equipStats.getAstab() != 0 || equipStats.getAslash() != 0
				|| equipStats.getAcrush() != 0 || equipStats.getAmagic() != 0 || equipStats.getArange() != 0;
			if (hasAttackBonuses)
			{
				Line header = new Line();
				header.addText("Attack:", COL_LABEL);
				lines.add(header);
				Line statsLine = new Line();
				statsLine.addText("  Stab: ",   COL_LABEL); statsLine.addText(bonus(equipStats.getAstab())  + "  ", bonusColor(equipStats.getAstab()));
				statsLine.addText("Slash: ",    COL_LABEL); statsLine.addText(bonus(equipStats.getAslash()) + "  ", bonusColor(equipStats.getAslash()));
				statsLine.addText("Crush: ",    COL_LABEL); statsLine.addText(bonus(equipStats.getAcrush()) + "  ", bonusColor(equipStats.getAcrush()));
				statsLine.addText("Magic: ",    COL_LABEL); statsLine.addText(bonus(equipStats.getAmagic()) + "  ", bonusColor(equipStats.getAmagic()));
				statsLine.addText("Ranged: ",   COL_LABEL); statsLine.addText(bonus(equipStats.getArange()),         bonusColor(equipStats.getArange()));
				lines.add(statsLine);
			}

			// Defence bonuses
			boolean hasDefenceBonuses = equipStats.getDstab() != 0 || equipStats.getDslash() != 0
				|| equipStats.getDcrush() != 0 || equipStats.getDmagic() != 0 || equipStats.getDrange() != 0;
			if (hasDefenceBonuses)
			{
				Line header = new Line();
				header.addText("Defence:", COL_LABEL);
				lines.add(header);
				Line statsLine = new Line();
				statsLine.addText("  Stab: ",   COL_LABEL); statsLine.addText(bonus(equipStats.getDstab())  + "  ", bonusColor(equipStats.getDstab()));
				statsLine.addText("Slash: ",    COL_LABEL); statsLine.addText(bonus(equipStats.getDslash()) + "  ", bonusColor(equipStats.getDslash()));
				statsLine.addText("Crush: ",    COL_LABEL); statsLine.addText(bonus(equipStats.getDcrush()) + "  ", bonusColor(equipStats.getDcrush()));
				statsLine.addText("Magic: ",    COL_LABEL); statsLine.addText(bonus(equipStats.getDmagic()) + "  ", bonusColor(equipStats.getDmagic()));
				statsLine.addText("Ranged: ",   COL_LABEL); statsLine.addText(bonus(equipStats.getDrange()),         bonusColor(equipStats.getDrange()));
				lines.add(statsLine);
			}

			// Other bonuses
			boolean hasOtherBonuses = equipStats.getStr() != 0 || equipStats.getPrayer() != 0
				|| equipStats.getRstr() != 0 || equipStats.getMdmg() != 0;
			if (hasOtherBonuses)
			{
				Line header = new Line();
				header.addText("Other:", COL_LABEL);
				lines.add(header);
				Line statsLine = new Line();
				if (equipStats.getStr()    != 0) { statsLine.addText("  Str: ",        COL_LABEL); statsLine.addText(bonus(equipStats.getStr())              + "  ", bonusColor(equipStats.getStr())); }
				if (equipStats.getRstr()   != 0) { statsLine.addText("  Ranged str: ", COL_LABEL); statsLine.addText(bonus(equipStats.getRstr())             + "  ", bonusColor(equipStats.getRstr())); }
				if (equipStats.getMdmg()   != 0) { statsLine.addText("  Magic dmg: ",  COL_LABEL); statsLine.addText(bonus((int) equipStats.getMdmg()) + "%  ", bonusColor((int) equipStats.getMdmg())); }
				if (equipStats.getPrayer() != 0) { statsLine.addText("  Prayer: ",     COL_LABEL); statsLine.addText(bonus(equipStats.getPrayer()),           bonusColor(equipStats.getPrayer())); }
				lines.add(statsLine);
			}
		}

		// Weight (independent of stats toggle, but only for equippable items)
		if (showWeight && hasEquipment)
		{
			Line weightLine = new Line();
			weightLine.addText("Weight: ", COL_LABEL);
			weightLine.addText(String.format("%.2f", tooltipData.weight) + " kg", COL_WHITE);
			lines.add(weightLine);
		}

		return lines;
	}

	private static String slotName(int slotIndex)
	{
		switch (slotIndex)
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

	private static String bonus(int value)
	{
		return value >= 0 ? "+" + value : String.valueOf(value);
	}

	private static Color bonusColor(int value)
	{
		if (value > 0) return COL_POS;
		if (value < 0) return COL_NEG;
		return COL_VALUE;
	}

	static String getRarityColor(int grandExchangePrice, ChatItemConfig config)
	{
		if (grandExchangePrice >= 10_000_000) return colorToHex(config.colorLegendary());
		if (grandExchangePrice >=  1_000_000) return colorToHex(config.colorEpic());
		if (grandExchangePrice >=    100_000) return colorToHex(config.colorRare());
		if (grandExchangePrice >=     10_000) return colorToHex(config.colorUncommon());
		return colorToHex(config.colorCommon());
	}

	private static String colorToHex(java.awt.Color color)
	{
		return String.format("%06x", color.getRGB() & 0xFFFFFF);
	}

	private static Color parseHex(String hex)
	{
		try
		{
			return new Color(Integer.parseInt(hex, 16));
		}
		catch (Exception e)
		{
			return Color.WHITE;
		}
	}

// ---------------------------------------------------------------
// Inner types
// ---------------------------------------------------------------

	private static class Line
	{
		final List<Seg> segs = new ArrayList<>();
		final boolean isSeparator;

		Line()
		{
			isSeparator = false;
		}

		private Line(boolean separator)
		{
			isSeparator = separator;
		}

		static Line separator()
		{
			return new Line(true);
		}

		Line addText(String text, Color color)
		{
			segs.add(new Seg(text, color));
			return this;
		}
	}

	private static class Seg
	{
		final String text;
		final Color color;

		Seg(String text, Color color)
		{
			this.text = text;
			this.color = color;
		}
	}

	static class TooltipData
	{
		int itemId;
		String name;
		int gePrice;
		int haPrice;
		boolean members;
		boolean isTradeable;
		int quantity = 1;
		String colHex;
		boolean isEquipable;
		double weight;
		ItemEquipmentStats equipmentStats;
	}
}
