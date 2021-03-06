package org.yamcs.yarch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.yamcs.YConfiguration;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.management.ManagementService;

import org.junit.Before;
import org.junit.BeforeClass;


import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.StreamSqlResult;


public abstract class YarchTestCase {
	protected StreamSqlParser parser;
	protected ExecutionContext context;
	protected YarchDatabase ydb;
	static boolean littleEndian;
	protected String instance;
	
	static private void deleteDir(File dir) throws IOException {
		if (dir.exists()) {
			File[] files=dir.listFiles();
			for (File f:files) {
				if(f.isDirectory()){
					deleteDir(f);
				} else {
					if(!f.delete()) throw new IOException("Cannot remove "+f);
				}
			}
			if(!dir.delete()) throw new IOException("Cannot remove "+dir);
		}
	}
	
	@BeforeClass 
	public static void setUpYarch() throws Exception {
	    YConfiguration.setup();
	    YConfiguration config=YConfiguration.getConfiguration("yamcs");
	    if(config.containsKey("littleEndian")) {
	        littleEndian=config.getBoolean("littleEndian");
	    } else {
	        littleEndian=false;
	    }
        ManagementService.setup(false);
	}
	
	@Before
	public void setUp() throws Exception {
		YConfiguration config=YConfiguration.getConfiguration("yamcs");
        String dir=config.getString("dataDir");
        instance = "yarchtest_"+this.getClass().getSimpleName();
        context=new ExecutionContext(instance);
        
        File ytdir=new File(dir+"/"+context.getDbName());
        deleteDir(ytdir);
        //System.out.println("creating "+ytdir);
        if(!ytdir.mkdirs()) throw new IOException("Cannot create directory "+ytdir);
        YarchDatabase.removeInstance(instance);
        ydb=YarchDatabase.getInstance(instance);
        
	}
	
	protected StreamSqlResult execute(String cmd) throws StreamSqlException, ParseException {
	    return ydb.execute(cmd);
	}
	
	protected List<Tuple> suckAll(String streamName) throws InterruptedException{
	    final List<Tuple> tuples=new ArrayList<Tuple>();
	    final Semaphore semaphore=new Semaphore(0);
	    Stream s=ydb.getStream(streamName);
	    s.addSubscriber(new StreamSubscriber() {
            
            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }
            
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                tuples.add(tuple);
            }
        });
	    s.start();
	    semaphore.acquire();
	    return tuples;
	}
}
