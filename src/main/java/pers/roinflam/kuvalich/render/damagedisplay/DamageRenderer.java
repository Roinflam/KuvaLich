package pers.roinflam.kuvalich.render.damagedisplay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class DamageRenderer {
    private final List<DamageInfo> damageInfos = new ArrayList<>();
    private static DamageRenderer instance;

    public static DamageRenderer getInstance() {
        if (instance == null) {
            instance = new DamageRenderer();
            MinecraftForge.EVENT_BUS.register(instance); // 注册事件
        }
        return instance;
    }

    public void addDamageInfo(DamageInfo info) {
        synchronized (damageInfos) {
            damageInfos.add(info);
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (Minecraft.getMinecraft().world.isRemote) {
            renderDamageInfos(event.getPartialTicks());
        }
    }

    private void renderDamageInfos(float partialTicks) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        synchronized (damageInfos) {
            long currentTime = System.currentTimeMillis();
            damageInfos.removeIf(info -> currentTime > info.endTime);

            for (DamageInfo info : damageInfos) {
                double x = info.position.x - playerX;
                double y = info.position.y - playerY + (currentTime - info.startTime) / 4000.0; // 降低上升速度
                double z = info.position.z - playerZ;
                renderDamageText(info.damage, info.color, x, y, z, partialTicks);
            }
        }
    }

    private void renderDamageText(float damage, int color, double x, double y, double z, float partialTicks) {
        RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.glNormal3f(0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        if (color == DamageInfo.DamageColor.WHITE.getColor()) {
            GlStateManager.scale(-0.03F, -0.03F, 0.03F);
        } else if (color == DamageInfo.DamageColor.YELLOW.getColor() || color == DamageInfo.DamageColor.BLUE.getColor()) {
            GlStateManager.scale(-0.04F, -0.04F, 0.04F);
        } else if (color == DamageInfo.DamageColor.ORANGE.getColor()) {
            GlStateManager.scale(-0.05F, -0.05F, 0.05F);
        } else if (color == DamageInfo.DamageColor.RED.getColor()) {
            GlStateManager.scale(-0.06F, -0.06F, 0.06F);
        }
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        int stringWidth = fontRenderer.getStringWidth(String.format("%.2f", damage));
        fontRenderer.drawString(String.format("%.2f", damage), -stringWidth / 2, 0, color, true);

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

}
