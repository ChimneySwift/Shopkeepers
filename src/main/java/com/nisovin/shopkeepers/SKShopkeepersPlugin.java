package com.nisovin.shopkeepers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.events.ShopkeepersStartupEvent;
import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.api.shopkeeper.ShopType;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.TradingRecipe;
import com.nisovin.shopkeepers.api.shopkeeper.offers.BookOffer;
import com.nisovin.shopkeepers.api.shopkeeper.offers.PriceOffer;
import com.nisovin.shopkeepers.api.shopkeeper.offers.TradingOffer;
import com.nisovin.shopkeepers.api.shopkeeper.player.PlayerShopkeeper;
import com.nisovin.shopkeepers.commands.Commands;
import com.nisovin.shopkeepers.compat.MC_1_16_Utils;
import com.nisovin.shopkeepers.compat.NMSManager;
import com.nisovin.shopkeepers.config.ConfigLoadException;
import com.nisovin.shopkeepers.container.protection.ProtectedContainers;
import com.nisovin.shopkeepers.container.protection.RemoveShopOnContainerBreak;
import com.nisovin.shopkeepers.debug.Debug;
import com.nisovin.shopkeepers.debug.DebugOptions;
import com.nisovin.shopkeepers.debug.events.DebugListener;
import com.nisovin.shopkeepers.debug.trades.TradingCountListener;
import com.nisovin.shopkeepers.itemconversion.ItemConversions;
import com.nisovin.shopkeepers.metrics.CitizensChart;
import com.nisovin.shopkeepers.metrics.FeaturesChart;
import com.nisovin.shopkeepers.metrics.GringottsChart;
import com.nisovin.shopkeepers.metrics.PlayerShopsChart;
import com.nisovin.shopkeepers.metrics.ShopkeepersCountChart;
import com.nisovin.shopkeepers.metrics.TownyChart;
import com.nisovin.shopkeepers.metrics.VaultEconomyChart;
import com.nisovin.shopkeepers.metrics.WorldGuardChart;
import com.nisovin.shopkeepers.metrics.WorldsChart;
import com.nisovin.shopkeepers.naming.ShopkeeperNaming;
import com.nisovin.shopkeepers.pluginhandlers.CitizensHandler;
import com.nisovin.shopkeepers.pluginhandlers.WorldGuardHandler;
import com.nisovin.shopkeepers.shopcreation.ShopkeeperCreation;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopType;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopkeeper.SKDefaultShopTypes;
import com.nisovin.shopkeepers.shopkeeper.SKShopTypesRegistry;
import com.nisovin.shopkeepers.shopkeeper.SKShopkeeperRegistry;
import com.nisovin.shopkeepers.shopkeeper.SKTradingRecipe;
import com.nisovin.shopkeepers.shopkeeper.offers.SKBookOffer;
import com.nisovin.shopkeepers.shopkeeper.offers.SKPriceOffer;
import com.nisovin.shopkeepers.shopkeeper.offers.SKTradingOffer;
import com.nisovin.shopkeepers.shopobjects.SKDefaultShopObjectTypes;
import com.nisovin.shopkeepers.shopobjects.SKShopObjectTypesRegistry;
import com.nisovin.shopkeepers.shopobjects.citizens.CitizensShops;
import com.nisovin.shopkeepers.shopobjects.living.LivingShops;
import com.nisovin.shopkeepers.shopobjects.sign.SignShops;
import com.nisovin.shopkeepers.spigot.SpigotFeatures;
import com.nisovin.shopkeepers.storage.SKShopkeeperStorage;
import com.nisovin.shopkeepers.tradelogging.TradeFileLogger;
import com.nisovin.shopkeepers.ui.SKUIRegistry;
import com.nisovin.shopkeepers.ui.defaults.SKDefaultUITypes;
import com.nisovin.shopkeepers.util.ClassUtils;
import com.nisovin.shopkeepers.util.Log;
import com.nisovin.shopkeepers.util.SchedulerUtils;
import com.nisovin.shopkeepers.util.TextUtils;
import com.nisovin.shopkeepers.util.Validate;
import com.nisovin.shopkeepers.villagers.BlockVillagerSpawnListener;
import com.nisovin.shopkeepers.villagers.BlockZombieVillagerCuringListener;
import com.nisovin.shopkeepers.villagers.VillagerInteractionListener;

