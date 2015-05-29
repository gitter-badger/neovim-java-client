package com.neovim;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neovim.msgpack.MessagePackRPC;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.msgpack.core.ExtendedTypeHeader;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class BufferTest {
    private static final int EXT_TYPE = 0;
    private static final long BUFFER_ID = 42L;
    private ObjectMapper objectMapper;
    private Buffer buffer;

    @Mock MessagePackRPC messagePackRPC;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper(new MessagePackFactory());
        NeovimModule module = new NeovimModule(messagePackRPC);
        objectMapper.registerModule(module);
        buffer = new Buffer(messagePackRPC, BUFFER_ID);
    }

    @Test
    public void testSerialize() throws Exception {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MessagePack.newDefaultPacker(payload)
                .packLong(buffer.getId())
                .close();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] payloadContents = payload.toByteArray();
        MessagePack.newDefaultPacker(out)
                .packArrayHeader(1)
                .packExtendedTypeHeader(EXT_TYPE, payloadContents.length)
                .writePayload(payloadContents)
                .close();

        ByteArrayOutputStream objectMapperOut = new ByteArrayOutputStream();
        objectMapper.writeValue(objectMapperOut, new Buffer[] { buffer });
        System.out.println(MessagePack.newDefaultUnpacker(objectMapperOut.toByteArray()).getCursor().next());

        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(objectMapperOut.toByteArray());
        assertThat(unpacker.unpackArrayHeader(), is(1));
        ExtendedTypeHeader extendedTypeHeader = unpacker.unpackExtendedTypeHeader();
        assertThat(extendedTypeHeader.getLength(), is(1));
        assertThat(extendedTypeHeader.getType(), is(EXT_TYPE));
        byte[] buf = new byte[extendedTypeHeader.getLength()];
        unpacker.readPayload(buf);
        assertThat(buf, is(payload.toByteArray()));

        assertThat(objectMapperOut.toByteArray(), is(out.toByteArray()));
    }

    @Test
    public void testDeserialize() throws Exception {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MessagePack.newDefaultPacker(payload)
                .packLong(buffer.getId())
                .close();

        byte[] payloadContents = payload.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessagePack.newDefaultPacker(out)
                .packArrayHeader(1)
                .packExtendedTypeHeader(EXT_TYPE, payloadContents.length)
                .writePayload(payloadContents)
                .close();

        ArrayList<Buffer> list = objectMapper.readValue(
                out.toByteArray(),
                new TypeReference<ArrayList<Buffer>>() {});

        assertThat(list.size(), is(1));
        assertThat(list.get(0), is(buffer));
    }

    @Test
    public void serializeDeserialize_sameObject() throws Exception {
        // Serializer can't handle object not wrapped in array
        Buffer[] wrapped = new Buffer[]{ buffer };
        byte[] serializedValue = objectMapper.writeValueAsBytes(wrapped);
        Buffer[] deserializedValue = objectMapper.readValue(serializedValue, Buffer[].class);

        assertThat(deserializedValue[0], is(buffer));
    }
}