package me.javavirtualenv.ecology.handles;

import me.javavirtualenv.behavior.parrot.*;
import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.EcologyHandle;
import me.javavirtualenv.ecology.EcologyProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Handle for parrot-specific behaviors.
 * Manages mimicking, dancing, music detection, and perching.
 */
public class ParrotBehaviorHandle implements EcologyHandle {
    private MimicBehavior mimicBehavior;
    private MusicDetectionBehavior musicDetectionBehavior;
    private DanceBehavior danceBehavior;
    private PerchBehavior perchBehavior;

    private Goal mimicGoal;
    private Goal musicGoal;
    private Goal perchGoal;
    private Goal behaviorGoal;

    @Override
    public String id() {
        return "parrot_behavior";
    }

    @Override
    public boolean supports(EcologyProfile profile) {
        return profile != null && profile.getBool("parrot.enabled", false);
    }

    @Override
    public void initialize(Mob mob, EcologyComponent component, @Nullable EcologyProfile profile) {
        // Get configuration from profile or use defaults
        MimicBehavior.MimicConfig mimicConfig = loadMimicConfig(profile);
        MusicDetectionBehavior.MusicConfig musicConfig = loadMusicConfig(profile);
        DanceBehavior.DanceConfig danceConfig = loadDanceConfig(profile);
        PerchBehavior.PerchConfig perchConfig = loadPerchConfig(profile);

        // Initialize behaviors
        this.mimicBehavior = new MimicBehavior(mob, mimicConfig, component);
        this.musicDetectionBehavior = new MusicDetectionBehavior(mob, musicConfig, component);
        this.danceBehavior = new DanceBehavior(mob, danceConfig, component);
        this.perchBehavior = new PerchBehavior(mob, perchConfig, component);

        // Initialize goals
        if (mob instanceof PathfinderMob pathfinderMob) {
            this.mimicGoal = new ParrotMimicGoal(pathfinderMob, mimicBehavior, mimicConfig);
            this.musicGoal = new ParrotMusicGoal(pathfinderMob, musicDetectionBehavior, danceBehavior, musicConfig);
            this.perchGoal = new ParrotPerchGoal(pathfinderMob, perchBehavior, perchConfig);

            ParrotBehaviorGoal.ParrotBehaviorConfig behaviorConfig = loadBehaviorConfig(profile);
            this.behaviorGoal = new ParrotBehaviorGoal(
                pathfinderMob,
                mimicBehavior,
                musicDetectionBehavior,
                danceBehavior,
                perchBehavior,
                behaviorConfig
            );
        }

        // Initialize component data
        initializeComponentData(component);
    }

    private void initializeComponentData(EcologyComponent component) {
        // Initialize mimic data
        CompoundTag mimicData = component.getHandleTag("mimic");
        if (!mimicData.contains("accuracy")) {
            mimicData.putDouble("accuracy", 0.75);
        }
        component.setHandleTag("mimic", mimicData);

        // Initialize dance data
        CompoundTag danceData = component.getHandleTag("dance");
        if (!danceData.contains("is_dancing")) {
            danceData.putBoolean("is_dancing", false);
        }
        component.setHandleTag("dance", danceData);

        // Initialize perch data
        CompoundTag perchData = component.getHandleTag("perch");
        if (!perchData.contains("is_perched")) {
            perchData.putBoolean("is_perched", false);
        }
        component.setHandleTag("perch", perchData);

        // Initialize note block tracking
        CompoundTag noteData = component.getHandleTag("note_blocks");
        component.setHandleTag("note_blocks", noteData);
    }

    private MimicBehavior.MimicConfig loadMimicConfig(@Nullable EcologyProfile profile) {
        MimicBehavior.MimicConfig config = new MimicBehavior.MimicConfig();

        if (profile != null) {
            // Load from profile if available
            config.baseMimicAccuracy = profile.getDouble("mimic.base_accuracy", 0.75);
            config.mimicChance = profile.getDouble("mimic.chance", 0.3);
            config.warningRange = profile.getDouble("mimic.warning_range", 16.0);
        }

        return config;
    }

