package me.zeroeightsix.kami.module.modules.dl;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.LagCompensator;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.opengl.Display;

@Module.Info(name="DLTracker", category=Module.Category.DL, description="a")
public class DLTracker
extends Module {
    public Setting<Integer> x = this.register(Settings.i("X", 0));
    public Setting<Integer> z = this.register(Settings.i("Z", 0));
    public Setting<Integer> timeout = this.register(Settings.integerBuilder("TimeoutMs").withMinimum(10).withValue(2500).build());
    public Setting<Boolean> debugDL = this.register(Settings.b("debugDL", false));
    public Setting<Boolean> debugErrors = this.register(Settings.b("debugErrors", true));
    public Setting<Boolean> notify = this.register(Settings.b("Notifications", true));
    public Setting<Integer> renderDistance = this.register(Settings.integerBuilder("RenderDistance").withMinimum(1).withValue(4).withMaximum(8).build());
    public Setting<Boolean> autoSpiral = this.register(Settings.b("AutoSpiral", true));
    public Setting<Integer> spiral_trigger = this.register(Settings.integerBuilder("spiral_trigger").withMinimum(2).withValue(3).withVisibility(v -> this.autoSpiral.getValue()).build());
    public Setting<Integer> spiral_ppt = this.register(Settings.integerBuilder("spiral_ppt").withMinimum(1).withValue(4).withMaximum(15).withVisibility(v -> this.autoSpiral.getValue()).build());
    public Setting<Integer> spiral_step_chunks = this.register(Settings.integerBuilder("spiral_step_chunks").withMinimum(1).withValue(2).withMaximum(15).withVisibility(v -> this.autoSpiral.getValue()).build());
    public Setting<Boolean> autoDisableSpiral = this.register(Settings.booleanBuilder("AutoDisableSpiral").withValue(true).withVisibility(v -> this.autoSpiral.getValue()).build());
    public Setting<Integer> spiral_range = this.register(Settings.integerBuilder("spiral_range").withMinimum(100).withValue(1000).withVisibility(v -> this.autoSpiral.getValue() != false && this.autoDisableSpiral.getValue() != false).build());
    private TrackedPlayer jew;
    private BlockPos beatPos;
    private boolean isBeating = false;
    public static DLTracker INSTANCE;
    @EventHandler
    Listener<PacketEvent.Receive> receiveListener = new Listener<PacketEvent.Receive>(event -> {
        SPacketRespawn packetIn;
        if (this.jew == null) {
            return;
        }
        if (event.getPacket() instanceof SPacketBlockChange) {
            SPacketBlockChange packetIn2 = (SPacketBlockChange)event.getPacket();
            String debug = "unknown";
            if (packetIn2.getBlockPosition().equals((Object)this.beatPos)) {
                debug = "heartbeat";
                this.jew.update();
            } else {
                if (packetIn2.getBlockPosition().getY() != 0) {
                    return;
                }
                for (ChunkPos chunkPos : this.jew.LastRequestedCoords) {
                    if (!chunkPos.getBlock(0, 0, 0).equals((Object)packetIn2.getBlockPosition())) continue;
                    this.jew.onCoordReceive(chunkPos);
                    debug = "primary chunk";
                    break;
                }
            }
            if (this.debugDL.getValue().booleanValue()) {
                this.SendMessage(debug + " " + packetIn2.getBlockPosition().toString() + " -> " + packetIn2.getBlockState().getBlock().getLocalizedName());
            }
        } else if (event.getPacket() instanceof SPacketRespawn && this.jew.dimension != (packetIn = (SPacketRespawn)event.getPacket()).getDimensionID()) {
            this.jew.onDimensionChange(packetIn.getDimensionID());
        }
    }, new Predicate[0]);

    public DLTracker() {
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        if (DLTracker.mc.player == null || DLTracker.mc.world == null) {
            return;
        }
        this.jew = new TrackedPlayer(this.x.getValue(), this.z.getValue());
    }

    @Override
    public void onUpdate() {
        if ((double)LagCompensator.INSTANCE.getTimeLastResponse() > 0.8 && DLTracker.mc.currentScreen instanceof GuiDownloadTerrain) {
            return;
        }
        if (this.jew == null) {
            this.toggle();
            return;
        }
        if (this.jew.isSpiraling) {
            if (!this.jew.processSpiral(this.spiral_range.getValue()) && this.autoDisableSpiral.getValue().booleanValue()) {
                if (this.debugErrors.getValue().booleanValue()) {
                    this.SendMessage("Spiral scan failed... disabling module rip");
                }
                this.toggle();
            }
            return;
        }
        if (this.jew.requestChunks()) {
            this.isBeating = true;
            return;
        }
        if (this.isBeating) {
            this.beatPos = DLTracker.mc.player.getPosition().down(10);
            if (this.debugDL.getValue().booleanValue()) {
                this.SendMessage(TextFormatting.YELLOW + "BEATING...");
            }
            DLTracker.mc.player.connection.sendPacket((Packet)new CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, this.beatPos, EnumFacing.UP));
            this.isBeating = false;
        }
    }

    @Override
    protected void onDisable() {
        if (this.jew != null) {
            this.SendMessage(this.jew.getReport());
        }
    }

    @Override
    public String getHudInfo() {
        if (this.jew == null || DLTracker.mc.player == null) {
            return "";
        }
        if (this.jew.isSpiraling) {
            return "[SPIRALING] " + (((TrackedPlayer)this.jew).getBlockCoords().x + this.jew.spiralX) + ", " + (((TrackedPlayer)this.jew).getBlockCoords().z + this.jew.spiralZ);
        }
        DecimalFormat df = new DecimalFormat("#");
        Vec3d pos1 = new Vec3d((double)DLTracker.mc.player.getPosition().getX(), (double)this.jew.getBlockCoords().getY(), (double)DLTracker.mc.player.getPosition().getZ());
        return "[" + this.jew.progress + "] " + ((TrackedPlayer)this.jew).getBlockCoords().x + ", " + ((TrackedPlayer)this.jew).getBlockCoords().z + "(" + df.format(pos1.distanceTo(new Vec3d((Vec3i)this.jew.getBlockCoords()))) + ")";
    }

    private class TrackedPlayer {
        int dimension;
        ChunkPos estimatedCenter;
        int Render_Distance;
        int failures;
        int successful_polls;
        long sinceLast_request;
        String progress = "|";
        List<ChunkPos> LastRequestedCoords;
        List<ChunkPos> LastReceivedCoords;
        TrackChunk[] primaryChunks;
        boolean isReadyToRequest;
        boolean isSpiraling;
        int spiralX;
        int spiralZ;

        private TrackedPlayer(int X, int Z) {
            this.Render_Distance = DLTracker.INSTANCE.renderDistance.getValue();
            this.primaryChunks = new TrackChunk[4];
            this.LastRequestedCoords = new ArrayList<ChunkPos>();
            this.LastReceivedCoords = new ArrayList<ChunkPos>();
            this.dimension = mc.player.dimension;
            this.setBlockCoords(X, Z);
            this.initChunksUsingCenter();
            this.sinceLast_request = System.currentTimeMillis();
            this.isReadyToRequest = true;
            this.failures = 0;
            this.successful_polls = 0;
            this.isSpiraling = false;
            this.spiralX = 0;
            this.spiralZ = 0;
        }

        public boolean processSpiral(int max) {
            int steps = DLTracker.this.spiral_step_chunks.getValue() * 16;
            for (int i = 0; i < DLTracker.this.spiral_ppt.getValue(); ++i) {
                int sx = this.spiralX + this.getBlockCoords().getX();
                int sz = this.spiralZ + this.getBlockCoords().getZ();
                BlockPos pos = new BlockPos(sx, 0, sz);
                mc.player.connection.sendPacket((Packet)new CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, pos, EnumFacing.UP));
                this.LastRequestedCoords.add(new ChunkPos(pos));
                if (Math.abs(this.spiralX) <= Math.abs(this.spiralZ) && (this.spiralX != this.spiralZ || this.spiralX >= 0)) {
                    this.spiralX += this.spiralZ >= 0 ? steps : -steps;
                    continue;
                }
                this.spiralZ += this.spiralX >= 0 ? -steps : steps;
            }
            return this.spiralX < max;
        }

        public void resetSpiral() {
            this.isSpiraling = false;
            this.spiralX = 0;
            this.spiralZ = 0;
            this.LastRequestedCoords.clear();
        }

        public void onCoordReceive(ChunkPos pos) {
            this.LastReceivedCoords.add(pos);
            this.LastRequestedCoords.remove(pos);
            if (this.isSpiraling) {
                if (DLTracker.this.debugErrors.getValue().booleanValue()) {
                    DLTracker.this.SendMessage("Spiral found a target. tracking...");
                }
                this.resetSpiral();
                this.update();
            }
        }

        public void update() {
            switch (this.LastReceivedCoords.size()) {
                case 0: {
                    ++this.failures;
                    if (DLTracker.this.debugErrors.getValue().booleanValue()) {
                        DLTracker.this.SendMessage("received 0 primary chunks, did we lose them? failures: " + this.failures);
                    }
                    if (DLTracker.this.notify.getValue().booleanValue() && !Display.isActive()) {
                        DLTracker.this.SendMessage("DL tracker lost target.");
                    }
                    this.estimatedCenter = new ChunkPos(new BlockPos(DLTracker.this.x.getValue().intValue(), 0, DLTracker.this.z.getValue().intValue()));
                    break;
                }
                case 1: {
                    this.estimatedCenter = this.LastReceivedCoords.get(0);
                    this.failures = 0;
                    break;
                }
                case 2: {
                    if (this.LastReceivedCoords.get((int)0).x != this.LastReceivedCoords.get((int)0).x && this.LastReceivedCoords.get((int)1).z != this.LastReceivedCoords.get((int)1).z && DLTracker.this.debugErrors.getValue().booleanValue()) {
                        DLTracker.this.SendMessage("received two chunks that are not on a line. is this a split?");
                    }
                    this.estimatedCenter = this.average(this.LastReceivedCoords.get(0), this.LastReceivedCoords.get(1));
                    this.failures = 0;
                    break;
                }
                case 3: {
                    int i;
                    for (i = 0; i < 4; ++i) {
                        boolean exists = false;
                        for (ChunkPos pos : this.LastReceivedCoords) {
                            if (!this.primaryChunks[i].getPos().equals((Object)pos)) continue;
                            exists = true;
                            break;
                        }
                        if (!exists) break;
                    }
                    this.estimatedCenter = this.getOppositeCorner(i);
                    this.failures = 0;
                    break;
                }
                case 4: {
                    this.failures = 0;
                }
            }
            this.initChunksUsingCenter();
            DLTracker.INSTANCE.x.setValue(this.getBlockCoords().x);
            DLTracker.INSTANCE.z.setValue(this.getBlockCoords().z);
            this.LastReceivedCoords.clear();
            this.updateProgress();
            if (this.failures == 0) {
                ++this.successful_polls;
            } else if (DLTracker.this.autoSpiral.getValue().booleanValue() && this.failures >= DLTracker.this.spiral_trigger.getValue()) {
                this.isSpiraling = true;
                if (DLTracker.this.debugErrors.getValue().booleanValue()) {
                    DLTracker.this.SendMessage("Enabling Spiral Scanner...");
                }
            }
            this.isReadyToRequest = true;
        }

        public boolean requestChunks() {
            if (System.currentTimeMillis() - this.sinceLast_request > (long)DLTracker.this.timeout.getValue().intValue()) {
                this.isReadyToRequest = true;
            }
            if (!this.isReadyToRequest) {
                return false;
            }
            this.LastRequestedCoords.clear();
            for (TrackChunk trackChunk : this.primaryChunks) {
                if (DLTracker.this.debugDL.getValue().booleanValue()) {
                    DLTracker.this.SendMessage(TextFormatting.YELLOW + "REQUESTING CHUNKS...");
                }
                mc.player.connection.sendPacket((Packet)new CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, trackChunk.getBlockPos(), EnumFacing.UP));
                this.LastRequestedCoords.add(trackChunk.getPos());
            }
            this.isReadyToRequest = false;
            this.sinceLast_request = System.currentTimeMillis();
            return true;
        }

        void initChunksUsingCenter() {
            for (int i = 0; i < 4; ++i) {
                int X = this.estimatedCenter.x;
                int Z = this.estimatedCenter.z;
                switch (i) {
                    case 0: {
                        X -= this.Render_Distance;
                        Z -= this.Render_Distance;
                        break;
                    }
                    case 1: {
                        X += this.Render_Distance;
                        Z -= this.Render_Distance;
                        break;
                    }
                    case 2: {
                        X -= this.Render_Distance;
                        Z += this.Render_Distance;
                        break;
                    }
                    case 3: {
                        X += this.Render_Distance;
                        Z += this.Render_Distance;
                    }
                }
                this.primaryChunks[i] = new TrackChunk(X, Z);
            }
        }

        private ChunkPos getOppositeCorner(int index) {
            switch (index) {
                case 0: {
                    return this.primaryChunks[3].pos;
                }
                case 1: {
                    return this.primaryChunks[2].pos;
                }
                case 2: {
                    return this.primaryChunks[1].pos;
                }
                case 3: {
                    return this.primaryChunks[0].pos;
                }
            }
            return this.estimatedCenter;
        }

        public void onDimensionChange(int newDimensionID) {
            if (newDimensionID == this.dimension) {
                return;
            }
            BlockPos old = this.getBlockCoords();
            int x = old.getX();
            int z = old.getZ();
            if (newDimensionID == -1) {
                x /= 8;
                z /= 8;
                if (DLTracker.this.debugErrors.getValue().booleanValue()) {
                    DLTracker.this.SendMessage("Dimension has been changed to nether.");
                }
            } else if (newDimensionID == 0) {
                x *= 8;
                z *= 8;
                if (DLTracker.this.debugErrors.getValue().booleanValue()) {
                    DLTracker.this.SendMessage("Dimension has been changed to overworld.");
                }
            }
            this.dimension = newDimensionID;
            if (this.isSpiraling) {
                this.resetSpiral();
            }
            this.setBlockCoords(x, z);
            this.isReadyToRequest = true;
        }

        private BlockPos getBlockCoords() {
            return this.estimatedCenter.getBlock(0, 0, 0);
        }

        private void setBlockCoords(int X, int Z) {
            DLTracker.INSTANCE.x.setValue(X);
            DLTracker.INSTANCE.z.setValue(Z);
            this.estimatedCenter = new ChunkPos(new BlockPos(X, 0, Z));
        }

        private void updateProgress() {
            if (this.failures > 0) {
                this.progress = "!" + this.failures + "!";
            } else if (this.progress.contains("!")) {
                this.progress = "|";
            }
            switch (this.progress) {
                case "|": {
                    this.progress = "/";
                    break;
                }
                case "/": {
                    this.progress = "-";
                    break;
                }
                case "-": {
                    this.progress = "\\";
                    break;
                }
                case "\\": {
                    this.progress = "|";
                }
            }
        }

        private String getReport() {
            String report = "";
            if (this.dimension == 0) {
                String dim = "Overworld";
                BlockPos overworld = this.getBlockCoords();
                BlockPos nether = new BlockPos(overworld.x / 8, 0, overworld.z / 8);
                report = overworld.x + ", " + overworld.z + " in dimension " + dim + ".../ Nether coords: (" + nether.x + ", " + nether.z + ")";
            } else if (this.dimension == -1) {
                String dim = "Nether";
                BlockPos nether = this.getBlockCoords();
                BlockPos overworld = new BlockPos(nether.x * 8, 0, nether.z * 8);
                report = nether.x + ", " + nether.z + " in dimension " + dim + ".../ Overworld coords: (" + overworld.x + ", " + overworld.z + ")";
            } else if (this.dimension == 1) {
                String dim = "end";
                BlockPos end = this.getBlockCoords();
                report = end.x + ", " + end.z + " in dimension " + dim;
            }
            return "Last reported coordinates: " + report + "\n Successful polls: " + this.successful_polls + " / failures before disabling module: " + this.failures;
        }

        private ChunkPos average(ChunkPos first, ChunkPos second) {
            int X = (first.x + second.x) / 2;
            int Z = (first.z + second.z) / 2;
            return new ChunkPos(X, Z);
        }

        private class TrackChunk {
            private ChunkPos pos;

            TrackChunk(int X, int Z) {
                this.setPos(X, Z);
            }

            public void setPos(int X, int Z) {
                this.pos = new ChunkPos(X, Z);
            }

            public ChunkPos getPos() {
                return this.pos;
            }

            public BlockPos getBlockPos() {
                return this.pos.getBlock(0, 0, 0);
            }
        }
    }
}

