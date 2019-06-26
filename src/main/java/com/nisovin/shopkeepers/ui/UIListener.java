package com.nisovin.shopkeepers.ui;

import java.util.ArrayDeque;
import java.util.Deque;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.inventory.Inventory;

import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.util.Log;

class UIListener implements Listener {

	private final ShopkeepersPlugin plugin;
	private final SKUIRegistry uiRegistry;

	// The relation between early and late event handling are maintained via stacks, in case something (a plugin) is
	// calling these inventory interaction events recursively from within an event handler. The DUMMY_UI_HANDLER on the
	// stack indicates that the event is not being processed by any ui handler.
	private static final UIHandler DUMMY_UI_HANDLER = new UIHandler(null, null) {
		@Override
		protected boolean openWindow(Player player) {
			return false;
		}

		@Override
		public boolean isWindow(Inventory inventory) {
			return false;
		}

		@Override
		protected boolean canOpen(Player player) {
			return false;
		}
	};
	private final Deque<UIHandler> clickHandlerStack = new ArrayDeque<>();
	private final Deque<UIHandler> dragHandlerStack = new ArrayDeque<>();

	UIListener(ShopkeepersPlugin plugin, SKUIRegistry uiRegistry) {
		this.plugin = plugin;
		this.uiRegistry = uiRegistry;
	}

	private SKUISession getUISession(HumanEntity human) {
		if (human.getType() != EntityType.PLAYER) return null;
		Player player = (Player) human;
		return uiRegistry.getSession(player);
	}

	private boolean validateSession(InventoryInteractEvent event, Player player, SKUISession session) {
		Inventory inventory = event.getInventory();
		UIHandler uiHandler = session.getUIHandler();

		// validate open inventory:
		if (!uiHandler.isWindow(inventory)) {
			// the player probably has some other inventory open, but an active session.. let's close it:
			Log.debug("Closing inventory of type " + inventory.getType() + " with title '" + inventory.getTitle()
					+ "' for " + player.getName() + ", because a different open inventory was expected for '"
					+ uiHandler.getUIType().getIdentifier() + "'.");
			event.setCancelled(true);
			Bukkit.getScheduler().runTask(plugin, () -> {
				player.closeInventory();
			});
			return false;
		}

		// validate shopkeeper:
		if (!session.getShopkeeper().isUIActive() || !session.getShopkeeper().isValid()) {
			// shopkeeper deleted, or the UIs got deactivated: ignore this click
			Log.debug("Inventory interaction by " + player.getName() + " ignored, because the window is about to get closed,"
					+ " or the shopkeeper got deleted.");
			event.setCancelled(true);
			return false;
		}

		return true;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	void onInventoryClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player)) {
			return;
		}
		Player player = (Player) event.getPlayer();

		// inform ui registry so that it can cleanup session data:
		uiRegistry.onInventoryClose(player, event);
	}

	@EventHandler(priority = EventPriority.LOW)
	void onInventoryEarly(InventoryClickEvent event) {
		// the ui handler processing this click, or DUMMY_UI_HANDLER if none is processing the event
		UIHandler uiHandler = DUMMY_UI_HANDLER;
		Player player = null; // the player, or null if there is no session
		SKUISession session = this.getUISession(event.getWhoClicked());
		if (session != null) {
			player = (Player) event.getWhoClicked();
			assert player.equals(session.getPlayer());
			// validate session:
			if (this.validateSession(event, player, session)) {
				uiHandler = session.getUIHandler();

				// debug information:
				Inventory inventory = event.getInventory();
				Log.debug("Inventory click: player=" + player.getName()
						+ ", inventory-type=" + inventory.getType() + ", inventory-title=" + inventory.getTitle()
						+ ", raw-slot-id=" + event.getRawSlot() + ", slot-id=" + event.getSlot() + ", slot-type=" + event.getSlotType()
						+ ", shift=" + event.isShiftClick() + ", hotbar key=" + event.getHotbarButton()
						+ ", left-or-right=" + (event.isLeftClick() ? "left" : (event.isRightClick() ? "right" : "unknown"))
						+ ", click-type=" + event.getClick() + ", action=" + event.getAction());
			}
		}

		// keep track of the processing ui handler (can be dummy):
		clickHandlerStack.push(uiHandler);

		if (uiHandler != DUMMY_UI_HANDLER) {
			// let the UIHandler handle the click:
			uiHandler.onInventoryClickEarly(event, player);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	void onInventoryClickLate(InventoryClickEvent event) {
		UIHandler uiHandler = clickHandlerStack.pop(); // not expected to be empty
		if (uiHandler == DUMMY_UI_HANDLER) return; // ignore
		// It is expected that the session and UI handler determined at the beginning of the event processing are still
		// valid at this point.

		// let the UIHandler handle the click:
		Player player = (Player) event.getWhoClicked();
		uiHandler.onInventoryClickLate(event, player);
	}

	@EventHandler(priority = EventPriority.LOW)
	void onInventoryDragEarly(InventoryDragEvent event) {
		// the ui handler processing this click, or DUMMY_UI_HANDLER if none is processing the event
		UIHandler uiHandler = DUMMY_UI_HANDLER;
		Player player = null; // the player, or null if there is no session
		SKUISession session = this.getUISession(event.getWhoClicked());
		if (session != null) {
			player = (Player) event.getWhoClicked();
			assert player.equals(session.getPlayer());
			// validate session:
			if (this.validateSession(event, player, session)) {
				uiHandler = session.getUIHandler();

				// debug information:
				Inventory inventory = event.getInventory();
				Log.debug("Inventory dragging: player=" + player.getName()
						+ ", inventory-type=" + inventory.getType() + ", inventory-title=" + inventory.getTitle()
						+ ", drag-type=" + event.getType());
			}
		}

		// keep track of the processing ui handler (can be dummy):
		dragHandlerStack.push(uiHandler);

		if (uiHandler != DUMMY_UI_HANDLER) {
			// let the UIHandler handle the click:
			uiHandler.onInventoryDragEarly(event, player);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	void onInventoryDragLate(InventoryDragEvent event) {
		UIHandler uiHandler = dragHandlerStack.pop(); // not expected to be empty
		if (uiHandler == DUMMY_UI_HANDLER) return; // ignore
		// It is expected that the session and UI handler determined at the beginning of the event processing are still
		// valid at this point.

		// let the UIHandler handle the dragging:
		Player player = (Player) event.getWhoClicked();
		uiHandler.onInventoryDragLate(event, player);
	}
}
