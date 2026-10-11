package com.glisco.isometricrenders.util;

import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.glisco.isometricrenders.render.AreaRenderable;
import com.glisco.isometricrenders.screen.RenderScreen;
import com.glisco.isometricrenders.screen.ScreenScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class AreaSelectionHelper {

    public static BlockPos pos1 = null;
    public static BlockPos pos2 = null;

    public static boolean shouldDraw() {
        return pos1 != null;
    }

    public static void clear() {
        AreaSelectionHelper.pos1 = null;
        AreaSelectionHelper.pos2 = null;
        Translate.actionBar("selection_cleared");
    }

    public static void renderSelectionBox() {
        if (!AreaSelectionHelper.shouldDraw()) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;

        BlockPos origin = AreaSelectionHelper.pos1;

        HitResult result = player.pick(player.getAbilities().instabuild ? 5.0F : 4.5F, 0, false);
        BlockPos size = AreaSelectionHelper.pos2 != null ? AreaSelectionHelper.pos2 : (result.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) result).getBlockPos() : BlockPos.containing(result.getLocation()));
        size = size.subtract(origin);

        origin = origin.offset(size.getX() < 0 ? 1 : 0, size.getY() < 0 ? 1 : 0, size.getZ() < 0 ? 1 : 0);
        size = size.offset(size.getX() >= 0 ? 1 : -1, size.getY() >= 0 ? 1 : -1, size.getZ() >= 0 ? 1 : -1);

        final var box = new AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin.offset(size)));
        try (var collection = client.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            Gizmos.cuboid(box, GizmoStyle.stroke(0xFFFFFFFF));
        }
    }

    public static void select() {
        final Minecraft client = Minecraft.getInstance();
        final HitResult target = client.hitResult;
        if ((target == null)) return;
        BlockPos targetPos = new BlockPos(target.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) target).getBlockPos() : BlockPos.containing(target.getLocation()));

        if (pos1 == null) {
            pos1 = targetPos;
            Translate.actionBar("selection_started");
        } else {
            Translate.actionBar("selection_finished");
            pos2 = targetPos;
        }
    }

    public static boolean tryOpenScreen() {
        if (pos1 == null || pos2 == null) return false;

        ScreenScheduler.schedule(new RenderScreen(
                AreaRenderable.of(pos1, pos2)
        ));
        return true;
    }
}
