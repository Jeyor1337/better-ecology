package me.javavirtualenv.behavior.villager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages trading reputation between a villager and players.
 * Reputation affects prices and unlocks special trades.
 * Includes supply/demand system and special deals.
 */
public class TradingReputation {
    private final Mob trader;
    private final Map<UUID, PlayerReputation> playerReputations = new HashMap<>();
    private final Map<Item, SupplyDemandData> supplyDemand = new HashMap<>();
    private long lastSupplyUpdate = 0;
    private static final long SUPPLY_UPDATE_INTERVAL = 12000;

    public TradingReputation(Mob trader) {
        this.trader = trader;
        initializeSupplyDemand();
    }

    /**
     * Initializes supply/demand tracking for common trade items.
     */
    private void initializeSupplyDemand() {
        addSupplyDemandItem(Items.EMERALD, 100, 1.0);
        addSupplyDemandItem(Items.DIAMOND, 10, 1.5);
        addSupplyDemandItem(Items.IRON_INGOT, 50, 0.8);
        addSupplyDemandItem(Items.GOLD_INGOT, 30, 1.0);
        addSupplyDemandItem(Items.BREAD, 80, 0.5);
        addSupplyDemandItem(Items.ENCHANTED_BOOK, 5, 2.0);
    }

    /**
     * Adds an item to supply/demand tracking.
     */
    private void addSupplyDemandItem(Item item, int initialSupply, double demandMultiplier) {
        supplyDemand.put(item, new SupplyDemandData(initialSupply, demandMultiplier));
    }

    /**
     * Updates supply/demand data periodically.
     */
    public void updateSupplyDemand() {
        long currentTime = trader.level().getGameTime();
        if (currentTime - lastSupplyUpdate < SUPPLY_UPDATE_INTERVAL) {
            return;
        }
        lastSupplyUpdate = currentTime;

        for (SupplyDemandData data : supplyDemand.values()) {
            data.regenerateSupply();
        }
    }

    /**
     * Records a trade with a player and updates supply/demand.
     */
    public void recordTrade(UUID playerId, boolean wasSuccessful, int emeraldValue, Item tradedItem) {
        updateSupplyDemand();

        PlayerReputation reputation = getOrCreatePlayerReputation(playerId);
        reputation.totalTrades++;
        if (wasSuccessful) {
            reputation.successfulTrades++;
            reputation.reputation += Math.min(5, emeraldValue / 10);
            reputation.totalEmeraldsTraded += emeraldValue;
        }
        reputation.lastTradeTime = trader.level().getGameTime();

        // Update supply/demand for traded item
        if (tradedItem != null) {
            SupplyDemandData data = supplyDemand.get(tradedItem);
            if (data != null) {
                data.onTraded(wasSuccessful);
            }
        }
    }

    /**
     * Gets the price multiplier for a player based on reputation and supply/demand.
     */
    public float getPriceMultiplier(UUID playerId, Item tradeItem) {
        PlayerReputation reputation = getPlayerReputation(playerId);
        float multiplier = calculateReputationMultiplier(reputation);

        // Apply supply/demand modifier
        if (tradeItem != null) {
            SupplyDemandData data = supplyDemand.get(tradeItem);
            if (data != null) {
                multiplier *= data.getDemandMultiplier();
            }
        }

        return Math.max(0.5f, Math.min(2.0f, multiplier));
    }

    /**
     * Gets the reputation modifier for a player (discount/markup as a fraction).
     * Returns a value between -0.35 (35% discount) and 0 (no discount).
     */
    public float getReputationModifier(UUID playerId) {
        PlayerReputation reputation = getPlayerReputation(playerId);
        if (reputation == null) {
            return 0.0f;
        }
        // Return 1 - multiplier to get the discount as a modifier
        return 1.0f - calculateReputationMultiplier(reputation);
    }

