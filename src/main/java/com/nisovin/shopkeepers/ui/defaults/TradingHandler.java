package com.nisovin.shopkeepers.ui.defaults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.PlayerInventory;

import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.events.ShopkeeperTradeEvent;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.TradingRecipe;
import com.nisovin.shopkeepers.compat.NMSManager;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.ui.AbstractUIType;
import com.nisovin.shopkeepers.ui.UIHandler;
import com.nisovin.shopkeepers.util.ItemUtils;
import com.nisovin.shopkeepers.util.Log;
import com.nisovin.shopkeepers.util.ShopkeeperUtils;
import com.nisovin.shopkeepers.util.Utils;

public class TradingHandler extends UIHandler {

	// TODO move into protected variables instead?
	/**
	 * Holds gathered information about the currently handled trade.
	 */
	protected static class TradeData {
		/**
		 * The inventory click event which originally triggered the trade.
		 * <p>
		 * Do not modify this event or any of the involved items! This has to be kept cancelled!
		 */
		public InventoryClickEvent clickEvent;
		/**
		 * The involved merchant inventory.
		 */
		public MerchantInventory merchantInventory;
		/**
		 * The trading player.
		 */
		public Player tradingPlayer;
		/**
		 * The involved player inventory.
		 */
		public PlayerInventory playerInventory;
		/**
		 * The used trading recipe.
		 */
		public TradingRecipe tradingRecipe;
		/**
		 * The item offered by the player matching the first required item of the used trading recipe (not necessarily
		 * the item in the first slot), not <code>null</code> or empty.
		 * <p>
		 * The type and durability equal those of the required item from the trading recipe. The metadata however can
		 * differ, but still be accepted for the trade depending on the item matching rules of the used minecraft
		 * version and the shopkeeper settings (ex. strict item comparison disabled).
		 * <p>
		 * Note: This is not a copy and might get modified once the trade gets applied!
		 */
		public ItemStack offeredItem1;
		/**
		 * The item offered by the player matching the second required item of the used trading recipe (not necessarily
		 * the item in the second slot), can be <code>null</code>.
		 * <p>
		 * The type and durability equal those of the required item from the trading recipe. The metadata however can
		 * differ, but still be accepted for the trade depending on the item matching rules of the used minecraft
		 * version and the shopkeeper settings (ex. strict item comparison disabled).
		 * <p>
		 * Note: This is not a copy and might get modified once the trade gets applied!
		 */
		public ItemStack offeredItem2;
		/**
		 * Whether the <code>offeredItem1</code> and <code>offeredItem2</code> are placed in reverse or regular order
		 * inside the trading slots of the merchant inventory.
		 */
		public boolean swappedItemOrder;

		protected TradeData() {
		}

		// separate from constructor to allow evolution without affecting sub-classes
		private void setup(	InventoryClickEvent clickEvent, MerchantInventory merchantInventory, Player tradingPlayer,
							TradingRecipe tradingRecipe, ItemStack offeredItem1, ItemStack offeredItem2, boolean swappedItemOrder) {
			this.clickEvent = clickEvent;
			this.merchantInventory = merchantInventory;
			this.tradingPlayer = tradingPlayer;
			this.playerInventory = tradingPlayer.getInventory();
			this.tradingRecipe = tradingRecipe;
			this.offeredItem1 = offeredItem1;
			this.offeredItem2 = offeredItem2;
			this.swappedItemOrder = swappedItemOrder;
		}
	}

	// those slot ids match both raw slot id and regular slot id for the merchant inventory view with the merchant
	// inventory at the top:
	protected static final int BUY_ITEM_1_SLOT_ID = 0;
	protected static final int BUY_ITEM_2_SLOT_ID = 1;
	protected static final int RESULT_ITEM_SLOT_ID = 2;

	private final Map<UUID, Merchant> merchants = new HashMap<>();

	// counts the trades triggered by the last click-event:
	protected int tradeCounter = 0;

	public TradingHandler(AbstractUIType uiType, AbstractShopkeeper shopkeeper) {
		super(uiType, shopkeeper);
	}

	protected Merchant getMerchant(Player player) {
		return merchants.get(player.getUniqueId());
	}

	@Override
	protected boolean canOpen(Player player) {
		assert player != null;
		if (!Utils.hasPermission(player, ShopkeepersPlugin.TRADE_PERMISSION)) {
			Log.debug("Blocked trade window opening from " + player.getName() + ": Missing trade permission.");
			Utils.sendMessage(player, Settings.msgMissingTradePerm);
			return false;
		}
		return true;
	}