public class SKShopkeepersPlugin extends JavaPlugin implements ShopkeepersPlugin {

	public static final String DATA_FOLDER = "data";
	public static final String LANG_FOLDER = "lang";

	private static final int ASYNC_TASKS_TIMEOUT_SECONDS = 10;

	private static SKShopkeepersPlugin plugin;

	public static SKShopkeepersPlugin getInstance() {
		return plugin;
	}

	// Shop types and shop object types registry:
	private final SKShopTypesRegistry shopTypesRegistry = new SKShopTypesRegistry();
	private final SKShopObjectTypesRegistry shopObjectTypesRegistry = new SKShopObjectTypesRegistry();

	// Default shop and shop object types:
	private final SKDefaultShopTypes defaultShopTypes = new SKDefaultShopTypes();
	private final SKDefaultShopObjectTypes defaultShopObjectTypes = new SKDefaultShopObjectTypes(this);

	// UI registry:
	private final SKUIRegistry uiRegistry = new SKUIRegistry(this);
	private final SKDefaultUITypes defaultUITypes = new SKDefaultUITypes();

	// Shopkeeper registry:
	private final SKShopkeeperRegistry shopkeeperRegistry = new SKShopkeeperRegistry(this);

	// Shopkeeper storage:
	private final SKShopkeeperStorage shopkeeperStorage = new SKShopkeeperStorage(this);

	private final ItemConversions itemConversions = new ItemConversions(this);
	private final Commands commands = new Commands(this);
	private final ShopkeeperNaming shopkeeperNaming = new ShopkeeperNaming(this);
	private final ShopkeeperCreation shopkeeperCreation = new ShopkeeperCreation(this);

	private final ProtectedContainers protectedContainers = new ProtectedContainers(this);
	private final RemoveShopOnContainerBreak removeShopOnContainerBreak = new RemoveShopOnContainerBreak(this, protectedContainers);
	private final LivingShops livingShops = new LivingShops(this);
	private final SignShops signShops = new SignShops(this);
	private final CitizensShops citizensShops = new CitizensShops(this);

	private boolean outdatedServer = false;
	private boolean incompatibleServer = false;
	private ConfigLoadException configLoadError = null; // null on success

	private void loadAllPluginClasses() {
		File pluginJarFile = this.getFile();
		long start = System.nanoTime();
		boolean success = ClassUtils.loadAllClassesFromJar(pluginJarFile, className -> {
			// Skip version dependent classes:
			if (className.startsWith("com.nisovin.shopkeepers.compat.")) return false;
			// Skip classes which interact with optional dependencies:
			if (className.equals("com.nisovin.shopkeepers.pluginhandlers.WorldGuardHandler$Internal")) return false;
			if (className.equals("com.nisovin.shopkeepers.shopobjects.citizens.CitizensShopkeeperTrait")) return false;
			if (className.equals("com.nisovin.shopkeepers.spigot.text.SpigotText$Internal")) return false;
			return true;
		});
		if (success) {
			long durationMillis = (System.nanoTime() - start) / 1000000L;
			Log.info("Loaded all plugin classes (" + durationMillis + " ms)");
		}
	}

	// Returns true if server is outdated.
	private boolean isOutdatedServerVersion() {
		// Validate that this server is running a minimum required version:
		// TODO Add proper version parsing.
		/*String cbVersion = Utils.getServerCBVersion(); // eg. 1_13_R2
		String bukkitVersion = Bukkit.getBukkitVersion(); // eg. 1.13.1-R0.1-SNAPSHOT*/
		try {
			// This has been added with the recent changes to PlayerBedEnterEvent: TODO outdated
			Class.forName("org.bukkit.event.player.PlayerBedEnterEvent$BedEnterResult");
			return false;
		} catch (ClassNotFoundException e) {
			return true;
		}
	}

