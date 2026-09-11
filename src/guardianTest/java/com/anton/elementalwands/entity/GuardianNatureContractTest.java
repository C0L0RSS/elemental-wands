package com.anton.elementalwands.entity;

public final class GuardianNatureContractTest {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void run() {
        var response = new GuardianNatureResponse();
        response.entangle(4,10);
        require(response.finishAttack(20)==0,"Partial vines extended recovery");
        response.entangle(5,30);
        for (int i=31;i<100;i++) response.entangle(5,i);
        require(response.finishAttack(100)==20,"Earned opening lost or extended by overlapping casters");
        for(int i=101;i<300;i++) response.entangle(5,i);
        require(response.finishAttack(299)==0,"Continuous vines bypassed immunity");
        response.entangle(5,300);
        require(response.finishAttack(330)==20,"Restraint did not return after immunity");
        response.thorn(400); response.thorn(420);
        require(!response.wantsClear(420),"One second of thorns caused an immediate clear");
        response.thorn(440);
        require(response.wantsClear(440),"Sustained thorns did not trigger a clear");
        response.clearing(440); response.thorn(460);
        require(!response.wantsClear(460),"Clearing cooldown failed");
        require(!response.wantsClear(650),"Stale pressure caused a phantom clear");
        response.reset();
        require(response.finishAttack(700)==0 && !response.wantsClear(700),"Reset retained Nature pressure");
        System.out.println("Guardian Nature checks passed: earned recovery, shared immunity, sustained pressure, clear cooldown and reset.");
    }
}
