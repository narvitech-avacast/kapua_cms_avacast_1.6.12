/*******************************************************************************
 * Copyright (c) 2017, 2022 Eurotech and/or its affiliates and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.kapua.app.api.resources.v1.resources.model;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

@Provider
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
public class WordsBodyWriter implements MessageBodyWriter<Words> {

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return Words.class.isAssignableFrom(type);
    }

    @Override
    public long getSize(Words words, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(Words words, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException, WebApplicationException {
        if (mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            writeJson(words, entityStream);
        } else {
            writeXml(words, entityStream);
        }
    }

    private void writeJson(Words words, OutputStream entityStream) {
        JsonObjectBuilder root = Json.createObjectBuilder()
                .add("value", nullSafe(words.getValue()));
        List<Word> entries = words.getWords();
        if (entries != null) {
            JsonArrayBuilder array = Json.createArrayBuilder();
            for (Word entry : entries) {
                array.add(Json.createObjectBuilder()
                        .add("key", nullSafe(entry.getKey()))
                        .add("value", nullSafe(entry.getValue())));
            }
            root.add("words", array);
        }
        Json.createWriter(entityStream).writeObject(root.build());
    }

    private void writeXml(Words words, OutputStream entityStream) throws IOException {
        try {
            XMLStreamWriter xml = XMLOutputFactory.newFactory()
                    .createXMLStreamWriter(entityStream, StandardCharsets.UTF_8.name());
            xml.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
            xml.writeStartElement("words");
            xml.writeAttribute("value", nullSafe(words.getValue()));

            List<Word> entries = words.getWords();
            if (entries != null) {
                for (Word entry : entries) {
                    xml.writeEmptyElement("word");
                    xml.writeAttribute("key", nullSafe(entry.getKey()));
                    xml.writeAttribute("value", nullSafe(entry.getValue()));
                }
            }

            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
        } catch (XMLStreamException e) {
            throw new IOException("Unable to serialize Words as XML.", e);
        }
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
