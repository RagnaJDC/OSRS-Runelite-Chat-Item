package com.chatitem;

import com.google.inject.Provides;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;
import net.runelite.http.api.item.ItemPrice;

@Slf4j
@PluginDescriptor(
name = "Chat Item Pricer & Tooltips",
description = "Shows GE and/or High Alch prices on drop, clue, and collection log notifications. Highlights item names in chat with hover tooltips showing item stats and prices.",
tags = {"chat", "item", "price", "pricer", "tooltip", "drop", "clue", "alch", "ge", "broadcast", "collection", "valuable"}
)
public class ChatItemPlugin extends Plugin
{
	//  Constants

	/** How many item IDs to process per clientThread tick during the scan. */
	private static final int CHUNK_SIZE = 2000;
	/** Upper bound of item IDs to scan. */
	private static final int MAX_ITEM_ID = 30000;

	//  Chat patterns

// group 1 = prefix up to item name, group 2 = quantity (optional), group 3 = item name,
// group 4 = original coin value string, group 5 = anything after ')'
	private static final Pattern DROP_PATTERN = Pattern.compile(
		"^(.*received a drop: )(\\d+)\\s+x\\s+(.+?)\\s*\\((.+?) coins\\)(.*)$"
			+ "|(.*received a drop: )(.+?)\\s*\\((.+?) coins\\)(.*)$",
		Pattern.CASE_INSENSITIVE
	);

	private static final Pattern SPECIAL_DROP_PATTERN = Pattern.compile(
		"^(Special drop: (.+))$",
		Pattern.CASE_INSENSITIVE
	);

	private static final Pattern VALUABLE_DROP_PATTERN = Pattern.compile(
		"Valuable drop: ((?:\\d+ x )?(.+?)) \\((\\d{1,3}(?:,\\d{3})*|\\d+) coins?\\)",
		Pattern.CASE_INSENSITIVE
	);

// "X received a new collection log item: Item Name (N/Total)"
	private static final Pattern COLLECTION_LOG_PATTERN = Pattern.compile(
		"^(.*received a new collection log item: )(.+?)( \\(\\d+/\\d+\\).*)$",
		Pattern.CASE_INSENSITIVE
	);

// "X received a clue item: Item Name (1,234,567 coins)."
	private static final Pattern CLUE_PATTERN = Pattern.compile(
		"^(.*received a clue item: )(.+?)\\s*\\((.+?) coins\\)(.*)$",
		Pattern.CASE_INSENSITIVE
	);

	//  Dependencies

	@Inject private Client client;
	@Inject private ChatItemConfig config;
	@Inject private ItemManager itemManager;
	@Inject private ClientThread clientThread;
	@Inject private OverlayManager overlayManager;
	@Inject private ChatItemOverlay overlay;

	//  State

/**
 * itemName (lower-case) -> item ID
 * Populated by the optional full-scan on login (enableChatScan).
 */
	private final Map<String, Integer> itemNameToId = new HashMap<>();

/**
 * First-word index for multi-word item names, used for fast word-boundary
 * scanning in player chat messages.
 * firstWord -> list of full item names that start with that word.
 */
	private final Map<String, List<String>> firstWordIndex = new HashMap<>();

	/** True once loadItemNames() has finished. */
	private volatile boolean itemsLoaded = false;

	//  Lifecycle

@Override
protected void startUp()
{
	overlayManager.add(overlay);
	overlay.registerHotkey();

	clientThread.invokeLater(() ->
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			if (config.enableChatScan())
			{
				loadItemNames();
			}
		}
	});
}

@Override
protected void shutDown()
{
	overlayManager.remove(overlay);
	overlay.unregisterHotkey();
	overlay.clearCache();
	itemNameToId.clear();
	firstWordIndex.clear();
	itemsLoaded = false;
}

	//  Events

@Subscribe
public void onGameStateChanged(GameStateChanged event)
{
	if (event.getGameState() == GameState.LOGGED_IN)
	{
		if (config.enableChatScan() && !itemsLoaded)
		{
			clientThread.invokeLater(this::loadItemNames);
		}
	}
	else if (event.getGameState() == GameState.LOGIN_SCREEN)
	{
		itemsLoaded = false;
		itemNameToId.clear();
		firstWordIndex.clear();
	}
}

@Subscribe
public void onConfigChanged(ConfigChanged event)
{
	if (!event.getGroup().equals("clanalch"))
	{
		return;
	}
}

