package at.tugraz.oo2.commands;

import at.tugraz.oo2.data.DataPoint;
import at.tugraz.oo2.data.DataSeries;
import at.tugraz.oo2.data.LiveData;
import at.tugraz.oo2.data.Sensor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import at.tugraz.oo2.data.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@EqualsAndHashCode
@ToString

public class DataObject implements Serializable {

    public DataObjectBuilder.Command command;
    public Sensor sensor;
    public long from;
    public long to;
    public long interval;

    @EqualsAndHashCode.Exclude public long intervalPoints;
    @EqualsAndHashCode.Exclude public long intervalClusters;
    @EqualsAndHashCode.Exclude public int numberOfClusters;


    @EqualsAndHashCode.Exclude private long timestamp;
    @EqualsAndHashCode.Exclude public List<Sensor> sensorList;
    @EqualsAndHashCode.Exclude public DataPoint dataPoint;
    @EqualsAndHashCode.Exclude public DataSeries dataSeries;
    @EqualsAndHashCode.Exclude public List<LiveData> liveDataList;
    @EqualsAndHashCode.Exclude public List<ClusterDescriptor> clusters;

    // for similiarity search
    @EqualsAndHashCode.Exclude public SimJob job;
    /**
     * In an error occurs during a query return DataSeries with
     * errorMessage != null. Handle in client.
     */
    @EqualsAndHashCode.Exclude @Getter @Setter private String errorMessage;

    public DataObject getCopy() {
        DataObject tohta = new DataObject(this.command, this.sensor, this.from, this.to, this.interval);
        if(this.job != null) tohta.job =  this.copySimJob();
        else tohta.job = null;

        return tohta;
    }

    public SimJob copySimJob() {
        SimJob job = new SimJob(this.job.origin, this.job.worker, this.job.simData);
        job.result = this.job.result;
        return job;
    }

    public DataObject getCopyForClustering() {
        DataObject dataObject = new DataObject(this.command, this.sensor, this.from, this.to, this.interval);
        dataObject.intervalPoints = this.intervalPoints;
        dataObject.intervalClusters = this.intervalClusters;
        dataObject.numberOfClusters = this.numberOfClusters;

        return dataObject;
    }

    public DataObject(DataObjectBuilder.Command command, Sensor sensor, long from, long to, long interval) {
        this.command = command;
        this.sensor = sensor;
        this.from = from;
        this.to = to;
        this.interval = interval;
        this.timestamp = Instant.now().toEpochMilli(); //test
        this.errorMessage = null;
        this.job = null;
    }

    public JsonElement toJsonOut() {

        String fromToJson = Long.toString(from);
        String toToJson = Long.toString(to);
        String commandJson = command.toString();
        String intervalJson = Long.toString(interval);

        //sensor
        JsonArray sensorValuesJson = new JsonArray();
        sensorValuesJson.add(sensor.getLocation());
        sensorValuesJson.add(sensor.getMetric());

        //arrayOfDataPointValue
        JsonObject dataObjectJson = new JsonObject();
        dataObjectJson.add("sensor",sensorValuesJson);
        dataObjectJson.addProperty("from", fromToJson);
        dataObjectJson.addProperty("to", toToJson);
        dataObjectJson.addProperty("command", commandJson);
        dataObjectJson.addProperty("interval", intervalJson);

        return dataObjectJson;
    }

}
