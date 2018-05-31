package com.nisovin.shopkeepers.metrics;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.bstats.bukkit.Metrics;

import com.nisovin.shopkeepers.ShopkeepersAPI;
import com.nisovin.shopkeepers.util.ChunkCoords;

/**
 * Reports the number of worlds containing shopkeepers.
 */
public class WorldsChart extends Metrics.SimplePie {

	public WorldsChart(ShopkeepersAPI plugin) {
		super("worlds_with_shops", () -> {
			Set<String> worlds = new HashSet<>();
			for (Entry<ChunkCoords, ?> byChunkEntry : plugin.getAllShopkeepersByChunks().entrySet()) {
				worlds.add(byChunkEntry.getKey().getWorldName());
			}
			return String.valueOf(worlds.size());
		});
	}
}