    private MusicDetectionBehavior.MusicConfig loadMusicConfig(@Nullable EcologyProfile profile) {
        MusicDetectionBehavior.MusicConfig config = new MusicDetectionBehavior.MusicConfig();

        if (profile != null) {
            config.detectionRadius = profile.getInt("music.detection_radius", 16);
            config.flightSpeed = profile.getDouble("music.flight_speed", 1.2);
        }

        return config;
    }

    private DanceBehavior.DanceConfig loadDanceConfig(@Nullable EcologyProfile profile) {
        DanceBehavior.DanceConfig config = new DanceBehavior.DanceConfig();

        if (profile != null) {
            config.showParticles = profile.getInt("dance.show_particles", 1) != 0;
            config.enablePartyEffect = profile.getInt("dance.enable_party_effect", 1) != 0;
            config.partyRadius = profile.getDouble("dance.party_radius", 8.0);
        }

        return config;
    }

    private PerchBehavior.PerchConfig loadPerchConfig(@Nullable EcologyProfile profile) {
        PerchBehavior.PerchConfig config = new PerchBehavior.PerchConfig();

        if (profile != null) {
            config.perchSearchRadius = profile.getInt("perch.search_radius", 16);
            config.preferHighPerches = profile.getInt("perch.prefer_high", 1) != 0;
            config.shoulderPerchRange = profile.getDouble("perch.shoulder_range", 2.0);
        }

        return config;
    }

    private ParrotBehaviorGoal.ParrotBehaviorConfig loadBehaviorConfig(@Nullable EcologyProfile profile) {
        ParrotBehaviorGoal.ParrotBehaviorConfig config = new ParrotBehaviorGoal.ParrotBehaviorConfig();

        if (profile != null) {
            config.enableMusicBehavior = profile.getInt("behavior.enable_music", 1) != 0;
            config.enablePerchBehavior = profile.getInt("behavior.enable_perch", 1) != 0;
            config.enableMimicBehavior = profile.getInt("behavior.enable_mimic", 1) != 0;
            config.perchSeekChance = profile.getDouble("behavior.perch_seek_chance", 0.1);
        }

        return config;
    }

    @Override
    public void registerGoals(Mob mob, EcologyComponent component, EcologyProfile profile) {
        // Goals are already registered in initialize() method
        // This override is kept for interface compliance
    }

    @Override
    public void tick(Mob mob, EcologyComponent component, EcologyProfile profile) {
        // Check for dance invitations from other parrots
        checkDanceInvitations(component);
    }

    private void checkDanceInvitations(EcologyComponent component) {
        CompoundTag danceData = component.getHandleTag("dance");

        if (danceData.getBoolean("should_start_dancing")) {
            String styleName = danceData.getString("invited_style");
            if (!styleName.isEmpty()) {
                try {
                    DanceBehavior.DanceStyle style = DanceBehavior.DanceStyle.valueOf(styleName);
                    if (danceBehavior != null) {
                        // Note: mob reference is not available in this context
                        // Position will be updated during behavior tick
                        danceBehavior.startDancing(style, null);
                    }
                } catch (IllegalArgumentException e) {
                    // Invalid dance style, ignore
                }
            }

            // Clear the invitation
            danceData.remove("should_start_dancing");
            danceData.remove("invited_style");
            component.setHandleTag("dance", danceData);
        }
    }

    public MimicBehavior getMimicBehavior() {
        return mimicBehavior;
    }

    public MusicDetectionBehavior getMusicDetectionBehavior() {
        return musicDetectionBehavior;
    }

    public DanceBehavior getDanceBehavior() {
        return danceBehavior;
    }

    public PerchBehavior getPerchBehavior() {
        return perchBehavior;
    }
}
