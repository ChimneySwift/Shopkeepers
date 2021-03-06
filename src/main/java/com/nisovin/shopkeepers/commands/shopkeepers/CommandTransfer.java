package com.nisovin.shopkeepers.commands.shopkeepers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.nisovin.shopkeepers.Messages;
import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.shopkeeper.player.PlayerShopkeeper;
import com.nisovin.shopkeepers.commands.arguments.ShopkeeperArgument;
import com.nisovin.shopkeepers.commands.arguments.ShopkeeperFilter;
import com.nisovin.shopkeepers.commands.arguments.TargetShopkeeperFallback;
import com.nisovin.shopkeepers.commands.lib.Command;
import com.nisovin.shopkeepers.commands.lib.CommandContextView;
import com.nisovin.shopkeepers.commands.lib.CommandException;
import com.nisovin.shopkeepers.commands.lib.CommandInput;
import com.nisovin.shopkeepers.commands.lib.arguments.PlayerArgument;
import com.nisovin.shopkeepers.util.PermissionUtils;
import com.nisovin.shopkeepers.util.ShopkeeperUtils.TargetShopkeeperFilter;
import com.nisovin.shopkeepers.util.TextUtils;

class CommandTransfer extends Command {

	private static final String ARGUMENT_SHOPKEEPER = "shopkeeper";
	private static final String ARGUMENT_NEW_OWNER = "new-owner";

	CommandTransfer() {
		super("transfer");

		// Set permission:
		this.setPermission(ShopkeepersPlugin.TRANSFER_PERMISSION);

		// Set description:
		this.setDescription(Messages.commandDescriptionTransfer);

		// Arguments:
		this.addArgument(new TargetShopkeeperFallback(
				new ShopkeeperArgument(ARGUMENT_SHOPKEEPER, ShopkeeperFilter.PLAYER),
				TargetShopkeeperFilter.PLAYER
		));
		this.addArgument(new PlayerArgument(ARGUMENT_NEW_OWNER)); // New owner has to be online
		// TODO Allow offline player?
	}

	@Override
	protected void execute(CommandInput input, CommandContextView context) throws CommandException {
		CommandSender sender = input.getSender();

		PlayerShopkeeper shopkeeper = (PlayerShopkeeper) context.get(ARGUMENT_SHOPKEEPER);
		assert shopkeeper != null;
		Player newOwner = context.get(ARGUMENT_NEW_OWNER);
		assert newOwner != null;

		// Check that the shop is owned by the executing player:
		Player player = (sender instanceof Player) ? (Player) sender : null;
		if ((player == null || !shopkeeper.isOwner(player)) && !PermissionUtils.hasPermission(sender, ShopkeepersPlugin.BYPASS_PERMISSION)) {
			TextUtils.sendMessage(sender, Messages.notOwner);
			return;
		}

		// Set new owner:
		shopkeeper.setOwner(newOwner);

		// Success:
		TextUtils.sendMessage(player, Messages.ownerSet, "owner", TextUtils.getPlayerText(newOwner));

		// Save:
		ShopkeepersPlugin.getInstance().getShopkeeperStorage().save();
	}
}