	// Returns false if no compatible NMS version, nor the fallback handler could be setup.
	private boolean setupNMS() {
		NMSManager.load(this);
		return (NMSManager.getProvider() != null);
	}

	private void registerDefaults() {
		Log.info("Registering defaults.");
		uiRegistry.registerAll(defaultUITypes.getAllUITypes());
		shopTypesRegistry.registerAll(defaultShopTypes.getAll());
		shopObjectTypesRegistry.registerAll(defaultShopObjectTypes.getAll());
	}

	public SKShopkeepersPlugin() {
		super();
	}

	@Override
	public void onLoad() {
		Log.setLogger(this.getLogger()); // Setup logger early
		// Setting plugin reference early, so it is also available for any code running here:
		plugin = this;
		ShopkeepersAPI.enable(this);

		// Loading all plugin classes up front ensures that we don't run into missing classes (usually during shutdown)
		// when the plugin jar gets replaced during runtime (eg. for hot reloads):
		this.loadAllPluginClasses();

		// Validate that this server is running a minimum required version:
		this.outdatedServer = this.isOutdatedServerVersion();
		if (this.outdatedServer) {
			return;
		}

		// Try to load suitable NMS (or fallback) code:
		this.incompatibleServer = !this.setupNMS();
		if (this.incompatibleServer) {
			return;
		}

		// Load config:
		this.configLoadError = Settings.loadConfig(this);
		if (this.configLoadError != null) {
			return;
		}

		// Create folder structure:
		this.createFolderStructure();

		// Load language file:
		Messages.loadLanguageFile();

		// WorldGuard only allows registering flags before it gets enabled.
		// Note: Changing the config setting has no effect until the next server restart or server reload.
		if (Settings.registerWorldGuardAllowShopFlag) {
			WorldGuardHandler.registerAllowShopFlag();
		}

		// Register defaults:
		this.registerDefaults();
	}

