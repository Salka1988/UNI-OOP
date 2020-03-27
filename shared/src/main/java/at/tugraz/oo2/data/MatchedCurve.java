package at.tugraz.oo2.data;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * Represents a single match returned by similarity search.
 */
@Data
public final class MatchedCurve implements Serializable {
	private final Sensor sensor;
	private final DataSeries series;
	private @Getter @Setter DataSeries originalSeries;
	private final double error;
}
