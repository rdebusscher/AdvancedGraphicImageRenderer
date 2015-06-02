/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Graphic image manager is responsible for managing all the temporary files saved in the OS tmp folder for each HTTP
 * session that took advantage of the Advanced Graphic Image Manager.
 *
 * <P>
 * NOTES: As session scoped bean with state synchronization is needed. The only synchronization object used in this
 * class is stored content. Any public API is obliged to acquire this lock.
 *
 */
@SessionScoped
@ManagedBean(name = "GraphicImageManager")
public class GraphicImageManager implements HttpSessionBindingListener, java.io.Serializable {

    private static final Logger LOGGER = Logger.getLogger(GraphicImageManager.class.getCanonicalName());

    private static final int MIN_FIXED_RANDOM_VALUE_FOR_USER_SESSION = 100;
    private static final int MAX_FIXED_RANDOM_VALUE_FOR_USER_SESSION = 50000;

    /**
     * The session random id is a random value produced on creation of the session bean. Every client session will have
     * a different value. The point of this value is to ensure that when a p:graphicImage is rendered using the advanced
     * algorithm, the unique id produced will never be the same across different sessions or application startup, thus
     * bypassing the caching of the browser.
     *
     * <P>
     * MOTIVATION: * In the current implementation the uniqueIds produced for the dynamic resources are of the form:
     *
     * (1)CANONICALIDsomeInteger(2)INCRAMENTALsomeIncrementIntegerRANDOMaValueThatIsUniqueForEachSession
     *
     * Such a UNIQUE id syntax the uniqueId to produced in a deterministic manner, such that when there is a page
     * refresh we are able to discover the uniqueId had had been produced on the last execution of the renderer for the
     * UI component being redendered, but when there is new content to be streamed a new uniqueid is produced.
     */
    private final String sessionRandomId;
    /**
     * AdvancedGraphicImageRenderer * Contains dynamic graphic image files creates in the context of the current
     * session. A map of UniqueIds -> To AbsoluteFilePath
     */
    private final Map<String, String> storedContent = new HashMap<String, String>();

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

    /**
     * Constructs a graphic image manager which will keep track of the temporary files saved for the current living
     * session.
     */
    public GraphicImageManager() {
        this.sessionRandomId = String.valueOf(randomWithRange(MIN_FIXED_RANDOM_VALUE_FOR_USER_SESSION,
                MAX_FIXED_RANDOM_VALUE_FOR_USER_SESSION));
    }

    @Override
    public void valueBound(HttpSessionBindingEvent event) {
        // No action required
    }

    @Override
    public void valueUnbound(HttpSessionBindingEvent event) {
        synchronized (storedContent) {
            for (String tempFile : storedContent.values()) {
                File f = new File(tempFile);
                f.delete();
            }
        }
    }

    // ////////////////////////////////////////
    // BEGIN: API
    // ////////////////////////////////////////

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
    public String registerImage(final StreamedContent content, final String canonicalId) {
        synchronized (storedContent) {
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
            deleteStaleTempFileByUniqueId(oldUniqueId);

            // (3) Since we will be saving a new temp image we want to make sure that we advance the uniqueId
            // so that the browser is sure do download a new image for this uniqueId instead of keep using the old
            // cached
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
                closeChannels(inputChannel, outputChannel);
            }

            // A new temp file was saved under this uniqueId
            return newUniqueId;
        }
    }

    public StreamedContent retrieveImage(String uniqueId) {
        synchronized (storedContent) {
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
    }

    // /////////////////////////////////////////////////
    // BEGIN: CANONICAL ID MANAGEMENT
    // /////////////////////////////////////////////////
    /**
     * Returns the current unique id for a given canonical value. The unique ids need to be random to the browser but
     * cannot be produced in absolutely random manner within the implementation. The random id that the browser will see
     * must appeaer unique to bypass its cache. But internally, the was it is produced has to be well defined, so that
     * when a page refresh takes place we can stream the old file, and when a new open stream is available we can
     * produce a new random id and temporary file.
     *
     * @param canonicalUniqueId
     *            the canonical id for the ui component
     * @return a unique dynamic resource id for the ui component which corresponds to an association between the
     *         canonical id and the current index of generated content for the component
     */
    private String getCurrentUniqueId(String canonicalUniqueId) {
        return String.format("CANONICAL%1$sINC%2$sSESSRAND%3$sEND", canonicalUniqueId,
                getCurrentCanonicalIdCount(canonicalUniqueId), this.sessionRandomId);
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

    // /////////////////////////////////////////////////
    // BEGIN: HELPER LOGIC
    // /////////////////////////////////////////////////
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

    /**
     * The renderer is asking for a new StreamContent image to be renderer for the same old client id for which a tmp
     * image has already been produced. Proactively clean up the temp folder
     *
     * @param oldUniqueId
     *            unique id that identifies a temp file that is no longer needed and should be deleted
     */
    private void deleteStaleTempFileByUniqueId(final String oldUniqueId) {
        if (storedContent.containsKey(oldUniqueId)) {
            File tempFileCreatedInAPreviousRenderingOfCurrentView = new File(storedContent.get(oldUniqueId));
            if (tempFileCreatedInAPreviousRenderingOfCurrentView.exists()) {
                if (tempFileCreatedInAPreviousRenderingOfCurrentView.delete()) {
                    storedContent.remove(oldUniqueId);
                    LOGGER.log(
                            Level.FINE,
                            String.format(
                                    "Deleting temp file with absolute path: %1$s . A new primefaces dynamic stream is available to write data for the same ui coponent"
                                            + " and we do not wish to keep alive stale data.",
                                    storedContent.get(oldUniqueId)));
                } else {
                    LOGGER.log(Level.WARNING, String.format("Delete file not successful for file: %1$s ",
                            tempFileCreatedInAPreviousRenderingOfCurrentView.getAbsolutePath()));
                }

            }
        }
    }

    // /////////////////////////////////////////////////
    // BEGIN: TRIVIAL
    // /////////////////////////////////////////////////
    /**
     * Generate a random int within a given range
     *
     * @param min
     *            min random value
     * @param max
     *            max random value
     * @return a random number between min and max random value
     */
    private int randomWithRange(int min, int max) {
        int range = (max - min) + 1;
        return (int) (Math.random() * range) + min;
    }

    /**
     * Close the channel resources created to copy the StreamContent to a temp file
     *
     * @param inputChannel
     *            a channel over the p:graphic image inputstream
     * @param outputChannel
     *            a channel over temporary file outputstream
     */
    private void closeChannels(ReadableByteChannel inputChannel, WritableByteChannel outputChannel) {
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