	@Override
	public void onEnable() {
		assert Log.getLogger() != null; // Log should already have been setup
		// Plugin instance and API might already have been set during onLoad:
		boolean alreadySetup = true;
		if (plugin == null) {
			alreadySetup = false;
			plugin = this;
			ShopkeepersAPI.enable(this);
		}

		// Validate that this server is running a minimum required version:
		if (this.outdatedServer) {
			Log.severe("Outdated server version (" + Bukkit.getVersion() + "): Shopkeepers cannot be enabled. Please update your server!");
			this.setEnabled(false); // also calls onDisable
			return;
		}

		// Check if the server version is incompatible:
		if (this.incompatibleServer) {
			Log.severe("Incompatible server version: Shopkeepers cannot be enabled.");
			this.setEnabled(false); // Also calls onDisable
			return;
		}

		// Load config (if not already loaded during onLoad):
		if (!alreadySetup) {
			this.configLoadError = Settings.loadConfig(this);
		} else {
			Log.debug("Config already loaded.");
		}
		if (this.configLoadError != null) {
			Log.severe("Could not load the config!", configLoadError);
			this.setEnabled(false); // Also calls onDisable
			return;
		}

		// Create folder structure (if not already done during onLoad):
		if (!alreadySetup) {
			this.createFolderStructure();
		}

		// Load language file (if not already loaded during onLoad):
		if (!alreadySetup) {
			Messages.loadLanguageFile();
		} else {
			Log.debug("Language file already loaded.");
		}

		// Process additional permissions:
		String[] perms = Settings.maxShopsPermOptions.replace(" ", "").split(",");
		for (String perm : perms) {
			if (Bukkit.getPluginManager().getPermission("shopkeeper.maxshops." + perm) == null) {
				Bukkit.getPluginManager().addPermission(new Permission("shopkeeper.maxshops." + perm, PermissionDefault.FALSE));
			}
		}

		// Check for and initialize version dependent utilities:
		MC_1_16_Utils.init();

		// Inform about Spigot exclusive features:
		if (SpigotFeatures.isSpigotAvailable()) {
			Log.debug("Spigot-based server found: Enabling Spigot exclusive features.");
		} else {
			Log.info("No Spigot-based server found: Disabling Spigot exclusive features!");
		}

		// Register defaults (if not already setup during onLoad):
		if (!alreadySetup) {
			this.registerDefaults();
		} else {
			Log.debug("Defaults already registered.");
		}

		// Call startup event so other plugins can make their registrations:
		Bukkit.getPluginManager().callEvent(new ShopkeepersStartupEvent());

		// Inform UI registry (registers UI event handlers):
		uiRegistry.onEnable();

		// Enable container protection:
		protectedContainers.enable();
		removeShopOnContainerBreak.onEnable();

		// Register events:
		PluginManager pm = Bukkit.getPluginManager();
		pm.registerEvents(new PlayerJoinQuitListener(this), this);
		pm.registerEvents(new TradingCountListener(this), this);
		pm.registerEvents(new TradeFileLogger(this.getSKDataFolder()), this);

		// DEFAULT SHOP OBJECT TYPES

		// Enable living entity shops:
		livingShops.onEnable();

		// Enable sign shops:
		signShops.onEnable();

		// Enable citizens shops:
		citizensShops.onEnable();

		//

		// Handling of regular villagers:
		pm.registerEvents(new VillagerInteractionListener(this), this);
		if (Settings.blockVillagerSpawns || Settings.blockWanderingTraderSpawns) {
			pm.registerEvents(new BlockVillagerSpawnListener(), this);
		}
		if (Settings.disableZombieVillagerCuring) {
			pm.registerEvents(new BlockZombieVillagerCuringListener(), this);
		}

		// Item conversions:
		itemConversions.onEnable();

		// Enable commands:
		commands.onEnable();

		// Enable shopkeeper naming:
		shopkeeperNaming.onEnable();

		// Enable shopkeeper creation:
		shopkeeperCreation.onEnable();

		// Enable shopkeeper storage:
		shopkeeperStorage.onEnable();

		// Enable shopkeeper registry:
		shopkeeperRegistry.onEnable();

		// Load shopkeepers from saved data:
		boolean loadingSuccessful = shopkeeperStorage.reload();
		if (!loadingSuccessful) {
			// Detected an issue during loading.
			// Disabling the plugin without saving, to prevent loss of shopkeeper data:
			Log.severe("Detected an issue during the loading of the shopkeepers data! Disabling the plugin!");
			shopkeeperStorage.disableSaving();
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		// Activate (spawn) shopkeepers in loaded chunks of all loaded worlds:
		shopkeeperRegistry.activateShopkeepersInAllWorlds();

		Bukkit.getScheduler().runTaskLater(this, () -> {
			// Remove inactive player shopkeepers:
			this.removeInactivePlayerShops();
		}, 5L);

		// Let's update the shopkeepers for all already online players:
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (CitizensHandler.isNPC(player)) continue;
			this.updateShopkeepersForPlayer(player.getUniqueId(), player.getName());
		}

		// Write back all updated data:
		if (shopkeeperStorage.isDirty()) {
			shopkeeperStorage.saveNow();
		}

		// Setup metrics:
		if (Settings.enableMetrics) {
			this.setupMetrics();
		}

		// Debugging tools:
		if (Settings.debug) {
			// Register debug listener:
			// Run delayed to also catch events / event listeners of other plugins.
			Bukkit.getScheduler().runTaskLater(this, () -> {
				boolean logAllEvent = Debug.isDebugging(DebugOptions.logAllEvents);
				boolean printListeners = Debug.isDebugging(DebugOptions.printListeners);
				if (logAllEvent || printListeners) {
					DebugListener.register(logAllEvent, printListeners);
				}
			}, 10L);
		}
	}

