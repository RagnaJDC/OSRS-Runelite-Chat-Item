package com.chatitem;

import com.google.inject.Provides;
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
loadItemNames();
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
clientThread.invokeLater(this::loadItemNames);
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
if (!event.getGroup().equals("clanalch")) return;
}

@Subscribe
public void onMenuOpened(MenuOpened event)
{
if (!config.enableWikiClick()) return;
ChatItemOverlay.TooltipData td = overlay.getHoveredItem();
if (td == null || td.name == null) return;
final String name = td.name;
MenuEntry entry = client.getMenu().createMenuEntry(-1);
entry.setOption("Lookup on Wiki");
entry.setTarget("<col=ff9040>" + name + "</col>");
entry.setType(MenuAction.RUNELITE);
entry.onClick(e -> ChatItemOverlay.openWiki(name));
}

@Subscribe
public void onChatMessage(ChatMessage event)
{
ChatMessageType type = event.getType();

//  1. Personal "Valuable drop" game message
if (type == ChatMessageType.GAMEMESSAGE)
{
if (config.enablePersonalDrops())
handlePersonalDrop(event);
return;
}

//  2. Clan / Friends chat drop broadcasts
boolean isFriendsBroadcast = type == ChatMessageType.FRIENDSCHATNOTIFICATION;
boolean isClanBroadcast    = type == ChatMessageType.CLAN_MESSAGE
|| type == ChatMessageType.CLAN_GUEST_MESSAGE;

if ((isFriendsBroadcast && config.replaceFriendsChatDrops())
|| (isClanBroadcast && config.replaceClanChatDrops()))
{
String raw = Text.removeTags(event.getMessage());
String replaced = tryReplaceDropValue(raw, type);
if (replaced != null)
{
event.getMessageNode().setValue(replaced);
return;
}
// Clue item broadcasts
String clue = tryColourClueItem(raw, type);
if (clue != null)
{
event.getMessageNode().setValue(clue);
return;
}
// Collection log notifications
String colLog = tryColourCollectionLog(raw, type);
if (colLog != null)
event.getMessageNode().setValue(colLog);
return;
}

// Clue/collection log notifications outside drop-replacement config --
// still colour the item name for tooltips
if (isClanBroadcast || isFriendsBroadcast)
{
String raw = Text.removeTags(event.getMessage());
String clue = tryColourClueItem(raw, type);
if (clue != null) { event.getMessageNode().setValue(clue); return; }
String colLog = tryColourCollectionLog(raw, type);
if (colLog != null)
event.getMessageNode().setValue(colLog);
return;
}

// 3. Free-form player chat  (chat scan mode)
if (!config.enableChatScan() || !itemsLoaded) return;

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

String msg = event.getMessage();
if (msg == null || msg.isEmpty()) return;
String highlighted = highlightItemNames(msg, chatDefaultColour(type));
if (!highlighted.equals(msg))
event.getMessageNode().setValue(highlighted);
}

//  Personal drop handling

private void handlePersonalDrop(ChatMessage event)
{
String raw = Text.removeTags(event.getMessageNode().getValue());
Matcher m = VALUABLE_DROP_PATTERN.matcher(raw);
if (!m.find()) return;

String quantityLabel = m.group(1);
String itemName      = m.group(2).trim();

int quantity = 1;
if (quantityLabel != null && quantityLabel.contains(" x "))
{
try { quantity = Integer.parseInt(quantityLabel.split(" x ")[0].trim()); }
catch (NumberFormatException ignored) { }
}

int itemId = lookupItemId(itemName);
if (itemId < 0) return;

int geValue = itemManager.getItemPrice(itemId);
int haValue = itemManager.getItemComposition(itemId).getHaPrice();

if (isCoins(itemName)) { geValue = 1; haValue = 1; }

int totalGe = geValue * quantity;
int totalHa = haValue * quantity;


// Always colour the item name. The game already filtered by the valuable drop
// threshold, so always show the price.
String colHex = ChatItemOverlay.getRarityColor(totalGe > 0 ? totalGe : totalHa, config);
String quantityPrefix = (quantity > 1) ? quantity + " x " : "";
String colouredName = quantityPrefix + "<col=" + colHex + ">" + itemName + "</col>";

String msgColour = ColorUtil.colorToHexCode(config.messageColour());
String priceSuffix = (totalGe > 0 || totalHa > 0)
? " " + buildValueSuffix(itemName, totalHa, totalGe) : "";
String message = "<col=" + msgColour + ">Valuable drop: </col>"
+ colouredName
+ "<col=" + msgColour + ">" + priceSuffix + "</col>";
event.getMessageNode().setValue(message);
overlay.cacheItem(itemId);
}