@Subscribe
public void onMenuOpened(MenuOpened event)
{
	if (!config.enableWikiClick())
	{
		return;
	}

	ChatItemOverlay.TooltipData tooltipData = overlay.getHoveredItem();
	if (tooltipData == null || tooltipData.name == null)
	{
		return;
	}

	final String itemName = tooltipData.name;
	MenuEntry entry = client.getMenu().createMenuEntry(-1);
	entry.setOption("Lookup on Wiki");
	entry.setTarget("<col=ff9040>" + itemName + "</col>");
	entry.setType(MenuAction.RUNELITE);
	entry.onClick(e -> ChatItemOverlay.openWiki(itemName));
}

@Subscribe
public void onChatMessage(ChatMessage event)
{
	ChatMessageType type = event.getType();

	//  1. Personal "Valuable drop" game message
	if (type == ChatMessageType.GAMEMESSAGE)
	{
		if (config.enablePersonalDrops())
		{
			handlePersonalDrop(event);
		}
		return;
	}

	//  2. Clan / Friends chat drop broadcasts
	boolean isFriendsBroadcast = type == ChatMessageType.FRIENDSCHATNOTIFICATION;
	boolean isClanBroadcast = type == ChatMessageType.CLAN_MESSAGE
		|| type == ChatMessageType.CLAN_GUEST_MESSAGE;

	if ((isFriendsBroadcast && config.replaceFriendsChatDrops())
		|| (isClanBroadcast && config.replaceClanChatDrops()))
	{
		String rawMessage = Text.removeTags(event.getMessage());
		String replacedMessage = tryReplaceDropValue(rawMessage, type);
		if (replacedMessage != null)
		{
			event.getMessageNode().setValue(replacedMessage);
			return;
		}

		String clueMessage = tryColourClueItem(rawMessage, type);
		if (clueMessage != null)
		{
			event.getMessageNode().setValue(clueMessage);
			return;
		}

		String collectionLogMessage = tryColourCollectionLog(rawMessage, type);
		if (collectionLogMessage != null)
		{
			event.getMessageNode().setValue(collectionLogMessage);
		}
		return;
	}

	// Clue/collection log notifications outside drop-replacement config --
	// still colour the item name for tooltips
	if (isClanBroadcast || isFriendsBroadcast)
	{
		String rawMessage = Text.removeTags(event.getMessage());
		String clueMessage = tryColourClueItem(rawMessage, type);
		if (clueMessage != null)
		{
			event.getMessageNode().setValue(clueMessage);
			return;
		}

		String collectionLogMessage = tryColourCollectionLog(rawMessage, type);
		if (collectionLogMessage != null)
		{
			event.getMessageNode().setValue(collectionLogMessage);
		}
		return;
	}

	// 3. Free-form player chat  (chat scan mode)
	if (!config.enableChatScan() || !itemsLoaded)
	{
		return;
	}

	switch (type)
	{
		case PUBLICCHAT:
		case MODCHAT:
		case AUTOTYPER:
		case MODAUTOTYPER:
		case PRIVATECHAT:
		case PRIVATECHATOUT:
		case MODPRIVATECHAT:
		case FRIENDSCHAT:
		case FRIENDSCHATNOTIFICATION:
		case CLAN_CHAT:
		case CLAN_MESSAGE:
		case CLAN_GUEST_CHAT:
		case CLAN_GUEST_MESSAGE:
		case CLAN_GIM_CHAT:
		case CLAN_GIM_MESSAGE:
			break;
		default:
			return;
	}

	String message = event.getMessage();
	if (message == null || message.isEmpty())
	{
		return;
	}

	String highlightedMessage = highlightItemNames(message, chatDefaultColour(type));
	if (!highlightedMessage.equals(message))
	{
		event.getMessageNode().setValue(highlightedMessage);
	}
}

//  Personal drop handling

