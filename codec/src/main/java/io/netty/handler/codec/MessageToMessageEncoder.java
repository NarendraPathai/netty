/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec;

import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandlerUtil;
import io.netty.channel.ChannelOutboundMessageHandlerAdapter;

/**
 * {@link ChannelOutboundMessageHandlerAdapter} which encodes from one message to an other message
 *
 * For example here is an implementation which decodes an {@link Integer} to an {@link String}.
 *
 * <pre>
 *     public class IntegerToStringEncoder extends
 *             {@link MessageToMessageEncoder}&lt;{@link Integer}&gt; {
 *
 *         {@code @Override}
 *         public void encode({@link ChannelHandlerContext} ctx, {@link Integer} message, {@link MessageBuf} out)
 *                 throws {@link Exception} {
 *             out.add(message.toString());
 *         }
 *     }
 * </pre>
 *
 */
public abstract class MessageToMessageEncoder<I> extends ChannelOutboundMessageHandlerAdapter<I> {
    private static final ThreadLocal<MessageBuf<Object>> encoderOutput =
            new ThreadLocal<MessageBuf<Object>>() {
                @Override
                protected MessageBuf<Object> initialValue() {
                    return Unpooled.messageBuffer();
                }
            };

    protected MessageToMessageEncoder() { }

    protected MessageToMessageEncoder(Class<? extends I> outboundMessageType) {
        super(outboundMessageType);
    }

    @Override
    public final void flush(ChannelHandlerContext ctx, I msg) throws Exception {
        MessageBuf<Object> out = encoderOutput.get();

        assert out.isEmpty();

        try {
            encode(ctx, msg, out);
        } catch (CodecException e) {
            throw e;
        } catch (Throwable cause) {
            if (cause instanceof CodecException) {
                throw (CodecException) cause;
            } else {
                throw new EncoderException(cause);
            }
        } finally {
            for (;;) {
                Object encoded = out.poll();
                if (encoded == null) {
                    break;
                }
                // Handle special case when the encoded output is a ByteBuf and the next handler in the pipeline
                // accept bytes. Related to #1222
                ChannelHandlerUtil.addToNextOutboundBuffer(ctx, encoded);
            }
        }
    }

    /**
     * Encode from one message to an other. This method will be called till either the {@link MessageBuf} has nothing
     * left or till this method returns {@code null}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageEncoder} belongs to
     * @param msg           the message to encode to an other one
     * @param out           the {@link MessageBuf} into which the encoded msg should be added
     *                      needs to do some kind of aggragation
     * @throws Exception    is thrown if an error accour
     */
    protected abstract void encode(ChannelHandlerContext ctx, I msg, MessageBuf<Object> out) throws Exception;
}