/** Returns just the value portion, e.g. "(GE: 3,072)" -- no item name. */
private String buildValueSuffix(String itemName, int haValue, int geValue)
{
boolean coins = isCoins(itemName);
DropPriceDisplayType display = config.displayPrices();

String haStr = formatValue(haValue);
String geStr = formatValue(geValue);

	String result;
	switch (display)
{
case GRAND_EXCHANGE:
result = coins ? "(" + geStr + ")" : "(GE: " + geStr + ")";
break;
case HIGH_ALCH:
result = coins ? "(" + haStr + ")" : "(HA: " + haStr + ")";
break;
case BOTH:
default:
if (haValue <= 0)      result = coins ? "(" + geStr + ")" : "(GE: " + geStr + ")";
else if (geValue <= 0) result = coins ? "(" + haStr + ")" : "(HA: " + haStr + ")";
else                   result = coins ? "(" + geStr + ")" : "(GE: " + geStr + ", HA: " + haStr + ")";
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
	try { return s != null ? Integer.parseInt(s.trim()) : 1; }
	catch (NumberFormatException e) { return 1; }
}

//  Clan / Friends chat drop replacement

/**
 * Colours the item name in clue item notifications and replaces the coin value.
 * Input: "Maxions received a clue item: Occult ornament kit (3,630,898 coins)."
 */
private String tryColourClueItem(String message, ChatMessageType type)
{
	Matcher m = CLUE_PATTERN.matcher(message);
	if (!m.matches()) return null;
	String prefix    = m.group(1); // "Maxions received a clue item: "
	String itemName  = m.group(2).trim();
	String origCoins = m.group(3);
	String trailing  = m.group(4); // usually "." or empty

	int itemId    = lookupItemId(itemName);
	int geValue   = itemId >= 0 ? itemManager.getItemPrice(itemId) : 0;
	int haValue   = itemId >= 0 ? itemManager.getItemComposition(itemId).getHaPrice() : 0;
	if (itemId >= 0) overlay.cacheItem(itemId);

	String itemColHex = itemId >= 0
		? ChatItemOverlay.getRarityColor(geValue > 0 ? geValue : haValue, config)
		: ChatItemOverlay.getRarityColor(0, config);
	String chatColHex = chatDefaultColour(type);

	String valueSuffix = buildBroadcastSuffix(itemName, haValue, geValue, origCoins);
	return "<col=" + chatColHex + ">" + prefix
		+ "<col=" + itemColHex + ">" + itemName + "</col>"
		+ "<col=" + chatColHex + ">" + valueSuffix + trailing + "</col>";
}

/**
 * Colours the item name in collection log notifications so the hover
 * tooltip can detect it. Returns the modified message or null if not matched.
 * Input: "HolyDab received a new collection log item: Bronze Boots (87/1698)"
 */
private String tryColourCollectionLog(String message, ChatMessageType type)
{
	Matcher m = COLLECTION_LOG_PATTERN.matcher(message);
	if (!m.matches()) return null;
	String prefix   = m.group(1); // "HolyDab received a new collection log item: "
	String itemName = m.group(2).trim(); // "Bronze Boots"
	String suffix   = m.group(3); // " (87/1698)"

	int itemId = lookupItemId(itemName);
	int geValue = itemId >= 0 ? itemManager.getItemPrice(itemId) : 0;
	int haValue = itemId >= 0 ? itemManager.getItemComposition(itemId).getHaPrice() : 0;
	String itemColHex = ChatItemOverlay.getRarityColor(geValue > 0 ? geValue : haValue, config);
	if (itemId >= 0) overlay.cacheItem(itemId);

	String chatColHex = chatDefaultColour(type);
	String priceSuffix = (geValue > 0 || haValue > 0)
		? " " + buildBroadcastSuffix(itemName, haValue, geValue, null) : "";
	return "<col=" + chatColHex + ">" + prefix
		+ "<col=" + itemColHex + ">" + itemName + "</col>"
		+ "<col=" + chatColHex + ">" + suffix + priceSuffix + "</col>";
}

private String tryReplaceDropValue(String message, ChatMessageType type)
{
String chatColHex = chatDefaultColour(type);
Matcher m = DROP_PATTERN.matcher(message);
if (m.matches())
{
	// The pattern has two alternatives:
	// Alt 1 (with quantity): groups 1-5  prefix | qty | item | coins | trailing
	// Alt 2 (no quantity):   groups 6-10 prefix | --- | item | coins | trailing
	boolean hasQty = m.group(1) != null;
	String beforeItem = hasQty ? m.group(1) : m.group(6);
	int    quantity   = hasQty ? parseQty(m.group(2)) : 1;
	String itemName   = (hasQty ? m.group(3) : m.group(7)).trim();
	String origCoins  = hasQty ? m.group(4) : m.group(8);
	String suffix     = hasQty ? m.group(5) : m.group(9);

	int itemId    = lookupItemId(itemName);
	int alchUnit  = itemId >= 0 ? itemManager.getItemComposition(itemId).getHaPrice() : 0;
	int geUnit    = itemId >= 0 ? itemManager.getItemPrice(itemId) : 0;
	int alchTotal = alchUnit * quantity;
	int geTotal   = geUnit   * quantity;

	if (alchUnit <= 0 && geUnit <= 0) return null;

	String itemColHex = ChatItemOverlay.getRarityColor(geTotal > 0 ? geTotal : alchTotal, config);
	// Show "3 x ItemName" for multi-quantity drops
	String qtyPrefix = quantity > 1 ? quantity + " x " : "";
	String coloured  = "<col=" + chatColHex + ">" + beforeItem
		+ "<col=" + itemColHex + ">" + qtyPrefix + itemName + "</col>";
	if (itemId >= 0) overlay.cacheItem(itemId);

	String valueSuffix = buildBroadcastSuffix(itemName, alchTotal, geTotal, origCoins);
	return coloured + "<col=" + chatColHex + ">" + valueSuffix + suffix + "</col>";
}

Matcher ms = SPECIAL_DROP_PATTERN.matcher(message);
if (ms.matches())
{
String itemName  = ms.group(2).trim();
int itemId       = lookupItemId(itemName);
int alchValue    = itemId >= 0 ? itemManager.getItemComposition(itemId).getHaPrice() : 0;
int geValue2     = itemId >= 0 ? itemManager.getItemPrice(itemId) : 0;
if (alchValue <= 0 && geValue2 <= 0) return null;

String itemColHex = ChatItemOverlay.getRarityColor(geValue2 > 0 ? geValue2 : alchValue, config);
if (itemId >= 0) overlay.cacheItem(itemId);

String sp = ms.group(1);
int spNameStart = sp.lastIndexOf(itemName);
String spBefore = spNameStart >= 0 ? sp.substring(0, spNameStart) : sp;
String valueSuffix2 = buildBroadcastSuffix(itemName, alchValue, geValue2, null);
return "<col=" + chatColHex + ">" + spBefore
	+ "<col=" + itemColHex + ">" + itemName + "</col>"
	+ "<col=" + chatColHex + ">" + valueSuffix2 + "</col>";
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
String lower = message.toLowerCase();
StringBuilder result = new StringBuilder(message.length() + 64);
// Stack of active colour tags so we can restore them after an item </col>
java.util.Deque<String> colourStack = new java.util.ArrayDeque<>();
// Seed the stack with the chat-type default so it is always non-empty
colourStack.push(defaultColour);
int i = 0;

while (i < message.length())
{
// Consume any RuneLite tags, keeping the colour stack up to date
if (message.charAt(i) == '<')
{
int tagEnd = message.indexOf('>', i);
if (tagEnd != -1)
{
String tag = message.substring(i, tagEnd + 1);
String tagLower = tag.toLowerCase();
if (tagLower.startsWith("<col="))
{
String hex = tag.substring(5, tag.length() - 1);
colourStack.push(hex);
}
else if (tagLower.equals("</col>") && colourStack.size() > 1)
{
// Never pop the seeded default
colourStack.pop();
}
result.append(tag);
i = tagEnd + 1;
continue;
}
}

boolean atBoundary = (i == 0 || !Character.isLetterOrDigit(lower.charAt(i - 1)));

if (!atBoundary || !Character.isLetterOrDigit(lower.charAt(i)))
{
result.append(message.charAt(i++));
continue;
}

// Extract current word
int wordEnd = i;
while (wordEnd < lower.length() && Character.isLetterOrDigit(lower.charAt(wordEnd)))
wordEnd++;
String firstWord = lower.substring(i, wordEnd);

// Try multi-word names starting with this word
List<String> candidates = firstWordIndex.get(firstWord);
String matchedName = null;
int matchLen = 0;

if (candidates != null)
{
for (String candidate : candidates)
{
if (i + candidate.length() > lower.length()) continue;
if (!lower.regionMatches(i, candidate, 0, candidate.length())) continue;
int end = i + candidate.length();
if (end < lower.length() && Character.isLetterOrDigit(lower.charAt(end))) continue;
if (candidate.length() > matchLen)
{
matchedName = candidate;
matchLen = candidate.length();
}
}
}

if (matchedName == null && itemNameToId.containsKey(firstWord))
{
matchedName = firstWord;
matchLen = firstWord.length();
}

if (matchedName != null)
{
int itemId = itemNameToId.get(matchedName);
int gePrice = itemManager.getItemPrice(itemId);
String itemColHex = ChatItemOverlay.getRarityColor(gePrice, config);
String display = message.substring(i, i + matchLen);
// Wrap item in its rarity colour, then restore the previous colour
String restoreCol = colourStack.peek();
result.append("<col=").append(itemColHex).append(">").append(display)
      .append("</col><col=").append(restoreCol).append(">");
overlay.cacheItem(itemId);
i += matchLen;
}
else
{
result.append(message.charAt(i++));
}
}

return result.toString();
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
for (int id = startId; id < endId; id++)
{
try
{
ItemComposition comp = itemManager.getItemComposition(id);
if (comp == null) continue;
String name = comp.getName();
if (name == null || name.equals("null") || name.isEmpty()) continue;

// Skip noted variants and bank placeholders — they share the base item's name
// and would only create duplicate/conflicting entries.
if (comp.getNote() != -1) continue;
if (comp.getPlaceholderTemplateId() != -1) continue;

// Skip entity/NPC items that have no real player-item properties.
// Real untradeable items (Fire cape, quest items, etc.) have haPrice > 0
// or are equipable. Pure entity items ("Man", "Run", etc.) have none.
if (!comp.isTradeable() && comp.getHaPrice() == 0)
{
	ItemStats scanStats = itemManager.getItemStats(id);
	if (scanStats == null || !scanStats.isEquipable()) continue;
}

String lower = name.toLowerCase();
itemNameToId.putIfAbsent(lower, id);

// Index first word for quick multi-word scanning
String firstWord = lower.split("\\s+")[0];
firstWordIndex.computeIfAbsent(firstWord, k -> new java.util.ArrayList<>()).add(lower);
}
catch (Exception ignored) { }
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
				String alias     = entry.getKey();   // e.g. "tbow"
				String canonical = entry.getValue(); // e.g. "twisted bow"
				if (itemNameToId.containsKey(alias)) continue; // don't overwrite real item names
				Integer id = itemNameToId.get(canonical);
				if (id == null) continue; // canonical not found in game data
				itemNameToId.put(alias, id);
				String firstWord = alias.split("\\s+")[0];
				firstWordIndex.computeIfAbsent(firstWord, k -> new java.util.ArrayList<>()).add(alias);
			}
			log.debug("ClanAlch: item scan complete, {} names loaded", itemNameToId.size());
		}
		return true;
		});
	}//  Item lookup

private int lookupItemId(String itemName)
{
// Try the pre-built scan map first (free, O(1))
if (!itemNameToId.isEmpty())
{
Integer id = itemNameToId.get(itemName.toLowerCase());
if (id != null) return id;
}

// Fall back to ItemManager.search() — exact name match only.
// Never accept the first fuzzy result: e.g. searching "Dom" must not
// return "Saradomin Platebody" just because it contains "dom".
List<ItemPrice> results = itemManager.search(itemName);
if (results == null || results.isEmpty()) return -1;
for (ItemPrice p : results)
if (p.getName().equalsIgnoreCase(itemName)) return p.getId();
return -1;
}

//  Formatting helpers

private String formatValue(int value)
{
if (config.formatPrices())
return QuantityFormatter.quantityToRSDecimalStack(value) + " gp";
return QuantityFormatter.formatNumber(value) + " gp";
}

private String formatBroadcastValue(int value)
{
if (config.formatBroadcastPrices())
return QuantityFormatter.quantityToRSDecimalStack(value);
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
