package top.miragedge.noweaponflight;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NoWeaponFlightPlugin extends JavaPlugin {

    private FlightManager flightManager;
    private Set<UUID> bypassPlayers;

    @Override
    public void onEnable() {
        // 保存默认配置
        // saveDefaultConfig();

        // 初始化管理器
        this.flightManager = new FlightManager(this);
        this.bypassPlayers = new HashSet<>();

        // 注册事件监听器
        Bukkit.getPluginManager().registerEvents(new PlayerFlightListener(this), this);

        // 启动定时任务检查飞行状态（每 tick 检查一次）
        Bukkit.getScheduler().runTaskTimer(this, flightManager::checkFlyingPlayers, 0L, 5L);

        getLogger().info("NoWeaponFlight 插件已启用！");
        getLogger().info("禁止手持重锤或矛时飞行");
    }

    @Override
    public void onDisable() {
        // 清理所有玩家的飞行状态
        if (flightManager != null) {
            flightManager.cleanup();
        }
        getLogger().info("NoWeaponFlight 插件已禁用！");
    }

    public FlightManager getFlightManager() {
        return flightManager;
    }

    public boolean hasBypassPermission(UUID playerId) {
        return bypassPlayers.contains(playerId);
    }

    public void setBypass(UUID playerId, boolean bypass) {
        if (bypass) {
            bypassPlayers.add(playerId);
        } else {
            bypassPlayers.remove(playerId);
        }
    }
}