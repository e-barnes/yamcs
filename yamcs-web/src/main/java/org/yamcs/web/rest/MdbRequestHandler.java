package org.yamcs.web.rest;

import java.io.IOException;
import java.io.ObjectOutputStream;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.protobuf.Rest.RestDumpRawMdbRequest;
import org.yamcs.protobuf.Rest.RestDumpRawMdbResponse;
import org.yamcs.protobuf.Rest.RestListAvailableParametersRequest;
import org.yamcs.protobuf.Rest.RestListAvailableParametersResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.NamedDescriptionIndex;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

/**
 * Handles incoming requests related to the Mission Database (offset /mdb).
 */
public class MdbRequestHandler extends AbstractRestRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(MdbRequestHandler.class);

    @Override
    public void handleRequest(ChannelHandlerContext ctx, HttpRequest httpRequest, MessageEvent evt, String yamcsInstance, String remainingUri) throws RestException {
        QueryStringDecoder qsDecoder = new QueryStringDecoder(remainingUri);
        if ("parameters".equals(qsDecoder.getPath())) {
            RestListAvailableParametersRequest request = readMessage(httpRequest, SchemaRest.RestListAvailableParametersRequest.MERGE).build();
            MessageLite responseMsg = listAvailableParameters(request, yamcsInstance);
            writeMessage(httpRequest, qsDecoder, evt, responseMsg, SchemaRest.RestListAvailableParametersResponse.WRITE);
        } else if ("dump".equals(qsDecoder.getPath())) {
            RestDumpRawMdbRequest request = readMessage(httpRequest, SchemaRest.RestDumpRawMdbRequest.MERGE).build();
            MessageLite responseMsg = dumpRawMdb(request, yamcsInstance);
            writeMessage(httpRequest, qsDecoder, evt, responseMsg, SchemaRest.RestDumpRawMdbResponse.WRITE);
        } else {
            log.debug("No match for '" + qsDecoder.getPath() + "'");
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
        }
    }

    /**
     * Sends the XTCEDB for the requested yamcs instance.
     * <p>
     * Currently only sends MDB:OPS Name names.
     */
    private RestListAvailableParametersResponse listAvailableParameters(RestListAvailableParametersRequest request, String yamcsInstance) throws RestException {
        XtceDb mdb = loadMdb(yamcsInstance);
        RestListAvailableParametersResponse.Builder responseb = RestListAvailableParametersResponse.newBuilder();

        NamedDescriptionIndex<Parameter> index = mdb.getParameterAliases();
        // TODO dump for all namespaces if not specified
        for (String namespace : request.getNamespacesList()) {
            for (String name : index.getNamesForAlias(namespace)) {
                responseb.addIds(NamedObjectId.newBuilder().setNamespace(namespace).setName(name));
            }
        }
        return responseb.build();
    }

    private RestDumpRawMdbResponse dumpRawMdb(RestDumpRawMdbRequest request, String yamcsInstance) throws RestException {
        RestDumpRawMdbResponse.Builder responseb = RestDumpRawMdbResponse.newBuilder();

        // TODO TEMP would prefer if we don't send java-serialized data.
        // TODO this limits our abilities to send, say, json
        // TODO and makes clients too dependent
        XtceDb mdb = loadMdb(yamcsInstance);
        ByteString.Output bout = ByteString.newOutput();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(bout);
            oos.writeObject(mdb);
        } catch (IOException e) {
            log.error("Could not serialize MDB", e);
            throw new RestException("Could not serialize MDB", e);
        } finally {
            if(oos != null) {
                try { oos.close(); } catch (IOException e) {}
            }
        }
        responseb.setRawMdb(bout.toByteString());
        return responseb.build();
    }

    private XtceDb loadMdb(String yamcsInstance) throws RestException {
        try {
            return XtceDbFactory.getInstance(yamcsInstance);
        } catch(ConfigurationException e) {
            log.error("Could not get MDB for instance '" + yamcsInstance + "'", e);
            throw new RestException("Could not get MDB for instance '" + yamcsInstance + "'", e);
        }
    }
}