	@Override
	public void onDisable() {
		// Wait for async tasks to complete:
		SchedulerUtils.awaitAsyncTasksCompletion(this, ASYNC_TASKS_TIMEOUT_SECONDS, this.getLogger());

		// Inform UI registry about disable:
		uiRegistry.onDisable();

		// Despawn all shopkeepers (prior to saving shopkeepers data and before unloading all shopkeepers):
		shopkeeperRegistry.deactivateShopkeepersInAllWorlds();

		// Disable living entity shops:
		livingShops.onDisable();

		// Disable sign shops:
		signShops.onDisable();

		// Disable citizens shops:
		citizensShops.onDisable();

		// Save shopkeepers:
		shopkeeperStorage.saveImmediateIfDirty();

		// Disable protected containers:
		protectedContainers.disable();
		removeShopOnContainerBreak.onDisable();

		// Disable shopkeeper registry: unloads all shopkeepers
		shopkeeperRegistry.onDisable();

		// Disable storage:
		shopkeeperStorage.onDisable();

		shopTypesRegistry.clearAllSelections();
		shopObjectTypesRegistry.clearAllSelections();

		// Disable commands:
		commands.onDisable();

		// Item conversions:
		itemConversions.onDisable();

		shopkeeperNaming.onDisable();
		shopkeeperCreation.onDisable();

		// Clear all types of registers:
		shopTypesRegistry.clearAll();
		shopObjectTypesRegistry.clearAll();
		uiRegistry.clearAll();

		HandlerList.unregisterAll(this);
		Bukkit.getScheduler().cancelTasks(this);

		ShopkeepersAPI.disable();
		plugin = null;
	}

	/**
	 * Reloads the plugin.
	 */
	public void reload() {
		this.onDisable();
		this.onEnable();
	}

	// METRICS

	private void setupMetrics() {
		Metrics metrics = new Metrics(this);
		metrics.addCustomChart(new CitizensChart());
		metrics.addCustomChart(new WorldGuardChart());
		metrics.addCustomChart(new TownyChart());
		metrics.addCustomChart(new VaultEconomyChart());
		metrics.addCustomChart(new GringottsChart());
		metrics.addCustomChart(new ShopkeepersCountChart(shopkeeperRegistry));
		metrics.addCustomChart(new PlayerShopsChart(shopkeeperRegistry));
		metrics.addCustomChart(new FeaturesChart());
		metrics.addCustomChart(new WorldsChart(shopkeeperRegistry));
		// TODO Add chart with number of virtual shops?
		// TODO Add chart with the server variant used (CraftBukkit, Spigot, Paper, other..).
	}

	// PLAYER JOINING AND QUITTING

	void onPlayerJoin(Player player) {
		this.updateShopkeepersForPlayer(player.getUniqueId(), player.getName());
	}

	void onPlayerQuit(Player player) {
		// Player cleanup:
		shopTypesRegistry.clearSelection(player);
		shopObjectTypesRegistry.clearSelection(player);
		uiRegistry.onPlayerQuit(player);

		shopkeeperNaming.onPlayerQuit(player);
		shopkeeperCreation.onPlayerQuit(player);
		commands.onPlayerQuit(player);
	}

	// FOLDER STRUCTURE

	public File getSKDataFolder() {
		return new File(this.getDataFolder(), DATA_FOLDER);
	}

	public File getSKLangFolder() {
		return new File(this.getDataFolder(), LANG_FOLDER);
	}

	private void createFolderStructure() {
		this.getSKDataFolder().mkdirs();
		this.getSKLangFolder().mkdirs();
	}

	// SHOPKEEPER REGISTRY

	@Override
	public SKShopkeeperRegistry getShopkeeperRegistry() {
		return shopkeeperRegistry;
	}

	// SHOPKEEPER STORAGE

	@Override
	public SKShopkeeperStorage getShopkeeperStorage() {
		return shopkeeperStorage;
	}

