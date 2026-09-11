package com.anton.elementalwands.entity;

/** Balance envelopes, independent of vanilla's shared hurt cooldown. */
public final class GuardianGuardContractTest {
    public static void run() {
        for (int party = 1; party <= 8; party++) {
            float maximum = GuardianGuardRules.health(party);
            // Each direct blast component accepted into an exposed core remains
            // bounded across party sizes. Falling-block contact is tested live.
            float volley = party * GuardianGuardRules.impact(211) * GuardianGuardRules.EXPOSED_DAMAGE;
            require(volley < maximum * .4f, "One meteor volley skips the encounter for party " + party);
        }
        require(GuardianGuardRules.impact(7) == 7, "Ordinary attacks were softened");
        require(GuardianGuardRules.impact(211) > GuardianGuardRules.impact(60), "Larger ultimates lost their advantage");
        require(GuardianGuardRules.openness(-1)==0 && GuardianGuardRules.openness(0)==0
                && GuardianGuardRules.openness(24)==1 && GuardianGuardRules.openness(164)==1
                && GuardianGuardRules.openness(194)==0, "Visual and damage boundaries disagree");
        require(GuardianGuardRules.cracks(120,120)==0 && GuardianGuardRules.cracks(90,120)==1
                && GuardianGuardRules.cracks(60,120)==2 && GuardianGuardRules.cracks(30,120)==3, "Crack cues drifted");
        System.out.println("Guardian guard checks passed: solo/eight-player burst envelope, ordinary damage, crack stages, exposure timing.");
    }
    private static void require(boolean ok, String why) { if(!ok) throw new AssertionError(why); }
}
