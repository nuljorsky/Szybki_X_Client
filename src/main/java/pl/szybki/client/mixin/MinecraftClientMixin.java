package pl.szybki.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.szybki.client.Mods;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow private int itemUseCooldown;

    /** Hitboxes: bicie przez ściany – tylko gdy sam klikniesz atak. */
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void szybki$attack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (Mods.HITBOXES == null || !Mods.HITBOXES.enabled) return;
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) return; // normalny cel
        Entity e = Mods.HITBOXES.find(mc);
        if (e == null) return;
        mc.interactionManager.attackEntity(mc.player, e);
        mc.player.swingHand(Hand.MAIN_HAND);
        cir.setReturnValue(true);
    }

    /** FastPlace: zero opóźnienia między postawieniami bloków. */
    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void szybki$use(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (Mods.FASTPLACE != null && Mods.FASTPLACE.active(mc)) this.itemUseCooldown = 0;
    }
}