	// COMMANDS

	public Commands getCommands() {
		return commands;
	}

	// UI

	@Override
	public SKUIRegistry getUIRegistry() {
		return uiRegistry;
	}

	@Override
	public SKDefaultUITypes getDefaultUITypes() {
		return defaultUITypes;
	}

	// PROTECTED CONTAINERS

	public ProtectedContainers getProtectedContainers() {
		return protectedContainers;
	}

	// SHOPKEEPR REMOVAL ON CONTAINER BREAKING

	public RemoveShopOnContainerBreak getRemoveShopOnContainerBreak() {
		return removeShopOnContainerBreak;
	}

	// LIVING ENTITY SHOPS

	public LivingShops getLivingShops() {
		return livingShops;
	}

	// SIGN SHOPS

	public SignShops getSignShops() {
		return signShops;
	}

	// CITIZENS SHOPS

	public CitizensShops getCitizensShops() {
		return citizensShops;
	}

	// SHOP TYPES

	@Override
	public SKShopTypesRegistry getShopTypeRegistry() {
		return shopTypesRegistry;
	}

	@Override
	public SKDefaultShopTypes getDefaultShopTypes() {
		return defaultShopTypes;
	}

	// SHOP OBJECT TYPES

	@Override
	public SKShopObjectTypesRegistry getShopObjectTypeRegistry() {
		return shopObjectTypesRegistry;
	}

	@Override
	public SKDefaultShopObjectTypes getDefaultShopObjectTypes() {
		return defaultShopObjectTypes;
	}

	// SHOPKEEPER NAMING

	public ShopkeeperNaming getShopkeeperNaming() {
		return shopkeeperNaming;
	}

	// SHOPKEEPER CREATION:

	public ShopkeeperCreation getShopkeeperCreation() {
		return shopkeeperCreation;
	}

	@Override
	public boolean hasCreatePermission(Player player) {
		if (player == null) return false;
		return (shopTypesRegistry.getSelection(player) != null) && (shopObjectTypesRegistry.getSelection(player) != null);
	}

	@Override
	public AbstractShopkeeper handleShopkeeperCreation(ShopCreationData shopCreationData) {
		Validate.notNull(shopCreationData, "CreationData is null!");
		ShopType<?> rawShopType = shopCreationData.getShopType();
		Validate.isTrue(rawShopType instanceof AbstractShopType,
				"Expecting an AbstractShopType, got " + rawShopType.getClass().getName());
		AbstractShopType<?> shopType = (AbstractShopType<?>) rawShopType;
		// Forward to shop type:
		return shopType.handleShopkeeperCreation(shopCreationData);
	}

	// INACTIVE SHOPS

	private void removeInactivePlayerShops() {
		if (Settings.playerShopkeeperInactiveDays <= 0) return;

		Set<UUID> playerUUIDs = new HashSet<>();
		for (Shopkeeper shopkeeper : shopkeeperRegistry.getAllShopkeepers()) {
			if (shopkeeper instanceof PlayerShopkeeper) {
				PlayerShopkeeper playerShop = (PlayerShopkeeper) shopkeeper;
				playerUUIDs.add(playerShop.getOwnerUUID());
			}
		}
		if (playerUUIDs.isEmpty()) {
			// No player shops found:
			return;
		}

		// Fetch OfflinePlayers async:
		int playerShopkeeperInactiveDays = Settings.playerShopkeeperInactiveDays;
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			List<OfflinePlayer> inactivePlayers = new ArrayList<>();
			long now = System.currentTimeMillis();
			for (UUID uuid : playerUUIDs) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
				if (!offlinePlayer.hasPlayedBefore()) continue;

				long lastPlayed = offlinePlayer.getLastPlayed();
				if ((lastPlayed > 0) && ((now - lastPlayed) / 86400000 > playerShopkeeperInactiveDays)) {
					inactivePlayers.add(offlinePlayer);
				}
			}

			if (inactivePlayers.isEmpty()) {
				// No inactive players found:
				return;
			}