    /**
     * Calculates price multiplier based on reputation only.
     */
    private float calculateReputationMultiplier(PlayerReputation reputation) {
        if (reputation == null) {
            return 1.0f;
        }

        float multiplier = 1.0f;

        // Reputation reduces price
        multiplier -= Math.min(0.3f, reputation.reputation / 500.0f);

        // Frequent customer bonus
        if (reputation.totalTrades >= 50) {
            multiplier -= 0.1f;
        } else if (reputation.totalTrades >= 20) {
            multiplier -= 0.05f;
        }

        // High spender bonus
        if (reputation.totalEmeraldsTraded >= 1000) {
            multiplier -= 0.05f;
        }

        return Math.max(0.5f, multiplier);
    }

    /**
     * Gets the supply/demand data for an item.
     */
    public SupplyDemandData getSupplyDemand(Item item) {
        return supplyDemand.get(item);
    }

    /**
     * Checks if a player qualifies for special deals.
     */
    public boolean hasSpecialDeals(UUID playerId) {
        PlayerReputation reputation = getPlayerReputation(playerId);
        if (reputation == null) {
            return false;
        }

        return reputation.reputation >= 100 && reputation.totalTrades >= 20;
    }

    /**
     * Checks if a rare deal should be offered.
     */
    public boolean shouldOfferRareDeal(UUID playerId) {
        if (!hasSpecialDeals(playerId)) {
            return false;
        }

        PlayerReputation reputation = getPlayerReputation(playerId);
        double baseChance = 0.01;

        // Higher reputation = better chance
        if (reputation.reputation >= 300) {
            baseChance = 0.02;
        }

        return trader.getRandom().nextDouble() < baseChance;
    }

    /**
     * Gets the customer tier for a player.
     */
    public CustomerTier getCustomerTier(UUID playerId) {
        PlayerReputation reputation = getPlayerReputation(playerId);
        if (reputation == null) {
            return CustomerTier.STRANGER;
        }

        if (reputation.reputation >= 300 && reputation.totalTrades >= 50) {
            return CustomerTier.VIP;
        } else if (reputation.reputation >= 100 && reputation.totalTrades >= 20) {
            return CustomerTier.REGULAR;
        } else if (reputation.reputation >= 20 && reputation.totalTrades >= 5) {
            return CustomerTier.CASUAL;
        }
        return CustomerTier.STRANGER;
    }

    /**
     * Gets or creates reputation data for a player.
     */
    public PlayerReputation getOrCreatePlayerReputation(UUID playerId) {
        return playerReputations.computeIfAbsent(playerId, id -> new PlayerReputation());
    }

    /**
     * Gets existing reputation for a player, or null if none exists.
     */
    public PlayerReputation getPlayerReputation(UUID playerId) {
        return playerReputations.get(playerId);
    }

    /**
     * Serializes reputation data to NBT.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        playerReputations.forEach((uuid, rep) -> {
            CompoundTag playerTag = rep.save();
            tag.put(uuid.toString(), playerTag);
        });

        CompoundTag supplyTag = new CompoundTag();
        int index = 0;
        for (Map.Entry<Item, SupplyDemandData> entry : supplyDemand.entrySet()) {
            CompoundTag itemTag = entry.getValue().save();
            itemTag.putString("Item", entry.getKey().toString());
            supplyTag.put("supply_" + index, itemTag);
            index++;
        }
        tag.put("SupplyDemand", supplyTag);
        tag.putLong("LastSupplyUpdate", lastSupplyUpdate);

        return tag;
    }

    /**
     * Loads reputation data from NBT.
     */
    public void load(CompoundTag tag) {
        playerReputations.clear();
        tag.getAllKeys().forEach(key -> {
            if (!key.equals("SupplyDemand") && !key.equals("LastSupplyUpdate")) {
                try {
                    UUID uuid = UUID.fromString(key);
                    PlayerReputation rep = new PlayerReputation();
                    rep.load(tag.getCompound(key));
                    playerReputations.put(uuid, rep);
                } catch (IllegalArgumentException e) {
                    // Invalid UUID, skip
                }
            }
        });

        if (tag.contains("SupplyDemand")) {
            CompoundTag supplyTag = tag.getCompound("SupplyDemand");
            for (String key : supplyTag.getAllKeys()) {
                if (key.startsWith("supply_")) {
                    CompoundTag itemTag = supplyTag.getCompound(key);
                    SupplyDemandData data = new SupplyDemandData();
                    data.load(itemTag);
                    // Note: Item loading would require Item registry lookup
                }
            }
        }

        lastSupplyUpdate = tag.getLong("LastSupplyUpdate");
    }

