/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.ApplyTransformationEvent;
import meteordevelopment.meteorclient.events.render.RenderItemEntityEvent;
import meteordevelopment.meteorclient.mixininterface.IBakedQuad;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;

public class ItemPhysics extends Module {
    private static final Direction[] FACES = { null, Direction.UP, Direction.DOWN, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST };
    private static final float PIXEL_SIZE = 1f / 16f;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> randomRotation = sgGeneral.add(new BoolSetting.Builder()
            .name("random-rotation")
            .description("Adds a random rotation to every item.")
            .defaultValue(true)
            .build()
    );

    private final Random random = Random.createLocal();
    private boolean renderingItem;

    public ItemPhysics() {
        super(Categories.Render, "item-physics", "Applies physics to items on the ground.");
    }

    @EventHandler
    private void onRenderItemEntity(RenderItemEntityEvent event) {
        MatrixStack matrices = event.matrixStack;
        matrices.push();

        ItemStack itemStack = event.itemEntity.getStack();
        int seed = itemStack.isEmpty() ? 187 : Item.getRawId(itemStack.getItem()) + itemStack.getDamage();
        event.random.setSeed(seed);

        event.matrixStack.push();

        BakedModel bakedModel = event.itemRenderer.getModel(itemStack, event.itemEntity.world, null, 0);
        boolean hasDepthInGui = bakedModel.hasDepth();
        int renderCount = getRenderedAmount(itemStack);
        IItemEntity rotator = (IItemEntity) event.itemEntity;
        boolean renderBlockFlat = false;

        if (event.itemEntity.getStack().getItem() instanceof BlockItem && !(event.itemEntity.getStack().getItem() instanceof AliasedBlockItem)) {
            Block b = ((BlockItem) event.itemEntity.getStack().getItem()).getBlock();
            VoxelShape shape = b.getOutlineShape(b.getDefaultState(), event.itemEntity.world, event.itemEntity.getBlockPos(), ShapeContext.absent());

            if (shape.getMax(Direction.Axis.Y) <= .5) renderBlockFlat = true;
        }

        Item item = event.itemEntity.getStack().getItem();
        if (item instanceof BlockItem && !(item instanceof AliasedBlockItem) && !renderBlockFlat) {
            event.matrixStack.translate(0, -0.06, 0);
        }

        if (!renderBlockFlat) {
            event.matrixStack.translate(0, .185, .0);
            event.matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(1.571F));
            event.matrixStack.translate(0, -.185, -.0);
        }

