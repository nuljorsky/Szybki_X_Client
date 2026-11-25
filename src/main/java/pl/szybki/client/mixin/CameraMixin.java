package pl.szybki.client.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.szybki.client.Mods;
import pl.szybki.client.SzybkiClient;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);

    /** Freecam: kamera w osobnym miejscu niż postać. */
    @Inject(method = "update", at = @At("TAIL"))
    private void szybki$update(CallbackInfo ci) {
        if (Mods.FREECAM != null && Mods.FREECAM.enabled) {
            Vec3d p = Mods.FREECAM.lerped(SzybkiClient.partial());
            this.setPos(p.x, p.y, p.z);
        }
    }

    /** Freecam: widać własną postać (tryb jak 3. osoba). */
    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void szybki$third(CallbackInfoReturnable<Boolean> cir) {
        if (Mods.FREECAM != null && Mods.FREECAM.enabled) cir.setReturnValue(true);
    }
}