private void handlePersonalDrop(ChatMessage event)
{
	String rawMessage = Text.removeTags(event.getMessageNode().getValue());
	Matcher valuableDropMatcher = VALUABLE_DROP_PATTERN.matcher(rawMessage);
	if (!valuableDropMatcher.find())
	{
		return;
	}

	String quantityLabel = valuableDropMatcher.group(1);
	String itemName = valuableDropMatcher.group(2).trim();

	int quantity = 1;
	if (quantityLabel != null && quantityLabel.contains(" x "))
	{
		try
		{
			quantity = Integer.parseInt(quantityLabel.split(" x ")[0].trim());
		}
		catch (NumberFormatException ignored)
		{
		}
	}

	int itemId = lookupItemId(itemName);
	if (itemId < 0)
	{
		return;
	}

	int grandExchangeValue = itemManager.getItemPrice(itemId);
	int highAlchValue = itemManager.getItemComposition(itemId).getHaPrice();

	if (isCoins(itemName))
	{
		grandExchangeValue = 1;
		highAlchValue = 1;
	}

	int totalGrandExchangeValue = grandExchangeValue * quantity;
	int totalHighAlchValue = highAlchValue * quantity;

	// Always colour the item name. The game already filtered by the valuable drop
	// threshold, so always show the price.
	String itemColorHex = ChatItemOverlay.getRarityColor(
		totalGrandExchangeValue > 0 ? totalGrandExchangeValue : totalHighAlchValue,
		config
	);
	String quantityPrefix = (quantity > 1) ? quantity + " x " : "";
	String coloredItemName = quantityPrefix + "<col=" + itemColorHex + ">" + itemName + "</col>";

	String messageColorHex = ColorUtil.colorToHexCode(config.messageColour());
	String priceSuffix = (totalGrandExchangeValue > 0 || totalHighAlchValue > 0)
		? " " + buildValueSuffix(itemName, totalHighAlchValue, totalGrandExchangeValue)
		: "";
	String updatedMessage = "<col=" + messageColorHex + ">Valuable drop: </col>"
		+ coloredItemName
		+ "<col=" + messageColorHex + ">" + priceSuffix + "</col>";

	event.getMessageNode().setValue(updatedMessage);
	overlay.cacheItem(itemId);
}

/** Returns just the value portion, e.g. "(GE: 3,072)" -- no item name. */
private String buildValueSuffix(String itemName, int haValue, int geValue)
{
	boolean isCoinDrop = isCoins(itemName);
	DropPriceDisplayType displayType = config.displayPrices();

	String highAlchValueText = formatValue(haValue);
	String grandExchangeValueText = formatValue(geValue);

	String result;
	switch (displayType)
	{
		case GRAND_EXCHANGE:
			result = isCoinDrop ? "(" + grandExchangeValueText + ")" : "(GE: " + grandExchangeValueText + ")";
			break;
		case HIGH_ALCH:
			result = isCoinDrop ? "(" + highAlchValueText + ")" : "(HA: " + highAlchValueText + ")";
			break;
		case BOTH:
		default:
			if (haValue <= 0)
			{
				result = isCoinDrop ? "(" + grandExchangeValueText + ")" : "(GE: " + grandExchangeValueText + ")";
			}
			else if (geValue <= 0)
			{
				result = isCoinDrop ? "(" + highAlchValueText + ")" : "(HA: " + highAlchValueText + ")";
			}
			else
			{
				result = isCoinDrop ? "(" + grandExchangeValueText + ")"
					: "(GE: " + grandExchangeValueText + ", HA: " + highAlchValueText + ")";
			}
			break;
	}
	return result;
}

/**
 * Builds the value parenthetical for clan/friends chat drop broadcasts.
 * Uses the broadcastDisplayPrices config. origCoins is the raw coin string
 * from the game message (used as GE fallback when geValue is 0).
 */
private String buildBroadcastSuffix(String itemName, int haValue, int geValue, String origCoins)
{
	boolean coins = isCoins(itemName);
	DropPriceDisplayType display = config.broadcastDisplayPrices();
	String haStr = formatBroadcastValue(haValue);
	String geStr = geValue > 0 ? formatBroadcastValue(geValue) : (origCoins != null ? origCoins : "");

	if (haValue <= 0 && geValue <= 0)
	{
		// Nothing to show — keep original value if available
		return origCoins != null ? " (" + origCoins + " coins)" : "";
	}

	String result;
	switch (display)
	{
		case GRAND_EXCHANGE:
			if (geValue <= 0 && origCoins == null) result = haValue > 0 ? " (" + haStr + " coins HA)" : "";
			else result = coins ? " (" + geStr + ")" : " (" + geStr + " coins GE)";
			break;
		case HIGH_ALCH:
			if (haValue <= 0) result = origCoins != null ? " (" + origCoins + " coins)" : "";
			else result = coins ? " (" + haStr + ")" : " (" + haStr + " coins HA)";
			break;
		case BOTH:
		default:
			if (haValue <= 0) result = coins ? " (" + geStr + ")" : " (" + geStr + " coins GE)";
			else if (geValue <= 0 && origCoins == null) result = coins ? " (" + haStr + ")" : " (" + haStr + " coins HA)";
			else result = coins ? " (" + geStr + ")" : " (GE: " + geStr + " / HA: " + haStr + " coins)";
			break;
	}
	return result;
}

