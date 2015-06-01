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

import org.primefaces.component.graphicimage.GraphicImage;
import org.primefaces.component.graphicimage.GraphicImageRenderer;
import org.primefaces.model.StreamedContent;
import org.primefaces.util.Constants;

import javax.faces.application.Resource;
import javax.faces.component.UIComponent;
import javax.faces.component.UIData;
import javax.faces.component.UIParameter;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import java.util.UUID;

/**
 */
public class AdvancedGraphicImageRenderer extends GraphicImageRenderer {

    private boolean determineIfAdvancedRendering(GraphicImage image) {
        boolean result = false;

        boolean isStreamedContent = image.getValue() instanceof StreamedContent;
        Boolean advancedMarker = (Boolean) image.getAttributes().get(AdvancedRendererHandler.ADVANCED_RENDERING);

        if (isStreamedContent && advancedMarker == null) {
            result = determineSpecificParents(image);
        }

        if (!result && advancedMarker != null && advancedMarker) {
            result = true;
        }

        return result;
    }

    private boolean determineSpecificParents(GraphicImage image) {
        boolean result = false;
        UIComponent current = image;
        while (!result && !(current instanceof UIViewRoot)) {
            result = current instanceof UIData;
            if (!result) {
                result = UIComponent.isCompositeComponent(current);
            }
            current = current.getParent();
        }
        return result;
    }

    @Override
    protected String getImageSrc(FacesContext context, GraphicImage image) {
        if (determineIfAdvancedRendering(image)) {
            String src;
            Object value = image.getValue();
            StreamedContent streamedContent = (StreamedContent) value;

            Resource resource = context.getApplication().getResourceHandler()
                    .createResource("dynamiccontent", "advancedPrimefaces", streamedContent.getContentType());
            String resourcePath = resource.getRequestPath();

            // Create a unique image id based on the component being rendered
            // NOTE: The fowllowing is a change that was done on the original repository on upgrade from 3.4 to 4.0
            // - we can not use this code - 3.5 does not support
            // StringEncrypter strEn = RequestContext.getCurrentInstance().getEncrypter();
            // String rid = strEn.encrypt(image.getValueExpression("value").getExpressionString());
            // NOTE: This is the orignal implmentation - not good not reselient to page refresh
            // creates leakage of temporary files in the folder
            // String rid = createUniqueContentId(context);
            // WORK AROUND IMPLEMENTATION:
            // this work around implementation creates a uniqueId using the string hashcode based on the uiComponent
            // ClientId and value expression
            // the outcome should be deterministic and not create file leakage.
            String id = String.format("%1$s_", image.getClientId(), image.getValueExpression("value")
                    .getExpressionString());
            String rid = String.valueOf(id.hashCode());

            StringBuilder builder = new StringBuilder(resourcePath);
            GraphicImageManager graphicImageManager = GraphicImageUtil.retrieveManager(context);
            graphicImageManager.registerImage(streamedContent, rid);

            builder.append("&").append(Constants.DYNAMIC_CONTENT_PARAM).append("=").append(rid);

            for (UIComponent kid : image.getChildren()) {
                if (kid instanceof UIParameter) {
                    UIParameter param = (UIParameter) kid;

                    builder.append("&").append(param.getName()).append("=").append(param.getValue());
                }
            }

            src = builder.toString();

            if (!image.isCache()) {
                src += src.contains("?") ? "&" : "?";
                src += "primefaces_image=" + UUID.randomUUID().toString();
            }

            src = context.getExternalContext().encodeResourceURL(src);
            return src;

        } else {
            return super.getImageSrc(context, image);
        }
    }
}
