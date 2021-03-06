package com.shatteredpixel.shatteredpixeldungeon.items.wands;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.FlavourBuff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.SoulMark;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.Effects;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.BlastParticle;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.EnergyParticle;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.SmokeParticle;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.SparkParticle;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Viscosity;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.shatteredpixel.shatteredpixeldungeon.utils.BArray;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Group;
import com.watabou.noosa.Image;
import com.watabou.noosa.audio.Sample;
import com.watabou.noosa.particles.Emitter;
import com.watabou.utils.Bundle;
import com.watabou.utils.Callback;
import com.watabou.utils.PathFinder;
import com.watabou.utils.PointF;
import com.watabou.utils.Random;

import java.util.ArrayList;

public class WandOfPlasma extends DamageWand {

    {
        image = ItemSpriteSheet.ARTIFACT_BEACON;

        collisionProperties = Ballistica.STOP_TARGET;
    }

    @Override
    public int min(int lvl) { return (5 + lvl) * chargesPerCast(); }

    @Override
    public int max(int lvl) { return (10 + 5 * lvl) * chargesPerCast(); }

    private int distance() {
        return buffedLvl() * 2 + 6;
    }

    @Override
    protected int chargesPerCast() {
        //consumes 30% of current charges, rounded up, with a minimum of one.
        return Math.max(1, (int)Math.ceil(curCharges*0.3f));
    }

    @Override
    protected void onZap(Ballistica attack) {
        if (Dungeon.level.solid[attack.collisionPos]) {
           GLog.i(Messages.get(this, "solid"));
            Sample.INSTANCE.play(Assets.Sounds.PUFF);
        } else if (Dungeon.level.distance(curUser.pos, attack.collisionPos) > distance()) {
            GLog.i(Messages.get(this, "too_far"));
            CellEmitter.center(attack.collisionPos).burst(SmokeParticle.FACTORY, 6);
            Sample.INSTANCE.play(Assets.Sounds.PUFF);
        } else {
            Plasma plasma = Buff.append(curUser, Plasma.class, chargesPerCast() - 1);
            plasma.cell = attack.collisionPos;

            plasma.damage += damageRoll();
            plasma.wandLevel = buffedLvl();
            plasma.chargesUsed = chargesPerCast();

            plasma.startParticles();
            Sample.INSTANCE.play(Assets.Sounds.BEACON);
        }
    }

    @Override
    public void onHit(MagesStaff staff, Char attacker, Char defender, int damage) {
        Viscosity.DeferedDamage deferred = Buff.affect( defender, Viscosity.DeferedDamage.class );
        deferred.prolong(Math.round(damage / 3f));
    }

    @Override
    public String statsDesc() {
        if (levelKnown)
            return Messages.get(this, "stats_desc", chargesPerCast(), min(), max());
        else
            return Messages.get(this, "stats_desc", chargesPerCast(), min(0), max(0));
    }

    @Override
    protected void fx(Ballistica bolt, Callback callback) {
        callback.call(); //No intermediate fx
    }

    @Override
    public void staffFx(MagesStaff.StaffParticle particle) {
        particle.color( 0x6622AA ); particle.am = 0.6f;
        particle.setLifespan(5f);
        particle.speed.polar(Random.Float(PointF.PI2), 0.3f);
        particle.setSize(1f, 1.2f);
        particle.radiateXY(Random.oneOf(1.5f, 3.5f));
    }

    public static class Plasma extends FlavourBuff {

        private int cell;
        private int damage = 0;
        private int wandLevel = 0;
        private int chargesUsed = 0;

        private Emitter emitter;

        private void startParticles() {
            emitter = CellEmitter.center(cell);
            emitter.pour(EnergyParticle.FACTORY, 0.1f / (chargesUsed));
        }