private static int parseQty(String s)
{
	try
	{
		return s != null ? Integer.parseInt(s.trim()) : 1;
	}
	catch (NumberFormatException e)
	{
		return 1;
	}
}

//  Clan / Friends chat drop replacement

/**
 * Colours the item name in clue item notifications and replaces the coin value.
 * Input: "Maxions received a clue item: Occult ornament kit (3,630,898 coins)."
 */
private String tryColourClueItem(String message, ChatMessageType type)
{
	Matcher clueMatcher = CLUE_PATTERN.matcher(message);
	if (!clueMatcher.matches())
	{
		return null;
	}

	String prefix = clueMatcher.group(1); // "Maxions received a clue item: "
	String itemName = clueMatcher.group(2).trim();
	String originalCoins = clueMatcher.group(3);
	String trailingText = clueMatcher.group(4); // usually "." or empty

	int itemId = lookupItemId(itemName);
	int grandExchangeValue = itemId >= 0 ? itemManager.getItemPrice(itemId) : 0;
	int highAlchValue = itemId >= 0 ? itemManager.getItemComposition(itemId).getHaPrice() : 0;
	if (itemId >= 0)
	{
		overlay.cacheItem(itemId);
	}

	String itemColorHex = itemId >= 0
		? ChatItemOverlay.getRarityColor(grandExchangeValue > 0 ? grandExchangeValue : highAlchValue, config)
		: ChatItemOverlay.getRarityColor(0, config);
	String chatColorHex = chatDefaultColour(type);

	String valueSuffix = buildBroadcastSuffix(itemName, highAlchValue, grandExchangeValue, originalCoins);
	return "<col=" + chatColorHex + ">" + prefix
		+ "<col=" + itemColorHex + ">" + itemName + "</col>"
		+ "<col=" + chatColorHex + ">" + valueSuffix + trailingText + "</col>";
}

/**
 * Colours the item name in collection log notifications so the hover
 * tooltip can detect it. Returns the modified message or null if not matched.
 * Input: "HolyDab received a new collection log item: Bronze Boots (87/1698)"
 */
private String tryColourCollectionLog(String message, ChatMessageType type)
{
	Matcher collectionLogMatcher = COLLECTION_LOG_PATTERN.matcher(message);
	if (!collectionLogMatcher.matches())
	{
		return null;
	}

	String prefix = collectionLogMatcher.group(1); // "HolyDab received a new collection log item: "
	String itemName = collectionLogMatcher.group(2).trim(); // "Bronze Boots"
	String suffix = collectionLogMatcher.group(3); // " (87/1698)"

	int itemId = lookupItemId(itemName);
	int grandExchangeValue = itemId >= 0 ? itemManager.getItemPrice(itemId) : 0;
	int highAlchValue = itemId >= 0 ? itemManager.getItemComposition(itemId).getHaPrice() : 0;
	String itemColorHex = ChatItemOverlay.getRarityColor(grandExchangeValue > 0 ? grandExchangeValue : highAlchValue, config);
	if (itemId >= 0)
	{
		overlay.cacheItem(itemId);
	}

	String chatColorHex = chatDefaultColour(type);
	String priceSuffix = (grandExchangeValue > 0 || highAlchValue > 0)
		? " " + buildBroadcastSuffix(itemName, highAlchValue, grandExchangeValue, null)
		: "";

	return "<col=" + chatColorHex + ">" + prefix
		+ "<col=" + itemColorHex + ">" + itemName + "</col>"
		+ "<col=" + chatColorHex + ">" + suffix + priceSuffix + "</col>";
}

