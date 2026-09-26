package dev.syrup.sao.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Shadow
    private void forceSendPlayerSupportBlocks() {
        throw new AssertionError();
    }

    @Unique
    private boolean sao$pendingSupportBlockCheck = false;

    @Inject(method = "handlePlayerPositionChange", at = @At("HEAD"))
    private void sao$resetCheckFlagOnHead(CallbackInfo ci) {
        this.sao$pendingSupportBlockCheck = false;
    }

    @Inject(method = "handlePlayerPositionChange", at = @At("RETURN"))
    private void sao$resetCheckFlagOnReturn(CallbackInfo ci) {
        this.sao$pendingSupportBlockCheck = false;
    }

    @WrapOperation(
        method = "handlePlayerPositionChange",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;forceSendPlayerSupportBlocks()V"
        )
    )
    private void sao$deferSupportBlockCheck(ServerGamePacketListenerImpl instance, Operation<Void> original) {
        this.sao$pendingSupportBlockCheck = true;
    }

    @Inject(
        method = "handlePlayerPositionChange",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            shift = At.Shift.AFTER
        )
    )
    private void sao$runAfterPlayerMove(CallbackInfo ci) {
        if (this.sao$pendingSupportBlockCheck) {
            this.sao$pendingSupportBlockCheck = false;

            if (this.sao$isTrulyStandingOnAir()) {
                this.forceSendPlayerSupportBlocks();
            }
        }
    }

    @WrapOperation(
        method = "forceSendPlayerSupportBlocks",
        at = @At(
            value = "INVOKE",
            target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V"
        )
    )
    private void sao$wrapLogMessage(Logger logger, String message, Object arg, Operation<Void> original) {
        if (message != null && message.contains("standing on air")) {
            logger.debug(message, arg);
        } else {
            original.call(logger, message, arg);
        }
    }

    @Unique
    private boolean sao$isTrulyStandingOnAir() {
        if (this.player.isRemoved() || !this.player.isAlive()) return false;
        if (this.player.getAbilities().flying
                || this.player.isFallFlying()
                || this.player.isPassenger()
                || this.player.onClimbable()
                || this.player.isInWater()
                || this.player.isInLava()) return false;

        if (this.player.verticalCollisionBelow || this.player.onGround()) return false;

        AABB box = this.player.getBoundingBox();
        double insetX = Math.min(1.0E-4, (box.maxX - box.minX) * 0.25);
        double insetZ = Math.min(1.0E-4, (box.maxZ - box.minZ) * 0.25);

        AABB checkArea = new AABB(
            box.minX + insetX, box.minY - 0.1, box.minZ + insetZ,
            box.maxX - insetX, box.minY + 1.0E-4, box.maxZ - insetZ
        );

        if (!this.player.level().noCollision(this.player, checkArea)) return false;

        int minX = Mth.floor(checkArea.minX);
        int maxX = Mth.floor(checkArea.maxX);
        int minZ = Mth.floor(checkArea.minZ);
        int maxZ = Mth.floor(checkArea.maxZ);
        int minY = Mth.floor(checkArea.minY);
        int maxY = Mth.floor(box.minY + 0.05);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = this.player.level().getBlockState(pos);
                    if (state.is(Blocks.MOVING_PISTON)
                            || state.is(Blocks.PISTON_HEAD)
                            || state.is(Blocks.SCAFFOLDING)) return false;
                }
            }
        }
        return true;
    }
}
