package pl.szybki.client.mixin;

import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pl.szybki.client.Mods;

@Mixin(TextVisitFactory.class)
public abstract class TextVisitFactoryMixin {
    /** NameProtect: podmiana Twojego nicku w każdym rysowanym tekście. */
    @ModifyVariable(
            method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
            at = @At("HEAD"), argsOnly = true)
    private static String szybki$nameProtect(String text) {
        return Mods.NAMEPROTECT == null ? text : Mods.NAMEPROTECT.replace(text);
    }
}
