package me.zeroeightsix.kami.module.modules.dl;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.function.Predicate;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.opengl.Display;

@Module.Info(name="DL", category=Module.Category.DL, description="")
public class DL
extends Module {
    public Setting<Integer> x = this.register(Settings.i("X", 0));
    public Setting<Integer> y = this.register(Settings.i("Y", 0));
    public Setting<Integer> z = this.register(Settings.i("Z", 0));
    public Setting<Boolean> latency = this.register(Settings.booleanBuilder("latency").withValue(false).build());
    public Setting<Boolean> ignore = this.register(Settings.booleanBuilder("IgnoreLoaded").withValue(true).build());
    public Setting<Boolean> loadChunks = this.register(Settings.booleanBuilder("loadChunks").withValue(false).withVisibility(v -> this.ignore.getValue()).build());
    public Setting<Boolean> notify = this.register(Settings.b("Notifications", false));
    private ArrayList<ChunkPos> loaded_chunks = new ArrayList();
    public static final int MAX_DL_PPT = 15;
    public static DL INSTANCE;
    private long startTime = -1L;
    @EventHandler
    private final Listener<PacketEvent.Receive> receiveListener = new Listener<PacketEvent.Receive>(event -> {
        if (DL.mc.player == null || DL.mc.world == null) {
            return;
        }
        if (event.getPacket() instanceof SPacketBlockChange) {
            SPacketBlockChange packetIn = (SPacketBlockChange)event.getPacket();
            ChunkPos chunkPos = new ChunkPos(packetIn.getBlockPosition());
            if (this.ignore.getValue().booleanValue()) {
                if (!this.loaded_chunks.contains(chunkPos) && DL.mc.world.isBlockLoaded(packetIn.getBlockPosition(), false)) {
                    return;
                }
                if (this.notify.getValue().booleanValue() && !Display.isActive()) {
                    this.SendMessage("DL found something.");
                }
            }
            DecimalFormat df = new DecimalFormat("#.#");
            Vec3d pos1 = new Vec3d((double)DL.mc.player.getPosition().getX(), (double)packetIn.getBlockPosition().getY(), (double)DL.mc.player.getPosition().getZ());
            Command.ChatMessage msg = new Command.ChatMessage("&7[&aM&7] &r" + TextFormatting.RED + "[DL]: " + TextFormatting.RESET + packetIn.getBlockPosition().toString() + " -> " + packetIn.getBlockState().getBlock().getLocalizedName() + " (" + df.format(pos1.distanceTo(new Vec3d((Vec3i)packetIn.getBlockPosition()))) + ") " + (DL.mc.player.dimension == -1 ? "Nether" : ""));
            msg.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, ".dl " + packetIn.getBlockPosition().getX() + " " + packetIn.getBlockPosition().getZ()));
            DL.mc.player.sendMessage((ITextComponent)msg);
            if (this.latency.getValue().booleanValue() && this.startTime != -1L) {
                this.SendMessage("Latency = " + (System.currentTimeMillis() - this.startTime) + " ms");
                this.startTime = -1L;
            }
            if (this.loadChunks.getValue().booleanValue() && this.loadChunks.isVisible() && !this.loaded_chunks.contains(chunkPos)) {
                DL.mc.world.doPreChunk(chunkPos.x, chunkPos.z, true);
                this.loaded_chunks.add(chunkPos);
            }
        }
    }, new Predicate[0]);
    @EventHandler
    private Listener<FMLNetworkEvent.ClientConnectedToServerEvent> connectedToServerEventListener = new Listener<FMLNetworkEvent.ClientConnectedToServerEvent>(event -> this.unloadChunks(), new Predicate[0]);

    public DL() {
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (DL.mc.world == null) {
            return;
        }
        BlockPos pos = new BlockPos(this.x.getValue().intValue(), this.y.getValue().intValue(), this.z.getValue().intValue());
        this.startTime = System.currentTimeMillis();
        DL.mc.player.connection.sendPacket((Packet)new CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, EnumFacing.UP));
    }

    @Override
    public void onDisable() {
        this.startTime = -1L;
        this.unloadChunks();
    }

    private void unloadChunks() {
        if (DL.mc.world != null) {
            for (ChunkPos chunkPos : this.loaded_chunks) {
                DL.mc.world.doPreChunk(chunkPos.x, chunkPos.z, false);
            }
        }
        this.loaded_chunks.clear();
    }
}

