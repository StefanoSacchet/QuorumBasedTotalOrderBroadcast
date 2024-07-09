package it.unitn.ds1.tools;

import java.io.Serializable;

public class Pair<A, B> implements Serializable {
    public A first;
    public B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public A getFirst() {
        return first;
    }

    public B getSecond() {
        return second;
    }

    public void setFirst(A first) {
        this.first = first;
    }

    public void setSecond(B second) {
        this.second = second;
    }

    @Override
    public String toString() {
        return "Pair{" +
                "first=" + first.toString() +
                ", second=" + second.toString() +
                '}';
    }
}
