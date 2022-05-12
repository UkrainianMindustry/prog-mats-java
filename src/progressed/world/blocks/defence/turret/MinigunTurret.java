package progressed.world.blocks.defence.turret;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.entities.bullet.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;
import progressed.graphics.*;
import progressed.util.*;

public class MinigunTurret extends ItemTurret{
    public float windupSpeed = 0.00625f, windDownSpeed = 0.0125f, minFiringSpeed = 3f, logicSpeedScl = 0.25f, maxSpeed = 30f;
    public float barX, barY, barStroke, barLength;
    public float barWidth = 1.5f, barHeight = 0.75f;

    public TextureRegion barrelRegion, barrelOutline, bodyRegion, bodyOutline;

    public MinigunTurret(String name){
        super(name);
    }

    @Override
    public void load(){
        super.load();

        barrelRegion = Core.atlas.find(name + "-barrel");
        barrelOutline = Core.atlas.find(name + "-barrel-outline");
        bodyRegion = Core.atlas.find(name + "-body");
        bodyOutline = Core.atlas.find(name + "-body-outline");
    }

    @Override
    public void createIcons(MultiPacker packer){
        Outliner.outlineRegion(packer, barrelRegion, outlineColor, name + "-barrel-outline");
        Outliner.outlineRegion(packer, bodyRegion, outlineColor, name + "-body-outline");
        super.createIcons(packer);
    }

    @Override
    public void setStats(){
        super.setStats();
        
        stats.remove(Stat.reload);
        float minValue = minFiringSpeed / 90f * 60f * shootLocs.length;
        float maxValue = maxSpeed / 90f * 60f * shootLocs.length;
        stats.add(Stat.reload, PMUtls.stringsFixed(minValue) + " - " + PMUtls.stringsFixed(maxValue) + StatUnit.perSecond.localized());
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("pm-minigun-speed", (MinigunTurretBuild entity) -> new Bar(
            () -> Core.bundle.format("bar.pm-minigun-speed", PMUtls.stringsFixed(entity.speedf() * 100f)),
            entity::barColor,
            entity::speedf
        ));
    }

    public class MinigunTurretBuild extends ItemTurretBuild{
        protected float[] heats = {0f, 0f, 0f, 0f};
        protected float spinSpeed, spin;

        @Override
        public void draw(){
            Draw.rect(baseRegion, x, y);

            Draw.z(Layer.turret - 0.2f);

            recoilOffset.trns(rotation, -curRecoil);

            Drawf.shadow(region, x + recoilOffset.x - elevation, y + recoilOffset.y - elevation, rotation - 90f);
            Draw.rect(bodyOutline, x + recoilOffset.x, y + recoilOffset.y, rotation - 90f);

            for(int i = 0; i < 4; i++){
                Draw.z(Layer.turret - 0.2f);
                Tmp.v1.trns(rotation - 90f, barWidth * Mathf.cosDeg(spin - 90 * i), barHeight * Mathf.sinDeg(spin - 90 * i)).add(recoilOffset);
                Draw.rect(barrelOutline, x + Tmp.v1.x, y + Tmp.v1.y, rotation - 90f);
                Draw.z(Layer.turret - 0.1f - Mathf.sinDeg(spin - 90 * i) / 100f);
                Draw.rect(barrelRegion, x + Tmp.v1.x, y + Tmp.v1.y, rotation - 90f);
                if(heats[i] > 0.001f){
                    Draw.blend(Blending.additive);
                    Draw.color(heatColor, heats[i]);
                    Draw.rect(heatRegion, x + Tmp.v1.x, y + Tmp.v1.y, rotation - 90f);
                    Draw.blend();
                    Draw.color();
                }
            }

            Draw.z(Layer.turret);

            Draw.rect(bodyRegion, x + recoilOffset.x, y + recoilOffset.y, rotation - 90f);

            if(speedf() > 0.0001f){
                Draw.color(barColor());
                Lines.stroke(barStroke);
                for(int i = 0; i < 2; i++){
                    recoilOffset.trns(rotation - 90f, barX * Mathf.signs[i], barY - curRecoil);
                    Lines.lineAngle(x + recoilOffset.x, y + recoilOffset.y, rotation, barLength * Mathf.clamp(speedf()), false);
                }
            }
        }

        public Color barColor(){
            return spinSpeed > minFiringSpeed ? team.color : team.palette[2];
        }

        @Override
        public void updateTile(){
            boolean notShooting = !hasAmmo() || !isShooting() || !isActive();
            if(notShooting){
                spinSpeed = Mathf.approachDelta(spinSpeed, 0, windDownSpeed);
            }

            if(spinSpeed > getMaxSpeed()){
                spinSpeed = Mathf.approachDelta(spinSpeed, getMaxSpeed(), windDownSpeed);
            }

            float capacity = coolant instanceof ConsumeLiquidFilter filter ? filter.getConsumed(this).heatCapacity : 1f;
            coolant.update(this);
            float add = (spinSpeed * (hasAmmo() ? peekAmmo().reloadMultiplier : 1f) + coolant.amount * capacity * coolantMultiplier) * delta();
            spin += add;
            reloadCounter += add;
            for(int i = 0; i < 4; i++){
                heats[i] = Math.max(heats[i] - Time.delta / cooldownTime, 0);
            }
            
            super.updateTile();
        }

        @Override
        protected void updateShooting(){
            if(!hasAmmo()) return;

            spinSpeed = Mathf.approachDelta(spinSpeed, getMaxSpeed(), windupSpeed * peekAmmo().reloadMultiplier * timeScale);

            if(reloadCounter >= 90 && spinSpeed > minFiringSpeed){
                BulletType type = peekAmmo();

                shoot(type);

                reload = spin % 90;

                heats[Mathf.floor(spin - 90) % 360 / 90] = 1f;
            }
        }

        @Override
        protected void updateCooling(){
            //Handled elsewhere
        }

        protected float getMaxSpeed(){
            return maxSpeed * (!isControlled() && logicControlled() && logicShooting ? logicSpeedScl : 1f);
        }

        protected float speedf(){
            return spinSpeed / maxSpeed;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(spinSpeed);
            write.f(spin % 360f);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);

            if(revision >= 2){
                spinSpeed = read.f();

                if(revision >= 3){
                    spin = read.f();
                }
            }
        }

        @Override
        public byte version(){
            return 3;
        }
    }
}
