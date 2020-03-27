package at.tugraz.oo2.commands;

import at.tugraz.oo2.data.LiveData;
import at.tugraz.oo2.data.Sensor;

import java.util.List;

public class DataObjectBuilder {
    Command command;

    Sensor sensor;
    long from;
    long to;
    long interval;
    List<LiveData> liveDataList;
    SimJob job;

    public enum Command {
        DATA,
        LS,
        NOW,
        CLOSE,
        LIVE,
        SIM,
        SIMJOB,
        CLUSTER
    }

    public DataObjectBuilder setComand(Command command)
    {
        this.command = command;
        return this;
    }

    public DataObjectBuilder setSensor(Sensor sensor) {
        this.sensor = sensor;
        return this;
    }
    public DataObjectBuilder setFrom(long from) {
        this.from = from;
        return this;
    }

    public DataObjectBuilder setTo(long to) {
        this.to = to;
        return this;
    }

    public DataObjectBuilder setInterval(long interval) {
        this.interval = interval;
        return this;
    }

    public DataObjectBuilder setLiveDataList(List<LiveData> liveDataList) {
        this.liveDataList = liveDataList;
        return this;
    }

    public DataObjectBuilder setSimJob(SimJob job) {
        this.job = job;
        return this;
    }

    public DataObject getDataObject() {
        return new DataObject(command,sensor,from,to,interval);
    }

}



