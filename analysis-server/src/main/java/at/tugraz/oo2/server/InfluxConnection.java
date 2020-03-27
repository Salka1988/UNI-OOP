package at.tugraz.oo2.server;

import at.tugraz.oo2.Util;
import at.tugraz.oo2.commands.DataObject;
import at.tugraz.oo2.data.ClusterDescriptor;
import at.tugraz.oo2.data.DataPoint;
import at.tugraz.oo2.data.DataSeries;
import at.tugraz.oo2.data.Sensor;
import at.tugraz.oo2.helpers.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * Handles the Influx API and should provide features like querying sensors and their values, etc.
 */
public final class InfluxConnection {
	private final String dbAuth;

	public InfluxConnection(String url, String databaseName, String userName, String password) {
		this.dbAuth = url + '/' + "/query?db=" + databaseName + "&u=" + userName + "&p=" + password;
	}


	public void getAllSensors(DataObject dataObject) {
		List<Sensor> list = new ArrayList<>();
		String responseString;
		String queryReadable = "&q=SHOW SERIES on oo2";

		if ((responseString = getDataFromDB(queryReadable, dataObject)) == null) {
			reportError("error fetching all sensors", dataObject);
			dataObject.sensorList = new ArrayList<>(0);
			return;
		}

		try {
			Gson gson = new Gson();
			JsonObject json = gson.fromJson(responseString, JsonObject.class);
			JsonObject results = json.getAsJsonArray("results").get(0).getAsJsonObject();

			JsonObject series = results.getAsJsonArray("series").get(0).getAsJsonObject();
			JsonArray values = series.getAsJsonArray("values");

			for (JsonElement string : values) {
				String[] resultString = string.getAsString().split(",location=");
				String metric = resultString[0];
				String location = resultString[1];
				list.add(new Sensor(location, metric));
			}
		} catch (Exception e) {
			reportError("Parsing of data failed!", dataObject);
			dataObject.sensorList = new ArrayList<>(0);
			return;
		}

		dataObject.sensorList = list;
	}

	public void getLatestSensor(DataObject dataObject) {
		if (!checkRoomSensorExistence(dataObject))
			return;

		String responseString;
		String queryReadable = "&q=SELECT * FROM " + dataObject.sensor.getMetric() + " WHERE location='" + dataObject.sensor.getLocation() + "' ORDER BY DESC LIMIT 1";

		if ((responseString = getDataFromDB(queryReadable, dataObject)) == null) {
			reportError("error fetching latest sensor", dataObject);
			dataObject.dataPoint = new DataPoint(0, 0);
			return;
		}

		try {
			Gson gson = new Gson();
			JsonObject json = gson.fromJson(responseString, JsonObject.class);
			JsonObject results = json.getAsJsonArray("results").get(0).getAsJsonObject();
			JsonArray series = results.getAsJsonArray("series");

			if(series == null) {
				reportError("There is no data for this metric!", dataObject);
				dataObject.dataPoint = new DataPoint(0, 0);
				return;
			}

			JsonArray values = series.get(0).getAsJsonObject().getAsJsonArray("values");

			String time = values.get(0).getAsJsonArray().get(0).getAsString();
			String value = values.get(0).getAsJsonArray().get(2).getAsString();

			dataObject.dataPoint = new DataPoint(Util.parseInfluxTime(time), Double.parseDouble(value));

		} catch (Exception e) {
			reportError("Parsing of data failed!", dataObject);
			dataObject.dataPoint = new DataPoint(0, 0);
		}
	}

