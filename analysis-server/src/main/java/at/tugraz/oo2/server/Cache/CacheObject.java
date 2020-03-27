package at.tugraz.oo2.server.Cache;

import at.tugraz.oo2.commands.DataObject;
import at.tugraz.oo2.commands.DataObjectBuilder;
import at.tugraz.oo2.helpers.Log;
import com.google.gson.JsonElement;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

public class CacheObject {

    //has all same as DataObject just different equals and hash so we can custom compare if something is in cache or not
    private final @Getter DataObject dataObject;

    public CacheObject(DataObject dataObject) {
       this.dataObject = dataObject;
    }

    @Override
    public boolean equals(Object obj) {
        // checking if both the object references are
        // referring to the same object.
        if(this == obj)
            return true;

        // if obj null or not of same class
        // false
        if(obj == null || obj.getClass()!= this.getClass())
            return false;

        // type casting of the argument.
        CacheObject requestDataObject = (CacheObject) obj;

        // comparing the state of argument with
        // the state of 'this' Object.
        return (this.dataObject.command == requestDataObject.getDataObject().command &&
                requestDataObject.getDataObject().from <= this.dataObject.from &&
                requestDataObject.getDataObject().to >= this.dataObject.to);
    }

    public int hashCode() {
        return Objects.hash(dataObject.sensor, dataObject.command);
    }

}
