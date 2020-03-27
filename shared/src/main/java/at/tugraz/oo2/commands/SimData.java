package at.tugraz.oo2.commands;

import at.tugraz.oo2.data.MatchedCurve;
import lombok.*;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

@Data
public class SimData implements Serializable {
    public final String metric;
    public final long from;
    public final long to;
    public final long minSize;
    public final long maxSize;
    public final int maxResultCount;
    public final double[] ref;

    @Override
    public String toString() {
        return  "Metric: " + this.metric + "\nfrom: " + this.from + "\nto: " + this.to + "\nminSize: " + this.minSize +
                "\nmaxSize: " + this.maxSize + "\nmaxResultCount: " + this.maxResultCount + "\nRef curve: " + Arrays.toString(this.ref);
    }
}