private String tryReplaceDropValue(String message, ChatMessageType type)
{
	String chatColorHex = chatDefaultColour(type);
	Matcher dropMatcher = DROP_PATTERN.matcher(message);
	if (dropMatcher.matches())
	{
		// The pattern has two alternatives:
		// Alt 1 (with quantity): groups 1-5  prefix | qty | item | coins | trailing
		// Alt 2 (no quantity):   groups 6-10 prefix | --- | item | coins | trailing
		boolean hasQuantity = dropMatcher.group(1) != null;
		String beforeItemName = hasQuantity ? dropMatcher.group(1) : dropMatcher.group(6);
		int quantity = hasQuantity ? parseQty(dropMatcher.group(2)) : 1;
		String itemName = (hasQuantity ? dropMatcher.group(3) : dropMatcher.group(7)).trim();
		String originalCoins = hasQuantity ? dropMatcher.group(4) : dropMatcher.group(8);
		String suffix = hasQuantity ? dropMatcher.group(5) : dropMatcher.group(9);

		int itemId = lookupItemId(itemName);
		int highAlchUnitValue = itemId >= 0 ? itemManager.getItemComposition(itemId).getHaPrice() : 0;
		int grandExchangeUnitValue = itemId >= 0 ? itemManager.getItemPrice(itemId) : 0;
		int highAlchTotalValue = highAlchUnitValue * quantity;
		int grandExchangeTotalValue = grandExchangeUnitValue * quantity;

		if (highAlchUnitValue <= 0 && grandExchangeUnitValue <= 0)
		{
			return null;
		}

		String itemColorHex = ChatItemOverlay.getRarityColor(
			grandExchangeTotalValue > 0 ? grandExchangeTotalValue : highAlchTotalValue,
			config
		);

		// Show "3 x ItemName" for multi-quantity drops
		String quantityPrefix = quantity > 1 ? quantity + " x " : "";
		String coloredMessage = "<col=" + chatColorHex + ">" + beforeItemName
			+ "<col=" + itemColorHex + ">" + quantityPrefix + itemName + "</col>";
		if (itemId >= 0)
		{
			overlay.cacheItem(itemId);
		}

		String valueSuffix = buildBroadcastSuffix(itemName, highAlchTotalValue, grandExchangeTotalValue, originalCoins);
		return coloredMessage + "<col=" + chatColorHex + ">" + valueSuffix + suffix + "</col>";
	}

	Matcher specialDropMatcher = SPECIAL_DROP_PATTERN.matcher(message);
	if (specialDropMatcher.matches())
	{
		String itemName = specialDropMatcher.group(2).trim();
		int itemId = lookupItemId(itemName);
		int highAlchValue = itemId >= 0 ? itemManager.getItemComposition(itemId).getHaPrice() : 0;
		int grandExchangeValue = itemId >= 0 ? itemManager.getItemPrice(itemId) : 0;
		if (highAlchValue <= 0 && grandExchangeValue <= 0)
		{
			return null;
		}

		String itemColorHex = ChatItemOverlay.getRarityColor(grandExchangeValue > 0 ? grandExchangeValue : highAlchValue, config);
		if (itemId >= 0)
		{
			overlay.cacheItem(itemId);
		}

		String specialDropMessage = specialDropMatcher.group(1);
		int itemNameStart = specialDropMessage.lastIndexOf(itemName);
		String beforeItemName = itemNameStart >= 0 ? specialDropMessage.substring(0, itemNameStart) : specialDropMessage;
		String valueSuffix = buildBroadcastSuffix(itemName, highAlchValue, grandExchangeValue, null);

		return "<col=" + chatColorHex + ">" + beforeItemName
			+ "<col=" + itemColorHex + ">" + itemName + "</col>"
			+ "<col=" + chatColorHex + ">" + valueSuffix + "</col>";
	}

	return null;
}

	//  Free-form chat scan

/**
 * Scan a player-typed chat message for item names (word-boundary matched)
 * and wrap them in rarity colour tags.
 */
// Varbit that controls chatbox transparency (Varbits.TRANSPARENT_CHATBOX = 4608)
// 1 = transparent chatbox, 0 = opaque chatbox
	private static final int VARBIT_TRANSPARENT_CHATBOX = 4608;

