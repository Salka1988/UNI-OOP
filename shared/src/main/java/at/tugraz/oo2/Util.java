package at.tugraz.oo2;

import at.tugraz.oo2.helpers.Log;
import javafx.scene.control.TextField;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.TimeZone;

/**
 * Holds some constants and useful methods.
 */
public final class Util {
	//--------------------------------------------- user defined
	public static final long CACHE_SIZE = 1024;
	public static final long CACHE_KEEP_TIME = 10;
	public static final long CACHE_OUT_INTERVAL = 10;
	public static final long MIN_TO_MS = 60 * 1000;
	public static final long NOW_CACHE_KEEP_TIME = 5;
	//---------------------------------------------

	public static final long EPOCH = 5 * 60 * 1000;

	public static final DateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	public static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
	private static final DateFormat USER_TIME_FORMAT0 = new SimpleDateFormat("yyyy-MM-dd-HH:mm");
	private static final DateFormat USER_TIME_FORMAT1 = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");

	public static final TimeZone UTC = TimeZone.getTimeZone("UTC");
	public static final DateFormat INFLUX_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	public static final DateFormat HUMAN_TIME_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
	public static final DateFormat HUMAN_DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy");

	static {
		INFLUX_TIME_FORMAT.setTimeZone(UTC);
	}

	public static long parseUserTime(String in) {
		try {
			return USER_TIME_FORMAT1.parse(in).getTime();
		} catch (final ParseException ex) {

		}
		try {
			return USER_TIME_FORMAT0.parse(in).getTime();
		} catch (final ParseException ex) {

		}
		try {
			return DATE_FORMAT.parse(in).getTime();
		} catch (final ParseException ex) {

		}
		throw new IllegalArgumentException("Invalid date format " + in);
	}

	/**
	 * Resamples an array containing a time series to a new length. For example,
	 * if the input is {1, 2, 3} and the array is scaled to 6 elements, the
	 * output will be {1, 1, 2, 2, 3, 3}.
	 */
	public static double[] resize(double[] in, int outLength) {
		final int inLength = in.length;
		if (inLength == outLength) {
			return in;
		}
		final double[] out = new double[outLength];
		final double factor = inLength / (double) outLength;
		for (int i = 0; i < outLength; i++) {
			final int j = (int) (i * factor);
			out[i] = in[j];
		}
		return out;
	}

	private Util() {

	}

	public static long parseInfluxTime(String in) {
		try {
			DateTime dateTime = ISODateTimeFormat.dateTimeParser().parseDateTime(in);
			return dateTime.getMillis();
		}
		catch (Exception e) {
			Log.ERROR("Couldn't parse INFLUX TIME given: " + in, true);
		}
		throw new IllegalArgumentException("Invalid date format " + in);
	}

	public static String convertMillisToUTC(long time) {
		try {
			return Instant.ofEpochMilli(time).toString();
		}
		catch (Exception e){
			Log.ERROR("Couldn't convert unix-milis to UTC!", true);
		}
		return null;
	}


	/*
	* Makes a text field accept only numbers
	* */
	public static void makeTextFieldNumberOnly(TextField textField) {
		textField.textProperty().addListener((observableValue, s, t1) -> {
			if (t1 != null && !t1.matches("\\d*")) {
				textField.textProperty().set(t1.replaceAll("[^\\d]", ""));
			}
		});
	}
}