package com.nisovin.shopkeepers.ui.defaults;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import com.nisovin.shopkeepers.Messages;
import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.Settings.DerivedSettings;
import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.shopkeeper.TradingRecipeDraft;
import com.nisovin.shopkeepers.util.EnumUtils;
import com.nisovin.shopkeepers.util.ItemUtils;
import com.nisovin.shopkeepers.util.Log;
import com.nisovin.shopkeepers.util.MathUtils;
import com.nisovin.shopkeepers.util.MerchantUtils;
import com.nisovin.shopkeepers.util.PermissionUtils;
import com.nisovin.shopkeepers.util.StringUtils;
import com.nisovin.shopkeepers.util.TextUtils;
import com.nisovin.shopkeepers.util.Validate;

/**
 * An editor for regular villagers and wandering traders.
 */
public final class VillagerEditorHandler extends AbstractEditorHandler {

	private final AbstractVillager villager;
	private final String title; // Determined once during construction

	public VillagerEditorHandler(AbstractVillager villager) {
		super(SKDefaultUITypes.VILLAGER_EDITOR());
		Validate.notNull(villager, "villager is null");
		this.villager = villager;
		String villagerName = villager.getName(); // Not null
		this.title = StringUtils.replaceArguments(Messages.villagerEditorTitle, "villagerName", villagerName);
	}

	public AbstractVillager getVillager() {
		return villager;
	}

	@Override
	protected String getEditorTitle() {
		return title;
	}