			// Continue in main thread:
			SchedulerUtils.runTaskOrOmit(SKShopkeepersPlugin.this, () -> {
				List<PlayerShopkeeper> forRemoval = new ArrayList<>();
				for (OfflinePlayer inactivePlayer : inactivePlayers) {
					// Remove all shops of this inactive player:
					UUID playerUUID = inactivePlayer.getUniqueId();

					for (Shopkeeper shopkeeper : shopkeeperRegistry.getAllShopkeepers()) {
						if (shopkeeper instanceof PlayerShopkeeper) {
							PlayerShopkeeper playerShop = (PlayerShopkeeper) shopkeeper;
							UUID ownerUUID = playerShop.getOwnerUUID();
							if (ownerUUID.equals(playerUUID)) {
								forRemoval.add(playerShop);
							}
						}
					}
				}

				// Remove those shopkeepers:
				if (!forRemoval.isEmpty()) {
					for (PlayerShopkeeper shopkeeper : forRemoval) {
						shopkeeper.delete();
						Log.info("Shopkeeper " + shopkeeper.getIdString() + " at " + shopkeeper.getPositionString()
								+ " owned by " + shopkeeper.getOwnerString() + " has been removed for owner inactivity.");
					}

					// save:
					shopkeeperStorage.save();
				}
			});
		});
	}

	// HANDLING PLAYER NAME CHANGES:

	// Updates owner names for the shopkeepers of the specified player:
	private void updateShopkeepersForPlayer(UUID playerUUID, String playerName) {
		Log.debug(DebugOptions.ownerNameUpdates,
				() -> "Updating shopkeepers for: " + TextUtils.getPlayerString(playerName, playerUUID)
		);
		boolean dirty = false;
		for (Shopkeeper shopkeeper : shopkeeperRegistry.getAllShopkeepers()) {
			if (shopkeeper instanceof PlayerShopkeeper) {
				PlayerShopkeeper playerShop = (PlayerShopkeeper) shopkeeper;
				UUID ownerUUID = playerShop.getOwnerUUID();
				String ownerName = playerShop.getOwnerName();

				if (ownerUUID.equals(playerUUID)) {
					if (!ownerName.equals(playerName)) {
						// Update the stored name, because the player must have changed it:
						Log.debug(DebugOptions.ownerNameUpdates,
								() -> "  Updating owner name ('" + ownerName + "') of shopkeeper " + shopkeeper.getId() + "."
						);
						playerShop.setOwner(playerUUID, playerName);
						dirty = true;
					} else {
						// The stored owner name matches the player's current name.
						// Assumption: The stored owner names among all shops are consistent.
						// We can therefore abort checking the other shops here.
						Log.debug(DebugOptions.ownerNameUpdates,
								() -> "  The stored owner name of shopkeeper " + shopkeeper.getId()
										+ " matches the current player name. Skipping checking of further shops."
						);
						return;
					}
				}
			}
		}

		if (dirty) {
			shopkeeperStorage.save();
		}
	}

	// FACTORY

	@Override
	public TradingRecipe createTradingRecipe(ItemStack resultItem, ItemStack item1, ItemStack item2) {
		return new SKTradingRecipe(resultItem, item1, item2);
	}

	@Override
	public TradingRecipe createTradingRecipe(ItemStack resultItem, ItemStack item1, ItemStack item2, boolean outOfStock) {
		return new SKTradingRecipe(resultItem, item1, item2, outOfStock);
	}

	// OFFERS

	@Override
	public PriceOffer createPriceOffer(ItemStack item, int price) {
		return new SKPriceOffer(item, price);
	}

	@Override
	public TradingOffer createTradingOffer(ItemStack resultItem, ItemStack item1, ItemStack item2) {
		return new SKTradingOffer(resultItem, item1, item2);
	}

	@Override
	public BookOffer createBookOffer(String bookTitle, int price) {
		return new SKBookOffer(bookTitle, price);
	}
}
