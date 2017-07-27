package de.offis.feelslike.insituarousal;

import java.util.Comparator;

/**
 * Created by QuincyLappie on 17.07.2017.
 */
public class HeartRateMeasurement implements Comparable<HeartRateMeasurement>{

    private double heartRate;
    private long timeStamp;

    public HeartRateMeasurement(double heartRate){
        this.heartRate = heartRate;
        this.timeStamp = System.currentTimeMillis();
    }

    public double getHeartRate() {
        return heartRate;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    @Override
    public int compareTo(HeartRateMeasurement heartRateMeasurement) {
        long diff = timeStamp - heartRateMeasurement.getTimeStamp();
        if(diff > 0)        return 1;
        else if(diff < 0)   return -1;
        else                return 0;
    }
}
