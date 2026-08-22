package com.warpmc.afk.storage;

public class SavedFake {
    public String uuid;
    public String name;
    public String world;
    public double x, y, z;
    public float yaw, pitch;
    public long timeoutMinutes;
    public String reason;
    public boolean damageDisabled;
    public boolean hidden;
    public boolean resetHealthOnDeath;
    public long spawnTimeMillis;
    // Persisted player state so restart doesn't reset to starter kit / survival
    public String gameMode;
    public double health;
    public int foodLevel;
    public float saturation;
    public float exhaustion;
    public float exp;
    public int level;
    public int totalExperience;
    // ItemStacks serialized as Map<String,Object> lists (Bukkit ConfigurationSerializable)
    public java.util.List<java.util.Map<String, Object>> inventory;
    public java.util.List<java.util.Map<String, Object>> armor;
    public java.util.Map<String, Object> offHand;
    public java.util.List<java.util.Map<String, Object>> enderChest;
    public java.util.List<java.util.Map<String, Object>> potionEffects;
}
