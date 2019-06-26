package com.nisovin.shopkeepers.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;

/**
 * The component which handles one specific type of user interface window for one specific shopkeeper.
 */
public abstract class UIHandler {

	private final AbstractUIType uiType;
	private final AbstractShopkeeper shopkeeper;

	protected UIHandler(AbstractUIType uiType, AbstractShopkeeper shopkeeper) {
		this.uiType = uiType;
		this.shopkeeper = shopkeeper;
	}

	public AbstractUIType getUIType() {
		return uiType;
	}

	/**
	 * Gets the shopkeeper for which this object is handling the specific interface type for.
	 * 
	 * @return the shopkeeper
	 */
	public AbstractShopkeeper getShopkeeper() {
		return shopkeeper;
	}

	/**
	 * Temporary deactivates UIs for the affected shopkeeper and closes the window (inventory) for the given player
	 * after a tiny delay.
	 * 
	 * @param player
	 *            the player
	 */
	protected void closeDelayed(Player player) {
		// temporary deactivate ui and close open window delayed for this player:
		shopkeeper.deactivateUI();
		Bukkit.getScheduler().runTask(ShopkeepersPlugin.getInstance(), () -> {
			player.closeInventory();

			// reactivate ui:
			shopkeeper.activateUI();
		});
	}

	/**
	 * Checks whether or not the given player can open the handled interface for this shopkeeper.
	 * <p>
	 * This for example gets called when a player requests the interface for this shopkeeper. It should perform the
	 * necessary permission checks.
	 * 
	 * @param player
	 *            a player
	 * @return <code>true</code> if the given player is allowed to open the interface window type this class is handling
	 */
	protected abstract boolean canOpen(Player player);

	/**
	 * This method should open the interface window for the given player.
	 * <p>
	 * Generally {@link #canOpen(Player) canOpen} should be checked before this method gets called, however this method
	 * should not rely on that.
	 * 
	 * @param player
	 *            a player
	 * @return <code>true</code> if the interface window was successfully opened
	 */
	protected abstract boolean openWindow(Player player);

	/**
	 * Checks whether or not the given inventory is a custom inventory created by this handler (for example
	 * by comparing the inventory titles). The result of this method gets checked before any inventory events
	 * are passed through to this handler.
	 * 
	 * @param inventory
	 *            an inventory
	 * @return <code>true</code> if the given inventory is representing a custom interface window created and handled by
	 *         this handler
	 */
	public abstract boolean isWindow(Inventory inventory);

	/**
	 * Gets called when this UI gets closed for a player.
	 * <p>
	 * The corresponding inventory close event might be <code>null</code> if the UI session gets ended for a different
	 * reason.
	 * 
	 * @param player
	 *            the player
	 * @param closeEvent
	 *            the inventory closing event, can be <code>null</code>
	 */
	protected void onInventoryClose(Player player, InventoryCloseEvent closeEvent) {
	}

	// handling of interface window interaction

	/**
	 * Called early for InventoryClickEvent's for inventories for which {@link #isWindow(Inventory)} returned true.
	 * <p>
	 * Any UI potentially canceling the event should consider doing so early in order for other plugins to ignore the
	 * event.
	 * 
	 * @param event
	 *            the inventory click event
	 * @param player
	 *            the clicking player
	 * @see #onInventoryClickLate(InventoryClickEvent, Player)
	 */
	protected void onInventoryClickEarly(InventoryClickEvent event, Player player) {
	}

	/**
	 * Called late for InventoryClickEvent's for inventories for which {@link #isWindow(Inventory)} returned true.
	 * 
	 * @param event
	 *            the inventory click event
	 * @param player
	 *            the clicking player
	 * @see #onInventoryClickEarly(InventoryClickEvent, Player)
	 */
	protected void onInventoryClickLate(InventoryClickEvent event, Player player) {
	}

	/**
	 * Called early for InventoryDragEvent's for inventories for which {@link #isWindow(Inventory)} returned true.
	 * <p>
	 * Any UI potentially canceling the event should consider doing so early in order for other plugins to ignore the
	 * event.
	 * 
	 * @param event
	 *            the inventory drag event
	 * @param player
	 *            the dragging player
	 * @see #onInventoryDragLate(InventoryDragEvent, Player)
	 */
	protected void onInventoryDragEarly(InventoryDragEvent event, Player player) {
	}

	/**
	 * Called late for InventoryDragEvent's for inventories for which {@link #isWindow(Inventory)} returned true.
	 * 
	 * @param event
	 *            the inventory drag event
	 * @param player
	 *            the dragging player
	 * @see #onInventoryDragEarly(InventoryDragEvent, Player)
	 */
	protected void onInventoryDragLate(InventoryDragEvent event, Player player) {
	}
}
