package org.yamcs.web.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Rest.RestExceptionMessage;
import org.yamcs.web.AbstractRequestHandler;

import com.dyuproject.protostuff.JsonIOUtil;
import com.dyuproject.protostuff.Schema;
import com.google.protobuf.MessageLite;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Collects some typical patterns which might be of use for extending classes when dealing with incoming
 * REST requests.
 *
 * <p>This is currently in no way enforced to extending classes since we would need to do a bit
 * more work as far as integrating the different approaches (archiverequesthandler does some special stuff
 * to support retrieving large dumps of unknown size).
 *
 * <p>Provides extra helper logic to return exeptions in the preferred outbound media type.
 */
public abstract class AbstractRestRequestHandler extends AbstractRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractRestRequestHandler.class);
    private static final String[] DEFAULT_INBOUND_MEDIA_TYPES = new String[] { JSON_MIME_TYPE, BINARY_MIME_TYPE };
    private static final String[] DEFAULT_OUTBOUND_MEDIA_TYPES = new String[] { JSON_MIME_TYPE, BINARY_MIME_TYPE };
    private JsonFactory jsonFactory = new JsonFactory();

    public abstract void handleRequest(ChannelHandlerContext ctx, HttpRequest httpRequest, MessageEvent evt, String yamcsInstance, String remainingUri) throws RestException;

    /**
     * Accepted Content-Type headers (in priority order, defaults to first if unspecified).
     * <p>By default support for JSON and GPB is configured.
     */
    public String[] getSupportedInboundMediaTypes() {
        return DEFAULT_INBOUND_MEDIA_TYPES;
    }

    /**
     * Supported Accept headers (if unspecified in request, will be matched
     * to inbound meda type or first in this list)
     * <p>By default support for JSON and GPB is configured
     */
    public String[] getSupportedOutboundMediaTypes() {
        return DEFAULT_OUTBOUND_MEDIA_TYPES;
    }

    /**
     * Derives the content type of the incoming request. Supported media types
     * are treated in priority order if no content type was specified.
     * @return null if an unsupported content type was specified
     */
    String getSourceContentType(HttpRequest httpRequest) {
        if (httpRequest.containsHeader(Names.CONTENT_TYPE)) {
            String declaredContentType = httpRequest.getHeader(Names.CONTENT_TYPE);
            for (String supportedContentType : getSupportedInboundMediaTypes()) {
                if (supportedContentType.equals(declaredContentType)) {
                    return declaredContentType;
                }
            }
        }

        // Assume default for simplicity
        return getSupportedInboundMediaTypes()[0];
    }

    /**
     * Derives an applicable content type for the output.
     *
     * This tries to match supported media types with the ACCEPT header,
     * else it will revert to the (derived) source content type.
     */
    String getTargetContentType(HttpRequest httpRequest) {
        if (httpRequest.containsHeader(Names.ACCEPT)) {
            String acceptedContentType = httpRequest.getHeader(Names.ACCEPT);
            for (String supportedContentType : getSupportedOutboundMediaTypes()) {
                if (supportedContentType.equals(acceptedContentType)) {
                    return acceptedContentType;
                }
            }
        } else {
            String sourceContentType = getSourceContentType(httpRequest);
            for (String supportedContentType : getSupportedOutboundMediaTypes()) {
                if (supportedContentType.equals(sourceContentType)) {
                    return supportedContentType;
                }
            }
        }

        // Unsupported content type or wildcard, like */*, just use default
        return getSupportedOutboundMediaTypes()[0];
    }

    /**
     * Deserializes the incoming message extracted from the body. This does not care about
     * what the HTTP method is. Any required checks should be done in extending classes.
     */
    protected <T extends MessageLite.Builder> T readMessage(HttpRequest httpRequest, Schema<T> sourceSchema) throws BadRequestException {
        String sourceContentType = getSourceContentType(httpRequest);
        InputStream cin = new ChannelBufferInputStream(httpRequest.getContent());
        T msg = sourceSchema.newMessage();
        // Allow for empty body, otherwise user has to specify '{}'
        if (HttpHeaders.getContentLength(httpRequest) > 0) {
            try {
                if (BINARY_MIME_TYPE.equals(sourceContentType)) {
                    msg.mergeFrom(cin);
                } else {
                    JsonIOUtil.mergeFrom(cin, msg, sourceSchema, false);
                }
            } catch(IOException e) {
                throw new BadRequestException(e);
            } finally {
                // GPB's mergeFrom does not close the stream, not sure about JsonIOUtil
                try { cin.close(); } catch (IOException e) {}
            }
        }
        return msg;
    }

    /**
     * Writes back a response in one of the supported media types
     */
    protected void writeMessage(HttpRequest httpRequest, QueryStringDecoder qsDecoder, MessageEvent evt, MessageLite responseMsg, Schema responseSchema) throws RestException {
        String targetContentType = getTargetContentType(httpRequest);
        ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
        ChannelBufferOutputStream channelOut = new ChannelBufferOutputStream(buf);
        try {
            if (BINARY_MIME_TYPE.equals(targetContentType)) {
                responseMsg.writeTo(channelOut);
            } else {
                JsonGenerator generator = createJsonGenerator(channelOut, qsDecoder);
                JsonIOUtil.writeTo(generator, responseMsg, responseSchema, false);
                generator.close();
            }
        } catch (IOException e) {
            log.error("Internal server error while writing out message", e);
            throw new RestException(e);
        }
        HttpResponse httpResponse = new DefaultHttpResponse(HTTP_1_1, OK);
        setContentTypeHeader(httpResponse, targetContentType);
        setContentLength(httpResponse, buf.readableBytes());
        httpResponse.setContent(buf);

        Channel ch = evt.getChannel();
        ChannelFuture writeFuture = ch.write(httpResponse);

        // Decide whether to close the connection or not.
        if (!isKeepAlive(httpRequest)) {
            // Close the connection when the whole content is written out.
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Just a little shortcut because builders are dead ugly
     */
    protected static RestExceptionMessage.Builder toException(String type, Throwable t) {
        return RestExceptionMessage.newBuilder().setType(type).setMsg(t.getMessage());
    }

    protected JsonGenerator createJsonGenerator(OutputStream out, QueryStringDecoder qsDecoder) throws IOException {
        JsonGenerator generator = jsonFactory.createJsonGenerator(out, JsonEncoding.UTF8);
        if (qsDecoder.getParameters().containsKey("pretty")) {
            generator.useDefaultPrettyPrinter();
        }
        return generator;
    }

    /**
     * Used for sending non-application exceptions (for example: bad request, ioexception, ..).
     * When outputting as json, provide some extra information for debugging reasons. When
     * outputting as GPB it's difficult to achieve this since we're not using extensions,
     * so send just a text/plain error in all other cases.
     */
    protected void sendError(Throwable t, HttpRequest req, QueryStringDecoder qsDecoder, ChannelHandlerContext ctx, HttpResponseStatus status) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
        String contentType = getTargetContentType(req);
        if (JSON_MIME_TYPE.equals(contentType)) {
            response.setHeader(CONTENT_TYPE, JSON_MIME_TYPE); // UTF-8 by default IETF RFC4627
            try {
                ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
                ChannelBufferOutputStream channelOut = new ChannelBufferOutputStream(buf);
                JsonGenerator generator = createJsonGenerator(channelOut, qsDecoder);
                generator.writeStartObject();
                generator.writeFieldName("exception");
                generator.writeStartObject();
                generator.writeStringField("type", t.getClass().getSimpleName());
                generator.writeStringField("msg", t.getMessage());
                generator.writeEndObject();
                generator.writeEndObject();
                generator.close();
                response.setContent(buf);
                // Close the connection as soon as the error message is sent.
                ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
            } catch (IOException e2) {
                log.error("Could not create Json Generator", e2);
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            sendError(ctx, status); // text/plain
        }
    }
}
