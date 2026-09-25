package dev.syrup.sao.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V"
        ),
        require = 0
    )
    private void silenceStandingOnAir(Logger instance, String message, Object arg) {
        if (message != null && message.contains("standing on air")) {
            return;
        }
        instance.info(message, arg);
    }
}