        boolean isAboveWater = event.itemEntity.world.getBlockState(event.itemEntity.getBlockPos()).getFluidState().getFluid().isIn(FluidTags.WATER);
        if (!event.itemEntity.isOnGround() && (!event.itemEntity.isSubmergedInWater() && !isAboveWater)) {
            float rotation = ((float) event.itemEntity.getItemAge() + event.tickDelta) / 20.0F + event.itemEntity.uniqueOffset; // calculate rotation based on age and ticks

            if (!renderBlockFlat) {
                event.matrixStack.translate(0, .185, .0);
                event.matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
                event.matrixStack.translate(0, -.185, .0);
                rotator.setRotation(new Vec3d(0, 0, rotation));
            } else {
                event.matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
                rotator.setRotation(new Vec3d(0, rotation, 0));
                event.matrixStack.translate(0, -.065, 0);
            }

            if (event.itemEntity.getStack().getItem() instanceof AliasedBlockItem) {
                event.matrixStack.translate(0, 0, .195);
            } else if (!(event.itemEntity.getStack().getItem() instanceof BlockItem)) {
                event.matrixStack.translate(0, 0, .195);
            }
        } else if (event.itemEntity.getStack().getItem() instanceof AliasedBlockItem) {
            event.matrixStack.translate(0, .185, .0);
            event.matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotator.getRotation().z));
            event.matrixStack.translate(0, -.185, .0);
            event.matrixStack.translate(0, 0, .195);
        } else if (renderBlockFlat) {
            event.matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotator.getRotation().y));
            event.matrixStack.translate(0, -.065, 0);
        } else {
            if (!(event.itemEntity.getStack().getItem() instanceof BlockItem)) {
                event.matrixStack.translate(0, 0, .195);
            }

            event.matrixStack.translate(0, .185, .0);
            event.matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotator.getRotation().z));
            event.matrixStack.translate(0, -.185, .0);
        }

        if (event.itemEntity.world.getBlockState(event.itemEntity.getBlockPos()).getBlock().equals(Blocks.SOUL_SAND)) {
            event.matrixStack.translate(0, 0, -.1);
        }

        if (event.itemEntity.getStack().getItem() instanceof BlockItem) {
            if (((BlockItem) event.itemEntity.getStack().getItem()).getBlock() instanceof SkullBlock) {
                event.matrixStack.translate(0, .11, 0);
            }
        }

        float scaleX = bakedModel.getTransformation().ground.scale.x;
        float scaleY = bakedModel.getTransformation().ground.scale.y;
        float scaleZ = bakedModel.getTransformation().ground.scale.z;

        float x;
        float y;
        if (!hasDepthInGui) {
            float r = -0.0F * (float) (renderCount) * 0.5F * scaleX;
            x = -0.0F * (float) (renderCount) * 0.5F * scaleY;
            y = -0.09375F * (float) (renderCount) * 0.5F * scaleZ;
            event.matrixStack.translate(r, x, y);
        }

        for (int u = 0; u < renderCount; ++u) {
            event.matrixStack.push();
            if (u > 0) {
                if (hasDepthInGui) {
                    x = (event.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    y = (event.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float z = (event.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    event.matrixStack.translate(x, y, z);
                } else {
                    x = (event.random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                    y = (event.random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                    event.matrixStack.translate(x, y, 0.0D);
                    event.matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(event.random.nextFloat()));
                }
            }

            event.itemRenderer.renderItem(itemStack, ModelTransformationMode.GROUND, false, matrices, event.vertexConsumerProvider, event.light, OverlayTexture.DEFAULT_UV, model);

            matrices.pop();

            float y = Math.max(random.nextFloat() * PIXEL_SIZE, PIXEL_SIZE / 2f);
            translate(matrices, info, 0, y, 0);
        }

        renderingItem = false;
    }

    private void translate(MatrixStack matrices, ModelInfo info, float x, float y, float z) {
        if (info.flat) {
            float temp = y;
            y = z;
            z = -temp;
        }

        matrices.translate(x, y, z);
    }

    private int getRenderedCount(ItemStack stack) {
        int i = 1;

        if (stack.getCount() > 48) i = 5;
        else if (stack.getCount() > 32) i = 4;
        else if (stack.getCount() > 16) i = 3;
        else if (stack.getCount() > 1) i = 2;

        return i;
    }

    private void applyTransformation(MatrixStack matrices, BakedModel model) {
        Transformation transformation = model.getTransformation().ground;

        float prevY = transformation.translation.y;
        transformation.translation.y = 0;

        transformation.apply(false, matrices);

        transformation.translation.y = prevY;
    }

    private void offsetInWater(MatrixStack matrices, ItemEntity entity) {
        if (entity.isTouchingWater()) {
            matrices.translate(0, 0.333f, 0);
        }
    }

    private void preventZFighting(MatrixStack matrices, ItemEntity entity) {
        float offset = 0.0001f;

        float distance = (float) mc.gameRenderer.getCamera().getPos().distanceTo(entity.getPos());
        offset = Math.min(offset * Math.max(1, distance), 0.01f); // Ensure distance is at least 1 and that final offset is not bigger than 0.01

        matrices.translate(0, offset, 0);
    }

    private BakedModel getModel(ItemEntity entity) {
        ItemStack itemStack = entity.getStack();

        // Mojang be like
        if (itemStack.isOf(Items.TRIDENT)) return mc.getItemRenderer().getModels().getModelManager().getModel(ItemRenderer.TRIDENT);
        if (itemStack.isOf(Items.SPYGLASS)) return mc.getItemRenderer().getModels().getModelManager().getModel(ItemRenderer.SPYGLASS);

        return mc.getItemRenderer().getModel(itemStack, entity.getWorld(), null, entity.getId());
    }

    private ModelInfo getInfo(BakedModel model) {
        Random random = Random.createLocal();

        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;

        for (Direction face : FACES) {
            for (BakedQuad _quad : model.getQuads(null, face, random)) {
                IBakedQuad quad = (IBakedQuad) _quad;

                for (int i = 0; i < 4; i++) {
                    switch (_quad.getFace()) {
                        case DOWN -> minY = Math.min(minY, quad.meteor$getY(i));
                        case UP -> maxY = Math.max(maxY, quad.meteor$getY(i));
                        case NORTH -> minZ = Math.min(minZ, quad.meteor$getZ(i));
                        case SOUTH -> maxZ = Math.max(maxZ, quad.meteor$getZ(i));
                        case WEST -> minX = Math.min(minX, quad.meteor$getX(i));
                        case EAST -> maxX = Math.max(maxX, quad.meteor$getX(i));
                    }
                }
            }
        }

        if (minX == Float.MAX_VALUE) minX = 0;
        if (minY == Float.MAX_VALUE) minY = 0;
        if (minZ == Float.MAX_VALUE) minZ = 0;

        if (maxX == Float.MIN_VALUE) maxX = 1;
        if (maxY == Float.MIN_VALUE) maxY = 1;
        if (maxZ == Float.MIN_VALUE) maxZ = 1;

        float x = maxX - minX;
        float y = maxY - minY;
        float z = maxZ - minZ;

        boolean flat = (x > PIXEL_SIZE && y > PIXEL_SIZE && z <= PIXEL_SIZE);

        return new ModelInfo(flat, 0.5f - minY, minZ - minY);
    }

    record ModelInfo(boolean flat, float offsetY, float offsetZ) {}
}