    /**
     * Reputation data for a single player.
     */
    public static class PlayerReputation {
        private int reputation = 0;
        private int totalTrades = 0;
        private int successfulTrades = 0;
        private int totalEmeraldsTraded = 0;
        private long lastTradeTime = 0;
        private long firstTradeTime = 0;

        public int getReputation() {
            return reputation;
        }

        public int getTotalTrades() {
            return totalTrades;
        }

        public int getSuccessfulTrades() {
            return successfulTrades;
        }

        public float getSuccessRate() {
            return totalTrades > 0 ? (float) successfulTrades / totalTrades : 0;
        }

        public int getTotalEmeraldsTraded() {
            return totalEmeraldsTraded;
        }

        public long getLastTradeTime() {
            return lastTradeTime;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Reputation", reputation);
            tag.putInt("TotalTrades", totalTrades);
            tag.putInt("SuccessfulTrades", successfulTrades);
            tag.putInt("TotalEmeraldsTraded", totalEmeraldsTraded);
            tag.putLong("LastTradeTime", lastTradeTime);
            tag.putLong("FirstTradeTime", firstTradeTime);
            return tag;
        }

        public void load(CompoundTag tag) {
            reputation = tag.getInt("Reputation");
            totalTrades = tag.getInt("TotalTrades");
            successfulTrades = tag.getInt("SuccessfulTrades");
            totalEmeraldsTraded = tag.getInt("TotalEmeraldsTraded");
            lastTradeTime = tag.getLong("LastTradeTime");
            firstTradeTime = tag.getLong("FirstTradeTime");
        }
    }

    /**
     * Supply and demand data for trade items.
     */
    public static class SupplyDemandData {
        private int supply;
        private double demandMultiplier;
        private int tradeCount = 0;

        public SupplyDemandData() {
            this(100, 1.0);
        }

        public SupplyDemandData(int supply, double demandMultiplier) {
            this.supply = supply;
            this.demandMultiplier = demandMultiplier;
        }

        public void onTraded(boolean wasSuccessful) {
            tradeCount++;
            if (wasSuccessful) {
                supply = Math.max(0, supply - 1);
                // Demand increases with more trades
                demandMultiplier = Math.min(3.0, demandMultiplier * 1.1);
            }
        }

        public void regenerateSupply() {
            // Slowly regenerate supply over time
            supply = Math.min(100, supply + 5);
            // Demand slowly decreases
            demandMultiplier = Math.max(0.5, demandMultiplier * 0.95);
        }

        public int getSupply() {
            return supply;
        }

        public double getDemandMultiplier() {
            return demandMultiplier;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Supply", supply);
            tag.putDouble("DemandMultiplier", demandMultiplier);
            tag.putInt("TradeCount", tradeCount);
            return tag;
        }

        public void load(CompoundTag tag) {
            supply = tag.getInt("Supply");
            demandMultiplier = tag.getDouble("DemandMultiplier");
            tradeCount = tag.getInt("TradeCount");
        }
    }

    /**
     * Customer tiers for trading benefits.
     */
    public enum CustomerTier {
        STRANGER("stranger", 1.0f),
        CASUAL("casual", 0.95f),
        REGULAR("regular", 0.85f),
        VIP("vip", 0.75f);

        private final String name;
        private final float baseDiscount;

        CustomerTier(String name, float baseDiscount) {
            this.name = name;
            this.baseDiscount = baseDiscount;
        }

        public String getName() {
            return name;
        }

        public float getBaseDiscount() {
            return baseDiscount;
        }
    }
}
