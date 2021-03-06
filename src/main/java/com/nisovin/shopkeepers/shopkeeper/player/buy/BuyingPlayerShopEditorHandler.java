package com.nisovin.shopkeepers.shopkeeper.player.buy;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import com.nisovin.shopkeepers.api.shopkeeper.offers.PriceOffer;
import com.nisovin.shopkeepers.shopkeeper.TradingRecipeDraft;
import com.nisovin.shopkeepers.shopkeeper.player.PlayerShopEditorHandler;
import com.nisovin.shopkeepers.util.ItemCount;
import com.nisovin.shopkeepers.util.ItemUtils;

public class BuyingPlayerShopEditorHandler extends PlayerShopEditorHandler {

	protected BuyingPlayerShopEditorHandler(SKBuyingPlayerShopkeeper shopkeeper) {
		super(shopkeeper);
	}

	@Override
	public SKBuyingPlayerShopkeeper getShopkeeper() {
		return (SKBuyingPlayerShopkeeper) super.getShopkeeper();
	}

	@Override
	protected List<TradingRecipeDraft> getTradingRecipes() {
		SKBuyingPlayerShopkeeper shopkeeper = this.getShopkeeper();
		List<TradingRecipeDraft> recipes = new ArrayList<>();

		// Add the shopkeeper's offers:
		for (PriceOffer offer : shopkeeper.getOffers()) {
			ItemStack currencyItem = Settings.createCurrencyItem(offer.getPrice());
			TradingRecipeDraft recipe = new TradingRecipeDraft(currencyItem, offer.getItem(), null);
			recipes.add(recipe);
		}

		// Add empty offers for items from the container:
		List<ItemCount> containerItems = shopkeeper.getItemsFromContainer();
		for (int containerItemIndex = 0; containerItemIndex < containerItems.size(); containerItemIndex++) {
			ItemCount itemCount = containerItems.get(containerItemIndex);
			ItemStack itemFromContainer = itemCount.getItem(); // This item is already a copy with amount 1

			if (shopkeeper.getOffer(itemFromContainer) != null) {
				continue; // Already added
			}

			// Add recipe:
			ItemStack currencyItem = Settings.createZeroCurrencyItem();
			TradingRecipeDraft recipe = new TradingRecipeDraft(currencyItem, itemFromContainer, null);
			recipes.add(recipe);
		}

		return recipes;
	}

	@Override
	protected void clearRecipes() {
		SKBuyingPlayerShopkeeper shopkeeper = this.getShopkeeper();
		shopkeeper.clearOffers();
	}

	@Override
	protected void addRecipe(Player player, TradingRecipeDraft recipe) {
		assert recipe != null && recipe.isValid();
		assert recipe.getItem2() == null;

		ItemStack tradedItem = recipe.getItem1();
		assert tradedItem != null;

		ItemStack priceItem = recipe.getResultItem();
		assert priceItem != null;
		if (priceItem.getType() != Settings.currencyItem.getType()) return; // Checking this just in case
		assert priceItem.getAmount() > 0;

		SKBuyingPlayerShopkeeper shopkeeper = this.getShopkeeper();
		shopkeeper.addOffer(ShopkeepersAPI.createPriceOffer(tradedItem, priceItem.getAmount()));
	}

	@Override
	protected void handleTradesClick(Session session, InventoryClickEvent event) {
		assert this.isTradesArea(event.getRawSlot());
		int rawSlot = event.getRawSlot();
		if (this.isResultRow(rawSlot)) {
			// Modifying cost:
			int column = rawSlot - RESULT_ITEM_OFFSET;
			ItemStack tradedItem = event.getInventory().getItem(column + ITEM_1_OFFSET);
			if (ItemUtils.isEmpty(tradedItem)) return;
			this.handleUpdateTradeCostItemOnClick(event, Settings.createCurrencyItem(1), Settings.createZeroCurrencyItem());
		} else if (this.isItem1Row(rawSlot)) {
			// Modifying bought item quantity:
			this.handleUpdateItemAmountOnClick(event, 1);
		} else if (this.isItem2Row(rawSlot)) {
			// Not used by the buying shopkeeper.
		}
	}
}
