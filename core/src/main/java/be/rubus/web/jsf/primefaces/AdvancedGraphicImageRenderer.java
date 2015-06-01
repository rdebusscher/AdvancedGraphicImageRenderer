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
        // (1) Regardles of the user asking to use advanced rendering explicitly by using the ui element
        // we simply do not override the default primefaces behavior if the Image Is not returning streamed conent
        if (image.getValue() == null) {
            // otherwise the renderer would break with null pointer exception on get image src
            // the user might want the advanced algorithm to run but we know already it would be doomed to fail
            return false;
        }
        boolean weAreDealingWithSaticResource = !(image.getValue() instanceof StreamedContent);
        if (weAreDealingWithSaticResource) {
            return false;
        }

        // (2) we are dealing with an attribute value that is a streamed content
        // if the user is forcing us explictely to to use the advanced graphic image renderer algorithm
        // then true
        Boolean advancedMarker = (Boolean) image.getAttributes().get(AdvancedRendererHandler.ADVANCED_RENDERING);
        if (advancedMarker != null && advancedMarker) {
            return true;
        }

        // (3) Streamed content but the user is not forcing us to absolutely use the AdvancedGraphicImageRneder
        // algorithm than use the "smart" strategy to determine weather this special algorithm is needed
        // the hope is to use this specific algorithm as little as possible and let primefaces run the show on its own
        // however there are some ValueExpression that we know already that p:graphicImage will fail to evaluate during
        // ResourceHandler call
        return determineSpecificParents(image);

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

            // Create a canonical id for the image to be returned for user
            // NOTE: this step is crucial since it needs to satisfy the following properties:
            // (1) Each time new data exists to be presented to the user the canonical id must led to the generation of
            // a new uniqueId
            // that will be linked to the img.src
            // (2) each time the user refreshes the page and no new content stream exists to be show, the old uniqueId
            // must be re-used
            String canonicalIdVerbose = String.format("%1$s_%2$s", image.getClientId(),
                    image.getValueExpression("value").getExpressionString());
            String canonicalId = String.valueOf(canonicalIdVerbose.hashCode());

            StringBuilder builder = new StringBuilder(resourcePath);
            GraphicImageManager graphicImageManager = GraphicImageUtil.retrieveManager(context);
            String rid = graphicImageManager.registerImage(streamedContent, canonicalId);

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
