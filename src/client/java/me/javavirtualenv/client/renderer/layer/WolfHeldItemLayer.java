package me.javavirtualenv.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.javavirtualenv.behavior.shared.AnimalItemStorage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Render layer for displaying items carried by wolves in their mouth.
 * <p>
 * Similar to FoxHeldItemLayer, this layer checks the wolf's AnimalItemStorage
 * component and renders any carried item at the appropriate position on the
 * wolf's head model.
 */
@Environment(EnvType.CLIENT)
public class WolfHeldItemLayer extends RenderLayer<Wolf, WolfModel<Wolf>> {
    private static final String STORAGE_KEY = "wolf_item_storage";
    private final ItemInHandRenderer itemInHandRenderer;

    public WolfHeldItemLayer(RenderLayerParent<Wolf, WolfModel<Wolf>> renderLayerParent, ItemInHandRenderer itemInHandRenderer) {
        super(renderLayerParent);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, Wolf wolf, float f, float g, float h, float j, float k, float l) {
        ItemStack carriedItem = getCarriedItem(wolf);
        if (carriedItem.isEmpty()) {
            return;
        }

        boolean isSitting = wolf.isInSittingPose();
        boolean isBaby = wolf.isBaby();

        poseStack.pushPose();

        // Scale down for baby wolves
        if (isBaby) {
            float scale = 0.75F;
            poseStack.scale(scale, scale, scale);
            poseStack.translate(0.0F, 0.5F, 0.209375F);
        }

        // Position at the head
        poseStack.translate(this.getParentModel().head.x / 16.0F, this.getParentModel().head.y / 16.0F, this.getParentModel().head.z / 16.0F);

        // Apply head rotations
        float headRoll = wolf.getHeadRollAngle(h);
        poseStack.mulPose(Axis.ZP.rotation(headRoll));
        poseStack.mulPose(Axis.YP.rotationDegrees(k));
        poseStack.mulPose(Axis.XP.rotationDegrees(l));

        // Position item in mouth - adjusted for wolf snout shape
        if (isBaby) {
            if (isSitting) {
                poseStack.translate(0.4F, 0.26F, 0.15F);
            } else {
                poseStack.translate(0.06F, 0.26F, -0.5F);
            }
        } else {
            if (isSitting) {
                poseStack.translate(0.46F, 0.26F, 0.22F);
            } else {
                poseStack.translate(0.06F, 0.27F, -0.5F);
            }
        }

        // Rotate item to appear held in mouth
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        if (isSitting) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }

        // Render the item
        this.itemInHandRenderer.renderItem(wolf, carriedItem, ItemDisplayContext.GROUND, false, poseStack, multiBufferSource, i);

        poseStack.popPose();
    }

    /**
     * Retrieve the item from wolf's AnimalItemStorage component.
     */
    private ItemStack getCarriedItem(Wolf wolf) {
        try {
            AnimalItemStorage storage = AnimalItemStorage.get(wolf, STORAGE_KEY);
            if (storage.hasItem()) {
                return storage.getItem();
            }
        } catch (Exception e) {
            // If storage component is not available or fails, return empty
        }
        return ItemStack.EMPTY;
    }
}
