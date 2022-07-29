package io.kasava.data;

public class CanMsg {

    public enum TYPE {
        FUEL_TANK_PERC,
        EV_BATTERY_PERC
    }

    public TYPE type;
    public double value;
}
