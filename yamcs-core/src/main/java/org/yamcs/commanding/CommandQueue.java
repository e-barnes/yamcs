package org.yamcs.commanding;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.yamcs.Channel;
import org.yamcs.Privilege;

import org.yamcs.protobuf.Commanding.QueueState;

public class CommandQueue {
    String name;
    ConcurrentLinkedQueue<PreparedCommand> commands=new ConcurrentLinkedQueue<PreparedCommand>();
    QueueState state=QueueState.BLOCKED;
    Channel channel;
    
    List<String> roles;
    
    CommandQueue(Channel channel, String name) {
        this.channel=channel;
        this.name=name;
        if(!Privilege.getInstance().isEnabled()) state=QueueState.ENABLED;
    }

    public String getName() {
        return name;
    };
    public QueueState getState() {
        return state;
    }
    
    public Channel getChannel() {
        return channel;
    }
    
    public PreparedCommand[] getCommandArray() {
        return commands.toArray(new PreparedCommand[0]);
    }

    public int getCommandCount() {
        return commands.size();
    }
}