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
import java.io.InputStream;
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
     * AdvancedGraphicImageRenderer * Contains dynamic graphic image files creates in the context of the current
     * session.
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
        final InputStream inputStream = content.getStream();
        // (1) Trivial scenario - there is nothing to be done here because
        // the user is most likely doing a page refresh and the image already exists in a temp file location
        if (!isInputStreamAvailable(inputStream)) {
            LOGGER.log(Level.FINEST,
                    "The streamed content for uniqueId will not be saved to a new file since it already exists in the cache. UiqueId: "
                            + uniqueId);
            return;
        }

        // (2) The primefaces stream content is opened and it should be possible to write the data to a file
        // we want to make sure we do not create file creation leakage for the same unique id
        if (storedContent.containsKey(uniqueId)) {
            File tempFileCreatedInAPreviousRenderingOfCurrentView = new File(storedContent.get(uniqueId));
            if (tempFileCreatedInAPreviousRenderingOfCurrentView.exists()) {
                tempFileCreatedInAPreviousRenderingOfCurrentView.delete();
                LOGGER.log(
                        Level.FINE,
                        String.format(
                                "Deleting temp file with absolute path: %1$s . A new primefaces dynamic stream is available to write data for the same ui coponent"
                                        + " and we do not wish to keep alive stale data.", storedContent.get(uniqueId)));
            }
        }

        // (3) The uniqueId is either brand new or we are dealing with a rendering of new content
        // for the same UI component and we wish the data of the image not to be stale.
        // A new temp file will produced with the stream content
        ReadableByteChannel inputChannel = null;
        WritableByteChannel outputChannel = null;
        try {
            File tempFile = File.createTempFile(uniqueId, "primefaces");
            storedContent.put(uniqueId, tempFile.getAbsolutePath());
            // get a channel from the stream
            inputChannel = Channels.newChannel(inputStream);
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

    /**
     * Determines if a given input stream has bytes available to be read, this normally only happens the first time a
     * view is being rendered, or when a new image gets uploaded. Upon page refresh, for example, the StreamContent will
     * be closed and we want to continue using the previously saved tmp image.
     */
    private boolean isInputStreamAvailable(InputStream inputStream) {
        try {
            return inputStream.available() > 0;
        } catch (Exception ignoreE) {
            // The GraphicImageManager has closed the input stream for the tmp file
            return false;
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