// OSRS in-game chat colour varplayer IDs (opaque / transparent pairs)
// Values are packed RGB + 1; 0 means unset (use Jagex default).
	private static final int VARP_PUBLIC_OPAQUE      = 2992;
	private static final int VARP_PUBLIC_TRANSP      = 3000;
	private static final int VARP_PRIVATE_OPAQUE     = 2993;
	private static final int VARP_PRIVATE_TRANSP     = 3001;
	private static final int VARP_FRIENDS_OPAQUE     = 2996;
	private static final int VARP_FRIENDS_TRANSP     = 3004;
	private static final int VARP_CLAN_OPAQUE        = 2997;
	private static final int VARP_CLAN_TRANSP        = 3005;
	private static final int VARP_CLAN_BCAST_OPAQUE  = 3192;
	private static final int VARP_CLAN_BCAST_TRANSP  = 3195;
	private static final int VARP_GUEST_OPAQUE       = 3060;
	private static final int VARP_GUEST_TRANSP       = 3061;
	private static final int VARP_GIM_OPAQUE         = 3191;
	private static final int VARP_GIM_TRANSP         = 3194;
	private static final int VARP_GIM_BCAST_OPAQUE   = 3193;
	private static final int VARP_GIM_BCAST_TRANSP   = 3196;

// Jagex default opaque colours (matches JagexColors in RuneLite source)
	private static final String DEFAULT_OPAQUE_PUBLIC  = "0000ff"; // Color.BLUE
	private static final String DEFAULT_OPAQUE_PRIVATE = "7f0000"; // 0x7F0000
	private static final String DEFAULT_OPAQUE_FC      = "7f0000"; // friends/clan/guest
	private static final String DEFAULT_OPAQUE_GAME    = "000000"; // Color.BLACK
// Jagex default transparent colours
	private static final String DEFAULT_TRANSP_PUBLIC  = "9090ff"; // new Color(144,144,255)
	private static final String DEFAULT_TRANSP_PRIVATE = "bf2020"; // 0xBF2020
	private static final String DEFAULT_TRANSP_FC      = "ef5050"; // new Color(239,80,80)
	private static final String DEFAULT_TRANSP_GAME    = "ffffff"; // Color.WHITE

/**
 * Returns the hex colour string for the given chat type by reading the
 * in-game chat colour varplayer (OSRS Settings → Chat).
 * The varplayer stores packed RGB + 1; 0 means default/unset.
 */
private String chatDefaultColour(ChatMessageType type)
{
	boolean transparent = client.isResized() && client.getVarbitValue(VARBIT_TRANSPARENT_CHATBOX) == 1;

	int varpId;
	String osrsDefault;
	switch (type)
	{
		case PUBLICCHAT:
		case MODCHAT:
		case AUTOTYPER:
		case MODAUTOTYPER:
			varpId = transparent ? VARP_PUBLIC_TRANSP : VARP_PUBLIC_OPAQUE;
			osrsDefault = transparent ? DEFAULT_TRANSP_PUBLIC : DEFAULT_OPAQUE_PUBLIC;
			break;
		case PRIVATECHAT:
		case MODPRIVATECHAT:
		case PRIVATECHATOUT:
			varpId = transparent ? VARP_PRIVATE_TRANSP : VARP_PRIVATE_OPAQUE;
			osrsDefault = transparent ? DEFAULT_TRANSP_PRIVATE : DEFAULT_OPAQUE_PRIVATE;
			break;
		case FRIENDSCHAT:
		case FRIENDSCHATNOTIFICATION:
			varpId = transparent ? VARP_FRIENDS_TRANSP : VARP_FRIENDS_OPAQUE;
			osrsDefault = transparent ? DEFAULT_TRANSP_FC : DEFAULT_OPAQUE_FC;
			break;
		case CLAN_CHAT:
			varpId = transparent ? VARP_CLAN_TRANSP : VARP_CLAN_OPAQUE;
			osrsDefault = transparent ? DEFAULT_TRANSP_FC : DEFAULT_OPAQUE_FC;
			break;
		case CLAN_MESSAGE:
			varpId = transparent ? VARP_CLAN_BCAST_TRANSP : VARP_CLAN_BCAST_OPAQUE;
			osrsDefault = transparent ? DEFAULT_TRANSP_GAME : DEFAULT_OPAQUE_GAME;
			break;
		case CLAN_GUEST_CHAT:
		case CLAN_GUEST_MESSAGE:
			varpId = transparent ? VARP_GUEST_TRANSP : VARP_GUEST_OPAQUE;
			osrsDefault = transparent ? DEFAULT_TRANSP_FC : DEFAULT_OPAQUE_FC;
			break;
		case CLAN_GIM_CHAT:
			varpId = transparent ? VARP_GIM_TRANSP : VARP_GIM_OPAQUE;
			osrsDefault = transparent ? DEFAULT_TRANSP_FC : DEFAULT_OPAQUE_FC;
			break;
		case CLAN_GIM_MESSAGE:
			varpId = transparent ? VARP_GIM_BCAST_TRANSP : VARP_GIM_BCAST_OPAQUE;
			osrsDefault = transparent ? DEFAULT_TRANSP_GAME : DEFAULT_OPAQUE_GAME;
			break;
		default:
			varpId = transparent ? VARP_PUBLIC_TRANSP : VARP_PUBLIC_OPAQUE;
			osrsDefault = transparent ? DEFAULT_TRANSP_PUBLIC : DEFAULT_OPAQUE_PUBLIC;
			break;
	}

	// Varplayer stores packed RGB + 1; 0 means unset
	int v = client.getVarpValue(varpId);
	if (v > 0)
	{
		return String.format("%06x", (v - 1) & 0xFFFFFF);
	}
	return osrsDefault;
}