	@Override
	protected boolean openWindow(Player player) {
		// create and open trading window:
		Shopkeeper shopkeeper = this.getShopkeeper();
		String title = this.getInventoryTitle();
		List<TradingRecipe> recipes = shopkeeper.getTradingRecipes(player);
		return this.openTradeWindow(title, recipes, player);
	}

	protected boolean openTradeWindow(String title, List<TradingRecipe> recipes, Player player) {
		// setup merchant:
		Merchant merchant = this.setupMerchant(title, recipes);
		merchants.put(player.getUniqueId(), merchant);

		// increase 'talked-to-villager' statistic:
		player.incrementStatistic(Statistic.TALKED_TO_VILLAGER);

		// open merchant:
		return (player.openMerchant(merchant, true) != null);
	}

	protected Merchant setupMerchant(String title, List<TradingRecipe> recipes) {
		// setup merchant:
		Merchant merchant = Bukkit.createMerchant(title);
		this.setupMerchantRecipes(merchant, recipes);
		return merchant;
	}

	protected void setupMerchantRecipes(Merchant merchant, List<TradingRecipe> recipes) {
		// create list of merchant recipes:
		List<MerchantRecipe> merchantRecipes = this.createMerchantRecipes(recipes);
		// set merchant's recipes:
		merchant.setRecipes(merchantRecipes);
	}

	protected List<MerchantRecipe> createMerchantRecipes(List<TradingRecipe> recipes) {
		List<MerchantRecipe> merchantRecipes = new ArrayList<>();
		for (TradingRecipe recipe : recipes) {
			merchantRecipes.add(this.createMerchantRecipe(recipe));
		}
		return merchantRecipes;
	}

	protected MerchantRecipe createMerchantRecipe(TradingRecipe recipe) {
		ItemStack buyItem1 = recipe.getItem1();
		ItemStack buyItem2 = recipe.getItem2();
		ItemStack sellingItem = recipe.getResultItem();
		assert !ItemUtils.isEmpty(sellingItem) && !ItemUtils.isEmpty(buyItem1);

		MerchantRecipe merchantRecipe = new MerchantRecipe(sellingItem, 10000); // no max-uses limit
		if (recipe.isOutOfStock()) merchantRecipe.setUses(10000); // .. except if it is out of stock
		merchantRecipe.setExperienceReward(false); // no experience rewards
		merchantRecipe.addIngredient(buyItem1);
		if (!ItemUtils.isEmpty(buyItem2)) {
			merchantRecipe.addIngredient(buyItem2);
		}
		return merchantRecipe;
	}

	protected String getInventoryTitle() {
		String title = this.getShopkeeper().getName();
		if (title == null || title.isEmpty()) {
			title = Settings.msgTradingTitleDefault;
		}
		return Settings.msgTradingTitlePrefix + title;
	}

	protected void updateTrades(Player player) {
		Merchant merchant = this.getMerchant(player);
		if (merchant == null) return;
		List<MerchantRecipe> oldMerchantRecipes = merchant.getRecipes();

		Shopkeeper shopkeeper = this.getShopkeeper();
		List<TradingRecipe> recipes = shopkeeper.getTradingRecipes(player);
		List<MerchantRecipe> newMerchantRecipes = this.createMerchantRecipes(recipes);
		if (ShopkeeperUtils.areMerchantRecipesEqual(oldMerchantRecipes, newMerchantRecipes)) {
			Log.debug("Trades are still up-to-date for player " + player.getName());
			return; // recipes did not change
		}
		Log.debug("Updating trades for player " + player.getName());

		// it is not safe to reduce the number of trading recipes for the player, so we need to add dummy recipes:
		for (int i = recipes.size(); i < oldMerchantRecipes.size(); ++i) {
			MerchantRecipe merchantRecipe = new MerchantRecipe(null, 0, 0, false);
			newMerchantRecipes.add(merchantRecipe);
		}
		// set merchant's recipes:
		merchant.setRecipes(newMerchantRecipes);

		// update recipes:
		NMSManager.getProvider().updateTrades(player, merchant);
	}

	@Override
	public boolean isWindow(Inventory inventory) {
		return inventory instanceof MerchantInventory;
	}

	@Override
	protected void onInventoryClose(Player player, InventoryCloseEvent closeEvent) {
		merchants.remove(player.getUniqueId());
	}

	// TRADE PROCESSING

