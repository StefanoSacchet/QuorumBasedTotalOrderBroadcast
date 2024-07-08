package it.unitn.ds1;

import java.io.Serializable;

public class UpdateIdentifier implements Serializable {
    private int epoch;
    private int sequence;

    public UpdateIdentifier(int epoch, int sequence) {
        this.epoch = epoch;
        this.sequence = sequence;
    }

    // Getters
    public int getEpoch() {
        return epoch;
    }
    public int getSequence() {
        return sequence;
    }

    // Setters
    public void setEpoch(int epoch) {
        this.epoch = epoch;
    }
    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    @Override
    public String toString() {
        return "(" + epoch + ", " + sequence + ")";
    }
}
