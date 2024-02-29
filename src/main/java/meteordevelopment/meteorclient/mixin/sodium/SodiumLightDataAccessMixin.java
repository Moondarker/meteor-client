/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin.sodium;

import me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Fullbright;
import meteordevelopment.meteorclient.systems.modules.render.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LightDataAccess.class, remap = false)
public class SodiumLightDataAccessMixin {
    @Unique
    private static final int BLOCK_OFFSET = 4;
    @Unique
    private static final int SKY_OFFSET = 20;
    @Unique
    private static final int FULL_LIGHT = 15 << BLOCK_OFFSET;

    @Shadow
    protected BlockRenderView world;
    @Shadow @Final
    private BlockPos.Mutable pos;

    @Unique
    private Xray xray;

    @Unique
    private Fullbright fb;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        xray = Modules.get().get(Xray.class);
        fb = Modules.get().get(Fullbright.class);
    }

    @ModifyVariable(method = "compute", at = @At(value = "TAIL"), name = "lm")
    private int compute_modifyLM(int lm) {
        if (xray.isActive()) {
            BlockState state = world.getBlockState(pos);
            if (!xray.isBlocked(state.getBlock(), pos)) return lm | FULL_LIGHT;
        }

        return lm;
    }

    // fullbright

    @ModifyVariable(method = "compute", at = @At(value = "STORE"), name = "lm")
    private int compute_assignLM(int lm) {
        if (fb.isActive()) {
            int sl = Math.max(fb.getLuminance(LightType.SKY), lm >> SKY_OFFSET);
            int bl = Math.max(fb.getLuminance(LightType.BLOCK), (lm >> BLOCK_OFFSET) & 0xFF);
            return sl << SKY_OFFSET | bl << BLOCK_OFFSET;
        }

        return lm;
    }
}
