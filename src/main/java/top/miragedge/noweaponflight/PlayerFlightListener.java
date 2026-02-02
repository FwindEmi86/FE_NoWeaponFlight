package top.miragedge.noweaponflight;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.inventory.InventoryClickEvent;

public class PlayerFlightListener implements Listener {

    private final NoWeaponFlightPlugin plugin;
    private final FlightManager flightManager;

    public PlayerFlightListener(NoWeaponFlightPlugin plugin) {
        this.plugin = plugin;
        this.flightManager = plugin.getFlightManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        // 检查绕过权限
        if (plugin.hasBypassPermission(player.getUniqueId())) {
            return;
        }

        // 如果玩家尝试开启飞行
        if (event.isFlying()) {
            // 检查是否持有禁止物品
            if (flightManager.isHoldingBannedItem(player)) {
                event.setCancelled(true);
                flightManager.cancelFlight(player);
            }
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        // 检查绕过权限
        if (plugin.hasBypassPermission(player.getUniqueId())) {
            return;
        }

        // 如果玩家正在飞行且交换物品后持有禁止物品
        if (player.isFlying()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (flightManager.isHoldingBannedItem(player)) {
                    flightManager.cancelFlight(player);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();

            // 检查绕过权限
            if (plugin.hasBypassPermission(player.getUniqueId())) {
                return;
            }

            // 只有玩家真正在空中（不在安全位置）且持有禁止物品时才取消攻击
            if (flightManager.isFalling(player.getUniqueId()) &&
                    flightManager.isHoldingBannedItem(player)) {

                // 检查玩家是否真正在空中（不在安全位置）
                if (!flightManager.isInSafePosition(player)) {
                    // 取消攻击伤害
                    event.setCancelled(true);
                    // 使用ActionBar发送消息
                    sendActionBar(player, "§c坠落时无法攻击！");
                } else {
                    // 玩家已经在安全位置，应该恢复飞行并允许攻击
                    flightManager.restoreFlight(player);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // 如果玩家没有移动位置，跳过
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        // 检查绕过权限
        if (plugin.hasBypassPermission(player.getUniqueId())) {
            return;
        }

        // 如果玩家处于坠落状态，检查是否落地
        if (flightManager.isFalling(player.getUniqueId())) {
            // 玩家已经落地就恢复飞行（不再检查是否持有禁止物品）
            if (flightManager.isInSafePosition(player)) {
                flightManager.restoreFlight(player);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();

            // 检查绕过权限
            if (plugin.hasBypassPermission(player.getUniqueId())) {
                return;
            }

            // 如果玩家正在飞行，检查是否装备了禁止物品
            if (player.isFlying()) {
                // 延迟检查，因为物品交换可能发生在事件之后
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (flightManager.isHoldingBannedItem(player)) {
                        flightManager.cancelFlight(player);
                    }
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 检查玩家是否有绕过权限
        if (player.hasPermission("noweaponflight.bypass")) {
            plugin.setBypass(player.getUniqueId(), true);
        }

        // 如果玩家持有禁止物品，确保不能飞行
        if (flightManager.isHoldingBannedItem(player) && player.isFlying()) {
            flightManager.cancelFlight(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 恢复玩家的飞行状态
        flightManager.restoreFlight(player);

        // 移除绕过权限
        plugin.setBypass(player.getUniqueId(), false);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // 检查绕过权限
        if (plugin.hasBypassPermission(player.getUniqueId())) {
            return;
        }

        // 如果玩家换到新世界时持有禁止物品且正在飞行，取消飞行
        if (flightManager.isHoldingBannedItem(player) && player.isFlying()) {
            flightManager.cancelFlight(player);
        }
    }

    /**
     * 发送ActionBar消息
     */
    private void sendActionBar(Player player, String message) {
        // 使用Bukkit的API发送ActionBar
        player.sendActionBar(message);
    }
}