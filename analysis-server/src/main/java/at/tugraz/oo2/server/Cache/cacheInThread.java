package at.tugraz.oo2.server.Cache;


import at.tugraz.oo2.commands.DataObject;
import at.tugraz.oo2.commands.DataObjectBuilder.Command;
import at.tugraz.oo2.data.DataPoint;
import at.tugraz.oo2.data.DataSeries;
import at.tugraz.oo2.data.Sensor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Callable;

public class cacheInThread implements Callable<HashMap<CacheObject, DataSeries>> {

    private final HashMap<CacheObject, DataSeries> cacheFromOut = new HashMap<CacheObject, DataSeries>();

    @Override
    public HashMap<CacheObject, DataSeries> call() throws Exception {

        JsonParser jsonParser = new JsonParser();
        try (FileReader reader = new FileReader("cacheOut.json")) {
            JsonArray inputData = (JsonArray) jsonParser.parse(reader);

            inputData.forEach(cacheObjects -> {
                JsonElement cacheObject = cacheObjects.getAsJsonObject().get("cashDataObject");
                JsonElement dataObject = cacheObjects.getAsJsonObject().get("dataSeries");

                CacheObject cacheObjectFromOut = new CacheObject(
                        new DataObject(Command.valueOf(cacheObject.getAsJsonObject().get("command").toString().replace("\"","")),
                                new Sensor(
                                cacheObject.getAsJsonObject().get("sensor").getAsJsonArray().get(0).toString().replace("\"",""),
                                cacheObject.getAsJsonObject().get("sensor").getAsJsonArray().get(1).toString().replace("\"","")),
                                Long.parseLong(cacheObject.getAsJsonObject().get("from").toString().replace("\"","")),
                                Long.parseLong(cacheObject.getAsJsonObject().get("to").toString().replace("\"","")),
                                Long.parseLong(cacheObject.getAsJsonObject().get("interval").toString().replace("\"",""))));

                double[] doubleDataFromOut = new double[dataObject.getAsJsonObject().get("data").getAsJsonArray().size()];
                for (int i = 0; i < dataObject.getAsJsonObject().get("data").getAsJsonArray().size(); i++) {
                    doubleDataFromOut[i] = Double.parseDouble(String.valueOf(dataObject.getAsJsonObject().get("data").getAsJsonArray().get(i)));
                }
                boolean[] presentDataFromOut = new boolean[dataObject.getAsJsonObject().get("present").getAsJsonArray().size()];
                for (int i = 0; i < dataObject.getAsJsonObject().get("present").getAsJsonArray().size(); i++) {
                    presentDataFromOut[i] = Boolean.parseBoolean(String.valueOf(dataObject.getAsJsonObject().get("present").getAsJsonArray().get(i)));
                }

                DataSeries dataSeriesFromOut = new DataSeries(
                        Long.parseLong(dataObject.getAsJsonObject().get("minTime").toString().replace("\"","")),
                        Long.parseLong(dataObject.getAsJsonObject().get("interval").toString().replace("\"","")),
                        doubleDataFromOut,
                        presentDataFromOut
                );

                cacheFromOut.put(cacheObjectFromOut, dataSeriesFromOut);
            });
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return  this.cacheFromOut;
    }
}