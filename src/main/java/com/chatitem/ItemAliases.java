package com.chatitem;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Common OSRS item abbreviations and nicknames mapped to their canonical item names.
 * Keys and values are all lowercase.
 * Values must exactly match the in-game item name so lookups succeed.
 */
public final class ItemAliases
{
	/** alias (lowercase) -> canonical item name (lowercase) */
	public static final Map<String, String> MAP;

	static
	{
		Map<String, String> m = new HashMap<>();

		// ── Weapons ──────────────────────────────────────────────────────────
		m.put("tbow",            "twisted bow");
		m.put("bp",              "toxic blowpipe");
		m.put("blowpipe",        "toxic blowpipe");
		m.put("ags",             "armadyl godsword");
		m.put("bgs",             "bandos godsword");
		m.put("sgs",             "saradomin godsword");
		m.put("zgs",             "zamorak godsword");
		m.put("egt",             "eldritch nightmare staff");
		m.put("voidwaker",       "voidwaker");
		m.put("dwh",             "dragon warhammer");
		m.put("dclaws",          "dragon claws");
		m.put("claws",           "dragon claws");
		m.put("scythe",          "scythe of vitur");
		m.put("sang",            "sanguinesti staff");
		m.put("rapier",          "ghrazi rapier");
		m.put("fang",            "osmumten's fang");
		m.put("shadow",          "tumeken's shadow");
		m.put("kodai",           "kodai wand");
		m.put("harm",            "harmonised nightmare staff");
		m.put("volatile",        "volatile nightmare staff");
		m.put("eldritch",        "eldritch nightmare staff");
		m.put("bowfa",           "bow of faerdhinen");
		m.put("bofa",            "bow of faerdhinen");
		m.put("acb",             "armadyl crossbow");
		m.put("zcb",             "zaryte crossbow");
		m.put("zaryte cbow",     "zaryte crossbow");
		m.put("dcb",             "dragon crossbow");
		m.put("rcb",             "rune crossbow");
		m.put("dhcb",            "dragon hunter crossbow");
		m.put("dragon hunter",   "dragon hunter crossbow");
		m.put("dhl",             "dragon hunter lance");
		m.put("msb",             "magic shortbow");
		m.put("msbi",            "magic shortbow (i)");
		m.put("dds",             "dragon dagger");
		m.put("gmaul",           "granite maul");
		m.put("tent",            "abyssal tentacle");
		m.put("whip",            "abyssal whip");
		m.put("dbow",            "dark bow");
		m.put("sotd",            "staff of the dead");
		m.put("tsotd",           "toxic staff of the dead");
		m.put("toxic sotd",      "toxic staff of the dead");
		m.put("trident",         "trident of the seas");
		m.put("swamp trident",   "trident of the swamp");
		m.put("elder maul",      "elder maul");
		m.put("dscim",           "dragon scimitar");
		m.put("dlong",           "dragon longsword");
		m.put("dbaxe",           "dragon battleaxe");
		m.put("d2h",             "dragon 2h sword");
		m.put("dhally",          "dragon halberd");
		m.put("dspear",          "dragon spear");
		m.put("dinhs",           "dinh's bulwark");
		m.put("dinhs bulwark",   "dinh's bulwark");
		m.put("bulwark",         "dinh's bulwark");
		m.put("craw",            "craw's bow");
		m.put("craws",           "craw's bow");
		m.put("vigg",            "viggora's chainmace");
		m.put("thamm",           "thammaron's sceptre");
		m.put("obby maul",       "tzhaar-ket-om");
		m.put("crystal hally",   "crystal halberd");
		m.put("chally",          "crystal halberd");
		m.put("zammy spear",     "zamorakian spear");
		m.put("zspear",          "zamorakian spear");
		m.put("zammy hasta",     "zamorakian hasta");
		m.put("steam staff",     "steam battlestaff");
		m.put("arcane",          "arcane spirit shield");
		m.put("spectral",        "spectral spirit shield");
		m.put("dfs",             "dragonfire shield");
		m.put("dfw",             "dragonfire ward");
		m.put("buckler",         "twisted buckler");
		m.put("hydra claw",      "hydra's claw");

		// ── Armour ───────────────────────────────────────────────────────────
		m.put("bcp",             "bandos chestplate");
		m.put("tassets",         "bandos tassets");
		m.put("tassies",         "bandos tassets");
		m.put("prims",           "primordial boots");
		m.put("pegs",            "pegasian boots");
		m.put("eternals",        "eternal boots");
		m.put("serp helm",       "serpentine helm");
		m.put("serp",            "serpentine helm");
		m.put("torva helm",      "torva full helm");
		m.put("torva body",      "torva platebody");
		m.put("torva platebody", "torva platebody");
		m.put("torva legs",      "torva platelegs");
		m.put("torva platelegs", "torva platelegs");
		m.put("torva full helm", "torva full helm");
		m.put("anc hat",         "ancestral hat");
		m.put("ancestral hat",   "ancestral hat");
		m.put("anc top",         "ancestral robe top");
		m.put("ancestral top",   "ancestral robe top");
		m.put("anc bottom",      "ancestral robe bottom");
		m.put("anc bottoms",     "ancestral robe bottom");
		m.put("ancestral bottom","ancestral robe bottom");
		m.put("masori body",     "masori body");
		m.put("masori legs",     "masori chaps");
		m.put("masori helm",     "masori mask");
		m.put("masori mask",     "masori mask");
		m.put("inq mace",        "inquisitor's mace");
		m.put("inq helm",        "inquisitor's great helm");
		m.put("inq top",         "inquisitor's hauberk");
		m.put("inq legs",        "inquisitor's plateskirt");
		m.put("inquisitor helm", "inquisitor's great helm");
		m.put("inquisitor top",  "inquisitor's hauberk");
		m.put("inquisitor legs", "inquisitor's plateskirt");
		m.put("inquisitor mace", "inquisitor's mace");
		m.put("avernic",         "avernic defender hilt");
		m.put("avernic hilt",    "avernic defender hilt");
		m.put("avernic def",     "avernic defender");
		m.put("avernic defender","avernic defender");
		m.put("dragon def",      "dragon defender");
		m.put("dragon defender", "dragon defender");
		m.put("rune def",        "rune defender");
		m.put("crystal helm",    "crystal helm");
		m.put("crystal body",    "crystal body");
		m.put("crystal legs",    "crystal legs");
		m.put("crystal bow",     "crystal bow");
		m.put("crystal shield",  "crystal shield");
		m.put("dchain",          "dragon chainbody");
		m.put("dlegs",           "dragon platelegs");

		// Barrows
		m.put("dharoks",         "dharok's greataxe");
		m.put("veracs",          "verac's flail");
		m.put("guthans",         "guthan's warspear");
		m.put("torags",          "torag's hammers");
		m.put("karils",          "karil's crossbow");
		m.put("ahrims",          "ahrim's staff");

		// 3rd age
		m.put("3a plate",        "3rd age platebody");
		m.put("3a legs",         "3rd age platelegs");
		m.put("3a helm",         "3rd age full helmet");
		m.put("3a kit",          "3rd age kit");
		m.put("3a bow",          "3rd age bow");
		m.put("3a wand",         "3rd age wand");
		m.put("3a amulet",       "3rd age amulet");
		m.put("3a pickaxe",      "3rd age pickaxe");
		m.put("3a axe",          "3rd age axe");
		m.put("third age",       "3rd age platebody");

		// ── Jewellery ─────────────────────────────────────────────────────────
		m.put("fury",            "amulet of fury");
		m.put("torture",         "amulet of torture");
		m.put("anguish",         "necklace of anguish");
		m.put("tormented",       "tormented bracelet");
		m.put("occult",          "occult necklace");
		m.put("suffering",       "ring of suffering");
		m.put("rancour",         "amulet of rancour");
		m.put("berserker ring",  "berserker ring");
		m.put("brimstone ring",  "brimstone ring");
		m.put("archers ring",    "archers ring");
		m.put("seers ring",      "seers ring");
		m.put("wealth",          "ring of wealth");
		m.put("row",             "ring of wealth");
		m.put("combat brace",    "combat bracelet(6)");
		m.put("zenyte",          "zenyte shard");
		m.put("zenny",           "zenyte shard");

		// ── Capes ────────────────────────────────────────────────────────────
		m.put("obby cape",       "obsidian cape");
		m.put("fire cape",       "fire cape");
		m.put("infernal",        "infernal cape");
		m.put("infernal cape",   "infernal cape");
		m.put("dcape",           "infernal cape");
		m.put("fcape",           "fire cape");
		m.put("assembler",       "ava's assembler");
		m.put("avas",            "ava's assembler");
		m.put("max cape",        "max cape");
		m.put("accumulator",     "ava's accumulator");
		m.put("attractor",       "ava's attractor");

		// ── Slayer ───────────────────────────────────────────────────────────
		m.put("slay helm",       "slayer helmet");
		m.put("slayer helm",     "slayer helmet");
		m.put("black mask",      "black mask");

		// ── Potions ───────────────────────────────────────────────────────────
		m.put("brews",           "saradomin brew(4)");
		m.put("brew",            "saradomin brew(4)");
		m.put("saradomin brew",  "saradomin brew(4)");
		m.put("restores",        "super restore(4)");
		m.put("restore",         "super restore(4)");
		m.put("super restore",   "super restore(4)");
		m.put("ppots",           "prayer potion(4)");
		m.put("ppot",            "prayer potion(4)");
		m.put("prayer pot",      "prayer potion(4)");
		m.put("prayer potion",   "prayer potion(4)");
		m.put("stams",           "stamina potion(4)");
		m.put("stam",            "stamina potion(4)");
		m.put("stamina pot",     "stamina potion(4)");
		m.put("stamina potion",  "stamina potion(4)");
		m.put("scb",             "super combat potion(4)");
		m.put("super combat",    "super combat potion(4)");
		m.put("divine",          "divine super combat potion(4)");
		m.put("divine scb",      "divine super combat potion(4)");
		m.put("divine combat",   "divine super combat potion(4)");
		m.put("divine super combat","divine super combat potion(4)");
		m.put("anti fire",       "antifire potion(4)");
		m.put("antifire",        "antifire potion(4)");
		m.put("antifires",       "antifire potion(4)");
		m.put("super antifire",  "super antifire potion(4)");
		m.put("extended antifire","extended antifire(4)");
		m.put("antivenom",       "anti-venom(4)");
		m.put("antivenoms",      "anti-venom(4)");
		m.put("anti venom",      "anti-venom(4)");
		m.put("antivenom+",      "anti-venom+(4)");
		m.put("anti venom+",     "anti-venom+(4)");
		m.put("sanfew",          "sanfew serum(4)");
		m.put("sanfews",         "sanfew serum(4)");
		m.put("sanfew serum",    "sanfew serum(4)");
		m.put("super att",       "super attack(4)");
		m.put("super attack",    "super attack(4)");
		m.put("super str",       "super strength(4)");
		m.put("super strength",  "super strength(4)");
		m.put("super def",       "super defence(4)");
		m.put("super defence",   "super defence(4)");
		m.put("super defense",   "super defence(4)");
		m.put("bastion",         "bastion potion(4)");
		m.put("battlemage",      "battlemage potion(4)");
		m.put("divine bastion",  "divine bastion potion(4)");
		m.put("divine battlemage","divine battlemage potion(4)");
		m.put("magic pot",       "magic potion(4)");
		m.put("magic potion",    "magic potion(4)");
		m.put("ranging pot",     "ranging potion(4)");
		m.put("ranging potion",  "ranging potion(4)");
		m.put("imbued heart",    "imbued heart");
		m.put("saturated heart", "saturated heart");
		m.put("antipoison",      "antipoison(4)");
		m.put("super antipoison","superantipoison(4)");
		m.put("energy pot",      "energy potion(4)");
		m.put("super energy",    "super energy(4)");

		// ── Runes ─────────────────────────────────────────────────────────────
		m.put("nats",            "nature rune");
		m.put("nature runes",    "nature rune");
		m.put("bloods",          "blood rune");
		m.put("blood runes",     "blood rune");
		m.put("deaths",          "death rune");
		m.put("death runes",     "death rune");
		m.put("laws",            "law rune");
		m.put("law runes",       "law rune");
		m.put("souls",           "soul rune");
		m.put("soul runes",      "soul rune");
		m.put("wraths",          "wrath rune");
		m.put("wrath runes",     "wrath rune");
		m.put("astrals",         "astral rune");
		m.put("astral runes",    "astral rune");
		m.put("cosmics",         "cosmic rune");
		m.put("cosmic runes",    "cosmic rune");
		m.put("chaos runes",     "chaos rune");
		m.put("air runes",       "air rune");
		m.put("water runes",     "water rune");
		m.put("earth runes",     "earth rune");
		m.put("fire runes",      "fire rune");
		m.put("mind runes",      "mind rune");
		m.put("body runes",      "body rune");

		// ── Materials / Resources ─────────────────────────────────────────────
		// Bars
		m.put("addy bar",        "adamantite bar");
		m.put("addy bars",       "adamantite bar");
		m.put("adamantite bars", "adamantite bar");
		m.put("mith bar",        "mithril bar");
		m.put("mith bars",       "mithril bar");
		m.put("mithril bars",    "mithril bar");
		m.put("rune bar",        "runite bar");
		m.put("rune bars",       "runite bar");
		m.put("runite bars",     "runite bar");
		m.put("iron bars",       "iron bar");
		m.put("steel bars",      "steel bar");
		m.put("gold bars",       "gold bar");
		m.put("bronze bars",     "bronze bar");

		// Ores
		m.put("rune ore",        "runite ore");
		m.put("rune ores",       "runite ore");
		m.put("runite ores",     "runite ore");
		m.put("addy ore",        "adamantite ore");
		m.put("addy ores",       "adamantite ore");
		m.put("mith ore",        "mithril ore");
		m.put("mith ores",       "mithril ore");

		// Bones
		m.put("dragon bones",    "dragon bones");
		m.put("dbone",           "dragon bones");
		m.put("dbones",          "dragon bones");
		m.put("superior bones",  "superior dragon bones");
		m.put("lava bones",      "lava dragon bones");
		m.put("wyvern bones",    "wyvern bones");
		m.put("big bones",       "big bones");

		// Essences
		m.put("pure ess",        "pure essence");
		m.put("rune ess",        "rune essence");
		m.put("daeyalt ess",     "daeyalt essence");

		// Herbs
		m.put("ranarr",          "ranarr weed");
		m.put("ranarrs",         "ranarr weed");
		m.put("snaps",           "snapdragon");
		m.put("snapdragons",     "snapdragon");
		m.put("toadflax",        "toadflax");
		m.put("kwuarms",         "kwuarm");
		m.put("cadantines",      "cadantine");
		m.put("lantadymes",      "lantadyme");
		m.put("dwarf weeds",     "dwarf weed");
		m.put("torstols",        "torstol");

		// Logs
		m.put("yews",            "yew logs");
		m.put("yew logs",        "yew logs");
		m.put("magics",          "magic logs");
		m.put("magic logs",      "magic logs");
		m.put("redwoods",        "redwood logs");
		m.put("redwood logs",    "redwood logs");
		m.put("maples",          "maple logs");
		m.put("maple logs",      "maple logs");

		// Scales / skilling materials
		m.put("scales",          "zulrah's scales");
		m.put("zulrah scales",   "zulrah's scales");

		// Bolts
		m.put("addy bolts",      "adamant bolts");
		m.put("mith bolts",      "mithril bolts");
		m.put("rune bolts",      "runite bolts");
		m.put("dragon bolts",    "dragon bolts (e)");
		m.put("ruby bolts",      "ruby bolts (e)");
		m.put("diamond bolts",   "diamond bolts (e)");
		m.put("onyx bolts",      "onyx bolts (e)");
		m.put("dragonstone bolts","dragonstone bolts (e)");

		// Darts / arrows
		m.put("addy darts",      "adamant dart");
		m.put("addy dart",       "adamant dart");
		m.put("mith darts",      "mithril dart");
		m.put("rune darts",      "rune dart");
		m.put("dragon darts",    "dragon dart");
		m.put("ddarts",          "dragon dart");
		m.put("rune arrows",     "rune arrow");
		m.put("dragon arrows",   "dragon arrow");
		m.put("amethyst arrows", "amethyst arrow");
		m.put("addy arrows",     "adamant arrow");
		m.put("mith arrows",     "mithril arrow");

		// ── Misc supplies ─────────────────────────────────────────────────────
		m.put("sharks",          "shark");
		m.put("anglers",         "anglerfish");
		m.put("anglerfish",      "anglerfish");
		m.put("mantas",          "manta ray");
		m.put("manta rays",      "manta ray");
		m.put("karams",          "karambwan");
		m.put("karambwans",      "karambwan");
		m.put("lobbies",         "lobster");
		m.put("lobsters",        "lobster");
		m.put("monks",           "monkfish");

		// ── Graceful ──────────────────────────────────────────────────────────
		m.put("graceful",        "graceful hood");
		m.put("graceful hood",   "graceful hood");
		m.put("graceful top",    "graceful top");
		m.put("graceful legs",   "graceful legs");
		m.put("graceful gloves", "graceful gloves");
		m.put("graceful boots",  "graceful boots");
		m.put("graceful cape",   "graceful cape");
		m.put("marks",           "mark of grace");
		m.put("marks of grace",  "mark of grace");
		m.put("amylase",         "amylase crystal");
		m.put("amylase crystals","amylase crystal");

		// ── Misc ──────────────────────────────────────────────────────────────
		m.put("bond",            "old school bond");
		m.put("bonds",           "old school bond");
		m.put("looting bag",     "looting bag");
		m.put("loot bag",        "looting bag");
		m.put("rune pouch",      "rune pouch");
		m.put("coal bag",        "coal bag");
		m.put("seed box",        "seed box");
		m.put("herb sack",       "herb sack");
		m.put("herbsack",        "herb sack");
		m.put("gem bag",         "gem bag");
		m.put("bonecrusher",     "bonecrusher");
		m.put("b2p",             "bones to peaches");
		m.put("gp",              "coins");

		// Clue scrolls
		m.put("easy clue",       "clue scroll (easy)");
		m.put("easy clues",      "clue scroll (easy)");
		m.put("medium clue",     "clue scroll (medium)");
		m.put("medium clues",    "clue scroll (medium)");
		m.put("hard clue",       "clue scroll (hard)");
		m.put("hard clues",      "clue scroll (hard)");
		m.put("elite clue",      "clue scroll (elite)");
		m.put("elite clues",     "clue scroll (elite)");
		m.put("master clue",     "clue scroll (master)");
		m.put("master clues",    "clue scroll (master)");

		// ── Raids uniques ─────────────────────────────────────────────────────
		m.put("dex",             "dexterous prayer scroll");
		m.put("dex scroll",      "dexterous prayer scroll");
		m.put("dexterous",       "dexterous prayer scroll");
		m.put("arcane scroll",   "arcane prayer scroll");
		m.put("arc scroll",      "arcane prayer scroll");
		m.put("olmlet",          "olmlet");
		m.put("little nightmare","little nightmare");
		m.put("lil zik",         "lil' zik");
		m.put("tumekens guardian","tumeken's guardian");
		m.put("elidinis guardian","elidinis' guardian");

		// ToB
		m.put("avernic hilt",    "avernic defender hilt");
		m.put("sang staff",      "sanguinesti staff");
		m.put("holy ornament kit","holy ornament kit");
		m.put("sanguine ornament kit","sanguine ornament kit");
		m.put("sang dust",       "sanguine dust");

		// ── Nex uniques ───────────────────────────────────────────────────────
		m.put("nihil horn",      "nihil horn");
		m.put("ancient hilt",    "ancient hilt");
		m.put("torva",           "torva full helm");

		// ── Boss/misc uniques ──────────────────────────────────────────────────
		m.put("hydra leather",   "hydra leather");
		m.put("visage",          "draconic visage");
		m.put("vork",            "vorki");
		m.put("vorki",           "vorki");
		m.put("phoenix",         "phoenix");
		m.put("orb",             "nightmare orb");
		m.put("jar of souls",    "jar of souls");
		m.put("jar of swamp",    "jar of swamp");
		m.put("jar of eyes",     "jar of eyes");
		m.put("larrans key",     "larran's key");
		m.put("brimstone key",   "brimstone key");
		m.put("crystal key",     "crystal key");

		MAP = Collections.unmodifiableMap(m);
	}

	private ItemAliases() {}
}
