package it.unitn.ds1;

import java.io.Serializable;

import it.unitn.ds1.tools.Pair;

public class UpdateIdentifier implements Serializable {
    public Pair<Integer, Integer> updateIdentifier;

    public UpdateIdentifier(int epoch, int sequence) {
        this.updateIdentifier = new Pair<>(epoch, sequence);
    }

    // Getters
    public int getEpoch() {
        return this.updateIdentifier.getFirst();
    }

    public int getSequence() {
        return this.updateIdentifier.getSecond();
    }

    // Setters
    public void setEpoch(int epoch) {
        this.updateIdentifier.setFirst(epoch);
    }

    public void setSequence(int sequence) {
        this.updateIdentifier.setSecond(sequence);
    }

    public void increaseEpoch() {
        this.updateIdentifier.setFirst(this.getEpoch() + 1);
        this.updateIdentifier.setSecond(0);
    }

    public int compareTo(UpdateIdentifier updateIdentifier) {
        if (this.getEpoch() == updateIdentifier.getEpoch()) {
            return this.getSequence() - updateIdentifier.getSequence();
        }
        return this.getEpoch() - updateIdentifier.getEpoch();
    }

    // override equals method
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof UpdateIdentifier updateIdentifier)) {
            return false;
        }
        return this.getEpoch() == updateIdentifier.getEpoch() && this.getSequence() == updateIdentifier.getSequence();
    }

    @Override
    public String toString() {
        return "(" + this.getEpoch() + ", " + this.getSequence() + ")";
    }
}
