package pers.roinflam.kuvalich.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 原版 {@code PlayerList.save(ServerPlayer)} 是 protected。终结落定后要马上把这一个玩家的数据写盘，
 * 东西离开玩家文件之前盘上必须已经是落定后的状态。Mojmap 名，由 Mixin 重映射
 */
@Mixin(PlayerList.class)
public interface PlayerListSaveInvoker {

    @Invoker("save")
    void kuvalich$save(ServerPlayer player);
}
