package com.aotmod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * All ODM gear physics runs on the server, so friends only need the mod installed
 * (no per-client physics). Tune the constants below to change the feel.
 */
public class OdmManager {
    // ---------- tuning ----------
    static final double RANGE = 55.0;            // hook range in blocks
    static final double MAX_GAS = 200.0;
    static final double MAX_SPEED = 2.4;         // blocks per tick cap
    static final double BOOST_ACCEL = 0.16;      // gas boost acceleration per tick
    static final double BOOST_GAS_COST = 1.2;    // gas per tick while boosting
    static final double MIN_ROPE = 3.0;
    static final double HOOK_SPREAD_DEG = 9.0;   // the two hooks fan out left/right of your crosshair
    static final double REEL_GROUND = 0.5;       // rope shortening per tick on the ground (launch)
    static final double REEL_AIR = 0.08;         // rope shortening per tick in the air (slow pull-in)
    static final double REEL_SNEAK = 0.7;        // rope shortening per tick while sneaking (costs gas)

    static class Hook {
        Vec3d anchor;
        Entity target;     // set when hooked onto a titan
        Vec3d offset;
        BlockPos block;    // set when hooked onto a block
        double length;
    }

    static class State {
        int flags;
        boolean hookWasHeld;
        boolean justFired;
        Vec3d lastPos;
        World lastWorld;
        double gas = MAX_GAS;
        final List<Hook> hooks = new ArrayList<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(ServerPlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), u -> new State());
    }

    public static void refill(ServerPlayerEntity p) {
        state(p).gas = MAX_GAS;
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(AotMod.INPUT_PACKET, (server, player, handler, buf, sender) -> {
            int flags = buf.readByte();
            server.execute(() -> state(player).flags = flags);
        });
        ServerTickEvents.END_SERVER_TICK.register(OdmManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.getPlayer().getUuid()));
    }

    private static boolean hasGear(ServerPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(ModItems.ODM_GEAR)) return true;
        }
        return false;
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            State s = state(p);
            if (!p.isAlive() || p.isSpectator()) {
                s.hooks.clear();
                s.hookWasHeld = false;
                s.lastPos = null;
                s.gas = MAX_GAS;
                continue;
            }
            ServerWorld world = (ServerWorld) p.getWorld();
            if (s.lastWorld != world) {
                s.hooks.clear();
                s.lastPos = null;
                s.lastWorld = world;
            }

            boolean gear = hasGear(p);
            boolean hookHeld = gear && (s.flags & 1) != 0;
            boolean boostHeld = gear && (s.flags & 2) != 0;

            if (hookHeld && !s.hookWasHeld) fireHooks(p, world, s);
            if (!hookHeld) s.hooks.clear();
            s.hookWasHeld = hookHeld;

            Vec3d pos = p.getPos();
            Vec3d vel = s.lastPos == null ? Vec3d.ZERO : pos.subtract(s.lastPos);
            s.lastPos = pos;
            if (vel.lengthSquared() > 16.0) vel = Vec3d.ZERO; // teleport / respawn

            boolean push = false;

            if (s.justFired) {
                s.justFired = false;
                if (p.isOnGround()) vel = vel.add(0, 0.45, 0); // little hop to get airborne
                push = true;
            }

            // ----- ropes -----
            Iterator<Hook> it = s.hooks.iterator();
            while (it.hasNext()) {
                Hook h = it.next();
                if (!updateAnchor(world, h)) { it.remove(); continue; }
                Vec3d center = pos.add(0, 1.0, 0);
                Vec3d toAnchor = h.anchor.subtract(center);
                double dist = toAnchor.length();
                if (dist > RANGE * 1.8) { it.remove(); continue; }
                if (dist < 0.5) continue;
                Vec3d n = toAnchor.multiply(1.0 / dist);

                double shrink = p.isOnGround() ? REEL_GROUND : REEL_AIR;
                if (p.isSneaking()) {
                    if (s.gas > 0.4) { s.gas -= 0.4; shrink = REEL_SNEAK; }
                }
                h.length = Math.max(MIN_ROPE, Math.min(h.length, dist) - shrink);

                if (dist > h.length) {
                    double vn = vel.dotProduct(n);            // < 0 means moving away from the anchor
                    if (vn < 0) vel = vel.subtract(n.multiply(vn));
                    vel = vel.add(n.multiply(Math.min((dist - h.length) * 0.5, 0.9)));
                }
                push = true;
            }

            // ----- gas boost -----
            boolean boosting = false;
            if (boostHeld && s.gas >= BOOST_GAS_COST) {
                vel = vel.add(p.getRotationVec(1.0f).multiply(BOOST_ACCEL));
                s.gas -= BOOST_GAS_COST;
                boosting = true;
                push = true;
                world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.8, pos.z, 2, 0.15, 0.15, 0.15, 0.01);
                if (p.age % 6 == 0) {
                    world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
                            SoundCategory.PLAYERS, 0.4f, 1.6f);
                }
            }

            if (push) {
                double sp = vel.length();
                if (sp > MAX_SPEED) vel = vel.multiply(MAX_SPEED / sp);
                p.setVelocity(vel);
                p.fallDistance = 0;
                p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
            }

            // ----- visuals -----
            if (p.age % 2 == 0) {
                for (Hook h : s.hooks) drawRope(world, pos.add(0, 1.1, 0), h.anchor);
            }

            // ----- gas regen + HUD -----
            if (!boosting) {
                s.gas = Math.min(MAX_GAS, s.gas + (p.isOnGround() ? 3.0 : 0.04));
            }
            if (gear && p.age % 4 == 0 && (s.gas < MAX_GAS || push)) {
                p.sendMessage(gasBar(s.gas), true);
            }
        }
    }

    private static void fireHooks(ServerPlayerEntity p, ServerWorld world, State s) {
        s.hooks.clear();
        Vec3d look = p.getRotationVec(1.0f);
        for (double deg : new double[]{-HOOK_SPREAD_DEG, HOOK_SPREAD_DEG}) {
            double rad = Math.toRadians(deg);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            Vec3d dir = new Vec3d(look.x * cos - look.z * sin, look.y, look.x * sin + look.z * cos).normalize();
            Hook h = castHook(p, world, dir);
            if (h != null) s.hooks.add(h);
        }
        if (!s.hooks.isEmpty()) {
            s.justFired = true;
            world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 1.0f, 0.6f);
            world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1.0f, 0.8f);
        }
    }

    private static Hook castHook(ServerPlayerEntity p, ServerWorld world, Vec3d dir) {
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(dir.multiply(RANGE));
        BlockHitResult bhit = world.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        boolean blockHit = bhit.getType() == HitResult.Type.BLOCK;
        double maxSq = blockHit ? eye.squaredDistanceTo(bhit.getPos()) : RANGE * RANGE;
        Box box = p.getBoundingBox().stretch(dir.multiply(RANGE)).expand(1.0);
        EntityHitResult ehit = ProjectileUtil.raycast(p, eye, end, box,
                e -> e instanceof TitanEntity && e.isAlive(), maxSq);

        Hook h = new Hook();
        if (ehit != null) {
            h.target = ehit.getEntity();
            h.anchor = ehit.getPos();
            h.offset = ehit.getPos().subtract(ehit.getEntity().getPos());
        } else if (blockHit) {
            h.anchor = bhit.getPos();
            h.block = bhit.getBlockPos();
            if (AotMod.fallingTrees) fellTree(p, world, h.block);
        } else {
            return null;
        }
        h.length = p.getPos().add(0, 1.0, 0).distanceTo(h.anchor);
        return h;
    }

    private static boolean updateAnchor(ServerWorld world, Hook h) {
        if (h.target != null) {
            if (!h.target.isAlive() || h.target.getWorld() != world) return false;
            h.anchor = h.target.getPos().add(h.offset);
            return true;
        }
        return h.block != null && !world.getBlockState(h.block).isAir();
    }

    private static void drawRope(ServerWorld world, Vec3d a, Vec3d b) {
        double len = a.distanceTo(b);
        int steps = (int) Math.min(24, Math.ceil(len / 1.5));
        for (int i = 2; i <= steps; i++) {
            Vec3d pt = a.lerp(b, i / (double) steps);
            world.spawnParticles(ParticleTypes.CRIT, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
    }

    private static Text gasBar(double gas) {
        int n = (int) Math.round(gas / MAX_GAS * 20.0);
        return Text.literal("GAS ").formatted(Formatting.GRAY)
                .append(Text.literal("|".repeat(n)).formatted(n < 5 ? Formatting.RED : Formatting.AQUA))
                .append(Text.literal("|".repeat(20 - n)).formatted(Formatting.DARK_GRAY));
    }

    // ---------- falling trees ----------

    private static boolean isNaturalTree(ServerWorld world, BlockPos start) {
        BlockState st = world.getBlockState(start);
        if (!st.isIn(BlockTags.LOGS)) return false;
        if (Registries.BLOCK.getId(st.getBlock()).getPath().startsWith("stripped_")) return false;
        // needs natural (non-persistent) leaves nearby, so player-built log houses are safe
        for (BlockPos pos : BlockPos.iterate(start.add(-3, 0, -3), start.add(3, 12, 3))) {
            BlockState ls = world.getBlockState(pos);
            if (ls.isIn(BlockTags.LEAVES) && ls.contains(LeavesBlock.PERSISTENT) && !ls.get(LeavesBlock.PERSISTENT)) {
                return true;
            }
        }
        return false;
    }

    private static void fellTree(ServerPlayerEntity p, ServerWorld world, BlockPos start) {
        if (!isNaturalTree(world, start)) return;
        Set<BlockPos> logs = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        logs.add(start);
        queue.add(start);
        while (!queue.isEmpty() && logs.size() < 120) {
            BlockPos c = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = c.add(dx, dy, dz);
                        if (logs.contains(n)) continue;
                        if (world.getBlockState(n).isIn(BlockTags.LOGS)) {
                            logs.add(n);
                            queue.add(n);
                        }
                    }
                }
            }
        }
        Vec3d toward = new Vec3d(p.getX() - (start.getX() + 0.5), 0, p.getZ() - (start.getZ() + 0.5));
        toward = toward.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : toward.normalize();
        world.playSound(null, start, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 3.0f, 0.5f);
        for (BlockPos lp : new ArrayList<>(logs)) {
            BlockState ls = world.getBlockState(lp);
            FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(world, lp, ls);
            double h = lp.getY() - start.getY();
            double push = 0.05 + 0.03 * h;
            fb.setVelocity(toward.x * push, 0.0, toward.z * push);
            fb.setHurtEntities(2.0f, 40);
        }
    }
}
