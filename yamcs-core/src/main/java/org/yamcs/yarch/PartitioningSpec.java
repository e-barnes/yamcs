package org.yamcs.yarch;


public class PartitioningSpec {

    public enum _type{TIME, VALUE, TIME_AND_VALUE};
    public _type type;
    public String timeColumn;
    public String valueColumn;
    public DataType valueColumnType;
    
    
    
    @Override
    public String toString() {
        return "timeColumn: "+timeColumn+" valueColumn:"+valueColumn;
    }
}
