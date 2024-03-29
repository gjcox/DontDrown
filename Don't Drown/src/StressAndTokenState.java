/* Handles stress-based calculations, and how many tokens the player has collected so far through a level. */
public class StressAndTokenState {

    private final DontDrown sketch;

    public static final int ABS_MAX_STRESS = 100;
    public static final int DEFAULT_STRESS_EFFECT_THRESHOLD = ABS_MAX_STRESS / 5;
    public static final int FRAMES_PER_RESKETCH_MAX = 40;
    public static final int FRAMES_PER_RESKETCH_MIN = 10;
    public static final int FRAMES_PER_RESKETCH_RANGE = FRAMES_PER_RESKETCH_MAX - FRAMES_PER_RESKETCH_MIN;
    public static final float STRESS_INCR_RATE = 0.75f;
    public static final float STRESS_DECR_RATE = 0.75f;
    public static final float STRESS_INCR_RANGE_DIV = 2.5f;

    // level and stress values
    public int tokensAvailable = 0;
    public int tokensCollected = 0;
    public float oldStress = 0;
    public float stress = 0f;
    public int maxStress = 100;
    public int minStress = 0;
    public int stressEffectThreshold = DEFAULT_STRESS_EFFECT_THRESHOLD;
    public float stressRating = stress - stressEffectThreshold;
    public float stressIncrRange;
    public Debuff debuff = Debuff.NONE;
    public int waveLastSeen = -1; // a frame count
    private float stressRange = (ABS_MAX_STRESS - stressEffectThreshold);

    // pc values
    public float pcThrust;
    public float pcFriction;
    public float pcMinSpeed;
    public float[] stressHSBColour;
    public int framesPerResketch;

    // calculation values
    public float pcThrustMultiplier;
    public float pcFrictionMultiplier;

    // hand-drawing values
    private final float stressHueMultiplier = (PlayerCharacter.PC_MAX_HUE - PlayerCharacter.PC_MIN_HUE)
            / stressRange;
    private final float stressSatMultiplier = (PlayerCharacter.PC_MAX_SAT - PlayerCharacter.PC_MIN_SAT)
            / stressRange;
    private final float stressLightMultiplier = (PlayerCharacter.PC_MAX_LIGHT - PlayerCharacter.PC_MIN_LIGHT)
            / stressRange;
    private final float strokeVariabilityMultiplier = (Sketcher.RSV_MAX - Sketcher.RSV_MIN) / stressRange;
    private final float strokeShakinessMultiplier = (Sketcher.RSS_MAX - Sketcher.RSS_MIN) / stressRange;
    private final float framesPerResketchMultiplier = FRAMES_PER_RESKETCH_RANGE / stressRange;

    public StressAndTokenState(DontDrown sketch) {
        this.sketch = sketch;
    }

    public void reset(Level level) {
        reset();
        tokensAvailable = level.tokens.size();
        debuff = level.debuff;
        level.reset();
        update();
    }

    /* Reset stress calculation values */
    public void reset() {
        tokensCollected = 0;
        oldStress = 0;
        stress = 0;
        maxStress = ABS_MAX_STRESS;
        minStress = 0;
        waveLastSeen = -1;
        debuff = Debuff.NONE;
        stressEffectThreshold = debuff.equals(Debuff.STRESS_MOTIVATED) ? 35 : DEFAULT_STRESS_EFFECT_THRESHOLD;
        AbstractDrawable.stressIndex = minStress;
        stressRange = (ABS_MAX_STRESS - stressEffectThreshold);
        stressIncrRange = sketch.height / STRESS_INCR_RANGE_DIV;
        update();
    }

    /**
     * Calculates the multipliers used for friction and thrust incrementation.
     */
    public void pcCalcs() {
        this.pcThrustMultiplier = (sketch.pc.maxHorizontalThrust - sketch.pc.minHorizontalThrust) / stressRange;
        this.pcFrictionMultiplier = (sketch.pc.maxHorizontalFriction - sketch.pc.minHorizontalFriction) / stressRange;
    }

    /* Sets the current stress-based thrust magnitude */
    private void pcThrust() {
        if (debuff.equals(Debuff.STRESS_MOTIVATED) || stress >= stressEffectThreshold) {
            pcThrust = sketch.pc.minHorizontalThrust + stressRating * pcThrustMultiplier;
        } else {
            pcThrust = sketch.pc.minHorizontalThrust;
        }
    }

    /* Sets the current stress-based friction magnitude */
    private void pcFriction() {
        if (debuff.equals(Debuff.STRESS_MOTIVATED) || stress >= stressEffectThreshold) {
            pcFriction = sketch.pc.maxHorizontalFriction - stressRating * pcFrictionMultiplier;

        } else {
            pcFriction = sketch.pc.maxHorizontalFriction;
        }
    }

