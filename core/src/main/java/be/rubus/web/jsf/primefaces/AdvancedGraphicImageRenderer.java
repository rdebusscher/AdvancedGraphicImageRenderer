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
import org.primefaces.context.RequestContext;
import org.primefaces.el.ValueExpressionAnalyzer;
import org.primefaces.model.StreamedContent;
import org.primefaces.util.Constants;
import org.primefaces.util.DynamicResourceBuilder;
import org.primefaces.util.SharedStringBuilder;
import org.primefaces.util.StringEncrypter;

import javax.el.ValueExpression;
import javax.faces.application.Resource;
import javax.faces.component.UIComponent;
import javax.faces.component.UIData;
import javax.faces.component.UIParameter;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;

/**
 */
public class AdvancedGraphicImageRenderer extends GraphicImageRenderer {

    private static final String SB_BUILD = DynamicResourceBuilder.class.getName() + "#build";

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
    protected String getImageSrc(FacesContext context, GraphicImage image) throws Exception {
        if (determineIfAdvancedRendering(image)) {
            return buildAdvancedImageSrc(context, image);

        } else {
            return super.getImageSrc(context, image);
        }
    }


    private String buildAdvancedImageSrc(FacesContext context, GraphicImage image) throws UnsupportedEncodingException {
        String src;

        StreamedContent streamedContent = (StreamedContent) image.getValue();
        Resource resource = context.getApplication().getResourceHandler().createResource("dynamiccontent", "advancedPrimefaces", streamedContent.getContentType());
        String resourcePath = resource.getRequestPath();
        StringEncrypter encrypter = RequestContext.getCurrentInstance().getEncrypter();

        ValueExpression expression = ValueExpressionAnalyzer.getExpression(context.getELContext(), image.getValueExpression("value"));
        String rid = encrypter.encrypt(expression.getExpressionString());

        GraphicImageManager graphicImageManager = GraphicImageUtil.retrieveManager(context);
        graphicImageManager.registerImage(streamedContent, rid);

        StringBuilder builder = SharedStringBuilder.get(context, SB_BUILD);

        builder.append(resourcePath).append("&").append(Constants.DYNAMIC_CONTENT_PARAM).append("=").append(URLEncoder.encode(rid, "UTF-8"));

        for (UIComponent kid : image.getChildren()) {
            if (kid instanceof UIParameter) {
                UIParameter param = (UIParameter) kid;
                Object paramValue = param.getValue();

                builder.append("&").append(param.getName()).append("=");

                if (paramValue != null) {
                    builder.append(URLEncoder.encode(param.getValue().toString(), "UTF-8"));
                }
            }
        }

        src = builder.toString();

        boolean cache = image.isCache();

        src += src.contains("?") ? "&" : "?";
        src += Constants.DYNAMIC_CONTENT_CACHE_PARAM + "=" + cache;

        if (!cache) {
            src += "&uid=" + UUID.randomUUID().toString();
        }

        return context.getExternalContext().encodeResourceURL(src);

    }
}
