package at.tugraz.oo2.data;

import lombok.Data;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
public class LiveData implements Serializable {
    private final String location;
    private final String metric;
    private Double data = null;
    private String timestamp = null;

    public void setDataPoint(DataPoint dataPoint) {
        DecimalFormat df = new DecimalFormat("0.00");
        this.data = Double.valueOf(df.format(dataPoint.getValue()));

        DateTime dt = new DateTime(dataPoint.getTime(), DateTimeZone.getDefault());
        DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:ss");
        this.timestamp = dtf.print(dt);

    }
}