	public void getDataSeries(DataObject dataObject) {
		if (!checkRoomSensorExistence(dataObject))
			return;

		String responseString;
		ArrayList<Pair<Double, Boolean>> dataPoints = new ArrayList<>();

		String fromEncoded = Util.convertMillisToUTC(dataObject.from);
		String toEncoded = Util.convertMillisToUTC(dataObject.to);

		String queryReadable = "&q=SELECT * FROM " + dataObject.sensor.getMetric() + " WHERE location = '" + dataObject.sensor.getLocation() + "' AND time >= '" + fromEncoded + "' AND time < '" + toEncoded + "'";

		if ((responseString = getDataFromDB(queryReadable, dataObject)) == null) {
			reportError("error fetching data series", dataObject);
			return;
		}

		Gson gson = new Gson();
		JsonObject json = gson.fromJson(responseString, JsonObject.class);
		JsonObject results = json.getAsJsonArray("results").get(0).getAsJsonObject();

		if (!results.has("series")) {
			long size = (dataObject.to - dataObject.from) / dataObject.interval;
			double[] data = new double[(int) size];
			boolean[] present = new boolean[(int) size];

			for (int i = 0; i < size; i++) {
				data[i] = 0.0;
				present[i] = false;
			}

			dataObject.dataSeries = new DataSeries(dataObject.from, Util.EPOCH, data, present);
		} else {
			JsonObject series = results.getAsJsonArray("series").get(0).getAsJsonObject();
			JsonArray values = series.getAsJsonArray("values");

			long currentTime = dataObject.from;

			int i = 0;
			while(currentTime <= dataObject.to) {
				try {
					if(i >= values.size()) {
					//	Log.INFO(fromEncoded + " | " + "not present", true);
						dataPoints.add(new Pair<>(0.0, false)); // missing data point
						currentTime += Util.EPOCH;
						continue;
					}

					//get timestamp in millis
					String timestamp = values.get(i).getAsJsonArray().get(0).getAsString();
					long timestampMillis = Util.parseInfluxTime(timestamp);

					// if same then datapoint exists
					if(currentTime == timestampMillis) {
					//	Log.INFO(fromEncoded + " | " + values.get(i).getAsJsonArray().get(2).getAsDouble(), true);
						dataPoints.add(new Pair<>(values.get(i).getAsJsonArray().get(2).getAsDouble(), true));
						i++;
						currentTime += Util.EPOCH;
					}
					// if current bigger than element, then move element forward
					else if(currentTime > timestampMillis) {
						i++;
					}
					//if current smaller, then datapoint never existed, move current forward
					else if(currentTime < timestampMillis) {
					//	Log.INFO(fromEncoded + " | " + "not present", true);
						dataPoints.add(new Pair<>(0.0, false)); // missing data point
						currentTime += Util.EPOCH;
					}
					else {
						Log.ERROR("Couldn't compare times, this shouldn't happen!", true);
					}
				}
				catch (Exception e) {
					reportError(String.format("Problem while parsing the retrieved data! Reason: [%s]", e.getMessage()), dataObject);
					return;
				}
			}

			double[] data = new double[dataPoints.size()];
			boolean[] present = new boolean[dataPoints.size()];

			i = 0;
			for (Pair<Double, Boolean> dataPoint : dataPoints)
			{
				data[i] = dataPoint.getFirst();
				present[i++] = dataPoint.getSecond();
			}

			Log.INFO("Data series with " +  dataPoints.size(), true);
			dataObject.dataSeries = new DataSeries(dataObject.from, Util.EPOCH, data, present);
		}
	}

