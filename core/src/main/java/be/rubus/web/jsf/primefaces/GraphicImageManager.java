/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package be.rubus.web.jsf.primefaces;

import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 */
@SessionScoped
@ManagedBean(name = "GraphicImageManager")
public class GraphicImageManager implements HttpSessionBindingListener {

    private static final Logger LOGGER = Logger.getLogger(GraphicImageManager.class.getCanonicalName());

    /**
     * Contains dynamic graphic image files creates in the context of the current session.
     */
    private final Map<String, String> storedContent = new ConcurrentHashMap<String, String>();

    @Override
    public void valueBound(HttpSessionBindingEvent event) {
        // No action required
    }

    @Override
    public void valueUnbound(HttpSessionBindingEvent event) {
        for (String tempFile : storedContent.values()) {
            File f = new File(tempFile);
            f.delete();
        }
    }

    /**
     * During the rendering phase of p:graphic image for which the advanced graphic image feature is enabled the
     * StreamedContent gets written to a temporary file in the OS tmp file folder. Finally, the uniqueId will be used to
     * do a lookup of path to the temporary file to render the image requested.
     *
     * @param content
     *            the primefaces streamed content for an image which should be written out to an OS temporary file.
     * @param uniqueId
     *            a unique id for the current user session and image access, used to create a new unique temp file
     *
     */
    public void registerImage(StreamedContent content, String uniqueId) {

        // (1) Trivial scenario - there is nothing to be done here because
        // the user is most likely doing a page refresh and the image already exists in a temp file location
        if (storedContent.containsKey(uniqueId)) {
            LOGGER.log(Level.FINEST,
                    "The streamed content for uniqueId will not be saved to a new file since it already exists in the cache. UiqueId: "
                            + uniqueId);
            return;
        }

        // (2) The uniqueId is brand new one so the streamed cotent has not yet been closed
        // and we can save the data to a temporary file.
        ReadableByteChannel inputChannel = null;
        WritableByteChannel outputChannel = null;
        try {
            File tempFile = File.createTempFile(uniqueId, "primefaces");
            storedContent.put(uniqueId, tempFile.getAbsolutePath());
            // get a channel from the stream
            inputChannel = Channels.newChannel(content.getStream());
            outputChannel = Channels.newChannel(new FileOutputStream(tempFile));
            // copy the channels
            fastChannelCopy(inputChannel, outputChannel);
        } catch (IOException e) {
            LOGGER.log(
                    Level.SEVERE,
                    "Unexpected error took place while attempting to save primefaces streamed content image to the temporary fold",
                    e);
        } finally {
            // closing the channels
            try {
                if (inputChannel != null) {
                    inputChannel.close();
                }
            } catch (Exception e) {
                LOGGER.log(
                        Level.SEVERE,
                        "Unexpected error took place while attempting to close the input stream of the image to be saved into a temporary location",
                        e);
            }
            try {
                if (outputChannel != null) {
                    outputChannel.close();
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE,
                        "Unexpected error took place while attempting to close the output stream of the temp file", e);
            }
        }
    }

    public StreamedContent retrieveImage(String uniqueId) {
        StreamedContent result = null;
        String tempFile = storedContent.get(uniqueId);
        if (tempFile != null) {
            File f = new File(tempFile);
            try {
                result = new DefaultStreamedContent(new FileInputStream(f));
            } catch (FileNotFoundException e) {
                // FIXME
                e.printStackTrace();
            }
        }
        return result;
    }

    private static void fastChannelCopy(final ReadableByteChannel src, final WritableByteChannel dest)
            throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
        while (src.read(buffer) != -1) {
            // prepare the buffer to be drained
            buffer.flip();
            // write to the channel, may block
            dest.write(buffer);
            // If partial transfer, shift remainder down
            // If buffer is empty, same as doing clear()
            buffer.compact();
        }
        // EOF will leave buffer in fill state
        buffer.flip();
        // make sure the buffer is fully drained.
        while (buffer.hasRemaining()) {
            dest.write(buffer);
        }
    }
}