	@Override
	public boolean canOpen(Player player, boolean silent) {
		assert player != null;
		// Check permission:
		if (villager instanceof WanderingTrader) {
			if (!PermissionUtils.hasPermission(player, ShopkeepersPlugin.EDIT_WANDERING_TRADERS_PERMISSION)) {
				if (!silent) {
					Log.debug(() -> "Blocked villager editor from opening for " + player.getName()
							+ ": Missing edit-wandering-traders permission.");
					TextUtils.sendMessage(player, Messages.missingEditWanderingTradersPerm);
				}
				return false;
			}
		} else { // Regular villager
			if (!PermissionUtils.hasPermission(player, ShopkeepersPlugin.EDIT_VILLAGERS_PERMISSION)) {
				if (!silent) {
					Log.debug(() -> "Blocked villager editor from opening for " + player.getName()
							+ ": Missing edit-villagers permission.");
					TextUtils.sendMessage(player, Messages.missingEditVillagersPerm);
				}
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean openWindow(Player player) {
		boolean result = super.openWindow(player);
		return result;
	}

	@Override
	protected void onInventoryClose(Player player, InventoryCloseEvent closeEvent) {
		super.onInventoryClose(player, closeEvent);
	}

	// BUTTONS

	@Override
	protected void setupButtons() {
		super.setupButtons();
		this.addButtonOrIgnore(this.createDeleteButton());
		// TODO Villager renaming? Players can simply use a nametag for this as well.
		// Note: Wandering traders have an inventory as well, but it is usually always empty.
		this.addButtonOrIgnore(this.createContainerButton());
		if (villager instanceof Villager) {
			// The wandering trader does not support the baby state (even though it is ageable).
			this.addButtonOrIgnore(this.getBabyEditorButton());
		}
		this.addButtonOrIgnore(this.getProfessionEditorButton());
		this.addButtonOrIgnore(this.getVillagerTypeEditorButton());
		this.addButtonOrIgnore(this.getVillagerLevelEditorButton());
		this.addButtonOrIgnore(this.getAIButton());
		// TODO Equipment?
		// TODO Option to generate random vanilla trades? Maybe when changing the profession and/or level and there are
		// no trades currently?
	}

	protected Button createDeleteButton() {
		return new ActionButton(true) {
			@Override
			public ItemStack getIcon(Session session) {
				return DerivedSettings.deleteVillagerButtonItem.createItemStack();
			}

			@Override
			protected boolean runAction(InventoryClickEvent clickEvent, Player player) {
				if (!villager.isValid()) return false;
				villager.remove();
				getUISession(player).abortDelayed();
				return true;
			}
		};
	}

	protected Button createContainerButton() {
		return new ActionButton() {
			@Override
			public ItemStack getIcon(Session session) {
				return DerivedSettings.villagerInventoryButtonItem.createItemStack();
			}

			@Override
			protected boolean runAction(InventoryClickEvent clickEvent, Player player) {
				if (!villager.isValid()) return false;
				getUISession(player).closeDelayedAndRunTask(() -> {
					if (!player.isValid() || !villager.isValid()) return;
					// We cannot open the villagers inventory directly. Instead we create custom inventory with its
					// contents. However, any changes in the inventory are not reflected in the villager.
					// TODO Apply inventory changes? The inventory may change during the editor session..
					Inventory villagerInventory = villager.getInventory();
					int inventorySize = (int) Math.ceil(villagerInventory.getSize() / 9.0D) * 9;

					String villagerName = villager.getName(); // Not null
					String inventoryTitle = StringUtils.replaceArguments(Messages.villagerInventoryTitle, "villagerName", villagerName);
					Inventory customInventory = Bukkit.createInventory(null, inventorySize, inventoryTitle);

					// Copy storage contents:
					customInventory.setStorageContents(villagerInventory.getStorageContents());
					player.openInventory(customInventory);
				});
				return true;
			}
		};
	}

	protected Button getBabyEditorButton() {
		return new ActionButton() {
			@Override
			public ItemStack getIcon(Session session) {
				ItemStack iconItem = new ItemStack(Material.EGG);
				ItemUtils.setItemStackNameAndLore(iconItem, Messages.buttonBaby, Messages.buttonBabyLore);
				return iconItem;
			}

			@Override
			protected boolean runAction(InventoryClickEvent clickEvent, Player player) {
				if (!villager.isValid()) return false;
				if (villager.isAdult()) {
					villager.setBaby();
				} else {
					// TODO: MC-9568: Growing up mobs get moved.
					Location location = villager.getLocation();
					villager.setAdult();
					villager.teleport(location);
				}
				return true;
			}
		};
	}

	protected Button getProfessionEditorButton() {
		if (!(villager instanceof Villager)) return null;
		Villager regularVillager = (Villager) villager;
		return new ActionButton() {

			private Profession profession = regularVillager.getProfession();

			@Override
			public ItemStack getIcon(EditorHandler.Session session) {
				ItemStack iconItem;
				switch (profession) {
				case ARMORER:
					iconItem = new ItemStack(Material.BLAST_FURNACE);
					break;
				case BUTCHER:
					iconItem = new ItemStack(Material.SMOKER);
					break;
				case CARTOGRAPHER:
					iconItem = new ItemStack(Material.CARTOGRAPHY_TABLE);
					break;
				case CLERIC:
					iconItem = new ItemStack(Material.BREWING_STAND);
					break;
				case FARMER:
					iconItem = new ItemStack(Material.WHEAT); // Instead of COMPOSTER
					break;
				case FISHERMAN:
					iconItem = new ItemStack(Material.FISHING_ROD); // Instead of BARREL
					break;
				case FLETCHER:
					iconItem = new ItemStack(Material.FLETCHING_TABLE);
					break;
				case LEATHERWORKER:
					iconItem = new ItemStack(Material.LEATHER); // Instead of CAULDRON
					break;
				case LIBRARIAN:
					iconItem = new ItemStack(Material.LECTERN);
					break;
				case MASON:
					iconItem = new ItemStack(Material.STONECUTTER);
					break;
				case SHEPHERD:
					iconItem = new ItemStack(Material.LOOM);
					break;
				case TOOLSMITH:
					iconItem = new ItemStack(Material.SMITHING_TABLE);
					break;
				case WEAPONSMITH:
					iconItem = new ItemStack(Material.GRINDSTONE);
					break;
				case NITWIT:
					iconItem = new ItemStack(Material.LEATHER_CHESTPLATE);
					ItemUtils.setLeatherColor(iconItem, Color.GREEN);
					break;
				case NONE:
				default:
					iconItem = new ItemStack(Material.BARRIER);
					break;
				}
				assert iconItem != null;
				ItemUtils.setItemStackNameAndLore(iconItem, Messages.buttonVillagerProfession, Messages.buttonVillagerProfessionLore);
				return iconItem;
			}

			@Override
			protected boolean runAction(InventoryClickEvent clickEvent, Player player) {
				boolean backwards = clickEvent.isRightClick();
				// Changing the profession will change the trades. Closing the editor view will replace the new trades
				// with the old ones from the editor. But we try to preserve the old trades with their original data:
				List<MerchantRecipe> prevRecipes = villager.getRecipes();
				profession = EnumUtils.cycleEnumConstant(Profession.class, profession, backwards);
				regularVillager.setProfession(profession);
				villager.setRecipes(prevRecipes); // Restore previous trades with their original data

				// We set the villager experience to at least 1, so that the villager does no longer automatically
				// change its profession:
				if (regularVillager.getVillagerExperience() == 0) {
					regularVillager.setVillagerExperience(1);
					TextUtils.sendMessage(player, Messages.setVillagerXp, "xp", 1);
				}
				return true;
			}
		};
	}

	protected Button getVillagerTypeEditorButton() {
		if (!(villager instanceof Villager)) return null;
		Villager regularVillager = (Villager) villager;
		return new ActionButton() {

			private Villager.Type villagerType = regularVillager.getVillagerType();

			@Override
			public ItemStack getIcon(Session session) {
				ItemStack iconItem = new ItemStack(Material.LEATHER_CHESTPLATE);
				switch (villagerType) {
				default:
				case PLAINS:
					// Default brown color:
					break;
				case DESERT:
					ItemUtils.setLeatherColor(iconItem, Color.ORANGE);
					break;
				case JUNGLE:
					ItemUtils.setLeatherColor(iconItem, Color.YELLOW.mixColors(Color.ORANGE));
					break;
				case SAVANNA:
					ItemUtils.setLeatherColor(iconItem, Color.RED);
					break;
				case SNOW:
					ItemUtils.setLeatherColor(iconItem, DyeColor.CYAN.getColor());
					break;
				case SWAMP:
					ItemUtils.setLeatherColor(iconItem, DyeColor.PURPLE.getColor());
					break;
				case TAIGA:
					ItemUtils.setLeatherColor(iconItem, Color.WHITE.mixDyes(DyeColor.BROWN));
					break;
				}
				ItemUtils.setItemStackNameAndLore(iconItem, Messages.buttonVillagerVariant, Messages.buttonVillagerVariantLore);
				return iconItem;
			}

			@Override
			protected boolean runAction(InventoryClickEvent clickEvent, Player player) {
				boolean backwards = clickEvent.isRightClick();
				villagerType = EnumUtils.cycleEnumConstant(Villager.Type.class, villagerType, backwards);
				regularVillager.setVillagerType(villagerType);
				return true;
			}
		};
	}

	protected Button getVillagerLevelEditorButton() {
		if (!(villager instanceof Villager)) return null;
		Villager regularVillager = (Villager) villager;
		return new ActionButton() {

			private int villagerLevel = regularVillager.getVillagerLevel();

			@Override
			public ItemStack getIcon(Session session) {
				ItemStack iconItem;
				switch (regularVillager.getVillagerLevel()) {
				default:
				case 1:
					iconItem = new ItemStack(Material.COBBLESTONE);
					break;
				case 2:
					iconItem = new ItemStack(Material.IRON_INGOT);
					break;
				case 3:
					iconItem = new ItemStack(Material.GOLD_INGOT);
					break;
				case 4:
					iconItem = new ItemStack(Material.EMERALD);
					break;
				case 5:
					iconItem = new ItemStack(Material.DIAMOND);
					break;
				}
				assert iconItem != null;
				// TODO Change the default message back to mention the villager level, instead of just the badge color?
				ItemUtils.setItemStackNameAndLore(iconItem, Messages.buttonVillagerLevel, Messages.buttonVillagerLevelLore);
				return iconItem;
			}

			@Override
			protected boolean runAction(InventoryClickEvent clickEvent, Player player) {
				boolean backwards = clickEvent.isRightClick();
				if (backwards) {
					villagerLevel -= 1;
				} else {
					villagerLevel += 1;
				}
				villagerLevel = MathUtils.rangeModulo(villagerLevel, 1, 5);
				regularVillager.setVillagerLevel(villagerLevel);
				return true;
			}
		};
	}

	protected Button getAIButton() {
		return new ActionButton() {

			private boolean hasAI = villager.hasAI();

			@Override
			public ItemStack getIcon(Session session) {
				ItemStack iconItem;
				if (hasAI) {
					iconItem = new ItemStack(Material.JACK_O_LANTERN);
				} else {
					iconItem = new ItemStack(Material.CARVED_PUMPKIN);
				}
				assert iconItem != null;
				ItemUtils.setItemStackNameAndLore(iconItem, Messages.buttonMobAi, Messages.buttonMobAiLore);
				return iconItem;
			}

			@Override
			protected boolean runAction(InventoryClickEvent clickEvent, Player player) {
				hasAI = !hasAI;
				villager.setAI(hasAI);
				return true;
			}
		};
	}

	// TRADING RECIPES

	@Override
	protected ItemStack createTradeSetupIcon() {
		String villagerName = villager.getName(); // Not null
		String itemName = StringUtils.replaceArguments(Messages.villagerEditorDescriptionHeader, "villagerName", villagerName);
		List<String> itemLore = Messages.villagerEditorDescription;
		return ItemUtils.setItemStackNameAndLore(Settings.tradeSetupItem.createItemStack(), itemName, itemLore);
	}

	@Override
	protected List<TradingRecipeDraft> getTradingRecipes() {
		List<MerchantRecipe> merchantRecipes = villager.getRecipes();
		List<TradingRecipeDraft> recipes = MerchantUtils.createTradingRecipeDrafts(merchantRecipes);
		return recipes;
	}

	@Override
	protected void saveRecipes(Session session) {
		Player player = session.getPlayer();
		// The villager might have been unloaded in the meantime. Our changes won't have any effect then:
		if (!villager.isValid()) {
			Log.debug("The villager edited by '" + player.getName() + "' is no longer existing or has been unloaded.");
			return;
		}

		// We compare the recipes from the editor with the original recipes and keep the original recipes with their
		// original internal data if the items have no changed.
		// TODO Somehow support changing/persisting: max-uses, uses, exp reward, villager xp reward, price multiplier?
		// TODO The trades may change during the editor session..
		List<MerchantRecipe> newRecipes = new ArrayList<>(villager.getRecipes());
		int changedTrades = 0;
		int index = 0;
		for (TradingRecipeDraft recipe : session.getRecipes()) {
			if (!recipe.isValid()) {
				if (index < newRecipes.size()) {
					newRecipes.set(index, null); // Mark as cleared
					changedTrades += 1;
				}
			} else {
				MerchantRecipe newRecipe = MerchantUtils.createMerchantRecipe(recipe);
				if (index < newRecipes.size()) {
					// Keep the old recipe (including all its other internal data) if the items are still the same:
					MerchantRecipe oldRecipe = newRecipes.get(index);
					if (!MerchantUtils.MERCHANT_RECIPES_EQUAL_ITEMS.equals(oldRecipe, newRecipe)) {
						newRecipes.set(index, newRecipe);
						changedTrades += 1;
					} // Else: Keep the old recipe.
				} else {
					newRecipes.add(newRecipe);
					changedTrades += 1;
				}
			}
			index++;
		}
		// Remove null markers:
		newRecipes.removeIf(Objects::isNull);

		if (changedTrades == 0) {
			// No changes:
			TextUtils.sendMessage(player, Messages.noVillagerTradesChanged);
			return;
		} else {
			TextUtils.sendMessage(player, Messages.villagerTradesChanged, "changedTrades", changedTrades);
		}

		// Stop any current trading with this villager:
		HumanEntity trader = villager.getTrader();
		if (trader != null) {
			trader.closeInventory();
		}

		// Apply new trading recipes:
		villager.setRecipes(newRecipes);

		if (villager instanceof Villager) {
			Villager regularVillager = (Villager) villager;

			// We set the villager experience to at least 1, so that the villager does no longer automatically change
			// its profession (and thereby its trades):
			if (regularVillager.getVillagerExperience() == 0) {
				regularVillager.setVillagerExperience(1);
				TextUtils.sendMessage(player, Messages.setVillagerXp, "xp", 1);
			}
		}
	}
}