private String highlightItemNames(String message, String defaultColour)
{
	String messageLower = message.toLowerCase();
	StringBuilder output = new StringBuilder(message.length() + 64);

	// Stack of active colour tags so we can restore them after an item </col>
	Deque<String> colorStack = new ArrayDeque<>();
	// Seed the stack with the chat-type default so it is always non-empty
	colorStack.push(defaultColour);

	int pos = 0;
	while (pos < message.length())
	{
		// Consume any RuneLite tags, keeping the colour stack up to date
		if (message.charAt(pos) == '<')
		{
			int tagEnd = message.indexOf('>', pos);
			if (tagEnd != -1)
			{
				String tag = message.substring(pos, tagEnd + 1);
				String tagLower = tag.toLowerCase();

				if (tagLower.startsWith("<col="))
				{
					String hexColor = tag.substring(5, tag.length() - 1);
					colorStack.push(hexColor);
				}
				else if (tagLower.equals("</col>") && colorStack.size() > 1)
				{
					// Never pop the seeded default
					colorStack.pop();
				}

				output.append(tag);
				pos = tagEnd + 1;
				continue;
			}
		}

		boolean atWordBoundary = (pos == 0 || !Character.isLetterOrDigit(messageLower.charAt(pos - 1)));
		if (!atWordBoundary || !Character.isLetterOrDigit(messageLower.charAt(pos)))
		{
			output.append(message.charAt(pos));
			pos++;
			continue;
		}

		// Extract current word
		int wordBoundary = pos;
		while (wordBoundary < messageLower.length() && Character.isLetterOrDigit(messageLower.charAt(wordBoundary)))
		{
			wordBoundary++;
		}
		String currentWord = messageLower.substring(pos, wordBoundary);

		// Try multi-word names starting with this word
		List<String> nameCandidates = firstWordIndex.get(currentWord);
		String bestMatchName = null;
		int bestMatchLength = 0;

		if (nameCandidates != null)
		{
			for (String candidateName : nameCandidates)
			{
				if (pos + candidateName.length() > messageLower.length())
				{
					continue;
				}

				if (!messageLower.regionMatches(pos, candidateName, 0, candidateName.length()))
				{
					continue;
				}

				int candidateEnd = pos + candidateName.length();
				if (candidateEnd < messageLower.length() && Character.isLetterOrDigit(messageLower.charAt(candidateEnd)))
				{
					continue;
				}

				if (candidateName.length() > bestMatchLength)
				{
					bestMatchName = candidateName;
					bestMatchLength = candidateName.length();
				}
			}
		}

		if (bestMatchName == null && itemNameToId.containsKey(currentWord))
		{
			bestMatchName = currentWord;
			bestMatchLength = currentWord.length();
		}

		if (bestMatchName != null)
		{
			int itemId = itemNameToId.get(bestMatchName);
			int gePrice = itemManager.getItemPrice(itemId);
			String itemColorHex = ChatItemOverlay.getRarityColor(gePrice, config);
			String displayText = message.substring(pos, pos + bestMatchLength);

			// Wrap item in its rarity colour, then restore the previous colour
			String restoreColorHex = colorStack.peek();
			output.append("<col=").append(itemColorHex).append(">").append(displayText)
				.append("</col><col=").append(restoreColorHex).append(">");

			overlay.cacheItem(itemId);
			pos += bestMatchLength;
		}
		else
		{
			output.append(message.charAt(pos));
			pos++;
		}
	}

	return output.toString();
}

//  Item name loading (chunked)

private void loadItemNames()
{
	loadItemChunk(0);
}