	public void getClusters(DataObject dataObject) {

		if (dataObject == null || dataObject.dataSeries == null)
			return;

		// to be sure
		dataObject.dataSeries = dataObject.dataSeries.interpolate();

		dataObject.dataSeries = dataObject.dataSeries.scale(dataObject.intervalPoints);
		Log.INFO("scale size: " + dataObject.dataSeries.getData().length + " real data points " + dataObject.dataSeries.getDataPoints().size(), true);

		// to be sure
		dataObject.dataSeries = dataObject.dataSeries.interpolate();
		Log.INFO("interpolate size: " + dataObject.dataSeries.getData().length, true);

		List<DoublePoint> subSeries = new ArrayList<>();
		HashMap<DoublePoint, DataSeries> pointToSeries = new HashMap<>();
		HashMap<DoublePoint, DataSeries> pointToSeriesNormalized = new HashMap<>();
		for (long time = dataObject.dataSeries.getMinTime(); time < dataObject.dataSeries.getMaxTime(); time += dataObject.intervalClusters) {
			DataSeries newSubSeries = dataObject.dataSeries.subSeries(time, time + dataObject.intervalClusters);
			DataSeries newSubSeriesNormalized = newSubSeries.normalize();
			if (!newSubSeriesNormalized.hasGaps()) {
			    DoublePoint newDoublePointNormalized = new DoublePoint(newSubSeriesNormalized.getData());
			    DoublePoint newDoublePoint = new DoublePoint(newSubSeriesNormalized.getData());
			    pointToSeriesNormalized.put(newDoublePointNormalized, newSubSeriesNormalized);
			    pointToSeries.put(newDoublePoint, newSubSeries);
				subSeries.add(newDoublePoint);
			}
		}

		Log.INFO("subseries size: " + subSeries.size(), true);

		if (subSeries.isEmpty()) return;

		KMeansPlusPlusClusterer<DoublePoint> kMeansPlusPlus = new KMeansPlusPlusClusterer<>(dataObject.numberOfClusters);
		List<CentroidCluster<DoublePoint>> clusters = kMeansPlusPlus.cluster(subSeries);

        List<ClusterDescriptor> clusterDescriptors = new ArrayList<>();
        for (CentroidCluster<DoublePoint> cluster : clusters) {
            List<DataSeries> pointsInClusterNormalized = new ArrayList<>();
            List<DataSeries> pointsInCluster = new ArrayList<>();
            for (DoublePoint dp : cluster.getPoints()) {
                if (pointToSeries.get(dp) == null) {
                    reportError("Nikola your mapping from DoublePoint to DataSeries sucks ass. GO FIX IT!", dataObject);
                    return;
                }
                pointsInClusterNormalized.add(pointToSeriesNormalized.get(dp));
                pointsInCluster.add(pointToSeries.get(dp));
            }
            Log.INFO("Cluster has " + pointsInClusterNormalized.size() + " points", true);
            clusterDescriptors.add(new ClusterDescriptor(
            		cluster.getCenter().getPoint(),
					pointsInClusterNormalized,
					pointsInCluster));
        }

		Log.INFO("Clustering DONE! Size:" + clusterDescriptors.size(), true);
        dataObject.clusters = clusterDescriptors;
	}

	/**
	 * Queries the database to check if the wanted room and sensor are existing
	 */
	private boolean checkRoomSensorExistence(DataObject dataObject) {
		String queryReadable = "&q=SHOW SERIES on oo2 FROM " + dataObject.sensor.getMetric() + " WHERE \"location\" = '" + dataObject.sensor.getLocation() + "'";
		String responseString;

		if ((responseString = getDataFromDB(queryReadable, dataObject)) == null) {
			reportError("error checking room sensor existence", dataObject);
			return false;
		}

		if (	responseString.length() <= 40) {
			reportError("There is no existent (room - sensor) pair for the given entry. Type 'ls' for all possible combinations.", dataObject);
			return false;
		}

		return true;
	}


	/**
	 * Captures the query respond from server. Handles server error also.
	 * @return
	 */
	private String getDataFromDB(String queryReadable, DataObject dataObject) {
		String query = encodeToURI(queryReadable);
		try {
			return Unirest.get(dbAuth + query).asString().getBody();
		} catch (Exception e) {
			reportError(String.format("Retrieving of data from server failed! Reason: [%s]", e.getMessage()), dataObject);
			return null;
		}
	}

	private void reportError(String errorMessage, DataObject dataObject) {
		Log.ERROR(errorMessage, true);

		// dummy value for dataSeries
		// when returning null the cache breaks
		double[] data = new double[1];
		boolean[] present = new boolean[1];
		dataObject.dataSeries = new DataSeries(dataObject.from, Util.EPOCH, data, present);

		dataObject.setErrorMessage(errorMessage);
	}

	/**
	 * Encodes readable query to URL
	 * @param str
	 * @return
	 */
	private String encodeToURI(String str)
	{
		str = str.replaceAll(" ", "%20");
		str = str.replaceAll(">", "%3E");
		str = str.replaceAll("<", "%3C");
		str = str.replaceAll("\"", "%22");

		return str;
	}
}
