package com.chatitem;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup("clanalch")
public interface ChatItemConfig extends Config
{
	// ---- Clan / Friends Chat ----

	@ConfigSection(
		name = "Clan / Friends Chat",
		description = "Settings for clan and friends chat drop broadcast messages.",
		position = 0
	)
	String sectionClanChat = "clanChat";

	@ConfigItem(
		keyName = "replaceFriendsChatDrops",
		name = "Friends Chat drops",
		description = "Replace GE value with high alch value in Friends Chat drop broadcasts.",
		section = sectionClanChat,
		position = 0
	)
	default boolean replaceFriendsChatDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "replaceClanChatDrops",
		name = "Clan Chat drops",
		description = "Replace GE value with high alch value in Clan Chat drop broadcasts.",
		section = sectionClanChat,
		position = 1
	)
	default boolean replaceClanChatDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "broadcastDisplayPrices",
		name = "Display price",
		description = "Show the High Alch price, GE price, or both in clan/friends chat drop broadcasts.",
		section = sectionClanChat,
		position = 2
	)
	default DropPriceDisplayType broadcastDisplayPrices()
	{
		return DropPriceDisplayType.BOTH;
	}

	@ConfigItem(
		keyName = "formatBroadcastPrices",
		name = "Format prices (1.2M)",
		description = "Show prices as compact notation (e.g. 1.2M) instead of exact numbers (e.g. 1,234,567) in clan/friends chat drop broadcasts.",
		section = sectionClanChat,
		position = 3
	)
	default boolean formatBroadcastPrices()
	{
		return false;
	}

	// ---- Personal Drop Notification ----

	@ConfigSection(
		name = "Personal Drop Notification",
		description = "Settings for your own 'Valuable drop' game messages.",
		position = 1
	)
	String sectionPersonal = "personal";

	@ConfigItem(
		keyName = "enablePersonalDrops",
		name = "Enable personal drops",
		description = "Modify the 'Valuable drop' notification that appears when you receive a drop."
			+ " Requires the Valuable drops notification to be enabled in your in-game settings.",
		section = sectionPersonal,
		position = 0
	)
	default boolean enablePersonalDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayPrices",
		name = "Display price",
		description = "Show the GE price, High Alch price, or both.",
		section = sectionPersonal,
		position = 1
	)
	default DropPriceDisplayType displayPrices()
	{
		return DropPriceDisplayType.BOTH;
	}

	@ConfigItem(
		keyName = "formatPrices",
		name = "Format prices (1.2M)",
		description = "Show prices as compact notation (e.g. 1.2M) instead of exact numbers (e.g. 1,234,567).",
		section = sectionPersonal,
		position = 2
	)
	default boolean formatPrices()
	{
		return false;
	}

	@ConfigItem(
		keyName = "messageColour",
		name = "Message colour",
		description = "Colour used for the personal drop notification text.",
		section = sectionPersonal,
		position = 3
	)
	default Color messageColour()
	{
		return Color.decode("#EF1020");
	}

	// ---- Item Tooltips ----

	@ConfigSection(
		name = "Item Tooltips",
		description = "Hover over item names in chat to see a price tooltip.",
		position = 2
	)
	String sectionTooltips = "tooltips";

	@ConfigItem(
		keyName = "enableTooltips",
		name = "Enable tooltips",
		description = "Show a tooltip with item icon, GE price and alch value when hovering over"
			+ " a highlighted item name in chat.",
		section = sectionTooltips,
		position = 0
	)
	default boolean enableTooltips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableChatScan",
		name = "Highlight items in player chat",
		description = "Scan free-form player chat (public, clan, friends, PMs) for item names"
			+ " and colour them so tooltips work there too."
			+ " Note: loads item names at login (one-time cost).",
		section = sectionTooltips,
		position = 1
	)
	default boolean enableChatScan()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tooltipPosition",
		name = "Tooltip position",
		description = "Whether the tooltip appears above or below the mouse cursor.",
		section = sectionTooltips,
		position = 2
	)
	default TooltipPosition tooltipPosition()
	{
		return TooltipPosition.BELOW;
	}

	@net.runelite.client.config.Range(min = 8, max = 24)
	@ConfigItem(
		keyName = "tooltipFontSizePx",
		name = "Tooltip font size (px)",
		description = "Font size in pixels for tooltip text (8 = very small, 16 = normal, 24 = large).",
		section = sectionTooltips,
		position = 3
	)
	default int tooltipFontSizePx()
	{
		return 16;
	}

	@net.runelite.client.config.Range(min = 10, max = 100)
	@ConfigItem(
		keyName = "overlayOpacity",
		name = "Tooltip opacity %",
		description = "Background opacity of the item tooltip (10 = very transparent, 100 = fully opaque).",
		section = sectionTooltips,
		position = 4
	)
	default int overlayOpacity()
	{
		return 75;
	}

	@ConfigItem(
		keyName = "showTooltipImage",
		name = "Show item image",
		description = "Show the item icon inside the tooltip.",
		section = sectionTooltips,
		position = 5
	)
	default boolean showTooltipImage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTooltipPrices",
		name = "Show prices",
		description = "Show the GE and High Alch prices inside the tooltip.",
		section = sectionTooltips,
		position = 6
	)
	default boolean showTooltipPrices()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTooltipStats",
		name = "Show equipment stats",
		description = "Show attack/defence bonuses and slot info for equippable items.",
		section = sectionTooltips,
		position = 7
	)
	default boolean showTooltipStats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTooltipWeight",
		name = "Show weight",
		description = "Show the item weight for equippable items.",
		section = sectionTooltips,
		position = 8
	)
	default boolean showTooltipWeight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "requireKeyHeld",
		name = "Require key to show tooltip",
		description = "When enabled, the tooltip only appears while the hotkey below is held down."
			+ " Useful for hiding the overlay during combat.",
		section = sectionTooltips,
		position = 9
	)
	default boolean requireKeyHeld()
	{
		return false;
	}

	@ConfigItem(
		keyName = "tooltipKey",
		name = "Tooltip hotkey",
		description = "Hold this key to show the tooltip (only active when Require key to show tooltip is ticked).",
		section = sectionTooltips,
		position = 10
	)
	default Keybind tooltipKey()
	{
		return new Keybind(java.awt.event.KeyEvent.VK_ALT, 0);
	}

	@ConfigItem(
		keyName = "tooltipFilter",
		name = "Filter items (comma-separated)",
		description = "Item names listed here will never show a tooltip. Separate with commas."
			+ " Example: coins,bones,ashes,bronze arrow,feather",
		section = sectionTooltips,
		position = 11
	)
	default String tooltipFilter()
	{
		return "coins";
	}

	@ConfigItem(
		keyName = "enableWikiClick",
		name = "Wiki right-click lookup",
		description = "Adds a 'Lookup on Wiki' option to the right-click menu when hovering over a highlighted item name in chat.",
		section = sectionTooltips,
		position = 12
	)
	default boolean enableWikiClick()
	{
		return true;
	}

	// ---- Item Colours ----

	@ConfigSection(
		name = "Item Colours",
		description = "Colours used to highlight item names in chat based on their GE value.",
		position = 3
	)
	String sectionColours = "itemColours";

	@ConfigItem(
		keyName = "colorCommon",
		name = "Common (< 10k)",
		description = "Colour for items worth less than 10,000 gp.",
		section = sectionColours,
		position = 0
	)
	default Color colorCommon()
	{
		return Color.decode("#ffffff");
	}

	@ConfigItem(
		keyName = "colorUncommon",
		name = "Uncommon (10k+)",
		description = "Colour for items worth 10,000 gp or more.",
		section = sectionColours,
		position = 1
	)
	default Color colorUncommon()
	{
		return Color.decode("#1eff00");
	}

	@ConfigItem(
		keyName = "colorRare",
		name = "Rare (100k+)",
		description = "Colour for items worth 100,000 gp or more.",
		section = sectionColours,
		position = 2
	)
	default Color colorRare()
	{
		return Color.decode("#0070dd");
	}

	@ConfigItem(
		keyName = "colorEpic",
		name = "Epic (1M+)",
		description = "Colour for items worth 1,000,000 gp or more.",
		section = sectionColours,
		position = 3
	)
	default Color colorEpic()
	{
		return Color.decode("#a335ee");
	}

	@ConfigItem(
		keyName = "colorLegendary",
		name = "Legendary (10M+)",
		description = "Colour for items worth 10,000,000 gp or more.",
		section = sectionColours,
		position = 4
	)
	default Color colorLegendary()
	{
		return Color.decode("#ff8000");
	}
}
