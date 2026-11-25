package pl.szybki.client.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.szybki.client.Mods;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    /** Nametags PRO: chowa zwykłe nametagi graczy (zwierzaki i hologramy zostają). */
    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void szybki$label(EntityRenderState state, Text text, MatrixStack matrices,
                              VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (Mods.NAMETAGS != null && Mods.NAMETAGS.enabled && Mods.NAMETAGS.hideVanilla.v
                && state instanceof PlayerEntityRenderState) {
            ci.cancel();
        }
    }
}
