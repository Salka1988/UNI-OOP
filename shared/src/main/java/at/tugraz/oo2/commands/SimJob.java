package at.tugraz.oo2.commands;

import at.tugraz.oo2.data.MatchedCurve;

import java.io.Serializable;
import java.util.List;

public class SimJob implements Serializable {

    public long origin, worker;
    public SimData simData;
    public List<MatchedCurve> result;

    public SimJob(long origin, long worker, SimData simData) {
        this.origin = origin;
        this.worker = worker;
        this.simData = simData;
        this.result = null;
    }

    @Override
    public String toString() {
        return "Origin: " + this.origin + "\nWorker: " + this.worker + "\nResult size: 0" +
                "\nSim Data: " + this.simData.toString();
    }
}
