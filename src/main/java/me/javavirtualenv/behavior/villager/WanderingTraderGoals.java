package me.javavirtualenv.behavior.villager;

import me.javavirtualenv.ecology.api.EcologyAccess;
import me.javavirtualenv.mixin.villager.WanderingTraderMixin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Goal for wandering trader to seek villages and interact with villagers.
 */
class SeekVillageGoal extends Goal {
    private final WanderingTrader trader;
    private BlockPos targetVillage;
    private int searchCooldown = 0;

    private static final int SEARCH_COOLDOWN_TICKS = 600;
    private static final int VILLAGE_DETECTION_RANGE = 64;

    public SeekVillageGoal(WanderingTrader trader) {
        this.trader = trader;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return searchCooldown == 0 && targetVillage == null;
    }

    @Override
    public boolean canContinueToUse() {
        return targetVillage != null && trader.position().distanceTo(
            new Vec3(targetVillage.getX() + 0.5, targetVillage.getY(), targetVillage.getZ() + 0.5)
        ) > 8.0;
    }

    @Override
    public void start() {
        targetVillage = findNearestVillage();
    }

    @Override
    public void stop() {
        targetVillage = null;
        searchCooldown = SEARCH_COOLDOWN_TICKS;
    }

    @Override
    public void tick() {
        if (targetVillage != null) {
            trader.getNavigation().moveTo(
                targetVillage.getX() + 0.5,
                targetVillage.getY(),
                targetVillage.getZ() + 0.5,
                0.5
            );
        }
    }

    /**
     * Finds the nearest village by looking for bells or beds.
     */
    private BlockPos findNearestVillage() {
        BlockPos center = trader.blockPosition();

        for (BlockPos pos : BlockPos.betweenClosed(
            center.offset(-VILLAGE_DETECTION_RANGE, -8, -VILLAGE_DETECTION_RANGE),
            center.offset(VILLAGE_DETECTION_RANGE, 8, VILLAGE_DETECTION_RANGE)
        )) {
            if (trader.level().getBlockState(pos).is(Blocks.BELL)) {
                return pos.immutable();
            }
        }

        return null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }
}

/**
 * Goal for wandering trader to socialize with villagers and gather gossip.
 */
class SocializeWithVillagersGoal extends Goal {
    private final WanderingTrader trader;
    private final GossipSystem gossipSystem;
    private Villager socialPartner;
    private int socializeTicks = 0;

    private static final int SOCIALIZE_DURATION = 200;

    public SocializeWithVillagersGoal(WanderingTrader trader, GossipSystem gossipSystem) {
        this.trader = trader;
        this.gossipSystem = gossipSystem;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return socializeTicks == 0 && trader.getRandom().nextDouble() < 0.02;
    }

    @Override
    public boolean canContinueToUse() {
        return socializeTicks < SOCIALIZE_DURATION &&
               socialPartner != null &&
               socialPartner.isAlive();
    }

    @Override
    public void start() {
        socialPartner = findNearbyVillager();
        socializeTicks = 0;
    }

    @Override
    public void stop() {
        if (socialPartner != null) {
            exchangeGossip(socialPartner);
        }
        socialPartner = null;
        socializeTicks = 0;
    }

    @Override
    public void tick() {
        if (socialPartner == null) {
            return;
        }

        socializeTicks++;

        // Look at partner
        trader.getLookControl().setLookAt(socialPartner);

        // Exchange gossip periodically
        if (socializeTicks % 60 == 0) {
            exchangeGossip(socialPartner);
        }

        // Move closer if too far
        double distance = trader.position().distanceTo(socialPartner.position());
        if (distance > 3.0) {
            trader.getNavigation().moveTo(
                socialPartner.getX(),
                socialPartner.getY(),
                socialPartner.getZ(),
                0.5
            );
        }
    }

    /**
     * Finds a nearby villager to socialize with.
     */
    private Villager findNearbyVillager() {
        var nearby = trader.level().getEntitiesOfClass(
            Villager.class,
            trader.getBoundingBox().inflate(16.0)
        );

        if (nearby.isEmpty()) {
            return null;
        }

        return nearby.get(trader.getRandom().nextInt(nearby.size()));
    }

    /**
     * Exchanges gossip with a villager.
     */
    private void exchangeGossip(Villager villager) {
        GossipSystem villagerGossip = ((EcologyAccess) villager).betterEcology$getGossipSystem();
        if (villagerGossip != null) {
            // Trader shares gossip with villager
            gossipSystem.spreadGossip(villager);

            // Villager shares gossip with trader (need to get trader as Villager for API compatibility)
            // Note: WanderingTrader extends AbstractVillager, not Villager, so direct spread not possible
            // This would require additional API support
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }
}

/**
 * Goal for wandering trader to prefer trading with high-reputation players.
 */
class PreferentialTradingGoal extends Goal {
    private final WanderingTrader trader;
    private final TradingReputation reputation;
    private Player preferredCustomer;

    public PreferentialTradingGoal(WanderingTrader trader, TradingReputation reputation) {
        this.trader = trader;
        this.reputation = reputation;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return trader.isTrading();
    }

    @Override
    public boolean canContinueToUse() {
        return trader.isTrading();
    }

    @Override
    public void start() {
        preferredCustomer = findPreferredCustomer();
    }

    @Override
    public void stop() {
        preferredCustomer = null;
    }

    @Override
    public void tick() {
        // Look at preferred customer
        if (preferredCustomer != null && preferredCustomer.isAlive()) {
            trader.getLookControl().setLookAt(preferredCustomer);
        }
    }

    /**
     * Finds the highest reputation customer nearby.
     */
    private Player findPreferredCustomer() {
        var nearby = trader.level().getEntitiesOfClass(
            Player.class,
            trader.getBoundingBox().inflate(8.0)
        );

        if (nearby.isEmpty()) {
            return null;
        }

        Player best = null;
        int highestReputation = -1;

        for (Player player : nearby) {
            TradingReputation.PlayerReputation rep = reputation.getPlayerReputation(player.getUUID());
            if (rep != null && rep.getReputation() > highestReputation) {
                highestReputation = rep.getReputation();
                best = player;
            }
        }

        return best;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }
}