        @Override
        public void detach() {
            super.detach();

            emitter.on = false;

            Sample.INSTANCE.play(Assets.Sounds.BLAST);
            if (Dungeon.level.heroFOV[cell]) {
                switch (chargesUsed) {
                    case 3:
                        for (int n = 0; n < PathFinder.NEIGHBOURS9.length; n++) { //x shape
                            if (n % 2 == 0) PlasmaWave.blast(cell + PathFinder.NEIGHBOURS9[n], n * 0.015f + 0.01f);
                        }
                        Camera.main.shake(1, 0.5f);
                        break;
                    case 2:
                        PlasmaWave.blast(cell, 0);
                        break;
                    case 1:
                        CellEmitter.center(cell).burst(BlastParticle.FACTORY, 12);
                        break;
                }

            }

            ArrayList<Char> affected = new ArrayList<>();

            boolean terrainAffected = false;
            PathFinder.buildDistanceMap( cell, BArray.not( Dungeon.level.solid, null ), chargesUsed - 1 );
            for (int i = 0; i < PathFinder.distance.length; i++) {
                if (PathFinder.distance[i] < Integer.MAX_VALUE) {

                    if (Dungeon.level.heroFOV[i]) {
                        CellEmitter.get(i).burst(SparkParticle.FACTORY, 3);
                    }

                    if (Dungeon.level.flamable[i]) {
                        Dungeon.level.destroy(i);
                        GameScene.updateMap(i);
                        terrainAffected = true;
                    }

                    Char ch = Actor.findChar(i);
                    if (ch != null){
                        affected.add(ch);
                    }
                }
            }

            for (Char ch: affected) {
                processSoulMark(ch, wandLevel, chargesUsed);

                //always deals 2/3 damage at edge of blast
                float multiplier =  1f;
                if (chargesUsed > 1) {
                    multiplier -= .33333f * Dungeon.level.distance(cell, ch.pos) / (chargesUsed - 1);
                }
                ch.damage(Math.round(damage * multiplier), WandOfPlasma.class);

                if (ch == Dungeon.hero && !ch.isAlive()) {
                    Dungeon.fail(WandOfPlasma.class);
                    GLog.n(Messages.get(WandOfPlasma.class, "ondeath"));
                }
            }

            if (terrainAffected) {
                Dungeon.observe();
            }
        }

        private static final String CELL = "cell";
        private static final String DAMAGE = "damage";
        private static final String WAND_LEVEL = "wandLevel";

        @Override
        public void storeInBundle(Bundle bundle) {
            super.storeInBundle(bundle);
            bundle.put(CELL, cell);
            bundle.put(DAMAGE, damage);
            bundle.put(WAND_LEVEL, wandLevel);
        }

        @Override
        public void restoreFromBundle(Bundle bundle) {
            super.restoreFromBundle(bundle);
            cell = bundle.getInt(CELL);
            damage = bundle.getInt(DAMAGE);
            wandLevel = bundle.getInt(WAND_LEVEL);
        }
    }

    public static class PlasmaWave extends Image {

        private static final float TIME_TO_FADE_L = 0.2f;
        private static final float TIME_TO_FADE_S = 0.125f;

        private float timeToFade;
        private float time;

        public PlasmaWave(){
            super(Effects.get(Effects.Type.RIPPLE));
            origin.set(width / 2, height / 2);
        }

        public void reset(int pos, float shift) {

            x = (pos % Dungeon.level.width()) * DungeonTilemap.SIZE + (DungeonTilemap.SIZE - width) / 2;
            y = (pos / Dungeon.level.width()) * DungeonTilemap.SIZE + (DungeonTilemap.SIZE - height) / 2;

            scale.x = scale.y = 0;

            time = timeToFade = shift > 0 ? TIME_TO_FADE_S : TIME_TO_FADE_L;

            if (shift > 0) {
                x += Random.Float(-DungeonTilemap.SIZE, DungeonTilemap.SIZE) / 1.5;
                y += Random.Float(-DungeonTilemap.SIZE, DungeonTilemap.SIZE) / 1.5;
                time += shift;
            }
        }

        @Override
        public void update() {
            super.update();

            if ((time -= Game.elapsed) <= 0) {
                killAndErase();
            } else if (time <= timeToFade) {
                float p = time / timeToFade;
                alpha(p);
                scale.y = scale.x = (1-p)*3;
            }
        }

        public static void blast(int pos, float shift) {
            Group parent = Dungeon.hero.sprite.parent;
            PlasmaWave b = (PlasmaWave) parent.addToFront(new PlasmaWave());
            b.reset(pos, shift);
        }
    }
}
