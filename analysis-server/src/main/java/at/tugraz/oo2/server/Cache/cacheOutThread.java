package at.tugraz.oo2.server.Cache;

import at.tugraz.oo2.data.DataSeries;
import at.tugraz.oo2.helpers.Log;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Callable;

public class cacheOutThread implements Callable<Double> {

    private static final String FILENAME = "./cacheOut.json";

    private final LoadingCache<CacheObject, DataSeries> cache;
    public cacheOutThread(LoadingCache<CacheObject, DataSeries> cacheIn) {
        this.cache = cacheIn;
    }

    @Override
    public Double call() throws Exception {

        JsonArray cacheDataSeries = new JsonArray();

        this.cache.asMap().forEach((key, value) -> {
            JsonObject cacheObject = new JsonObject();
            cacheObject.add("cashDataObject", key.getDataObject().toJsonOut());
            cacheObject.add("dataSeries", value.toJsonOut());
            cacheDataSeries.add(cacheObject);
        });

        // do not write empty array to file, this happens when the cache is emptied after given time
        if (!cacheDataSeries.toString().equals("[]")) {
            try {
                // read all from cacheOut
                FileReader fileReader = new FileReader(FILENAME);
                StringBuilder content = new StringBuilder();
                int iterator;
                while ((iterator = fileReader.read()) != -1)
                    content.append((char) iterator);

                fileReader.close();
                // save as array
                JsonArray jsonArray = new Gson().fromJson(content.toString(), JsonArray.class).getAsJsonArray();

                // check if the persistent cache already contains objects that are to be written
                jsonArray.forEach(element -> {
                    if (cacheDataSeries.contains(element)) {
                        // if so, remove it, we do not need duplicates
                        cacheDataSeries.remove(element);
                    }
                });

                // then add all new cache entries
                jsonArray.addAll(cacheDataSeries);

//                Log.INFO("File cacheOut.json found, check existing data, append new", true);

                // and write to back to disk
                writeToFile(jsonArray.toString());
            } catch (IOException exception) {
                Log.INFO("File cacheOut.json not found, no appending, create new file", true);
                writeToFile(cacheDataSeries.toString());
                return null;
            }
        } else {
            Log.INFO("Cache probably emptied after some time, nothing to write to disk", true);
        }

        return null;
    }

    private void writeToFile(String content) {
        try {
            FileWriter fileWriter = new FileWriter(FILENAME);
            fileWriter.write(content);
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}