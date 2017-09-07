package de.offis.feelslike.insituarousal.containers;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

/**
 * Created by Christoph Ressel on 30.08.2017.
 */

public class StatisticalResult implements Comparable<StatisticalResult>, Parcelable{

    private long timeStamp;
    private double mrr;
    private double sdnn;
    private double rmssd;

    public StatisticalResult(long timeStamp, double mrr, double sdnn, double rmssd){
        this.timeStamp = timeStamp;
        this.mrr = mrr;
        this.sdnn = sdnn;
        this.rmssd = rmssd;
    }

    // Required for Parcelable
    public StatisticalResult(Parcel in){
        double[] data = new double[4];

        in.readDoubleArray(data);
        // the order needs to be the same as in writeToParcel() method
        this.timeStamp = (long) data[0];
        this.mrr = data[1];
        this.sdnn = data[2];
        this.rmssd = data[3];
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public double getMrr() {
        return mrr;
    }

    public double getSdnn() {
        return sdnn;
    }

    public double getRmssd() {
        return rmssd;
    }

    @Override
    public int compareTo(@NonNull StatisticalResult statisticalResult) {
        long diff = timeStamp - statisticalResult.getTimeStamp();
        if(diff > 0)        return 1;
        else if(diff < 0)   return -1;
        else                return 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDoubleArray(new double[] {
                (double)this.timeStamp,
                this.mrr,
                this.sdnn,
                this.rmssd});
    }

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
        public StatisticalResult createFromParcel(Parcel in) {
            return new StatisticalResult(in);
        }

        public StatisticalResult[] newArray(int size) {
            return new StatisticalResult[size];
        }
    };
}