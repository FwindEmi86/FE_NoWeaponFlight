package top.miragedge.noweaponflight;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class FlightManager {

    private final NoWeaponFlightPlugin plugin;
    private final Map<UUID, FlightData> flightDataMap;
    private Set<Material> bannedItems;

    public FlightManager(NoWeaponFlightPlugin plugin) {
        this.plugin = plugin;
        this.flightDataMap = new HashMap<>();
        loadBannedItems();
    }

    /**
     * 加载禁止飞行的物品列表
     */
    private void loadBannedItems() {
        bannedItems = new HashSet<>(Arrays.asList(
                Material.MACE,           // 重锤
                Material.WOODEN_SPEAR,   // 木矛
                Material.STONE_SPEAR,    // 石矛
                Material.GOLDEN_SPEAR,   // 金矛
                Material.COPPER_SPEAR,   // 铜矛
                Material.IRON_SPEAR,     // 铁矛
                Material.DIAMOND_SPEAR,  // 钻石矛
                Material.NETHERITE_SPEAR // 下界合金矛
        ));
    }

    /**
     * 检查玩家是否持有禁止飞行的物品
     */
    public boolean isHoldingBannedItem(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        // 检查主手和副手
        return (mainHand != null && bannedItems.contains(mainHand.getType())) ||
                (offHand != null && bannedItems.contains(offHand.getType()));
    }

    /**
     * 检查玩家是否在安全位置
     */
    public boolean isInSafePosition(Player player) {
        // 简单判断：玩家是否在地面上或液体中
        if (player.isOnGround()) {
            return true;
        }

        Location location = player.getLocation();
        Location below = location.clone().subtract(0, 0.1, 0);

        // 检查脚下的方块
        Material blockType = below.getBlock().getType();

        // 检查是否在液体中
        if (player.isInWater() || player.isInLava() || player.isInBubbleColumn()) {
            return true;
        }

        // 检查脚下是否是固体方块或液体
        return blockType.isSolid() ||
                blockType == Material.WATER ||
                blockType == Material.LAVA ||
                blockType == Material.BUBBLE_COLUMN;
    }

    /**
     * 取消玩家飞行并使其坠落
     */
    public void cancelFlight(Player player) {
        UUID playerId = player.getUniqueId();

        // 如果已经有记录，不需要重复取消
        if (flightDataMap.containsKey(playerId)) {
            return;
        }

        // 保存当前的飞行状态
        FlightData data = new FlightData(
                player.getAllowFlight(),
                player.isFlying(),
                player.getGameMode()
        );
        flightDataMap.put(playerId, data);

        // 取消飞行
        player.setFlying(false);
        player.setAllowFlight(false);

        // 重置坠落距离，防止摔落伤害被计算
        player.setFallDistance(0.0f);

        // 播放音效和粒子效果
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 0.8f);
        player.spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);

        // 使用ActionBar发送警告消息
        sendActionBar(player, "§c你不能在手持重锤或矛时飞行！");
    }

    /**
     * 恢复玩家飞行
     */
    public void restoreFlight(Player player) {
        UUID playerId = player.getUniqueId();
        FlightData data = flightDataMap.remove(playerId);

        if (data != null) {
            // 恢复原来的飞行状态
            player.setAllowFlight(data.wasAllowedFlight());
            if (data.wasFlying() && data.wasAllowedFlight()) {
                // 延迟设置飞行，确保玩家安全
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.setFlying(true);
                    }
                }, 1L);
            }

            // 恢复原来的游戏模式（如果需要）
            if (data.getGameMode() != null) {
                player.setGameMode(data.getGameMode());
            }

            // 播放恢复音效（不发送消息）
            // player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.5f, 1.0f);
        }
    }

    /**
     * 检查玩家是否处于坠落状态
     */
    public boolean isFalling(UUID playerId) {
        return flightDataMap.containsKey(playerId);
    }

    /**
     * 定期检查所有飞行中的玩家
     */
    public void checkFlyingPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // 检查绕过权限
            if (plugin.hasBypassPermission(player.getUniqueId())) {
                // 如果之前被限制了，现在恢复
                if (isFalling(player.getUniqueId())) {
                    restoreFlight(player);
                }
                continue;
            }

            UUID playerId = player.getUniqueId();

            // 如果玩家正在飞行且持有禁止物品
            if (player.isFlying() && isHoldingBannedItem(player)) {
                cancelFlight(player);
            }
            // 如果玩家之前被取消了飞行，现在检查是否可以恢复
            else if (isFalling(playerId)) {
                // 玩家已经落地就恢复飞行
                if (isInSafePosition(player)) {
                    restoreFlight(player);
                }
            }
        }
    }

    /**
     * 清理所有玩家的飞行数据
     */
    public void cleanup() {
        for (Map.Entry<UUID, FlightData> entry : flightDataMap.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                restoreFlight(player);
            }
        }
        flightDataMap.clear();
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        cleanup();
        loadBannedItems();
    }

    /**
     * 发送ActionBar消息
     */
    private void sendActionBar(Player player, String message) {
        // 使用Bukkit的API发送ActionBar
        player.sendActionBar(message);
    }

    /**
     * 飞行数据记录类
     */
    private static class FlightData {
        private final boolean wasAllowedFlight;
        private final boolean wasFlying;
        private final GameMode gameMode;

        public FlightData(boolean wasAllowedFlight, boolean wasFlying, GameMode gameMode) {
            this.wasAllowedFlight = wasAllowedFlight;
            this.wasFlying = wasFlying;
            this.gameMode = gameMode;
        }

        public boolean wasAllowedFlight() {
            return wasAllowedFlight;
        }

        public boolean wasFlying() {
            return wasFlying;
        }

        public GameMode getGameMode() {
            return gameMode;
        }
    }
}