package progressed.world.blocks.defence.turret;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.meta.*;
import progressed.graphics.*;
import progressed.util.*;
import progressed.world.meta.*;

import static arc.Core.*;

public class SniperTurret extends ItemTurret{
    public int partCount = 3;
    public float split, chargeMoveFract = 0.9f;

    public TextureRegion[] outlines, connectors, parts, heats, cHeats;

    public SniperTurret(String name){
        super(name);

        unitSort = UnitSorts.strongest;
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.remove(Stat.ammo);
        stats.add(Stat.ammo, PMStatValues.ammo(ammoTypes));
    }

    @Override
    public void load(){
        super.load();

        outlines = new TextureRegion[partCount];
        parts = new TextureRegion[partCount];
        connectors = new TextureRegion[partCount - 1];
        heats = new TextureRegion[partCount];
        cHeats = new TextureRegion[partCount - 1];
        
        for(int i = 0; i < partCount; i++){
            parts[i] = atlas.find(name + "-part-" + i);
            outlines[i] = atlas.find(name + "-outline-" + i);
            heats[i] = atlas.find(name + "-heat-" + i);
            if(i < partCount - 1){
                connectors[i] = atlas.find(name + "-connector-" + i);
                cHeats[i] = atlas.find(name + "-connector-heat-" + i);
            }
        }
    }

    @Override
    public void init(){
        shootY = Math.max(shootY, shootY + split * (partCount - 1f));
        super.init();
    }

    @Override
    public void createIcons(MultiPacker packer){
        super.createIcons(packer);
        Outliner.outlineRegions(packer, parts, outlineColor, name + "-outline");
        Outliner.outlineRegions(packer, connectors, outlineColor, name + "-connector");
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("pm-reload", (SniperTurretBuild entity) -> new Bar(
            () -> Core.bundle.format("bar.pm-reload", PMUtls.stringsFixed(Mathf.clamp(entity.reloadCounter / reload) * 100f)),
            () -> entity.team.color,
            () -> Mathf.clamp(entity.reloadCounter / reload)
        ));

        addBar("pm-charge", (SniperTurretBuild entity) -> new Bar(
            () -> Core.bundle.format("bar.pm-charge", PMUtls.stringsFixed(Mathf.clamp(entity.charge) * 100f)),
            () -> Pal.surge,
            () -> entity.charge
        ));
    }

    public class SniperTurretBuild extends ItemTurretBuild{
        protected float charge;

        @Override
        public void draw(){
            Draw.rect(baseRegion, x, y);

            Draw.z(Layer.turret);

            float scl = split * Interp.pow2Out.apply(charge);
            recoilOffset.trns(rotation, -curRecoil);

            for(int i = 0; i < partCount; i++){
                float tx = Angles.trnsx(rotation, scl * i);
                float ty = Angles.trnsy(rotation, scl * i);
                Drawf.shadow(outlines[i], x + recoilOffset.x + tx - elevation, y + recoilOffset.y + ty - elevation, rotation - 90);
            }

            for(int i = 0; i < partCount - 1; i++){
                float tx = Angles.trnsx(rotation, scl * (i + 0.5f));
                float ty = Angles.trnsy(rotation, scl * (i + 0.5f));
                Drawf.shadow(connectors[i], x + recoilOffset.x + tx - elevation, y + recoilOffset.y + ty - elevation, rotation - 90);
            }

            for(int i = 0; i < partCount; i++){
                float tx = Angles.trnsx(rotation, scl * i);
                float ty = Angles.trnsy(rotation, scl * i);
                Draw.rect(outlines[i], x + recoilOffset.x + tx, y + recoilOffset.y + ty, rotation - 90);
            }

            for(int i = 0; i < partCount - 1; i++){
                float tx = Angles.trnsx(rotation, scl * (i + 0.5f));
                float ty = Angles.trnsy(rotation, scl * (i + 0.5f));
                Draw.rect(connectors[i], x + recoilOffset.x + tx, y + recoilOffset.y + ty, rotation - 90);
                if(heat > 0.001f){
                    if(Core.atlas.isFound(cHeats[i])){
                        Draw.color(heatColor, heat);
                        Draw.blend(Blending.additive);
                        Draw.rect(cHeats[i], x + recoilOffset.x + tx, y + recoilOffset.y + ty, rotation - 90);
                        Draw.blend();
                        Draw.color();
                    }
                }
            }

            for(int i = 0; i < partCount; i++){
                float tx = Angles.trnsx(rotation, scl * i);
                float ty = Angles.trnsy(rotation, scl * i);
                Draw.rect(parts[i], x + recoilOffset.x + tx, y + recoilOffset.y + ty, rotation - 90);
            }

            if(heat > 0.001f){
                Draw.color(heatColor, heat);
                Draw.blend(Blending.additive);
                for(int i = 0; i < partCount; i++){
                    if(heats[i].found()){
                        float tx = Angles.trnsx(rotation, scl * i);
                        float ty = Angles.trnsy(rotation, scl * i);
                        Draw.rect(heats[i], x + recoilOffset.x + tx, y + recoilOffset.y + ty, rotation - 90);
                    }
                }
                Draw.blend();
                Draw.color();
            }
        }

        @Override
        public void updateTile(){
            if(charging()){
                charge = Mathf.clamp(charge + Time.delta / shoot.firstShotDelay);
            }else{
                charge = 0;
            }
    
            super.updateTile();
        }

        @Override
        protected void updateShooting(){
            if(canConsume()){
                if(reloadCounter >= reload && !charging()){
                    BulletType type = peekAmmo();
        
                    shoot(type);
                }else if(hasAmmo() && reloadCounter < reload){
                    reloadCounter += delta() * peekAmmo().reloadMultiplier * baseReloadSpeed();
                }
            }
        }

        @Override
        protected void updateCooling(){
            if(hasAmmo() && canConsume()){
                super.updateCooling();
            }
        }
        
        @Override
        protected void turnToTarget(float targetRot){
            rotation = Angles.moveToward(rotation, targetRot, efficiency * rotateSpeed * delta() * (charging() ? (1 - chargeMoveFract * charge) : 1));
        }
        
        @Override
        public boolean shouldTurn(){
            return true;
        }
    }
}
