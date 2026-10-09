package com.aotmod;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

/** 15 meter titan. Only blade hits to the nape (back of the neck) hurt it. */
public class TitanEntity extends HostileEntity {

    public TitanEntity(EntityType<? extends TitanEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 500;
    }

    public static DefaultAttributeContainer.Builder createTitanAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 400.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 14.0)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 2.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96.0);
    }

    public static boolean canTitanSpawn(EntityType<TitanEntity> type, ServerWorldAccess world,
                                        SpawnReason reason, BlockPos pos, Random random) {
        return AotMod.naturalTitans && world.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(4) == 0;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.7));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 48.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        World w = this.getWorld();
        if (!w.isClient) {
            if (this.age % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
                this.heal(10.0f); // titans regenerate fast
            }
            if (this.age % 4 == 0) {
                trample(w);
            }
        }
    }

    /** Smash through leaves and logs so titans don't get stuck in forests. Respects mobGriefing. */
    private void trample(World w) {
        if (!w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) return;
        Box b = this.getBoundingBox().expand(0.5, 0.0, 0.5);
        int minY = MathHelper.floor(b.minY);
        int maxY = MathHelper.floor(Math.min(b.maxY, b.minY + 9.0));
        int broken = 0;
        for (BlockPos pos : BlockPos.iterate(MathHelper.floor(b.minX), minY, MathHelper.floor(b.minZ),
                MathHelper.floor(b.maxX), maxY, MathHelper.floor(b.maxZ))) {
            BlockState st = w.getBlockState(pos);
            if (st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.LOGS)) {
                w.breakBlock(pos, st.isIn(BlockTags.LOGS), this);
                if (++broken >= 60) break;
            }
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) {
            return super.damage(source, amount); // /kill still works
        }
        Entity attacker = source.getAttacker();
        if (attacker instanceof PlayerEntity player && player.getMainHandStack().getItem() instanceof BladeItem) {
            if (isNapeHit(player)) {
                amount = this.getMaxHealth() * 2.0f; // clean cut
                if (this.getWorld() instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getEyeY(), player.getZ(), 30, 0.5, 0.5, 0.5, 0.3);
                }
            } else {
                amount = Math.min(amount, 1.0f); // blades just scratch the rest of the body
            }
        } else {
            amount = Math.min(amount, 1.0f);
        }
        return super.damage(source, amount);
    }

    /** Attacker must be high up (neck height) and behind the titan. */
    private boolean isNapeHit(PlayerEntity p) {
        double rel = p.getEyeY() - this.getY();
        if (rel < 9.5 || rel > 14.5) return false;
        float yaw = this.bodyYaw * ((float) Math.PI / 180.0f);
        double fx = -MathHelper.sin(yaw);
        double fz = MathHelper.cos(yaw);
        double dx = p.getX() - this.getX();
        double dz = p.getZ() - this.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) return false;
        double dot = (dx * fx + dz * fz) / len;
        return dot < -0.25;
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 7, getZ(), 300, 2.0, 6.0, 2.0, 0.05);
            sw.spawnParticles(ParticleTypes.POOF, getX(), getY() + 3, getZ(), 120, 2.0, 3.0, 2.0, 0.1);
        }
        super.onDeath(damageSource);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_ZOMBIE_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ENTITY_ZOMBIE_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_ZOMBIE_DEATH; }
    @Override
    public float getSoundPitch() { return 0.35f; }
    @Override
    protected float getSoundVolume() { return 4.0f; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ENTITY_IRON_GOLEM_STEP, 2.5f, 0.55f);
    }
}