private void loadItemChunk(int startId)
{
	clientThread.invokeLater(() ->
	{
		int endId = Math.min(startId + CHUNK_SIZE, MAX_ITEM_ID);
		for (int itemId = startId; itemId < endId; itemId++)
		{
			try
			{
				ItemComposition itemComposition = itemManager.getItemComposition(itemId);
				if (itemComposition == null)
				{
					continue;
				}

				String itemName = itemComposition.getName();
				if (itemName == null || itemName.equals("null") || itemName.isEmpty())
				{
					continue;
				}

				// Skip noted variants and bank placeholders — they share the base item's name
				// and would only create duplicate/conflicting entries.
				if (itemComposition.getNote() != -1)
				{
					continue;
				}
				if (itemComposition.getPlaceholderTemplateId() != -1)
				{
					continue;
				}

				// Skip entity/NPC items that have no real player-item properties.
				// Real untradeable items (Fire cape, quest items, etc.) have haPrice > 0
				// or are equipable. Pure entity items ("Man", "Run", etc.) have none.
				if (!itemComposition.isTradeable() && itemComposition.getHaPrice() == 0)
				{
					ItemStats scanStats = itemManager.getItemStats(itemId);
					if (scanStats == null || !scanStats.isEquipable())
					{
						continue;
					}
				}

				String itemNameLower = itemName.toLowerCase();
				itemNameToId.putIfAbsent(itemNameLower, itemId);

				// Index first word for quick multi-word scanning
				String firstWord = itemNameLower.split("\\s+")[0];
				firstWordIndex.computeIfAbsent(firstWord, key -> new ArrayList<>()).add(itemNameLower);
			}
			catch (Exception ignored)
			{
			}
		}

		if (endId < MAX_ITEM_ID)
		{
			loadItemChunk(endId);
		}
		else
		{
			itemsLoaded = true;

			// Resolve aliases: map each abbreviation to the ID of its canonical name.
			// This lets the chat scanner recognise e.g. "tbow" without re-scanning.
			for (Map.Entry<String, String> entry : ItemAliases.MAP.entrySet())
			{
				String alias = entry.getKey();       // e.g. "tbow"
				String canonicalName = entry.getValue(); // e.g. "twisted bow"

				if (itemNameToId.containsKey(alias))
				{
					continue; // don't overwrite real item names
				}

				Integer canonicalItemId = itemNameToId.get(canonicalName);
				if (canonicalItemId == null)
				{
					continue; // canonical not found in game data
				}

				itemNameToId.put(alias, canonicalItemId);
				String firstWord = alias.split("\\s+")[0];
				firstWordIndex.computeIfAbsent(firstWord, key -> new ArrayList<>()).add(alias);
			}

			log.debug("ClanAlch: item scan complete, {} names loaded", itemNameToId.size());
		}
		return true;
	});
}

//  Item lookup

private int lookupItemId(String itemName)
{
	// Try the pre-built scan map first (free, O(1))
	if (!itemNameToId.isEmpty())
	{
		Integer itemId = itemNameToId.get(itemName.toLowerCase());
		if (itemId != null)
		{
			return itemId;
		}
	}

	// Fall back to ItemManager.search() — exact name match only.
	// Never accept the first fuzzy result: e.g. searching "Dom" must not
	// return "Saradomin Platebody" just because it contains "dom".
	List<ItemPrice> searchResults = itemManager.search(itemName);
	if (searchResults == null || searchResults.isEmpty())
	{
		return -1;
	}

	for (ItemPrice itemPrice : searchResults)
	{
		if (itemPrice.getName().equalsIgnoreCase(itemName))
		{
			return itemPrice.getId();
		}
	}
	return -1;
}

//  Formatting helpers

private String formatValue(int value)
{
	if (config.formatPrices())
	{
		return QuantityFormatter.quantityToRSDecimalStack(value) + " gp";
	}
	return QuantityFormatter.formatNumber(value) + " gp";
}

private String formatBroadcastValue(int value)
{
	if (config.formatBroadcastPrices())
	{
		return QuantityFormatter.quantityToRSDecimalStack(value);
	}
	return String.format("%,d", value);
}

private static boolean isCoins(String name)
{
	return name.equalsIgnoreCase("coins") || name.equalsIgnoreCase("coin");
}

@Provides
ChatItemConfig provideConfig(ConfigManager configManager)
{
	return configManager.getConfig(ChatItemConfig.class);
}
}