	// late processing, so that other plugins can cancel the trading earlier without having to rely on Shopkeepers' API
	@Override
	protected void onInventoryClickLate(InventoryClickEvent clickEvent, Player player) {
		assert clickEvent != null && player != null;
		// note: this expects that there are no other click-events while this event is getting processed
		// reset trade counter:
		tradeCounter = 0;

		Shopkeeper shopkeeper = this.getShopkeeper();
		String playerName = player.getName();
		if (clickEvent.isCancelled()) {
			Log.debug("Some plugin has cancelled the click in the trading window for "
					+ playerName + " at " + shopkeeper.getPositionString() + ".");
			return;
		}

		int rawSlot = clickEvent.getRawSlot();
		InventoryAction action = clickEvent.getAction();

		MerchantInventory merchantInventory = (MerchantInventory) clickEvent.getInventory();
		ItemStack resultItem = merchantInventory.getItem(RESULT_ITEM_SLOT_ID);
		ItemStack cursor = clickEvent.getCursor();

		// prevent unsupported types of special clicks:
		if (action == InventoryAction.COLLECT_TO_CURSOR && ItemUtils.isSimilar(resultItem, cursor)) {
			// weird behavior and buggy, see MC-129515
			// for now: only allowed if the item on the cursor and inside the result slot are different
			// TODO maybe replicate the behavior of this inventory action, but limit its effect to the player's
			// inventory?
			Log.debug("Prevented unsupported special click in trading window by " + playerName
					+ " at " + shopkeeper.getPositionString() + ": " + action);
			clickEvent.setCancelled(true);
			ItemUtils.updateInventoryLater(player);
			return;
		}

		// all currently supported inventory actions that might trigger trades involve a click of the result slot:
		if (rawSlot != RESULT_ITEM_SLOT_ID) {
			// not canceling the event to allow regular inventory interaction inside the player's inventory
			return;
		}

		// some clicks on the result slot don't trigger trades:
		if (action == InventoryAction.CLONE_STACK) {
			return;
		}

		// we are handling all types of clicks which might trigger a trade ourselves:
		clickEvent.setCancelled(true);
		ItemUtils.updateInventoryLater(player);

		// check for a trade and prepare trade data:
		TradeData tradeData = this.checkForTrade(clickEvent, false);
		if (tradeData == null) {
			// no trade available
			return;
		}
		assert tradeData.tradingRecipe.getResultItem().isSimilar(resultItem);

		PlayerInventory playerInventory = player.getInventory();
		boolean isCursorEmpty = ItemUtils.isEmpty(cursor);

		// handle trade depending on used inventory action:
		if (action == InventoryAction.PICKUP_ALL || action == InventoryAction.PICKUP_HALF) {
			if (!isCursorEmpty && (!cursor.isSimilar(resultItem) || (cursor.getAmount() + resultItem.getAmount()) > cursor.getMaxStackSize())) {
				Log.debug("Not handling trade: The cursor cannot carry the resulting items.");
				return;
			} else {
				if (this.handleTrade(tradeData)) {
					// add result items to cursor:
					ItemStack resultCursor;
					if (isCursorEmpty) {
						resultCursor = resultItem; // no item copy required here
					} else {
						resultCursor = ItemUtils.increaseItemAmount(cursor, resultItem.getAmount());
					}
					player.setItemOnCursor(resultCursor);

					// common apply trade:
					this.commonApplyTrade(tradeData);
				}
				this.updateTrades(player);
			}
		} else if (action == InventoryAction.DROP_ONE_SLOT || action == InventoryAction.DROP_ALL_SLOT) {
			// not supported for now, since this might be tricky to accurately reproduce
			// dropItemNaturally is not equivalent to the player himself dropping the item
			// and inventoryView.setItem(-999, item) deosn't set the item's thrower
			// (and there is no API to set that, nor does the inventoryView return a reference to the dropped item)
			/*if (isCursorEmpty) {
				if (this.handleTrade(tradeData)) {
					// drop result items:
					ItemStack droppedItem = resultItem.clone(); // todo copy required?
					// todo call drop event first
					player.getWorld().dropItemNaturally(player.getEyeLocation(), droppedItem);
				
					// common apply trade:
					this.commonApplyTrade(tradeData);
				}
			}*/
		} else if (action == InventoryAction.HOTBAR_SWAP) {
			int hotbarButton = clickEvent.getHotbarButton();
			if (hotbarButton >= 0 && hotbarButton <= 8 && ItemUtils.isEmpty(playerInventory.getItem(hotbarButton))) {
				if (this.handleTrade(tradeData)) {
					// set result items to hotbar slot:
					playerInventory.setItem(hotbarButton, resultItem); // no item copy required here

					// common apply trade:
					this.commonApplyTrade(tradeData);
				}
				this.updateTrades(player);
			}
		} else if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
			// trades as often as possible (depending on offered items and inventory space) for the current result item:
			// if the current trading recipe is no longer fulfilled, and the currently selected recipe index is 0,
			// it will switch to the next applicable trading recipe, and continue the trading if the new result item is
			// equal to the previous result item
			while (true) {
				// check if there is enough space in the player's inventory:
				ItemStack[] newPlayerContents = playerInventory.getStorageContents();

				// minecraft is adding items in reverse container order (starting with hotbar slot 9),
				// so we reverse the player contents accordingly before adding items:
				// changes write through to the original array:
				List<ItemStack> listView = Arrays.asList(newPlayerContents);
				List<ItemStack> hotbarView = listView.subList(0, 9);
				List<ItemStack> contentsView = listView.subList(9, 36);
				Collections.reverse(hotbarView);
				Collections.reverse(contentsView);

				// no item copy required here
				if (ItemUtils.addItems(newPlayerContents, resultItem) != 0) {
					// not enough inventory space, abort trading:
					break;
				}

				if (!this.handleTrade(tradeData)) {
					// trade was aborted:
					break;
				}

				// revert previous reverse:
				Collections.reverse(hotbarView);
				Collections.reverse(contentsView);

				// apply player inventory changes:
				playerInventory.setStorageContents(newPlayerContents);

				// common apply trade:
				this.commonApplyTrade(tradeData);

				// check if we might continue trading:
				tradeData = this.checkForTrade(clickEvent, true); // silent
				if (tradeData == null) {
					// no trade available:
					break;
				}
				// compare result items:
				ItemStack newResultItem = tradeData.tradingRecipe.getResultItem();
				if (!resultItem.isSimilar(newResultItem)) {
					// new result item doesn't match previous result item, abort trading (mimics minecraft behavior):
					break;
				}
				// update result item:
				resultItem = newResultItem;
			}
			this.updateTrades(player);
		} else {
			// the inventory action involves the result slot, but doesn't trigger a trade usually, or isn't supported
			// yet
		}
	}

	// checks for an available trade and does some preparation in case a trade is found,
	// returns null if no trade could be prepared for some reason:
	private TradeData checkForTrade(InventoryClickEvent clickEvent, boolean silent) {
		Player player = (Player) clickEvent.getWhoClicked();
		MerchantInventory merchantInventory = (MerchantInventory) clickEvent.getInventory();
		ItemStack resultItem = merchantInventory.getItem(RESULT_ITEM_SLOT_ID);
		if (ItemUtils.isEmpty(resultItem)) {
			if (!silent) {
				Log.debug("Not handling trade: There is no item in the clicked result slot (no trade available).");
			}
			return null; // no trade available
		}

		// find (and validate) the recipe minecraft is using for the trade:
		TradingRecipe tradingRecipe = ShopkeeperUtils.getSelectedTradingRecipe(merchantInventory);
		if (tradingRecipe == null) {
			// this shouldn't happen..
			if (!silent) {
				Log.debug("Not handling trade: We couldn't find the used trading recipe!");
			}
			return null;
		}
		if (!tradingRecipe.getResultItem().equals(resultItem)) {
			// this shouldn't happen..
			if (!silent) {
				Log.debug("Not handling trade: The trade result item doesn't match the expected item of the used trading recipe!");
			}
			return null;
		}

		ItemStack requiredItem1 = tradingRecipe.getItem1();
		ItemStack requiredItem2 = tradingRecipe.getItem2();
		assert !ItemUtils.isEmpty(requiredItem1);

		// use null here instead of air for consistent behavior with previous versions:
		ItemStack offeredItem1 = ItemUtils.getNullIfEmpty(merchantInventory.getItem(BUY_ITEM_1_SLOT_ID));
		ItemStack offeredItem2 = ItemUtils.getNullIfEmpty(merchantInventory.getItem(BUY_ITEM_2_SLOT_ID));
		boolean swappedItemOrder = false;

		// minecraft checks both combinations (item1, item2) and (item2, item1) when determining if a trading recipe
		// matches, so we need to determine the used item order for the currently used trading recipe:
		if (NMSManager.getProvider().matches(offeredItem1, requiredItem1) && NMSManager.getProvider().matches(offeredItem2, requiredItem2)) {
			// order is as-is
		} else if (NMSManager.getProvider().matches(offeredItem1, requiredItem2) && NMSManager.getProvider().matches(offeredItem2, requiredItem1)) {
			// swapped order:
			swappedItemOrder = true;
			ItemStack temp = offeredItem1;
			offeredItem1 = offeredItem2;
			offeredItem2 = temp;
		} else {
			// the used item order couldn't be determined
			// this shouldn't happen..
			// but this might for example happen if the FailedHandler#matches implementation falls back to using
			// the stricter isSimilar for the item comparison and the involved items are not strictly similar
			if (!silent) {
				Log.debug("Not handling trade: Couldn't match the offered items to the used trading recipe!");
			}
			return null;
		}
		assert offeredItem1 != null;

		if (Settings.useStrictItemComparison) {
			// verify the recipe items are perfectly matching (they can still be swapped though):
			if (!ItemUtils.isSimilar(requiredItem1, offeredItem1) || !ItemUtils.isSimilar(requiredItem2, offeredItem2)) {
				// additional check for the debug flag, so we don't do the item comparisons if not really needed
				if (!silent && Settings.debug) {
					this.debugPreventedTrade(player, "The offered items do not strictly match the required items.");
					Log.debug("Used trading recipe: " + ItemUtils.getSimpleRecipeInfo(tradingRecipe));
					Log.debug("Recipe item 1: " + (ItemUtils.isSimilar(requiredItem1, offeredItem1) ? "similar" : "not similar"));
					Log.debug("Recipe item 2: " + (ItemUtils.isSimilar(requiredItem2, offeredItem2) ? "similar" : "not similar"));
				}
				return null;
			}
		}

		// setup trade data:
		TradeData tradeData = this.createTradeData();
		tradeData.setup(clickEvent, merchantInventory, player, tradingRecipe, offeredItem1, offeredItem2, swappedItemOrder);
		// custom setup by sub-classes:
		this.setupTradeData(tradeData, clickEvent);
		return tradeData;
	}

	protected final void debugPreventedTrade(Player player, String reason) {
		Log.debug("Prevented trade by " + player.getName() + " with shopkeeper at " + this.getShopkeeper().getPositionString()
				+ ": " + reason);
	}

	/**
	 * Creates a new {@link TradeData}.
	 * <p>
	 * This can be overridden to allow sub-classes to store additional data.
	 * 
	 * @return the new trade data object
	 */
	protected TradeData createTradeData() {
		return new TradeData();
	}

	/**
	 * This can be used by sub-classes to initially fill in additional data based on the given
	 * {@link InventoryClickEvent} into the {@link TradeData}, before it gets passed around.
	 * <p>
	 * This is called after the common setup of the {@link TradeData} has been performed.
	 * 
	 * @param tradeData
	 *            the trade data
	 * @param clickEvent
	 *            the click event
	 */
	protected void setupTradeData(TradeData tradeData, InventoryClickEvent clickEvent) {
	}

	// returns true if the trade was not aborted and is now supposed to get applied
	private boolean handleTrade(TradeData tradeData) {
		assert tradeData != null;
		// increase trade counter:
		tradeCounter++;

		// check and prepare the trade:
		if (!this.prepareTrade(tradeData)) {
			// the trade got cancelled for some shopkeeper-specific reason:
			this.onTradeAborted(tradeData);
			return false;
		}

		// call trade event, giving other plugins a chance to cancel the trade before it gets applied:
		ShopkeeperTradeEvent tradeEvent = new ShopkeeperTradeEvent(this.getShopkeeper(), tradeData.tradingPlayer,
				tradeData.clickEvent, tradeData.tradingRecipe, tradeData.offeredItem1, tradeData.offeredItem2,
				tradeData.swappedItemOrder);
		Bukkit.getPluginManager().callEvent(tradeEvent);
		if (tradeEvent.isCancelled()) {
			Log.debug("The trade got cancelled by some other plugin.");
			this.onTradeAborted(tradeData);
			return false;
		}
		// making sure that the click event is still cancelled:
		if (!tradeData.clickEvent.isCancelled()) {
			Log.warning("Some plugin tried to uncancel the click event during trade handling!");
			tradeData.clickEvent.setCancelled(true);
		}

		// assert: the click event and the affected inventories should not get modified during the event!

		// we are going to apply the trade now:
		this.preApplyTrade(tradeData);
		return true;
	}

	private void commonApplyTrade(TradeData tradeData) {
		// update merchant inventory contents:
		MerchantInventory merchantInventory = tradeData.merchantInventory;
		merchantInventory.setItem(RESULT_ITEM_SLOT_ID, null); // clear result slot, just in case

		TradingRecipe tradingRecipe = tradeData.tradingRecipe;
		ItemStack newOfferedItem1 = ItemUtils.descreaseItemAmount(tradeData.offeredItem1, ItemUtils.getItemStackAmount(tradingRecipe.getItem1()));
		ItemStack newOfferedItem2 = ItemUtils.descreaseItemAmount(tradeData.offeredItem2, ItemUtils.getItemStackAmount(tradingRecipe.getItem2()));
		// inform the merchant inventory about the change (updates the active trading recipe and result item):
		merchantInventory.setItem(tradeData.swappedItemOrder ? BUY_ITEM_2_SLOT_ID : BUY_ITEM_1_SLOT_ID, newOfferedItem1);
		merchantInventory.setItem(tradeData.swappedItemOrder ? BUY_ITEM_1_SLOT_ID : BUY_ITEM_2_SLOT_ID, newOfferedItem2);

		// TODO increase uses of corresponding MerchanRecipe?
		// TODO add support for exp-rewards?
		// TODO support modifications to the MerchantRecipe's maxUses?

		// TODO option to increase the player's crafting statistic for the traded item (like in vanilla minecraft)?
		// TODO option to increase the player's trading statistic (like in vanilla minecraft)?

		// shopkeeper-specific application of the trade:
		this.onTradeApplied(tradeData);

		// log trade:
		Log.debug("Trade (#" + tradeCounter + ") by " + tradeData.tradingPlayer.getName() + " with shopkeeper at "
				+ this.getShopkeeper().getPositionString() + ": " + ItemUtils.getSimpleRecipeInfo(tradingRecipe));
	}

	/**
	 * Checks whether the trade can be performed and makes any preparations required for applying the trade in case it
	 * actually gets performed.
	 * <p>
	 * This gets called for every trade a player triggered through a merchant inventory action. Depending on the
	 * inventory action multiple successive trades (even using different trading recipes) might get triggered by a
	 * single inventory action.
	 * <p>
	 * There should be no changes of the corresponding click event and the involved inventories (player, chest) have to
	 * be expected between this phase of the trade handling and the actual application of the trade.
	 * 
	 * @param tradeData
	 *            the trade data
	 * @return <code>true</code> to continue trade handling, <code>false</code> to cancel the trade and any successive
	 *         trades triggered by the same inventory click
	 */
	protected boolean prepareTrade(TradeData tradeData) {
		return true;
	}

	/**
	 * Called if a previously already prepared trade got aborted for some reason.
	 * <p>
	 * Does also get called if the trade got aborted by {@link #prepareTrade(TradeData)} itself.
	 * <p>
	 * This can be used to perform any necessary cleanup.
	 * 
	 * @param tradeData
	 *            the trade data
	 */
	protected void onTradeAborted(TradeData tradeData) {
	}

	/**
	 * This gets called right before a trade gets applied.
	 * <p>
	 * This can be used to perform any kind of pre-processing which needs to happen first.
	 * <p>
	 * At this phase of the trade handling, the trade should no longer get cancelled. Any conditions which could prevent
	 * a trade from getting successfully applied have to be checked inside {@link #prepareTrade(TradeData)} instead.
	 * 
	 * @param tradeData
	 *            the trade data
	 */
	protected void preApplyTrade(TradeData tradeData) {
	}

	/**
	 * This gets called right after a trade has been applied.
	 * <p>
	 * This can be used to perform any kind of post-processing which needs to happen last. For example any shopkeeper
	 * specific behavior required for applying the trade can happen here.
	 * <p>
	 * At this phase of the trade handling, the trade should no longer get cancelled. Any conditions which could prevent
	 * a trade from getting successfully applied have to be checked inside {@link #prepareTrade(TradeData)} instead.
	 * 
	 * @param tradeData
	 *            the trade data
	 */
	protected void onTradeApplied(TradeData tradeData) {
	}

	// returns a value >= 0 and <= amount
	protected int getAmountAfterTaxes(int amount) {
		assert amount >= 0;
		if (Settings.taxRate == 0) return amount;
		int taxes = 0;
		if (Settings.taxRoundUp) {
			taxes = (int) Math.ceil(amount * (Settings.taxRate / 100.0D));
		} else {
			taxes = (int) Math.floor(amount * (Settings.taxRate / 100.0D));
		}
		return Math.max(0, Math.min(amount - taxes, amount));
	}
}