    /* The speed at which the PC comes to rest. */
    private void pcMinSpeed() {
        pcMinSpeed = pcFriction * PlayerCharacter.I_MASS * sketch.pc.incr;
    }

    /** Converts a stress value into a colour for the PC's and stress bar's token generation. */
    public void calcStressHSBColour() {
        if (stress >= stressEffectThreshold) {
            stressRating = stress - stressEffectThreshold;
            float[] hsb = new float[3];
            hsb[0] = PlayerCharacter.PC_MIN_HUE + stressRating * stressHueMultiplier;
            hsb[1] = PlayerCharacter.PC_MIN_SAT + stressRating * stressSatMultiplier;
            hsb[2] = PlayerCharacter.PC_MIN_LIGHT + stressRating * stressLightMultiplier;
            stressHSBColour = hsb;
        } else {
            stressHSBColour = new float[] { PlayerCharacter.PC_MIN_HUE, PlayerCharacter.PC_MIN_SAT,
                    PlayerCharacter.PC_MIN_LIGHT };
        }

    }

    /** Calculates a stress-based value of sketchiness for token generation */
    public void sketchiness() {
        if (stress >= stressEffectThreshold) {
            stressRating = stress - stressEffectThreshold;
            framesPerResketch = (int) (FRAMES_PER_RESKETCH_MAX - stressRating * framesPerResketchMultiplier);
            sketch.roughStrokeVariabilityRate = Sketcher.RSV_MIN + stressRating * strokeVariabilityMultiplier;
            sketch.roughStrokeShakiness = (int) (Sketcher.RSS_MIN + stressRating * strokeShakinessMultiplier);

        } else {
            framesPerResketch = FRAMES_PER_RESKETCH_MAX;
            sketch.roughStrokeVariabilityRate = Sketcher.RSV_MIN;
            sketch.roughStrokeShakiness = Sketcher.RSS_MIN;
        }
    }

    /** Increments the collected token count, and updates the token accordingly. */
    public void collectToken(Token token) {
        token.collected = true;
        tokensCollected++;
    }

    /** Updates the stress based on debuff and distance between wave and player */
    public void updateStress() {

        if (sketch.staticStress) {
            return;
        }

        float waveDistance = Math.abs(sketch.risingWave.pos.y - sketch.pc.pos.y);

        if (debuff.equals(Debuff.PANIC_PRONE) && sketch.frameCount % 300 < 100) {
            waveDistance = Math.min(waveDistance, stressIncrRange / 2);
        }

        else if (debuff.equals(Debuff.TUNNEL_VISION)) {
            if (sketch.risingWave.pos.y > sketch.pc.pos.y + sketch.pc.jumpHeight) {
                if (sketch.frameCount < waveLastSeen + stress * 2) {
                    // don't destress as soon as the wave is out of sight
                    waveDistance = stressIncrRange;
                } else {
                    // destress if the wave has been out of sight long enough
                    waveDistance = Math.max(waveDistance, stressIncrRange * 1.5f);
                }
            } else {
                // when the wave is visible, the rate of stress increase is extra high
                waveDistance = waveDistance / 2f;
                waveLastSeen = sketch.frameCount;
            }
        }

        if (waveDistance <= stressIncrRange) {
            stress += STRESS_DECR_RATE * ((stressIncrRange - waveDistance) / stressIncrRange);
        } else if (!debuff.equals(Debuff.CANT_UNWIND)) {
            stress -= Math.min(STRESS_DECR_RATE, STRESS_DECR_RATE * (waveDistance - stressIncrRange) / stressIncrRange);
        }

        if (stress > maxStress) {
            stress = maxStress;
        } else if (stress < minStress) {
            stress = minStress;
        }

        if (debuff.equals(Debuff.LACK_CONTRAST)) {
            // don't update stress index for drawables
        } else {
            AbstractDrawable.stressIndex = (int) stress;
        }

        // used for other calculations 
        stressRating = stress - stressEffectThreshold;
    }

    public float getNoteDuration() {
        if (!debuff.equals(Debuff.LACK_CONTRAST)
                && (stress >= stressEffectThreshold || debuff.equals(Debuff.STRESS_MOTIVATED))) {
            return 1 - 0.65f * (stressRating / stressRange);
        } else {
            return 1;
        }
    }

    public void update() {
        updateStress();
        pcThrust();
        pcFriction();
        pcMinSpeed();
        calcStressHSBColour();
        sketchiness();
        oldStress = stress;
    }
}
