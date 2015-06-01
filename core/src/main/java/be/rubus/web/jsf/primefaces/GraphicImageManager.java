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
import java.util.HashMap;
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
     * session. A map of UniqueIds -> To AbsoluteFilePath
     */
    private final Map<String, String> storedContent = new ConcurrentHashMap<String, String>();

    /**
     * Map of canonicalId to count of times temporary images have been produced for the canonical id.
     *
     * <P>
     * MOTIVATION: A browser keeps a cache of image resources it has already downloaded. If we have p:graphiImage
     * component that points to a temporary file with uniqueId ref value, the browser will not download that image a
     * second time until the img.src attribute points a new location. Therefore we have a two fold problem. (1) When the
     * user refreshes a page, the StreamContent will not have changed and the stream is closed, therefore it is ok to
     * led the img.src keep whatever uniqueId value is already in the browser page
     *
     * (2) When the user uplaods a new file image for the same uiComponent, we want to save a new tmp file, delete the
     * old one and we especially want the browser to download it. Therefore, we need to produce a new uniqueId.
     *
     */
    private final Map<String, Long> canonicalIdToGenerationCount = new HashMap<String, Long>();

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
     * @param canonicalId
     *            a unique id that essentially identifies a ui component, it does not change throughout time for that
     *            graphic image component.
     *
     * @return a uniqueId that is based on the canonical id and whose value changes each time a new image resource needs
     *         to be saved.
     */
    public synchronized String registerImage(final StreamedContent content, final String canonicalId) {
        final InputStream inputStream = content.getStream();
        // (1) Trivial scenario - there is nothing to be done here because
        // the user is most likely doing a page refresh and the image already exists in a temp file location
        final String oldUniqueId = getCurrentUniqueId(canonicalId);
        if (!GraphicImageUtil.isInputStreamAvailable(inputStream)) {
            LOGGER.log(Level.FINEST,
                    "The streamed content for uniqueId will not be saved to a new file since it already exists in the cache. UiqueId: "
                            + oldUniqueId);
            return oldUniqueId;
        }

        // (2) The primefaces stream content is opened and it should be possible to write the data to a file
        // we want to make sure we do not create file creation leakage for the same unique id
        if (storedContent.containsKey(oldUniqueId)) {
            File tempFileCreatedInAPreviousRenderingOfCurrentView = new File(storedContent.get(oldUniqueId));
            if (tempFileCreatedInAPreviousRenderingOfCurrentView.exists()) {
                tempFileCreatedInAPreviousRenderingOfCurrentView.delete();
                storedContent.remove(oldUniqueId);
                LOGGER.log(
                        Level.FINE,
                        String.format(
                                "Deleting temp file with absolute path: %1$s . A new primefaces dynamic stream is available to write data for the same ui coponent"
                                        + " and we do not wish to keep alive stale data.",
                                storedContent.get(oldUniqueId)));
            }
        }

        // (3) Since we will be saving a new temp image we want to make sure that we advance the uniqueId
        // so that the browser is sure do download a new image for this uniqueId instead of keep using the old cached
        // image
        incrementCanonicalIdCount(canonicalId);
        String newUniqueId = getCurrentUniqueId(canonicalId);

        // (4) The uniqueId is either brand new or we are dealing with a rendering of new content
        // for the same UI component and we wish the data of the image not to be stale.
        // A new temp file will produced with the stream content
        ReadableByteChannel inputChannel = null;
        WritableByteChannel outputChannel = null;
        try {
            File tempFile = File.createTempFile(newUniqueId, "primefaces");
            storedContent.put(newUniqueId, tempFile.getAbsolutePath());
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

        // A new temp file was saved under this uniqueId
        return newUniqueId;
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

    // /////////////////////////////////////////////////
    // BEGIN: CANONICAL ID MANAGEMENT
    // /////////////////////////////////////////////////
    /**
     *
     * @param canonicalUniqueId
     *            the canonical id for the ui component
     * @return a unique dynamic resource id for the ui component which corresponds to an association between the
     *         canonical id and the current index of generated content for the component
     */
    private String getCurrentUniqueId(String canonicalUniqueId) {
        return String
                .format("CANONIAL%1$sUNIQUE%2$s", canonicalUniqueId, getCurrentCanonicalIdCount(canonicalUniqueId));
    }

    /**
     *
     * @param canonicalUniqueId
     *            A unique id for an image that reflects a specific UI component. This value will not change for that
     *            component id, regardless of underlying stream changing with file uploads
     * @return The number of times that canonical id has been correlated with the saving of new streams
     */
    private Long getCurrentCanonicalIdCount(String canonicalUniqueId) {
        if (!canonicalIdToGenerationCount.containsKey(canonicalUniqueId)) {
            canonicalIdToGenerationCount.put(canonicalUniqueId, 0l);
        }
        return canonicalIdToGenerationCount.get(canonicalUniqueId);
    }

    /**
     *
     * @param canonicalUniqueId
     *            A unique id for an image that reflects a specific UI component. This value will not change for that
     *            component id, regardless of underlying stream changing with file uploads
     * @return the increment value of the canonical id
     */
    private Long incrementCanonicalIdCount(String canonicalUniqueId) {
        Long nextCanonicalIdCount = getCurrentCanonicalIdCount(canonicalUniqueId) + 1;
        canonicalIdToGenerationCount.put(canonicalUniqueId, nextCanonicalIdCount);
        return nextCanonicalIdCount;
    }
}
