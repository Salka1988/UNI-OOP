package at.tugraz.oo2.server.Cache;

import at.tugraz.oo2.Util;
import at.tugraz.oo2.data.DataPoint;
import at.tugraz.oo2.data.DataSeries;
import at.tugraz.oo2.data.Sensor;
import at.tugraz.oo2.helpers.Log;
import at.tugraz.oo2.server.AnalysisServer;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// inspired by Google Guava and their explanation of how cache works
// referenced by: https://github.com/google/guava/wiki/CachesExplained
//
// server has its own cache/ maybe let class be static because only one cache per server and
// we start only one server anyway
//
public class CacheManager implements Serializable {

    private final AnalysisServer server;
    private final LoadingCache<CacheObject, DataSeries> dataCache;
    private final LoadingCache<ListCacheObject, List<Sensor>> listCache;
    private final LoadingCache<NowCacheObject, DataPoint> nowCache;
    private ScheduledFuture<?> service;

    public CacheManager(AnalysisServer analysisServer) throws Exception {

        this.server = analysisServer;
        this.dataCache = CacheBuilder.newBuilder().expireAfterAccess(Util.CACHE_KEEP_TIME, TimeUnit.MINUTES)
                .maximumSize(Util.CACHE_SIZE)
                .build(new CacheLoader<>() {
                    @Override
                    public DataSeries load(CacheObject cacheObject) throws Exception {
//                        Log.INFO("Need to cache data series!", true);
                        // load is started if the data is not in cache when .getting, saves new data to cache and returns
                        server.influxConnection.getDataSeries(cacheObject.getDataObject());
                        return cacheObject.getDataObject().dataSeries;
                    }
                });

        this.listCache = CacheBuilder.newBuilder().expireAfterWrite(Util.CACHE_KEEP_TIME, TimeUnit.MINUTES)
                .maximumSize(2)
                .build(new CacheLoader<>() {
                        @Override
                    public List<Sensor> load(ListCacheObject listCacheObject) throws Exception {
//                            Log.INFO("Need to cache sensor list!", true);
                            server.influxConnection.getAllSensors(listCacheObject.getDataObject());
                            return listCacheObject.getDataObject().sensorList;
                        }
                });

        this.nowCache = CacheBuilder.newBuilder().expireAfterWrite(Util.NOW_CACHE_KEEP_TIME, TimeUnit.SECONDS)
                .maximumSize(Util.CACHE_SIZE)
                .build(new CacheLoader<>() {
                           @Override
                           public DataPoint load(NowCacheObject nowCacheObject) throws Exception {
//                               Log.INFO("Need to cache current sensor value!", true);
                               server.influxConnection.getLatestSensor(nowCacheObject.getDataObject());
                               return nowCacheObject.getDataObject().dataPoint;
                           }
                       }

                );

                if (new File("cacheOut.json").exists())
                {
                    dataCache.asMap().putAll(new cacheInThread().call());
                }

        cacheExport();
    }


    // get cache object so we could retrieve data
    // use .get(DataObject) where DataObject is key in cache
    // so if we cached "same dataObject before, we get dataSeries, else we call function that gets data series store in
    // cache and return new queried data
    //
    public DataSeries getCachedData(CacheObject cacheObject) {
        try {
            DataSeries dataSeries = this.dataCache.get(cacheObject);
            return dataSeries;
        } catch (ExecutionException e) {
            Log.ERROR("Couldn't retrieve data from cache!", true);
        }

        return null;
    }

    public List<Sensor> getCachedSensorList(ListCacheObject listCacheObject) {
        try {
           return this.listCache.get(listCacheObject);
        }
        catch (ExecutionException e) {
        Log.ERROR("Couldn't retrieve sensor list from cache!", true);
        }

        return null;
    }

    public DataPoint getCachedCurrentData(NowCacheObject nowCacheObject) {
        try {
            return this.nowCache.get(nowCacheObject);
        }
        catch (ExecutionException e) {
            Log.ERROR("Couldn't retrieve current sensor value from cache!", true);
        }

        return null;
    }

    public void cacheExport() {
        service = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            Log.INFO("In Cache Export", true);
            try {
                new cacheOutThread(this.dataCache).call();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, Util.CACHE_KEEP_TIME, TimeUnit.MINUTES);

    }

    public void  turnServiceOff()
    {
        this.service.cancel(true);
    }
}


