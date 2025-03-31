package org.example.yasspfe.scenarios;

import java.util.Random;

public class PacketLossInjector {
    private final double lossRate; // Pourcentage de perte (ex: 10% -> 0.1)
    private final Random random;

    public PacketLossInjector(double lossRate) {
        this.lossRate = lossRate;
        this.random = new Random();
    }

    public boolean shouldDropPacket() {
        return random.nextDouble() < lossRate;
    }
}
