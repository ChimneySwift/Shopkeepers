package com.nisovin.shopkeepers.shopkeeper.player.sell;

import java.util.Arrays;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.shopkeepers.Messages;
import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.api.shopkeeper.ShopkeeperCreateException;
import com.nisovin.shopkeepers.api.shopkeeper.player.PlayerShopCreationData;
import com.nisovin.shopkeepers.shopkeeper.player.AbstractPlayerShopType;

public class SellingPlayerShopType extends AbstractPlayerShopType<SKSellingPlayerShopkeeper> {

	public SellingPlayerShopType() {
		super("sell", Arrays.asList("selling", "normal", "player"), ShopkeepersPlugin.PLAYER_SELL_PERMISSION);
	}

	@Override
	public String getDisplayName() {
		return Messages.shopTypeSelling;
	}

	@Override
	public String getDescription() {
		return Messages.shopTypeDescSelling;
	}

	@Override
	public String getSetupDescription() {
		return Messages.shopSetupDescSelling;
	}

	@Override
	public List<String> getTradeSetupDescription() {
		return Messages.tradeSetupDescSelling;
	}

	@Override
	public SKSellingPlayerShopkeeper createShopkeeper(int id, ShopCreationData shopCreationData) throws ShopkeeperCreateException {
		this.validateCreationData(shopCreationData);
		SKSellingPlayerShopkeeper shopkeeper = new SKSellingPlayerShopkeeper(id, (PlayerShopCreationData) shopCreationData);
		return shopkeeper;
	}

	@Override
	public SKSellingPlayerShopkeeper loadShopkeeper(int id, ConfigurationSection configSection) throws ShopkeeperCreateException {
		this.validateConfigSection(configSection);
		SKSellingPlayerShopkeeper shopkeeper = new SKSellingPlayerShopkeeper(id, configSection);
		return shopkeeper;
	}
}
