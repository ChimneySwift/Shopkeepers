package com.nisovin.shopkeepers.util;

import com.nisovin.shopkeepers.api.user.User;
import com.nisovin.shopkeepers.util.PlayerUtils.PlayerNameMatch;

public class UserUtils {

	private UserUtils() {
	}

	public static PlayerNameMatch toPlayerId(User user) {
		assert user != null;
		return new PlayerNameMatch(user.getUniqueId(), user.getName());
	}
}
